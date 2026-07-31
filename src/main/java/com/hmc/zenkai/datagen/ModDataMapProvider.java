package com.hmc.zenkai.datagen;

import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataMaps;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Strippable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModDataMapProvider extends DataMapProvider {
    protected ModDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather(HolderLookup.@NotNull Provider provider) {

        // ── Desbastado con hacha ─────────────────────────────────────────────
        builder(NeoForgeDataMaps.STRIPPABLES)
                .add(ModBlocks.AJISA_LOG.getKey(),  new Strippable(ModBlocks.STRIPPED_AJISA_LOG.get()), false)
                .add(ModBlocks.AJISA_WOOD.getKey(), new Strippable(ModBlocks.STRIPPED_AJISA_WOOD.get()), false);

        // ── Créditos de modelos y texturas ───────────────────────────────────
        credit(ModItems.ARCOSIAN_RACE_HELMET.get(), "Kirbro", "All Arcosian Model and Texture");
        credit(ModItems.ARCOSIAN_RACE_CHESTPLATE.get(), "Kirbro", "All Arcosian Model and Texture");
        credit(ModItems.ARCOSIAN_RACE_LEGGINGS.get(), "Kirbro", "All Arcosian Model and Texture");
        credit(ModItems.ARCOSIAN_RACE_BOOTS.get(), "Kirbro", "All Arcosian Model and Texture");
        credit(ModBlocks.DRAGON_BALL_1.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_2.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_3.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_4.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_5.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_6.get(), "Kirbro", "Texture");
        credit(ModBlocks.DRAGON_BALL_7.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_1.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_2.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_3.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_4.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_5.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_6.get(), "Kirbro", "Texture");
        credit(ModBlocks.NAMEK_DRAGON_BALL_7.get(), "Kirbro", "Texture");
        credit(ModItems.SHENLONG_SPAWN_EGG, "Kirbro", "Entity Model");
        credit(ModItems.ISAAC_SPAWN_EGG, "Kirbro", "Entity Model");
        credit(ModBlocks.NAMEKIAN_GRASS_BLOCK,"IxWolxz","Texture");
        credit(ModItems.SCOUTER,"IxWolxz","Texture");
    }

    /** Registra el crédito de un item o del BlockItem de un bloque. */
    private void credit(ItemLike itemLike, String author, String detail) {
        Item item = itemLike.asItem();
        builder(ModDataMaps.MODEL_CREDITS)
                .add(item.builtInRegistryHolder().key(), new ModDataMaps.ModelCredit(author, detail), false);
    }
}