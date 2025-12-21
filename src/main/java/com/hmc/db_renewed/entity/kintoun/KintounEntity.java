package com.hmc.db_renewed.entity.kintoun;

import com.hmc.db_renewed.entity.CommonAnimations;
import com.hmc.db_renewed.item.ModItems;
import com.hmc.db_renewed.network.VerticalControlVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public class KintounEntity extends Animal implements GeoEntity, VerticalControlVehicle {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Ajustes
    private static final float  HORIZONTAL_SPEED = 2f;
    private static final double VERTICAL_SPEED   = 1.5f;   // subir/bajar (input)
    private static final double INPUT_DEADLINE   = 1.0E-3;

    // Asientos (offsets locales en el modelo)
    private static final Vec3 SEAT_DRIVER = new Vec3(0, 0.8, 0.25);
    private static final Vec3 SEAT_REAR   = new Vec3(0.0, 0.70,  -0.25);

    private boolean inputUp;
    private boolean inputDown;

    public KintounEntity(EntityType<? extends KintounEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setNoAi(true);
        this.noCulling = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    @Override
    public void setVerticalInput(boolean up, boolean down) {
        this.inputUp = up;
        this.inputDown = down;
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(CommonAnimations.genericIdleController(this));
    }

    // -------------------------
    // Montura + movimiento
    // -------------------------

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        // 2 puestos: conductor + copiloto
        // Permitimos LivingEntity (Player u otra entidad montable)
        return this.getPassengers().size() < 2 && passenger instanceof LivingEntity;
    }

    @Override
    public @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand hand) {
        // Deja subir si aún hay cupo
        if (!player.isPassenger() && this.getPassengers().size() < 2) {
            player.startRiding(this, true);
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (!this.isAlive()) return;

        LivingEntity rider = this.getControllingPassenger();

        if (this.isVehicle() && rider instanceof Player player) {

            // Mini “impulso” al despegar si está pegada al suelo
            if (this.onGround()) {
                Vec3 dm = this.getDeltaMovement();
                this.setDeltaMovement(dm.x, 0.45, dm.z);
            }

            // Rotación (yaw completo, pitch suave/clamp)
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();

            float pitch = Mth.clamp(player.getXRot(), -25f, 25f);
            this.setXRot(pitch);
            this.xRotO = pitch;

            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();

            // Input horizontal (WASD)
            float strafe  = player.xxa * 0.8f;
            float forward = player.zza;
            if (forward < 0) forward *= 0.25f;

            // Input vertical (SPACE/CTRL) desde packet
            double upDown = 0.0;
            if (inputUp) upDown += 1.0;
            if (inputDown) upDown -= 1.0;

            // Anti-drift: si no hay input, frena fuerte
            if (Math.abs(strafe) < INPUT_DEADLINE
                    && Math.abs(forward) < INPUT_DEADLINE
                    && Math.abs(upDown) < INPUT_DEADLINE) {

                Vec3 dm = this.getDeltaMovement().multiply(0.5, 0.5, 0.5);
                if (dm.lengthSqr() < 1.0E-5) dm = Vec3.ZERO;

                this.setDeltaMovement(dm);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.fallDistance = 0;

                if (this.onGround()) {
                    Vec3 d2 = this.getDeltaMovement();
                    this.setDeltaMovement(d2.x, 0.45, d2.z);
                }
                return;
            }

            Vec3 blended = computeTargetMotion(strafe, forward, upDown);

            this.setDeltaMovement(blended);
            this.hasImpulse = true;
            this.fallDistance = 0;

            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }

        // Sin pasajero: baja suave si no está en el piso
        if (!this.isVehicle() || this.getControllingPassenger() == null) {
            double y = this.onGround() ? 0.0 : -0.1;

            this.setDeltaMovement(0.0, y, 0.0);
            this.fallDistance = 0;

            // Mantén colisiones/ajustes vanilla
            super.travel(Vec3.ZERO);
            return;
        }

        // Fallback
        this.setDeltaMovement(Vec3.ZERO);
        this.move(MoverType.SELF, Vec3.ZERO);
    }

    private @NotNull Vec3 computeTargetMotion(float strafe, float forward, double upDown) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double x = (strafe * cos - forward * sin) * HORIZONTAL_SPEED;
        double z = (forward * cos + strafe * sin) * HORIZONTAL_SPEED;
        double y = upDown * VERTICAL_SPEED;

        // Suavizado leve (más fluido)
        Vec3 target = new Vec3(x, y, z);
        Vec3 cur = this.getDeltaMovement();

        return new Vec3(
                Mth.lerp(0.35, cur.x, target.x),
                Mth.lerp(0.35, cur.y, target.y),
                Mth.lerp(0.35, cur.z, target.z)
        );
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        // El primero que se sube controla (tu requisito)
        Entity p = this.getFirstPassenger();
        return p instanceof LivingEntity le ? le : null;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        boolean hasRider = this.isVehicle() && this.getControllingPassenger() != null;

        // Con rider: sin gravedad (vuelo). Sin rider: con gravedad (cae) + tu travel también baja suave.
        this.setNoGravity(hasRider);
    }

    // -------------------------
    // Drops / XP
    // -------------------------

    @Override
    protected int getBaseExperienceReward() {
        return 0; // sin XP
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (!this.level().isClientSide) {
            this.spawnAtLocation(ModItems.KINTOUN_ITEM.get());
        }
        super.die(source);
    }

    // -------------------------
    // Sonidos: fuera
    // -------------------------

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        // no-op
    }

    // -------------------------
    // Asientos (2 puestos)
    // -------------------------

    @Override
    public @NotNull Vec3 getPassengerRidingPosition(@NotNull Entity passenger) {
        // 0 = conductor (primero), 1 = segundo puesto
        int idx = this.getPassengers().indexOf(passenger);

        Vec3 local = (idx == 1) ? SEAT_REAR : SEAT_DRIVER;

        // rota el offset con el yaw para que quede alineado al vehículo
        Vec3 rotated = local.yRot(-this.getYRot() * Mth.DEG_TO_RAD);

        return this.position().add(rotated);
    }

    // -------------------------
    // GeckoLib
    // -------------------------

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // -------------------------
    // Animal abstract methods
    // -------------------------

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        return null;
    }
}
