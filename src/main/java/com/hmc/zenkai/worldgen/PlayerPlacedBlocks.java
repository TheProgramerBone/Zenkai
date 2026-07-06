package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registra las posiciones de los bloques que los jugadores colocan dentro de zonas protegidas.
 * En esas zonas SOLO se pueden romper los bloques aquí registrados; la estructura original
 * (colocada por código, no por un evento de jugador) no está registrada → queda protegida.
 * Persistente por mundo (se guarda en el store del overworld, indexado por dimensión).
 */
public class PlayerPlacedBlocks extends SavedData {

    private static final String ID = "zenkai_player_placed";
    private final Map<ResourceLocation, Set<Long>> byDim = new HashMap<>();

    public static final SavedData.Factory<PlayerPlacedBlocks> FACTORY =
            new SavedData.Factory<>(PlayerPlacedBlocks::new, PlayerPlacedBlocks::load, null);

    public static PlayerPlacedBlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        Set<Long> s = byDim.get(dim.location());
        return s != null && s.contains(pos.asLong());
    }

    public void add(ResourceKey<Level> dim, BlockPos pos) {
        if (byDim.computeIfAbsent(dim.location(), k -> new HashSet<>()).add(pos.asLong())) setDirty();
    }

    public void remove(ResourceKey<Level> dim, BlockPos pos) {
        Set<Long> s = byDim.get(dim.location());
        if (s != null && s.remove(pos.asLong())) setDirty();
    }

    public static PlayerPlacedBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerPlacedBlocks d = new PlayerPlacedBlocks();
        for (String key : tag.getAllKeys()) {
            Set<Long> s = new HashSet<>();
            for (long v : tag.getLongArray(key)) s.add(v);
            d.byDim.put(ResourceLocation.parse(key), s);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        byDim.forEach((dim, set) -> {
            long[] arr = new long[set.size()];
            int i = 0;
            for (long v : set) arr[i++] = v;
            tag.put(dim.toString(), new LongArrayTag(arr));
        });
        return tag;
    }
}