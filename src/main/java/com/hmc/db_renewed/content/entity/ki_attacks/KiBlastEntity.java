package com.hmc.db_renewed.content.entity.ki_attacks;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class KiBlastEntity extends AbstractHurtingProjectile implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean playedSpawn = false;

    private static final RawAnimation SPAWN_ANIM = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("idle");

    public KiBlastEntity(EntityType<? extends KiBlastEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setRemainingFireTicks(0);
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }


    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult result) {
        super.onHitEntity(result);

        var target = result.getEntity();
        var owner  = this.getOwner();

        if (owner instanceof LivingEntity livingOwner) {
            target.hurt(
                    this.damageSources().indirectMagic(this, livingOwner),
                    this.power
            );
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected float getInertia() {
        return 1.0F; // sin desaceleración
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"controller",0,this::predicate));
    }

    private PlayState predicate(AnimationState<GeoAnimatable> animationState) {
        if (!playedSpawn) {
            animationState.setAnimation(SPAWN_ANIM);
            if (animationState.getController().hasAnimationFinished()) {
                playedSpawn = true;
            }
            return PlayState.CONTINUE;
        }
        animationState.setAnimation(IDLE_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private boolean charging = false;

    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    public boolean isCharging() {
        return charging;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.charging) {
            if (this.getOwner() instanceof net.minecraft.world.entity.LivingEntity owner) {
                Vec3 look = owner.getLookAngle();
                double dist = 1.0; // ~1 bloque delante
                double tx = owner.getX() + look.x * dist;
                double ty = owner.getEyeY() - 0.2;
                double tz = owner.getZ() + look.z * dist;
                Vec3 target = new Vec3(tx, ty, tz);

                Vec3 current = this.position();
                double distanceSq = current.distanceToSqr(target);

                // Si está MUY lejos (teleport, lag gordo, etc.), haz snap directo
                if (distanceSq > 9.0) { // >3 bloques
                    this.setPos(target);
                    this.setDeltaMovement(Vec3.ZERO);
                } else {
                    // Movimiento suave hacia el objetivo (factor 0.5–0.8 ajustable)
                    double lerpFactor = 0.6;
                    Vec3 newPos = current.lerp(target, lerpFactor);
                    this.setPos(newPos);
                    this.setDeltaMovement(Vec3.ZERO);
                }
            } else {
                this.charging = false;
            }
        }

        // resto de lógica normal de proyectil (cuando NO está charging)...
    }


    private float power = 4.0F;

    public void setPower(float power) {
        this.power = power;
    }

    public float getPower() {
        return power;
    }

}
