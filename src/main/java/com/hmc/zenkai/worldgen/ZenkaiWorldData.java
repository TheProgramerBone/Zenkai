package com.hmc.zenkai.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Recuerda qué estructuras ÚNICAS (Kami, otherworld) ya se colocaron, para no
 * recrearlas. Se guarda en el almacenamiento del overworld (global del mundo).
 */
public class ZenkaiWorldData extends SavedData {

    private static final String ID = "zenkai_world";
    private final Set<String> placed = new HashSet<>();

    // ⚠ 1.21.1: Factory(Supplier<T> ctor, BiFunction<CompoundTag,HolderLookup.Provider,T> loader, @Nullable DataFixTypes)
    public static final SavedData.Factory<ZenkaiWorldData> FACTORY =
            new SavedData.Factory<>(ZenkaiWorldData::new, ZenkaiWorldData::load, null);

    public static ZenkaiWorldData get(MinecraftServer server) {
        // El store del overworld sirve como almacenamiento global del mundo.
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public boolean isPlaced(String key) { return placed.contains(key); }

    public void markPlaced(String key) {
        if (placed.add(key)) setDirty();
    }

    public static ZenkaiWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        ZenkaiWorldData d = new ZenkaiWorldData();
        ListTag list = tag.getList("placed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.placed.add(list.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String s : placed) list.add(StringTag.valueOf(s));
        tag.put("placed", list);
        return tag;
    }
}