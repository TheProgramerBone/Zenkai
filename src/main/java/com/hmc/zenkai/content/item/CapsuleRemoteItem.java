package com.hmc.zenkai.content.item;

import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import com.hmc.zenkai.feature.capsule.CapsuleRemote;
import com.hmc.zenkai.feature.capsule.CapsuleRemoteLinks;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Control remoto de cápsulas Hoi-Poi (pedido 2026-11-25). La lógica vive en {@link CapsuleRemote}; aquí solo se enrutan los
 * clics. Acción activa = ítem, sin tecla nueva (regla del usuario); la rueda con Shift la captura el cliente
 * (CapsuleRemoteClient) y la manda como CapsuleRemoteCyclePayload.
 *
 * El vínculo va en {@code onItemUseFirst} y no en {@code useOn}: este corre ANTES que el clic del bloque, y si no la
 * cápsula se quedaría con el clic (mostraría el contorno o se abriría en vez de vincularse).
 */
public class CapsuleRemoteItem extends Item {

    public CapsuleRemoteItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, @NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof CapsuleBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) CapsuleRemote.link(sp, stack, be);
        return InteractionResult.CONSUME;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) CapsuleRemote.forgetSelected(sp, stack);
            else CapsuleRemote.toggle(sp, stack);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tip, @NotNull TooltipFlag flag) {
        CapsuleRemoteLinks l = CapsuleRemote.links(stack);
        if (l.links().isEmpty()) {
            tip.add(Component.translatable("item.zenkai.capsule_remote.desc.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int sel = l.selectedIndex();
        for (int i = 0; i < l.links().size(); i++) {
            CapsuleRemoteLinks.Link link = l.links().get(i);
            BlockPos p = link.pos().pos();
            Component line = Component.translatable("item.zenkai.capsule_remote.entry",
                    i == sel ? "▶" : " ", CapsuleRemote.label(link.name(), link.tier()),
                    ZenkaiNumbers.exact((long) p.getX()), ZenkaiNumbers.exact((long) p.getY()), ZenkaiNumbers.exact((long) p.getZ()));
            tip.add(line.copy().withStyle(i == sel ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.translatable("item.zenkai.capsule_remote.desc.linked").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) { return false; }
}
