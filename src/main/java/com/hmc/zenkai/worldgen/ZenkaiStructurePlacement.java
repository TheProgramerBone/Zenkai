package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import com.hmc.zenkai.worldgen.npc.StructureNpcManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Colocación de estructuras únicas (no aleatorias):
 *  - Kami: una vez en el overworld al arrancar el servidor.
 *  - Otherworld: una vez, justo antes de mandar al primer jugador allí
 *    (ensureOtherworldPalace, llamado desde OtherworldManager).
 *
 * El flag de "ya colocada" vive en ZenkaiWorldData (una vez por mundo).
 * Los NPCs de estructura (Yemma, etc.) los gestiona StructureNpcManager.
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
                String.valueOf(Component.translatable("protector.zenkai.kami")));
        NoHostileSpawnZones.addFromBase(ModDimensions.OTHERWORLD_LEVEL,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_MIN,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SX,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SY,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SZ,
                String.valueOf(Component.translatable("protector.zenkai.yemma")));
        NoHostileSpawnZones.addFromBase(ModDimensions.HTC_LEVEL,
                ModStructureSegments.HTC_NO_SPAWN_MIN,
                ModStructureSegments.HTC_NO_SPAWN_SX,
                ModStructureSegments.HTC_NO_SPAWN_SY,
                ModStructureSegments.HTC_NO_SPAWN_SZ,
                String.valueOf(Component.translatable("protector.zenkai.htc")));
    }

    /**
     * Busca el bioma objetivo alrededor del spawn, DUPLICANDO el radio hasta
     * encontrarlo (o hasta KAMI_MAX_SEARCH_RADIUS). Ancla la base (kami_1) al
     * nivel del mar o a la superficie del bioma, según KAMI_ANCHOR_SEA_LEVEL.
     */
    private static BlockPos findKamiBase(ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();

        Pair<BlockPos, Holder<Biome>> found = null;
        int radius = ModStructureSegments.KAMI_SEARCH_RADIUS;
        while (found == null && radius <= ModStructureSegments.KAMI_MAX_SEARCH_RADIUS) {
            // ⚠ 1.21.1: findClosestBiome3d(Predicate<Holder<Biome>>, BlockPos, int radius, int hStep, int vStep)
            found = overworld.findClosestBiome3d(
                    h -> h.is(ModStructureSegments.KAMI_BIOME), spawn, radius, 32, 64);
            if (found == null) {
                LOGGER.info("[Zenkai] Bioma de Kami no hallado en radio {}, ampliando...", radius);
                radius *= 2;
            }
        }

        BlockPos xz;
        if (found != null) {
            xz = found.getFirst();
            LOGGER.info("[Zenkai] Kami: bioma hallado a {} bloques del spawn.",
                    (int) Math.sqrt(spawn.distSqr(xz)));
        } else {
            xz = spawn;
            LOGGER.warn("[Zenkai] No se encontró el bioma de Kami; se coloca en el spawn.");
        }

        // Dentro del bioma, busca un cuadro naturalmente PLANO del tamaño de kami_1,
        // para que la base del pilar no quede medio enterrada / medio flotando en pendiente.
        BlockPos flat = findFlatSpot(overworld, xz);
        if (flat != null) {
            LOGGER.info("[Zenkai] Kami: terreno plano hallado en {}, {}.", flat.getX(), flat.getZ());
            xz = flat;
        } else {
            LOGGER.warn("[Zenkai] Kami: sin terreno plano cercano; se usa el punto del bioma.");
        }

        // Ancla kami_1 (offset 0,0,0) al nivel del mar o a la superficie.
        int baseY = ModStructureSegments.KAMI_ANCHOR_SEA_LEVEL
                ? overworld.getSeaLevel()
                : overworld.getHeight(Heightmap.Types.WORLD_SURFACE, xz.getX(), xz.getZ());
        return new BlockPos(xz.getX(), baseY + ModStructureSegments.KAMI_Y_OFFSET, xz.getZ());
    }

    /**
     * Busca alrededor de {@code centerXZ} un cuadro plano del tamaño de kami_1
     * ({@link ModStructureSegments#KAMI_FOOTPRINT}). Muestrea la altura del terreno en las 4 esquinas
     * + centro de cada candidato; se queda con el más plano (menor desnivel), prefiriendo los cercanos
     * al nivel del mar y al centro. Si nada cumple, relaja el desnivel tolerado hasta 8; si aun así no,
     * devuelve null (se usará centerXZ).
     */
    private static BlockPos findFlatSpot(ServerLevel level, BlockPos centerXZ) {
        int half   = ModStructureSegments.KAMI_FOOTPRINT / 2;
        int radius = ModStructureSegments.KAMI_FLAT_SEARCH_RADIUS;
        int step   = Math.max(1, ModStructureSegments.KAMI_FLAT_STEP);
        int sea    = level.getSeaLevel();

        for (int maxDiff = ModStructureSegments.KAMI_FLAT_MAX_DIFF; maxDiff <= 8; maxDiff += 2) {
            BlockPos best = null;
            long bestScore = Long.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    int cx = centerXZ.getX() + dx;
                    int cz = centerXZ.getZ() + dz;
                    int h0 = surfaceY(level, cx - half, cz - half);
                    int h1 = surfaceY(level, cx + half, cz - half);
                    int h2 = surfaceY(level, cx - half, cz + half);
                    int h3 = surfaceY(level, cx + half, cz + half);
                    int h4 = surfaceY(level, cx, cz);
                    int min = Math.min(Math.min(h0, h1), Math.min(h2, Math.min(h3, h4)));
                    int max = Math.max(Math.max(h0, h1), Math.max(h2, Math.max(h3, h4)));
                    if (max - min > maxDiff) continue; // no es plano

                    int avg = (h0 + h1 + h2 + h3 + h4) / 5;
                    long score = (long) (max - min) * 100L        // plano
                            + (long) Math.abs(avg - sea) * 2L      // preferir cerca del nivel del mar
                            + (Math.abs(dx) + Math.abs(dz)) / 8L;  // y cerca del centro del bioma
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(cx, avg, cz);
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Altura del suelo ignorando agua y hojas (OCEAN_FLOOR): sirve para medir "planitud" del terreno. */
    private static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    /** Garantiza que el palacio del otro mundo exista antes de teletransportar. */
    public static void ensureOtherworldPalace(ServerLevel otherworld) {
        placeOnce(otherworld.getServer(), otherworld, KEY_OTHERWORLD,
                ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.OTHERWORLD, true);
        StructureNpcManager.ensureAllIn(otherworld);   // spawnea los NPCs de esta dimensión
    }

    /** Garantiza que la estructura de la Habitación del Tiempo exista antes de teletransportar allí. */
    public static void ensureHtcStructure(ServerLevel htc) {
        placeOnce(htc.getServer(), htc, KEY_HTC,
                ModStructureSegments.HTC_BASE, ModStructureSegments.HTC, true);
        StructureNpcManager.ensureAllIn(htc);
    }

    private static void placeOnce(MinecraftServer server, ServerLevel level,
                                  String key, BlockPos base, List<Segment> segments, boolean airToLight) {
        ZenkaiWorldData data = ZenkaiWorldData.get(server);
        if (data.isPlaced(key)) return;
        boolean ok = StaticStructurePlacer.place(level, base, segments, airToLight);
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