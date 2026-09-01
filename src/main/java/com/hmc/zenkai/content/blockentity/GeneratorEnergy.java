package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.CommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Búfer de FE del generador. Al revés que BenchEnergy: este SÍ deja extraer y NO deja meter.
energy_generator * La asimetría es deliberada y es lo que hace que la pareja generador + banco funcione sin
 * configurar nada: el generador solo empuja, el banco solo recibe, y no hay forma de montar
 * un bucle en el que dos máquinas se pasen la misma energía. Con un IEnergyStorage genérico
 * en ambos lados habría que decidir por cara qué hace cada una.
energy_generator * receiveEnergy devuelve 0 de cara al exterior pero generate() sí llena: lo mismo que hace
 * BenchEnergy con spend(), en espejo. Lo interno no pasa por la interfaz.
 */
public class GeneratorEnergy implements IEnergyStorage {

    private int energy;

    public int get() { return energy; }
    public int capacity() { return CommonConfig.energyGeneratorCapacity(); }

    /** Producción interna. Devuelve lo que realmente cupo: si el búfer está lleno, el
     *  generador debe SABERLO para no seguir quemando combustible a cambio de nada. */
    public int generate(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, capacity() - energy);
        if (accepted <= 0) return 0;
        energy += accepted;
        return accepted;
    }

    public boolean isFull() { return energy >= capacity(); }

    public void load(CompoundTag tag) {
        energy = Math.max(0, Math.min(capacity(), tag.getInt("Energy")));
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", energy);
    }

    // ── IEnergyStorage: solo salida ──────────────────────────────────────────

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int given = Math.min(energy, Math.min(CommonConfig.energyGeneratorMaxExtract(), maxExtract));
        if (given <= 0) return 0;
        if (!simulate) energy -= given;
        return given;
    }

    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity(); }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return false; }
}