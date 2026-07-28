package com.hmc.zenkai.feature.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Submódulo de PlayerStatsAttachment: habilidades y su NIVEL actual.
 *  - levels: nivel actual de cada habilidad (ausente = 0 = no la tiene).
 *  - grantedFloor: nivel mínimo otorgado por maestros/NPC/comando. El respec devuelve el TP
 *    de los niveles comprados por encima de ese suelo y baja hasta él; solo el reset full o
 *    /zenkai skill revoke lo borran.
 * Viaja dentro del save/load del attachment -> persiste, se copia al morir y se sincroniza
 * al cliente por el mismo canal que el resto de stats.
 */
public final class PlayerSkills {

    private final Map<String, Integer> levels       = new LinkedHashMap<>();
    private final Map<String, Integer> grantedFloor = new LinkedHashMap<>();
    private final Set<String> toggles = new LinkedHashSet<>();

    /** Nivel actual (0 = no la tiene). */
    public int level(String id) { return levels.getOrDefault(id, 0); }

    public boolean has(String id) { return level(id) > 0; }

    /** Niveles pagados con TP (los que devuelve el respec). */
    public int boughtLevels(String id) {
        return Math.max(0, level(id) - grantedFloor.getOrDefault(id, 0));
    }

    /** Fija el nivel exacto (comando/admin). 0 o menos la elimina. */
    public void setLevel(String id, int lvl) {
        if (lvl <= 0) { levels.remove(id); return; }
        levels.put(id, lvl);
    }

    /** El maestro enseña: sube el suelo otorgado y el nivel si hace falta. */
    public void grant(String id, int lvl) {
        if (lvl <= 0) return;
        grantedFloor.merge(id, lvl, Math::max);
        levels.merge(id, lvl, Math::max);
    }

    /** Sube un nivel con TP, respetando el máximo. true si subió. */
    public boolean raise(String id, int maxLevel) {
        int cur = level(id);
        if (cur >= maxLevel) return false;
        levels.put(id, cur + 1);
        return true;
    }

    /** Quita la habilidad venga de donde venga. true si la tenía. */
    public boolean revoke(String id) {
        grantedFloor.remove(id);
        toggles.remove(id);
        return levels.remove(id) != null;
    }

    /** Todas las que tiene, en orden de desbloqueo. Para la GUI. */
    public Set<String> all() { return Collections.unmodifiableSet(levels.keySet()); }

    public Map<String, Integer> allLevels() { return Collections.unmodifiableMap(levels); }

    /** Respec: baja cada habilidad a su suelo otorgado (quien llama devuelve el TP). */
    public void clearBought() {
        levels.entrySet().removeIf(e -> {
            int floor = grantedFloor.getOrDefault(e.getKey(), 0);
            if (floor <= 0) return true;
            e.setValue(floor);
            return false;
        });
    }

    /** Reset full: todas. */
    public void clear() { levels.clear(); grantedFloor.clear(); toggles.clear(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag lv = new CompoundTag();
        levels.forEach(lv::putInt);
        tag.put("levels", lv);
        CompoundTag gf = new CompoundTag();
        grantedFloor.forEach(gf::putInt);
        tag.put("grantedFloor", gf);
        ListTag tg = new ListTag();
        for (String id : toggles) tg.add(StringTag.valueOf(id));
        tag.put("toggles", tg);
        return tag;
    }

    public void load(CompoundTag tag) {
        levels.clear();
        grantedFloor.clear();
        if (tag.contains("levels")) {
            CompoundTag lv = tag.getCompound("levels");
            for (String k : lv.getAllKeys()) levels.put(k, lv.getInt(k));
        }
        if (tag.contains("grantedFloor")) {
            CompoundTag gf = tag.getCompound("grantedFloor");
            for (String k : gf.getAllKeys()) grantedFloor.put(k, gf.getInt(k));
        }
        toggles.clear();
        if (tag.contains("toggles")) {
            ListTag tg = tag.getList("toggles", Tag.TAG_STRING);
            for (int i = 0; i < tg.size(); i++) toggles.add(tg.getString(i));
        }
        // Migración del formato binario: lo que tuviera pasa a nivel 1.
        migrateFlat(tag, "bought",   false);
        migrateFlat(tag, "unlocked", false);
        migrateFlat(tag, "granted",  true);
    }

    private void migrateFlat(CompoundTag tag, String key, boolean granted) {
        if (!tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            levels.merge(id, 1, Math::max);
            if (granted) grantedFloor.merge(id, 1, Math::max);
        }
    }

    // ── Interruptores ────────────────────────────────────────────────────────
    // OJO: leer esto directamente NO valida nada. El consumidor correcto es
    // SkillToggles.isOn(player, id), que además comprueba habilidad y prerrequisitos.

    public boolean isToggleOn(String id) { return toggles.contains(id); }

    public void setToggle(String id, boolean on) {
        if (on) toggles.add(id); else toggles.remove(id);
    }

    public void clearToggles() { toggles.clear(); }
}