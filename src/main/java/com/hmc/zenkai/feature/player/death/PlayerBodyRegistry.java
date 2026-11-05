package com.hmc.zenkai.feature.player.death;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Índice de los cuerpos que existen en el mundo, por dueño. SavedData en el overworld (mismo
 * patrón que {@code PartyManager}).
 *
 * Existe porque la entidad vive en su chunk y el servidor solo ve las cargadas: para aplicar el tope
 * por jugador, o para llevar a alguien junto a su cuerpo más reciente (visita de Uranai Baba),
 * hay que saber DÓNDE está cada cuerpo sin tener el chunk cargado. La entidad sigue siendo la fuente
 * de verdad del inventario; aquí solo se guarda quién, dónde y cuándo.
 */
public class PlayerBodyRegistry extends SavedData {

    private static final String ID = "zenkai_player_bodies";

    public record Entry(UUID bodyId, UUID owner, ResourceKey<Level> dimension, BlockPos pos, long createdAt) {}

    private final List<Entry> entries = new ArrayList<>();
    /** Cuerpos ya sacados del índice cuyo chunk estaba descargado: se eliminan solos al volver a cargar. */
    private final Map<UUID, Boolean> condemned = new HashMap<>();   // id -> soltar objetos al eliminarlo

    public static final SavedData.Factory<PlayerBodyRegistry> FACTORY =
            new SavedData.Factory<>(PlayerBodyRegistry::new, PlayerBodyRegistry::load, null);

    public static PlayerBodyRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public void put(Entry e) {
        entries.removeIf(o -> o.bodyId.equals(e.bodyId));
        entries.add(e);
        setDirty();
    }

    /** Actualiza la posición (el cuerpo cae, lo recolocan...) sin tocar el resto. */
    public void move(UUID bodyId, ResourceKey<Level> dim, BlockPos pos) {
        for (int i = 0; i < entries.size(); i++) {
            Entry o = entries.get(i);
            if (o.bodyId.equals(bodyId)) {
                if (o.dimension.equals(dim) && o.pos.equals(pos)) return;
                entries.set(i, new Entry(o.bodyId, o.owner, dim, pos, o.createdAt));
                setDirty();
                return;
            }
        }
    }

    public void remove(UUID bodyId) {
        if (entries.removeIf(o -> o.bodyId.equals(bodyId))) setDirty();
    }

    /** Marca un cuerpo para eliminarlo en cuanto exista de nuevo (sale ya del índice). {@code spill}: soltar sus objetos. */
    public void condemn(UUID bodyId, boolean spill) {
        remove(bodyId);
        condemned.put(bodyId, spill);
        setDirty();
    }

    /** null si no estaba condenado; si lo estaba, lo olvida y devuelve si hay que soltar sus objetos. */
    @Nullable
    public Boolean takeCondemned(UUID bodyId) {
        if (condemned.isEmpty()) return null;
        Boolean spill = condemned.remove(bodyId);
        if (spill != null) setDirty();
        return spill;
    }

    public List<Entry> all() { return new ArrayList<>(entries); }

    /** Cuerpos de un dueño, del más antiguo al más reciente. */
    public List<Entry> of(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.owner.equals(owner)) out.add(e);
        out.sort(Comparator.comparingLong(Entry::createdAt));
        return out;
    }

    @Nullable
    public Entry newest(UUID owner) {
        List<Entry> l = of(owner);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.put("body", NbtUtils.createUUID(e.bodyId));
            t.put("owner", NbtUtils.createUUID(e.owner));
            t.putString("dim", e.dimension.location().toString());
            t.putLong("pos", e.pos.asLong());
            t.putLong("at", e.createdAt);
            list.add(t);
        }
        tag.put("bodies", list);
        ListTag dead = new ListTag();
        for (var en : condemned.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("id", NbtUtils.createUUID(en.getKey()));
            t.putBoolean("spill", en.getValue());
            dead.add(t);
        }
        tag.put("condemned", dead);
        return tag;
    }

    public static PlayerBodyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerBodyRegistry r = new PlayerBodyRegistry();
        for (Tag raw : tag.getList("bodies", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null) continue;
            r.entries.add(new Entry(NbtUtils.loadUUID(t.get("body")), NbtUtils.loadUUID(t.get("owner")),
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
                    BlockPos.of(t.getLong("pos")), t.getLong("at")));
        }
        for (Tag raw : tag.getList("condemned", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            r.condemned.put(NbtUtils.loadUUID(t.get("id")), t.getBoolean("spill"));
        }
        return r;
    }
}
