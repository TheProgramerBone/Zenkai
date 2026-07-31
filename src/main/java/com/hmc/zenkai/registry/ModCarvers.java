package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarverDebugSettings;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * Carvers propios de Namek: los de vainilla con la probabilidad recortada.
 * cave 0.15 -> 0.08 y canyon 0.02 -> 0.01. Además NO se usa cave_extra_underground, que es
 * el que llena de túneles la franja profunda. Resultado: montaña maciza con cuevas
 * ocasionales, en vez de queso gruyere.
 * Tienen que vivir en el RegistrySetBuilder (no como JSON suelto) porque ModBiomeGen los
 * resuelve con getOrThrow y ese lookup solo ve vainilla y lo que esté en el builder.
 */
public final class ModCarvers {
    private ModCarvers() {}

    public static final ResourceKey<ConfiguredWorldCarver<?>> NAMEK_CAVE   = key("namek_cave");
    public static final ResourceKey<ConfiguredWorldCarver<?>> NAMEK_CANYON = key("namek_canyon");

    public static void bootstrap(BootstrapContext<ConfiguredWorldCarver<?>> ctx) {
        HolderGetter<Block> blocks = ctx.lookup(Registries.BLOCK);

        ctx.register(NAMEK_CAVE, WorldCarver.CAVE.configured(new CaveCarverConfiguration(
                0.10F,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1F, 0.9F),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                blocks.getOrThrow(BlockTags.OVERWORLD_CARVER_REPLACEABLES),
                UniformFloat.of(0.7F, 1.4F),
                UniformFloat.of(0.8F, 1.3F),
                UniformFloat.of(-1.0F, -0.4F))));

        ctx.register(NAMEK_CANYON, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.01F,
                UniformHeight.of(VerticalAnchor.absolute(10), VerticalAnchor.absolute(67)),
                ConstantFloat.of(3.0F),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                blocks.getOrThrow(BlockTags.OVERWORLD_CARVER_REPLACEABLES),
                UniformFloat.of(-0.125F, 0.125F),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.75F, 1.0F),
                        UniformFloat.of(0.0F, 6.0F),
                        3,
                        UniformFloat.of(0.75F, 1.0F),
                        1.0F,
                        0.0F))));
    }

    private static ResourceKey<ConfiguredWorldCarver<?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_CARVER,
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }
}