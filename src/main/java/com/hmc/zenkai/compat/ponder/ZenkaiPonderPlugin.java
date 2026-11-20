package com.hmc.zenkai.compat.ponder;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.ponder.scenes.DragonBallsPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.EnergyGeneratorPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.GravityChamberPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.PieceConnectorPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.ScouterBenchPonderScenes;
import com.hmc.zenkai.compat.ponder.scenes.TechPonderScenes;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Esta clase (y cuanto cuelga de compat/ponder/scenes/) importa tipos de la API de
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
        DragonBallsPonderScenes.register(helper);
        TechPonderScenes.register(helper);   // cámara de ki, cables, tuberías, tanques, llave, motor (0.7.0)
        GravityChamberPonderScenes.register(helper);   // consola y puerto de la cámara de gravedad (Fase 3)
    }
}
