package com.hmc.zenkai.content.entity.overworld;

import com.hmc.zenkai.content.entity.ZenkaiCommonAnimations;
import com.hmc.zenkai.content.entity.ZenkaiDefaultNPC;
import com.hmc.zenkai.core.network.feature.wishes.OpenWishScreenPayload;
import com.hmc.zenkai.core.network.feature.wishes.SyncWishTogglesPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ShenLongEntity extends ZenkaiDefaultNPC {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer> WISHES_REMAINING =
            SynchedEntityData.defineId(ShenLongEntity.class, EntityDataSerializers.INT);

    public ShenLongEntity(EntityType<? extends ZenkaiDefaultNPC> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WISHES_REMAINING, 1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(ZenkaiCommonAnimations.getSpawnController(this, AnimationState::getController,2*20));
        controllers.add(ZenkaiCommonAnimations.genericIdleController(this));
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, SyncWishTogglesPayload.current());
            PacketDistributor.sendToPlayer(sp, new OpenWishScreenPayload());
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    // ── Pool de deseos ────────────────────────────────────────────────────────
    public int getWishesRemaining()       { return this.entityData.get(WISHES_REMAINING); }
    public void setWishesRemaining(int n) { this.entityData.set(WISHES_REMAINING, n); }

    /**
     * Consume un deseo del pool compartido.
     * @return true si el dragón debe desaparecer (pool agotado).
     */
    public boolean consumeWish() {
        int left = getWishesRemaining() - 1;
        setWishesRemaining(left);
        return left <= 0;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("wishesRemaining", getWishesRemaining());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("wishesRemaining")) {
            setWishesRemaining(tag.getInt("wishesRemaining"));
        }
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldPlayAnimsWhileGamePaused() {
        return true;
    }

    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }

    @Override
    public void tick() {
        super.tick();
        Player nearestPlayer = this.level().getNearestPlayer(this, 64.0D);
        if (nearestPlayer != null) {
            this.getLookControl().setLookAt(nearestPlayer, 30.0F, 30.0F);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}