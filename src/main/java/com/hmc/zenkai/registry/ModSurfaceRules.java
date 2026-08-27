package com.hmc.zenkai.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

public class ModSurfaceRules {

    /** Altura absoluta mínima a la que ROCKY_BLOCK puede pintar la superficie (ver comentario
     *  más abajo) — deliberadamente por DEBAJO del nivel del mar (63), no en él. */
    private static final int MIN_ROCKY_SURFACE_Y = 57;

    public static SurfaceRules.RuleSource makeRules() {
        Block rockyBlock = ModBlocks.ROCKY_BLOCK.get();
        SurfaceRules.RuleSource rockySurface = SurfaceRules.state(rockyBlock.defaultBlockState());

        // abovePreliminarySurface() acota la regla a la superficie REAL del terreno.
        // Sin él, ON_FLOOR/UNDER_FLOOR también aciertan en el suelo de cada cueva y el
        // bloque rocoso tapiza la columna entera hasta la bedrock.
        //
        // Corte por altura a MIN_ROCKY_SURFACE_Y (55), NO al nivel del mar (63): se probó antes
        // un yBlockCheck(63, 0) — cortar justo en la línea de agua — y se revirtió porque el
        // usuario pedía que el bloque rocoso siguiera hasta la orilla, como un acantilado
        // natural, en vez de un muro artificial justo en la costa. Con erosion en EROSION_5
        // (ver ModOverworldRegion) esa costa ya no es tan abrupta, pero el bioma seguía
        // pudiendo asignarse en valles bajos que acaban siendo literalmente OCÉANO (suelo
        // marino a bastante profundidad, no solo la orilla) — ROCKY_BLOCK tapizando el fondo
        // del mar lejos de cualquier costa. 55 es un punto intermedio a propósito: sigue
        // dejando que el acantilado se hunda varios bloques bajo el agua cerca de la orilla
        // (el efecto natural que se quería conservar), pero corta antes de llegar al suelo
        // oceánico real (que en vainilla suele estar bastante más abajo de eso), así que ya no
        // "tapiza" el océano. Por debajo de esta Y la regla simplemente no aplica y cae al
        // bloque por defecto (stone/deepslate) del resto de la secuencia de superficie.
        SurfaceRules.RuleSource rockySurfaceAboveFloor = SurfaceRules.ifTrue(
                SurfaceRules.yBlockCheck(VerticalAnchor.absolute(MIN_ROCKY_SURFACE_Y), 0),
                rockySurface
        );

        return SurfaceRules.ifTrue(
                SurfaceRules.abovePreliminarySurface(),                      // ⚠ API
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(ModBiomes.ROCKY_WASTELAND),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, rockySurfaceAboveFloor),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, rockySurfaceAboveFloor)
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