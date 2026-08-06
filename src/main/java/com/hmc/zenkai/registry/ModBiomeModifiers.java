package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_KATCHIN_ORE = registerKey("add_katchin_ore");
    public static final ResourceKey<BiomeModifier> ADD_KATCHIN_ORE_ROCKY = registerKey("add_katchin_ore_rocky");

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var biomes = context.lookup(Registries.BIOME);

        context.register(ADD_KATCHIN_ORE, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(placedFeatures.getOrThrow(ModPlacedFeatures.KATCHIN_ORE_OVERWORLD)),
                GenerationStep.Decoration.UNDERGROUND_ORES));

        context.register(ADD_KATCHIN_ORE_ROCKY, new BiomeModifiers.AddFeaturesBiomeModifier(
                HolderSet.direct(biomes.getOrThrow(ModBiomes.ROCKY_WASTELAND)),
                HolderSet.direct(placedFeatures.getOrThrow(ModPlacedFeatures.KATCHIN_ORE_ROCKY)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    }

    private static ResourceKey<BiomeModifier> registerKey(String name) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }
}