package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Recuerda qué estructuras únicas ya se colocaron y DÓNDE (para que la zona de
 * protección use siempre el mismo sitio). Se guarda en el store del overworld.
 */
public class ZenkaiWorldData extends SavedData {

    private static final String ID = "zenkai_world";
    private final Set<String> placed = new HashSet<>();
    private final Map<String, BlockPos> positions = new HashMap<>();

    public static final SavedData.Factory<ZenkaiWorldData> FACTORY =
            new SavedData.Factory<>(ZenkaiWorldData::new, ZenkaiWorldData::load, null);

    public static ZenkaiWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public boolean isPlaced(String key) { return placed.contains(key); }
    public void markPlaced(String key) { if (placed.add(key)) setDirty(); }

    public BlockPos getPos(String key) { return positions.get(key); }
    public void setPos(String key, BlockPos pos) { positions.put(key, pos); setDirty(); }

    public static ZenkaiWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        ZenkaiWorldData d = new ZenkaiWorldData();
        ListTag list = tag.getList("placed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.placed.add(list.getString(i));
        if (tag.contains("positions")) {
            CompoundTag pos = tag.getCompound("positions");
            for (String k : pos.getAllKeys()) d.positions.put(k, BlockPos.of(pos.getLong(k)));
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (String s : placed) list.add(StringTag.valueOf(s));
        tag.put("placed", list);
        CompoundTag pos = new CompoundTag();
        positions.forEach((k, v) -> pos.putLong(k, v.asLong()));
        tag.put("positions", pos);
        return tag;
    }
}