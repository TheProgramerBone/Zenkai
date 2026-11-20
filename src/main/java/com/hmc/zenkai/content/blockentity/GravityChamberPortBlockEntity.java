package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.feature.sideconfig.SideConfigurable;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Puerto de energía de la cámara de gravedad. No guarda energía: su capability ES el búfer de la
 * consola enlazada, así que un cable que empuja aquí llena la consola directamente.
 *
 * Modos por cara (vocabulario común, la llave los cambia): NORMAL acepta lo que le empujen; PULL además
 * tira activamente del vecino cada tick (para fuentes que no empujan, como el tanque en NORMAL); NONE
 * desconecta la cara. PUSH no tiene sentido en un puerto que solo recibe (se pinta apagado).
 *
 * El enlace es responsabilidad de la consola ({@link GravityChamberBlockEntity#validate}): al validar
 * enlaza los puertos que tocan el interior y desenlaza los que ya no. La capability además comprueba
 * que la consola siga reconociendo este puerto, por si el enlace guardado quedó viejo.
 */
public class GravityChamberPortBlockEntity extends BlockEntity implements SideConfigurable {

    @Nullable private BlockPos controller;
    private final SideMode[] modes = new SideMode[6];

    public GravityChamberPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_CHAMBER_PORT.get(), pos, state);
        Arrays.fill(modes, SideMode.NORMAL);
    }

    @Nullable public BlockPos controller() { return controller; }

    /** Lo llama la consola al validar. Invalida la capability solo si cambia el enlace. */
    public void link(@Nullable BlockPos controllerPos) {
        if (java.util.Objects.equals(controller, controllerPos)) return;
        controller = controllerPos == null ? null : controllerPos.immutable();
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    @Nullable
    private GravityChamberBlockEntity linkedConsole() {
        if (controller == null || level == null || !level.isLoaded(controller)) return null;
        return level.getBlockEntity(controller) instanceof GravityChamberBlockEntity c && c.isPortLinked(worldPosition)
                ? c : null;
    }

    /** Capability de energía por cara: el búfer de la consola, o null si la cara está cerrada o no hay consola. */
    @Nullable
    public IEnergyStorage energyAt(@Nullable Direction side) {
        if (side != null && !modes[side.get3DDataValue()].canTake()) return null;
        GravityChamberBlockEntity c = linkedConsole();
        return c == null ? null : c.energyHandler();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityChamberPortBlockEntity be) {
        // Solo trabaja si alguna cara está en Tirar: el caso normal (empujan aquí) no cuesta nada.
        boolean anyPull = false;
        for (SideMode m : be.modes) anyPull |= m == SideMode.PULL;
        if (!anyPull) return;
        GravityChamberBlockEntity c = be.linkedConsole();
        if (c == null) return;
        IEnergyStorage into = c.energyHandler();
        for (Direction d : Direction.values()) {
            if (be.modes[d.get3DDataValue()] != SideMode.PULL) continue;
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n)) continue;
            IEnergyStorage from = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite());
            if (from == null || !from.canExtract()) continue;
            int room = into.receiveEnergy(Integer.MAX_VALUE, true);
            if (room <= 0) return;
            int got = from.extractEnergy(room, false);
            if (got > 0) into.receiveEnergy(got, false);
        }
    }

    // ── SideConfigurable ─────────────────────────────────────────────────────

    @Override public List<Channel> sideChannels() { return List.of(Channel.ENERGY); }

    @Override public SideMode sideMode(Channel channel, Direction face) { return modes[face.get3DDataValue()]; }

    @Override
    public void setSideMode(Channel channel, Direction face, SideMode mode) {
        modes[face.get3DDataValue()] = mode;
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean sideModeUseful(Channel channel, SideMode mode) { return mode != SideMode.PUSH; }

    // ── NBT y sync (los modos viajan para el sombreado de la llave) ──────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (controller != null) tag.put("Controller", NbtUtils.writeBlockPos(controller));
        byte[] b = new byte[6];
        for (int i = 0; i < 6; i++) b[i] = (byte) modes[i].ordinal();
        tag.putByteArray("Modes", b);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        byte[] b = tag.getByteArray("Modes");
        for (int i = 0; i < 6; i++) {
            int o = i < b.length ? b[i] : 0;
            modes[i] = o >= 0 && o < SideMode.values().length ? SideMode.values()[o] : SideMode.NORMAL;
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, reg);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
