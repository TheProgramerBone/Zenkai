package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Zenkai.MOD_ID)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTableProvider::new, LootContextParamSets.BLOCK)), lookupProvider));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));

        BlockTagsProvider blockTagsProvider = new ModBlockTagProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTagsProvider);
        generator.addProvider(event.includeServer(), new ModItemTagProvider(packOutput, lookupProvider, blockTagsProvider.contentsGetter(), existingFileHelper));
        generator.addProvider(event.includeServer(), new ModDataMapProvider(packOutput, lookupProvider));

        // En variable porque su lookup ENRIQUECIDO es lo único que conoce los biomas,
        // dimensiones y features que él mismo crea. El de event.getLookupProvider() solo
        // tiene los registros estáticos y los dinámicos de vainilla.
        ModDatapackProvider datapackProvider = new ModDatapackProvider(packOutput, lookupProvider);
        generator.addProvider(event.includeServer(), datapackProvider);

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeServer(),
                new ModAdvancementProvider(packOutput, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(), new ModEntityStatsProvider(packOutput));

        // OJO: el lookup del datapackProvider, NO el base. rocky_wasteland es un bioma de
        // datapack, así que en el lookup base no existe y TagsProvider aborta el datagen
        // entero con "missing following references: zenkai:rocky_wasteland". Va después de
        // registrar el datapackProvider: este futuro se completa cuando aquel ha corrido.
        generator.addProvider(event.includeServer(),
                new ModBiomeTagProvider(packOutput, datapackProvider.getRegistryProvider(),
                        existingFileHelper));
    }
}