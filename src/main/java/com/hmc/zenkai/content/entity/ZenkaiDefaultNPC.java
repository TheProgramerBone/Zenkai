package com.hmc.zenkai.content.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// Clase para los npc: inmortales (salvo /kill y el vacío), no se mueven, tienen una animación idle.

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

    /**
     * Inmunes a cualquier cosa salvo /kill y el vacío (la etiqueta vanilla
     * BYPASSES_INVULNERABILITY). Antes solo ignoraban lo que venía de un jugador y morían como un
     * mob cualquiera al fuego, la lava, un creeper, un rayo o el ki de un Saibaman: un maestro
     * sacado de huevo no tiene bloque marcador que lo reponga (petición del usuario, 2026-10-11).
     */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Sin llamas encima: el fuego no les hace daño y no tiene sentido verlos ardiendo. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /** Ningún efecto de poción (veneno, wither, levitación, lentitud...). */
    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return false;
    }

    /** Tampoco se congelan en la nieve polvo (el contador de congelación no pasa por hurt). */
    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(0, 0, 0);
    }

    /** Doble cierre: algunas rutas llaman a hurt sin pasar antes por isInvulnerableTo. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        return super.hurt(source, amount);
    }
}