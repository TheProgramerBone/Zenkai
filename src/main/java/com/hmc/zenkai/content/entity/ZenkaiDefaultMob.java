package com.hmc.zenkai.content.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// Esta clase será para los enemigos neutrales

public abstract class ZenkaiDefaultMob extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected ZenkaiDefaultMob(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(ZenkaiCommonAnimations.genericWalkController(this));
        controllers.add(ZenkaiCommonAnimations.genericAttackAnimation(
                this, ZenkaiCommonAnimations.ATTACK_STRIKE));
    }

    /** El golpe cuerpo a cuerpo dispara la animación en TODOS los clientes que ven al mob.
     *  triggerAnim viaja solo (server -> trackers), así que no hay que sincronizar nada más. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide()) {
            triggerAnim("Attack", "attack"); // ⚠ (controllerName, animName)
        }
        return hit;
    }
}