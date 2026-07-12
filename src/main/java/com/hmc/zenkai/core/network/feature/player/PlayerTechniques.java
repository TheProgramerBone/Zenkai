package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
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
 * Submódulo de PlayerStatsAttachment: técnicas ki del jugador.
 *  - unlockedTypes: tipos comprados con TP (una vez cada uno).
 *  - slots: instancias creadas (nombre + tipo + color + tamaño), en orden.
 *  - bindings: asignaciones al overlay de combate (estilo Cursed Fate). bindings[pos]
 *    (pos 0..8, teclas 1-9) = índice del slot asignado, o -1 vacío. Una técnica ocupa
 *    como mucho UNA posición. Diseñado para mezclar ki y físicas en el futuro.
 * Viaja en el save/load del attachment -> persiste, se copia al morir y se sincroniza
 * al cliente por PlayerLifeCycle.syncIfServer.
 */
public final class PlayerTechniques {

    public static final int BIND_POSITIONS = 9;

    private final Set<KiTechniqueType> unlockedTypes = new LinkedHashSet<>();
    private final List<KiTechnique> slots = new ArrayList<>();
    private final int[] bindings = new int[BIND_POSITIONS];

    public PlayerTechniques() {
        Arrays.fill(bindings, -1);
    }

    public boolean isUnlocked(KiTechniqueType t) { return unlockedTypes.contains(t); }
    public void unlock(KiTechniqueType t)        { unlockedTypes.add(t); }

    public List<KiTechnique> slots()             { return Collections.unmodifiableList(slots); }
    public KiTechnique slot(int i)               { return (i >= 0 && i < slots.size()) ? slots.get(i) : null; }
    public int slotCount()                       { return slots.size(); }

    public void addSlot(KiTechnique t)           { slots.add(t); }

    public void removeSlot(int i) {
        if (i < 0 || i >= slots.size()) return;
        slots.remove(i);
        // Reparar bindings: limpiar el borrado y desplazar los índices superiores.
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == i) bindings[p] = -1;
            else if (bindings[p] > i) bindings[p]--;
        }
    }

    // ── Bindings (overlay de combate) ────────────────────────────────────────

    /** Índice del slot asignado a la posición (0..8), o -1. */
    public int binding(int position) {
        return (position >= 0 && position < BIND_POSITIONS) ? bindings[position] : -1;
    }

    /** Posición donde está asignado el slot dado, o -1. */
    public int positionOf(int slotIndex) {
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == slotIndex) return p;
        }
        return -1;
    }

    /**
     * Asigna el slot a la posición (quitándolo de cualquier otra). position -1 = desasignar.
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

    // ── NBT ──────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag types = new ListTag();
        for (KiTechniqueType t : unlockedTypes) types.add(StringTag.valueOf(t.name()));
        tag.put("types", types);
        ListTag list = new ListTag();
        for (KiTechnique t : slots) list.add(t.save());
        tag.put("slots", list);
        tag.putIntArray("bindings", bindings.clone());
        return tag;
    }

    public void load(CompoundTag tag) {
        unlockedTypes.clear();
        slots.clear();
        Arrays.fill(bindings, -1);
        if (tag.contains("types")) {
            ListTag types = tag.getList("types", Tag.TAG_STRING);
            for (int i = 0; i < types.size(); i++) {
                KiTechniqueType t = KiTechniqueType.byName(types.getString(i));
                if (t != null) unlockedTypes.add(t);
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
                bindings[p] = (loaded[p] >= 0 && loaded[p] < slots.size()) ? loaded[p] : -1;
            }
        }
    }
}