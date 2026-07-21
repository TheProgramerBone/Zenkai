package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Posiciones de esferas del dragón ya recogidas por un jugador. El radar las usa para no
 * seguir apuntando a una estructura que ya está vacía. Se guarda en el store de CADA nivel,
 * así que Namek tendrá su propio registro sin necesidad de claves por dimensión.
 */
public class LootedDragonBalls extends SavedData {

    private static final String ID = "zenkai_looted_dragon_balls";
    private final Set<Long> looted = new HashSet<>();

    public static final SavedData.Factory<LootedDragonBalls> FACTORY =
            new SavedData.Factory<>(LootedDragonBalls::new, LootedDragonBalls::load, null);

    public static LootedDragonBalls get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public void markLooted(BlockPos pos) {
        if (looted.add(pos.asLong())) setDirty();
    }

    /** ¿Hay alguna esfera ya recogida a menos de {@code radius} bloques (en XZ) de esta posición? */
    public boolean isLootedNear(BlockPos pos, int radius) {
        long r2 = (long) radius * radius;
        for (long l : looted) {
            BlockPos p = BlockPos.of(l);
            long dx = p.getX() - pos.getX();
            long dz = p.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    public static LootedDragonBalls load(CompoundTag tag, HolderLookup.Provider registries) {
        LootedDragonBalls d = new LootedDragonBalls();
        for (long l : tag.getLongArray("looted")) d.looted.add(l);
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray("looted", looted.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}