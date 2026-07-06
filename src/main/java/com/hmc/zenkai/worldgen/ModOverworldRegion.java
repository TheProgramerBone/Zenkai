package com.hmc.zenkai.worldgen;


import com.hmc.zenkai.Zenkai;
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
        // El 'weight' (5) es cuánto "pesa" nuestra región frente a la vanilla: súbelo para que
        // aparezca más seguido en general, bájalo para menos. Es el dial global de presencia.
        super(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "overworld"), RegionType.OVERWORLD, 1);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

        // ── DIALES DE CLIMA (cada eje decide DÓNDE y CUÁNTO sale el bioma) ────────────────
        // Cuanto más ancho el rango de cada eje, MÁS grande/frecuente el bioma (reemplaza más
        // biomas vanilla en ese nicho). Cuanto más estrecho, más raro y pequeño.
        //  · temperature   : franja de temperatura (el bioma se ve cálido igual por temp=2.0 del JSON).
        //  · humidity       : ARID..NEUTRAL = mitad seca (rocoso = seco).
        //  · continentalness: MID_INLAND..FAR_INLAND = tierra adentro (montañas, lejos de costa).
        //  · erosion        : EROSION_0..2 = poca erosión = relieve ALTO y escarpado (picos rocosos).
        //                     Si añades EROSION_3..6 saldría más plano/mesetas.
        //  · weirdness      : MID/HIGH/PEAK = laderas medias, altas y cumbres → cordilleras grandes.
        //                     Quita los MID_* si solo quieres cumbres; añade LOW_* para bajar más.
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.COOL, Temperature.HOT))
                .humidity(Humidity.span(Humidity.ARID, Humidity.NEUTRAL))
                .continentalness(Continentalness.span(Continentalness.MID_INLAND, Continentalness.FAR_INLAND))
                .erosion(Erosion.EROSION_0, Erosion.EROSION_1, Erosion.EROSION_2)
                .depth(Depth.SURFACE, Depth.FLOOR)
                .weirdness(
                        Weirdness.MID_SLICE_NORMAL_ASCENDING,
                        Weirdness.MID_SLICE_NORMAL_DESCENDING,
                        Weirdness.MID_SLICE_VARIANT_ASCENDING,
                        Weirdness.MID_SLICE_VARIANT_DESCENDING,
                        Weirdness.HIGH_SLICE_NORMAL_ASCENDING,
                        Weirdness.HIGH_SLICE_NORMAL_DESCENDING,
                        Weirdness.HIGH_SLICE_VARIANT_ASCENDING,
                        Weirdness.HIGH_SLICE_VARIANT_DESCENDING,
                        Weirdness.PEAK_NORMAL,
                        Weirdness.PEAK_VARIANT
                )
                .build()
                .forEach(point -> builder.add(point, ModBiomes.ROCKY_WASTELAND));

        builder.build().forEach(mapper);
    }
}