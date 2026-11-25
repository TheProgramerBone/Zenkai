package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sets de armadura de Zenkai y utilidades de patrón. El id del set es el que comparten el patrón (componente
 * {@code zenkai:armor_pattern}), las recetas {@code zenkai:armor_fabrication}, el modelo {@code geo/armor/<set>} y los ítems
 * {@code <set>_<pieza>}. Un addon añade un set nuevo con sus recetas, sus ítems y su nombre de lang ({@code armor_set.<ns>.<path>}).
 * NO confundir: patrón = diseño (qué armadura); plantilla = atributos (reiniciar, copiar, transferir, aumentar; Fase 5c).
 *
 * Arte definitivo (2026-11-25): cada set sale de una hoja de referencia de 4 vistas de
 * {@code .claude/pendiente/referencias_armaduras} con la skill {@code armor-from-reference}. Los tres sets provisionales sobre
 * el modelo vanilla (saiyan_armor de 4 piezas, namek_clothes, training_gi) se BORRARON sin migración (decisión del usuario:
 * la 0.7.0 no está publicada).
 */
public final class ArmorSets {
    private ArmorSets() {}

    /**
     * Clase del set (decisión del usuario 2026-11-25, sobre el diseño de la Fabricadora y la Infusora). La clase decide el
     * material de fabricación, la protección de partida y el Ki para imbuir sin infundir; el poder lo pone la infusión (los
     * sets de Zenkai van en {@code zenkai:infusion_uncapped}, su tope sale solo de la infusión).
     */
    public enum Clase {
        /** Armadura de batalla: de lingote de katchin, a la altura del diamante, Ki Súper para imbuir. */
        BATALLA,
        /** Ropa y gi: de tela de katchin, a la altura del hierro, Ki Normal para imbuir. */
        ROPA
    }

    /**
     * Un set. {@code piezas} = las que dibuja la hoja: sin tocado no hay casco (decisión del usuario 2026-11-25) y sus
     * puntos de protección se reparten entre las otras tres ({@code ModArmorMaterials}).
     */
    public record Def(ResourceLocation id, Clase clase, List<ArmorItem.Type> piezas) {
        public boolean tieneCasco() { return piezas.contains(ArmorItem.Type.HELMET); }
    }

    private static final List<ArmorItem.Type> SIN_CASCO =
            List.of(ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS);

    /** Armadura Saiyan estándar (ArmaduraSaiyanEstandar.png). */
    public static final Def SAIYAN_ARMOR = new Def(id("saiyan_armor"), Clase.BATALLA, SIN_CASCO);
    /** Gi de Piccolo, morado con fajín azul (Picokoro2.png): el básico Namek. */
    public static final Def PICCOLO_GI = new Def(id("piccolo_gi"), Clase.ROPA, SIN_CASCO);

    /** Todos los sets, en el orden del creativo. */
    public static final List<Def> ALL = List.of(SAIYAN_ARMOR, PICCOLO_GI);

    /**
     * Los que salen por receta (papel + tintes); el resto, de loot (decisión del usuario 2026-11-25: Saiyan, Piccolo 2 y Goku 3,
     * este último todavía sin modelo).
     */
    public static final List<ResourceLocation> BASIC = List.of(SAIYAN_ARMOR.id(), PICCOLO_GI.id());

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path); }

    /** Patrón de ese set, listo para el creativo, el loot o un gametest. */
    public static ItemStack pattern(ResourceLocation set) {
        ItemStack s = new ItemStack(ModItems.ARMOR_PATTERN.get());
        s.set(ModDataComponents.ARMOR_PATTERN.get(), set);
        return s;
    }

    public static ItemStack pattern(Def set) { return pattern(set.id()); }

    @Nullable
    public static ResourceLocation setOf(ItemStack pattern) {
        return pattern.isEmpty() ? null : pattern.get(ModDataComponents.ARMOR_PATTERN.get());
    }

    public static Component name(ResourceLocation set) {
        return Component.translatable("armor_set." + set.getNamespace() + "." + set.getPath());
    }
}
