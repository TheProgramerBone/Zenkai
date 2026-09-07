package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * "Train with your shadow" (TrainingHubScreen): un clon del propio jugador para entrenar solo.
 * Ver {@link com.hmc.zenkai.feature.training.ShadowTrainingManager} para el spawn/tracking/reward
 * — esta clase es solo la entidad (IA + identidad), toda la resolución de stats/TP vive allí.
 *
 * NEUTRAL HASTA QUE LE PEGAN: mismo comportamiento que {@link
 * com.hmc.zenkai.content.entity.overworld.SaibamanEntity}, pero SIN su
 * NearestAttackableTargetGoal&lt;Player&gt; — el targetSelector de esta clase lleva ÚNICAMENTE
 * HurtByTargetGoal, así que nunca inicia el combate por su cuenta, solo responde a un golpe.
 *
 * MODELO: usa el PlayerModel real + el skin del dueño (resuelto vía la tab list), SIN tinte
 * oscuro — ver el javadoc de {@link com.hmc.zenkai.client.render_and_model_entities.entity.ShadowCloneRenderer}
 * para el porqué (no hay hook limpio de tinte por entidad en LivingEntityRenderer). La única
 * diferenciación visual hoy es el nombre flotante "&lt;Dueño&gt;'s Shadow".
 */
public class ShadowCloneEntity extends ZenkaiDefaultMob {

    /** Dueño (quien la invocó) — puramente informativo/para limpieza; el tracking real de
     *  "qué sombra pertenece a qué jugador" vive en ShadowTrainingManager. */
    private UUID ownerId;

    public ShadowCloneEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        addKiAttackGoalIfDefined(2, 1);
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // SOLO HurtByTargetGoal: a propósito no hay NearestAttackableTargetGoal aquí, así
        // nunca ataca primero — el pedido explícito era "IA neutral al inicio, solo se vuelve
        // agresiva cuando el jugador le golpea".
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ShadowCloneEntity.class).setAlertOthers());
    }

    public void setOwner(Player owner) {
        this.ownerId = owner.getUUID();
        Component name = Component.literal(owner.getName().getString() + "'s Shadow");
        setCustomName(name);
        setCustomNameVisible(true);
    }

    public UUID getOwnerId() { return ownerId; }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        if (super.isAlliedTo(entity)) return true;
        return entity instanceof ShadowCloneEntity;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Los números reales (vida, daño) los fija EntityStats.applyDef/mirrorToVanilla al
        // spawnear (ver ShadowTrainingManager) — estos son solo el piso mientras eso no ha
        // corrido todavía, mismo criterio que cualquier otra ZenkaiDefaultMob.
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) ownerId = tag.getUUID("owner");
    }
}
