package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hoja de luz de la técnica LIGHT_BLADE: una media luna de ki que sale del tajo y VIAJA en línea recta,
 * atravesando a quien alcanza (cada objetivo recibe UN golpe) hasta que se le acaba el alcance o choca con
 * un bloque. No es un KiProjectileEntity a propósito: aquel vive atado a un KiTechniqueType y al pipeline
 * de daño de ki, y esto es una técnica FÍSICA (daño por STR, gasta estamina, mismo puente de defensa que
 * el resto de físicas vía {@link PhysicalCombatServer#waveHit}).
 *
 * Movimiento idéntico en servidor y cliente (setPos por tick con la velocidad del paquete de spawn), así el
 * cliente no depende de la interpolación para verla suave. Solo el servidor detecta impactos. Sin
 * persistencia: vive menos de un segundo.
 */
public class SwordWaveEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(SwordWaveEntity.class, EntityDataSerializers.INT);
    /** Ladeo de la media luna en grados (el tajo es en diagonal). */
    private static final EntityDataAccessor<Integer> DATA_TILT =
            SynchedEntityData.defineId(SwordWaveEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private double damage;
    private int life = 20;
    private final Set<Integer> hit = new HashSet<>();

    public SwordWaveEntity(EntityType<? extends SwordWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;   // el renderer lo decide la media luna, no su hitbox
    }

    /** Solo servidor. {@code velocity} en bloques por tick. */
    public void configure(ServerPlayer owner, double damage, int lifeTicks, Vec3 velocity, int rgb, int tiltDeg) {
        this.ownerId = owner.getUUID();
        this.damage = damage;
        this.life = lifeTicks;
        setDeltaMovement(velocity);
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
        this.entityData.set(DATA_TILT, tiltDeg);
    }

    public int rgb() { return this.entityData.get(DATA_RGB); }
    public int tilt() { return this.entityData.get(DATA_TILT); }

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {
        builder.define(DATA_RGB, 0x8FE8FF);
        builder.define(DATA_TILT, 25);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 vel = getDeltaMovement();
        Vec3 to = from.add(vel);

        if (level() instanceof ServerLevel sl) {
            if (--life < 0 || !tickServer(sl, from, to)) {
                discard();
                return;
            }
        } else {
            // Chispas en el filo y estela de partículas detrás: lo que la hace pesada de ver.
            int rgb = rgb();
            for (int i = 0; i < 2; i++) {
                level().addParticle(ModParticles.spark(rgb, 0.5f),
                        getX() + (random.nextDouble() - 0.5) * 1.2, getY() + (random.nextDouble() - 0.5) * 1.0,
                        getZ() + (random.nextDouble() - 0.5) * 1.2,
                        -vel.x * 0.2, 0.02, -vel.z * 0.2);
            }
        }
        setPos(to.x, to.y, to.z);
    }

    /** @return false si hay que destruirla (bloque, dueño ausente). */
    private boolean tickServer(ServerLevel sl, Vec3 from, Vec3 to) {
        if (!(sl.getEntity(ownerId == null ? new UUID(0, 0) : ownerId) instanceof ServerPlayer owner)) return false;

        var clip = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = clip.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? clip.getLocation() : to;

        // Barrido a lo largo del tramo: la media luna mide ~1.8 de ancho y 1.6 de alto.
        AABB box = new AABB(from, end).inflate(0.9, 0.8, 0.9);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != owner && x.isAlive() && !hit.contains(x.getId()))) {
            hit.add(e.getId());
            PhysicalCombatServer.waveHit(owner, e, damage, PhysicalTechnique.LIGHT_BLADE);
            Vec3 d = getDeltaMovement().normalize();
            e.knockback(0.6, -d.x, -d.z);
        }
        if (blocked) {
            sl.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 10, 0.5, 0.5, 0.5, 0.25);
            return false;
        }
        return true;
    }

    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) { }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
}
