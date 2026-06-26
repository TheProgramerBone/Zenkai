package com.hmc.zenkai.core.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;
import java.util.stream.Collectors;

public class WishConfig {
    private WishConfig() {}

    /** Tipos de deseo, para habilitar/deshabilitar y cualquier lógica genérica. */
    public enum WishType { STACK, REVIVE_PLAYER, ENCHANT_VILLAGER, IMMORTAL, TRAINING_POINTS }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Toggles por deseo ──────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLE_STACK;
    public static final ModConfigSpec.BooleanValue ENABLE_REVIVE_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANT_VILLAGER;
    public static final ModConfigSpec.BooleanValue ENABLE_IMMORTAL;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAINING_POINTS;

    // ── Stack wish ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW;
    public static final ModConfigSpec.BooleanValue ALLOW_ABOVE_MAX;
    public static final ModConfigSpec.IntValue GLOBAL_HARD_CAP;

    // ── Enchant villager ───────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue VILLAGER_BOOK_BASE_PRICE;
    public static final ModConfigSpec.IntValue VILLAGER_BOOK_PRICE_PER_LEVEL;

    // ── Stack whitelist (7) ─────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST_ITEMS_RAW;

    // ── Training points (3) ─────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue TRAINING_POINTS_AMOUNT;

    // ── Invocación: cooldown por jugador (1) + deseos por entidad (2) ───────────
    public static final ModConfigSpec.IntValue SUMMON_COOLDOWN_DAYS;
    public static final ModConfigSpec.IntValue SHENLONG_WISHES;
    public static final ModConfigSpec.IntValue PORUNGA_WISHES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Habilita o deshabilita cada deseo de Shenlong de forma individual.")
                .push("wishes_enabled");
        ENABLE_STACK            = BUILDER.comment("Deseo: stack de ítems.").define("stack", true);
        ENABLE_REVIVE_PLAYER    = BUILDER.comment("Deseo: revivir jugador.").define("revive_player", true);
        ENABLE_ENCHANT_VILLAGER = BUILDER.comment("Deseo: aldeano con encantamiento.").define("enchant_villager", true);
        ENABLE_IMMORTAL         = BUILDER.comment("Deseo: inmortalidad.").define("immortal", true);
        ENABLE_TRAINING_POINTS  = BUILDER.comment("Deseo: puntos de entrenamiento (TP).").define("training_points", true);
        BUILDER.pop();

        BUILDER.push("stack_wish");
        STACK_OVERRIDES_RAW = BUILDER
                .comment("Overrides the amount of the item given with this format: 'namespace:item=count'. Ex: minecraft:diamond=32")
                .defineListAllowEmpty("stack_overrides",
                        List.of("minecraft:ender_pearl=32"),
                        obj -> true);
        BANNED_ITEMS_RAW = BUILDER
                .comment("List of items banned from wishes. Ex: minecraft:shulker_box")
                .defineListAllowEmpty("banned_items",
                        List.of("minecraft:shulker_box"),
                        obj -> true);
        WHITELIST_ITEMS_RAW = BUILDER
                .comment("Whitelist de ítems permitidos en el deseo de stack.",
                        "Si está VACÍA, se permiten todos salvo la blacklist.",
                        "Si tiene ítems, SOLO se permiten esos (la blacklist sigue teniendo prioridad).")
                .defineListAllowEmpty("whitelist_items",
                        List.of(),
                        obj -> true);
        ALLOW_ABOVE_MAX = BUILDER
                .comment("Allow Overrides above max (ex: get 64 ender pearls instead of the max stack size of 16).")
                .define("allow_overrides_above_max", true);
        GLOBAL_HARD_CAP = BUILDER
                .comment("Global hard cap override (security).")
                .defineInRange("global_hard_cap", 4096, 1, 65535);
        BUILDER.pop();

        BUILDER.push("enchant_villager");
        VILLAGER_BOOK_BASE_PRICE = BUILDER
                .comment("Precio base (emeralds) del libro encantado del aldeano.")
                .defineInRange("base_price", 10, 0, 9999);
        VILLAGER_BOOK_PRICE_PER_LEVEL = BUILDER
                .comment("Precio extra por nivel de encantamiento.")
                .defineInRange("price_per_level", 5, 0, 9999);
        BUILDER.pop();

        BUILDER.push("training_points");
        TRAINING_POINTS_AMOUNT = BUILDER
                .comment("Cantidad de Training Points otorgados por el deseo.")
                .defineInRange("amount", 1000, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("summoning");
        SUMMON_COOLDOWN_DAYS = BUILDER
                .comment("Cooldown POR JUGADOR para invocar de nuevo, en días de juego.",
                        "0 = sin cooldown (útil para tests).")
                .defineInRange("cooldown_days", 7, 0, 100000);
        SHENLONG_WISHES = BUILDER
                .comment("Cuántos deseos concede Shenlong por invocación (pool compartido; se agota).")
                .defineInRange("shenlong_wishes", 3, 1, 100);
        PORUNGA_WISHES = BUILDER
                .comment("Cuántos deseos concede Porunga por invocación.")
                .defineInRange("porunga_wishes", 3, 1, 100);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // ── API de toggles ──────────────────────────────────────────────────────────
    public static boolean isEnabled(WishType w) {
        return switch (w) {
            case STACK            -> ENABLE_STACK.get();
            case REVIVE_PLAYER    -> ENABLE_REVIVE_PLAYER.get();
            case ENCHANT_VILLAGER -> ENABLE_ENCHANT_VILLAGER.get();
            case IMMORTAL         -> ENABLE_IMMORTAL.get();
            case TRAINING_POINTS  -> ENABLE_TRAINING_POINTS.get();
        };
    }

    // ── Precios villager (ahora desde config) ────────────────────────────────────
    public static int villagerBookBasePrice()     { return VILLAGER_BOOK_BASE_PRICE.get(); }
    public static int villagerBookPricePerLevel()  { return VILLAGER_BOOK_PRICE_PER_LEVEL.get(); }

    private static volatile Map<ResourceLocation, Integer> STACK_OVERRIDES = Collections.emptyMap();
    private static volatile Set<ResourceLocation> BANNED_ITEMS = Collections.emptySet();
    private static volatile Set<ResourceLocation> WHITELIST_ITEMS = Collections.emptySet();

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        Map<ResourceLocation, Integer> parsedOverrides = new HashMap<>();
        for (String line : STACK_OVERRIDES_RAW.get()) {
            if (line == null || !line.contains("=")) continue;
            String[] parts = line.split("=", 2);
            if (parts.length != 2) continue;

            try {
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;

                int raw = Integer.parseInt(parts[1].trim());
                int count = Math.max(1, raw); // no limitamos aquí; el clamp final se hace en resolve
                parsedOverrides.put(id, count);
            } catch (Exception ignored) {
                // silencioso
            }
        }
        STACK_OVERRIDES = Collections.unmodifiableMap(parsedOverrides);

        Set<ResourceLocation> parsedBanned = new HashSet<>();
        for (String rawId : BANNED_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(rawId.trim());
                if (id != null) parsedBanned.add(id);
            } catch (Exception ignored) {
                // silencioso
            }
        }
        BANNED_ITEMS = Collections.unmodifiableSet(parsedBanned);

        Set<ResourceLocation> parsedWhitelist = new HashSet<>();
        for (String rawId : WHITELIST_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(rawId.trim());
                if (id != null) parsedWhitelist.add(id);
            } catch (Exception ignored) {
            }
        }
        WHITELIST_ITEMS = Collections.unmodifiableSet(parsedWhitelist);
    }

    public static Map<ResourceLocation, Integer> getStackOverrides() {
        return STACK_OVERRIDES;
    }

    public static boolean isBanned(ResourceLocation id) {
        return BANNED_ITEMS.contains(id);
    }

    public static Set<ResourceLocation> getBannedItems() {
        return BANNED_ITEMS;
    }

    /** Whitelist: vacía => permitido (salvo blacklist). Con ítems => solo esos. */
    public static boolean isAllowedByWhitelist(ResourceLocation id) {
        return WHITELIST_ITEMS.isEmpty() || WHITELIST_ITEMS.contains(id);
    }

    // ── Helpers de balance ──────────────────────────────────────────────────────
    public static int trainingPointsAmount()    { return TRAINING_POINTS_AMOUNT.get(); }
    public static int summonCooldownDays()       { return SUMMON_COOLDOWN_DAYS.get(); }
    public static long summonCooldownTicks()     { return (long) SUMMON_COOLDOWN_DAYS.get() * 24000L; }
    public static int shenlongWishCount()        { return SHENLONG_WISHES.get(); }
    public static int porungaWishCount()           { return PORUNGA_WISHES.get(); }

    // === LÓGICA FINAL: aplica overrides con las nuevas opciones ===
    public static ItemStack resolveWishStack(ItemStack chosen) {
        if (chosen == null || chosen.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());
        if (isBanned(id)) return ItemStack.EMPTY;
        if (!isAllowedByWhitelist(id)) return ItemStack.EMPTY;

        int override = STACK_OVERRIDES.getOrDefault(id, -1);
        int desired = (override > 0) ? override : chosen.getMaxStackSize();

        // clamp final según config
        int hardCap = Math.max(1, GLOBAL_HARD_CAP.get());
        if (ALLOW_ABOVE_MAX.get()) {
            desired = Math.min(desired, hardCap);
        } else {
            desired = Math.min(desired, Math.min(hardCap, chosen.getMaxStackSize()));
        }

        ItemStack copy = chosen.copy(); // copia NBT
        copy.setCount(desired);
        return copy;
    }

    public static String overridesToString() {
        return STACK_OVERRIDES.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}