package com.hmc.db_renewed.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;
import java.util.stream.Collectors;

import java.util.*;

public class WishConfig {
    private WishConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW =
            BUILDER.comment("Overrides of stacks with this format: 'namespace:item=count'. Ex: minecraft:diamond=32")
                    .defineListAllowEmpty("stack_overrides",
                            List.of("minecraft:ender_pearl=16"),
                            obj -> true // Acepta cualquier string
                    );

    // Lista cruda de items baneados: puede contener cualquier string
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW =
            BUILDER.comment("List of items banned from stack wish. Ex: minecraft:shulker_box")
                    .defineListAllowEmpty("banned_items",
                            List.of("minecraft:shulker_box"),
                            obj -> true // Acepta cualquier string
                    );

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

                int count = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                parsedOverrides.put(id, count);
            } catch (Exception ignored) {
                // Ignorar errores de parseo sin crashear
            }
        }
        STACK_OVERRIDES = Collections.unmodifiableMap(parsedOverrides);

        Set<ResourceLocation> parsedBanned = new HashSet<>();
        for (String rawId : BANNED_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(rawId.trim());
                // AHORA: solo aceptamos IDs que existan en el registry
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    parsedBanned.add(id);
                }
            } catch (Exception ignored) {
                // Silencioso
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

    public static ItemStack resolveWishStack(ItemStack chosen) {
        if (chosen == null || chosen.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());
        if (isBanned(id)) return ItemStack.EMPTY;

        // El override aplica SIEMPRE, pero respetando maxStackSize
        int desired = STACK_OVERRIDES.getOrDefault(id, chosen.getMaxStackSize());
        int max = chosen.getMaxStackSize();

        ItemStack copy = chosen.copy(); // preserva NBT (encantamientos, etc.)
        copy.setCount(Math.max(1, Math.min(desired, max)));
        return copy;
    }

    public static String overridesToString() {
        return STACK_OVERRIDES.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}