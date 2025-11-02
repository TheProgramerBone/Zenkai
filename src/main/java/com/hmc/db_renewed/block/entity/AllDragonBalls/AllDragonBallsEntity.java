package com.hmc.db_renewed.block.entity.AllDragonBalls;

import com.hmc.db_renewed.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AllDragonBallsEntity extends BlockEntity implements GeoBlockEntity {

    protected static final RawAnimation AWAY_ANIM = RawAnimation.begin().thenPlay("away").thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean isAnimating = false;
    private long animationStartTick = 0;
    private static final int ANIMATION_DURATION = 80;

    public AllDragonBallsEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ALL_DRAGON_BALLS_ENTITY.get(), pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", state -> PlayState.STOP)
                .triggerableAnim("away", AWAY_ANIM));
    }

    public void triggerAwayAnimation() {
            this.triggerAnim("controller", "away");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return this.level != null ? this.level.getGameTime() : 0;
    }

    public void startAnimation(ServerLevel level) {
        if (!isAnimating) {
            this.isAnimating = true;
            this.animationStartTick = level.getGameTime();
            triggerAwayAnimation();
        }
    }

    public boolean isAnimating(ServerLevel level) {
        return isAnimating && (level.getGameTime() - animationStartTick < ANIMATION_DURATION);
    }

    public boolean hasFinishedAnimation(ServerLevel level) {
        return isAnimating && (level.getGameTime() - animationStartTick >= ANIMATION_DURATION);
    }

    public void stopAnimation() {
        this.isAnimating = false;
    }
}
