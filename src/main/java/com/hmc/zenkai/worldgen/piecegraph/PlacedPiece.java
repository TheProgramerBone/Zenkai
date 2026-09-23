package com.hmc.zenkai.worldgen.piecegraph;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Resultado resuelto por {@link PieceGraphPlacer}: una pieza con posición y rotación ya
 *  decididas, lista para materializarse en el mundo con {@link PieceGraphPlacer#placeAll}. */
public record PlacedPiece(ResourceLocation nbt, BlockPos templatePosition, Rotation rotation) {}
