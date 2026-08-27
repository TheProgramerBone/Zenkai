package com.hmc.zenkai.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;

public class ModSurfaceRules {
    public static SurfaceRules.RuleSource makeRules() {
        Block rockyBlock = ModBlocks.ROCKY_BLOCK.get();
        SurfaceRules.RuleSource rockySurface = SurfaceRules.state(rockyBlock.defaultBlockState());

        // abovePreliminarySurface() acota la regla a la superficie REAL del terreno.
        // Sin él, ON_FLOOR/UNDER_FLOOR también aciertan en el suelo de cada cueva y el
        // bloque rocoso tapiza la columna entera hasta la bedrock.
        //
        // A propósito SIN corte por nivel del mar: se probó un yBlockCheck(63, 0) para que
        // ROCKY_BLOCK no pintara por debajo del nivel del mar, pero el usuario pidió
        // explícitamente lo contrario — que el bloque rocoso SIGA hasta tocar el agua, como
        // hacía en versiones bastante anteriores a los cambios de generación de esta sesión,
        // porque cortarlo antes de la orilla hace que el bioma se vea "sobrepuesto" (un límite
        // artificial en vez de un acantilado/orilla natural). Con erosion ajustado a EROSION_5
        // (ver ModOverworldRegion) el bioma ya no debería llegar al agua por transiciones tan
        // abruptas como antes, así que este corte ya no hacía falta para evitar el problema
        // original de "rocky_wasteland bajo el agua" (eso lo arregla depth(SURFACE) + erosion).
        return SurfaceRules.ifTrue(
                SurfaceRules.abovePreliminarySurface(),                      // ⚠ API
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(ModBiomes.ROCKY_WASTELAND),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, rockySurface),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, rockySurface)
                        )
                )
        );
    }

    public static final SurfaceRules.RuleSource NAMEK_SURFACE_BUILDER = SurfaceRules.sequence(
            SurfaceRules.ifTrue(
                    SurfaceRules.ON_FLOOR,
                    SurfaceRules.state(ModBlocks.NAMEKIAN_GRASS_BLOCK.get().defaultBlockState())
            ),
            SurfaceRules.ifTrue(
                    SurfaceRules.UNDER_FLOOR,
                    SurfaceRules.state(ModBlocks.NAMEKIAN_DIRT.get().defaultBlockState())
            ),
            SurfaceRules.state(ModBlocks.NAMEKIAN_STONE.get().defaultBlockState())
    );
}