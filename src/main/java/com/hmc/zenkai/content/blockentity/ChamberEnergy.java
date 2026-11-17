package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Búfer de FE de la cámara de contención de ki. Misma asimetría que GeneratorEnergy y por el
 * mismo motivo: SÍ deja extraer y NO deja meter, así la cámara solo empuja y no se puede montar
 * un bucle con otra máquina. Lo interno (el conversor de fluido a FE) no pasa por la interfaz:
 * usa generate().
 * Es una clase aparte y no una reutilización de GeneratorEnergy porque la capacidad y el tope
 * de salida salen de OTRAS claves de config (ki_chamber.*), y compartirlas ataría el balance de
 * las dos máquinas.
 */
public class ChamberEnergy implements IEnergyStorage {

    private int energy;
    /** Capacidad y tope por tick EFECTIVOS: los fija el block entity cada tick según sus mejoras
     *  de almacenamiento y de velocidad (Fase 2b). Arrancan en los valores base del config. */
    private int capacity = -1;
    private int maxOut = -1;

    public int get() { return energy; }
    public int capacity() { return capacity > 0 ? capacity : ServerConfig.kiChamberFeCapacity(); }
    public int maxOut() { return maxOut > 0 ? maxOut : ServerConfig.kiChamberFeMaxPerTick(); }

    /** Lo llama la máquina. Si la capacidad baja (se quitó una mejora), lo que sobra se pierde:
     *  es el precio de sacar la mejora con el búfer lleno, y evita guardar más de lo que cabe. */
    public void setLimits(int capacity, int maxOut) {
        this.capacity = Math.max(1, capacity);
        this.maxOut = Math.max(1, maxOut);
        if (energy > this.capacity) energy = this.capacity;
    }
    public int space() { return Math.max(0, capacity() - energy); }

    /** Producción interna. Devuelve lo que cupo. */
    public int generate(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, space());
        if (accepted <= 0) return 0;
        energy += accepted;
        return accepted;
    }

    public void load(CompoundTag tag) {
        // Sin recorte por capacidad: al cargar, las mejoras aún no han fijado los límites y
        // recortar al valor base tiraría la energía que cabía gracias al almacenamiento extra.
        energy = Math.max(0, tag.getInt("Energy"));
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", energy);
    }

    // ── IEnergyStorage: solo salida ──────────────────────────────────────────

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int given = Math.min(energy, Math.min(maxOut(), maxExtract));
        if (given <= 0) return 0;
        if (!simulate) energy -= given;
        return given;
    }

    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity(); }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return false; }
}
