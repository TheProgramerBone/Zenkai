package com.hmc.zenkai.content.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Búfer de FE del banco de scouter. Consume; NO genera ni reparte.
 *
 * Por qué no se usa el EnergyStorage de NeoForge tal cual: hace falta que extractEnergy()
 * devuelva 0 SIEMPRE de cara al exterior (un cable no puede vaciar el banco) pero que el
 * propio banco sí pueda gastar. Con la clase de NeoForge eso obliga a un wrapper aparte para
 * las caras, y acaba habiendo dos objetos donde basta uno: aquí lo externo es la interfaz y
 * lo interno es spend(), que no forma parte de ella.
 *
 * Los dos límites son de diseño, no de balance fino:
 *   CAPACITY     — un búfer lleno termina cualquier mejora del catálogo de una sentada.
 *   MAX_RECEIVE  — techo de entrada por tick, para que una red grande no lo llene de golpe
 *                  y el jugador vea la barra subir.
 */
public class BenchEnergy implements IEnergyStorage {

    public static final int CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 1_000;

    private int energy;

    public int get() { return energy; }
    public int capacity() { return CAPACITY; }

    /** Gasto interno del banco. No pasa por la interfaz a propósito. */
    public boolean spend(int amount) {
        if (amount <= 0) return true;
        if (energy < amount) return false;
        energy -= amount;
        return true;
    }

    public void load(CompoundTag tag) {
        energy = Math.max(0, Math.min(CAPACITY, tag.getInt("Energy")));
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", energy);
    }

    // ── IEnergyStorage: solo entrada ─────────────────────────────────────────

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.min(CAPACITY - energy, Math.min(MAX_RECEIVE, maxReceive));
        if (accepted <= 0) return 0;
        if (!simulate) energy += accepted;
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) { return 0; }

    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return CAPACITY; }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return true; }
}