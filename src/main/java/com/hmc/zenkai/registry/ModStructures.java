package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.structure.BloodPondPiece;
import com.hmc.zenkai.worldgen.structure.BloodPondStructure;
import com.hmc.zenkai.worldgen.structure.FriezaCocoonPiece;
import com.hmc.zenkai.worldgen.structure.FriezaCocoonStructure;
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

    // Landmark del Blood Pond, Fase 6 del rework del HFIL (ver
    // .claude/docs/worldgen.md). BloodPondPiece NO es un
    // StructureTemplateType (no lee NBT, ver su javadoc) — usa el StructurePieceType genérico.
    public static final DeferredHolder<StructureType<?>, StructureType<BloodPondStructure>> BLOOD_POND =
            TYPES.register("blood_pond", () -> () -> BloodPondStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> BLOOD_POND_PIECE =
            PIECES.register("blood_pond", () -> BloodPondPiece::new);

    // El capullo de Freezer (propuesta H del HFIL, Dragon Ball Super). Pieza a mano, como el
    // Blood Pond: ver FriezaCocoonPiece.
    public static final DeferredHolder<StructureType<?>, StructureType<FriezaCocoonStructure>> FRIEZA_COCOON =
            TYPES.register("frieza_cocoon", () -> () -> FriezaCocoonStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> FRIEZA_COCOON_PIECE =
            PIECES.register("frieza_cocoon", () -> FriezaCocoonPiece::new);

    // La torre de Janemba (landmark del Janemba Rift; sustituye a la Compuerta el 2026-10-05).
    // Pieza a mano, como las dos de arriba.
    public static final DeferredHolder<StructureType<?>, StructureType<com.hmc.zenkai.worldgen.structure.JanembaTowerStructure>> JANEMBA_TOWER =
            TYPES.register("janemba_tower", () -> () -> com.hmc.zenkai.worldgen.structure.JanembaTowerStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> JANEMBA_TOWER_PIECE =
            PIECES.register("janemba_tower", () -> com.hmc.zenkai.worldgen.structure.JanembaTowerPiece::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}