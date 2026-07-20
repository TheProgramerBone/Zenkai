package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Coloca una estructura ÚNICA y grande compuesta por varios segmentos NBT
 * (cada uno en data/zenkai/structure/&lt; nombre&gt; .nbt), recolocándolos en
 * base + offset. Para estructuras fijas (otherworld, Kami) que NO se generan
 * aleatoriamente — se colocan una sola vez por código.
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

    /** Coloca sin iluminar (comportamiento por defecto). */
    public static boolean place(ServerLevel level, BlockPos base, List<Segment> segments) {
        return place(level, base, segments, false);
    }

    /**
     * Coloca todos los segmentos en base+offset. Si airToLight es true, convierte
     * las celdas de aire en bloques de luz (ilumina interiores que quedaron como aire).
     * flags = 2 (Block.UPDATE_CLIENTS) para no disparar updates costosos de vecinos.
     */
    public static boolean place(ServerLevel level, BlockPos base, List<Segment> segments, boolean airToLight) {
        StructureTemplateManager mgr = level.getStructureManager();
        boolean placedAny = false;

        // Ignora bloques de edición capturados en el NBT y respeta el structure void.
        BlockIgnoreProcessor ignore = new BlockIgnoreProcessor(
                List.of(Blocks.STRUCTURE_VOID, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW));

        for (Segment seg : segments) {
            var opt = mgr.get(seg.nbt());
            if (opt.isEmpty()) {
                LOGGER.error("[Zenkai] No se encontró el NBT de estructura: {}", seg.nbt());
                continue;
            }
            StructureTemplate tpl = opt.get();
            BlockPos pos = base.offset(seg.offset());
            StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(ignore);
            tpl.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            placedAny = true;
        }
        return placedAny;
    }
}