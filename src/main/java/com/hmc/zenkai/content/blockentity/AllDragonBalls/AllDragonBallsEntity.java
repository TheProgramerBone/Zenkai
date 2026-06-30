package com.hmc.zenkai.content.blockentity.AllDragonBalls;

import com.hmc.zenkai.content.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

    private boolean summoned = false;

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

    // ── Estado de invocación (por-posición) ───────────────────────────────────
    public boolean isSummoned() { return summoned; }

    public void setSummoned(boolean v) {
        this.summoned = v;
        setChanged(); // marca el BE para guardarse
    }

    public void startAnimation(ServerLevel level) {
        if (!isAnimating) {
            this.isAnimating = true;
            this.animationStartTick = level.getGameTime();
            setChanged();
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
        setChanged();
    }

    // ── NBT (persistencia por-posición) ───────────────────────────────────────
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("summoned", summoned);
        tag.putBoolean("isAnimating", isAnimating);
        tag.putLong("animationStartTick", animationStartTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.summoned = tag.getBoolean("summoned");
        this.isAnimating = tag.getBoolean("isAnimating");
        this.animationStartTick = tag.getLong("animationStartTick");
    }
}