package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Proyectil ki (sistema nuevo, desde cero). Tipo/color/tamaño viajan como entity data
 * (el renderer los lee directamente). El DAÑO se fija en servidor al disparar
 * (kiPower × dmgMult × sizeF, ver KiFirePacket) y entra al pipeline de combate como
 * proyectil: CombatZenkaiHooks NO lo recalcula como melee (bypass por instanceof).
 * Tipos especiales:
 *  - SPIRAL: oscilación perpendicular a la trayectoria (server mueve, cliente interpola).
 *  - BARRIER: no se mueve ni golpea; sigue el centro del dueño y muere al expirar
 *    (la absorción de daño vive en KiCombatServer, esta entidad es solo el visual).
 * Explosiva: al impactar (bloque o entidad) genera daño en ÁREA con caída lineal —
 * radio 1.5 + 0.35×size, daño AoE = 60% del directo. Sin daño a bloques (griefing off).
 * El objetivo directo recibe el daño completo y se excluye del AoE (no doble golpe);
 * el dueño también se excluye (sin auto-daño).
 * Sin gravedad; muere al chocar o al agotar la vida.
 */
public class KiProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Byte> DATA_TYPE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);

    // ── Estela (SOLO cliente): historial de posiciones del centro, cabeza primero.
    //    Lo llena tick() y lo lee KiProjectileRenderer. En server queda vacío. ──
    private final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
    public java.util.Deque<Vec3> trailHistory() { return trail; }

    // ── DISK: ids de entidades ya atravesadas (solo server, para no re-golpear).
    //    No persiste en NBT a propósito: el proyectil vive segundos. ──
    private final java.util.Set<Integer> pierced = new java.util.HashSet<>();

    private double damage = 0;
    private int life = 100;
    private boolean explosive = false;

    public KiProjectileEntity(EntityType<? extends KiProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /** Configuración al disparar (solo servidor; el data syncer propaga al cliente). */
    public void configure(LivingEntity owner, KiTechniqueType type, int rgb, int size,
                          double damage, int lifeTicks, boolean explosive) {
        setOwner(owner);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
        this.entityData.set(DATA_SIZE, (byte) size);
        this.damage = damage;
        this.life = lifeTicks;
        this.explosive = explosive && !type.defensive();
        this.noCulling = true; // la estela sobresale del hitbox: sin esto desaparece al salir la bola de cámara
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
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key) || DATA_TYPE.equals(key)) refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
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

        // Estela: historial de posiciones en cliente (cabeza primero, longitud por tipo).
        if (level().isClientSide && techniqueType().hasTrail()) {
            trail.addFirst(position().add(0, getBbHeight() * 0.5, 0));
            while (trail.size() > techniqueType().trailPoints()) trail.removeLast();
        }

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
        setPos(c.x, c.y - getBbHeight() * 0.5, c.z);
        if (!level().isClientSide && --life <= 0) discard();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        if (level().getServer() == null || ModGameRules.enableKiDamage(Objects.requireNonNull(level().getServer()))) {
            hit.getEntity().hurt(damageSources().mobProjectile(this, owner), (float) damage);
        }

        if (techniqueType() == KiTechniqueType.DISK) {
            // Perforante: marca al objetivo y sigue volando. Si es explosiva, la explosión
            // queda para el impacto con bloque (onHit) — nada de tren de explosiones.
            pierced.add(hit.getEntity().getId());
            return;
        }

        if (explosive) explode(hit.getEntity().position(), hit.getEntity());
        discard();
    }

    @Override
    protected void onHit(@NotNull HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() == HitResult.Type.BLOCK) {
            if (explosive) explode(hit.getLocation(), null);
            discard();
        }
    }

    /** Poder de ki de referencia con el que se calculó el daño. Se congela al desviar,
     *  porque a partir de ahí el dueño ya no es quien disparó y la defensa del receptor
     *  se escala contra el poder del ORIGINAL, no contra el de quien lo devolvió. */
    private double refPower = 0.0;

    public double refPower() { return refPower; }

    /**
     * Radio de la explosión (Radio = Diámetro / 2).
     * Escala según el tamaño (size) y se limita o multiplica según el tipo de técnica (KiTechniqueType).
     */
    /**
     * Radio de la explosión (Radio = Diámetro / 2).
     * Escala con el tamaño (size) y aplica multiplicadores + límites máximos según la técnica.
     */
    private double explosionRadius() {
        double baseRadius = 1.2 * size();

        return switch (techniqueType()) {
            case WAVE -> {
                // Kamehameha: Onda potente de impacto amplio (Diámetro máx: 10 bloques)
                yield Math.min(baseRadius * 1.3, 5.0);
            }
            case BLAST -> {
                // Bola estándar (Diámetro máx: 7 bloques)
                yield Math.min(baseRadius, 3.5);
            }
            case LAZER -> {
                // Láser: Superconcentrado y penetrante, explosión muy reducida (Diámetro máx: 3 bloques)
                yield Math.min(baseRadius * 0.5, 1.5);
            }
            case SPIRAL -> {
                // Espiral: Onda de choque expansiva (Diámetro máx: 8.5 bloques)
                yield Math.min(baseRadius * 1.1, 4.25);
            }
            case BIG_BLAST -> {
                // Enorme y lento: Explosión masiva (Diámetro máx: 16 bloques)
                yield Math.min(baseRadius * 2.2, 8.0);
            }
            case BURST -> {
                // Ráfaga pequeña: Múltiples disparos con explosiones pequeñas (Diámetro máx: 4 bloques)
                yield Math.min(baseRadius * 0.6, 2.0);
            }
            case DISK -> {
                // Disco cortante: Impacto muy localizado (Diámetro máx: 4 bloques)
                yield Math.min(baseRadius * 0.7, 2.0);
            }
            case BARRIER -> 0.0; // Burbuja defensiva: sin explosión
        };
    }

    /**
     * Porcentaje del daño directo que se transmite como daño en área (AoE).
     * Ej: 0.80 = 80% del daño base repartido en la zona.
     */
    private double explosionAoeFactor() {
        return switch (techniqueType()) {
            case WAVE      -> 0.75; // 75% del daño en área (Onda destructiva)
            case BIG_BLAST -> 0.85; // 85% del daño en área (Devastador)
            case BLAST     -> 0.50; // 50% del daño en área (Balanceado)
            case SPIRAL    -> 0.60; // 60% del daño en área
            case DISK      -> 0.40; // 40% del daño en área (Daño más centrado en el corte)
            case BURST     -> 0.35; // 35% por cada bolita (Acumulable por cantidad)
            case LAZER     -> 0.20; // 20% en área (El daño es single-target)
            case BARRIER   -> 0.00;
        };
    }

    private void explode(Vec3 center, Entity directHit) {
        if (!(level() instanceof ServerLevel sl)) return;
        double radius = explosionRadius();
        double aoeFactor = explosionAoeFactor(); // <-- Leemos el factor dinámico
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        if (ModGameRules.enableKiDamage(sl.getServer())) {
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                    t -> t.isAlive() && t != getOwner() && t != directHit)) {
                double dist = target.position().add(0, target.getBbHeight() * 0.5, 0)
                        .distanceTo(center);
                if (dist > radius) continue;
                double falloff = 1.0 - dist / radius;

                // Aplicamos aoeFactor en lugar de la constante
                target.hurt(damageSources().mobProjectile(this, owner),
                        (float) (damage * aoeFactor * falloff));
            }
        }

        if (ModGameRules.enableKiGriefing(sl.getServer())) {
            sl.explode(this, null,
                    new SimpleExplosionDamageCalculator(true, false,
                            Optional.empty(), Optional.empty()),
                    center.x, center.y, center.z, (float) radius, false,
                    Level.ExplosionInteraction.TNT);
        } else {
            int emitters = 1 + size() / 2;
            double spread = radius * 0.35;
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                    emitters, spread, spread, spread, 0);
            int puffs = 8 + size() * 6;
            sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                    puffs, radius * 0.5, radius * 0.5, radius * 0.5, 0);
            sl.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    1.2f + 0.15f * size(), 1.25f - 0.06f * size());
        }
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity e) {
        return super.canHitEntity(e) && e != getOwner() && !pierced.contains(e.getId());
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("damage", damage);
        tag.putInt("life", life);
        tag.putBoolean("explosive", explosive);
        tag.putByte("ktype", this.entityData.get(DATA_TYPE));
        tag.putInt("rgb", rgb());
        tag.putByte("size", (byte) size());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        life = tag.getInt("life");
        explosive = tag.getBoolean("explosive");
        this.entityData.set(DATA_TYPE, tag.getByte("ktype"));
        this.entityData.set(DATA_RGB, tag.getInt("rgb"));
        this.entityData.set(DATA_SIZE, tag.getByte("size"));
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.@NotNull DamageSource source, float amount) {
        return false; // los proyectiles ki no reciben daño
    }

    /**
     * ¿Se puede desviar con kiai? La BARRIER no: no viaja, es el visual de la burbuja y
     * tickBarrier() la pega a su dueño cada tick. Cambiarle el dueño le movería la esfera
     * al que desvía mientras el pool de absorción (BARRIERS, por UUID) sigue protegiendo
     * al original. Se exige damage > 0 por lo mismo: lo que no hace daño no se devuelve.
     */
    public boolean canBeDeflected() {
        return !techniqueType().defensive() && damage > 0.0;
    }

    /**
     * Desvío por kiai: el proyectil cambia de dueño. Con eso, quien lo disparó pasa a ser
     * blanco válido (canHitEntity excluye al dueño) y quien lo devuelve queda inmune, tanto
     * al impacto como a la explosión. El daño NO se recalcula: devuelve exactamente lo que
     * traía. Llamar ANTES de invertir la dirección.
     * @return false si este proyectil no es desviable (ver canBeDeflected).
     */
    public boolean deflect(LivingEntity newOwner) {
        if (!canBeDeflected()) return false;

        if (refPower <= 0.0 && getOwner() instanceof LivingEntity original) {
            var st = com.hmc.zenkai.core.combat.ZenkaiStats.of(original);
            if (st != null) refPower = st.computeKiPowerFinal();
        }
        setOwner(newOwner);
        pierced.clear(); // DISK: puede volver a atravesar a los que ya cruzó de ida
        return true;
    }
}