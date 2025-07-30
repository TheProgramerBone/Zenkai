package com.hmc.db_renewed.entity.saiyan_pod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SaiyanPodEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("open");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlay("close");

    public SaiyanPodEntity(EntityType<? extends SaiyanPodEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0,this::predicate)
                .triggerableAnim("open", OPEN_ANIM)
                .triggerableAnim("close", CLOSE_ANIM));
    }

    private <E extends GeoAnimatable> PlayState predicate(AnimationState<E> state) {
        state.setAnimation(OPEN_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }
    
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isPassenger()) {
            player.startRiding(this);

            if (!level().isClientSide()) {
                triggerCloseAnimation();
            }

            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (!level().isClientSide()) {
            if (getPassengers().isEmpty()) {
                triggerOpenAnimation();
            }
        }
        return InteractionResult.PASS;
    }

    public void triggerCloseAnimation() {
            triggerAnim("controller", "close");
    }

    public void triggerOpenAnimation(){
        triggerAnim("controller","open");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(0, 0.2, 0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.isInvulnerableTo(source)) {
            this.kill();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    protected void doWaterSplashEffect() {}
}