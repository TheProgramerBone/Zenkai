package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sets de armadura de la primera tanda (P14) y utilidades de patrón. Un set NO es un registro: es solo el id que comparten
 * el patrón (componente {@code zenkai:armor_pattern}) y las cuatro recetas {@code zenkai:armor_fabrication} que lo usan.
 * Un addon añade un set nuevo con sus recetas y su nombre de lang ({@code armor_set.<ns>.<path>}), sin código.
 * NO confundir: patrón = diseño (qué armadura); plantilla = atributos (reiniciar, copiar, transferir, aumentar; Fase 5c).
 */
public final class ArmorSets {
    private ArmorSets() {}

    public static final ResourceLocation SAIYAN_ARMOR = id("saiyan_armor");
    public static final ResourceLocation NAMEK_CLOTHES = id("namek_clothes");
    public static final ResourceLocation TRAINING_GI = id("training_gi");

    /** Los tres básicos, que salen por receta; en este orden en el creativo. */
    public static final List<ResourceLocation> BASIC = List.of(SAIYAN_ARMOR, NAMEK_CLOTHES, TRAINING_GI);

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path); }

    /** Patrón de ese set, listo para el creativo, el loot o un gametest. */
    public static ItemStack pattern(ResourceLocation set) {
        ItemStack s = new ItemStack(ModItems.ARMOR_PATTERN.get());
        s.set(ModDataComponents.ARMOR_PATTERN.get(), set);
        return s;
    }

    @Nullable
    public static ResourceLocation setOf(ItemStack pattern) {
        return pattern.isEmpty() ? null : pattern.get(ModDataComponents.ARMOR_PATTERN.get());
    }

    public static Component name(ResourceLocation set) {
        return Component.translatable("armor_set." + set.getNamespace() + "." + set.getPath());
    }
}
