package com.hmc.zenkai.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base mínima para mobs GeckoLib del mod.
 *
 * Extiende {@link PathfinderMob} (no solo Mob) para que las subclases puedan
 * usar goals de navegación como WaterAvoidingRandomStrollGoal, RandomStrollGoal,
 * etc. Un mob inmóvil también funciona: simplemente no añadas goals de movimiento.
 *
 * Se encarga de plumbing de GeckoLib (cache de animación + un controlador
 * idle por defecto que reproduce "misc.idle"). Las subclases solo añaden su lógica
 * e interacciones especiales.
 *
 * Para animaciones extra (walk, attack, spawn...) sobreescribe
 * {@link #registerControllers(AnimatableManager.ControllerRegistrar)}.
 * Si la entidad no tiene animación idle, sobreescríbelo y registra otra (o ninguna)
 * para evitar que GeckoLib busque "misc.idle" inexistente.
 */
public abstract class ZenkaiGeoMob extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected ZenkaiGeoMob(EntityType<? extends PathfinderMob> type, Level level) {
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
}