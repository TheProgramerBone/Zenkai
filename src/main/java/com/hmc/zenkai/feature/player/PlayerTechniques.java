package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
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

    /** Vista de solo lectura, en el orden en que se desbloquearon (LinkedHashSet). Para
     *  MasteryScreen: listar por orden de desbloqueo en vez del orden fijo del enum. */
    public Set<KiTechniqueType> unlockedTypesOrdered() {
        return Collections.unmodifiableSet(unlockedTypes);
    }

    /** Igual que {@link #unlockedTypesOrdered()} pero para técnicas físicas. */
    public Set<PhysicalTechnique> unlockedPhysicalOrdered() {
        return Collections.unmodifiableSet(unlockedPhysical);
    }

    /**
     * Olvida un tipo de ki Y BORRA sus instancias guardadas.
     * Las instancias tienen que irse con el tipo: si se quedan, el jugador sigue lanzándolas
     * desde la barra porque el disparo no revalida el desbloqueo — solo lo hace el guardado.
     * Se recorre hacia atrás para que removeSlot pueda reparar los bindings sin que los
     * índices que faltan por visitar se muevan bajo los pies del bucle.
     */
    public boolean forget(KiTechniqueType t) {
        if (!unlockedTypes.remove(t)) return false;
        for (int i = slots.size() - 1; i >= 0; i--) {
            if (slots.get(i).type() == t) removeSlot(i);
        }
        return true;
    }

    /** Olvida una técnica física y la quita de la barra. */
    public boolean forget(PhysicalTechnique t) {
        if (!unlockedPhysical.remove(t)) return false;
        bindPhysical(-1, t);   // -1 desasigna de donde estuviera
        return true;
    }

    /** Reset full: olvida (tipos ki, técnicas físicas, instancias y bindings). A
     *  diferencia de forget() por tipo/técnica, no hay nada que reparar índice a índice
     *  porque no queda nada en pie. */
    public void clearAll() {
        unlockedTypes.clear();
        unlockedPhysical.clear();
        slots.clear();
        Arrays.fill(bindings, -1);
    }

    public List<KiTechnique> slots()             { return Collections.unmodifiableList(slots); }
    public KiTechnique slot(int i)               { return (i >= 0 && i < slots.size()) ? slots.get(i) : null; }
    public int slotCount()                       { return slots.size(); }

    /** Igual que {@link #slotCount()} pero SIN contar instancias de técnica firma (Spirit
     *  Bomb, etc.). El límite de `ServerConfig.techniqueMaxSlots()` (12) es el hueco de
     *  técnicas de ki que el JUGADOR fabrica en el editor — una técnica de maestro la enseña
     *  el maestro entero, no ocupa ese hueco, así que no debe contar ni para el contador
     *  "X/12" ni para el gate de "sin sitio para una más". Usar esta función, no slotCount(),
     *  en cualquier sitio que compare contra techniqueMaxSlots(). */
    public int customSlotCount() {
        int n = 0;
        for (KiTechnique t : slots) if (t.type().master().isEmpty()) n++;
        return n;
    }

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

    /**
     * Intercambia dos instancias de la lista Y ARRASTRA sus bindings con ellas.
     * Mover una técnica dentro del inventario no debe cambiar a qué tecla responde: si el
     * jugador tenía la Laser en la tecla 1 y la sube una fila, sigue en la tecla 1. Por eso no
     * basta con Collections.swap — hay que reescribir los bindings que apuntaban a cada índice.
     * Solo se usa con vecinos (las flechas de la pantalla de técnicas), pero vale para
     * cualquier par válido.
     * (Los bindings físicos son <= PHYS_BIND_BASE: ninguna rama los toca, igual que en
     * removeSlot.)
     */
    public boolean swapSlots(int a, int b) {
        if (a < 0 || b < 0 || a >= slots.size() || b >= slots.size() || a == b) return false;
        Collections.swap(slots, a, b);
        for (int p = 0; p < BIND_POSITIONS; p++) {
            if (bindings[p] == a) bindings[p] = b;
            else if (bindings[p] == b) bindings[p] = a;
        }
        return true;
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