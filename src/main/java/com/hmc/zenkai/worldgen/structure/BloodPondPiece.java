package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.registry.ModStructures;
import com.hmc.zenkai.worldgen.HfilBloodPoolFeature;
import com.hmc.zenkai.worldgen.LocalGroundProbe;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Pieza única del landmark del Blood Pond (ver {@link BloodPondStructure} para el porqué de la
 * estructura). NO extiende {@link SegmentPiece} — esta pieza necesita CAVAR terreno además de
 * colocar bloques, y {@code SegmentPiece} solo estampa un NBT tal cual. Todo a mano, mismo
 * estilo ligero que {@code HfilSpikeFeature}/{@code HfilBonePileFeature}: sin jigsaw, sin
 * asset NBT (a diferencia del plan original de la sección 7 de
 * .claude/pendiente/hfil-rework-propuesta.md, que proponía un NBT para el cartel/plataforma —
 * innecesario aquí porque toda la geometría es simple y fija, ver esa sección para el porqué no
 * hacía falta la complejidad de un asset NBT + script de generación para esto).
 *
 * Tres piezas de contenido, todas ancladas al suelo real vía {@link LocalGroundProbe} (nunca un
 * heightmap global — una isla flotante encima confundiría la altura):
 * 1. El charco en sí — {@link HfilBloodPoolFeature#carve}, mismo algoritmo que los charcos
 *    ambientales pero a un radio fijo mayor (es "el" Blood Pond, no uno cualquiera).
 * 2. Cuatro marcadores de hueso+calavera en los puntos cardinales alrededor del charco.
 * 3. Un cartel de madera sobre un poste de hueso, con el texto "Blood"/"Pond" — la referencia
 *    icónica de las 5 imágenes de diseño de la sesión (sección 3.1 de la propuesta).
 *
 * El cartel SÍ se coloca vía NBT de block entity a mano ({@link SignText#DIRECT_CODEC} +
 * {@link net.minecraft.world.level.block.entity.BlockEntity#loadWithComponents}), al revés de lo
 * que decía este mismo comentario antes: usar la API "en vivo" ({@code SignBlockEntity.setText})
 * revienta con NPE aquí. Esa API llama a {@code markUpdated()}, que SIEMPRE hace
 * {@code this.level.sendBlockUpdated(...)} sin comprobar null — y en el momento en que
 * {@code postProcess} corre (fase de features, dentro de {@code applyBiomeDecoration}), el chunk
 * todavía es un {@code ProtoChunk} sin nivel: {@code BlockEntity.level} solo se asigna más tarde,
 * cuando el chunk se promociona a {@code LevelChunk} de verdad. {@code loadWithComponents} (y el
 * {@code loadAdditional} que llama por dentro) es la vía correcta para esto — es la misma que usa
 * vainilla para aplicar NBT de block entity al colocar una estructura, y nunca toca
 * {@code this.level}.
 */
public class BloodPondPiece extends StructurePiece {

    private static final int POOL_RADIUS = 8;
    private static final int POOL_MAX_DEPTH = 4;
    private static final int GROUND_SEARCH_RADIUS = 6;
    private static final float EDGE_ERODE_CHANCE = 0.2f;
    /** Distancia desde el centro a la que se plantan los marcadores/el cartel — fuera de la
     *  orilla del charco (radio real del charco + 1 de anillo, ver HfilBloodPoolFeature.carve). */
    private static final int MARKER_DISTANCE = POOL_RADIUS + 3;

    private final BlockPos origin;

    public BloodPondPiece(BlockPos origin) {
        super(ModStructures.BLOOD_POND_PIECE.get(), 0, makeBox(origin));
        this.origin = origin;
    }

    public BloodPondPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.BLOOD_POND_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
    }

    private static BoundingBox makeBox(BlockPos origin) {
        int r = MARKER_DISTANCE + 2;
        return new BoundingBox(origin.getX() - r, origin.getY() - POOL_MAX_DEPTH - 2, origin.getZ() - r,
                origin.getX() + r, origin.getY() + 3, origin.getZ() + r);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator chunkGenerator,
                             RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        HfilBloodPoolFeature.carve(level, random, origin, POOL_RADIUS, POOL_MAX_DEPTH, GROUND_SEARCH_RADIUS,
                EDGE_ERODE_CHANCE, chunkBox);
        placeMarkers(level, random, chunkBox);
        placeSign(level, chunkBox);
    }

    /** Cuatro marcadores de hueso+calavera en los puntos cardinales, alrededor del anillo del
     *  charco — las almas que se hundieron aquí, mismo motivo que HfilBonePileFeature, pero fijo
     *  en vez de aleatorio: esto es el landmark, no la decoración ambiental. */
    private void placeMarkers(WorldGenLevel level, RandomSource random, BoundingBox chunkBox) {
        int[][] offsets = {{MARKER_DISTANCE, 0}, {-MARKER_DISTANCE, 0}, {0, MARKER_DISTANCE}, {0, -MARKER_DISTANCE}};
        for (int[] off : offsets) {
            int x = origin.getX() + off[0];
            int z = origin.getZ() + off[1];
            if (!chunkBox.isInside(new BlockPos(x, origin.getY(), z))) continue;

            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue;

            BlockPos bone = new BlockPos(x, groundY, z);
            level.setBlock(bone, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.setBlock(bone.above(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    /** El cartel, en la diagonal SE del charco (fuera de los 4 marcadores cardinales) — un poste
     *  de hueso con un letrero de madera encima, texto "Blood"/"Pond". */
    private void placeSign(WorldGenLevel level, BoundingBox chunkBox) {
        int dist = Math.round(MARKER_DISTANCE * 0.8f);
        int x = origin.getX() + dist;
        int z = origin.getZ() + dist;
        if (!chunkBox.isInside(new BlockPos(x, origin.getY(), z))) return;

        int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
        if (groundY == Integer.MIN_VALUE) return;

        BlockPos post = new BlockPos(x, groundY, z);
        level.setBlock(post, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);

        BlockPos signPos = post.above();
        level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(0, Component.literal("Blood"))
                    .setMessage(1, Component.literal("Pond"));
            // NO usar sign.setText(text, true) aquí: revienta con NPE durante worldgen (ver
            // javadoc de la clase). En vez de eso, codificamos el SignText a NBT tal cual lo
            // guardaría SignBlockEntity.saveAdditional y lo recargamos con loadWithComponents,
            // que solo toca los campos internos, nunca this.level.
            CompoundTag signTag = new CompoundTag();
            DynamicOps<Tag> dynamicOps = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            SignText.DIRECT_CODEC.encodeStart(dynamicOps, text)
                    .result()
                    .ifPresent(encoded -> signTag.put("front_text", encoded));
            sign.loadWithComponents(signTag, level.registryAccess());
        }
    }
}
