package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.technique.AbsorbMassEntity;
import com.hmc.zenkai.event.tick.TickCtx;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStateFlags;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Absorción del Majin (diseño aceptado 2026-10-24, {@code .claude/pendiente/vision-y-backlog.md}): la
 * técnica INNATA de la raza ({@code PhysicalTechnique.ABSORB}, {@code innate_race: majin}), sin comprarla y sin tecla
 * propia: se enlaza a una casilla de combate como cualquier técnica física. El Majin ARROJA una masa del color de su
 * piel ({@link AbsorbMassEntity}); la primera criatura que toca se evalúa AL IMPACTO ({@link #onMassHit}) y, si cumple
 * la regla, es absorbida y suma MASA. Alcance, estamina y enfriamiento son los del JSON de la técnica
 * (physical/absorb.json); además cuesta un % del ki máximo ({@code majin.absorb_ki_cost_percent}).
 *
 * REGLA de absorber (todo en ServerConfig, {@code majin.*}), con el PL del Majin frente al del objetivo:
 *  - DOMINADO: el Majin tiene {@code absorb_dominant_pl_ratio} veces el PL del objetivo o más -> se absorbe aunque esté
 *    sano.
 *  - Si no, el objetivo debe estar por debajo de {@code absorb_max_health_percent} de su vida Y su PL no puede pasar de
 *    {@code absorb_max_pl_ratio} veces el del Majin.
 * Mobs siempre; jugadores solo con {@code majin.absorb_pvp}.
 *
 * MASA: pilas TEMPORALES (cada una suma {@code mass_stat_per_stack} al multiplicador de stats, aditivo en
 * MasteryEffects.formStatFactor), con tope, que caducan juntas al pasar {@code mass_seconds} desde la
 * ÚLTIMA absorción. Cada absorción deja además una parte PERMANENTE muy pequeña y con techo
 * ({@code mass_permanent_*}) para que absorber siga importando fuera de la pelea.
 *
 * LA CADENA DE FORMAS decide cómo se comporta la masa (el eje de §4 del diseño archivado, .claude/archivo/2026-10-25-skill-tree-cerrado/skill-tree.md):
 *  - Masa Voraz ({@code majin_super}): tope + {@code voraz_extra_cap}.
 *  - Majin Saciado ({@code majin_harmony}) RETIENE: la masa dura {@code saciado_duration_mult} veces más.
 *  - Majin Primitivo ({@code majin_pure_chaos}) SUELTA: cada pila rinde {@code primitivo_bonus_mult} veces
 *    más, pero caduca en {@code primitivo_duration_mult} veces el tiempo.
 *  - Majin Primigenio ({@code majin_god}) la USA SIN PERDERLA: mientras lleva esa forma la masa no caduca.
 * El estado vive en PlayerStateFlags (save()/load() es también el transporte de red, así que el cliente ve lo mismo y
 * {@link #statBonus} se puede llamar en ambos lados).
 */
public final class MajinAbsorption {
    private MajinAbsorption() {}

    private static final ResourceLocation VORAZ = id("majin_super");
    private static final ResourceLocation SACIADO = id("majin_harmony");
    private static final ResourceLocation PRIMITIVO = id("majin_pure_chaos");
    private static final ResourceLocation PRIMIGENIO = id("majin_god");
    /** «Majin Devorador»: la forma Legendaria del Majin (LegendaryForms). */
    private static final ResourceLocation DEVORADOR = id("majin_legendary");

    /** Velocidad de la masa en bloques por tick. */
    private static final double MASS_SPEED = 0.9;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    private static ResourceLocation formOf(Player p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
    }

    // ── Reglas de masa por forma ─────────────────────────────────────────────

    public static int massCap(ResourceLocation form) {
        int cap = ServerConfig.majinMassCap();
        if (VORAZ.equals(form)) cap += ServerConfig.majinVorazExtraCap();
        if (DEVORADOR.equals(form)) cap += ServerConfig.majinLegendExtraCap();
        return cap;
    }

    private static double durationTicks(ResourceLocation form) {
        double s = ServerConfig.majinMassSeconds();
        if (SACIADO.equals(form)) s *= ServerConfig.majinSaciadoDurationMult();
        if (PRIMITIVO.equals(form)) s *= ServerConfig.majinPrimitivoDurationMult();
        if (DEVORADOR.equals(form)) s *= ServerConfig.majinLegendDurationMult();
        return s * 20.0;
    }

    private static double perStack(ResourceLocation form) {
        double v = ServerConfig.majinMassStatPerStack();
        if (PRIMITIVO.equals(form)) v *= ServerConfig.majinPrimitivoBonusMult();
        return v;
    }

    /** ¿La masa vive indefinidamente con esta forma puesta? (Majin Primigenio). */
    private static boolean keepsMass(ResourceLocation form) {
        return PRIMIGENIO.equals(form);
    }

    // ── Bono de stats (cliente y servidor) ───────────────────────────────────

    /** Pilas de masa VIVAS ahora mismo (0 si caducaron aunque el tick del servidor aún no las limpiara). */
    public static int liveStacks(Player p) {
        PlayerStateFlags f = PlayerStatsAttachment.get(p).flags();
        int stacks = f.getAbsorbMass();
        if (stacks <= 0) return 0;
        if (keepsMass(formOf(p))) return stacks;
        return p.level().getGameTime() <= f.getAbsorbMassUntil() ? stacks : 0;
    }

    /** Bono ADITIVO de stats (masa temporal + parte permanente), sumado en MasteryEffects.formStatFactor. */
    public static double statBonus(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.getRace() != Race.MAJIN) return 0.0;
        double bonus = 0.0;
        int stacks = Math.min(liveStacks(p), massCap(formOf(p)));
        if (stacks > 0) bonus += stacks * perStack(formOf(p));
        // Poder robado con «Majin Devorador»: solo cuenta mientras lleva la forma y la masa sigue viva.
        if (stacks > 0 && DEVORADOR.equals(formOf(p))) bonus += att.flags().getAbsorbStolen();
        bonus += Math.min(ServerConfig.majinMassPermanentCap(),
                att.flags().getAbsorbedTotal() * ServerConfig.majinMassPermanentPerAbsorb());
        return bonus;
    }

    // ── Tick: caducar la masa ────────────────────────────────────────────────

    public static void tick(TickCtx c) {
        PlayerStatsAttachment att = c.att();
        if (att.getRace() != Race.MAJIN) return;
        PlayerStateFlags f = att.flags();
        if (f.getAbsorbMass() <= 0) return;
        ResourceLocation form = c.form().getFormId();
        if (keepsMass(form)) {
            // Congelada: el reloj no corre. Al salir de la forma vuelve a contar el tiempo que ya tenía.
            f.setAbsorbMassUntil(f.getAbsorbMassUntil() + 1);
            return;
        }
        if (c.p().level().getGameTime() > f.getAbsorbMassUntil()) {
            f.setAbsorbMass(0);
            f.setAbsorbStolen(0.0);
            if (c.p() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("messages.zenkai.absorb.mass_gone"), true);
            }
        }
    }

    // ── Lanzar la masa ───────────────────────────────────────────────────────

    /**
     * Comprobación PREVIA de la técnica (PhysicalCombatServer.checkStart, antes de gastar estamina o enfriamiento):
     * ser Majin y tener el ki que cuesta. Si falla avisa en la action bar. A QUIÉN se puede absorber se decide al
     * IMPACTO, no aquí: la masa se arroja y vuela.
     */
    public static boolean canThrow(ServerPlayer sp) {
        if (!ServerConfig.majinAbsorbEnabled()) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return false;
        if (att.getRace() != Race.MAJIN) {
            sp.displayClientMessage(Component.translatable("messages.zenkai.absorb.not_majin"), true);
            return false;
        }
        if (att.getEnergy() < kiCost(att)) {
            sp.displayClientMessage(Component.translatable("messages.zenkai.absorb.no_ki"), true);
            return false;
        }
        return true;
    }

    private static int kiCost(PlayerStatsAttachment att) {
        return (int) Math.round(att.getEnergyMax() * ServerConfig.majinAbsorbKiCostPct() / 100.0);
    }

    /** Color de la masa: la piel del Majin (el tono que haya elegido). */
    public static int massColor(ServerPlayer sp) {
        return PlayerVisualAttachment.get(sp).getSkinColorRgb() & 0xFFFFFF;
    }

    /**
     * Ejecución de la técnica (PhysicalCombatServer.execute, case ABSORB), ya pagada su estamina y puesto su
     * enfriamiento: cobra el ki y arroja la masa hacia donde mira.
     */
    public static void throwMass(ServerPlayer sp) {
        if (!canThrow(sp)) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.consumeEnergy(kiCost(att));
        double range = PhysicalTechnique.ABSORB.range();
        Vec3 look = sp.getLookAngle();
        AbsorbMassEntity mass = new AbsorbMassEntity(ModEntities.ABSORB_MASS.get(), sp.level());
        mass.configure(sp, (int) Math.ceil(range / MASS_SPEED), look.scale(MASS_SPEED), massColor(sp));
        Vec3 start = sp.getEyePosition().add(look.scale(0.8)).add(0, -0.45, 0);
        mass.setPos(start.x, start.y, start.z);
        sp.level().addFreshEntity(mass);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    // ── Impacto ──────────────────────────────────────────────────────────────

    /** Por qué NO se puede absorber a {@code target} (clave de lang) o null si SÍ. */
    public static String refusal(ServerPlayer owner, LivingEntity target) {
        if (target instanceof ServerPlayer && !ServerConfig.majinAbsorbPvp()) return "messages.zenkai.absorb.pvp_off";
        long myPl = Math.max(1L, PlayerStatsAttachment.get(owner).getPowerLevel());
        long theirPl = powerLevelOf(target);
        // DOMINADO: el Majin le saca suficiente ventaja y se lo traga aunque esté sano.
        if (myPl >= theirPl * ServerConfig.majinAbsorbDominantPlRatio()) return null;
        if (theirPl > myPl * ServerConfig.majinAbsorbMaxPlRatio()) return "messages.zenkai.absorb.too_strong";
        if (target.getHealth() > target.getMaxHealth() * ServerConfig.majinAbsorbMaxHealthPct() / 100.0) {
            return "messages.zenkai.absorb.too_healthy";
        }
        return null;
    }

    /** La masa alcanzó a {@code target} (solo servidor, llamado por AbsorbMassEntity). @return true si lo absorbió. */
    public static boolean onMassHit(ServerPlayer owner, LivingEntity target) {
        String refused = refusal(owner, target);
        if (refused != null) {
            owner.displayClientMessage(Component.translatable(refused), true);
            impactFx(owner.serverLevel(), target, massColor(owner), false);
            return false;
        }
        // Gancho de addons: cancelable, antes de matar al objetivo (ver MajinAbsorbEvent).
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.hmc.zenkai.api.event.MajinAbsorbEvent(owner, target)).isCanceled()) {
            impactFx(owner.serverLevel(), target, massColor(owner), false);
            return false;
        }
        PlayerStatsAttachment att = PlayerStatsAttachment.get(owner);
        long now = owner.level().getGameTime();
        // PL del objetivo ANTES de matarlo: lo que «Majin Devorador» le roba depende de cuánto pesa frente al Majin.
        double plRatio = (double) powerLevelOf(target) / Math.max(1L, att.getPowerLevel());

        impactFx(owner.serverLevel(), target, massColor(owner), true);
        // Muere como si lo hubiera matado el Majin (TP y botín normales); si algo lo salva, se fuerza.
        target.hurt(owner.damageSources().playerAttack(owner), Float.MAX_VALUE);
        if (target.isAlive()) target.kill();

        ResourceLocation form = formOf(owner);
        PlayerStateFlags f = att.flags();
        f.setAbsorbMass(Math.min(massCap(form), f.getAbsorbMass() + 1));
        f.setAbsorbMassUntil(now + Math.round(durationTicks(form)));
        f.setAbsorbedTotal(f.getAbsorbedTotal() + 1);
        if (DEVORADOR.equals(form)) {
            // Roba un % del poder del objetivo como stats temporales y recupera ki (alarga la forma).
            double steal = Math.min(ServerConfig.majinLegendStealPerAbsorbMax(), plRatio * ServerConfig.majinLegendStealRatio());
            f.setAbsorbStolen(Math.min(ServerConfig.majinLegendStealCap(), f.getAbsorbStolen() + steal));
            att.addEnergy((int) Math.round(att.getEnergyMax() * ServerConfig.majinLegendKiRefundPct() / 100.0));
        }
        owner.displayClientMessage(Component.translatable("messages.zenkai.absorb.done",
                f.getAbsorbMass(), massCap(form)), true);
        PlayerLifeCycle.sync(owner);
        return true;
    }

    /** PL del objetivo: el de su EntityStats si lo tiene, el de un jugador, o su vida máxima por el factor vainilla. */
    private static long powerLevelOf(LivingEntity e) {
        if (e instanceof ServerPlayer other) return Math.max(1L, PlayerStatsAttachment.get(other).getPowerLevel());
        if (e.hasData(ZenkaiDataAttachments.ENTITY_STATS.get())) {
            EntityStats st = e.getData(ZenkaiDataAttachments.ENTITY_STATS.get());
            if (st.isInitialized()) return st.getPowerLevel();
        }
        return Math.max(1L, Math.round(e.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor()));
    }

    /** Estallido de masa sobre el objetivo: se traga (absorbido) o salpica y se disuelve (rechazado). */
    private static void impactFx(ServerLevel level, LivingEntity target, int rgb, boolean absorbed) {
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        DustParticleOptions dust = new DustParticleOptions(color, 1.6f);
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.sendParticles(dust, c.x, c.y, c.z, absorbed ? 36 : 14, 0.4, 0.5, 0.4, absorbed ? 0.04 : 0.02);
        level.playSound(null, target.blockPosition(), absorbed ? SoundEvents.HONEY_BLOCK_SLIDE : SoundEvents.SLIME_BLOCK_HIT,
                SoundSource.PLAYERS, 1.0f, absorbed ? 0.5f : 0.8f);
    }
}
