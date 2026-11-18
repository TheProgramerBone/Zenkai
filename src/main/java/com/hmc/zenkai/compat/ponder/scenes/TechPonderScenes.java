package com.hmc.zenkai.compat.ponder.scenes;

import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Escenas Ponder de la tecnología de la 0.7.0: cámara de contención de ki, cables, tuberías, tanques,
 * la llave y el Motor de Ki. Esquemas en assets/zenkai/ponder/ (tools/gen_ponder_schematics.py):
 * suelo a cuadros en y=0 y los bloques en y=1; los transportes llevan su aspecto escrito en el NBT,
 * porque en el mundo de Ponder no corre el tick del servidor que lo calcula.
 *
 * El id de scene.title(...) decide TODAS las claves de lang (zenkai.ponder.<id>.header/text_N, en orden
 * de llamada); ver la skill add-ponder-scene. Las dos de transporte se registran en la subcarpeta
 * transport/ (el .nbt) pero su title va sin barra, como scouter_bench_energy_link.
 */
public final class TechPonderScenes {
    private TechPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModBlocks.KI_CONTAINMENT_CHAMBER.getId(), "ki_containment_chamber", TechPonderScenes::kiChamber);
        helper.addStoryBoard(ModBlocks.ENERGY_CABLE.getId(), "transport/energy_cables", TechPonderScenes::energyCables);
        helper.addStoryBoard(ModBlocks.FLUID_PIPE.getId(), "transport/fluid_pipes", TechPonderScenes::fluidPipes);
        helper.forComponents(ModBlocks.ENERGY_TANK.getId(), ModBlocks.FLUID_TANK.getId())
                .addStoryBoard("tanks", TechPonderScenes::tanks);
        helper.addStoryBoard(ModItems.WRENCH.getId(), "wrench", TechPonderScenes::wrench);
        // El motor y su esquema (ejes de Create) solo tienen sentido con Create: sin él el bloque es inerte.
        if (ModList.get().isLoaded("create")) {
            helper.addStoryBoard(ModBlocks.KI_MOTOR.getId(), "ki_motor", TechPonderScenes::kiMotor);
        }
    }

    private static void start(SceneBuilder scene, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
    }

    private static void kiChamber(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "ki_containment_chamber", "Cámara de contención de ki");
        BlockPos lower = util.grid().at(2, 1, 2), upper = util.grid().at(2, 2, 2);
        scene.world().showSection(util.select().fromTo(lower, upper), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(upper))
                .text("Entra en la cámara y cierra la puerta: carga tu ki y lo guarda como fluido de Ki");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(upper, Direction.SOUTH))
                .text("El tipo de ki depende de la forma que lleves: Base, Súper, Divino o Legendario");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(lower, Direction.SOUTH))
                .colored(PonderPalette.RED)
                .text("Sin la mejora de tolerancia de ese tipo, la cámara se sobrecarga y te hace daño");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(lower, Direction.EAST))
                .text("Con el conversor encendido el fluido pasa a FE; saca fluido o energía con tuberías y cables");
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void energyCables(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "transport_energy_cables", "Cables de energía");
        scene.world().showSection(util.select().position(0, 1, 2).add(util.select().position(4, 1, 2)), Direction.DOWN);
        scene.idle(10);
        var cables = util.select().fromTo(1, 1, 2, 3, 1, 2);
        scene.world().showSection(cables, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(cables, 90).colored(PonderPalette.GREEN)
                .text("Los cables conectados forman una red: su capacidad es la suma de la de cada cable");
        scene.idle(100);
        scene.overlay().showText(80).placeNearTarget().pointAt(util.vector().topOf(util.grid().at(4, 1, 2)))
                .text("La red reparte el FE entre todo lo que tiene delante");
        scene.idle(90);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Cada cara tiene un modo: Normal, Empujar, Tirar o Desconectada");
        scene.idle(100);
        scene.overlay().showText(100).placeNearTarget().pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .text("Clic derecho con la llave en una cara cambia su modo; con la mano vacía, agachado + clic derecho");
        scene.idle(110);
        scene.markAsFinished();
    }

    private static void fluidPipes(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "transport_fluid_pipes", "Tuberías de fluidos");
        scene.world().showSection(util.select().position(0, 1, 2).add(util.select().position(4, 1, 2)), Direction.DOWN);
        scene.idle(10);
        var pipes = util.select().fromTo(1, 1, 2, 3, 1, 2);
        scene.world().showSection(pipes, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(pipes, 90).colored(PonderPalette.BLUE)
                .text("Las tuberías mueven fluidos; dos redes con fluidos distintos no se mezclan");
        scene.idle(100);
        scene.overlay().showText(80).placeNearTarget().pointAt(util.vector().blockSurface(util.grid().at(1, 1, 2), Direction.WEST))
                .colored(PonderPalette.GREEN)
                .text("Tirar (el collar) saca fluido del bloque de delante");
        scene.idle(90);
        scene.overlay().showText(80).placeNearTarget().pointAt(util.vector().blockSurface(util.grid().at(3, 1, 2), Direction.EAST))
                .colored(PonderPalette.BLUE)
                .text("Empujar (la punta) lo mete en el bloque de delante");
        scene.idle(90);
        scene.markAsFinished();
    }

    private static void tanks(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "tanks", "Tanques de energía y de fluidos");
        BlockPos energy = util.grid().at(1, 1, 2), fluid = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().position(energy), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(energy))
                .text("Clic derecho abre su pantalla: carga o descarga baterías y otros ítems con FE");
        scene.idle(100);
        scene.world().showSection(util.select().position(fluid), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(fluid))
                .text("El de fluidos llena y vacía cubos y recipientes, también los cubos de Ki");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(fluid))
                .text("Tanques iguales pegados se unen en uno: base de hasta 3x3 y la altura que quieras");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(energy, Direction.EAST))
                .colored(PonderPalette.OUTPUT)
                .text("Las placas muestran el modo de cada cara: salida, entrada o apagada");
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void wrench(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "wrench", "Llave");
        BlockPos tank = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(tank), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(tank))
                .text("Con la llave en la mano, cada cara del bloque apuntado se sombrea con el color de su modo");
        scene.idle(100);
        scene.overlay().showText(80).placeNearTarget().pointAt(util.vector().blockSurface(tank, Direction.SOUTH))
                .text("Clic derecho en una cara pasa ese puerto al siguiente modo");
        scene.idle(90);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(tank, Direction.WEST))
                .colored(PonderPalette.RED)
                .text("Agachado + clic derecho desmonta el bloque y lo guarda con su contenido y sus ajustes");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(tank))
                .text("Agachado + clic derecho al aire cambia de modo: energía, fluido o rotar el bloque");
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void kiMotor(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "ki_motor", "Motor de Ki");
        BlockPos motor = util.grid().at(1, 1, 2);
        scene.world().showSection(util.select().position(0, 1, 2).add(util.select().position(motor)), Direction.DOWN);
        scene.idle(10);
        var shafts = util.select().fromTo(2, 1, 2, 3, 1, 2);
        scene.world().showSection(shafts, Direction.WEST);
        scene.idle(15);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(motor))
                .text("El Motor de Ki convierte FE y fluido de Ki en rotación para Create");
        scene.idle(100);
        scene.overlay().showOutlineWithText(shafts, 90).colored(PonderPalette.GREEN)
                .text("Elige velocidad y sentido en su pantalla; solo gasta la carga que la red usa de verdad");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(motor))
                .text("En modo dínamo hace lo contrario: toma rotación de la red y produce FE");
        scene.idle(100);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(motor, Direction.WEST))
                .text("Sus puertos de energía y de fluido se configuran en la cruz de caras");
        scene.idle(100);
        scene.markAsFinished();
    }
}
