package com.hmc.zenkai.worldgen.npc;

import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
// import net.minecraft.world.level.Level;   // <- para NPCs del overworld

import java.util.List;

/**
 * ÚNICO lugar donde se declaran los NPCs de estructura del mod.
 * Para añadir uno nuevo, agrega una línea a ALL. Nada más que tocar.
 */
public final class StructureNpcs {
    private StructureNpcs() {}

    public static final List<StructureNpc> ALL = List.of(
            // Yemma en el Otro Mundo
            new StructureNpc(ModDimensions.OTHERWORLD_LEVEL, new BlockPos(57, 147, 54), 180.0f,
                    ModEntities.YEMMA::get)

            // ── Ejemplos futuros (descomenta y ajusta cuando existan la entidad y la dimensión) ──
            // , new StructureNpc(ModDimensions.KAIO_LEVEL,  new BlockPos(0, 100, 0), 0.0f,   () -> ModEntities.KAIOSAMA.get())
            // , new StructureNpc(ModDimensions.BILLS_LEVEL, new BlockPos(10, 80, 4), 90.0f,  () -> ModEntities.BILLS.get())
            // , new StructureNpc(ModDimensions.BILLS_LEVEL, new BlockPos(12, 80, 4), 90.0f,  () -> ModEntities.WHIS.get())
            // , new StructureNpc(Level.OVERWORLD,           new BlockPos(200, 70, 50), 180f, () -> ModEntities.GOKU.get())
            // , new StructureNpc(Level.OVERWORLD,           KAMI_NPC_POS, 180f,             () -> ModEntities.KAMISAMA.get())
    );
}