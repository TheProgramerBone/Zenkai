package com.hmc.zenkai.compat.ponder;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.ponder.scenes.EnergyGeneratorPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.PieceConnectorPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.ScouterBenchPonderScenes;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * ⚠ Esta clase (y todo lo que cuelga de compat/ponder/scenes/) importa tipos de la API de
 * Ponder directamente en sus firmas. Nunca debe instanciarse ni referenciarse desde código que
 * se ejecute sin pasar antes por PonderCompat.register() — es lo que garantiza que, sin el mod
 * Ponder instalado, esta clase jamás llega a cargarse.
 */
public final class ZenkaiPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return Zenkai.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PieceConnectorPonderScenes.register(helper);
        ScouterBenchPonderScenes.register(helper);
        EnergyGeneratorPonderScenes.register(helper);

        // La cámara de gravedad (bloque nuevo, ver diseño de gravedad/nave) todavía no existe en
        // ModBlocks — la gravedad natural (planeta de Kaiosama/HTC) ya está cerrada, pero la
        // cámara en sí sigue sin empezar. En cuanto se registre el bloque, añadir aquí su propia
        // clase de escenas siguiendo el mismo patrón que las tres de abajo.
        // TODO: GravityChamberPonderScenes.register(helper) — pendiente de que exista el bloque.
    }
}
