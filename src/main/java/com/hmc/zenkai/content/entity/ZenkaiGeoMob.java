package com.hmc.zenkai.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base mínima para mobs GeckoLib del mod.
 *
 * Se encarga del plumbing de GeckoLib (cache de animación + un
 * controlador idle por defecto que reproduce "misc.idle"). Las subclases
 * solo añaden su lógica e interacciones especiales.
 *
 * Para animaciones extra (walk, attack, spawn...) sobreescribe
 * {@link #registerControllers(AnimatableManager.ControllerRegistrar)}.
 * Si la entidad no tiene animación idle, sobreescríbelo y registra otra
 * (o ninguna) para evitar que GeckoLib busque "misc.idle" inexistente.
 */
public abstract class ZenkaiGeoMob extends Mob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected ZenkaiGeoMob(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(CommonAnimations.genericIdleController(this));
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}