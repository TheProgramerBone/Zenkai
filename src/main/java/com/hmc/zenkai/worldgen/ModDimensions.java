package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
    public static final ResourceKey<Level> NAMEK_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "namek")
    );

    public static final ResourceKey<DimensionType> NAMEK_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "namek")
    );

    public static final ResourceKey<Level> OTHERWORLD_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "otherworld")
    );

    public static final ResourceKey<DimensionType> OTHERWORLD_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "otherworld")
    );

    public static final ResourceKey<Level> HTC_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "htc")
    );

    public static final ResourceKey<DimensionType> HTC_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "htc")
    );
}