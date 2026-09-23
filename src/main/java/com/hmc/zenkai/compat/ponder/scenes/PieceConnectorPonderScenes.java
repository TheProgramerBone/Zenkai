package com.hmc.zenkai.compat.ponder.scenes;

import com.hmc.zenkai.registry.ModBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Escenas Ponder del conector de piezas (piece_connector) — el bloque de autoría del motor
 * propio de piezas modulares de Zenkai (nuestro "jigsaw", ver .claude/docs/piece-graph.md).
 * Dos escenas registradas bajo el mismo bloque: "piece_connector" (identificación rápida) y
 * "piece_connector_example" (cómo encajan de verdad dos piezas, con socket y todo). Los
 * esquemas viven en tools/gen_ponder_schematics.py — regenerar ahí, no a mano.
 */
public final class PieceConnectorPonderScenes {
    private PieceConnectorPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation component = ModBlocks.PIECE_CONNECTOR.getId();
        helper.addStoryBoard(component, "piece_connector", PieceConnectorPonderScenes::intro);
        helper.addStoryBoard(component, "piece_connector_example", PieceConnectorPonderScenes::example);
    }

    private static void intro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("piece_connector", "Conector de piezas");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // El suelo (y=0) ya lo revela showBasePlate() arriba; esto muestra el bloque de
        // verdad, que en el esquema vive en y=1.
        scene.world().showSection(util.select().layer(1), Direction.UP);
        scene.idle(10);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Cada zenkai:piece_connector marca un punto de enganche de una pieza NBT");
        scene.idle(100);

        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(2, 1, 2)))
                .text("Clic derecho abre un editor: ahí se gira la cara activa y se escribe el socket");
        scene.idle(90);

        scene.markAsFinished();
    }

    /**
     * Pieza A (x 0-2) y Pieza B (x 3-5) ya "encajadas" en el esquema, cada una con su propio
     * conector en el borde compartido — el motor real (PieceGraphPlacer) nunca las anima
     * deslizándose, así que esta escena tampoco lo simula: muestra el resultado del algoritmo
     * (buscar socket igual, rotar, comprobar colisión) explicado paso a paso sobre el estado ya
     * resuelto, y termina ocultando los dos conectores para dejar claro que nunca sobreviven a
     * la pieza final (PieceGraphPlacer#placeAll los excluye siempre, mismo BlockIgnoreProcessor
     * que excluye STRUCTURE_VOID/JIGSAW).
     */
    private static void example(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("piece_connector_example", "Cómo encajan dos piezas");

        scene.world().showSection(
                util.select().fromTo(util.grid().at(0, 0, 0), util.grid().at(5, 0, 2)), Direction.UP);
        scene.idle(5);

        scene.world().showSection(
                util.select().fromTo(util.grid().at(0, 1, 0), util.grid().at(2, 1, 2)), Direction.UP);
        scene.idle(10);
        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(1, 1, 1)))
                .text("Esta es la Pieza A: un fragmento de estructura guardado en su propio NBT");
        scene.idle(100);

        scene.overlay().showOutlineWithText(util.select().position(2, 1, 1), 90)
                .colored(PonderPalette.GREEN)
                .text("Su conector apunta hacia fuera (FACING) — por aquí espera engancharse a otra pieza");
        scene.idle(100);

        scene.world().showSection(
                util.select().fromTo(util.grid().at(3, 1, 0), util.grid().at(5, 1, 2)), Direction.UP);
        scene.idle(10);
        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(4, 1, 1)))
                .text("El motor busca en el catálogo una Pieza B con un conector del MISMO socket");
        scene.idle(100);

        scene.overlay().showOutlineWithText(util.select().position(3, 1, 1), 90)
                .colored(PonderPalette.GREEN)
                .text("zenkai:namek_path, por ejemplo — el mismo texto libre en las dos piezas");
        scene.idle(100);

        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(2, 1, 1)))
                .text("La rota para que encaje de frente y comprueba que no choque con lo ya colocado");
        scene.idle(90);

        scene.world().hideSection(
                util.select().position(2, 1, 1).add(util.select().position(3, 1, 1)),
                Direction.UP);
        scene.idle(10);

        scene.overlay().showText(90)
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)))
                .text("Los dos conectores se descartan siempre: nunca quedan en la pieza final");
        scene.idle(100);

        scene.markAsFinished();
    }
}
