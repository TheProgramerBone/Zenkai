package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModStructureProcessors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Marca como PERSISTENTES todas las hojas que coloca una plantilla.
 *
 * Las hojas guardadas en un NBT conservan persistent=false, así que al colocarlas empiezan a
 * decaer por random tick y van soltando palos y retoños durante los minutos siguientes. En el
 * Otherworld eso es especialmente visible: la estructura se coloca cuando un jugador muere y
 * para cuando alguien llega, lleva rato desmoronándose sola.
 *
 * Ojo: esto NO es lo mismo que el flag de colocación. El flag evita updates en el momento;
 * el decaimiento ocurre después y solo se corta con persistent=true.
 */
public class PersistentLeavesProcessor extends StructureProcessor {

    public static final PersistentLeavesProcessor INSTANCE = new PersistentLeavesProcessor();
    public static final MapCodec<PersistentLeavesProcessor> CODEC = MapCodec.unit(INSTANCE);

    private PersistentLeavesProcessor() {}

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {

        BlockState state = current.state();
        if (!state.hasProperty(LeavesBlock.PERSISTENT)) return current;
        if (state.getValue(LeavesBlock.PERSISTENT)) return current;

        return new StructureTemplate.StructureBlockInfo(
                current.pos(),
                state.setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 1),
                current.nbt());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessors.PERSISTENT_LEAVES.get();
    }
}