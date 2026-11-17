package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.KiChamberMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.KiContainmentChamberBlock;
import com.hmc.zenkai.feature.combat.StunState;
import com.hmc.zenkai.feature.kichamber.KiChamberSessions;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStateFlags;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Cámara de contención de ki (0.7.0, Fase 2a): el jugador entra, su ki se drena y sale convertido
 * en un FLUIDO de Ki del tipo de su forma (ver KiEnergyType). Vive solo en la mitad INFERIOR del
 * bloque de dos alturas; la superior delega aquí sus capabilities (ver Zenkai.registerCapabilities).
 *
 * UNA SESIÓN = alguien SUJETO dentro con la puerta cerrada (rediseñado el 2026-11-16 a petición
 * del usuario). Clic derecho abre la puerta; con la puerta abierta, el primer ser vivo que entra
 * andando (jugador o mob, también empujado) queda atrapado: la puerta se cierra sola, se le
 * inmoviliza (StunState.hold sin estrellas, y además se le clava en el centro cada tick) y la
 * extracción ARRANCA SOLA. Otro clic derecho, de quien sea y también del ocupante, abre la puerta,
 * corta y lo libera. Al terminar por su cuenta (reserva, tanque lleno, sobrecarga) la puerta se
 * abre sola. El liberado no vuelve a ser atrapado hasta que salga de la columna. La pantalla se
 * abre agachado + clic derecho y ya no hace falta estar dentro: Forzar y Convertir son ajustes
 * de la MÁQUINA. No se guarda en disco: tras un reinicio no hay nadie sujeto.
 *
 * MIENTRAS ESTÁ DENTRO el jugador no hace nada: ni técnicas (KiChamberSessions → ActionContext),
 * ni cargar ki con la tecla, ni regenerar ki pasivo (KiChargeSystem y RegenSystem lo saltan; con
 * Meditación el regen igualaba al drenaje y la carga no acababa nunca). Se le pone isChargingKi
 * para que se vea la pose y el aura de cargar: es solo visual.
 *
 * UN MOB da Ki Base a cambio de VIDA (un % de su vida máxima por segundo) y se detiene en 1 de
 * vida, sin matarlo. Sin cooldown ni tolerancia: Base viene de serie.
 *
 * LA CARGA drena un % por segundo del ki MÁXIMO (quien tiene más reserva da más fluido) y para
 * sola al llegar a la reserva (20 %). El COOLDOWN:
 *   - solo lo deja LLEGAR A LA RESERVA (decisión del usuario, 2026-11-16). Parar a mano, salir,
 *     cambiar de forma, el tanque lleno o la sobrecarga NO lo ponen. Se sabe que así se puede
 *     parar al 21 %, recuperar ki fuera y volver: es el precio aceptado de no castigar a quien
 *     para antes de tiempo;
 *   - es del JUGADOR (PlayerStateFlags), no de la máquina: cambiar de cámara no lo salta;
 *   - en cooldown (o por debajo de la reserva) solo se carga con el ajuste FORZAR encendido: sigue
 *     drenando ki y además vida, y al dejar de forzar el
 *     cooldown se renueva (ha seguido por debajo de la reserva). La vida se escribe directa en
 *     el pool y se detiene en 1: nunca pasa por un evento de daño ni por die() (regla dura de
 *     combate-y-muerte.md), así que forzar no puede matar.
 * El tipo de fluido se fija al EMPEZAR la carga; cambiar de forma a mitad la corta (decisión de
 * diseño del 2026-11-16), no la convierte.
 *
 * PUERTOS, sin cables propios como el generador: el tanque de fluido (se puede sacar Y meter
 * fluido de Ki por tubería: así una cámara sirve de generador del Ki que produjo otra) y FE. El conversor interno pasa mB a FE con
 * la tasa del tipo (1/8/24/32 por defecto) hasta un tope por tick, y la FE se empuja a los vecinos
 * de las dos mitades. Con el conversor apagado el tanque se guarda entero para las tuberías.
 *
 * MEJORAS (Fase 2b), seis ranuras: tolerancia a Ki Súper, Divino y Legendario (la de Base viene
 * de serie), velocidad (hasta 3: multiplica el tope de FE por tick), almacenamiento (hasta 4:
 * amplía tanque y búfer) y una BATERÍA, que se carga del búfer antes de empujar a los vecinos.
 * La velocidad NO acelera el drenaje de ki: ese ritmo es el freno anti-exploit.
 *
 * SOBRECARGA: cargar un tipo de Ki sin su mejora de tolerancia NO está prohibido, se paga. La
 * máquina acumula CALOR mientras dure esa carga: primero humo, desde el 40 % alarma y aviso al
 * jugador, y al 100 % explota. La explosión vacía el tanque y le quita vida a quien cargaba
 * (escrita en el pool, con suelo en 1: no mata, igual que forzar; a ese jugador se le excluye
 * del daño normal de la explosión para que la regla no dependa de su armadura). Si rompe bloques
 * lo decide la gamerule zenkai_kiOverloadGriefing. El calor baja solo al dejar de cargar.
 *
 * DOS NIVELES DE «ALGO CAMBIÓ», como el banco: markDirty() cada tick de trabajo (solo guardar) y
 * syncToClient() cuando cambia lo que ve el renderer sin la GUI (si carga y de qué tipo). Las
 * cifras van por ContainerData, solo a quien tiene la pantalla abierta.
 */
public class KiContainmentChamberBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {

    private static final RawAnimation CHARGING = RawAnimation.begin().thenLoop("charging");
    private static final RawAnimation DOOR_OPEN = RawAnimation.begin().thenLoop("door_open");
    private static final RawAnimation DOOR_CLOSED = RawAnimation.begin().thenLoop("door_closed");

    // ── Ranuras de mejora (un solo sitio para block entity, menú y pantalla) ──
    public static final int SLOT_TOL_SUPER = 0, SLOT_TOL_DIVINE = 1, SLOT_TOL_LEGENDARY = 2;
    public static final int SLOT_SPEED = 3, SLOT_STORAGE = 4, SLOT_BATTERY = 5;
    public static final int UPGRADE_SLOTS = 6;
    public static final int MAX_SPEED = 3, MAX_STORAGE = 4;

    /** Calor de sobrecarga: 0 fría, HEAT_ALARM empieza la alarma, HEAT_MAX explota. */
    public static final double HEAT_MAX = 100.0, HEAT_ALARM = 40.0;

    /** Sujeción «para siempre» (20 h): la corta endSession, no el reloj. Mismo criterio que el agarre. */
    private static final int HOLD_TICKS = 1_440_000;

    /** ¿Qué ítem vale en cada ranura? Lo usan el handler, los Slot del menú y el shift-clic. */
    public static boolean validFor(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_TOL_SUPER -> stack.is(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_SUPER.get());
            case SLOT_TOL_DIVINE -> stack.is(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_DIVINE.get());
            case SLOT_TOL_LEGENDARY -> stack.is(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_LEGENDARY.get());
            case SLOT_SPEED -> stack.is(com.hmc.zenkai.registry.ModItems.MACHINE_SPEED_UPGRADE.get());
            case SLOT_STORAGE -> stack.is(com.hmc.zenkai.registry.ModItems.MACHINE_STORAGE_UPGRADE.get());
            case SLOT_BATTERY -> stack.is(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get());
            default -> false;
        };
    }

    /** Cuántos caben en cada ranura: una tolerancia basta, velocidad y almacenamiento se apilan. */
    public static int slotLimit(int slot) {
        return switch (slot) {
            case SLOT_SPEED -> MAX_SPEED;
            case SLOT_STORAGE -> MAX_STORAGE;
            default -> 1;
        };
    }

    /** Ranura de tolerancia de un tipo, o -1 si no la necesita (Base viene de serie). */
    public static int toleranceSlot(KiEnergyType type) {
        return switch (type) {
            case SUPER -> SLOT_TOL_SUPER;
            case DIVINE -> SLOT_TOL_DIVINE;
            case LEGENDARY -> SLOT_TOL_LEGENDARY;
            default -> -1;
        };
    }


    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final FluidTank tank = new FluidTank(16_000, s -> KiEnergyType.ofFluid(s.getFluid()) != null) {
        @Override
        protected void onContentsChanged() { markDirty(); }
    };

    private final ChamberEnergy energy = new ChamberEnergy();

    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return validFor(slot, stack); }
        @Override public int getSlotLimit(int slot) { return slotLimit(slot); }
    };

    // ── Sesión (no se guarda) ────────────────────────────────────────────────
    /** Quién está sujeto dentro (jugador o mob); null = vacía. */
    @Nullable private UUID occupant;
    /** A quién NO volver a atrapar mientras siga dentro: el recién liberado o el rechazado. */
    @Nullable private UUID ignored;
    private boolean charging;
    private boolean forcing;
    /** ¿La carga en curso llegó a drenar algo? Decide si al cortarse deja cooldown. */
    private boolean drainedSomething;
    @Nullable private KiEnergyType sessionType;
    @Nullable private ResourceLocation sessionForm;
    /** Restos fraccionales: el ki y el body son enteros y un 5 %/s repartido en 20 ticks casi
     *  nunca da un entero. Sin acumulador, una reserva pequeña no drenaría nada. */
    private double kiCarry, mbCarry, bodyCarry;
    /** Puntos de body entregados en esta sesión (fase de vida del forzado): escalan el cooldown extra. */
    private int bodySpent;
    /** Lecturas del último tick para la pantalla. */
    private int lastMbPerSecond, lastFePerTick;

    // ── Estado guardado ──────────────────────────────────────────────────────
    private boolean convert = true;
    /** Ajuste «Forzar» de la máquina: con él, quien entra en cooldown o sin reserva carga pagando vida. */
    private boolean forceSetting;
    /** Calor de sobrecarga (0..HEAT_MAX). Se guarda: recargar el chunk no enfría la máquina. */
    private double heat;

    /** Tipo que brilla en el modelo; -1 = apagado. Es lo único que viaja en el paquete de bloque. */
    private int glowType = -1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KiChamberMenu.D_TANK_LO -> tank.getFluidAmount() & 0xFFFF;
                case KiChamberMenu.D_TANK_HI -> tank.getFluidAmount() >>> 16;
                case KiChamberMenu.D_CAP_LO -> tank.getCapacity() & 0xFFFF;
                case KiChamberMenu.D_CAP_HI -> tank.getCapacity() >>> 16;
                case KiChamberMenu.D_TANK_TYPE -> {
                    KiEnergyType t = tankType();
                    yield t == null ? -1 : t.ordinal();
                }
                case KiChamberMenu.D_FE_LO -> energy.get() & 0xFFFF;
                case KiChamberMenu.D_FE_HI -> energy.get() >>> 16;
                case KiChamberMenu.D_FE_CAP_LO -> energy.capacity() & 0xFFFF;
                case KiChamberMenu.D_FE_CAP_HI -> energy.capacity() >>> 16;
                case KiChamberMenu.D_FLAGS -> (charging ? KiChamberMenu.F_CHARGING : 0)
                        | (forcing ? KiChamberMenu.F_FORCING : 0)
                        | (convert ? KiChamberMenu.F_CONVERT : 0)
                        | (isOpen() ? KiChamberMenu.F_OPEN : 0)
                        | (forceSetting ? KiChamberMenu.F_FORCE_SET : 0)
                        | (occupant != null ? KiChamberMenu.F_OCCUPIED : 0);
                case KiChamberMenu.D_RATE_LO -> lastMbPerSecond & 0xFFFF;
                case KiChamberMenu.D_RATE_HI -> lastMbPerSecond >>> 16;
                case KiChamberMenu.D_FE_RATE -> Math.min(lastFePerTick, Short.MAX_VALUE);
                case KiChamberMenu.D_HEAT -> (int) Math.round(heat);
                default -> 0;
            };
        }

        @Override public void set(int i, int v) { /* solo lectura desde el cliente */ }
        @Override public int getCount() { return KiChamberMenu.DATA_SLOTS; }
    };

    public KiContainmentChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KI_CONTAINMENT_CHAMBER.get(), pos, state);
    }

    // ── Accesos ──────────────────────────────────────────────────────────────

    /** El puerto de fluido ES el tanque: acepta solo fluidos de Ki (su validador) y de un tipo a la vez. */
    public IFluidHandler fluidHandler() { return tank; }
    public boolean isForceSet() { return forceSetting; }
    public IEnergyStorage energyHandler() { return energy; }
    public FluidTank tank() { return tank; }
    public ChamberEnergy energyBuffer() { return energy; }
    public ContainerData containerData() { return data; }
    public boolean isCharging() { return charging; }
    public boolean isForcing() { return forcing; }
    public boolean isConverting() { return convert; }
    public ItemStackHandler upgradeHandler() { return upgrades; }
    public double heat() { return heat; }

    /** ¿La máquina tolera este tipo de Ki? Base siempre; los demás, con su mejora puesta. */
    public boolean tolerates(KiEnergyType type) {
        int slot = toleranceSlot(type);
        return slot < 0 || !upgrades.getStackInSlot(slot).isEmpty();
    }

    /** Tope de FE por tick con las mejoras de velocidad puestas. */
    public int feMaxPerTick() {
        int n = Math.min(MAX_SPEED, upgrades.getStackInSlot(SLOT_SPEED).getCount());
        double v = ServerConfig.kiChamberFeMaxPerTick() * Math.pow(ServerConfig.kiChamberSpeedMult(), n);
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(1.0, v));
    }

    /** Factor de capacidad (tanque y búfer) con las mejoras de almacenamiento puestas. */
    private double storageFactor() {
        int n = Math.min(MAX_STORAGE, upgrades.getStackInSlot(SLOT_STORAGE).getCount());
        return 1.0 + n * ServerConfig.kiChamberStoragePercent() / 100.0;
    }

    /** Aplica al tanque y al búfer los límites que dan las mejoras. Barato: se llama cada tick. */
    private void applyUpgradeLimits() {
        double f = storageFactor();
        int tankCap = (int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.kiChamberTankCapacity() * f);
        if (tank.getCapacity() != tankCap) {
            tank.setCapacity(tankCap);
            // Quitar una mejora con el tanque lleno pierde lo que ya no cabe (como el búfer de FE).
            if (tank.getFluidAmount() > tankCap) {
                tank.setFluid(new FluidStack(tank.getFluid().getFluid(), tankCap));
            }
        }
        energy.setLimits((int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.kiChamberFeCapacity() * f), feMaxPerTick());
    }

    /** Tipo del fluido que hay en el tanque, o null si está vacío. */
    @Nullable
    public KiEnergyType tankType() {
        return tank.isEmpty() ? null : KiEnergyType.ofFluid(tank.getFluid().getFluid());
    }

    /** Tipo que debe brillar en el modelo (cliente), o null si la cámara está apagada. */
    @Nullable
    public KiEnergyType glowType() { return KiEnergyType.byOrdinal(glowType); }

    // ── Sesión ───────────────────────────────────────────────────────────────

    public boolean isFree() { return occupant == null; }

    public boolean isOccupant(Entity e) { return e.getUUID().equals(occupant); }

    public boolean isOpen() {
        BlockState st = getBlockState();
        return st.hasProperty(KiContainmentChamberBlock.OPEN) && st.getValue(KiContainmentChamberBlock.OPEN);
    }

    /**
     * Centro del hueco, A RAS DEL BLOQUE: el suelo del modelo es una placa de 1 px y no hay colisión
     * de suelo, así que el ocupante pisa el bloque de debajo y un jugador de 1,8 cabe bajo la tapa.
     */
    private Vec3 seat() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
    }

    /** Abre la pantalla (agachado + clic derecho). Cualquiera que llegue al bloque. */
    public void openMenuFor(ServerPlayer sp) {
        sp.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    /** Clic derecho, o el botón de la pantalla: con alguien dentro lo libera; si no, abre o cierra. */
    public void toggleDoor() {
        if (occupant != null) { release(); return; }
        boolean open = !isOpen();
        // Cerrar a mano «olvida» al rechazado: al volver a abrir se le intenta atrapar otra vez.
        if (!open) ignored = null;
        setOpen(open);
    }

    private void setOpen(boolean open) {
        if (level == null) return;
        BlockState st = getBlockState();
        if (!st.hasProperty(KiContainmentChamberBlock.OPEN) || st.getValue(KiContainmentChamberBlock.OPEN) == open) return;
        level.setBlock(worldPosition, st.setValue(KiContainmentChamberBlock.OPEN, open), 3);
        BlockPos up = worldPosition.above();
        BlockState top = level.getBlockState(up);
        if (top.is(st.getBlock())) level.setBlock(up, top.setValue(KiContainmentChamberBlock.OPEN, open), 3);
        level.playSound(null, worldPosition,
                open ? net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN : net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.3f);
    }

    /** ¿El centro de la entidad está dentro de la columna de dos bloques de la cámara? */
    private boolean insideColumn(Entity e) {
        double dx = e.getX() - worldPosition.getX(), dz = e.getZ() - worldPosition.getZ();
        double dy = e.getY() - worldPosition.getY();
        return dx >= 0.0 && dx <= 1.0 && dz >= 0.0 && dz <= 1.0 && dy >= -0.1 && dy < 2.0;
    }

    /** Jugadores y mobs vivos. Ni soportes de armadura ni espectadores. */
    private boolean extractable(LivingEntity e) {
        return e.isAlive() && !e.isSpectator() && (e instanceof ServerPlayer || e instanceof Mob)
                && !e.getUUID().equals(ignored);
    }

    /** Con la puerta abierta: ¿ha entrado alguien? El centro tiene que estar bien dentro, no en el umbral. */
    private void detect() {
        if (level == null) return;
        double x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
        AABB inner = new AABB(x + 0.25, y - 0.1, z + 0.25, x + 0.75, y + 1.0, z + 0.75);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, inner, this::extractable)) {
            if (!inner.contains(e.getX(), e.getY(), e.getZ())) continue;
            capture(e);
            return;
        }
    }

    /** Atrapa a quien entró: cierra, lo sujeta en el centro y arranca. Si no puede cargar, lo deja estar. */
    private void capture(LivingEntity e) {
        if (level == null) return;
        String reason = denyReason(e);
        if (reason != null) {
            ignored = e.getUUID();           // no se le vuelve a preguntar hasta que salga
            if (e instanceof ServerPlayer sp && !reason.isEmpty()) deny(sp, reason);
            return;
        }
        occupant = e.getUUID();
        ignored = null;
        setOpen(false);
        e.stopRiding();
        Direction facing = getBlockState().getValue(KiContainmentChamberBlock.FACING);
        Vec3 seat = seat();
        if (e instanceof ServerPlayer sp) {
            KiChamberSessions.enter(sp);
            sp.teleportTo((ServerLevel) level, seat.x, seat.y, seat.z, facing.toYRot(), 0f);
        } else {
            e.moveTo(seat.x, seat.y, seat.z, facing.toYRot(), 0f);
        }
        e.setDeltaMovement(Vec3.ZERO);
        // Sujeción sin estrellas ni icono (la misma de la escena de transformación): sin andar, sin
        // saltar, sin técnicas. Se suelta en endSession. Con stun.affect_players apagado no hace
        // nada sobre jugadores; para eso está además pin(), que lo devuelve al centro cada tick.
        StunState.hold(e, HOLD_TICKS, false);
        begin(e);
        markDirty();
    }

    /** Tipo de Ki que da quien entra: el de su forma si es jugador; Base si es un mob. */
    @Nullable
    private static KiEnergyType typeOf(LivingEntity e) {
        return e instanceof Player p ? KiEnergyType.of(p) : KiEnergyType.BASE;
    }

    /** null = puede cargar; si no, la clave del aviso (vacía = rechazo silencioso, para mobs). */
    @Nullable
    private String denyReason(LivingEntity e) {
        if (level == null) return "";
        KiEnergyType type = typeOf(e);
        if (type == null) return "messages.zenkai.ki_chamber.no_energy";
        KiEnergyType inTank = tankType();
        if (inTank != null && inTank != type) return "messages.zenkai.ki_chamber.wrong_fluid";
        if (tank.getSpace() <= 0) return "messages.zenkai.ki_chamber.full";
        if (e instanceof ServerPlayer sp) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            boolean cooling = cooldownLeft(att.flags(), level.getGameTime()) > 0L;
            boolean low = att.getKiCurrent() <= reserve(att);
            if (cooling || low) {
                // Forzar existe para saltarse el cooldown y la reserva pagando vida.
                if (!forceSetting) return cooling ? "messages.zenkai.ki_chamber.cooldown" : "messages.zenkai.ki_chamber.low_ki";
                // Con ki 0 forzar sigue valiendo: la máquina pasa a funcionar con la vida.
                if (att.getBody() <= 1 && att.getKiCurrent() <= 0) return "messages.zenkai.ki_chamber.exhausted";
            }
            return null;
        }
        return e.getHealth() > 1f ? null : "";
    }

    private void begin(LivingEntity e) {
        charging = true;
        forcing = false;
        sessionForm = null;
        if (e instanceof ServerPlayer sp && level != null) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            forcing = cooldownLeft(att.flags(), level.getGameTime()) > 0L || att.getKiCurrent() <= reserve(att);
            sessionForm = formOf(sp);
        }
        drainedSomething = false;
        sessionType = typeOf(e);
        kiCarry = mbCarry = bodyCarry = 0.0;
        bodySpent = 0;
        setGlow(sessionType);
    }

    /** Devuelve al ocupante al centro y mantiene la pose de cargar. Cada tick mientras está sujeto. */
    private void pin(LivingEntity e) {
        Vec3 seat = seat();
        if (e instanceof ServerPlayer sp) {
            // Solo si se ha movido: teletransportar cada tick haría temblar la cámara del jugador.
            if (sp.distanceToSqr(seat) > 0.01) sp.teleportTo(seat.x, seat.y, seat.z);
            PlayerStatsAttachment.get(sp).setChargingKi(true);   // pose, aura y sonido; no recarga (KiChargeSystem)
        } else {
            e.setPos(seat.x, seat.y, seat.z);
            e.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Corta la carga (con o sin cooldown), avisa al jugador, lo libera y abre la puerta. */
    private void finish(@Nullable LivingEntity e, @Nullable String key, boolean cooldown) {
        ServerPlayer sp = e instanceof ServerPlayer p ? p : null;
        stopCharge(sp, cooldown);
        if (sp != null && key != null) deny(sp, key);
        release();
    }

    /** Libera a quien esté dentro y abre la puerta. */
    public void release() {
        endSession();
        setOpen(true);
    }

    /**
     * Cierra la sesión: corta la carga y suelta al ocupante. No toca la puerta ni lo mueve. Se puede
     * llamar más de una vez. El liberado queda en «ignored» para que la puerta no lo vuelva a
     * atrapar en el mismo tick en que se abre.
     */
    public void endSession() {
        if (occupant == null) return;
        LivingEntity e = occupantEntity();
        ServerPlayer sp = e instanceof ServerPlayer p ? p : null;
        stopCharge(sp, forcing);
        if (e != null) StunState.release(e, false);
        if (sp != null) PlayerStatsAttachment.get(sp).setChargingKi(false);
        KiChamberSessions.leave(occupant);
        ignored = occupant;
        occupant = null;
        markDirty();
    }

    @Nullable
    private LivingEntity occupantEntity() {
        if (occupant == null || !(level instanceof ServerLevel sl)) return null;
        return sl.getEntity(occupant) instanceof LivingEntity e ? e : null;
    }

    // ── Botones del menú ─────────────────────────────────────────────────────

    /** Ajuste «Forzar». Es de la máquina: lo toca quien tenga la pantalla abierta. */
    public void toggleForce() {
        forceSetting = !forceSetting;
        markDirty();
    }

    /** Ajuste «Convertir a FE». */
    public void toggleConvert() {
        convert = !convert;
        markDirty();
    }

    /**
     * Corta la carga. Con {@code cooldown} y si la carga llegó a drenar algo, el jugador queda
     * en cooldown. Solo se pide al llegar a la reserva o al dejar de forzar (ver el javadoc de
     * la clase); el resto de cortes pasan false.
     */
    private void stopCharge(@Nullable ServerPlayer sp, boolean cooldown) {
        if (!charging) return;
        charging = false;
        forcing = false;
        lastMbPerSecond = 0;
        if (cooldown && drainedSomething && sp != null) {
            // Cuanta más vida entregó, más largo el enfriamiento (100 % del body = el extra completo).
            int bodyMax = Math.max(1, PlayerStatsAttachment.get(sp).getBodyMax());
            int extra = (int) Math.round((double) bodySpent / bodyMax * ServerConfig.kiChamberForceExtraCooldownSeconds());
            applyCooldown(sp, extra);
        }
        bodySpent = 0;
        drainedSomething = false;
        sessionType = null;
        sessionForm = null;
        setGlow(null);
    }

    private void applyCooldown(ServerPlayer sp) { applyCooldown(sp, 0); }

    private void applyCooldown(ServerPlayer sp, int extraSeconds) {
        if (level == null) return;
        PlayerStateFlags flags = PlayerStatsAttachment.get(sp).flags();
        long until = level.getGameTime() + (ServerConfig.kiChamberCooldownSeconds() + (long) extraSeconds) * 20L;
        flags.setKiChamberCooldownUntil(Math.max(flags.getKiChamberCooldownUntil(), until));
    }

    private static boolean deny(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable(key), true);
        return false;
    }

    private static double reserve(PlayerStatsAttachment att) {
        return att.getKiPool() * ServerConfig.kiChamberReservePercent() / 100.0;
    }

    public static long cooldownLeft(PlayerStateFlags flags, long gameTime) {
        return Math.max(0L, flags.getKiChamberCooldownUntil() - gameTime);
    }

    private static ResourceLocation formOf(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return form.getFormId();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  KiContainmentChamberBlockEntity be) {
        be.applyUpgradeLimits();

        ServerPlayer sp = null;
        if (be.occupant != null) {
            LivingEntity e = be.occupantEntity();
            if (!be.sessionValid(e)) {
                be.endSession();             // murió, se fue o se desconectó: la puerta se queda como está
            } else {
                be.pin(e);
                if (e instanceof ServerPlayer p) { sp = p; be.tickCharge(p); }
                else be.tickMob(e);
            }
        } else if (be.isOpen()) {
            be.detect();
        }
        if (be.ignored != null && level instanceof ServerLevel sl) {
            Entity ig = sl.getEntity(be.ignored);
            if (ig == null || !be.insideColumn(ig)) be.ignored = null;
        }
        be.tickHeat(be.occupant == null ? null : sp);

        be.tickConvert();
        be.chargeBattery();
        be.pushToNeighbours(level, pos);
    }

    private boolean sessionValid(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && level != null && e.level() == level && insideColumn(e);
    }

    /** Un tick de extracción a un mob: vida → Ki Base, con suelo en 1 de vida. */
    private void tickMob(LivingEntity e) {
        if (!charging) return;
        int space = tank.getSpace();
        float hp = e.getHealth();
        KiEnergyType inTank = tankType();
        if (space <= 0 || hp <= 1f || (inTank != null && inTank != KiEnergyType.BASE)) {
            finish(e, null, false);
            return;
        }
        double perSecond = e.getMaxHealth() * ServerConfig.kiChamberMobHealthPercent() / 100.0;
        float drain = (float) Math.min(hp - 1f, perSecond / 20.0);
        e.setHealth(hp - drain);
        mbCarry += drain * ServerConfig.kiChamberMbPerMobHealth();
        int mb = (int) mbCarry;
        mbCarry -= mb;
        if (mb > 0) {
            tank.fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), Math.min(mb, space)),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        lastMbPerSecond = (int) Math.round(perSecond * ServerConfig.kiChamberMbPerMobHealth());
        markDirty();
    }

    /** Un tick de carga. Público para los gametests, que no tienen un menú abierto de verdad. */
    public void tickCharge(ServerPlayer sp) {
        if (!charging || level == null || sessionType == null) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        // Cambió de forma a mitad: se corta, no se convierte. Sin cooldown salvo que estuviera forzando.
        if (!formOf(sp).equals(sessionForm)) {
            finish(sp, "messages.zenkai.ki_chamber.form_changed", forcing);
            return;
        }

        // Alguien metió por tubería otro tipo de Ki con el tanque vacío: no se mezclan.
        KiEnergyType inTank = tankType();
        if (inTank != null && inTank != sessionType) {
            finish(sp, "messages.zenkai.ki_chamber.wrong_fluid", forcing);
            return;
        }

        int space = tank.getSpace();
        if (space <= 0) {                       // tanque lleno: sin cooldown (salvo forzando)
            finish(sp, "messages.zenkai.ki_chamber.full", forcing);
            return;
        }

        double want = att.getKiPool() * ServerConfig.kiChamberChargePercent() / 100.0 / 20.0;
        if (!forcing && att.getKiCurrent() - reserve(att) <= 0) {
            // Reserva alcanzada: cooldown. Con el ajuste Forzar la carga SIGUE, ya pagando vida.
            applyCooldown(sp);
            if (forceSetting && (att.getBody() > 1 || att.getKiCurrent() > 0)) {
                forcing = true;
            } else {
                finish(sp, "messages.zenkai.ki_chamber.cooldown_started", false);
                return;
            }
        }
        double available;
        if (forcing) {
            // Forzar, fase 1: el ki baja hasta 0 sin coste de vida. Fase 2 (ki a 0): la máquina sigue con
            // el body, que se convierte en Ki y se detiene en 1 sin matar (con ki 0 la máquina pararía
            // igualmente, así que cobrar vida mientras queda ki no tenía sentido).
            available = att.getKiCurrent();
            if (available <= 0) {
                if (att.getBody() <= 1) {
                    finish(sp, "messages.zenkai.ki_chamber.exhausted", true);
                    return;
                }
                int bodyMax = Math.max(1, att.getBodyMax());
                bodyCarry += bodyMax * ServerConfig.kiChamberForceBodyDrain() / 100.0 / 20.0;
                int bodyDrain = (int) Math.min(bodyCarry, att.getBody() - 1);
                if (bodyDrain > 0) {
                    bodyCarry -= bodyDrain;
                    att.setBody(att.getBody() - bodyDrain);
                    bodySpent += bodyDrain;
                    // Un punto de body vale lo que un bodyMax-ésimo del pool de ki: mismo ritmo que la carga normal.
                    mbCarry += bodyDrain * ((double) att.getKiPool() / bodyMax) * ServerConfig.kiChamberMbPerKi();
                    int mb = (int) mbCarry;
                    mbCarry -= mb;
                    drainedSomething = true;
                    if (mb > 0) {
                        int filled = tank.fill(new FluidStack(sessionType.fluid().source().get(), Math.min(mb, space)),
                                IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0 && glowType != sessionType.ordinal()) setGlow(sessionType);
                    }
                }
                lastMbPerSecond = (int) Math.round(att.getKiPool() * ServerConfig.kiChamberForceBodyDrain() / 100.0
                        * ServerConfig.kiChamberMbPerKi());
                markDirty();
                return;
            }
        } else {
            available = att.getKiCurrent() - reserve(att);
        }

        kiCarry += Math.min(want, available);
        int kiDrain = (int) Math.min(kiCarry, available);
        if (kiDrain <= 0) { lastMbPerSecond = mbPerSecond(want); return; }

        mbCarry += kiDrain * ServerConfig.kiChamberMbPerKi();
        int mb = (int) mbCarry;
        mbCarry -= mb;
        kiCarry -= kiDrain;
        att.addKi(-kiDrain);
        drainedSomething = true;
        if (mb > 0) {
            int filled = tank.fill(new FluidStack(sessionType.fluid().source().get(), Math.min(mb, space)),
                    IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0 && glowType != sessionType.ordinal()) setGlow(sessionType);
        }
        lastMbPerSecond = mbPerSecond(want);
        markDirty();
    }

    private static int mbPerSecond(double kiPerTick) {
        return (int) Math.round(kiPerTick * 20.0 * ServerConfig.kiChamberMbPerKi());
    }

    /** Conversor: mB del tanque → FE, con la tasa del tipo y el tope por tick. */
    private void tickConvert() {
        lastFePerTick = 0;
        if (!convert || tank.isEmpty()) return;
        KiEnergyType type = tankType();
        if (type == null) return;
        int rate = Math.max(1, type.fePerMb());
        int budget = Math.min(feMaxPerTick(), energy.space());
        int mb = Math.min(tank.getFluidAmount(), budget / rate);
        if (mb <= 0) return;                    // no cabe ni un mB entero: no se desperdicia
        tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        lastFePerTick = energy.generate(mb * rate);
        markDirty();
    }

    /** Carga la batería de la ranura con el búfer, al ritmo de la máquina y al que acepte la batería. */
    private void chargeBattery() {
        ItemStack battery = upgrades.getStackInSlot(SLOT_BATTERY);
        if (battery.isEmpty() || energy.getEnergyStored() <= 0) return;
        IEnergyStorage cell = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        if (cell == null || !cell.canReceive()) return;
        int accepted = cell.receiveEnergy(Math.min(feMaxPerTick(), energy.getEnergyStored()), false);
        if (accepted > 0) {
            energy.extractEnergy(accepted, false);
            markDirty();
        }
    }

    // ── Sobrecarga ───────────────────────────────────────────────────────────

    /**
     * Sube el calor mientras se carga un tipo sin tolerancia y lo baja el resto del tiempo (a la
     * mitad de ritmo: enfriar cuesta el doble que calentar). Avisos por tramos y, al tope, explosión.
     */
    private void tickHeat(@Nullable ServerPlayer sp) {
        if (level == null) return;
        double step = HEAT_MAX / (ServerConfig.kiChamberOverloadSeconds() * 20.0);
        boolean heating = charging && sessionType != null && !tolerates(sessionType);
        if (!heating) {
            if (heat > 0.0) { heat = Math.max(0.0, heat - step * 0.5); markDirty(); }
            return;
        }
        heat = Math.min(HEAT_MAX, heat + step);
        markDirty();

        long t = level.getGameTime();
        if (level instanceof ServerLevel sl && t % 5 == 0) {
            // Humo desde el primer momento, más denso cuanto más caliente.
            int count = 1 + (int) (heat / 20.0);
            sl.sendParticles(heat >= HEAT_ALARM ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE
                            : net.minecraft.core.particles.ParticleTypes.SMOKE,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 2.05, worldPosition.getZ() + 0.5,
                    count, 0.25, 0.05, 0.25, 0.01);
        }
        if (heat >= HEAT_ALARM && t % 10 == 0) {
            // Alarma cada vez más aguda, y el aviso en la action bar para quien tiene la pantalla abierta.
            float pitch = 0.8f + (float) ((heat - HEAT_ALARM) / (HEAT_MAX - HEAT_ALARM)) * 1.0f;
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, pitch);
            if (sp != null) {
                sp.displayClientMessage(Component.translatable("messages.zenkai.ki_chamber.overload",
                        (int) Math.round(heat)).withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }
        if (heat >= HEAT_MAX) {
            overload(sp, com.hmc.zenkai.registry.ModGameRules.kiOverloadGriefing(level.getServer()));
        }
    }

    /**
     * La explosión de sobrecarga. Público para los gametests (que pasan grief=false: una explosión
     * que rompe bloques se llevaría por delante las parcelas de los tests vecinos).
     */
    public void overload(@Nullable ServerPlayer charger, boolean grief) {
        if (level == null) return;
        heat = 0.0;
        tank.setFluid(FluidStack.EMPTY);
        stopCharge(charger, false);   // la sobrecarga no es llegar a la reserva: sin cooldown
        release();

        if (charger != null) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(charger);
            int loss = (int) Math.round(att.getBodyMax() * ServerConfig.kiChamberOverloadBodyPercent() / 100.0);
            att.setBody(Math.max(1, att.getBody() - loss));
            deny(charger, "messages.zenkai.ki_chamber.exploded");
        }

        float power = (float) ServerConfig.kiChamberOverloadPower();
        if (power <= 0f) return;
        final ServerPlayer spared = charger;
        // Quien cargaba ya pagó arriba, con suelo en 1: se le excluye del daño normal para que
        // la explosión no pueda matarlo según la armadura que lleve.
        net.minecraft.world.level.ExplosionDamageCalculator calc = new net.minecraft.world.level.ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(@NotNull net.minecraft.world.level.Explosion explosion,
                                              @NotNull net.minecraft.world.entity.Entity entity) {
                return entity != spared;
            }
        };
        level.explode(null, level.damageSources().explosion(null, null), calc,
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
                power, false, grief ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }

    /** Suelta mejoras y batería al romper el bloque. Vacía las ranuras además de soltar. */
    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack);
                upgrades.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** Reparte FE a los vecinos de las DOS mitades (misma lógica que el generador). */
    private void pushToNeighbours(Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) return;
        int budget = feMaxPerTick();
        for (int half = 0; half < 2 && budget > 0; half++) {
            BlockPos from = half == 0 ? pos : pos.above();
            for (Direction dir : Direction.values()) {
                if (budget <= 0 || energy.getEnergyStored() <= 0) return;
                BlockPos to = from.relative(dir);
                if (to.equals(pos) || to.equals(pos.above())) continue;   // la otra mitad
                IEnergyStorage target = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, to, dir.getOpposite());
                if (target == null || !target.canReceive()) continue;
                int accepted = target.receiveEnergy(Math.min(budget, energy.getEnergyStored()), false);
                if (accepted > 0) {
                    energy.extractEnergy(accepted, false);
                    budget -= accepted;
                    markDirty();
                }
            }
        }
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        // Título CORTO para la placa de cabecera (8..140 px): el nombre entero del bloque en mayúsculas
        // y negrita se salía de la placa y pisaba el piloto.
        return Component.translatable("screen.zenkai.ki_chamber.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new KiChamberMenu(id, inv, this);
    }

    // ── Persistencia y sincronización ────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        // El paquete de bloque trae SOLO GlowType (ver getUpdateTag): cada lectura tolera que falte el resto.
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        if (tag.contains("Energy")) energy.load(tag);
        if (tag.contains("Convert")) convert = tag.getBoolean("Convert");
        if (tag.contains("Force")) forceSetting = tag.getBoolean("Force");
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(reg, tag.getCompound("Upgrades"));
        if (tag.contains("Heat")) heat = tag.getDouble("Heat");
        glowType = tag.contains("GlowType") ? tag.getInt("GlowType") : -1;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
        energy.save(tag);
        tag.putBoolean("Convert", convert);
        tag.putBoolean("Force", forceSetting);
        tag.put("Upgrades", upgrades.serializeNBT(reg));
        tag.putDouble("Heat", heat);
        // GlowType NO se guarda: al recargar no hay sesión, así que la cámara arranca apagada.
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("GlowType", glowType);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** El chunk se descarga con alguien sujeto: se le suelta, o se quedaría inmóvil para siempre. */
    @Override
    public void onChunkUnloaded() {
        endSession();
        super.onChunkUnloaded();
    }

    /** Solo marca para guardar. Lo llaman el drenaje y el conversor, que corren cada tick. */
    private void markDirty() {
        if (level != null) level.blockEntityChanged(worldPosition);
    }

    /** Enciende o apaga el modelo: blockstate ACTIVE (animación) y tipo de brillo (paquete de bloque). */
    private void setGlow(@Nullable KiEnergyType type) {
        int next = type == null ? -1 : type.ordinal();
        if (level == null || level.isClientSide) { glowType = next; return; }
        boolean changed = glowType != next;
        glowType = next;
        BlockState st = getBlockState();
        boolean active = type != null;
        if (st.hasProperty(KiContainmentChamberBlock.ACTIVE)
                && st.getValue(KiContainmentChamberBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, st.setValue(KiContainmentChamberBlock.ACTIVE, active), 3);
        }
        if (changed) {
            markDirty();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, state -> {
            BlockState st = getBlockState();
            boolean active = st.hasProperty(KiContainmentChamberBlock.ACTIVE)
                    && st.getValue(KiContainmentChamberBlock.ACTIVE);
            return active ? state.setAndContinue(CHARGING) : PlayState.STOP;
        }));
        // Puerta: dos poses fijas; los 8 ticks de transición del controlador son el giro de las hojas.
        controllers.add(new AnimationController<>(this, "door", 8,
                state -> state.setAndContinue(isOpen() ? DOOR_OPEN : DOOR_CLOSED)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public double getTick(Object object) {
        return this.level != null ? this.level.getGameTime() : 0;
    }

    // ── Solo para el arnés de capturas y los gametests ───────────────────────

    /** Fuerza en CLIENTE el tipo que brilla (client/dev/ScreenShots, modo «chamber»). No toca lógica. */
    public void devSetGlow(int typeOrdinal) { glowType = typeOrdinal; }


    /**
     * Arranca una carga sin puerta ni teletransporte, con el ajuste Forzar que se pida. Devuelve
     * false si la cámara lo habría rechazado. Los tests llaman después a tickCharge.
     */
    public boolean testStartCharge(ServerPlayer sp, boolean force) {
        forceSetting = force;
        if (denyReason(sp) != null) return false;
        occupant = sp.getUUID();
        begin(sp);
        return true;
    }

    /** Lo mismo con un mob; después se llama a testMobTick. */
    public boolean testStartMob(LivingEntity e) {
        if (denyReason(e) != null) return false;
        occupant = e.getUUID();
        begin(e);
        return true;
    }

    public void testMobTick(LivingEntity e) { tickMob(e); }

    /** Un tick del ticker real (detección de la puerta incluida). */
    public void testDetect() { detect(); }

    /** Un tick de calor de sobrecarga; devuelve el calor resultante. */
    public double testHeatTick(ServerPlayer sp) {
        tickHeat(sp);
        return heat;
    }

    /** Aplica los límites de las mejoras y carga la batería una vez. */
    public void testUpgradeTick() {
        applyUpgradeLimits();
        chargeBattery();
    }

    /** Un tick del conversor, para probar tasas y tope sin esperar al ticker del bloque. */
    public int testConvertTick() {
        tickConvert();
        return lastFePerTick;
    }
}
