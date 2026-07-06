package com.hmc.zenkai.content.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// Clase para los npc, inmortales, no se mueven, tienen una animación idle.

public abstract class ZenkaiDefaultNPC extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected ZenkaiDefaultNPC(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(ZenkaiCommonAnimations.genericIdleController(this));
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(0, 0, 0);
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