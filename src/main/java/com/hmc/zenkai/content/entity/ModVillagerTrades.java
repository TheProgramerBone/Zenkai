package com.hmc.zenkai.content.entity;

import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;

/**
 * Tradeos del namekiano.
 * La moneda es el CRISTAL DE NAMEK, no la esmeralda: la esmeralda no existe en Namek a
 * propósito, para que la economía del planeta sea suya y no un apéndice del overworld.
 * Convenciones:
 *  - buy(...)     = el jugador PAGA cristales y RECIBE algo.
 *  - sellFor(...) = el jugador ENTREGA material y RECIBE cristales.
 *  - priceMultiplier 0.05 = poca inflación; 0.0 = precio fijo, para lo que es clave.
 * Anclaje de precios: 1 diamante = 5 cristales. Lo demás cuelga de ahí. Vender carbón
 * rinde 0,06 cristales por unidad, así que comerciar nunca supera a minar; es un colchón
 * para cuando el jugador tiene de sobra de una cosa y le falta otra.
 */
public final class ModVillagerTrades {
    private ModVillagerTrades() {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemLike currency() { return ModItems.NAMEK_CRYSTAL.get(); }

    /** Comprar: pagar `crystals` -> recibir `result` x`resultN`. */
    private static VillagerTrades.ItemListing buy(int crystals,
                                                  ItemLike result, int resultN,
                                                  int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(currency(), crystals),
                new ItemStack(result, resultN),
                maxUses, xp, mult);
    }

    /** Comprar con un segundo coste además de los cristales. */
    private static VillagerTrades.ItemListing buy2(int crystals,
                                                   ItemLike costB, int bN,
                                                   ItemLike result, int resultN,
                                                   int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(currency(), crystals),
                Optional.of(new ItemCost(costB, bN)),
                new ItemStack(result, resultN),
                maxUses, xp, mult);
    }

    /** Vender: entregar `give` x`giveN` -> recibir cristales. */
    private static VillagerTrades.ItemListing sellFor(ItemLike give, int giveN,
                                                      int crystals,
                                                      int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(give, giveN),
                new ItemStack(currency().asItem(), crystals),
                maxUses, xp, mult);
    }

    // ── Tradeos por nivel ─────────────────────────────────────────────────────

    /** Nivel 1 — Novato: lo barato, para que la primera aldea ya sirva de algo. */
    public static final VillagerTrades.ItemListing[] LEVEL_1 = {
            buy(2, ModBlocks.AJISA_SAPLING.get(), 1, 16, 2, 0.05f),
            buy(2, ModBlocks.NAMEKIAN_SAND.get(), 8, 16, 2, 0.05f),
            sellFor(Items.COAL, 16, 1, 16, 2, 0.05f),
    };

    /** Nivel 2 — Aprendiz: la hierba medicinal y el hierro. */
    public static final VillagerTrades.ItemListing[] LEVEL_2 = {
            buy(3, ModItems.NAMEKIAN_HERB_SEEDS.get(), 2, 12, 5, 0.05f),
            sellFor(Items.RAW_IRON, 12, 2, 12, 5, 0.05f),
    };

    /** Nivel 3 — Oficial: material de construcción de templos. */
    public static final VillagerTrades.ItemListing[] LEVEL_3 = {
            buy(4, ModBlocks.SACRED_STONE_BLOCK.get(), 8, 12, 10, 0.05f),
            sellFor(Items.RAW_GOLD, 8, 3, 12, 10, 0.05f),
    };

    /** Nivel 4 — Experto: las variantes trabajadas y el diamante. */
    public static final VillagerTrades.ItemListing[] LEVEL_4 = {
            buy(5, ModBlocks.POLISHED_SACRED_STONE.get(), 8, 8, 15, 0.05f),
            buy(6, ModBlocks.SACRED_STONE_BRICKS.get(), 8, 8, 15, 0.05f),
            sellFor(Items.DIAMOND, 2, 10, 8, 15, 0.05f),
    };

    /**
     * Nivel 5 — Maestro. Precio fijo (mult 0.0) en los dos: son la meta del comercio y no
     * deben moverse por reputación ni por demanda.
     */
    public static final VillagerTrades.ItemListing[] LEVEL_5 = {
            buy(24, ModItems.ENERGY_CRYSTAL.get(), 1, 4, 30, 0.0f),
            buy(48, ModItems.SCOUTER_RADAR_UPGRADE.get(), 1, 2, 30, 0.0f),
    };

    // ── Utilidades de consumo ─────────────────────────────────────────────────

    public static VillagerTrades.ItemListing[] byLevel(int level) {
        return switch (level) {
            case 1 -> LEVEL_1;
            case 2 -> LEVEL_2;
            case 3 -> LEVEL_3;
            case 4 -> LEVEL_4;
            case 5 -> LEVEL_5;
            default -> new VillagerTrades.ItemListing[0];
        };
    }

    public static final VillagerTrades.ItemListing[] NAMEKIAN_TRADES = concat(
            LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5);

    private static VillagerTrades.ItemListing[] concat(VillagerTrades.ItemListing[]... arrays) {
        int n = 0;
        for (var a : arrays) n += a.length;
        VillagerTrades.ItemListing[] out = new VillagerTrades.ItemListing[n];
        int i = 0;
        for (var a : arrays) { System.arraycopy(a, 0, out, i, a.length); i += a.length; }
        return out;
    }
}