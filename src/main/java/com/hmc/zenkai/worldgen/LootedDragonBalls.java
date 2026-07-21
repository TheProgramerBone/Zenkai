package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Posiciones de esferas del dragón ya recogidas por un jugador. El radar las usa para no
 * seguir apuntando a una estructura que ya está vacía. Se guarda en el store de CADA nivel,
 * así que Namek tendrá su propio registro sin necesidad de claves por dimensión.
 */
public class LootedDragonBalls extends SavedData {

    private static final String ID = "zenkai_looted_dragon_balls";
    private final Set<Long> looted = new HashSet<>();
    private final Map<Long, List<Long>> byChunk = new HashMap<>();

    private static long chunkKey(int cx, int cz) { return ChunkPos.asLong(cx, cz); }

    public void markLooted(BlockPos pos) {
        if (!looted.add(pos.asLong())) return;
        byChunk.computeIfAbsent(chunkKey(pos.getX() >> 4, pos.getZ() >> 4), k -> new ArrayList<>())
                .add(pos.asLong());
        setDirty();
    }

    public static final SavedData.Factory<LootedDragonBalls> FACTORY =
            new SavedData.Factory<>(LootedDragonBalls::new, LootedDragonBalls::load, null);

    public static LootedDragonBalls get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** ¿Hay alguna esfera ya recogida a menos de {@code radius} bloques (XZ)? Indexado por chunk
     *  para no recorrer el registro: se llama una vez por estructura candidata. */
    public boolean isLootedNear(BlockPos pos, int radius) {
        long r2 = (long) radius * radius;
        int cr = (radius >> 4) + 1;
        int pcx = pos.getX() >> 4, pcz = pos.getZ() >> 4;
        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                List<Long> list = byChunk.get(chunkKey(cx, cz));
                if (list == null) continue;
                for (long l : list) {
                    BlockPos p = BlockPos.of(l);
                    long dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
                    if (dx * dx + dz * dz <= r2) return true;
                }
            }
        }
        return false;
    }

    public static LootedDragonBalls load(CompoundTag tag, HolderLookup.Provider registries) {
        LootedDragonBalls d = new LootedDragonBalls();
        for (long l : tag.getLongArray("looted")) {
            d.looted.add(l);
            BlockPos p = BlockPos.of(l);
            d.byChunk.computeIfAbsent(chunkKey(p.getX() >> 4, p.getZ() >> 4), k -> new ArrayList<>())
                    .add(l);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray("looted", looted.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}