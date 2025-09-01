package com.hmc.db_renewed.config;

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

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Lista cruda en el .toml (ej: "minecraft:ender_pearl=16")
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW =
            BUILDER.comment("Overrides de stacks en formato 'namespace:item=count'. Ej: minecraft:ender_pearl=16")
                    .defineListAllowEmpty("stack_overrides",
                            List.of("minecraft:ender_pearl=16"),
                            WishConfig::validateOverride);

    // Lista cruda de ítems prohibidos
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW =
            BUILDER.comment("Lista de items prohibidos para deseos. Ej: minecraft:shulker_box")
                    .defineListAllowEmpty("banned_items",
                            List.of("minecraft:shulker_box"),
                            obj -> obj instanceof String s && !s.trim().isEmpty());

    // Especificación final
    public static final ModConfigSpec SPEC = BUILDER.build();

    // Datos parseados y cacheados
    private static volatile Map<ResourceLocation, Integer> STACK_OVERRIDES = Collections.emptyMap();
    private static volatile Set<ResourceLocation> BANNED_ITEMS = Collections.emptySet();

    // --- Validadores ---
    private static boolean validateOverride(final Object obj) {
        if (!(obj instanceof String s)) return false;
        String line = s.trim();
        if (line.isEmpty() || !line.contains("=")) return false;
        String[] parts = line.split("=", 2);
        if (parts.length != 2) return false;

        String idStr = parts[0].trim();
        String valStr = parts[1].trim();
        try {
            ResourceLocation id = ResourceLocation.parse(idStr);
            if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
            int v = Integer.parseInt(valStr);
            return v >= 1 && v <= 64;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Eventos ---
    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        // Procesar overrides
        Map<ResourceLocation, Integer> parsedOverrides = new HashMap<>();
        for (String line : STACK_OVERRIDES_RAW.get()) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty() || !s.contains("=")) continue;
            String[] parts = s.split("=", 2);
            try {
                ResourceLocation id = ResourceLocation.parse(parts[0].trim());
                int count = Math.max(1, Integer.parseInt(parts[1].trim()));
                parsedOverrides.put(id, count);
            } catch (Exception ignored) {}
        }
        STACK_OVERRIDES = Collections.unmodifiableMap(parsedOverrides);

        // Procesar items prohibidos
        Set<ResourceLocation> parsedBanned = new HashSet<>();
        for (String rawId : BANNED_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.parse(rawId.trim());
                parsedBanned.add(id);
            } catch (Exception ignored) {}
        }
        BANNED_ITEMS = Collections.unmodifiableSet(parsedBanned);
    }

    // --- Getters ---
    public static Map<ResourceLocation, Integer> getStackOverrides() {
        return STACK_OVERRIDES;
    }

    public static Set<ResourceLocation> getBannedItems() {
        return BANNED_ITEMS;
    }

    // --- Helpers ---
    public static String overridesToString() {
        return STACK_OVERRIDES.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public static ItemStack resolveWishStack(ItemStack chosen) {
        if (chosen.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());

        // Si está prohibido
        if (BANNED_ITEMS.contains(id)) {
            return ItemStack.EMPTY;
        }

        // Cantidad configurada o el máximo permitido por el ítem
        int count = STACK_OVERRIDES.getOrDefault(id, chosen.getMaxStackSize());
        return new ItemStack(chosen.getItem(), count);
    }
}
