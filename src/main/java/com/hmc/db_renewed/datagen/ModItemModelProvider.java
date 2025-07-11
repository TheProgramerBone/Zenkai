package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, DragonBlockRenewed.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.SENZU_BEAN.get());
        basicItem(ModItems.WARENAI_CRYSTAL.get());
        basicItem(ModItems.WARENAI_CRYSTAL_DUST.get());

    }
}