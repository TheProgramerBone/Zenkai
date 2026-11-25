package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.master.MasterService;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.training.ShadowTrainingManager;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * God Ki (skill tree F5, diseño §5): cómo se consigue. El nodo tiene tres estados —SELLADO,
 * HABILITADO por la audiencia de un maestro, ADQUIRIDO— y tres vías de adquisición:
 *  (a) ser una divinidad con cargo (isDivine): SkillTree.ensureDivineGodKi, cada tick;
 *  (b) la PRUEBA del maestro tras la audiencia: vencer a una Sombra fuerte (Shadow Training con
 *      la marca {@link #TRIAL_TAG}); ganar da God Ki, perder no cuesta nada y se repite;
 *  (c) el RITUAL: el candidato y N jugadores más cargan ki a la vez a su alrededor durante unos
 *      segundos; el candidato se transforma directamente en la primera forma divina de su raza
 *      (nodo regalado, vía "ritual") y con ello obtiene God Ki. Se salta TP y audiencia, NO la
 *      cadena mortal ni los requisitos de esa forma (un jugador recién creado no puede ser dios).
 *      No consume nada y se puede repetir para ayudar a otro.
 * Sin vía por deseo (decisión del usuario: rotundo no).
 *
 * La audiencia y la prueba son SERVICIOS del maestro (pestaña Servicios de MasterScreen, ver
 * MasterService): {@link #services} los da a cualquier maestro de la lista "masters" de
 * god_ki.json (Tori-Bot hoy; Whis/Bills cuando existan, sin tocar código).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class GodKi {
    private GodKi() {}

    /** Marca de la Sombra de la prueba divina (como DEBUG_DUMMY_TAG del muñeco de depuración). */
    public static final String TRIAL_TAG = "zenkai_god_ki_trial";

    // ── Elegibilidad ─────────────────────────────────────────────────────────

    /**
     * ¿Tiene la cadena mortal que pide lo divino? Es poseer el nodo del que cuelga God Ki en el
     * árbol (SSJ3 para el Saiyan, Black para el Arcosiano…).
     */
    public static boolean mortalChainReady(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) return false;
        SkillTreeNodes.Node gk = SkillTreeNodes.find(att.getRace(), SkillTreeMigration.NODE_GOD_KI);
        if (gk == null) return false;
        if (gk.parent() == null || SkillTreeNodes.ROOT.equals(gk.parent())) return true;
        SkillTreeNodes.Node parent = SkillTreeNodes.find(att.getRace(), gk.parent());
        return parent != null ? SkillTreeNodes.owned(p, parent) : FormAccess.unlocked(p, gk.parent());
    }

    /** Forma mortal de la que cuelga lo divino (la que simula la Sombra de la prueba). */
    private static ResourceLocation mortalPeak(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        SkillTreeNodes.Node gk = SkillTreeNodes.find(att.getRace(), SkillTreeMigration.NODE_GOD_KI);
        if (gk == null || gk.parent() == null || SkillTreeNodes.ROOT.equals(gk.parent())) return FormIds.BASE;
        return gk.parent();
    }

    /** Concede God Ki y avisa (prueba o ritual). */
    /**
     * Concede God Ki desde un addon (su propia prueba, ritual o misión), por la vía {@code EVENT}. No hace nada si el
     * jugador ya lo tiene. NO comprueba la cadena mortal ni el alineamiento: eso es decisión del addon. Dispara
     * {@code GodKiGrantedEvent} igual que la prueba y el ritual de Zenkai.
     * @return true si se concedió ahora
     */
    public static boolean grantFromAddon(ServerPlayer sp) {
        if (SkillTree.hasGodKi(sp)) return false;
        grantGodKi(sp, SkillTree.Via.EVENT);
        PlayerLifeCycle.sync(sp);
        return true;
    }

    private static void grantGodKi(ServerPlayer sp, SkillTree.Via via) {
        SkillTree.grant(sp, SkillTreeMigration.NODE_GOD_KI, 1, via);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.hmc.zenkai.api.event.GodKiGrantedEvent(sp, via.name().toLowerCase(java.util.Locale.ROOT)));
        sp.displayClientMessage(Component.translatable("message.zenkai.god_ki.granted")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
    }

    // ── Servicios del maestro: audiencia y prueba ────────────────────────────

    /** Servicios de God Ki de un maestro, o ninguno si no figura en god_ki.json. */
    public static List<MasterService> services(String masterId) {
        var def = com.hmc.zenkai.feature.skills.SkillDef.get(com.hmc.zenkai.feature.skills.DivineForms.SKILL);
        if (def == null || def.masters() == null || !def.masters().contains(masterId)) return List.of();
        return List.of(AUDIENCE, TRIAL);
    }

    private static final MasterService AUDIENCE = new MasterService() {
        @Override public String id() { return "god_ki_audience"; }

        @Override public Component label(ServerPlayer sp) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            return Component.translatable(att.hasGodKiAudience() || SkillTree.hasGodKi(sp)
                    ? "master.zenkai.service.god_ki_audience.done" : "master.zenkai.service.god_ki_audience");
        }

        @Override public boolean available(ServerPlayer sp) {
            return !SkillTree.hasGodKi(sp) && !PlayerStatsAttachment.get(sp).hasGodKiAudience() && mortalChainReady(sp);
        }

        @Override public Component tooltip(ServerPlayer sp) {
            if (SkillTree.hasGodKi(sp)) return Component.translatable("master.zenkai.service.god_ki.have");
            if (PlayerStatsAttachment.get(sp).hasGodKiAudience()) {
                return Component.translatable("master.zenkai.service.god_ki_audience.already");
            }
            return Component.translatable("master.zenkai.service.god_ki_audience.not_ready",
                    SkillTreeData.displayName(mortalPeak(sp)));
        }

        @Override public boolean claim(ServerPlayer sp) {
            if (!available(sp)) return false;
            PlayerStatsAttachment.get(sp).setGodKiAudience(true);
            PlayerLifeCycle.sync(sp);
            sp.sendSystemMessage(Component.translatable("message.zenkai.god_ki.audience_granted")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return true;
        }
    };

    private static final MasterService TRIAL = new MasterService() {
        @Override public String id() { return "god_ki_trial"; }

        @Override public Component label(ServerPlayer sp) {
            return Component.translatable("master.zenkai.service.god_ki_trial");
        }

        @Override public boolean available(ServerPlayer sp) {
            return !SkillTree.hasGodKi(sp) && PlayerStatsAttachment.get(sp).hasGodKiAudience()
                    && !ShadowTrainingManager.hasActive(sp);
        }

        @Override public Component tooltip(ServerPlayer sp) {
            if (SkillTree.hasGodKi(sp)) return Component.translatable("master.zenkai.service.god_ki.have");
            if (ShadowTrainingManager.hasActive(sp)) return Component.translatable("master.zenkai.service.god_ki_trial.busy");
            return Component.translatable("master.zenkai.service.god_ki_trial.need_audience");
        }

        @Override public boolean claim(ServerPlayer sp) {
            if (!available(sp)) return false;
            return startTrial(sp);
        }
    };

    /** Lanza la prueba: una Sombra a {@code god_ki.trial_shadow_fraction} de tu poder con tu cima mortal. */
    public static boolean startTrial(ServerPlayer sp) {
        var shadow = ShadowTrainingManager.start(sp, (float) ServerConfig.godKiTrialFraction(), mortalPeak(sp));
        if (shadow == null) return false;
        shadow.addTag(TRIAL_TAG);
        sp.sendSystemMessage(Component.translatable("message.zenkai.god_ki.trial_start")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** La Sombra de la prueba ha caído: God Ki. Lo llama ShadowTrainingManager.onShadowDeath. */
    public static void onTrialWon(ServerPlayer owner) {
        if (SkillTree.hasGodKi(owner)) return;
        grantGodKi(owner, SkillTree.Via.MASTER_TRIAL);
        ZenkaiTriggers.MILESTONE.get().trigger(owner, ZenkaiTriggers.Kinds.GOD_KI_TRIAL);
        PlayerLifeCycle.sync(owner);
    }

    /** La prueba terminó sin victoria (muerte, derribo, desconexión). Lo llama ShadowTrainingManager. */
    public static void onTrialLost(ServerPlayer owner) {
        owner.sendSystemMessage(Component.translatable("message.zenkai.god_ki.trial_failed")
                .withStyle(ChatFormatting.GRAY));
    }

    // ── Ritual ───────────────────────────────────────────────────────────────

    /** Candidato → ticks que lleva el ritual sin interrupción. Transitorio, como ShadowTrainingManager.ACTIVE. */
    private static final Map<UUID, Integer> RITUAL = new HashMap<>();

    /** ¿Puede este jugador ser CANDIDATO? Sin God Ki, cadena mortal hecha y requisitos de su primera forma divina. */
    public static boolean ritualCandidate(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || SkillTree.hasGodKi(sp) || !mortalChainReady(sp)) return false;
        ResourceLocation first = SkillTreeNodes.firstDivineForm(att.getRace());
        if (first == null) return false;
        if (att.getAlignment() < ServerConfig.godKiRitualMinAlignment()) return false;
        return SkillTreeNodes.requirementsMet(sp, first);
    }

    private static boolean participant(ServerPlayer candidate, ServerPlayer other) {
        if (other == candidate || other.isSpectator() || !other.isAlive()) return false;
        PlayerStatsAttachment o = PlayerStatsAttachment.get(other);
        if (!o.isRaceChosen() || !o.isChargingKi() || o.flags().isDowned()) return false;
        if (ServerConfig.godKiRitualSameRace()
                && o.getRace() != PlayerStatsAttachment.get(candidate).getRace()) return false;
        if (o.getAlignment() < ServerConfig.godKiRitualMinAlignment()) return false;
        double r = ServerConfig.godKiRitualRadius();
        return other.level() == candidate.level() && other.distanceToSqr(candidate) <= r * r;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 5 != 0) return; // 4 veces por segundo basta
        List<UUID> alive = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ShadowTrainingManager.checkTrialDowned(sp);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isChargingKi() || att.flags().isDowned() || !ritualCandidate(sp)) continue;
            List<ServerPlayer> around = new ArrayList<>();
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (participant(sp, other)) around.add(other);
            }
            List<net.minecraft.world.entity.Entity> dummies = ritualDummies(sp);
            int need = ServerConfig.godKiRitualParticipants();
            // Los muñecos de «dummy ritual» (solo pruebas) cuentan como participantes cargando ki.
            int helpers = around.size() + dummies.size();
            if (helpers < need) continue;
            alive.add(sp.getUUID());
            int ticks = RITUAL.merge(sp.getUUID(), 5, Integer::sum);
            int total = ServerConfig.godKiRitualSeconds() * 20;
            float progress = Math.min(1f, ticks / (float) total);
            // El gauge de carga de ki se oculta en todos los implicados mientras dure (ver KiChargeGaugeOverlay).
            long until = sp.level().getGameTime() + 12L;
            att.flags().setGodKiRitualUntil(until);
            PlayerLifeCycle.sync(sp);
            for (ServerPlayer o : around) {
                PlayerStatsAttachment.get(o).flags().setGodKiRitualUntil(until);
                PlayerLifeCycle.sync(o);
            }
            ritualFx(sp, around, dummies, progress);
            sp.displayClientMessage(Component.translatable("message.zenkai.god_ki.ritual_progress",
                    Math.round(100f * progress), helpers).withStyle(ChatFormatting.GOLD), true);
            for (ServerPlayer o : around) {
                o.displayClientMessage(Component.translatable("message.zenkai.god_ki.ritual_helping",
                        sp.getDisplayName(), Math.round(100f * progress)).withStyle(ChatFormatting.GOLD), true);
            }
            if (ticks >= total) completeRitual(sp, around);
        }
        // Quien soltó la carga, salió del radio o perdió participantes empieza de cero.
        RITUAL.keySet().retainAll(alive);
    }

    /**
     * Efecto de Genki Dama: de cada participante sale una estela de energía hacia el cuerpo del
     * candidato, mientras la energía gira en espiral a su alrededor (más rápido y cerca con el progreso). Colores como la Genki
     * Dama (SpiritBombGatherer): luminoso y blanco para un candidato bueno o neutral, rojo oscuro
     * (la Genki Dama malvada) para uno MALVADO, según su alineamiento.
     */
    private static void ritualFx(ServerPlayer sp, List<ServerPlayer> around,
                                 List<net.minecraft.world.entity.Entity> dummies, float progress) {
        ServerLevel level = sp.serverLevel();
        boolean candidateEvil = isEvil(sp);
        // Las estelas apuntan al CUERPO del candidato; cada una lleva el color del alineamiento de QUIEN LA ENVÍA
        // (los muñecos de prueba, sin alineamiento, usan el del candidato).
        net.minecraft.world.phys.Vec3 body = sp.position().add(0, sp.getBbHeight() * 0.55, 0);
        List<net.minecraft.core.particles.ParticleOptions> donorColors = new ArrayList<>();
        for (ServerPlayer o : around) {
            boolean evil = isEvil(o);
            donorColors.add(energy(evil, 1.0f));
            com.hmc.zenkai.feature.technique.SpiritBombGatherer.streak(level, energy(evil, 1.0f),
                    o.position().add(0, o.getBbHeight() * 0.6, 0), body, 4, 40);
        }
        for (net.minecraft.world.entity.Entity d : dummies) {
            donorColors.add(energy(candidateEvil, 1.0f));
            com.hmc.zenkai.feature.technique.SpiritBombGatherer.streak(level, energy(candidateEvil, 1.0f),
                    d.position().add(0, d.getBbHeight() * 0.6, 0), body, 4, 40);
        }
        // Pequeña cinemática: brazos de energía giran en espiral alrededor del candidato, cada vez más rápido y
        // más cerca, y suben por el cuerpo según avanza el ritual. El halo se reparte entre los participantes:
        // cada brazo toma el color del alineamiento de uno de ellos.
        double radius = 1.4 - 0.7 * progress;
        double spin = 0.25 + 0.55 * progress;            // rad por tick
        double base = sp.tickCount * spin;
        int arms = Math.max(3, Math.min(donorColors.size(), 6));
        for (int arm = 0; arm < arms; arm++) {
            net.minecraft.core.particles.ParticleOptions orbit = donorColors.isEmpty()
                    ? energy(candidateEvil, 1.3f) : donorColors.get(arm % donorColors.size());
            for (int k = 0; k < 6; k++) {                 // 6 puntos por llamada: cubren los 5 ticks de arco
                double ang = base + arm * (Math.PI * 2 / arms) - k * spin * 0.8;
                double y = sp.getY() + 0.2 + (sp.getBbHeight() - 0.1) * ((progress * 0.5 + k / 12.0 + arm / (2.0 * arms)) % 1.0);
                level.sendParticles(orbit, sp.getX() + Math.cos(ang) * radius, y, sp.getZ() + Math.sin(ang) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        // Fogonazo final: según el alineamiento de quien está en el MEDIO (blanco o rojo malvado).
        if (progress >= 0.97f) flash(level, body, candidateEvil);
        level.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                (int) (6 + 30 * progress), 0.6, 1.0, 0.6, 0.4);
    }

    private static boolean isEvil(ServerPlayer p) {
        return com.hmc.zenkai.feature.alignment.AlignmentTier.of(PlayerStatsAttachment.get(p).getAlignment())
                == com.hmc.zenkai.feature.alignment.AlignmentTier.EVIL;
    }

    /** Mota de energía como la de la Genki Dama: luminosa (END_ROD) o roja oscura si es malvada. */
    private static net.minecraft.core.particles.ParticleOptions energy(boolean evil, float scale) {
        return evil ? com.hmc.zenkai.registry.ModParticles.energyMote(
                com.hmc.zenkai.feature.technique.SpiritBombGatherer.EVIL_STREAK_RGB, scale) : ParticleTypes.END_ROD;
    }

    /** Fogonazo del final: FLASH (blanco) para un candidato no malvado; para uno malvado, una explosión de motes rojas. */
    private static void flash(ServerLevel level, net.minecraft.world.phys.Vec3 at, boolean evil) {
        if (!evil) {
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            return;
        }
        level.sendParticles(energy(true, 2.2f), at.x, at.y, at.z, 80, 0.3, 0.5, 0.3, 0.35);
        level.sendParticles(energy(true, 1.2f), at.x, at.y, at.z, 60, 0.2, 0.3, 0.2, 0.6);
    }

    private static List<net.minecraft.world.entity.Entity> concat(List<ServerPlayer> a,
                                                                  List<net.minecraft.world.entity.Entity> b) {
        List<net.minecraft.world.entity.Entity> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static void completeRitual(ServerPlayer sp, List<ServerPlayer> around) {
        RITUAL.remove(sp.getUUID());
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        Race race = att.getRace();
        ResourceLocation first = SkillTreeNodes.firstDivineForm(race);
        if (first == null) return;
        // La primera forma divina queda COMPRADA de forma permanente (vía "ritual": gratis) y,
        // como consecuencia, God Ki.
        SkillTree.grant(sp, first, 1, SkillTree.Via.RITUAL);
        grantGodKi(sp, SkillTree.Via.RITUAL);
        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        ResourceLocation resolved = com.hmc.zenkai.feature.skills.DivineForms.resolveForPlayer(sp, first);
        if (FormAccess.usable(sp, resolved)) form.setFormId(sp, resolved);
        flash(sp.serverLevel(), sp.position().add(0, sp.getBbHeight() * 0.55, 0), isEvil(sp));
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 0.8f);
        ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.GOD_KI_RITUAL);
        for (ServerPlayer o : around) {
            o.sendSystemMessage(Component.translatable("message.zenkai.god_ki.ritual_done_helper", sp.getDisplayName())
                    .withStyle(ChatFormatting.GOLD));
        }
        PlayerLifeCycle.sync(sp);
        PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
    }

    /** Etiqueta de los muñecos que hacen de participantes en las pruebas (`/zenkai debug dummy ritual`). */
    public static final String RITUAL_DUMMY_TAG = "zenkai_ritual_dummy";

    private static List<net.minecraft.world.entity.Entity> ritualDummies(ServerPlayer sp) {
        double r = ServerConfig.godKiRitualRadius();
        return new ArrayList<>(sp.serverLevel().getEntitiesOfClass(
                com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.class, sp.getBoundingBox().inflate(r),
                d -> d.isAlive() && d.getTags().contains(RITUAL_DUMMY_TAG) && d.distanceToSqr(sp) <= r * r));
    }

    /**
     * Solo para pruebas: rodea al jugador de tantos muñecos quietos como pida el ritual. Recorren el
     * flujo REAL (progreso, partículas, completeRitual, logro) en vez del atajo de debugCompleteRitual;
     * el jugador solo tiene que cargar ki. Devuelve cuántos creó.
     */
    public static int debugSpawnRitualDummies(ServerPlayer sp) {
        int n = ServerConfig.godKiRitualParticipants();
        double ring = Math.min(3.0, Math.max(1.5, ServerConfig.godKiRitualRadius() * 0.5));
        ServerLevel level = sp.serverLevel();
        int made = 0;
        for (int i = 0; i < n; i++) {
            var shadow = com.hmc.zenkai.registry.ModEntities.SHADOW_CLONE.get().create(level);
            if (shadow == null) continue;
            double a = i * (Math.PI * 2 / n);
            shadow.moveTo(sp.getX() + Math.cos(a) * ring, sp.getY() + 0.2, sp.getZ() + Math.sin(a) * ring,
                    (float) Math.toDegrees(a) + 90f, 0f);
            shadow.setOwner(sp);
            shadow.setFlightAllowed(false);
            shadow.setDebugScript(com.hmc.zenkai.content.entity.misc.ShadowDebugMode.IDLE, null, 3, 0);
            shadow.addTag(RITUAL_DUMMY_TAG);
            level.addFreshEntity(shadow);
            made++;
        }
        return made;
    }

    /** Solo para el comando de depuración: completa el ritual sin participantes. */
    public static boolean debugCompleteRitual(ServerPlayer sp) {
        if (!ritualCandidate(sp)) return false;
        completeRitual(sp, List.of());
        return true;
    }

    /** Progreso actual del ritual de este candidato (ticks), o null. Para depurar. */
    public static @Nullable Integer ritualTicks(ServerPlayer sp) { return RITUAL.get(sp.getUUID()); }
}
