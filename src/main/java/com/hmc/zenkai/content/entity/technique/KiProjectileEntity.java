package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
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
 *  - SPIRAL: vuela recto. El nombre viene de la forma helicoidal (ver KiVisual/KiMeshFactory),
 *    no de la trayectoria — llevó oscilación perpendicular y se retiró porque además torcía la
 *    estela.
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

    /** Techo del historial de estela. Es el máximo que cualquier KiVisual puede pedir; grabar
     *  de más cuesta un Vec3 por tick y ahorra tener el largo declarado en dos sitios. */
    public static final int TRAIL_MAX = 34;

    /** Desduplicador de efectos por tick para el renderer, que corre por FRAME. Devuelve true
     *  una sola vez por tick. Cliente: en servidor nadie lo llama. */
    private int lastFxTick = -1;
    public boolean consumeFxTick() {
        if (lastFxTick == tickCount) return false;
        lastFxTick = tickCount;
        return true;
    }

    // ── DISK: ids de entidades ya atravesadas (solo server, para no re-golpear).
    //    No persiste en NBT a propósito: el proyectil vive segundos. ──
    private final java.util.Set<Integer> pierced = new java.util.HashSet<>();

    private double damage = 0;
    private int life = 100;
    private TechniqueEffect effect = TechniqueEffect.NONE;

    public KiProjectileEntity(EntityType<? extends KiProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /** Configuración al disparar (solo servidor; el data syncer propaga al cliente). */
    public void configure(LivingEntity owner, KiTechniqueType type, int rgb, int size,
                          double damage, int lifeTicks, TechniqueEffect effect) {
        setOwner(owner);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
        this.entityData.set(DATA_SIZE, (byte) size);
        this.damage = damage;
        this.life = lifeTicks;
        this.effect = (effect == null || !type.allowsEffect(effect))
                ? TechniqueEffect.NONE : effect;
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
        // Un tamaño 5 no significa lo mismo en un láser que en un big blast: la escala vive en
        // el tipo, que es quien conoce su propia proporción.
        float d = (float) techniqueType().projectileSize(size());
        return EntityDimensions.scalable(d, d);
    }

    @Override
    public void tick() {
        super.tick();

        // Lo que no viaja va pegado al dueño. La barrera expira sin más; la explosión DETONA
        // al expirar — su `life` es la mecha, no su duración.
        if (!techniqueType().travels()) {
            tickAttached();
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

        setPos(next.x, next.y, next.z);

        // Estela: historial de posiciones en cliente (cabeza primero). Se graba SIEMPRE hasta
        // TRAIL_MAX y es el cliente (KiVisual) quien decide cuántos puntos dibuja y con qué
        // ancho. Antes la longitud la mandaba el enum, que es código común: con eso, activar
        // una estela era tocar código de identidad compartido con el servidor.
        if (level().isClientSide) {
            trail.addFirst(position().add(0, getBbHeight() * 0.5, 0));
            while (trail.size() > TRAIL_MAX) trail.removeLast();
        }

        if (!level().isClientSide && --life <= 0) discard();
    }

    /** Sigue el centro del dueño; muere al expirar o si el dueño desaparece. */
    private void tickAttached() {
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        setPos(c.x, c.y - getBbHeight() * 0.5, c.z);

        if (!level().isClientSide && --life <= 0) {
            if (techniqueType() == KiTechniqueType.EXPLOSION) detonate(c, null);
            discard();
        }
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

        detonate(hit.getEntity().position(), hit.getEntity());
        discard();
    }

    @Override
    protected void onHit(@NotNull HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() == HitResult.Type.BLOCK) {
            detonate(hit.getLocation(), null);
            discard();
        }
    }

    /** Poder de ki de referencia con el que se calculó el daño. Se congela al desviar,
     *  porque a partir de ahí el dueño ya no es quien disparó y la defensa del receptor
     *  se escala contra el poder del ORIGINAL, no contra el de quien lo devolvió. */
    private double refPower = 0.0;

    public double refPower() { return refPower; }

    /**
     * Impacto: daño en ÁREA siempre, rotura de bloques solo con el efecto EXPLOSIVE.
     * ANTES el área y la destrucción iban juntas bajo la marca `explosive`; separarlas es lo
     * que hace que la marca signifique una cosa sola. Consecuencia asumida: ahora TODA técnica
     * reparte algo de área, según el aoeFactor de su tipo (un láser, un 20 %).
     * El objetivo directo y el dueño quedan fuera del área: el primero ya cobró el golpe
     * entero y el segundo nunca se daña a sí mismo — el autodaño de la explosión es otra cosa
     * y se aplica en KiFirePacket.
     */
    private void detonate(Vec3 center, Entity directHit) {
        if (!(level() instanceof ServerLevel sl)) return;

        double radius = techniqueType().explosionRadius(size());
        if (radius <= 0.0) return;

        double aoe = techniqueType().aoeFactor();
        double edge = techniqueType().aoeEdgeFalloff();
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        if (aoe > 0.0 && ModGameRules.enableKiDamage(sl.getServer())) {
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                    t -> t.isAlive() && t != getOwner() && t != directHit)) {
                double dist = target.position().add(0, target.getBbHeight() * 0.5, 0)
                        .distanceTo(center);
                if (dist > radius) continue;
                // Los proyectiles caen a cero en el borde; la explosión conserva `edge`, que es
                // lo que la convierte en una zona de muerte y no en un golpe con halo.
                double falloff = 1.0 - (1.0 - edge) * (dist / radius);
                target.hurt(damageSources().mobProjectile(this, owner),
                        (float) (damage * aoe * falloff));
            }
        }

        boolean grief = effect == TechniqueEffect.EXPLOSIVE
                && ModGameRules.enableKiGriefing(sl.getServer());
        if (grief) {
            sl.explode(this, null,
                    new SimpleExplosionDamageCalculator(true, false,
                            Optional.empty(), Optional.empty()),
                    center.x, center.y, center.z, (float) radius, false,
                    Level.ExplosionInteraction.TNT);
            return;
        }

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

    @Override
    protected boolean canHitEntity(@NotNull Entity e) {
        return super.canHitEntity(e) && e != getOwner() && !pierced.contains(e.getId());
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("damage", damage);
        tag.putInt("life", life);
        tag.putInt("effect", effect.ordinal());
        tag.putByte("ktype", this.entityData.get(DATA_TYPE));
        tag.putInt("rgb", rgb());
        tag.putByte("size", (byte) size());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        life = tag.getInt("life");
        effect = tag.contains("effect") ? TechniqueEffect.byOrdinal(tag.getInt("effect"))
                : (tag.getBoolean("explosive") ? TechniqueEffect.EXPLOSIVE : TechniqueEffect.NONE);
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

    /** Solo se devuelve lo que VIAJA. Ni la barrera ni la explosión: la primera es el visual de
     *  una burbuja pegada a su dueño y devolverla movería la esfera sin mover el pool de
     *  absorción; la segunda es una autodetonación, y devolvérsela a alguien no significa nada. */
    public boolean canBeDeflected() {
        return techniqueType().travels() && damage > 0.0;
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
            var st = ZenkaiStats.of(original);
            if (st != null) refPower = st.computeKiPowerFinal();
        }
        setOwner(newOwner);
        pierced.clear(); // DISK: puede volver a atravesar a los que ya cruzó de ida
        return true;
    }
}