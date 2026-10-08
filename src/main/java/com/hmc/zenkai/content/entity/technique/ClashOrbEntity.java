package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.feature.technique.clash.ClashManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Esfera de choque EN EL MUNDO: el punto donde se empujan dos haces durante un choque
 * (ClashManager). Solo es un marcador visual SINCRONIZADO — la ven los dos participantes y
 * cualquier espectador, que no tienen el HUD del choque. Sin física, sin colisión,
 * invulnerable, y NO se guarda con el mundo (un choque no sobrevive a un reinicio).
 *
 * Lleva dos «lóbulos», uno por haz, cada uno con su color (rgb + segundo color) y el tamaño
 * de la cabeza de ese haz — mientras dura el choque las cabezas no se pintan
 * (KiVfxProjectileRenderer, isClashHeld) y los lóbulos ocupan su sitio —, y el eje que
 * va del punto de contacto hacia el haz A. Lo que el cliente necesita para animarla viaja aquí;
 * el resultado SOLO se escribe al resolver ({@link #resolve}): antes, la esfera no se inclina
 * hacia nadie, porque ni los participantes saben todavía quién va a ganar.
 *
 * La anima {@code ClashOrbRenderer} (cliente) a partir de {@code tickCount} y {@link #resolvedAt()}.
 */
public class ClashOrbEntity extends Entity {

    public static final byte OUTCOME_NONE = 0, OUTCOME_A = 1, OUTCOME_B = 2, OUTCOME_TIE = 3;

    private static final EntityDataAccessor<Integer> DATA_RGB_A =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RGB_B =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RGB2_A =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RGB2_B =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.INT);
    /** Del punto de contacto hacia el haz A (unitario). */
    private static final EntityDataAccessor<Vector3f> DATA_AXIS =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.VECTOR3);
    /** Diámetro del lóbulo de cada haz, en bloques: el de su cabeza, a la que sustituye. */
    private static final EntityDataAccessor<Float> DATA_SIZE_A =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIZE_B =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_OUTCOME =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.BYTE);
    /** {@code tickCount} en que se resolvió (-1 mientras no). Relativo a la entidad y no al
     *  reloj del mundo: el cliente cuenta su propio tickCount desde que la vio aparecer. */
    private static final EntityDataAccessor<Integer> DATA_RESOLVED_AT =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.INT);

    /** Solo cliente: último tickCount en que el renderer soltó partículas (una vez por tick,
     *  no por frame). */
    public int lastFxTick = -1;

    /**
     * Quién está en cada lado y si se admite gente nueva, para el aviso «Clic derecho para ayudar»
     * de CUALQUIER cliente cercano (ClashJoinClient), que no tiene el roster del HUD porque no está
     * en el choque. Claves: {@code a}/{@code b} (ids de entidad, líder primero), {@code open}
     * (el choque aún admite incorporaciones: no se ha resuelto), {@code max} (tamaño máximo de
     * equipo) y {@code range} (distancia para unirse). Es solo una pista para el aviso: quien
     * decide de verdad es ClashManager.join.
     */
    private static final EntityDataAccessor<CompoundTag> DATA_ROSTER =
            SynchedEntityData.defineId(ClashOrbEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Red de seguridad: ningún choque dura tanto, ni con incorporaciones que alarguen el plazo
     *  (la guarda real es ClashManager.ownsOrb). */
    private static final int MAX_LIFE_TICKS = 20 * 120;

    public ClashOrbEntity(EntityType<? extends ClashOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RGB_A, 0xFFFFFF);
        builder.define(DATA_RGB_B, 0xFFFFFF);
        builder.define(DATA_RGB2_A, -1);
        builder.define(DATA_RGB2_B, -1);
        builder.define(DATA_AXIS, new Vector3f(0f, 0f, 1f));
        builder.define(DATA_SIZE_A, 1.5f);
        builder.define(DATA_SIZE_B, 1.5f);
        builder.define(DATA_OUTCOME, OUTCOME_NONE);
        builder.define(DATA_RESOLVED_AT, -1);
        builder.define(DATA_ROSTER, new CompoundTag());
    }

    /** Configuración al nacer (solo servidor). */
    public void configure(int rgbA, int rgb2A, int rgbB, int rgb2B, Vec3 axisToA,
                          float sizeA, float sizeB) {
        entityData.set(DATA_RGB_A, rgbA);
        entityData.set(DATA_RGB2_A, rgb2A);
        entityData.set(DATA_RGB_B, rgbB);
        entityData.set(DATA_RGB2_B, rgb2B);
        entityData.set(DATA_AXIS, new Vector3f((float) axisToA.x, (float) axisToA.y, (float) axisToA.z));
        entityData.set(DATA_SIZE_A, sizeA);
        entityData.set(DATA_SIZE_B, sizeB);
    }

    /** Diámetro de cada mitad (solo servidor): crece con los miembros de su equipo. */
    public void setLobeSizes(float sizeA, float sizeB) {
        entityData.set(DATA_SIZE_A, sizeA);
        entityData.set(DATA_SIZE_B, sizeB);
    }

    /** Actualiza quién está en cada lado (solo servidor). */
    public void setRoster(int[] sideA, int[] sideB, boolean open, int max, float range) {
        CompoundTag t = new CompoundTag();
        t.putIntArray("a", sideA);
        t.putIntArray("b", sideB);
        t.putBoolean("open", open);
        t.putInt("max", max);
        t.putFloat("range", range);
        entityData.set(DATA_ROSTER, t);
    }

    /** Lado del choque en que está esta entidad: 0 = A, 1 = B, -1 = ninguno. */
    public int sideOf(int entityId) {
        CompoundTag t = entityData.get(DATA_ROSTER);
        for (int id : t.getIntArray("a")) if (id == entityId) return 0;
        for (int id : t.getIntArray("b")) if (id == entityId) return 1;
        return -1;
    }

    public int[] sideIds(int side) {
        return entityData.get(DATA_ROSTER).getIntArray(side == 0 ? "a" : "b");
    }

    public int sideSize(int side) {
        return entityData.get(DATA_ROSTER).getIntArray(side == 0 ? "a" : "b").length;
    }

    public boolean joinOpen() { return entityData.get(DATA_ROSTER).getBoolean("open"); }
    public int teamMax() { return entityData.get(DATA_ROSTER).getInt("max"); }
    public float joinRange() { return entityData.get(DATA_ROSTER).getFloat("range"); }

    /** El choque se resolvió: a partir de aquí la esfera puede contar quién ganó. */
    public void resolve(byte outcome) {
        entityData.set(DATA_OUTCOME, outcome);
        entityData.set(DATA_RESOLVED_AT, tickCount);
    }

    public int rgbA() { return entityData.get(DATA_RGB_A); }
    public int rgbB() { return entityData.get(DATA_RGB_B); }
    public int rgb2A() { return entityData.get(DATA_RGB2_A); }
    public int rgb2B() { return entityData.get(DATA_RGB2_B); }
    public Vector3f axis() { return entityData.get(DATA_AXIS); }
    public float sizeA() { return entityData.get(DATA_SIZE_A); }
    public float sizeB() { return entityData.get(DATA_SIZE_B); }
    public byte outcome() { return entityData.get(DATA_OUTCOME); }
    public int resolvedAt() { return entityData.get(DATA_RESOLVED_AT); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // Solo vive mientras su choque la reconozca: si el choque se abortó por una ruta que no
        // la retiró, o el servidor la cargó de algún modo, desaparece sola en vez de quedarse
        // flotando para siempre.
        if (tickCount > MAX_LIFE_TICKS || !ClashManager.ownsOrb(this)) discard();
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        // Se ve desde lejos aunque sea pequeña: un choque es un acontecimiento de la pelea.
        double d = 96.0;
        return distSq < d * d;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
