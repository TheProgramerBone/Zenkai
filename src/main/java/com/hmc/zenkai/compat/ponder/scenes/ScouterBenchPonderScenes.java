package com.hmc.zenkai.compat.ponder.scenes;

import com.hmc.zenkai.registry.ModBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Escenas Ponder del banco de scouter (scouter_bench). Dos escenas: "scouter_bench" (qué hace
 * el banco: reparar, mejorar, teñir — cuesta materiales, la mesa solo facilita el proceso, NO
 * regala nada) y "scouter_bench/energy_link", registrada también bajo energy_generator, que
 * enseña la conexión sin cables entre los dos bloques. Esquemas en
 * tools/gen_ponder_schematics.py.
 */
public final class ScouterBenchPonderScenes {
    private ScouterBenchPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModBlocks.SCOUTER_BENCH.getId(), "scouter_bench",
                ScouterBenchPonderScenes::intro);

        // Visible desde el menú de ayuda de los dos bloques: es la misma historia contada una
        // vez, no una escena "de scouter_bench" y otra "de energy_generator" por separado.
        helper.forComponents(ModBlocks.SCOUTER_BENCH.getId(), ModBlocks.ENERGY_GENERATOR.getId())
                .addStoryBoard("scouter_bench/energy_link", ScouterBenchPonderScenes::energyLink);
    }

    private static void intro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("scouter_bench", "Banco de scouter");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // El suelo (y=0) ya lo revela showBasePlate() arriba; esto muestra el bloque de
        // verdad, que en el esquema vive en y=1.
        scene.world().showSection(util.select().layer(1), Direction.UP);
        scene.idle(10);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Inserta un scouter para repararlo, mejorarlo o cambiarle el color desde aquí");
        scene.idle(100);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(2, 1, 2)))
                .text("Reparar y mejorar cuestan materiales de tu inventario y tardan unos segundos");
        scene.idle(100);

        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(2, 1, 2)))
                .text("El tinte de color es instantáneo: solo gasta FE, no pasa por ese trabajo");
        scene.idle(90);

        scene.markAsFinished();
    }

    private static void energyLink(SceneBuilder scene, SceneBuildingUtil util) {
        // El id de aquí (title/text_N en el lang) es INDEPENDIENTE del sceneId de
        // addStoryBoard/forComponents de arriba — ese solo localiza el .nbt (por eso ahí sí
        // lleva la barra, "scouter_bench/energy_link.nbt"). Con barra aquí, la clave de lang
        // sale igual, con barra — y Minecraft no la encuentra. Confirmado con capturas del
        // usuario tras usar barra en los dos: la clave real que pide el juego es con guion bajo.
        scene.title("scouter_bench_energy_link", "Generador + Banco: sin cables");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        scene.world().showSection(util.select().position(1, 1, 2), Direction.UP);
        scene.idle(10);
        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(1, 1, 2)))
                .text("El generador quema combustible y produce FE mientras dura la carga");
        scene.idle(100);

        scene.world().showSection(util.select().position(2, 1, 2), Direction.UP);
        scene.idle(10);
        scene.overlay().showOutlineWithText(
                        util.select().fromTo(util.grid().at(1, 1, 2), util.grid().at(2, 1, 2)), 100)
                .colored(PonderPalette.BLUE)
                .text("Sin cables: cada tick busca un receptor de FE en sus 6 caras y le empuja lo que acepte");
        scene.idle(110);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Basta con dejarlos pegados — el banco usa esa FE para reparar y mejorar el scouter");
        scene.idle(100);

        scene.markAsFinished();
    }
}
