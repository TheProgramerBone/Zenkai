package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.feature.teleport.StructureAnchors;
import com.hmc.zenkai.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/**
 * Una pieza = un NBT colocado en una posición absoluta. Reutilizable por cualquier
 * estructura Zenkai construida a base de segmentos con offset fijo.
 */
public class SegmentPiece extends TemplateStructurePiece {

    private static final BlockIgnoreProcessor IGNORE = new BlockIgnoreProcessor(
            List.of(Blocks.STRUCTURE_VOID, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW));

    public SegmentPiece(StructureTemplateManager mgr, ResourceLocation nbt, BlockPos pos) {
        super(ModStructures.SEGMENT.get(), 0, mgr, nbt, nbt.toString(), settings(), pos);
    }

    /** Constructor de carga desde disco. Lo referencia el StructurePieceType registrado. */
    public SegmentPiece(StructureTemplateManager mgr, CompoundTag tag) {
        super(ModStructures.SEGMENT.get(), tag, mgr, id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().addProcessor(IGNORE);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // Structure Block en modo Data -> punto de ancla nombrado (Instant Transmission, o
        // cualquier otro sistema futuro que necesite un punto fijo dentro de una estructura de
        // segmentos). Ver StructureAnchors para la guía de cómo añadir/mover uno sin tocar Java.
        StructureAnchors.capture(name, pos);
    }
}