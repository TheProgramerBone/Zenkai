package com.hmc.db_renewed.worldgen;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class ModBiomes {

        public static final ResourceKey<Biome> ROCKY_WASTELAND = register("rocky_wasteland");

        private static ResourceKey<Biome> register(String name)
        {
            return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, name));
        }
}
