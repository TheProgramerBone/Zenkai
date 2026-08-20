package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.feature.technique.TechniqueDef.Kind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tercera capa de números de técnica, encima de fábrica y datapack: los cambios hechos en
 * caliente con {@code /zenkai tech ... set}. Se guarda en el store del overworld, igual que
 * KorinSenzuData, así que sobrevive a reinicios, a {@code /reload} y a un crash.
 *
 * SOLO GUARDA LO QUE SE HA TOCADO. Un override es un par (campo, valor), no un def completo:
 * si mañana cambio el JSON de fábrica de una técnica cuyo {@code damage_mult} está overrideado,
 * el resto de sus campos sigue el JSON nuevo. Guardar defs enteros habría congelado el
 * datapack entero en el primer {@code set}.
 *
 * PUEDE HABER OVERRIDES DE TÉCNICAS SIN JSON: es justo lo que activa un tipo desactivado
 * (fábrica + override). La composición final la hace {@link TechniqueManager#rebuildAndSync}.
 *
 * Los valores llegan YA VALIDADOS por {@link TechniqueField#clamp}; aquí no se reinterpreta
 * nada, porque el clamp es del campo y no del almacén.
 */
public final class TechniqueOverrides extends SavedData {

    private static final String ID = "zenkai_technique_overrides";
    private static final String TAG_ROOT = "overrides";

    /** Clave "KI/wave": misma convención que TechniqueDef.key, para poder cruzarlas. */
    private final Map<String, EnumMap<TechniqueField, Object>> map = new LinkedHashMap<>();

    public static final SavedData.Factory<TechniqueOverrides> FACTORY =
            new SavedData.Factory<>(TechniqueOverrides::new, TechniqueOverrides::load, null);

    public static TechniqueOverrides get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static String key(Kind kind, String id) { return kind.name() + "/" + id; }

    // ── Lectura ──────────────────────────────────────────────────────────────

    /** Todos los overrides, por clave "KIND/id". */
    public Map<String, EnumMap<TechniqueField, Object>> all() {
        return Collections.unmodifiableMap(map);
    }

    /** Nunca null: un mapa vacío significa "esta técnica no está tocada". */
    public Map<TechniqueField, Object> values(Kind kind, String id) {
        EnumMap<TechniqueField, Object> m = map.get(key(kind, id));
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    public boolean isEmpty() { return map.isEmpty(); }

    // ── Escritura ────────────────────────────────────────────────────────────

    /** Devuelve el valor REALMENTE guardado (tras clamp), que puede no ser el pedido. */
    public Object set(Kind kind, String id, TechniqueField field, Object raw) {
        Object v = field.clamp(raw);
        map.computeIfAbsent(key(kind, id), k -> new EnumMap<>(TechniqueField.class)).put(field, v);
        setDirty();
        return v;
    }

    /** false = no había override de ese campo. */
    public boolean clear(Kind kind, String id, TechniqueField field) {
        String k = key(kind, id);
        EnumMap<TechniqueField, Object> m = map.get(k);
        if (m == null || m.remove(field) == null) return false;
        if (m.isEmpty()) map.remove(k);
        setDirty();
        return true;
    }

    /** Cuántos overrides se han borrado. */
    public int clearAll(Kind kind, String id) {
        EnumMap<TechniqueField, Object> m = map.remove(key(kind, id));
        if (m == null || m.isEmpty()) return 0;
        setDirty();
        return m.size();
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    public static TechniqueOverrides load(CompoundTag tag, HolderLookup.Provider registries) {
        TechniqueOverrides o = new TechniqueOverrides();
        CompoundTag root = tag.getCompound(TAG_ROOT);
        for (String k : root.getAllKeys()) {
            CompoundTag entry = root.getCompound(k);
            EnumMap<TechniqueField, Object> m = new EnumMap<>(TechniqueField.class);
            for (TechniqueField f : TechniqueField.values()) {
                Object v = f.readNbt(entry);
                if (v != null) m.put(f, v);
            }
            // Un campo retirado del enum desaparece solo al leer, y una entrada que se queda
            // sin campos no se guarda: el archivo se limpia con el tiempo sin migración.
            if (!m.isEmpty()) o.map.put(k, m);
        }
        return o;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        CompoundTag root = new CompoundTag();
        map.forEach((k, m) -> {
            CompoundTag entry = new CompoundTag();
            m.forEach((f, v) -> f.writeNbt(entry, v));
            root.put(k, entry);
        });
        tag.put(TAG_ROOT, root);
        return tag;
    }
}
