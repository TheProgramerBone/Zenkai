package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.*;
import com.hmc.zenkai.feature.combat.entity.EntityStatDef;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.combat.entity.EntityStatsManager;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModTags;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.Mob;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * C2S: "escanea mi entorno" (sentir el ki). El cliente lo manda cada 10 ticks mientras el modo
 * no sea OFF. El servidor responde con un SenseKiDataPacket con lo que hay en rango (el
 * filtrado por MODO —todos/jugadores/criaturas/fijado— es del cliente; el de NIVEL es de aquí).
 *
 * PL de cada entidad:
 *  - Jugador con raza / entidad con stats -> su PL real (y su body real).
 *  - Entidad con JSON display_only -> PL fijo del JSON; vida = la vanilla.
 *  - Mob vanilla / jugador sin raza -> PL = vida_max × factor (config); vida = la vanilla.
 *
 * LO QUE VE CADA NIVEL se decide aquí y no en el cliente: si viajara siempre, ocultarlo allí sería
 * un gate de mentira. Nivel 1: posición y fuerza relativa; 2: vida en cuatro estados y alineamiento;
 * 3: vida continua, stamina y ki; 4: bits de estado (jefe, te fija…); 5: el objetivo fijado con vida
 * exacta y ki/stamina por cuartos (SenseKiDataPacket.TargetReading). El PL no viaja: solo su
 * categoría relativa a quien siente.
 */
public record SenseKiScanPacket() implements CustomPacketPayload {
    public static final Type<SenseKiScanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sense_ki_scan"));

    public static final StreamCodec<FriendlyByteBuf, SenseKiScanPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new SenseKiScanPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SenseKiScanPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Deja constancia de que este jugador tiene el sense encendido: es lo que
            // permite avisarle si alguien lo fija con el lock-on.
            // Sin la habilidad no se escanea, pase lo que pase en el cliente.
            if (SkillEffects.senseLevel(sp) <= 0) return;
            SenseServerState.markScan(sp);

            // Rango del nivel (64 × 1,0..2,2), pero nunca más allá de la distancia de visión del
            // servidor: fuera de ella no hay chunks cargados y prometer más sería mentir.
            double r = Math.min(ServerConfig.senseKiRange() * SkillEffects.senseRangeFactor(sp),
                    sp.server.getPlayerList().getViewDistance() * 16.0);
            double r2 = r * r;
            AABB box = AABB.ofSize(sp.position(), r * 2, r * 2, r * 2);

            int level = SkillEffects.senseLevel(sp);
            LivingEntity locked = SenseServerState.lockedTarget(sp);
            long myPl = ownPowerLevel(sp);

            List<LivingEntity> found = sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != sp && e.isAlive() && !e.isSpectator() && e.distanceToSqr(sp) <= r2);
            // El techo del paquete no puede cortar por orden de llegada: primero el fijado y quien
            // te fija, después el resto por distancia (un jefe lejano no se pierde tras 128 pollos).
            found.sort(Comparator.comparingDouble(e -> e == locked ? -2.0
                    : targetsYou(e, sp) ? -1.0 : e.distanceToSqr(sp)));

            List<SenseKiDataPacket.Entry> out = new ArrayList<>();
            SenseKiDataPacket.TargetReading reading = null;
            for (LivingEntity le : found) {
                Raw raw = read(le);
                out.add(entryOf(le, raw, level, myPl, sp));
                if (le == locked && level >= SENSE_READING_LEVEL
                        && !out.get(out.size() - 1).has(SenseKiDataPacket.FLAG_DIVINE_UNKNOWN)) {
                    reading = readingOf(le, raw);
                }
                if (out.size() >= MAX_ENTRIES) break;
            }
            PacketDistributor.sendToPlayer(sp, new SenseKiDataPacket(level, out, reading));
        });
    }

    /** Techo de seguridad del paquete. */
    private static final int MAX_ENTRIES = 128;

    /** ¿Esta entidad tiene fijado a {@code sp}? Lock-on de un jugador u objetivo de un mob. */
    private static boolean targetsYou(LivingEntity le, ServerPlayer sp) {
        if (le instanceof ServerPlayer other) return SenseServerState.lockOf(other) == sp.getId();
        return le instanceof Mob m && m.getTarget() == sp;
    }

    /** PL REAL de quien siente (su propio PL no se lo esconde a sí mismo). Misma escala que read(). */
    private static long ownPowerLevel(ServerPlayer sp) {
        if (sp.getServer() != null && ModGameRules.enableRaceBoosts(sp.getServer())) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (att.isRaceChosen()) return att.getPowerLevel();
        }
        return Math.round(sp.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor());
    }

    // Cortes de la fuerza relativa (PL aparente del otro / tu PL).
    private static final double FAINT_BELOW = 0.25;
    private static final double INFERIOR_BELOW = 0.67;
    private static final double COMPARABLE_UP_TO = 1.5;
    private static final double SUPERIOR_BELOW = 4.0;

    /** Categoría de fuerza relativa: lo único del PL que sale del servidor hacia el Ki Sense. */
    private static int strengthOf(long pl, long myPl) {
        if (pl <= 0 || myPl <= 0) return SenseKiDataPacket.STRENGTH_COMPARABLE;
        double ratio = (double) pl / myPl;
        if (ratio < FAINT_BELOW) return SenseKiDataPacket.STRENGTH_FAINT;
        if (ratio < INFERIOR_BELOW) return SenseKiDataPacket.STRENGTH_INFERIOR;
        if (ratio <= COMPARABLE_UP_TO) return SenseKiDataPacket.STRENGTH_COMPARABLE;
        if (ratio < SUPERIOR_BELOW) return SenseKiDataPacket.STRENGTH_SUPERIOR;
        return SenseKiDataPacket.STRENGTH_OVERWHELMING;
    }

    /** Nivel a partir del cual llegan la vida (en cuatro estados) y el alineamiento aparente. */
    private static final int SENSE_HEALTH_LEVEL = 2;
    /** Nivel a partir del cual la vida es continua y llegan stamina y ki de quien los tenga. */
    private static final int SENSE_BARS_LEVEL = 3;
    /** Nivel del Ki Sense a partir del cual llegan los bits de estado (cargando, transformado…). */
    private static final int SENSE_STATE_LEVEL = 4;
    /** Nivel a partir del cual el objetivo fijado llega con vida exacta (la única cifra del sentido). */
    private static final int SENSE_READING_LEVEL = 5;

    /** Lectura cruda de una entidad, antes de decidir qué parte puede ver cada nivel. */
    private record Raw(int body, int bodyMax, int alignment, long powerLevel, boolean isPlayer,
                       float kiFrac, float staminaFrac, int flags) {}

    /**
     * Filtra la lectura cruda por nivel ANTES de serializar: lo que el nivel no autoriza sale como
     * -1 / NO_ALIGNMENT / bit a cero, nunca como dato real que el cliente tenga que esconder.
     */
    private static SenseKiDataPacket.Entry entryOf(LivingEntity le, Raw raw, int level, long myPl, ServerPlayer sp) {
        // Ki divino (skill tree F5, §5): sin God Ki no se lee. La firma no desaparece (sigue ahí,
        // «algo» está), pero llega como desconocida y tenue: ni fuerza, ni vida, ni estados.
        if (hasDivineKi(le) && !com.hmc.zenkai.feature.skilltree.SkillTree.hasGodKi(sp)) {
            return new SenseKiDataPacket.Entry(le.getId(), (float) le.getX(), (float) le.getY(), (float) le.getZ(),
                    le.getBbHeight(), le instanceof Player, SenseKiDataPacket.STRENGTH_FAINT,
                    SenseKiDataPacket.NO_ALIGNMENT, -1, -1, -1, SenseKiDataPacket.FLAG_DIVINE_UNKNOWN);
        }
        int health = -1;
        int alignment = SenseKiDataPacket.NO_ALIGNMENT;
        if (level >= SENSE_HEALTH_LEVEL) {
            float f = raw.bodyMax() <= 0 ? 1f : Math.max(0f, Math.min(1f, raw.body() / (float) raw.bodyMax()));
            // Nivel 2: cuatro estados, cuantizados AQUÍ. Hacia arriba: algo vivo nunca se lee como 0.
            if (level < SENSE_BARS_LEVEL) f = Math.max(1, (int) Math.ceil(f * 4f)) / 4f;
            health = Math.round(f * 1000f);
            alignment = Math.max(-100, Math.min(100, raw.alignment()));
        }
        int flags = 0;
        if (level >= SENSE_STATE_LEVEL) {
            flags = raw.flags();
            if (le.getType().is(ModTags.EntityTypes.BOSSES)) flags |= SenseKiDataPacket.FLAG_BOSS;
            if (targetsYou(le, sp)) flags |= SenseKiDataPacket.FLAG_TARGETING_YOU;
        }
        boolean bars = level >= SENSE_BARS_LEVEL;
        int sta = bars && raw.staminaFrac() >= 0f ? Math.round(raw.staminaFrac() * 1000f) : -1;
        int ki = bars && raw.kiFrac() >= 0f ? Math.round(raw.kiFrac() * 1000f) : -1;
        return new SenseKiDataPacket.Entry(le.getId(), (float) le.getX(), (float) le.getY(), (float) le.getZ(),
                le.getBbHeight(), raw.isPlayer(), strengthOf(raw.powerLevel(), myPl), alignment,
                health, sta, ki, flags);
    }

    /** ¿Irradia ki divino? Jugadores con God Ki (o divinidades con cargo) y los mobs del tag zenkai:divine. */
    private static boolean hasDivineKi(LivingEntity le) {
        if (le instanceof Player p) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
            return att.isDivine() || com.hmc.zenkai.feature.skilltree.SkillTree.hasGodKi(p);
        }
        return le.getType().is(ModTags.EntityTypes.DIVINE);
    }

    private static SenseKiDataPacket.TargetReading readingOf(LivingEntity le, Raw raw) {
        return new SenseKiDataPacket.TargetReading(le.getId(), raw.body(), raw.bodyMax(),
                tier(raw.kiFrac()), tier(raw.staminaFrac()));
    }

    /** Cuartos de una fracción 0..1: 0 vacío, 1..4 por tramos. -1 si no aplica (fracción negativa). */
    private static int tier(float frac) {
        if (frac < 0f) return -1;
        return frac <= 0f ? 0 : Math.min(4, (int) Math.ceil(frac * 4f));
    }

    private static Raw read(LivingEntity le) {
        boolean isPlayer = le instanceof Player;

        // Gamerule de la capa Zenkai apagado: el combate va por vida vanilla (los pools quedan
        // congelados), asi que se reporta vanilla, igual que el resto del sistema.
        if (le.getServer() == null || !ModGameRules.enableRaceBoosts(le.getServer())) {
            return vanillaRaw(le, isPlayer);
        }

        // Jugador con raza: stats reales.
        if (le instanceof Player p) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
            if (att.isRaceChosen()) {
                int flags = 0;
                if (att.flags().isChargingKi()) flags |= SenseKiDataPacket.FLAG_CHARGING;
                var form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
                if (form.isTransforming()) flags |= SenseKiDataPacket.FLAG_TRANSFORMING;
                if (!FormIds.BASE.equals(form.getFormId()) || form.getKaioken() != KaiokenTier.OFF) {
                    flags |= SenseKiDataPacket.FLAG_TRANSFORMED;
                }
                // Suprime el ki: muestra menos que el techo que podría liberar hoy.
                if (att.getPowerPercent() < Math.min(100, SkillEffects.maxPowerPercent(att))) {
                    flags |= SenseKiDataPacket.FLAG_SUPPRESSING;
                }
                if (InCombatState.isInCombat(p)) flags |= SenseKiDataPacket.FLAG_IN_COMBAT;
                return new Raw(att.getBody(), att.getBodyMax(), att.getAlignment(),
                        att.getApparentPowerLevel(), true,
                        frac(att.getEnergy(), att.getEnergyMax()),
                        frac(att.getStamina(), att.getStaminaMax()), flags);
            }
            return vanillaRaw(le, true);
        }

        // Muñeco de depuración: ningún mob tiene ki ni stamina, así que se inventan unos que oscilan
        // y unos estados que se encienden y apagan solapados, solo para poder ver los arcos y los iconos del nivel 3-4.
        if (le.getTags().contains(com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.DEBUG_DUMMY_TAG)) {
            long t = le.level().getGameTime();
            float ki = 0.5f + 0.5f * (float) Math.sin(t * 0.05);
            float sta = 0.5f + 0.5f * (float) Math.sin(t * 0.03 + 2.0);
            int flags = SenseKiDataPacket.FLAG_IN_COMBAT;
            // Cada estado con su propio periodo, para que se solapen (en un jugador real son bits independientes).
            if (t / 60 % 2 == 0) flags |= SenseKiDataPacket.FLAG_CHARGING;
            if (t / 90 % 2 == 0) flags |= SenseKiDataPacket.FLAG_TRANSFORMED;
            if (t / 110 % 3 == 0) flags |= SenseKiDataPacket.FLAG_TRANSFORMING;
            if (t / 140 % 2 == 0) flags |= SenseKiDataPacket.FLAG_SUPPRESSING;
            return new Raw(Math.round(le.getHealth()), Math.round(le.getMaxHealth()), entityAlignment(le),
                    vanillaRaw(le, false).powerLevel(), false, ki, sta, flags);
        }

        // Entidad con stats de combate resueltos.
        EntityStats stats = ZenkaiStats.entityStats(le);
        if (stats != null) {
            return new Raw(stats.getBody(), stats.getBodyMax(), entityAlignment(le),
                    stats.getApparentPowerLevel(), false, -1f, -1f, mobFlags(le));
        }

        // JSON display_only: PL fijo, vida vanilla.
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
        EntityStatDef def = EntityStatsManager.get(id);
        if (def != null && def.displayOnly()) {
            return new Raw(Math.round(le.getHealth()), Math.round(le.getMaxHealth()),
                    entityAlignment(le), def.powerLevel(), false, -1f, -1f, mobFlags(le));
        }

        return vanillaRaw(le, false);
    }

    private static Raw vanillaRaw(LivingEntity le, boolean isPlayer) {
        long pl = Math.round(le.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor());
        return new Raw(Math.round(le.getHealth()), Math.round(le.getMaxHealth()),
                entityAlignment(le), pl, isPlayer, -1f, -1f, isPlayer ? 0 : mobFlags(le));
    }

    /** De un mob solo se sabe si está en combate: tiene un objetivo al que ataca. */
    private static int mobFlags(LivingEntity le) {
        int flags = le instanceof Mob m && m.getTarget() != null ? SenseKiDataPacket.FLAG_IN_COMBAT : 0;
        if (le instanceof com.hmc.zenkai.content.entity.ZenkaiDefaultMob zm && zm.isTransforming()) {
            flags |= SenseKiDataPacket.FLAG_TRANSFORMING;
        }
        EntityStats stats = ZenkaiStats.entityStats(le);
        if (stats != null && stats.isTransformed()) flags |= SenseKiDataPacket.FLAG_TRANSFORMED;
        return flags;
    }

    private static float frac(int cur, int max) {
        return max <= 0 ? -1f : Math.max(0f, Math.min(1f, cur / (float) max));
    }

    /**
     * Alineamiento de una entidad no jugadora, -100..+100.
     * Lo manda el datapack (zenkai_entities). El heurístico de hostilidad es solo el respaldo
     * para lo que no tenga ficha: daba exactamente dos colores para el bestiario, y la
     * paleta tiene doscientas posiciones.
     */
    private static int entityAlignment(LivingEntity le) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
        EntityStatDef def = EntityStatsManager.get(id);
        if (def != null && def.hasAlignment()) return def.alignment();
        return le instanceof net.minecraft.world.entity.monster.Enemy ? -60 : 0;
    }
}