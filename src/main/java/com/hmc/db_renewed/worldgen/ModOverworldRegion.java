package com.hmc.db_renewed.worldgen;


import com.hmc.db_renewed.DragonBlockRenewed;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;

import static terrablender.api.ParameterUtils.*;
import java.util.function.Consumer;

public class ModOverworldRegion extends Region {

    public ModOverworldRegion() {
        super(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "overworld"), RegionType.OVERWORLD, 5);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

        new ParameterUtils.ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.WARM, Temperature.HOT))
                .humidity(Humidity.ARID)
                .continentalness(Continentalness.FAR_INLAND)
                .erosion(Erosion.EROSION_0, Erosion.EROSION_1)
                .depth(Depth.SURFACE, Depth.FLOOR)
                .weirdness(
                        Weirdness.PEAK_NORMAL,
                        Weirdness.PEAK_VARIANT,
                        Weirdness.HIGH_SLICE_VARIANT_ASCENDING,
                        Weirdness.HIGH_SLICE_VARIANT_DESCENDING
                )
                .build()
                .forEach(point -> builder.add(point, ModBiomes.ROCKY_WASTELAND));

        builder.build().forEach(mapper);
    }
}