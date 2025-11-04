package com.hmc.db_renewed.worldgen;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public class ModDimensions {
    public static final ResourceKey<Level> NAMEK_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "namek")
    );

    public static final ResourceKey<DimensionType> NAMEK_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "namek")
    );

    public static final ResourceKey<Level> OTHERWORLD_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "otherworld")
    );

    public static final ResourceKey<DimensionType> OTHERWORLD_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "otherworld")
    );
}