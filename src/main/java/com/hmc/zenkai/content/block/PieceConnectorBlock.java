package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.PieceConnectorBlockEntity;
import com.hmc.zenkai.network.OpenPieceConnectorPayload;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * El "jigsaw" propio de Zenkai. Se coloca dentro de un NBT de pieza (a mano con un
 * Structure Block en modo Save, o generado por script) para marcar un punto por el que esa
 * pieza puede engancharse a otra. A diferencia del {@code minecraft:jigsaw} vainilla, el
 * socket que ofrece NO se declara en un pool JSON aparte — vive tal cual en el block entity
 * (campo "Socket"), así que el motor ({@code worldgen.piecegraph.PieceGraphPlacer}) lo
 * descubre escaneando el NBT, sin nada que pueda desincronizarse.
 *
 * SIN el truco de invisibilidad de {@link NpcMarkerBlock}/{@code LightBlock} a propósito: ese
 * truco tiene sentido para un bloque que se queda para siempre en un mundo de verdad y no debe
 * verlo un jugador normal. Este conector es justo lo contrario — vive SOLO dentro de la pieza
 * fuente mientras se construye, y {@link com.hmc.zenkai.worldgen.piecegraph.PieceGraphPlacer#placeAll}
 * lo excluye siempre de la colocación final (mismo `BlockIgnoreProcessor` que ya excluye
 * STRUCTURE_VOID/JIGSAW) — nunca llega a existir en una aldea terminada, así que esconderlo
 * durante la CONSTRUCCIÓN solo estorbaba: quien construye la pieza necesita verlo sin tener que
 * llevar el ítem en la mano. Renderiza como un bloque normal (modelo cube_all de
 * piece_connector.png) y se apunta/interactúa igual que cualquier otro bloque.
 */
public class PieceConnectorBlock extends BaseEntityBlock {

    public static final MapCodec<PieceConnectorBlock> CODEC = simpleCodec(PieceConnectorBlock::new);
    /** Hacia dónde mira el conector — la pieza vecina ocupa la celda de al lado en esta
     *  dirección y su propio conector de entrada queda orientado en sentido opuesto. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public PieceConnectorBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static BlockBehaviour.@NotNull Properties connectorProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()   // se camina a través mientras se construye la pieza alrededor
                .noOcclusion()
                .noLootTable()
                .strength(-1.0F, 3600000.0F)   // irrompible como la bedrock
                .sound(SoundType.EMPTY)
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Ver el javadoc de la clase: a propósito NO es RenderShape.INVISIBLE — quien construye la
     *  pieza necesita ver exactamente dónde están sus conectores. */
    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Orienta el conector según la cara clicada al colocarlo — comodidad de autoría a mano,
     *  no afecta al motor (que lee la orientación cruda del NBT, ya rotada o no según cómo se
     *  guardó, y hace su propia matemática de rotación sobre eso). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PieceConnectorBlockEntity(pos, state);
    }

    /** Clic (sin ítem especial en la mano, ya no hace falta): abre PieceConnectorScreen con el
     *  socket y la cara actuales — mismo patrón que NpcMarkerBlock/OpenNpcMarkerPayload. */
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level,
                                                         @NotNull BlockPos pos, @NotNull Player player,
                                                         @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PieceConnectorBlockEntity be)) return InteractionResult.PASS;
        if (!player.canUseGameMasterBlocks()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        PacketDistributor.sendToPlayer(sp, new OpenPieceConnectorPayload(
                pos, be.getSocket().toString(), state.getValue(FACING).getSerializedName()));
        return InteractionResult.CONSUME;
    }
}
