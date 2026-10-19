package com.hmc.zenkai.compat.ponder.scenes;

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
 * Escena Ponder de las siete Esferas del Dragón: cómo se funden en el bloque "Todas las esferas"
 * (all_dragon_balls) y, con otro esquema y su propio bloque, el de Namek (namek_all_dragon_balls). Cada escena se
 * muestra desde SU bloque Y desde cada una de SUS siete esferas.
 *
 * Reglas reales que enseña (DragonBallStructureGenerator.tryFormStructure): las siete, una de cada
 * estrella, en forma de H dentro de una rejilla 3x3 (dos columnas de tres + la del centro), y la
 * fusión se dispara con la ÚLTIMA esfera colocada, que tiene que ser la del centro (el "origen"
 * de la comprobación). El esquema (assets/zenkai/ponder/all_dragon_balls.nbt y namek_all_dragon_balls.nbt) lo escribe
 * tools/gen_ponder_schematics.py: suelo 7x7 y las siete esferas en y=1 con la H centrada en (3,1,3).
 *
 * Los text_N salen en el orden de las llamadas .text(...) de esta función (ver la skill
 * add-ponder-scene); si se reordenan, hay que reordenar las claves de lang.
 */
public final class DragonBallsPonderScenes {
    private DragonBallsPonderScenes() {}

    /** Ids de las escenas: los usan el registro Y scene.title() (TIENEN que ser el mismo string). */
    private static final String EARTH_ID = "all_dragon_balls";
    private static final String NAMEK_ID = "namek_all_dragon_balls";

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                        ModBlocks.ALL_DRAGON_BALLS.getId(),
                        ModBlocks.DRAGON_BALL_1.getId(), ModBlocks.DRAGON_BALL_2.getId(),
                        ModBlocks.DRAGON_BALL_3.getId(), ModBlocks.DRAGON_BALL_4.getId(),
                        ModBlocks.DRAGON_BALL_5.getId(), ModBlocks.DRAGON_BALL_6.getId(),
                        ModBlocks.DRAGON_BALL_7.getId())
                .addStoryBoard(EARTH_ID, (scene, util) -> fusion(scene, util, EARTH_ID, false));
        // Namek: referencia SU bloque (NAMEK_ALL_DRAGON_BALLS) y sus esferas, no las de la Tierra.
        helper.forComponents(
                        ModBlocks.NAMEK_ALL_DRAGON_BALLS.getId(),
                        ModBlocks.NAMEK_DRAGON_BALL_1.getId(), ModBlocks.NAMEK_DRAGON_BALL_2.getId(),
                        ModBlocks.NAMEK_DRAGON_BALL_3.getId(), ModBlocks.NAMEK_DRAGON_BALL_4.getId(),
                        ModBlocks.NAMEK_DRAGON_BALL_5.getId(), ModBlocks.NAMEK_DRAGON_BALL_6.getId(),
                        ModBlocks.NAMEK_DRAGON_BALL_7.getId())
                .addStoryBoard(NAMEK_ID, (scene, util) -> fusion(scene, util, NAMEK_ID, true));
    }

    private static void fusion(SceneBuilder scene, SceneBuildingUtil util, String id, boolean namek) {
        scene.title(id, namek ? "Las siete Esferas de Namekusei" : "Las siete Esferas del Dragón");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos center = util.grid().at(3, 1, 3);
        // Las seis de las dos columnas de la H, de una en una (x=2 y x=4, z=2..4).
        BlockPos[] outer = {
                util.grid().at(2, 1, 2), util.grid().at(4, 1, 2),
                util.grid().at(2, 1, 3), util.grid().at(4, 1, 3),
                util.grid().at(2, 1, 4), util.grid().at(4, 1, 4)};
        Selection outerSel = util.select().position(outer[0]);
        for (int i = 1; i < outer.length; i++) outerSel = outerSel.add(util.select().position(outer[i]));

        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().topOf(center))
                .text("Hacen falta las siete esferas del dragón, una de cada estrella");
        for (BlockPos p : outer) {
            scene.world().showSection(util.select().position(p), Direction.DOWN);
            scene.idle(8);
        }
        scene.idle(40);

        scene.overlay().showOutlineWithText(outerSel, 90)
                .colored(PonderPalette.WHITE)
                .placeNearTarget()
                .text("Colócalas en forma de H: dos columnas de tres esferas, dentro de una rejilla de 3×3");
        scene.idle(100);

        // La del centro va la última, y el esquema ya la trae: solo se revela.
        scene.overlay().showOutlineWithText(util.select().position(center), 100)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .text("La ÚLTIMA esfera que pongas debe ser la del CENTRO de la H: es la que cierra la figura");
        scene.idle(20);
        scene.world().showSection(util.select().position(center), Direction.DOWN);
        scene.idle(90);

        // Fusión: las seis de fuera se rompen y el centro pasa a ser el bloque único.
        scene.overlay().showText(80)
                .placeNearTarget()
                .pointAt(util.vector().topOf(center))
                .text(namek ? "Al cerrar la H, las siete se funden en un solo bloque: Todas las Esferas de Namekusei"
                        : "Al cerrar la H, las siete se funden en un solo bloque: Todas las Esferas del Dragón");
        scene.idle(30);
        for (BlockPos p : outer) scene.world().destroyBlock(p);
        scene.idle(6);
        scene.world().setBlock(center, (namek ? ModBlocks.NAMEK_ALL_DRAGON_BALLS : ModBlocks.ALL_DRAGON_BALLS).get().defaultBlockState(), true);
        scene.effects().indicateSuccess(center);
        scene.idle(70);

        scene.overlay().showText(100)
                .placeNearTarget()
                .pointAt(util.vector().topOf(center))
                .text(namek ? "Clic derecho lo activa: Polunga aún no tiene modelo, así que invoca a Shenlong con 3 deseos"
                        : "Clic derecho lo activa: invoca a Shenlong, hace caer la noche y concede deseos");
        scene.idle(110);

        scene.overlay().showText(100)
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(center, Direction.NORTH))
                .text("Cada jugador debe esperar un tiempo entre deseos. Si rompes el bloque, recuperas las siete esferas");
        scene.idle(110);

        scene.markAsFinished();
    }
}
