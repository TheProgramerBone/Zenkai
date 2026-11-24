package com.hmc.zenkai.content.item;

import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import com.hmc.zenkai.content.entity.misc.ThrownCapsuleEntity;
import com.hmc.zenkai.feature.capsule.CapsuleContent;
import com.hmc.zenkai.feature.capsule.CapsuleTier;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cápsula Hoi-Poi (Fase 6 de la 0.7.0, §3.5 de pendientes-0.7.0.md). Un ítem por rango ({@link CapsuleTier}); llena o
 * vacía según el componente {@code zenkai:capsule_content} ({@link CapsuleContent}).
 *
 * VACÍA: clic derecho sobre un bloque la COLOCA en el suelo (como una antorcha); el resto del flujo (contorno, pantalla,
 * guardar) vive en {@link CapsuleBlock}/{@link CapsuleBlockEntity}.
 *
 * LLENA (dos modos, decididos 2026-11-16):
 *   - clic derecho al aire (o sobre un bloque sin Shift): se LANZA como una bola de nieve y suelta lo guardado donde cae;
 *   - Shift + clic derecho sobre un bloque: se COLOCA y enseña el holograma; un clic sobre ella lo abre.
 * Las vacías se apilan de 16 en 16; las llenas nunca, porque cada una lleva su id.
 */
public class CapsuleItem extends Item {

    private final CapsuleTier tier;

    public CapsuleItem(CapsuleTier tier, Properties props) {
        super(props.stacksTo(16));
        this.tier = tier;
    }

    public CapsuleTier tier() { return tier; }

    public static @Nullable CapsuleContent content(ItemStack stack) {
        return stack.get(ModDataComponents.CAPSULE_CONTENT.get());
    }

    public static boolean isFull(ItemStack stack) {
        return stack.getItem() instanceof CapsuleItem && content(stack) != null;
    }

    /** La misma cápsula, vacía (para devolverla tras soltar lo guardado). */
    public static ItemStack emptied(ItemStack stack) {
        ItemStack e = stack.copyWithCount(1);
        e.remove(ModDataComponents.CAPSULE_CONTENT.get());
        return e;
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext ctx) {
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        boolean full = isFull(stack);
        // Llena y sin Shift: no se coloca, se lanza (use() corre justo después si esto devuelve PASS).
        if (player == null || (full && !player.isShiftKeyDown())) return InteractionResult.PASS;

        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockPos at = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(ctx.getClickedFace());
        if (!level.getBlockState(at).canBeReplaced()
                || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockState state = ModBlocks.CAPSULE.get().defaultBlockState()
                .setValue(CapsuleBlock.TIER, tier)
                .setValue(CapsuleBlock.FULL, full)
                .setValue(CapsuleBlock.FACING, player.getDirection().getOpposite());
        level.setBlock(at, state, 3);
        if (level.getBlockEntity(at) instanceof CapsuleBlockEntity be) {
            be.setCapsule(stack.copyWithCount(1));
        }
        level.playSound(null, at, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 0.8F, 1.4F);
        stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isFull(stack)) return InteractionResultHolder.pass(stack);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel sl) {
            ThrownCapsuleEntity thrown = new ThrownCapsuleEntity(sl, player);
            thrown.setItem(stack.copyWithCount(1));
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
            sl.addFreshEntity(thrown);
        }
        player.getCooldowns().addCooldown(this, 10);
        stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tip, @NotNull TooltipFlag flag) {
        CapsuleContent c = content(stack);
        if (c == null) {
            int side = tier.side();
            tip.add(Component.translatable("item.zenkai.capsule.zone", ZenkaiNumbers.screen(side),
                    ZenkaiNumbers.screen(side), ZenkaiNumbers.screen(side)).withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("item.zenkai.capsule.desc.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.translatable("item.zenkai.capsule.summary", ZenkaiNumbers.screen(c.blocks()),
                    ZenkaiNumbers.screen(c.mobs())).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("item.zenkai.capsule.desc.full").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) { return false; }
}
