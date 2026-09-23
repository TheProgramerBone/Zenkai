package com.hmc.zenkai.compat.ponder.scenes;

import com.hmc.zenkai.registry.ModBlocks;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Escena Ponder del generador de energía (energy_generator). El esquema en
 * assets/zenkai/ponder/energy_generator.nbt está generado con tools/gen_ponder_schematics.py:
 * suelo 5x5 a cuadros en y=0 + el bloque real en (2, 1, 2).
 */
public final class EnergyGeneratorPonderScenes {
    private EnergyGeneratorPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModBlocks.ENERGY_GENERATOR.getId(), "energy_generator",
                EnergyGeneratorPonderScenes::intro);
    }

    private static void intro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("energy_generator", "Generador de energía");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // El suelo (y=0) ya lo revela showBasePlate() arriba; esto muestra el bloque de
        // verdad, que en el esquema vive en y=1.
        scene.world().showSection(util.select().layer(1), Direction.UP);
        scene.idle(10);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Clic derecho con combustible lo carga; se consume entero al encenderse");
        scene.idle(100);

        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.NORTH))
                .text("Sin cables: empuja FE a cualquiera de sus seis caras mientras arde");
        scene.idle(90);

        scene.markAsFinished();
    }
}
