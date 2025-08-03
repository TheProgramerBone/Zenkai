package com.hmc.db_renewed.entity.namekian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class NamekianWarriorEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean angry = false;

    public NamekianWarriorEntity(EntityType<? extends NamekianWarriorEntity> type, Level level) {
        super(type, level);
    }

    // ========================== AI ==========================

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new DefendTradersGoal(this)); // <- Aquí
    }

    private boolean shouldAttack(LivingEntity target) {
        if (target instanceof Player) {
            return angry;
        }
        return false;
    }

    public class DefendTradersGoal extends TargetGoal {
        private final NamekianWarriorEntity warrior;
        private LivingEntity target;

        public DefendTradersGoal(NamekianWarriorEntity mob) {
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

    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "walk_controller",
                4, // ticks delay before state change
                state -> {
                    if (state.isMoving()) {
                        return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
                    }
                    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
                }
        ));

        controllers.add(new AnimationController<>(
                this,
                "attack_controller",
                0, // play instantly
                state -> {
                    if (this.swinging && state.getController().getAnimationState() == AnimationController.State.STOPPED) {
                        return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
                    }
                    return PlayState.CONTINUE;
                }
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.swinging && this.level() instanceof ServerLevel) {
            this.triggerAnim("controller", "attack");
        }
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