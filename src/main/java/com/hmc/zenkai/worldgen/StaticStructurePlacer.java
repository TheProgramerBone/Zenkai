package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Coloca una estructura ÚNICA y grande compuesta por varios segmentos NBT
 * (cada uno en data/zenkai/structure/&lt;nombre&gt;.nbt), recolocándolos en
 * base + offset. Para estructuras fijas (otherworld, Kami) que NO se generan
 * aleatoriamente — se colocan una sola vez por código.
 *
 * Los offsets describen cómo encajan las piezas (los NBT no guardan su posición).
 */
public final class StaticStructurePlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Structures");

    private StaticStructurePlacer() {}

    /** Un segmento: el NBT y su desplazamiento relativo a la esquina origen. */
    public record Segment(ResourceLocation nbt, BlockPos offset) {
        public static Segment of(String name, int x, int y, int z) {
            return new Segment(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name), new BlockPos(x, y, z));
        }
    }

    /**
     * Coloca todos los segmentos en base+offset. Devuelve true si colocó al menos uno.
     * flags = 2 (Block.UPDATE_CLIENTS) para no disparar updates costosos de vecinos.
     */
    public static boolean place(ServerLevel level, BlockPos base, List<Segment> segments) {
        StructureTemplateManager mgr = level.getStructureManager();
        boolean placedAny = false;

        for (Segment seg : segments) {
            var opt = mgr.get(seg.nbt());
            if (opt.isEmpty()) {
                LOGGER.error("[Zenkai] No se encontró el NBT de estructura: {}", seg.nbt());
                continue;
            }
            StructureTemplate tpl = opt.get();
            BlockPos pos = base.offset(seg.offset());
            StructurePlaceSettings settings = new StructurePlaceSettings();
            // ⚠ 1.21.1: placeInWorld(ServerLevelAccessor, BlockPos pos, BlockPos pivot,
            //            StructurePlaceSettings, RandomSource, int flags)
            tpl.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            placedAny = true;
        }
        return placedAny;
    }
}