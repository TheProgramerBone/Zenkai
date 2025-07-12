package com.hmc.db_renewed.item;

import com.hmc.db_renewed.util.ModTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class ModToolTiers {
    public static final Tier WARENAI_CRYSTAL = new SimpleTier(ModTags.Blocks.INCORRECT_FOR_WARENAI_CRYSTAL_TOOL,
            905, 7.0F, 2.5F, 15, ()-> Ingredient.of(ModItems.WARENAI_CRYSTAL));
}
