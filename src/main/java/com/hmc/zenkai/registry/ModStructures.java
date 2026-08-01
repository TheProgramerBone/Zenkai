package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.structure.KamiStructure;
import com.hmc.zenkai.worldgen.structure.SegmentPiece;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Zenkai.MOD_ID);

    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Zenkai.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<KamiStructure>> KAMI_PALACE =
            TYPES.register("kami_palace", () -> () -> KamiStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SEGMENT =
            PIECES.register("segment", () -> (StructurePieceType.StructureTemplateType) SegmentPiece::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}