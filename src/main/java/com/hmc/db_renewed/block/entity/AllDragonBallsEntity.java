package com.hmc.db_renewed.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AllDragonBallsEntity extends BlockEntity implements GeoBlockEntity {

    protected static final RawAnimation SUMMON_ANIM = RawAnimation.begin().thenPlay("summon").thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AllDragonBallsEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ALL_DRAGON_BALLS_ENTITY.get(), pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", state -> PlayState.STOP)
                .triggerableAnim("summon", SUMMON_ANIM));
    }

    public void triggerSummonAnimation() {
        if (level instanceof ServerLevel serverLevel) {
            triggerAnim("controller","summon");
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return this.level != null ? this.level.getGameTime() : 0;
    }
}
