package com.hmc.db_renewed.entity.shenlong;

import com.hmc.db_renewed.entity.CommonAnimations;
import com.hmc.db_renewed.network.wishes.OpenWishScreenPayload;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ShenLongEntity extends Mob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShenLongEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(CommonAnimations.getSpawnController(this, AnimationState::getController,2*20));
        controllers.add(CommonAnimations.genericIdleController(this));
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new OpenWishScreenPayload());
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
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
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(0, 0, 0);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ||
            source.is(DamageTypeTags.IS_EXPLOSION) ||
            source.is(DamageTypeTags.IS_FREEZING) ||
            source.is(DamageTypeTags.IS_FALL) ||
            source.is(DamageTypeTags.IS_LIGHTNING) ||
            source.is(DamageTypeTags.IS_FIRE))
        {
            return super.isInvulnerableTo(source);
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile proj && proj.getOwner() instanceof Player) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.getEntity() instanceof Player) return false;
            Entity direct = source.getDirectEntity();
            if (direct instanceof Projectile proj && proj.getOwner() instanceof Player) return false;
        }
        return super.hurt(source, amount);
    }
}
