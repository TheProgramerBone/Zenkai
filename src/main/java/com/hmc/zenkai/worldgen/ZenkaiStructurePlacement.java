package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import com.hmc.zenkai.worldgen.npc.StructureNpcManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Colocación de estructuras únicas (no aleatorias):
 *  - Kami: una vez en el overworld al arrancar el servidor.
 *  - Otherworld: una vez, justo antes de mandar al primer jugador allí
 *    (ensureOtherworldPalace, llamado desde OtherworldManager).
 * El flag de "ya colocada" vive en ZenkaiWorldData (una vez por mundo).
 * Los NPC de estructura (Yemma, etc.) los gestiona StructureNpcManager.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ZenkaiStructurePlacement {
    private ZenkaiStructurePlacement() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Structures");

    public static final String KEY_KAMI       = "kami_palace";
    public static final String KEY_OTHERWORLD = "otherworld_palace";
    public static final String KEY_HTC        = "htc_structure";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        ZenkaiWorldData data = ZenkaiWorldData.get(server);

        // Base de Kami: usa la guardada; si no, busca el bioma objetivo dentro del
        // radio alrededor del spawn (garantiza bioma + cercanía). Fallback: spawn.
        BlockPos kamiBase = data.getPos(KEY_KAMI);
        if (kamiBase == null) kamiBase = findKamiBase(overworld);

        // Colocar Kami una sola vez y recordar dónde quedó.
        if (!data.isPlaced(KEY_KAMI)) {
            if (StaticStructurePlacer.place(overworld, kamiBase, ModStructureSegments.KAMI, true)) {
                data.markPlaced(KEY_KAMI);
                data.setPos(KEY_KAMI, kamiBase);
            }
        }

        // Registrar zonas anti-hostiles cada arranque (la lista es en memoria).
        NoHostileSpawnZones.clear();
        BlockPos kamiZoneMin = kamiBase.offset(
                ModStructureSegments.KAMI_NO_SPAWN_OFF_X,
                ModStructureSegments.KAMI_NO_SPAWN_OFF_Y,
                ModStructureSegments.KAMI_NO_SPAWN_OFF_Z);
        NoHostileSpawnZones.addFromBase(Level.OVERWORLD, kamiZoneMin,
                ModStructureSegments.KAMI_NO_SPAWN_SX,
                ModStructureSegments.KAMI_NO_SPAWN_SY,
                ModStructureSegments.KAMI_NO_SPAWN_SZ,
                "protector.zenkai.kami");
        NoHostileSpawnZones.addFromBase(ModDimensions.OTHERWORLD_LEVEL,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_MIN,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SX,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SY,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SZ,
                "protector.zenkai.yemma");
        NoHostileSpawnZones.addFromBase(ModDimensions.HTC_LEVEL,
                ModStructureSegments.HTC_NO_SPAWN_MIN,
                ModStructureSegments.HTC_NO_SPAWN_SX,
                ModStructureSegments.HTC_NO_SPAWN_SY,
                ModStructureSegments.HTC_NO_SPAWN_SZ,
                "protector.zenkai.htc");
    }

    /**
     * Sistema único de búsqueda del sitio de Kami:
     * 1) Recorre un anillo de puntos a >= KAMI_MIN_DIST_FROM_SPAWN del spawn y busca el bioma
     *    objetivo cerca de cada uno (así garantizamos distancia mínima Y bioma).
     * 2) Alrededor del punto del bioma, barre candidatos buscando un pad KAMI_PAD_SIZE² de
     *    césped EXACTAMENTE plano, con dirt debajo, a altura KAMI_PAD_MIN_Y..KAMI_PAD_MAX_Y.
     * 3) Ancla la base a la superficie de ese pad (+ KAMI_Y_OFFSET).
     * Las alturas se leen forzando la generación del chunk (level.getChunk), porque
     * Level.getHeight devuelve la altura mínima del mundo (bedrock) en chunks no generados
     * — ese era el bug del anclaje a superficie.
     */
    private static BlockPos findKamiBase(ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        int minDist = ModStructureSegments.KAMI_MIN_DIST_FROM_SPAWN;

        for (int ring = 0; ring < 3; ring++) {
            int dist = minDist + ModStructureSegments.KAMI_SITE_SEARCH_RADIUS + 64 + ring * 1000;
            for (int d = 0; d < 8; d++) {
                double ang = Math.PI * 2.0 * d / 8.0;
                BlockPos center = spawn.offset(
                        (int) Math.round(Math.cos(ang) * dist), 0,
                        (int) Math.round(Math.sin(ang) * dist));

                var found = overworld.findClosestBiome3d(
                        h -> h.is(ModStructureSegments.KAMI_BIOME), center,
                        ModStructureSegments.KAMI_BIOME_SEARCH_RADIUS, 32, 64);
                if (found == null) continue;

                BlockPos site = findGoodPad(overworld, found.getFirst(), spawn);
                if (site != null) {
                    LOGGER.info("[Zenkai] Kami: sitio válido en {} {} {} ({} bloques del spawn).",
                            site.getX(), site.getY(), site.getZ(),
                            (int) Math.sqrt(spawn.distSqr(site)));
                    return site.offset(0, ModStructureSegments.KAMI_Y_OFFSET, 0);
                }
                LOGGER.info("[Zenkai] Kami: bioma hallado cerca de {},{} pero sin pad válido; probando otra dirección.",
                        center.getX(), center.getZ());
            }
        }

        // Fallback muy improbable: superficie real (chunk generado) a distancia mínima del spawn.
        LOGGER.warn("[Zenkai] Kami: sin sitio ideal; colocando en superficie a {} bloques del spawn.", minDist);
        int fx = spawn.getX() + minDist, fz = spawn.getZ();
        return new BlockPos(fx, surfaceY(overworld, fx, fz), fz)
                .offset(0, ModStructureSegments.KAMI_Y_OFFSET, 0);
    }

    /**
     * Barre en cuadrícula alrededor de {@code biomePos} buscando el primer pad válido que además
     * respete la distancia mínima al spawn. Barato por diseño: la primera condición que falla
     * descarta el candidato sin leer más bloques.
     */
    private static BlockPos findGoodPad(ServerLevel level, BlockPos biomePos, BlockPos spawn) {
        int radius = ModStructureSegments.KAMI_SITE_SEARCH_RADIUS;
        int step   = Math.max(1, ModStructureSegments.KAMI_SITE_STEP);
        long minDistSqr = (long) ModStructureSegments.KAMI_MIN_DIST_FROM_SPAWN
                * ModStructureSegments.KAMI_MIN_DIST_FROM_SPAWN;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int cx = biomePos.getX() + dx;
                int cz = biomePos.getZ() + dz;
                long ddx = cx - spawn.getX(), ddz = cz - spawn.getZ();
                if (ddx * ddx + ddz * ddz < minDistSqr) continue;

                BlockPos pad = goodPadAt(level, cx, cz);
                if (pad != null) return pad;
            }
        }
        return null;
    }

    /**
     * Valida el pad centrado en (cx, cz): KAMI_PAD_SIZE² columnas con la MISMA altura de
     * superficie, césped en la capa superior y dirt debajo, dentro del rango de altura.
     * Devuelve la posición de la base (primer bloque de aire sobre el césped) o null.
     */
    private static BlockPos goodPadAt(ServerLevel level, int cx, int cz) {
        int half = ModStructureSegments.KAMI_PAD_SIZE / 2;
        int y = surfaceY(level, cx, cz);
        if (y < ModStructureSegments.KAMI_PAD_MIN_Y || y > ModStructureSegments.KAMI_PAD_MAX_Y) return null;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                if (surfaceY(level, x, z) != y) return null;                          // plano exacto
                m.set(x, y - 1, z);
                if (!level.getBlockState(m).is(Blocks.GRASS_BLOCK)) return null;      // césped arriba
                m.set(x, y - 2, z);
                if (!level.getBlockState(m).is(Blocks.DIRT)) return null;             // dirt debajo
            }
        }
        return new BlockPos(cx, y, cz);
    }

    /**
     * Altura del primer bloque de aire sobre la superficie, FORZANDO la generación del chunk.
     * (Level.getHeight sin chunk cargado devuelve la altura mínima del mundo → bug de la bedrock.)
     */
    private static int surfaceY(ServerLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    /** Garantiza que el palacio del otro mundo exista antes de teletransportar. */
    public static void ensureOtherworldPalace(ServerLevel otherworld) {
        placeOnce(otherworld.getServer(), otherworld, KEY_OTHERWORLD,
                ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.OTHERWORLD);
        StructureNpcManager.ensureAllIn(otherworld);   // spawnea los NPCs de esta dimensión
    }

    /** Garantiza que la estructura de la Habitación del Tiempo exista antes de teletransportar allí. */
    public static void ensureHtcStructure(ServerLevel htc) {
        placeOnce(htc.getServer(), htc, KEY_HTC,
                ModStructureSegments.HTC_BASE, ModStructureSegments.HTC);
        StructureNpcManager.ensureAllIn(htc);
    }

    private static void placeOnce(MinecraftServer server, ServerLevel level,
                                  String key, BlockPos base, List<Segment> segments) {
        ZenkaiWorldData data = ZenkaiWorldData.get(server);
        if (data.isPlaced(key)) return;
        boolean ok = StaticStructurePlacer.place(level, base, segments, true);
        if (ok) data.markPlaced(key);
    }

    /** Colocación forzada (para pruebas de offsets): ignora el flag de "ya colocada" e ilumina el aire. */
    public static boolean forcePlace(ServerLevel level, String which, BlockPos base) {
        return switch (which) {
            case "kami"       -> StaticStructurePlacer.place(level, base, ModStructureSegments.KAMI, true);
            case "otherworld" -> StaticStructurePlacer.place(level, base, ModStructureSegments.OTHERWORLD, true);
            case "htc"        -> StaticStructurePlacer.place(level, base, ModStructureSegments.HTC, true);
            default -> false;
        };
    }
}