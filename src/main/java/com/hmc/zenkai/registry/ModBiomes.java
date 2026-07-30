package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class ModBiomes {

    public static final ResourceKey<Biome> ROCKY_WASTELAND = register("rocky_wasteland");
    public static final ResourceKey<Biome> OTHERWORLD = register("otherworld");
    public static final ResourceKey<Biome> HFIL_BADLANDS = register("hfil_badlands");
    public static final ResourceKey<Biome> HFIL_WASTES   = register("hfil_wastes");
    public static final ResourceKey<Biome> HFIL_DUNES    = register("hfil_dunes");

        private static ResourceKey<Biome> register(String name)
        {
            return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
        }
}
