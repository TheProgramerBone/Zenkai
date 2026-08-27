package com.hmc.zenkai.registry;


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
        //  · erosion        : EROSION_5 — NO EROSION_0..2 (así estaba antes). Comprobado contra
        //                     el propio OverworldBiomeBuilder de vainilla (fuente descompilada):
        //                     erosions[0]/[1] son la banda de las CUMBRES (jagged_peaks/frozen_peaks
        //                     — terracería extrema en escalones, exactamente lo que se veía en las
        //                     capturas de juego) y no es donde vainilla coloca su bioma "rocoso
        //                     genérico" — WINDSWEPT_HILLS/WINDSWEPT_GRAVELLY_HILLS (pickShatteredBiome)
        //                     vive en erosions[5] = span(0.45, 0.55), una banda estrecha y bastante
        //                     erosionada (mucho más cerca del extremo "río", erosions[6], que del
        //                     extremo "picos", erosions[0]). Ese desajuste — usar la banda de picos
        //                     para un bioma pensado como "terreno rocoso ondulado", no cordillera —
        //                     era la causa real de la terracería en escalón Y de que el borde del
        //                     bioma cayera tan abrupto y cerca del agua (con relieve extremo la
        //                     transición a tierras bajas ocurre en muy poca distancia horizontal).
        //                     EROSION_5 iguala la banda real de vainilla para este tipo de bioma:
        //                     ondulado, más bajo, sin la terracería en escalón.
        //  · weirdness      : las 10 bandas (MID_SLICE+HIGH_SLICE+PEAK) SÍ están bien — verificado
        //                     en la misma fuente que pickShatteredBiome() en erosions[5] aparece en
        //                     addMidSlice/addHighSlice/addPeaks por igual; la terracería no venía de
        //                     aquí, venía solo de erosion. Ensanchar esto es la palanca si hace falta
        //                     más área, no tocar erosion otra vez.
        //  · depth          : SOLO SURFACE. Depth es el eje que vainilla/TerraBlender usan para
        //                     distinguir biomas de SUPERFICIE de biomas de CUEVA (3D biome, tipo
        //                     dripstone/lush caves) en la MISMA columna — no tiene nada que ver
        //                     con continentalness/altura real del terreno. Con Depth.FLOOR incluido
        //                     (como estaba antes), rocky_wasteland también se asignaba a huecos
        //                     subterráneos, incluidas cuevas/acuíferos por debajo del nivel del
        //                     mar — eso es lo que se veía en juego como "rocky_wasteland bajo el
        //                     agua": no era el bioma de superficie inundado, era una asignación de
        //                     bioma bajo tierra que nunca debió pasar por aquí. Un bioma de
        //                     superficie normal como este solo debe span-ear Depth.SURFACE.
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.COOL, Temperature.HOT))
                .humidity(Humidity.span(Humidity.ARID, Humidity.NEUTRAL))
                .continentalness(Continentalness.span(Continentalness.MID_INLAND, Continentalness.FAR_INLAND))
                .erosion(Erosion.EROSION_5)
                .depth(Depth.SURFACE)
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