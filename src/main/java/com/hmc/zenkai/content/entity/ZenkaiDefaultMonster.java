package com.hmc.zenkai.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// Enemigos agresivos, saibaman, dinosaurios, etc.

public abstract class ZenkaiDefaultMonster extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected ZenkaiDefaultMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(ZenkaiCommonAnimations.genericWalkIdleController(this));
        controllers.add(ZenkaiCommonAnimations.genericAttackAnimation(this, ZenkaiCommonAnimations.ATTACK_STRIKE));
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}