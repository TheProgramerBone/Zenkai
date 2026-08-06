package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBiomes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Tags de bioma. Existe por un motivo concreto: rocky_wasteland es un bioma de overworld que
 * no estaba en minecraft:is_overworld, y ese tag es el que usan los AddFeaturesBiomeModifier
 * —el nuestro y el de cualquier otro mod— para repartir menas. Sin esto el bioma se queda
 * fuera de lo que se añada por tag y nadie se entera, porque no falla: simplemente no
 * genera.
 */
public class ModBiomeTagProvider extends TagsProvider<Biome> {

    public ModBiomeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                               @Nullable ExistingFileHelper existingFileHelper) {
        super(output, Registries.BIOME, registries, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(BiomeTags.IS_OVERWORLD).add(ModBiomes.ROCKY_WASTELAND);
    }
}