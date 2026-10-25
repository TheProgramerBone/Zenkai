package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.feature.race.MajinAbsorption;
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

import java.util.UUID;

/**
 * Masa de la técnica ABSORB del Majin (ver {@link MajinAbsorption}): una bola blanda del COLOR DE LA PIEL del Majin que
 * viaja en línea recta y se queda con la PRIMERA criatura que toca. Al impactar el servidor decide si la absorbe
 * ({@link MajinAbsorption#onMassHit}); haya absorbido o no, la masa se acaba ahí. También se acaba al chocar con un
 * bloque o al agotar su alcance. No es un KiProjectileEntity por el mismo motivo que {@link SwordWaveEntity}: aquel vive
 * atado a un KiTechniqueType y al pipeline de daño de ki, y esto es una técnica física con su propia regla.
 *
 * Movimiento idéntico en servidor y cliente (setPos por tick con la velocidad del paquete de spawn). Solo el servidor
 * detecta impactos. Sin persistencia: vive un instante.
 */
public class AbsorbMassEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(AbsorbMassEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private int life = 12;

    public AbsorbMassEntity(EntityType<? extends AbsorbMassEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Solo servidor. {@code velocity} en bloques por tick. */
    public void configure(ServerPlayer owner, int lifeTicks, Vec3 velocity, int rgb) {
        this.ownerId = owner.getUUID();
        this.life = lifeTicks;
        setDeltaMovement(velocity);
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
    }

    public int rgb() { return this.entityData.get(DATA_RGB); }

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {
        builder.define(DATA_RGB, 0xF2A0C4);
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
            // Goterones de masa detrás: lo que la hace leerse como algo blando que se arrastra.
            if (random.nextInt(2) == 0) {
                level().addParticle(ModParticles.spark(rgb(), 0.35f),
                        getX() + (random.nextDouble() - 0.5) * 0.4, getY() + (random.nextDouble() - 0.5) * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * 0.4,
                        -vel.x * 0.1, -0.02, -vel.z * 0.1);
            }
        }
        setPos(to.x, to.y, to.z);
    }

    /** @return false si hay que destruirla (impacto, bloque, dueño ausente). */
    private boolean tickServer(ServerLevel sl, Vec3 from, Vec3 to) {
        if (!(sl.getEntity(ownerId == null ? new UUID(0, 0) : ownerId) instanceof ServerPlayer owner)) return false;

        var clip = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = clip.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? clip.getLocation() : to;

        // Barrido del tramo recorrido este tick: la primera criatura viva (la más cercana al origen del tramo).
        AABB box = new AABB(from, end).inflate(0.55);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != owner && x.isAlive() && !x.isSpectator())) {
            double d = e.position().distanceToSqr(from);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) {
            MajinAbsorption.onMassHit(owner, best);
            return false;
        }
        if (blocked) {
            sl.sendParticles(ParticleTypes.ITEM_SLIME, end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.05);
            return false;
        }
        return true;
    }

    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) { }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
}
