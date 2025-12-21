package com.hmc.db_renewed.content.item.special;

import com.hmc.db_renewed.content.entity.ModEntities;
import com.hmc.db_renewed.content.entity.kintoun.ShadowKintounEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ShadowKintounItem extends Item {
    public ShadowKintounItem(Properties properties) {
        super(properties);
    }
    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        ItemStack stack = context.getItemInHand();

        if (!level.isClientSide()) {
            Vec3 spawnPos = Vec3.atCenterOf(clickedPos).add(0, 1, 0);
            ShadowKintounEntity kintoun = new ShadowKintounEntity(ModEntities.SHADOW_KINTOUN.get(), level);
            assert player != null;
            kintoun.moveTo(spawnPos, player.getYRot(), 0.0F);
            level.addFreshEntity(kintoun);
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

}
