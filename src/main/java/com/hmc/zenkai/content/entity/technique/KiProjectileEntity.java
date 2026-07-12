package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Proyectil ki (sistema nuevo, desde cero). Tipo/color/tamaño viajan como entity data
 * (el renderer los lee directamente). El DAÑO se fija en servidor al disparar
 * (kiPower × dmgMult × sizeF, ver KiFirePacket) y entra al pipeline de combate como
 * proyectil: CombatZenkaiHooks NO lo recomputa como melee (bypass por instanceof).
 *
 * Tipos especiales:
 *  - SPIRAL: oscilación perpendicular a la trayectoria (server mueve, cliente interpola).
 *  - BARRIER: no se mueve ni golpea; sigue el centro del dueño y muere al expirar
 *    (la absorción de daño vive en KiCombatServer, esta entidad es solo el visual).
 *
 * Sin gravedad; muere al chocar (bloque o entidad) o al agotar la vida.
 */
public class KiProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Byte> DATA_TYPE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);

    private static final int PROJECTILE_LIFE = 100; // ticks (5 s)

    private double damage = 0;
    private int life = PROJECTILE_LIFE;

    public KiProjectileEntity(EntityType<? extends KiProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /** Configuración al disparar (solo servidor; el data syncer propaga al cliente). */
    public void configure(LivingEntity owner, KiTechniqueType type, int rgb, int size,
                          double damage, int lifeTicks) {
        setOwner(owner);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
        this.entityData.set(DATA_SIZE, (byte) size);
        this.damage = damage;
        this.life = lifeTicks;
        refreshDimensions();
    }

    public KiTechniqueType techniqueType() {
        int i = this.entityData.get(DATA_TYPE);
        KiTechniqueType[] all = KiTechniqueType.values();
        return all[Math.min(Math.max(i, 0), all.length - 1)];
    }

    public int rgb()  { return this.entityData.get(DATA_RGB); }
    public int size() { return this.entityData.get(DATA_SIZE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE, (byte) KiTechniqueType.BLAST.ordinal());
        builder.define(DATA_RGB, 0xFFFFFF);
        builder.define(DATA_SIZE, (byte) 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key) || DATA_TYPE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float d = techniqueType() == KiTechniqueType.BARRIER
                ? 2.4f + 0.2f * size()
                : 0.3f + 0.15f * size();
        return EntityDimensions.scalable(d, d);
    }

    @Override
    public void tick() {
        super.tick();

        if (techniqueType() == KiTechniqueType.BARRIER) {
            tickBarrier();
            return;
        }

        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
            }
        }

        Vec3 vel = getDeltaMovement();
        Vec3 next = position().add(vel);

        // Espiral: oscilación perpendicular a la trayectoria (delta entre este tick y el anterior).
        if (techniqueType() == KiTechniqueType.SPIRAL && vel.lengthSqr() > 1.0e-4) {
            Vec3 perp = vel.cross(new Vec3(0, 1, 0)).normalize();
            double amp = 0.12 + 0.04 * size();
            double w = 0.55; // rad/tick
            double delta = Math.cos(tickCount * w) - Math.cos((tickCount - 1) * w);
            next = next.add(perp.scale(amp * delta * 8));
        }

        setPos(next.x, next.y, next.z);

        if (!level().isClientSide && --life <= 0) discard();
    }

    /** Sigue el centro del dueño; muere al expirar o si el dueño desaparece. */
    private void tickBarrier() {
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        setPos(c.x - getBbWidth() * 0, c.y - getBbHeight() * 0.5, c.z);
        if (!level().isClientSide && --life <= 0) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
        hit.getEntity().hurt(damageSources().mobProjectile(this, owner), (float) damage);
        discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() == HitResult.Type.BLOCK) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("damage", damage);
        tag.putInt("life", life);
        tag.putByte("ktype", this.entityData.get(DATA_TYPE));
        tag.putInt("rgb", rgb());
        tag.putByte("size", (byte) size());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        life = tag.getInt("life");
        this.entityData.set(DATA_TYPE, tag.getByte("ktype"));
        this.entityData.set(DATA_RGB, tag.getInt("rgb"));
        this.entityData.set(DATA_SIZE, tag.getByte("size"));
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false; // los proyectiles ki no reciben daño
    }
}