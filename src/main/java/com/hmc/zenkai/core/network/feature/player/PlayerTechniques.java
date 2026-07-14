package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import com.hmc.zenkai.core.technique.PhysicalTechnique;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Submódulo de PlayerStatsAttachment: técnicas ki y físicas del jugador.
 *  - unlockedTypes: tipos ki comprados con TP (una vez cada uno).
 *  - unlockedPhysical: técnicas físicas compradas con TP (maestros después).
 *  - slots: instancias ki creadas (nombre + tipo + color + tamaño), en orden.
 *  - bindings: asignaciones al overlay de combate (estilo Cursed Fate). bindings[pos]
 *    (pos 0..8, teclas 1-9) = índice del slot ki asignado (>= 0), o técnica física
 *    codificada como PHYS_BIND_BASE - ordinal (<= -100), o -1 vacío. Una técnica ocupa
 *    como mucho UNA posición.
 * Viaja en el save/load del attachment -> persiste, se copia al morir y se sincroniza
 * al cliente por PlayerLifeCycle.syncIfServer.
 */
public final class PlayerTechniques {

    public static final int BIND_POSITIONS = 9;

    /** Encoding de bindings físicos dentro del mismo int[]: -100 - ordinal (ki >= 0, -1 vacío). */
    private static final int PHYS_BIND_BASE = -100;

    private final Set<KiTechniqueType> unlockedTypes = new LinkedHashSet<>();
    private final Set<PhysicalTechnique> unlockedPhysical = new LinkedHashSet<>();
    private final List<KiTechnique> slots = new ArrayList<>();
    private final int[] bindings = new int[BIND_POSITIONS];

    public PlayerTechniques() {
        Arrays.fill(bindings, -1);
    }

    public boolean isUnlocked(KiTechniqueType t) { return unlockedTypes.contains(t); }
    public void unlock(KiTechniqueType t)        { unlockedTypes.add(t); }

    public boolean isUnlocked(PhysicalTechnique t) { return unlockedPhysical.contains(t); }
    public void unlock(PhysicalTechnique t)        { unlockedPhysical.add(t); }

    public List<KiTechnique> slots()             { return Collections.unmodifiableList(slots); }
    public KiTechnique slot(int i)               { return (i >= 0 && i < slots.size()) ? slots.get(i) : null; }
    public int slotCount()                       { return slots.size(); }

    public void addSlot(KiTechnique t)           { slots.add(t); }

    public void removeSlot(int i) {
        if (i < 0 || i >= slots.size()) return;
        slots.remove(i);
        // Reparar bindings ki: limpiar el borrado y desplazar los índices superiores.
        // (Los físicos son <= PHYS_BIND_BASE: ninguna de las dos ramas los toca.)
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == i) bindings[p] = -1;
            else if (bindings[p] > i) bindings[p]--;
        }
    }

    // ── Bindings (overlay de combate) ────────────────────────────────────────

    /** Valor crudo asignado a la posición (índice ki >= 0, físico <= -100), o -1. */
    public int binding(int position) {
        return (position >= 0 && position < BIND_POSITIONS) ? bindings[position] : -1;
    }

    /** Técnica física asignada a la posición, o null si está vacía o es ki. */
    public PhysicalTechnique physicalBinding(int position) {
        int v = binding(position);
        return v <= PHYS_BIND_BASE ? PhysicalTechnique.byOrdinal(PHYS_BIND_BASE - v) : null;
    }

    /** Posición donde está asignado el slot ki dado, o -1. */
    public int positionOf(int slotIndex) {
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == slotIndex) return p;
        }
        return -1;
    }

    /** Posición donde está asignada la física dada, o -1. */
    public int positionOf(PhysicalTechnique t) {
        int encoded = PHYS_BIND_BASE - t.ordinal();
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == encoded) return p;
        }
        return -1;
    }

    /**
     * Asigna el slot ki a la posición (quitándolo de cualquier otra). position -1 = desasignar.
     * Valida rangos; slotIndex fuera de rango solo desasigna.
     */
    public void bind(int position, int slotIndex) {
        boolean validSlot = slotIndex >= 0 && slotIndex < slots.size();
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == slotIndex) bindings[p] = -1;
        }
        if (validSlot && position >= 0 && position < BIND_POSITIONS) {
            bindings[position] = slotIndex;
        }
    }

    /** Asigna una física a la posición (quitándola de cualquier otra). position -1 = desasignar. */
    public void bindPhysical(int position, PhysicalTechnique t) {
        int encoded = PHYS_BIND_BASE - t.ordinal();
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == encoded) bindings[p] = -1;
        }
        if (position >= 0 && position < BIND_POSITIONS) {
            bindings[position] = encoded;
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag types = new ListTag();
        for (KiTechniqueType t : unlockedTypes) types.add(StringTag.valueOf(t.name()));
        tag.put("types", types);
        ListTag phys = new ListTag();
        for (PhysicalTechnique t : unlockedPhysical) phys.add(StringTag.valueOf(t.name()));
        tag.put("physical", phys);
        ListTag list = new ListTag();
        for (KiTechnique t : slots) list.add(t.save());
        tag.put("slots", list);
        tag.putIntArray("bindings", bindings.clone());
        return tag;
    }

    public void load(CompoundTag tag) {
        unlockedTypes.clear();
        unlockedPhysical.clear();
        slots.clear();
        Arrays.fill(bindings, -1);
        if (tag.contains("types")) {
            ListTag types = tag.getList("types", Tag.TAG_STRING);
            for (int i = 0; i < types.size(); i++) {
                KiTechniqueType t = KiTechniqueType.byName(types.getString(i));
                if (t != null) unlockedTypes.add(t);
            }
        }
        if (tag.contains("physical")) {
            ListTag phys = tag.getList("physical", Tag.TAG_STRING);
            for (int i = 0; i < phys.size(); i++) {
                PhysicalTechnique t = PhysicalTechnique.byName(phys.getString(i));
                if (t != null) unlockedPhysical.add(t);
            }
        }
        if (tag.contains("slots")) {
            ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                KiTechnique t = KiTechnique.load(list.getCompound(i));
                if (t != null) slots.add(t);
            }
        }
        if (tag.contains("bindings")) {
            int[] loaded = tag.getIntArray("bindings");
            for (int p = 0; p < Math.min(loaded.length, BIND_POSITIONS); p++) {
                int v = loaded[p];
                boolean validKi = v >= 0 && v < slots.size();
                boolean validPhys = v <= PHYS_BIND_BASE
                        && PhysicalTechnique.byOrdinal(PHYS_BIND_BASE - v) != null;
                bindings[p] = (validKi || validPhys) ? v : -1;
            }
        }
    }
}