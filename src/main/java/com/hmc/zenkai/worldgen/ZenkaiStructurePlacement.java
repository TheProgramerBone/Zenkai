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
                "Kami");
        NoHostileSpawnZones.addFromBase(ModDimensions.OTHERWORLD_LEVEL,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_MIN,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SX,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SY,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SZ,
                "Yemma");
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

        // Ancla kami_1 (offset 0,0,0) al nivel del mar o a la superficie.
        int baseY = ModStructureSegments.KAMI_ANCHOR_SEA_LEVEL
                ? overworld.getSeaLevel()
                : overworld.getHeight(Heightmap.Types.WORLD_SURFACE, xz.getX(), xz.getZ());
        return new BlockPos(xz.getX(), baseY + ModStructureSegments.KAMI_Y_OFFSET, xz.getZ());
    }

    /** Garantiza que el palacio del otro mundo exista antes de teletransportar. */
    public static void ensureOtherworldPalace(ServerLevel otherworld) {
        placeOnce(otherworld.getServer(), otherworld, KEY_OTHERWORLD,
                ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.OTHERWORLD, true);
        StructureNpcManager.ensureAllIn(otherworld);   // spawnea los NPCs de esta dimensión
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
            default -> false;
        };
    }
}