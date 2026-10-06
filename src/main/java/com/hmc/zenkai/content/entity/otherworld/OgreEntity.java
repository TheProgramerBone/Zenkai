package com.hmc.zenkai.content.entity.otherworld;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Ogro del HFIL (oni) — propuesta I de .claude/pendiente/worldgen-pendiente.md. Canon desde Dragon
 * Ball original: los ogros trabajan para el Rey Yemma en el Otro Mundo (Goz y Mez, el rojo y el
 * azul, son los guardias del HFIL). Dos tipos de entidad (red_ogre / blue_ogre) con esta misma
 * clase; solo cambia la textura (skins del usuario, en .claude/imagenes_videos_referencias/entidades).
 *
 * NEUTRAL: no busca pelea. Es un trabajador del infierno, no un monstruo — como el piglin
 * zombificado: si le pegas, te persigue él y avisa a los ogros cercanos (HurtByTargetGoal con
 * setAlertOthers; rojos y azules comparten clase, así que se defienden entre sí).
 *
 * GUARDIA: si tiene un puesto (el caldero de los oni lo fija al generarlos, ver
 * HfilOniCauldronFeature), no se aleja de él más que GUARD_RADIUS. El puesto se GUARDA en el NBT:
 * vanilla no persiste Mob.restrictTo, así que sin esto el ogro olvidaría su caldero al recargar el
 * chunk y acabaría vagando por el bioma.
 *
 * Humanoide GeckoLib con rig de jugador (geo/ogre.geo.json, UV de skin estándar 64x64 con segunda
 * capa) para reusar las animaciones de combate de zenkai_animations, como pide
 * .claude/docs/npcs-de-combate.md. Kit de combate en el JSON de zenkai_entities (cuerpo a cuerpo,
 * técnicas físicas y guardia; sin ki: los ogros pegan, no lanzan energía), con la IA universal de
 * ZenkaiDefaultMob.registerCombatGoals.
 */
public class OgreEntity extends ZenkaiDefaultMob {

    public static final int GUARD_RADIUS = 10;
    private static final String GUARD_TAG = "GuardPos";

    public OgreEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // IA de combate universal (ZenkaiDefaultMob): físicas, cuerpo a cuerpo y guardia salen
        // de la ficha (red_ogre/blue_ogre.json: physical_attacks, can_block).
        registerCombatGoals(1.1D);
        // Lo propio del ogro: volver a su puesto (si lo tiene) cuando no pelea.
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8D));

        // Solo responde: nada de NearestAttackableTargetGoal.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    /** Fija el puesto de guardia (centro y radio que no abandona). */
    public void guard(BlockPos center) {
        restrictTo(center, GUARD_RADIUS);
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        return super.isAlliedTo(entity) || entity instanceof OgreEntity;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasRestriction()) tag.put(GUARD_TAG, NbtUtils.writeBlockPos(getRestrictCenter()));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, GUARD_TAG).ifPresent(this::guard);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                // Más grandes que un jugador: los ogros del anime sacan una cabeza a cualquiera.
                .add(Attributes.SCALE, 1.2D);
    }
}
