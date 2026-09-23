package com.hmc.zenkai.worldgen.piecegraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Un conector tal cual está guardado en el NBT de una pieza, SIN rotar — el espacio local del
 * propio template (igual que si se hubiera colocado en 0,0,0 sin girar). {@code localPos} es
 * la celda que ocupa el bloque {@link com.hmc.zenkai.content.block.PieceConnectorBlock},
 * {@code localFacing} hacia dónde mira. La pieza vecina se conecta "de frente": ocupa la
 * celda de al lado en esa dirección, con su propio conector mirando en sentido opuesto.
 */
public record PieceSocket(ResourceLocation socket, BlockPos localPos, Direction localFacing) {}
