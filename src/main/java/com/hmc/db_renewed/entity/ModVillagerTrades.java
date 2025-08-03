package com.hmc.db_renewed.entity;

import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public class ModVillagerTrades {
    public static final VillagerTrades.ItemListing[] NAMEKIAN_TRADES = new VillagerTrades.ItemListing[]{
            (entity, random) -> new MerchantOffer(
                    new ItemCost(new ItemStack(Items.EMERALD, 3).getItem()),
                    new ItemStack(Items.BLAZE_ROD, 1),
                    10, // maxUses
                    5,  // xpValue
                    0.05f // priceMultiplier
            ),
            (entity, random) -> new MerchantOffer(
                    new ItemCost(new ItemStack(Items.GOLD_INGOT, 4).getItem()),
                    new ItemStack(Items.ENDER_PEARL, 2),
                    5,
                    3,
                    0.05f
            )
    };
}