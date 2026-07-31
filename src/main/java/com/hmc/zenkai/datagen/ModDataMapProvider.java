package com.hmc.zenkai.datagen;

import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Strippable;

import java.util.concurrent.CompletableFuture;

public class ModDataMapProvider extends DataMapProvider {
    protected ModDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);

        builder(NeoForgeDataMaps.STRIPPABLES)
                .add(ModBlocks.AJISA_LOG.getKey(),
                        new Strippable(ModBlocks.STRIPPED_AJISA_LOG.get()), false)
                .add(ModBlocks.AJISA_WOOD.getKey(),
                        new Strippable(ModBlocks.STRIPPED_AJISA_WOOD.get()), false);
    }



}