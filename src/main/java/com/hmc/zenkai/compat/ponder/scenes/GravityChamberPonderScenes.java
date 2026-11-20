package com.hmc.zenkai.compat.ponder.scenes;

import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.registry.ModBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Escena Ponder de la cámara de gravedad (Fase 3 de la 0.7.0), para la consola y el puerto.
 * Esquema: assets/zenkai/ponder/gravity_chamber.nbt (tools/gen_ponder_schematics.py, gen_gravity_chamber):
 * esfera de radio 5 con la consola en (6,1,6), el suelo a cuadros como fondo de la sala, un puerto en la
 * pared este a ras de suelo, tres cables y un tanque de energía fuera.
 *
 * La corteza se calcula con la MISMA geometría que el block entity (insideSphere), así la selección
 * coincide celda a celda con el esquema. Para enseñar el interior se oculta la mitad norte (z bajas,
 * la más cercana a la cámara de Ponder).
 */
public final class GravityChamberPonderScenes {
    private GravityChamberPonderScenes() {}

    private static final int R = 5;
    private static final int CX = 6, CY = 1, CZ = 6;   // consola
    private static final int PORT_X = CX + 3;

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModBlocks.GRAVITY_CHAMBER.getId(), ModBlocks.GRAVITY_CHAMBER_PORT.getId())
                .addStoryBoard("gravity_chamber", GravityChamberPonderScenes::chamber);
    }

    /** Celdas de la corteza por encima del suelo (y >= 1), sin el puerto; {@code nearHalf} = solo la mitad norte. */
    private static Selection shell(SceneBuildingUtil util, boolean nearHalfOnly) {
        Selection sel = null;
        int cy = CY + R - 1;
        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dy = -R - 1; dy <= R + 1; dy++) {
                for (int dz = -R - 1; dz <= R + 1; dz++) {
                    if (GravityChamberBlockEntity.insideSphere(dx, dy, dz, R)) continue;
                    if (!(GravityChamberBlockEntity.insideSphere(dx + 1, dy, dz, R) || GravityChamberBlockEntity.insideSphere(dx - 1, dy, dz, R)
                            || GravityChamberBlockEntity.insideSphere(dx, dy + 1, dz, R) || GravityChamberBlockEntity.insideSphere(dx, dy - 1, dz, R)
                            || GravityChamberBlockEntity.insideSphere(dx, dy, dz + 1, R) || GravityChamberBlockEntity.insideSphere(dx, dy, dz - 1, R))) {
                        continue;
                    }
                    int x = CX + dx, y = cy + dy, z = CZ + dz;
                    if (y < 1 || (x == PORT_X && y == 1 && z == CZ)) continue;
                    if (nearHalfOnly && dz >= 0) continue;
                    Selection one = util.select().position(x, y, z);
                    sel = sel == null ? one : sel.add(one);
                }
            }
        }
        return sel;
    }

    private static void chamber(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("gravity_chamber", "Cámara de gravedad");
        scene.configureBasePlate(0, 0, 15);
        scene.showBasePlate();
        BlockPos console = util.grid().at(CX, CY, CZ);
        BlockPos port = util.grid().at(PORT_X, 1, CZ);

        scene.idle(10);
        scene.world().showSection(util.select().position(console), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(console))
                .text("La consola va en el centro del suelo de una sala esférica cerrada");
        scene.idle(100);

        Selection wall = shell(util, false);
        scene.world().showSection(wall, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(110).placeNearTarget().pointAt(util.vector().topOf(util.grid().at(CX, CY + 2 * R - 1, CZ)))
                .text("La pared puede ser de cualquier bloque. El radio (de 5 a 10) se elige en la pantalla, y su holograma marca dónde faltan bloques");
        scene.idle(120);

        // Vista interior: fuera la mitad más cercana a la cámara.
        scene.world().hideSection(shell(util, true), Direction.UP);
        scene.idle(20);
        scene.overlay().showText(100).placeNearTarget().pointAt(util.vector().topOf(console))
                .text("Pon una mejora de gravedad (de cobre x10 a netherita x500) y elige la gravedad en la pantalla");
        scene.idle(110);

        scene.world().showSection(util.select().position(port)
                .add(util.select().fromTo(PORT_X + 1, 1, CZ, PORT_X + 4, 1, CZ)), Direction.WEST);
        scene.idle(15);
        scene.overlay().showOutlineWithText(util.select().position(port), 100).colored(PonderPalette.GREEN)
                .text("La energía entra por un puerto de la pared, o por debajo de la consola. Más gravedad y más radio gastan más FE");
        scene.idle(110);

        scene.overlay().showText(110).placeNearTarget().pointAt(util.vector().centerOf(console.above(2)))
                .text("Dentro, la gravedad frena y entrena según tu poder: en una forma más fuerte aguantas más peso. El botón de la pantalla la enciende o la apaga");
        scene.idle(120);
        scene.overlay().showText(100).placeNearTarget().pointAt(util.vector().centerOf(console.above(2)))
                .colored(PonderPalette.RED)
                .text("Con más del doble de lo que aguantas pierdes vida, hasta quedarte en 1 (gamerule zenkai_gravityDamage)");
        scene.idle(110);
        scene.markAsFinished();
    }
}
