package com.hmc.zenkai.feature.sideconfig;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.content.item.WrenchItem;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.feature.transport.TransportWrench;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Clics de la llave de Zenkai sobre bloques del mod (0.7.0). Va en RightClickBlock a prioridad MÁXIMA por
 * Create: su WrenchEventHandler trata cualquier ítem del tag c:tools/wrench como su llave sobre bloques
 * IWrenchable (el Motor de Ki lo es) y lo GIRA cancelando el evento; solo se aparta si el evento ya llega
 * cancelado. Antes la llave nunca cambiaba puertos del motor (lo vio el usuario el 2026-11-17).
 *
 * Reparto:
 *  - Agachado: desmontar cualquier bloque con caras (SideConfigurable). Cables/tuberías/tanques también.
 *  - Modo ROTAR: el motor se deja pasar a Create (que lo gira); los transportes no giran; el resto de
 *    máquinas del mod con orientación se giran aquí (las de dos bloques, las dos mitades).
 *  - Modos ENERGÍA/FLUIDO: el motor (y cualquier SideConfigurable que no sea transporte) cambia el puerto
 *    de la cara clicada en el canal del modo. Los transportes los resuelve TransportWrench, que elige la
 *    cara por el PUNTO golpeado (un brazo de cable).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class WrenchEvents {
    private WrenchEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WrenchItem)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!"zenkai".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return;
        Player player = event.getEntity();
        BlockEntity be = level.getBlockEntity(pos);
        boolean transport = state.getBlock() instanceof TransportBlock;
        WrenchItem.Mode mode = WrenchItem.mode(stack);

        if (player.isShiftKeyDown()) {
            if (!(be instanceof SideConfigurable)) return;
            cancel(event, level);
            if (level instanceof ServerLevel sl) TransportWrench.dismantle(sl, pos, state, be, player, stack);
            return;
        }

        if (mode == WrenchItem.Mode.ROTATE) {
            if (state.is(ModBlocks.KI_MOTOR.get())) return;   // lo gira Create (y sin Create el bloque es inerte)
            if (transport) { cancel(event, level); return; }   // un cable o un tanque no tienen orientación
            if (!rotatable(state)) return;
            cancel(event, level);
            if (!level.isClientSide) rotate(level, pos, state, event.getFace());
            return;
        }

        if (transport || !(be instanceof SideConfigurable cfg)) return;   // los transportes: TransportWrench
        cancel(event, level);
        if (level.isClientSide) return;
        Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
        if (cfg.sideLocked(face)) {
            player.displayClientMessage(Component.translatable("screen.zenkai.side_config.locked"), true);
            return;
        }
        SideConfigurable.Channel ch = WrenchItem.channelFor(stack, cfg);
        SideMode next = cfg.sideMode(ch, face).next();
        cfg.setSideMode(ch, face, next);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.4f);
        player.displayClientMessage(Component.translatable("item.zenkai.wrench.set",
                Component.translatable(ch.langKey()),
                Component.translatable("messages.zenkai.transport.side." + face.getName()),
                Component.translatable(next.langKey())), true);
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event, Level level) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private static boolean rotatable(BlockState s) {
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || s.hasProperty(BlockStateProperties.FACING);
    }

    /**
     * Horizontal: 90° en el sentido de las agujas. Con las seis direcciones: hacia la cara clicada, o a la
     * contraria si ya mira hacia ella. Un bloque de dos alturas (la cámara de contención) gira las dos mitades.
     */
    private static void rotate(Level level, BlockPos pos, BlockState state, Direction clicked) {
        BlockState turned;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            turned = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        } else {
            Direction now = state.getValue(BlockStateProperties.FACING);
            Direction target = clicked == null ? now.getOpposite() : clicked == now ? clicked.getOpposite() : clicked;
            turned = state.setValue(BlockStateProperties.FACING, target);
        }
        // UPDATE_KNOWN_SHAPE: sin avisar a los vecinos, para que la otra mitad de un bloque doble no se rompa
        // al ver una orientación distinta antes de que la giremos también.
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(pos, turned, flags);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState os = level.getBlockState(other);
            if (os.is(state.getBlock()) && os.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                level.setBlock(other, os.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        turned.getValue(BlockStateProperties.HORIZONTAL_FACING)), flags);
            }
        }
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.1f);
    }
}
