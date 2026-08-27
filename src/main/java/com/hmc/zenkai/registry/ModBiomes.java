package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class ModBiomes {

    public static final ResourceKey<Biome> ROCKY_WASTELAND = register("rocky_wasteland");
    public static final ResourceKey<Biome> OTHERWORLD = register("otherworld");
    public static final ResourceKey<Biome> HFIL_BLOOD_SHORE = register("hfil_blood_shore");
    public static final ResourceKey<Biome> HFIL_NEEDLE_WASTES = register("hfil_needle_wastes");
    public static final ResourceKey<Biome> HFIL_CINDER_DUNES = register("hfil_cinder_dunes");
    public static final ResourceKey<Biome> NAMEK_PLAINS = register("namek_plains");
    public static final ResourceKey<Biome> NAMEK_HILLS  = register("namek_hills");
    public static final ResourceKey<Biome> NAMEK_OCEAN  = register("namek_ocean");
    public static final ResourceKey<Biome> NAMEK_SHORE  = register("namek_shore");
    public static final ResourceKey<Biome> NAMEK_FOREST = register("namek_forest");

        private static ResourceKey<Biome> register(String name)
        {
            return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
        }
}
