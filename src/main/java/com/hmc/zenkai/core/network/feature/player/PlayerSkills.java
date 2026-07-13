package com.hmc.zenkai.core.network.feature.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Submódulo de PlayerStatsAttachment: habilidades desbloqueadas (ids de SkillDef).
 *  - bought: compradas con TP (el respec las limpia y devuelve su tpCost).
 *  - granted: otorgadas por maestros/NPC/comando, sin TP (sobreviven al respec;
 *    solo las borra el reset full o /zenkai skill revoke).
 * Viaja dentro del save/load del attachment -> persiste, se copia al morir y se
 * sincroniza al cliente por el mismo canal que el resto de stats.
 */
public final class PlayerSkills {

    private final Set<String> bought  = new LinkedHashSet<>();
    private final Set<String> granted = new LinkedHashSet<>();

    public boolean has(String id) { return bought.contains(id) || granted.contains(id); }

    public void unlock(String id, boolean isBought) {
        if (has(id)) return;
        (isBought ? bought : granted).add(id);
    }

    /** Compat: la vía de compra con TP (SkillBuyPacket). */
    public void unlock(String id) { unlock(id, true); }

    /** Quita la habilidad venga de donde venga. true si la tenía. */
    public boolean revoke(String id) { return bought.remove(id) | granted.remove(id); }

    /** Todas (compradas + otorgadas), en orden de desbloqueo. Para la GUI. */
    public Set<String> all() {
        Set<String> out = new LinkedHashSet<>(bought);
        out.addAll(granted);
        return Collections.unmodifiableSet(out);
    }

    public Set<String> boughtAll() { return Collections.unmodifiableSet(bought); }

    /** Respec: solo las compradas. */
    public void clearBought() { bought.clear(); }

    /** Reset full: todas. */
    public void clear() { bought.clear(); granted.clear(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("bought",  toList(bought));
        tag.put("granted", toList(granted));
        return tag;
    }

    public void load(CompoundTag tag) {
        bought.clear();
        granted.clear();
        readInto(tag, "bought",  bought);
        readInto(tag, "granted", granted);
        // Migración del formato viejo: "unlocked" -> compradas (se pagaron con TP).
        readInto(tag, "unlocked", bought);
    }

    private static ListTag toList(Set<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) list.add(StringTag.valueOf(id));
        return list;
    }

    private static void readInto(CompoundTag tag, String key, Set<String> out) {
        if (!tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
    }
}