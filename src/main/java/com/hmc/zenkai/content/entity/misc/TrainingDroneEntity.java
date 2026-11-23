package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.content.entity.EffectImmune;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.gravity.DroneDifficulty;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Dron de entrenamiento (Fase 4 de la 0.7.0). Lo genera la consola de la cámara de gravedad al empezar una
 * ronda ({@link com.hmc.zenkai.feature.gravity.GravityDroneRound}) y desaparece al acabar: el ítem nunca sale
 * de la máquina, la entidad es solo su «proyección» en la sala.
 *
 * <ul>
 *   <li><b>Daño:</b> NO tiene vida que bajar. Solo cuenta el golpe de su dueño (cuerpo a cuerpo, ki o
 *       cualquier daño cuyo atacante sea él) y se lo pasa a la ronda; lo demás se ignora. Con PL alto un
 *       golpe normal lo haría pedazos, por eso el modo agresivo cuenta golpes y no vida. {@code hurt} no
 *       llama a super: tampoco entra en la tubería de combate, así que golpearlo NO da TP de combate.</li>
 *   <li><b>Vuelo:</b> sin gravedad ni IA de vanilla. Pasivo: va de una celda del interior a otra. Agresivo:
 *       ronda al dueño a 3–6 bloques y le dispara un ki pequeño; el daño lo resuelve
 *       {@code CombatZenkaiHooks} (un % del body máximo que nunca baja de 1).</li>
 *   <li><b>Dificultad</b> ({@link DroneDifficulty}): velocidad, cadencia de disparo, daño y ESQUIVA. Esquivar
 *       = el golpe del dueño no cuenta y el dron se aparta de un salto a otra celda; luego pasa
 *       {@link DroneDifficulty#DODGE_COOLDOWN_TICKS} sin poder volver a esquivar.</li>
 *   <li><b>Persistencia:</b> nunca se guarda (tipo con {@code noSave()} en ModEntities); si su consola o su ronda ya no
 *       existen, se borra sola en el siguiente tick.</li>
 * </ul>
 * Textura por desgaste (4 estados, {@code DATA_WEAR}) y ojo por estado ({@code DATA_STATE}: pendiente,
 * golpeado o agresivo), en {@code TrainingDroneRenderer}.
 */
public class TrainingDroneEntity extends Mob implements GeoEntity, EffectImmune {

    public static final byte STATE_PENDING = 0, STATE_HIT = 1, STATE_AGGRESSIVE = 2;
    /** Color del ki de los drones (rojo de aviso, el mismo tono que el ojo agresivo). */
    public static final int SHOT_RGB = 0xFF4A3A;

    private static final EntityDataAccessor<Integer> DATA_WEAR =
            SynchedEntityData.defineId(TrainingDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(TrainingDroneEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Solo servidor.
    @Nullable private BlockPos console;
    @Nullable private UUID owner;
    private boolean aggressive;
    private DroneDifficulty difficulty = DroneDifficulty.NORMAL;
    private int hits;
    private int dodgeCooldown;
    @Nullable private Vec3 goal;
    private int goalTicks;
    private int shotCooldown;

    public TrainingDroneEntity(EntityType<? extends TrainingDroneEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_WEAR, 0);
        b.define(DATA_STATE, STATE_PENDING);
    }

    /** Lo llama la ronda antes de añadirla al mundo. */
    public void setup(BlockPos console, UUID owner, boolean aggressive, DroneDifficulty difficulty, int wearStage) {
        this.console = console.immutable();
        this.owner = owner;
        this.aggressive = aggressive;
        this.difficulty = difficulty;
        this.entityData.set(DATA_WEAR, Mth.clamp(wearStage, 0, 3));
        this.entityData.set(DATA_STATE, aggressive ? STATE_AGGRESSIVE : STATE_PENDING);
        this.shotCooldown = 40 + random.nextInt(40);   // primer disparo escalonado: que no disparen todos a la vez
    }

    public int wearStage() { return entityData.get(DATA_WEAR); }
    public byte droneState() { return entityData.get(DATA_STATE); }
    public void markHit() { entityData.set(DATA_STATE, STATE_HIT); }
    public int addHit() { return ++hits; }
    /** % del body máximo que quita un impacto de este dron (lo lee CombatZenkaiHooks). */
    public double shotBodyPercent() { return difficulty.shotBodyPercent; }

    /** Estallido al destruirlo (modo agresivo): chispas y sonido, sin explosión de vanilla. */
    public void burst() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ModParticles.impact(SHOT_RGB, 1.2f), getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ModParticles.spark(0xC8C8C8, 1.0f), getX(), getY() + 0.3, getZ(), 12, 0.25, 0.25, 0.25, 0.2);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.7f);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        GravityChamberBlockEntity be = console != null && sl.getBlockEntity(console) instanceof GravityChamberBlockEntity c ? c : null;
        if (be == null || !be.droneRound().owns(this)) { discard(); return; }
        if (dodgeCooldown > 0) dodgeCooldown--;

        ServerPlayer sp = owner == null ? null : (sl.getPlayerByUUID(owner) instanceof ServerPlayer p ? p : null);
        long[] cells = be.droneRound().flightCells();
        if (aggressive && sp != null) tickAggressive(sl, sp, cells);
        else tickPassive(cells);

        // Encara al dueño (o hacia donde vuela si no hay dueño cerca).
        Vec3 look = sp != null ? sp.getEyePosition().subtract(position()) : getDeltaMovement();
        if (look.horizontalDistanceSqr() > 1.0e-4) {
            float yaw = (float) (Mth.atan2(look.z, look.x) * (180.0 / Math.PI)) - 90.0f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    private void tickPassive(long[] cells) {
        // Un dron golpeado se queda quieto flotando: ya no hace falta perseguirlo.
        if (droneState() == STATE_HIT) { setDeltaMovement(getDeltaMovement().scale(0.6)); return; }
        if (goal == null || --goalTicks <= 0 || position().distanceToSqr(goal) < 0.09) pickGoal(cells, null, 50, 90);
        steer(difficulty.passiveSpeed);
    }

    private void tickAggressive(ServerLevel sl, ServerPlayer sp, long[] cells) {
        if (goal == null || --goalTicks <= 0 || position().distanceToSqr(goal) < 0.09) pickGoal(cells, sp.position(), 30, 50);
        steer(difficulty.aggressiveSpeed);
        if (--shotCooldown <= 0) {
            int base = difficulty.shotIntervalTicks;
            shotCooldown = base + random.nextInt(Math.max(1, base / 2));
            if (hasLineOfSight(sp)) shoot(sl, sp);
        }
    }

    /**
     * Elige una celda de vuelo al azar; con {@code near}, entre las que están a 3–6 bloques de ese punto
     * (si no hay ninguna tras unos intentos, cualquiera).
     */
    private void pickGoal(long[] cells, @Nullable Vec3 near, int minTicks, int maxTicks) {
        goalTicks = minTicks + random.nextInt(Math.max(1, maxTicks - minTicks));
        if (cells.length == 0) { goal = position(); return; }
        BlockPos pick = BlockPos.of(cells[random.nextInt(cells.length)]);
        if (near != null) {
            for (int tries = 0; tries < 12; tries++) {
                BlockPos c = BlockPos.of(cells[random.nextInt(cells.length)]);
                double d2 = c.getCenter().distanceToSqr(near);
                if (d2 >= 9.0 && d2 <= 36.0) { pick = c; break; }
            }
        }
        goal = new Vec3(pick.getX() + 0.5, pick.getY() + 0.2, pick.getZ() + 0.5);
    }

    /** Acelera hacia el objetivo con suavizado; el movimiento real lo hace {@link #travel} con colisión. */
    private void steer(double speed) {
        if (goal == null) return;
        Vec3 d = goal.subtract(position());
        double len = d.length();
        Vec3 want = len < 1.0e-3 ? Vec3.ZERO : d.scale(Math.min(speed, len) / len);
        setDeltaMovement(getDeltaMovement().scale(0.75).add(want.scale(0.25)));
    }

    private void shoot(ServerLevel sl, ServerPlayer sp) {
        Vec3 eye = getEyePosition();
        Vec3 dir = sp.getEyePosition().subtract(0, 0.3, 0).subtract(eye).normalize();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sl);
        // Daño simbólico (> 0 para que haya impacto): el de verdad lo calcula CombatZenkaiHooks.
        proj.configure(this, KiTechniqueType.BLAST, SHOT_RGB, 1, 1.0, 60, TechniqueEffect.NONE);
        Vec3 spawn = eye.add(dir.scale(0.6));
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(KiTechniqueType.BLAST.speed() * 0.7));
        sl.addFreshEntity(proj);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.4f, 1.8f);
    }

    @Override
    public void travel(@NotNull Vec3 input) {
        // Sin fricción de suelo ni empuje de IA: el dron se mueve solo por su velocidad, con colisión.
        move(MoverType.SELF, getDeltaMovement());
    }

    // ── Daño ─────────────────────────────────────────────────────────────────

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (level().isClientSide) return false;
        // /kill y el vacío sí lo quitan (la ronda lo verá desaparecer y se cancela).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!(level() instanceof ServerLevel sl) || console == null) return false;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer sp) || !sp.getUUID().equals(owner)) return false;
        if (!(sl.getBlockEntity(console) instanceof GravityChamberBlockEntity be)) return false;
        // Un dron ya golpeado (pasivo) no esquiva: no tiene nada que proteger.
        if (droneState() != STATE_HIT && dodgeCooldown <= 0 && random.nextDouble() < difficulty.dodgeChance(aggressive)) {
            dodge(sl, be.droneRound().flightCells(), sp);
            return false;
        }
        be.droneRound().onDroneHit(sl, this, sp);
        return false;
    }

    /**
     * Esquiva: salta a una celda de vuelo a 2–4 bloques (la más alejada del atacante entre unas pocas al azar),
     * con un empujón fuerte para que se vea el salto. Chispas y un silbido para que el jugador sepa que falló.
     */
    private void dodge(ServerLevel sl, long[] cells, ServerPlayer attacker) {
        dodgeCooldown = DroneDifficulty.DODGE_COOLDOWN_TICKS;
        Vec3 best = null;
        double bestScore = -1;
        for (int tries = 0; tries < 10 && cells.length > 0; tries++) {
            BlockPos c = BlockPos.of(cells[random.nextInt(cells.length)]);
            Vec3 v = new Vec3(c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 0.5);
            double d2 = v.distanceToSqr(position());
            if (d2 < 4.0 || d2 > 16.0) continue;
            double score = v.distanceToSqr(attacker.position());
            if (score > bestScore) { bestScore = score; best = v; }
        }
        if (best == null) return;
        goal = best;
        goalTicks = 25;
        setDeltaMovement(best.subtract(position()).normalize().scale(0.55));
        sl.sendParticles(ModParticles.spark(0xC8C8C8, 0.8f), getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.15);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(@NotNull Entity e) { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void checkDespawn() { }
    @Override protected boolean shouldDropLoot() { return false; }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Un disparo de un dron dejó al dueño en 1 de body: la ronda agresiva termina (TP de los ya destruidos). */
    public void onOwnerKnockedOut(ServerPlayer sp) {
        if (!(level() instanceof ServerLevel sl) || console == null || !sp.getUUID().equals(owner)) return;
        if (sl.getBlockEntity(console) instanceof GravityChamberBlockEntity be) be.droneRound().defeat(sl);
    }

    /** Para gametests: id de la consola y del dueño. */
    @Nullable public BlockPos consoleForTests() { return console; }
}
