package com.hmc.zenkai.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Escalera invisible para estructuras (Kami, para que el jugador pueda llegar hasta arriba). Se escala igual
 * que una escalera: lo único que hace falta es que esté en el tag {@code minecraft:climbable}
 * (data/minecraft/tags/block/climbable.json); el jugador la recorre porque no tiene colisión y se agarra a ella
 * con solo estar dentro de su celda. No se dibuja y no se puede apuntar salvo con su ítem en la mano (mismo
 * truco que NpcMarkerBlock y el bloque de luz de vainilla), así el constructor la ve y el jugador no.
 * Se pone en columna junto a una pared. Es irrompible (como la barrier) y va sin tabla de botín: es un bloque de
 * construcción, no de supervivencia; solo se rompe en creativo.
 */
public class InvisibleLadderBlock extends Block {

    public InvisibleLadderBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public static BlockBehaviour.@NotNull Properties ladderProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .strength(-1.0F, 3600000.0F)   // irrompible como la barrier
                .sound(SoundType.EMPTY)
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.INVISIBLE; }

    /** Sin el ítem en la mano el rayo de selección la atraviesa; con él, un bloque entero para poder apuntarla. */
    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return context.isHoldingItem(this.asItem()) ? Shapes.block() : Shapes.empty();
    }

    /** Que el cielo y el agua pasen: no debe oscurecer ni tapar nada. */
    @Override
    protected boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }
}
