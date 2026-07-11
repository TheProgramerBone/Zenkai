package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ModDataComponents;
import com.hmc.zenkai.content.item.special.ScouterItem;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.combat.EntityStatDef;
import com.hmc.zenkai.core.combat.EntityStats;
import com.hmc.zenkai.core.combat.EntityStatsManager;
import com.hmc.zenkai.core.combat.ZenkaiStats;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: escaneo por ÁREA del scouter. Dos modos:
 *
 *  - STRONGEST: entidad con MÁS PL en scouter.range. Solo cuentan jugadores con raza y
 *    entidades con stats Zenkai (EntityStats aplicado o JSON display_only). Los mobs vanilla
 *    se ignoran (el scouter "no lee" ki sin firma). Gamerule de boosts apagado -> SIN SEÑAL.
 *
 *  - RADAR: esfera del dragón más cercana (tag zenkai:dragon_balls) en RADAR_RADIUS.
 *    Requiere la mejora (data component zenkai:radar_upgrade en el casco); sin ella responde
 *    UNAVAILABLE. Búsqueda EFICIENTE: solo chunks YA CARGADOS (getChunkNow, jamás fuerza
 *    carga síncrona) y filtro por paleta de sección (maybeHas) antes de iterar bloques —
 *    la sección de 16³ solo se recorre si su paleta contiene alguna esfera.
 *
 * Responde ScouterAreaDataPacket (posición para la flecha del cliente + PL si aplica).
 */
public record ScouterAreaScanPacket(byte mode) implements CustomPacketPayload {

    /** Valores de mode (byte propio del protocolo, NO el ordinal del enum cliente). */
    public static final byte MODE_STRONGEST = 1;
    public static final byte MODE_RADAR = 2;

    /** Radio de búsqueda de esferas (bloques). Mismo espíritu que el ítem radar (128). */
    private static final int RADAR_RADIUS = 128;

    public static final Type<ScouterAreaScanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_area_scan"));

    public static final StreamCodec<FriendlyByteBuf, ScouterAreaScanPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeByte(pkt.mode()),
                    buf -> new ScouterAreaScanPacket(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterAreaScanPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Autoritativo: sin scouter puesto no hay respuesta.
            ItemStack helmet = sp.getItemBySlot(EquipmentSlot.HEAD);
            if (!(helmet.getItem() instanceof ScouterItem)) return;

            switch (pkt.mode()) {
                case MODE_STRONGEST -> handleStrongest(sp);
                case MODE_RADAR -> handleRadar(sp, helmet);
                default -> { /* byte desconocido: ignorar */ }
            }
        });
    }

    // ------------------------------------------------------------------ STRONGEST

    private static void handleStrongest(ServerPlayer sp) {
        if (sp.getServer() == null || !ModGameRules.enableRaceBoosts(sp.getServer())) {
            reply(sp, MODE_STRONGEST, ScouterAreaDataPacket.STATUS_NONE, Vec3.ZERO, 0L);
            return;
        }

        double r = StatsConfig.scouterRange();
        AABB box = AABB.ofSize(sp.position(), r * 2, r * 2, r * 2);

        LivingEntity best = null;
        long bestPl = -1L;
        for (LivingEntity le : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != sp && e.isAlive() && !e.isSpectator())) {
            long pl = zenkaiPowerLevel(le);
            if (pl > bestPl) {
                bestPl = pl;
                best = le;
            }
        }

        if (best != null) {
            reply(sp, MODE_STRONGEST, ScouterAreaDataPacket.STATUS_FOUND,
                    best.position().add(0, best.getBbHeight() * 0.5, 0), bestPl);
        } else {
            reply(sp, MODE_STRONGEST, ScouterAreaDataPacket.STATUS_NONE, Vec3.ZERO, 0L);
        }
    }

    /**
     * PL con firma Zenkai, o -1 si la entidad no cuenta para el modo (mobs vanilla y
     * jugadores sin raza se ignoran).
     */
    private static long zenkaiPowerLevel(LivingEntity le) {
        if (le instanceof Player p) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
            return att.isRaceChosen() ? att.getPowerLevel() : -1L;
        }
        EntityStats stats = ZenkaiStats.entityStats(le);
        if (stats != null) return stats.getPowerLevel();

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
        EntityStatDef def = EntityStatsManager.get(id);
        if (def != null && def.displayOnly()) return def.powerLevel();

        return -1L;
    }

    // ------------------------------------------------------------------ RADAR

    private static void handleRadar(ServerPlayer sp, ItemStack helmet) {
        if (!Boolean.TRUE.equals(helmet.get(ModDataComponents.RADAR_UPGRADE.get()))) {
            reply(sp, MODE_RADAR, ScouterAreaDataPacket.STATUS_UNAVAILABLE, Vec3.ZERO, 0L);
            return;
        }

        BlockPos nearest = findNearestDragonBall(sp.serverLevel(), sp.blockPosition());
        if (nearest != null) {
            reply(sp, MODE_RADAR, ScouterAreaDataPacket.STATUS_FOUND, Vec3.atCenterOf(nearest), 0L);
        } else {
            reply(sp, MODE_RADAR, ScouterAreaDataPacket.STATUS_NONE, Vec3.ZERO, 0L);
        }
    }

    /**
     * Esfera más cercana en RADAR_RADIUS, SOLO en chunks cargados. Por chunk: descarta
     * secciones cuya paleta no contiene esferas (maybeHas = barato); solo las que sí,
     * se iteran bloque a bloque. Poda: chunks cuya esquina más cercana ya queda más lejos
     * que el mejor hallazgo se saltan.
     */
    private static BlockPos findNearestDragonBall(ServerLevel level, BlockPos origin) {
        int minCx = (origin.getX() - RADAR_RADIUS) >> 4, maxCx = (origin.getX() + RADAR_RADIUS) >> 4;
        int minCz = (origin.getZ() - RADAR_RADIUS) >> 4, maxCz = (origin.getZ() + RADAR_RADIUS) >> 4;

        BlockPos nearest = null;
        double bestSqr = (double) RADAR_RADIUS * RADAR_RADIUS;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // ⚠ nunca carga
                if (chunk == null) continue;

                // Poda horizontal: distancia mínima posible de este chunk al origen.
                double dx = axisGap(origin.getX(), cx << 4, (cx << 4) + 15);
                double dz = axisGap(origin.getZ(), cz << 4, (cz << 4) + 15);
                if (dx * dx + dz * dz > bestSqr) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(s -> s.is(ModTags.Blocks.DRAGON_BALLS))) continue; // paleta

                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!sec.getBlockState(x, y, z).is(ModTags.Blocks.DRAGON_BALLS)) continue;
                                BlockPos p = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                double d = origin.distSqr(p);
                                if (d < bestSqr) {
                                    bestSqr = d;
                                    nearest = p;
                                }
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /** Distancia de v al intervalo [lo, hi] (0 si está dentro). */
    private static double axisGap(int v, int lo, int hi) {
        if (v < lo) return lo - v;
        if (v > hi) return v - hi;
        return 0;
    }

    private static void reply(ServerPlayer sp, byte mode, byte status, Vec3 pos, long pl) {
        PacketDistributor.sendToPlayer(sp,
                new ScouterAreaDataPacket(mode, status, pos.x, pos.y, pos.z, pl));
    }
}