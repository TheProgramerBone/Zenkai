package com.hmc.zenkai.content.entity;

import com.hmc.zenkai.content.entity.ai.KiAttackGoal;
import com.hmc.zenkai.core.combat.EntityStatsManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
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
        // Melee y ki en UN controlador triggereado: 'triggerAnim' reinicia siempre, así que ya
        // no se queda pegado como el de 'swinging'.
        controllers.add(new AnimationController<>(
                this, "KiAttack", 5, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("strike",    ZenkaiCommonAnimations.ATTACK_STRIKE)
                .triggerableAnim("ki_charge", ZenkaiCommonAnimations.ATTACK_CHARGE)
                .triggerableAnim("ki_shoot",  ZenkaiCommonAnimations.ATTACK_SHOOT));
    }

    /** Melee: dispara el strike triggereado en todos los clientes que ven al mob. */
    @Override
    public boolean doHurtTarget(@org.jetbrains.annotations.NotNull net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide()) triggerAnim("KiAttack", "strike");
        return hit;
    }

    /** Añade el goal de ki SOLO si el datapack define ataques para esta entidad. Lo llama
     *  la subclase desde registerGoals (p. ej. un soldado de Freezer sí, el saibaman no). */
    protected void addKiAttackGoalIfDefined(int priority, double moveSpeed) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        var def = EntityStatsManager.get(id);
        if (def != null && def.hasKiAttacks()) {
            this.goalSelector.addGoal(priority,
                    new KiAttackGoal<>(this, def.kiAttacks(), moveSpeed));
        }
    }
}