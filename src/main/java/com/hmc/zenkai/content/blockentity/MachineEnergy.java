package com.hmc.zenkai.content.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.IntSupplier;

/**
 * Búfer de FE común de las máquinas de Zenkai (2026-11-23: antes había cinco copias casi iguales, una por máquina).
 *
 * Por qué no el EnergyStorage de NeoForge: cada máquina es ASIMÉTRICA a propósito y lo interno no pasa por la interfaz.
 * Las que producen (generador, cámara de contención) dejan SACAR y no dejan METER, y llenan con {@link #generate}; las que
 * consumen (banco, cámara de gravedad, talleres de armadura, infusora) dejan METER y no dejan SACAR, y gastan con
 * {@link #spend}. Así un cable no puede vaciar una consumidora ni rellenar una productora, y no se puede montar un bucle
 * en el que dos máquinas se pasen la misma energía (con un IEnergyStorage genérico en ambos lados habría que decidir por
 * cara qué hace cada una).
 *
 * Capacidad y topes por tick se leen de ServerConfig en cada uso ({@link IntSupplier}): la config se puede cambiar en
 * caliente. {@link #setLimits} los fija a mano para las máquinas cuyas mejoras los cambian; un valor fijado manda sobre
 * el de la config hasta que se vuelva a fijar.
 *
 * {@link #clampOnLoad}: la mayoría recorta lo guardado a la capacidad al cargar; las que amplían la capacidad con mejoras
 * NO (al cargar las mejoras aún no han fijado los límites y recortar al valor base tiraría la energía que cabía gracias
 * al almacenamiento extra).
 */
public class MachineEnergy implements IEnergyStorage {

    /** Qué deja hacer la interfaz desde fuera (cables, baterías, otros mods). */
    public enum Io { INPUT, OUTPUT }

    protected int energy;
    private final Io io;
    private final IntSupplier baseCapacity, baseRate;
    private final boolean clampOnLoad;
    private final Runnable changed;
    private int capacity = -1, rate = -1;

    /**
     * @param io           si desde fuera solo se mete (consumidora) o solo se saca (productora)
     * @param baseCapacity capacidad sin mejoras (normalmente una clave de ServerConfig)
     * @param baseRate     tope por tick de la única dirección que deja la interfaz
     * @param clampOnLoad  si al cargar se recorta a la capacidad base (ver la nota de la clase)
     * @param changed      aviso de cambio al recibir o dar por la interfaz (setChanged de la máquina; puede ser vacío)
     */
    public MachineEnergy(Io io, IntSupplier baseCapacity, IntSupplier baseRate, boolean clampOnLoad, Runnable changed) {
        this.io = io;
        this.baseCapacity = baseCapacity;
        this.baseRate = baseRate;
        this.clampOnLoad = clampOnLoad;
        this.changed = changed;
    }

    // ── Lectura y límites ────────────────────────────────────────────────────
    public int get() { return energy; }
    public int capacity() { return capacity > 0 ? capacity : baseCapacity.getAsInt(); }
    /** Tope por tick de la dirección de la interfaz (entrada en las consumidoras, salida en las productoras). */
    public int rate() { return rate > 0 ? rate : baseRate.getAsInt(); }
    public int space() { return Math.max(0, capacity() - energy); }
    public boolean isFull() { return energy >= capacity(); }

    /**
     * Fija capacidad y tope (mejoras de almacenamiento y velocidad). Si la capacidad baja (se quitó una mejora), lo que
     * sobra se pierde: es el precio de sacar la mejora con el búfer lleno, y evita guardar más de lo que cabe.
     */
    public void setLimits(int capacity, int rate) {
        this.capacity = Math.max(1, capacity);
        this.rate = Math.max(1, rate);
        if (energy > this.capacity) energy = this.capacity;
    }

    /** Solo la capacidad, con el tope de la config. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        if (energy > this.capacity) energy = this.capacity;
    }

    // ── Uso interno (no pasa por la interfaz) ────────────────────────────────

    /** Producción interna. Devuelve lo que cupo: si el búfer está lleno la máquina debe SABERLO para no seguir
     *  quemando combustible (o ki) a cambio de nada. */
    public int generate(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, space());
        if (accepted <= 0) return 0;
        energy += accepted;
        return accepted;
    }

    /** Gasto interno: todo o nada. */
    public boolean spend(int amount) {
        if (amount <= 0) return true;
        if (energy < amount) return false;
        energy -= amount;
        return true;
    }

    /** Gasto interno de una cantidad ya validada (el llamador comprobó antes que había). */
    public void drain(long amount) { energy = (int) Math.max(0, energy - amount); }

    /** Solo para gametests y depuración: fija lo almacenado sin pasar por los topes. */
    public void setForTests(int fe) { energy = Math.max(0, Math.min(capacity(), fe)); }

    // ── Persistencia ─────────────────────────────────────────────────────────
    public void load(CompoundTag tag) {
        int v = Math.max(0, tag.getInt("Energy"));
        energy = clampOnLoad ? Math.min(capacity(), v) : v;
    }

    public void save(CompoundTag tag) { tag.putInt("Energy", energy); }

    // ── IEnergyStorage ───────────────────────────────────────────────────────
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (io != Io.INPUT) return 0;
        int accepted = Math.min(space(), Math.min(rate(), maxReceive));
        if (accepted <= 0) return 0;
        if (!simulate) { energy += accepted; changed.run(); }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (io != Io.OUTPUT) return 0;
        int given = Math.min(energy, Math.min(rate(), maxExtract));
        if (given <= 0) return 0;
        if (!simulate) { energy -= given; changed.run(); }
        return given;
    }

    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity(); }
    @Override public boolean canExtract() { return io == Io.OUTPUT; }
    @Override public boolean canReceive() { return io == Io.INPUT; }
}
