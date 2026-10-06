package com.hmc.zenkai.content.entity.overworld;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class SaibamanEntity extends ZenkaiDefaultMob {
    public SaibamanEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // IA de combate universal (ZenkaiDefaultMob.registerCombatGoals). Antes el cuerpo a cuerpo
        // iba por delante del ki (prioridad 1 contra 2): el mismo orden que en la Sombra hacía
        // que el melee cortara la carga de ki a medias y no llegara a disparar.
        registerCombatGoals(1.2D);

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, SaibamanEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        if (super.isAlliedTo(entity)) {
            return true;
        } else {
            return entity instanceof SaibamanEntity;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.SCALE, 0.75);
    }
}