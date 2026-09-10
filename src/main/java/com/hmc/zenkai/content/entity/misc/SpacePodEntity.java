package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.spacepod.OpenGalacticMenuPayload;
import com.hmc.zenkai.feature.spacepod.SpacePodDestination;
import com.hmc.zenkai.feature.teleport.DimensionEntryTracker;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.network.vehicle.VerticalControlVehicle;
import com.hmc.zenkai.util.TeleportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SpacePodEntity extends Animal implements GeoEntity, VerticalControlVehicle {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation OPEN_ANIM   = RawAnimation.begin().thenPlay("open");
    private static final RawAnimation CLOSE_ANIM  = RawAnimation.begin().thenPlay("close");
    private static final RawAnimation LAUNCH_ANIM = RawAnimation.begin().thenPlay("launch");

    // Ajustes
    private static final float  HORIZONTAL_SPEED = 1f;
    private static final double VERTICAL_SPEED   = 1.5f;   // subir/bajar (input)
    private static final double INPUT_DEADZONE   = 1.0E-3;

    // Input vertical (lo setea tu packet en servidor)
    private boolean inputUp;
    private boolean inputDown;

    // -------------------------
    // Cuenta atrás de despegue (menú galáctico)
    // -------------------------
    /** 3 segundos — el número que ve el jugador en la action bar (3, 2, 1) coincide con el
     *  diseño pedido ("cuenta atrás 3...2...1"), ver
     *  .claude/pendiente/nave-espacial-menu-galactico.md. */
    private static final int LAUNCH_COUNTDOWN_TICKS = 60;

    /** -1 = sin cuenta atrás en curso. Server-authoritative por completo: el cliente solo ve
     *  los mensajes de action bar, nunca decide por su cuenta cuándo teletransportar. */
    private int launchTicksLeft = -1;
    @Nullable private SpacePodDestination launchDestination;

    public SpacePodEntity(EntityType<? extends SpacePodEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setNoAi(true);
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    /** Llamar desde tu handler de packet (SERVER) */
    @Override
    public void setVerticalInput(boolean up, boolean down) {
        this.inputUp = up;
        this.inputDown = down;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate)
                .triggerableAnim("open", OPEN_ANIM)
                .triggerableAnim("close", CLOSE_ANIM)
                .triggerableAnim("launch", LAUNCH_ANIM));
    }

    private <E extends GeoAnimatable> PlayState predicate(AnimationState<E> state) {
        state.setAnimation(OPEN_ANIM);
        return PlayState.CONTINUE;
    }

    // -------------------------
    // Montura + movimiento
    // -------------------------

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand hand) {
        // Ya montado en ESTA nave: el mismo click derecho que antes solo montaba ahora abre el
        // menú galáctico (pedido explícito del usuario, ver
        // .claude/pendiente/nave-espacial-menu-galactico.md). mobInteract SIGUE llamándose con
        // el jugador ya encima — igual que un caballo abre su inventario con el jinete montado
        // — así que no hace falta un input nuevo ni una tecla dedicada.
        if (player.getVehicle() == this) {
            if (!this.level().isClientSide() && player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new OpenGalacticMenuPayload());
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (!player.isPassenger() && this.getPassengers().isEmpty()) {
            player.startRiding(this, true);
            if (!this.level().isClientSide()) triggerCloseAnimation();
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
            if (Math.abs(strafe) < INPUT_DEADZONE
                    && Math.abs(forward) < INPUT_DEADZONE
                    && Math.abs(upDown) < INPUT_DEADZONE) {

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
            double y = this.onGround() ? 0.0 : -0.05;

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
        Entity p = this.getFirstPassenger();
        return p instanceof LivingEntity le ? le : null;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        // Solo el cliente del conductor predice/mueve el pod; el resto lo recibe por tracking.
        // Antes devolvía siempre true -> cada cliente lo movía -> desincronización en MP.
        return getControllingPassenger() instanceof Player p && p.isLocalPlayer();
    }

    @Override
    public void tick() {
        super.tick();

        boolean hasRider = this.isVehicle() && this.getControllingPassenger() != null;

        // Con rider: sin gravedad (vuelo). Sin rider: con gravedad (cae) + tu travel también baja suave.
        this.setNoGravity(hasRider);

        if (!this.level().isClientSide()) {
            if (launchTicksLeft >= 0) {
                tickLaunchCountdown();
            } else if (!hasRider) {
                triggerOpenAnimation();
            }
        }
    }

    // -------------------------
    // Menú galáctico: cuenta atrás + salto real
    // -------------------------

    /** Arranca la cuenta atrás de despegue hacia `dest`. Llamado SOLO desde
     *  SpacePodLaunchPacket.handle, ya validado (jugador realmente montado en ESTA nave,
     *  destino distinto de la dimensión actual). No hace nada si ya hay una en curso — evita
     *  que un doble clic la reinicie o la solape. */
    public void beginLaunch(ServerPlayer pilot, SpacePodDestination dest) {
        if (launchTicksLeft >= 0) return;
        this.launchDestination = dest;
        this.launchTicksLeft = LAUNCH_COUNTDOWN_TICKS;
        triggerLaunchAnimation();
        pilot.displayClientMessage(Component.literal("3"), true);
        // Dura exactamente los 3s de la cuenta atrás (ver tools/gen_space_pod_launch_sfx.py) —
        // pedido explícito del usuario tras probar el menú sin ningún sonido de despegue.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SPACE_POD_LAUNCH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private void cancelLaunch() {
        this.launchTicksLeft = -1;
        this.launchDestination = null;
    }

    /** Un tick de la cuenta atrás YA en curso. Se cancela sola (sin viajar) si el piloto se
     *  bajó o murió mientras contaba — nunca teletransporta a nadie que ya no esté montado. */
    private void tickLaunchCountdown() {
        LivingEntity rider = getControllingPassenger();
        if (!(rider instanceof ServerPlayer pilot) || !pilot.isAlive()) {
            cancelLaunch();
            return;
        }

        launchTicksLeft--;
        if (launchTicksLeft == 40) {
            pilot.displayClientMessage(Component.literal("2"), true);
        } else if (launchTicksLeft == 20) {
            pilot.displayClientMessage(Component.literal("1"), true);
        } else if (launchTicksLeft <= 0) {
            executeLaunch(pilot);
        }
    }

    /** El salto real, al llegar la cuenta atrás a cero: mismas X/Z que el punto de despegue,
     *  altura reajustada al terreno del destino — como un portal, sin puerto espacial fijo que
     *  definir a mano en Tierra/Namek todavía (pedido explícito del usuario, ver el pendiente).
     *  La nave viaja CON el jugador de verdad (ver más abajo) y aparece de pie ENCIMA de ella,
     *  no dentro de la cabina — pedido explícito del usuario. */
    private void executeLaunch(ServerPlayer pilot) {
        SpacePodDestination dest = this.launchDestination;
        cancelLaunch();
        if (dest == null) return;

        ServerLevel destLevel = pilot.server.getLevel(dest.dimension());
        if (destLevel == null) return;

        BlockPos launchPos = this.blockPosition();
        pilot.stopRiding();

        BlockPos surface = surfaceAt(destLevel, launchPos.getX(), launchPos.getZ());
        // Red de seguridad para una columna genuinamente sin terreno (no debería pasar con el
        // generador de ruido de Namek/Overworld, los dos cubren toda columna, pero por si
        // acaso) — mismo criterio que TeleportRequestPacket.resolveHome: caer al spawn del
        // propio destino en vez de al vacío.
        if (surface.getY() <= destLevel.getMinBuildHeight() + 1) {
            BlockPos spawn = destLevel.getSharedSpawnPos();
            surface = surfaceAt(destLevel, spawn.getX(), spawn.getZ());
        }
        BlockPos safe = TeleportUtil.findSafeSpot(destLevel, surface);
        Vec3 landingBase = TeleportUtil.footCenter(safe);

        // La nave viaja CON el jugador — bug real reportado por el usuario: la ronda anterior
        // dejaba ESTA nave atrás en el sitio de despegue y plantaba una nueva "de bienvenida" en
        // cada llegada, así que un viaje de ida y vuelta acababa con DOS SpacePodEntity (una por
        // planeta) en vez de una sola. Un Entity no puede simplemente cambiar de ServerLevel —
        // hay que recrearlo en el nivel de destino, igual que hace vainilla al cruzar un portal
        // real: se guarda el NBT completo con saveWithoutId (posición/rotación ya no importan,
        // se sobrescriben después de todos modos), se retira ESTA instancia del nivel de origen
        // con remove(CHANGED_DIMENSION) — NO discard()/muerte, así que no dispara die() ni suelta
        // SPACE_POD_ITEM, la nave no se está destruyendo, solo mudando de sitio — y se recrea con
        // esos mismos datos en destLevel. this queda inválida a partir de aquí; todo lo de abajo
        // opera sobre travelledPod.
        CompoundTag podData = new CompoundTag();
        this.saveWithoutId(podData);
        this.remove(RemovalReason.CHANGED_DIMENSION);

        SpacePodEntity travelledPod = new SpacePodEntity(ModEntities.SPACE_POD.get(), destLevel);
        travelledPod.load(podData);
        travelledPod.moveTo(landingBase.x, landingBase.y, landingBase.z, pilot.getYRot(), 0.0F);
        destLevel.addFreshEntity(travelledPod);

        // Encima del casco, no dentro de la cabina — "aparezca encima de la spacepod" tal cual
        // lo pidió el usuario, no ya montado (canBeCollidedWith la hace sólida, así que el
        // jugador se queda de pie ahí en cuanto la física del siguiente tick lo asiente).
        Vec3 dest3 = new Vec3(landingBase.x, landingBase.y + travelledPod.getBbHeight(), landingBase.z);

        // Mismo motivo que TeleportExecution.execute: teleportTo(ServerLevel, ...) SÍ dispara
        // PlayerChangedDimensionEvent en cuanto cruza de dimensión, y DimensionEntryTracker está
        // enganchado a ese evento — avisarle de que este cruce es nuestro, no una llegada real.
        if (!pilot.serverLevel().dimension().equals(destLevel.dimension())) {
            DimensionEntryTracker.suppressNextEntry(pilot);
        }
        pilot.teleportTo(destLevel, dest3.x, dest3.y, dest3.z, pilot.getYRot(), pilot.getXRot());
        pilot.setPortalCooldown();

        ZenkaiTriggers.MILESTONE.get().trigger(pilot, ZenkaiTriggers.Kinds.SPACE_POD_LAUNCH_USED);
    }

    /** Heightmap de verdad en (x, z) — NO el atajo barato de {@code Level#getHeight}, que solo
     *  mira si el chunk YA está cargado ({@code hasChunk}) y, si no, devuelve directamente
     *  {@code getMinBuildHeight()} sin generar nada (verificado leyendo el fuente real de
     *  NeoForm, `Level.getHeight(Heightmap.Types, int, int)`). Bug real reportado por el
     *  usuario: al viajar a una columna que el servidor nunca había cargado (cualquier X/Z
     *  lejos de spawn en un mundo nuevo, que es justo el caso normal de esta nave), el
     *  heightmap "veía" el fondo del mundo y `TeleportUtil.isSafe` daba por buena esa posición
     *  (el chunk descargado también hace que `getBlockState` devuelva aire por defecto) —
     *  aparecía en el vacío y moría de caída. Forzar la generación completa del chunk con
     *  {@code getChunkAt} ANTES de leer el heightmap es la diferencia entre las dos. */
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        level.getChunkAt(new BlockPos(x, 0, z));
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
    }

    // -------------------------
    // Inmunidades (igual que KintounEntity): no se ahoga ni muere por pociones
    // -------------------------

    /** Inmune a cualquier efecto de poción (veneno, wither, etc.). */
    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return false;
    }

    /** Inmune a ahogo, asfixia en bloque y daño mágico/pociones (harming/veneno/wither) — mismo
     *  criterio que KintounEntity, pedido explícito del usuario ("ajustar la IA y protecciones
     *  para el spacepod"). */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        if (source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    // -------------------------
    // Colisión sólida: el jugador puede pararse encima, igual que KintounEntity — pedido
    // explícito del usuario ("hazla tangible... así como en los kintoun").
    // -------------------------

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    // -------------------------
    // Drops / XP
    // -------------------------

    @Override
    protected int getBaseExperienceReward() {
        return 0; // sin XP
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        return false; // el space pod no recibe daño de caída
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (!this.level().isClientSide) {
            this.spawnAtLocation(ModItems.SPACE_POD_ITEM.get());
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
    // Asiento
    // -------------------------

    @Override
    public @NotNull Vec3 getPassengerRidingPosition(@NotNull Entity passenger) {
        Vec3 off = new Vec3(0, 0.7, -0.5);
        off = off.yRot(-this.getYRot() * Mth.DEG_TO_RAD);
        return this.position().add(off);
    }

    // -------------------------
    // GeckoLib helpers
    // -------------------------

    public void triggerCloseAnimation() { triggerAnim("controller", "close"); }
    public void triggerOpenAnimation()  { triggerAnim("controller", "open");  }
    public void triggerLaunchAnimation(){ triggerAnim("controller", "launch"); }

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