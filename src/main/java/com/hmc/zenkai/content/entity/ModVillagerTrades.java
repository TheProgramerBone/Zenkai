package com.hmc.zenkai.content.entity;

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
 * Tradeos del aldeano namekiano.
 * Convenciones:
 *  - buy(...)  = el jugador PAGA y RECIBE algo.
 *  - sellFor(...) = el jugador VENDE algo y recibe esmeraldas.
 *  - priceMultiplier 0.05 = poca inflación; 0.0 = precio fijo (ideal para ítems clave).
 *  - Temática Namekian: agua, curación (Dende/Guru), naturaleza y esferas del dragón.
 * Están agrupados por NIVEL de profesión (1..5). Si tu registro usa un array plano,
 */
public final class ModVillagerTrades {
    private ModVillagerTrades() {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Comprar: pagar `cost` x`costN` -> recibir `result` x`resultN`. */
    private static VillagerTrades.ItemListing buy(ItemLike cost, int costN,
                                                  ItemLike result, int resultN,
                                                  int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(cost, costN),
                new ItemStack(result, resultN),
                maxUses, xp, mult);
    }

    /** Comprar con DOS costos (p. ej. esmeraldas + un ítem). */
    private static VillagerTrades.ItemListing buy2(ItemLike costA, int aN,
                                                   ItemLike costB, int bN,
                                                   ItemLike result, int resultN,
                                                   int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(costA, aN),
                Optional.of(new ItemCost(costB, bN)),
                new ItemStack(result, resultN),
                maxUses, xp, mult);
    }

    /** Vender: entregar `give` x`giveN` -> recibir esmeraldas x`emeralds`. */
    private static VillagerTrades.ItemListing sellFor(ItemLike give, int giveN,
                                                      int emeralds,
                                                      int maxUses, int xp, float mult) {
        return (Entity e, RandomSource r) -> new MerchantOffer(
                new ItemCost(give, giveN),
                new ItemStack(Items.EMERALD, emeralds),
                maxUses, xp, mult);
    }

    // ── Tradeos por nivel ───────────────────────────────────────────────────────

    /** Nivel 1 — Novato: agua, comida y compra de recursos básicos. */
    public static final VillagerTrades.ItemListing[] LEVEL_1 = {
            // Namekians "viven del agua": vende agua barata y compra cultivos.
            buy(Items.EMERALD, 1, Items.WATER_BUCKET, 1, 16, 2, 0.05f),
            sellFor(Items.MELON_SLICE, 12, 1, 16, 2, 0.05f),
            sellFor(Items.KELP, 16, 1, 16, 2, 0.05f),
            // TODO: comprar tu semilla/planta namekiana:
            // sellFor(ModItems.AJISSA_SEED.get(), 8, 1, 16, 2, 0.05f),
    };

    /** Nivel 2 — Aprendiz: curación básica (Dende sana). */
    public static final VillagerTrades.ItemListing[] LEVEL_2 = {
            buy(Items.EMERALD, 3, Items.GLISTERING_MELON_SLICE, 4, 12, 5, 0.05f),
            buy2(Items.EMERALD, 2, Items.GLASS_BOTTLE, 1, Items.HONEY_BOTTLE, 1, 12, 5, 0.05f),
            sellFor(Items.LAPIS_LAZULI, 10, 1, 12, 5, 0.05f),
    };

    /** Nivel 3 — Oficial: pociones de curación y materiales raros. */
    public static final VillagerTrades.ItemListing[] LEVEL_3 = {
            // Poción de curación embotellada (Dende). Precio fijo por ser clave.
            buy(Items.EMERALD, 6, Items.GLOWSTONE, 4, 8, 10, 0.0f),
            buy(Items.EMERALD, 5, Items.ENDER_PEARL, 2, 8, 10, 0.05f),
            // TODO: vender un ítem curativo del mod (p. ej. semilla senzu limitada):
            // buy(Items.EMERALD, 32, ModItems.SENZU_BEAN.get(), 1, 2, 15, 0.0f),
    };

    /** Nivel 4 — Experto: cosas relacionadas con las esferas del dragón. */
    public static final VillagerTrades.ItemListing[] LEVEL_4 = {
            buy(Items.EMERALD, 12, Items.GOLD_BLOCK, 1, 6, 15, 0.05f),
            // TODO: pistas/fragmentos del sistema de esferas del dragón, radar, etc.:
            // buy2(Items.EMERALD, 24, Items.DIAMOND, 2, ModItems.DRAGON_RADAR.get(), 1, 2, 20, 0.0f),
    };

    /** Nivel 5 — Maestro: objeto de prestigio caro y limitado. */
    public static final VillagerTrades.ItemListing[] LEVEL_5 = {
            buy(Items.EMERALD, 16, Items.EXPERIENCE_BOTTLE, 6, 8, 30, 0.05f),
            // TODO: recompensa top del mod (armadura namekiana, esfera, etc.):
            // buy2(Items.EMERALD, 48, Items.DIAMOND, 4, ModItems.NAMEKIAN_RELIC.get(), 1, 1, 30, 0.0f),
    };

    // ── Utilidades de consumo ────────────────────────────────────────────────────

    /** Devuelve los tradeos de un nivel (1..5); vacío si fuera de rango. */
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

    /** Todos juntos, por si tu registro usa un único array plano. */
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