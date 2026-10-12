package com.hmc.zenkai.content.entity.namek;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.hmc.zenkai.content.entity.overworld.SaibamanEntity;
import com.hmc.zenkai.feature.alignment.AlignmentFearGoals;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import software.bernie.geckolib.animation.*;

import java.util.EnumSet;
import java.util.List;

public class NamekianWarriorEntity extends ZenkaiDefaultMob {

    public NamekianWarriorEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    // ========================== AI ==========================

    /** Radio en el que un guerrero acude a defender a un namekiano atacado. */
    private static final double DEFEND_RADIUS = 32.0;

    /**
     * Guardián de la aldea (revisado 2026-10-11, petición del usuario: "que cumplan su función").
     * Por orden:
     *  1. Devuelve el golpe a quien le pegue y avisa a los demás guerreros (antes no avisaba).
     *  2. Defiende a los namekianos de CUALQUIER atacante (antes solo de jugadores: un zombi
     *     podía matar aldeanos delante de él).
     *  3. Ataca sin dudar a los jugadores MALVADOS (alineamiento EVIL), aunque no hayan hecho
     *     nada: los namekianos les tienen miedo, los guerreros no.
     *  4. Patrulla contra monstruos y Saibaman cercanos, como un gólem de hierro (los creepers no:
     *     reventarían la aldea).
     */
    @Override
    protected void registerGoals() {
        // IA de combate universal (ZenkaiDefaultMob.registerCombatGoals). Ojo: su ficha
        // (namekian_warrior.json) traía ki_attacks desde siempre, pero este registerGoals solo
        // ponía melee vainilla, así que nunca lanzaba ki. Ahora sí lo usa.
        registerCombatGoals(1.0D);
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, NamekianWarriorEntity.class, NamekianEntity.class)
                .setAlertOthers(NamekianWarriorEntity.class));
        this.targetSelector.addGoal(2, new DefendNamekians(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                AlignmentFearGoals::isEvilPlayer));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                NamekianWarriorEntity::isVillageThreat));
    }

    /** Monstruo que un guardián debe atacar por iniciativa propia. */
    private static boolean isVillageThreat(LivingEntity e) {
        return (e instanceof Enemy && !(e instanceof Creeper)) || e instanceof SaibamanEntity;
    }

    /** No ataca a los suyos ni a los namekianos aunque un golpe perdido le alcance. */
    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        return super.isAlliedTo(entity) || entity instanceof NamekianWarriorEntity || entity instanceof NamekianEntity;
    }

    /**
     * Fija como objetivo al último que ha herido a un namekiano cercano (el más próximo al
     * guerrero si hay varios). Antes solo contaba a jugadores y se quedaba con el primero de la
     * lista aunque estuviera a 64 bloques.
     */
    public static class DefendNamekians extends TargetGoal {
        private final NamekianWarriorEntity warrior;
        private LivingEntity attacker;

        public DefendNamekians(NamekianWarriorEntity mob) {
            super(mob, false, true);
            this.warrior = mob;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            attacker = null;
            double best = Double.MAX_VALUE;
            List<NamekianEntity> nearby = warrior.level().getEntitiesOfClass(NamekianEntity.class,
                    warrior.getBoundingBox().inflate(DEFEND_RADIUS));
            for (NamekianEntity villager : nearby) {
                LivingEntity a = villager.getLastHurtByMob();   // vanilla lo olvida a los 100 ticks
                if (a == null || !a.isAlive() || warrior.isAlliedTo(a)) continue;
                if (a instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                double d = warrior.distanceToSqr(a);
                if (d < best) { best = d; attacker = a; }
            }
            return attacker != null && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            warrior.setTarget(attacker);
            super.start();
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader levelReader) {
        return levelReader.isUnobstructed(this);
    }

    // ========================== GeckoLib ==========================

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