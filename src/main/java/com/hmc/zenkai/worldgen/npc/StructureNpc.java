package com.hmc.zenkai.worldgen.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * Descriptor de un NPC ligado a una estructura (Yemma, Kaiosama, Bills, Whis,
 * Goku, Kamisama...). El sistema lo mantiene siempre presente en su posición.
 *
 * @param dimension dimensión donde vive (Level.OVERWORLD, ModDimensions.OTHERWORLD_LEVEL, ...)
 * @param pos       posición fija
 * @param yaw       hacia dónde mira
 * @param type      proveedor del EntityType (p. ej. () -> ModEntities.YEMMA.get())
 * @param radius    radio de búsqueda para saber si ya existe (evita duplicados)
 */
public record StructureNpc(
        ResourceKey<Level> dimension,
        BlockPos pos,
        float yaw,
        Supplier<EntityType<?>> type,
        double radius
) {
    /** Radio por defecto (8 bloques). */
    public StructureNpc(ResourceKey<Level> dimension, BlockPos pos, float yaw, Supplier<EntityType<?>> type) {
        this(dimension, pos, yaw, type, 8.0);
    }
}