package com.hmc.zenkai.feature.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Submódulo de PlayerStatsAttachment: los NODOS del skill tree de transformaciones que el
 * jugador tiene (id del nodo -> nivel; las formas valen 1, los nodos con niveles como Poder
 * Arcosiano o las doctrinas guardan su nivel). Diseño: {@code .claude/pendiente/skill-tree-transformaciones.md}.
 *
 * Dos conjuntos, y la diferencia importa:
 *  - DERIVADOS: espejo de las skills heredadas (super_forms, ultra_instinct, ultra_ego,
 *    potential_unlock, arcosian_power). Solo los escribe SkillTreeMigration.refresh() desde un
 *    único punto (PlayerSkills.onChange) y se vuelven a calcular enteros cada vez.
 *  - PROPIOS (desde F4/F5): nodos sin skill detrás que los represente —pasivas de raza, God Ki,
 *    formas divinas, y cualquier nodo regalado por una vía especial (ritual, evento)—. Son
 *    AUTORIDAD: nadie los deriva, así que refresh() no los toca. Los escribe SkillTree.grant().
 * {@link #level} devuelve el mayor de los dos, así que quien lee no necesita saber de dónde sale.
 *
 * Se guarda con versión: si lo guardado es más viejo se migra al cargar (ver
 * PlayerStatsAttachment.load). Viaja dentro del save/load del attachment: persiste, se copia
 * al morir y se sincroniza al cliente por el mismo canal que las skills.
 */
public final class PlayerSkillTree {

    /**
     * Versión actual. 1: espejo de F1. 2: nodos propios; god_ki pasa a nivel máximo 1 y las
     * formas divinas a nodos propios (SkillTreeMigration.migrateToV2).
     */
    public static final int VERSION = 3;

    private final Map<ResourceLocation, Integer> derived = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> owned = new LinkedHashMap<>();
    private int version = 0;

    public int level(ResourceLocation id) {
        return Math.max(derived.getOrDefault(id, 0), owned.getOrDefault(id, 0));
    }

    public boolean owns(ResourceLocation id) { return level(id) > 0; }

    /** Nivel guardado como nodo PROPIO (0 si solo es derivado o no existe). */
    public int ownedLevel(ResourceLocation id) { return owned.getOrDefault(id, 0); }

    /** Unión de los dos conjuntos (para depurar y para el sync de pantallas). */
    public Map<ResourceLocation, Integer> all() {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>(derived);
        owned.forEach((k, v) -> out.merge(k, v, Math::max));
        return Collections.unmodifiableMap(out);
    }

    public Map<ResourceLocation, Integer> ownedNodes() { return Collections.unmodifiableMap(owned); }

    public int version() { return version; }

    public void setVersion(int v) { this.version = v; }

    /** Sustituye los nodos DERIVADOS (los propios no se tocan). true si cambió algo. */
    public boolean replaceWith(Map<ResourceLocation, Integer> fresh) {
        boolean changed = !derived.equals(fresh);
        derived.clear();
        derived.putAll(fresh);
        return changed;
    }

    /** Fija el nivel de un nodo PROPIO (0 lo quita). Solo lo llama SkillTree. */
    public void setOwned(ResourceLocation id, int level) {
        if (level <= 0) owned.remove(id);
        else owned.put(id, level);
    }

    /** Vacía los dos conjuntos (reset full, reinicio del árbol). */
    public void clear() {
        derived.clear();
        owned.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        // CompoundTag (id -> nivel): aquí el orden no importa, a diferencia de PlayerSkills.
        tag.put("nodes", write(derived));
        tag.put("owned", write(owned));
        return tag;
    }

    public void load(CompoundTag tag) {
        version = tag.getInt("version");
        read(tag.getCompound("nodes"), derived);
        read(tag.getCompound("owned"), owned);
    }

    private static CompoundTag write(Map<ResourceLocation, Integer> m) {
        CompoundTag n = new CompoundTag();
        m.forEach((id, lvl) -> n.putInt(id.toString(), lvl));
        return n;
    }

    private static void read(CompoundTag n, Map<ResourceLocation, Integer> into) {
        into.clear();
        for (String k : n.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(k);
            int lvl = n.getInt(k);
            if (id != null && lvl > 0) into.put(id, lvl);
        }
    }
}
