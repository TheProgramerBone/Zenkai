package com.hmc.zenkai.compat.create;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.transport.SideMode;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Motor de Ki (0.7.0, compat OPCIONAL con Create): convierte FE y fluido de Ki en rotación de
 * Create y, con el modo conmutado, rotación en FE. Lo de Create entero está en este paquete y nada
 * de fuera lo nombra: sin Create esta clase no se carga nunca (ver {@link KiMotorLoader}).
 *
 * <b>Un solo búfer de FE.</b> El fluido de Ki NO tiene una curva propia hacia SU/RPM: se convierte
 * a FE con {@link KiEnergyType#fePerMb()} —la misma tabla que la cámara de contención— y desde ahí
 * alimenta el eje. Así hay un único balance (FE por SU) y cada tipo de ki escala solo.
 *
 * <b>Modo MOTOR:</b> ofrece {@code ki_motor.rated_su} SU a la red de Create a la velocidad elegida
 * (la capacidad por RPM es SU/RPM: Create multiplica capacidad por velocidad). Solo PAGA la carga que
 * la red usa de verdad (stress/capacity), no el nominal; sin FE suficiente se apaga y la red para.
 *
 * <b>Modo GENERADOR:</b> es un consumidor de Create ({@code generator_impact} SU por RPM) y rinde
 * {@code generator_fe_per_su} FE/t por SU absorbido, siempre por DEBAJO de lo que cuesta el modo
 * motor (0,04 contra 0,05 de serie): un motor y un generador encadenados no fabrican energía.
 *
 * <b>Puertos:</b> cada cara menos la del eje tiene un {@link SideMode} para la energía y otro para
 * el fluido, con el mismo vocabulario que cables y tanques (Normal/Empujar/Tirar/Nada). En modo
 * motor la energía y el fluido solo ENTRAN (Normal o Tirar); en modo generador la energía solo
 * SALE (Normal o Empujar) y el fluido no se usa.
 */
public class KiMotorBlockEntity extends GeneratingKineticBlockEntity
        implements KiMotorControl, com.hmc.zenkai.feature.sideconfig.SideConfigurable {

    // Mode y RPM_STEPS se heredan de KiMotorControl (KiMotorBlockEntity.Mode sigue valiendo).

    private Mode mode = Mode.MOTOR;
    private int speedStep = 1;
    private boolean reversed = false;

    private final SideMode[] energyModes = new SideMode[6];
    private final SideMode[] fluidModes = new SideMode[6];

    private final Energy energy = new Energy();
    /** Ojo: FluidTank.fill y getSpace leen su CAMPO de capacidad, no getCapacity(). Sobrescribir el
     *  getter dejaba el tanque capado a 1000 mB aunque la config dijera 4000 (lo vio el usuario con una
     *  tubería, 2026-11-17; la misma trampa que FluidTransportBlockEntity). Se fija con setCapacity en
     *  el constructor y en cada tick, por si la config cambia en caliente. */
    private final FluidTank tank = new FluidTank(1000, fs -> KiEnergyType.ofFluid(fs.getFluid()) != null) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    private void refreshTankCapacity() {
        int cap = ServerConfig.kiMotorTankCapacity();
        if (tank.getCapacity() != cap) tank.setCapacity(cap);
    }

    /** ¿Está pagando y girando? (solo modo MOTOR). Lo que Create ve sale de aquí. */
    private boolean active = false;
    /** Lo último que se le dijo a Create, para llamar a updateGeneratedRotation solo si cambia. */
    private float appliedSpeed = 0f;
    /** Resto fraccionario de FE entre ticks (0,05 FE/t por SU no es entero). */
    private double carry = 0.0;

    // ── Lectura para la pantalla (se sincroniza cada 10 ticks si cambia) ─────
    /** FE pagados (motor) o producidos (generador) en la ventana actual de 10 ticks. */
    private int windowFe = 0;
    private int feRateX10 = 0;
    private int loadPercent = 0;
    /** Lo último enviado al cliente, para no mandar sendData si nada se movió. */
    private int sentFe = -1, sentKi = -1, sentRate = -1, sentLoad = -1;

    public KiMotorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < 6; i++) {
            energyModes[i] = SideMode.NORMAL;
            fluidModes[i] = SideMode.NORMAL;
        }
        refreshTankCapacity();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Sin ScrollValueBehaviour: la velocidad se elige en la pantalla propia, no con la rueda.
    }

    // ── Estado y ajustes (los usa la pantalla y el bloque) ───────────────────

    public Mode mode() { return mode; }
    public int speedStep() { return speedStep; }
    public int rpm() { return RPM_STEPS[speedStep]; }
    public boolean reversed() { return reversed; }
    public boolean active() { return active; }
    public int fe() { return energy.energy; }
    public FluidTank tank() { return tank; }
    public SideMode energyMode(Direction d) { return energyModes[d.ordinal()]; }
    public SideMode fluidMode(Direction d) { return fluidModes[d.ordinal()]; }
    @Override public int feCapacity() { return energy.capacity(); }
    @Override public FluidStack kiFluid() { return tank.getFluid(); }
    @Override public int kiCapacity() { return tank.getCapacity(); }
    @Override public Direction shaftSide() { return shaftFace(); }
    @Override public int loadPercent() { return loadPercent; }
    @Override public int feRateX10() { return feRateX10; }

    private Direction shaftFace() { return getBlockState().getValue(KiMotorBlock.FACING); }

    public void setMode(Mode m) {
        if (m == mode) return;
        mode = m;
        active = false;
        carry = 0.0;
        // Las caras que solo servían al modo anterior se vuelven Normal: una cara en Tirar (útil en
        // motor) no puede dar FE, y el generador parecía mudo al conmutar sin que se viera por qué.
        for (int i = 0; i < 6; i++) {
            if (!sideModeUseful(Channel.ENERGY, energyModes[i])) energyModes[i] = SideMode.NORMAL;
            if (!sideModeUseful(Channel.FLUID, fluidModes[i])) fluidModes[i] = SideMode.NORMAL;
        }
        // El papel cambia (fuente <-> consumidor): Create tiene que volver a sumar la red.
        detachKinetics();
        appliedSpeed = 0f;
        attachKinetics();
        reActivateSource = true;
        syncAll();
    }

    public void setSpeedStep(int step) {
        speedStep = Math.floorMod(step, RPM_STEPS.length);
        syncAll();
    }

    @Override
    public void rotateKinetics(Runnable change) {
        detachKinetics();
        appliedSpeed = 0f;
        change.run();
        attachKinetics();
        reActivateSource = true;
    }

    public void toggleReversed() {
        reversed = !reversed;
        syncAll();
    }

    /** Cicla el modo de la cara para la energía (fluid=false) o el fluido (fluid=true). La cara del
     *  eje no es configurable. */
    public SideMode cyclePort(Direction face, boolean fluid) {
        if (face == shaftFace()) return SideMode.NONE;
        SideMode[] arr = fluid ? fluidModes : energyModes;
        arr[face.ordinal()] = arr[face.ordinal()].next();
        syncAll();
        return arr[face.ordinal()];
    }

    // ── Pantalla de caras (SideConfigurable): energía y fluido; el eje, bloqueado ──

    @Override
    public java.util.List<Channel> sideChannels() { return java.util.List.of(Channel.ENERGY, Channel.FLUID); }

    @Override
    public SideMode sideMode(Channel channel, Direction face) {
        return channel == Channel.FLUID ? fluidMode(face) : energyMode(face);
    }

    @Override
    public void setSideMode(Channel channel, Direction face, SideMode m) {
        if (face == shaftFace()) return;
        (channel == Channel.FLUID ? fluidModes : energyModes)[face.ordinal()] = m;
        syncAll();
    }

    @Override
    public boolean sideLocked(Direction face) { return face == shaftFace(); }

    /** En MOTOR energía y fluido solo entran (Normal/Tirar); en GENERADOR la energía solo sale y el fluido no se usa. */
    @Override
    public boolean sideModeUseful(Channel channel, SideMode m) {
        if (m == SideMode.NONE) return true;
        if (mode == Mode.MOTOR) return m.canTake();
        return channel == Channel.ENERGY && m.canGive();
    }

    private void syncAll() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
            level.invalidateCapabilities(worldPosition);
        }
    }

    // ── Create: lo que ofrece y lo que consume ───────────────────────────────

    @Override
    public float getGeneratedSpeed() {
        if (mode != Mode.MOTOR || !active) return 0f;
        float rpm = rpm() * (reversed ? -1f : 1f);
        return convertToDirection(rpm, shaftFace());
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (mode != Mode.MOTOR || !active) {
            lastCapacityProvided = 0f;
            return 0f;
        }
        lastCapacityProvided = (float) ServerConfig.kiMotorRatedSu() / rpm();
        return lastCapacityProvided;
    }

    @Override
    public float calculateStressApplied() {
        float impact = mode == Mode.GENERATOR ? ServerConfig.kiMotorGeneratorImpact() : 0f;
        lastStressApplied = impact;
        return impact;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        refreshTankCapacity();
        if (level == null || level.isClientSide) return;

        if (mode == Mode.MOTOR) tickMotor();
        else tickGenerator();

        if (level.getGameTime() % 10 == 0) syncReadout();
    }

    /**
     * Cierra la ventana de 10 ticks y, si algo de lo que muestra la pantalla cambió, manda el estado
     * al cliente. Sin esto la pantalla vería el FE y el tanque congelados: el tick solo hace
     * setChanged(), que guarda pero no sincroniza. Un motor parado y lleno no manda nada.
     */
    private void syncReadout() {
        feRateX10 = windowFe;
        windowFe = 0;
        loadPercent = mode == Mode.MOTOR && active && capacity > 0
                ? (int) Math.round(Math.min(1.0, Math.max(0.0, stress / capacity)) * 100.0) : 0;
        int ki = tank.getFluidAmount();
        if (energy.energy != sentFe || ki != sentKi || feRateX10 != sentRate || loadPercent != sentLoad) {
            sentFe = energy.energy;
            sentKi = ki;
            sentRate = feRateX10;
            sentLoad = loadPercent;
            sendData();
        }
    }

    private void tickMotor() {
        pullInputs();
        convertFluid();

        double rated = ServerConfig.kiMotorRatedSu() * ServerConfig.kiMotorFePerSu();
        if (!active) {
            // Arranque: hace falta poder pagar un tick a plena carga. Evita encender y apagar cada tick.
            if (energy.energy >= Math.ceil(rated) && energy.energy > 0) active = true;
        } else {
            double load = capacity > 0 ? Math.min(1.0, Math.max(0.0, stress / capacity)) : 0.0;
            carry += rated * load;
            int pay = (int) carry;
            if (pay > energy.energy) {
                // No puede pagar la carga de este tick: se apaga y la red de Create se detiene.
                active = false;
                carry = 0.0;
            } else {
                carry -= pay;
                energy.energy -= pay;
                windowFe += pay;
                if (pay > 0) setChanged();
            }
        }

        float wanted = getGeneratedSpeed();
        if (wanted != appliedSpeed) {
            appliedSpeed = wanted;
            updateGeneratedRotation();
            syncAll();
        }
    }

    /** Un tick del modo generador. Público para los gametests, donde no hay red de Create que dé velocidad. */
    public void tickGenerator() {
        if (!overStressed && speed != 0f) {
            carry += Math.abs(speed) * ServerConfig.kiMotorGeneratorImpact() * ServerConfig.kiMotorGeneratorFePerSu();
            int whole = (int) carry;
            if (whole > 0) {
                carry -= whole;
                energy.energy = Math.min(energy.capacity(), energy.energy + whole);
                windowFe += whole;
                setChanged();
            }
        } else {
            carry = 0.0;
        }
        pushOutputs();
    }

    /** Modo TIRAR de la energía y del fluido: saca de lo que hay delante. */
    private void pullInputs() {
        int budget = ServerConfig.kiMotorIoPerTick();
        for (Direction d : Direction.values()) {
            if (d == shaftFace()) continue;
            BlockPos np = worldPosition.relative(d);
            if (energyModes[d.ordinal()] == SideMode.PULL && energy.space() > 0 && budget > 0) {
                IEnergyStorage src = level.getCapability(Capabilities.EnergyStorage.BLOCK, np, d.getOpposite());
                if (src != null && src.canExtract()) {
                    int got = src.extractEnergy(Math.min(budget, energy.space()), false);
                    energy.energy += got;
                    budget -= got;
                    if (got > 0) setChanged();
                }
            }
            if (fluidModes[d.ordinal()] == SideMode.PULL && tank.getSpace() > 0) {
                IFluidHandler src = level.getCapability(Capabilities.FluidHandler.BLOCK, np, d.getOpposite());
                if (src != null) {
                    FluidStack probe = src.drain(tank.getSpace(), IFluidHandler.FluidAction.SIMULATE);
                    if (!probe.isEmpty() && tank.isFluidValid(probe)
                            && (tank.isEmpty() || tank.getFluid().is(probe.getFluid()))) {
                        int took = tank.fill(probe, IFluidHandler.FluidAction.EXECUTE);
                        if (took > 0) src.drain(new FluidStack(probe.getFluid(), took), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }
    }

    /** Fluido de Ki -> FE del búfer, con la tabla de la cámara de contención. */
    private void convertFluid() {
        if (tank.isEmpty()) return;
        KiEnergyType type = KiEnergyType.ofFluid(tank.getFluid().getFluid());
        if (type == null) return;
        int perMb = Math.max(1, type.fePerMb());
        int mb = Math.min(tank.getFluidAmount(), Math.min(energy.space(), ServerConfig.kiMotorIoPerTick()) / perMb);
        if (mb <= 0) return;
        tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        energy.energy += mb * perMb;
        setChanged();
    }

    /** Modo generador: empuja FE a lo que haya delante en las caras Normal/Empujar. */
    private void pushOutputs() {
        int budget = Math.min(ServerConfig.kiMotorIoPerTick(), energy.energy);
        for (Direction d : Direction.values()) {
            if (budget <= 0) break;
            if (d == shaftFace() || !energyModes[d.ordinal()].canGive()) continue;
            IEnergyStorage dst = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(d), d.getOpposite());
            if (dst == null || !dst.canReceive()) continue;
            int sent = dst.receiveEnergy(budget, false);
            energy.energy -= sent;
            budget -= sent;
        }
    }

    // ── Capabilities (las cablea KiMotorCompat) ──────────────────────────────

    @Nullable
    public IEnergyStorage energyAt(@Nullable Direction side) {
        if (side == null) return energy;
        if (side == shaftFace() || energyModes[side.ordinal()] == SideMode.NONE) return null;
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int max, boolean sim) {
                if (mode != Mode.MOTOR || !energyModes[side.ordinal()].canTake()) return 0;
                int ok = Math.min(Math.min(max, ServerConfig.kiMotorIoPerTick()), energy.space());
                if (ok > 0 && !sim) { energy.energy += ok; setChanged(); }
                return Math.max(ok, 0);
            }
            @Override public int extractEnergy(int max, boolean sim) {
                if (mode != Mode.GENERATOR || !energyModes[side.ordinal()].canGive()) return 0;
                int ok = Math.min(Math.min(max, ServerConfig.kiMotorIoPerTick()), energy.energy);
                if (ok > 0 && !sim) { energy.energy -= ok; setChanged(); }
                return Math.max(ok, 0);
            }
            @Override public int getEnergyStored() { return energy.energy; }
            @Override public int getMaxEnergyStored() { return energy.capacity(); }
            @Override public boolean canExtract() { return mode == Mode.GENERATOR && energyModes[side.ordinal()].canGive(); }
            @Override public boolean canReceive() { return mode == Mode.MOTOR && energyModes[side.ordinal()].canTake(); }
        };
    }

    @Nullable
    public IFluidHandler fluidAt(@Nullable Direction side) {
        if (side == null) return tank;
        if (side == shaftFace() || fluidModes[side.ordinal()] == SideMode.NONE) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
            @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return tank.isFluidValid(fs); }
            @Override public int fill(FluidStack fs, FluidAction action) {
                // Solo entra en modo motor, por una cara que pueda tomar, y sin mezclar tipos de ki.
                if (mode != Mode.MOTOR || !fluidModes[side.ordinal()].canTake()) return 0;
                if (!tank.isEmpty() && !tank.getFluid().is(fs.getFluid())) return 0;
                return tank.fill(fs, action);
            }
            // El fluido solo se convierte: nadie lo saca (como la cámara, que no es un tanque).
            @Override public FluidStack drain(FluidStack fs, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int max, FluidAction action) { return FluidStack.EMPTY; }
        };
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Mode", mode.name());
        tag.putInt("SpeedStep", speedStep);
        tag.putBoolean("Reversed", reversed);
        tag.putBoolean("Active", active);
        tag.putInt("Energy", energy.energy);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        int[] e = new int[6], f = new int[6];
        for (int i = 0; i < 6; i++) { e[i] = energyModes[i].ordinal(); f[i] = fluidModes[i].ordinal(); }
        tag.putIntArray("EnergyModes", e);
        tag.putIntArray("FluidModes", f);
        if (clientPacket) {
            // Solo lectura de pantalla: no se guarda en disco, se recalcula a los 10 ticks.
            tag.putInt("FeRateX10", feRateX10);
            tag.putInt("LoadPercent", loadPercent);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        try { mode = Mode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException ex) { mode = Mode.MOTOR; }
        speedStep = Math.floorMod(tag.getInt("SpeedStep"), RPM_STEPS.length);
        reversed = tag.getBoolean("Reversed");
        active = tag.getBoolean("Active");
        energy.energy = Math.max(0, tag.getInt("Energy"));
        refreshTankCapacity();
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        int[] e = tag.getIntArray("EnergyModes"), f = tag.getIntArray("FluidModes");
        SideMode[] all = SideMode.values();
        for (int i = 0; i < 6; i++) {
            energyModes[i] = i < e.length && e[i] >= 0 && e[i] < all.length ? all[e[i]] : SideMode.NORMAL;
            fluidModes[i] = i < f.length && f[i] >= 0 && f[i] < all.length ? all[f[i]] : SideMode.NORMAL;
        }
        if (clientPacket) {
            feRateX10 = tag.getInt("FeRateX10");
            loadPercent = tag.getInt("LoadPercent");
        }
    }

    /** Búfer de FE. Los límites de entrada/salida los pone cada cara (energyAt); el acceso sin
     *  cara (null) es solo para pantallas y gametests y no limita. */
    private final class Energy implements IEnergyStorage {
        int energy;
        int capacity() { return ServerConfig.kiMotorFeCapacity(); }
        int space() { return Math.max(0, capacity() - energy); }
        @Override public int receiveEnergy(int max, boolean sim) {
            int ok = Math.min(max, space());
            if (ok > 0 && !sim) { energy += ok; setChanged(); }
            return Math.max(ok, 0);
        }
        @Override public int extractEnergy(int max, boolean sim) {
            int ok = Math.min(max, energy);
            if (ok > 0 && !sim) { energy -= ok; setChanged(); }
            return Math.max(ok, 0);
        }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }
}
