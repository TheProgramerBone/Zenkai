package com.hmc.db_renewed.entity.namekian;

import com.hmc.db_renewed.entity.CommonAnimations;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class NamekianWarriorEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NamekianWarriorEntity(EntityType<? extends NamekianWarriorEntity> type, Level level) {
        super(type, level);
    }

    // ========================== AI ==========================

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(1, new DefendNamekians(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(CommonAnimations.genericWalkIdleController(this));
        controllers.add(CommonAnimations.genericAttackAnimation(this, CommonAnimations.ATTACK_STRIKE));
    }

    public static class DefendNamekians extends TargetGoal {
        private final NamekianWarriorEntity warrior;
        private LivingEntity target;

        public DefendNamekians(NamekianWarriorEntity mob) {
            super(mob, false, true);
            this.warrior = mob;
        }

        @Override
        public boolean canUse() {
            List<NamekianEntity> nearbyTraders = this.warrior.level().getEntitiesOfClass(NamekianEntity.class, this.warrior.getBoundingBox().inflate(64));
            for (NamekianEntity trader : nearbyTraders) {
                LivingEntity attacker = trader.getLastHurtByMob();
                if (attacker instanceof Player player && !player.isCreative()) {
                    this.target = attacker;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            if (this.target != null) {
                this.warrior.setTarget(this.target);
                super.start();
            }
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader levelReader) {
        return levelReader.isUnobstructed(this);
    }

    // ========================== GeckoLib ==========================

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }


    // ========================== Sonidos ==========================

//    @Override
//    protected SoundEvent getAmbientSound() {
//        return SoundEvents.VILLAGER_AMBIENT;
//    }
//
//    @Override
//    protected SoundEvent getHurtSound(DamageSource damageSource) {
//        return SoundEvents.VILLAGER_HURT;
//    }
//
//    @Override
//    protected SoundEvent getDeathSound() {
//        return SoundEvents.VILLAGER_DEATH;
//    }

    // ========================== Extras ==========================

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.COW_STEP, 0.15F, 1.0F);
    }
}