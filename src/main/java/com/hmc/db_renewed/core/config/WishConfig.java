package com.hmc.db_renewed.core.config;

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

    public static int villagerBookBasePrice() { return 10; }
    public static int villagerBookPricePerLevel() { return 5; }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW =
            BUILDER.comment("Overrides the amount of the item given with this format: 'namespace:item=count'. Ex: minecraft:diamond=32")
                    .defineListAllowEmpty("stack_overrides",
                            List.of("minecraft:ender_pearl=32"),
                            obj -> true // Acepta cualquier string
                    );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW =
            BUILDER.comment("List of items banned from wishes. Ex: minecraft:shulker_box")
                    .defineListAllowEmpty("banned_items",
                            List.of("minecraft:shulker_box"),
                            obj -> true // Acepta cualquier string
                    );

    public static final ModConfigSpec.BooleanValue ALLOW_ABOVE_MAX =
            BUILDER.comment("Allow Overrides above max (ex: get 64 ender pearls instead of the max stack size of 16).")
                    .define("allow_overrides_above_max", true);

    public static final ModConfigSpec.IntValue GLOBAL_HARD_CAP =
            BUILDER.comment("Global hard cap override (security).")
                    .defineInRange("global_hard_cap", 4096, 1, 65535);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Map<ResourceLocation, Integer> STACK_OVERRIDES = Collections.emptyMap();
    private static volatile Set<ResourceLocation> BANNED_ITEMS = Collections.emptySet();

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

    // === LÓGICA FINAL: aplica overrides con las nuevas opciones ===
    public static ItemStack resolveWishStack(ItemStack chosen) {
        if (chosen == null || chosen.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());
        if (isBanned(id)) return ItemStack.EMPTY;

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