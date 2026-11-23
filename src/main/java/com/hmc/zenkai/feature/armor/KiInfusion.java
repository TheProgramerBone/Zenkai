package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Infusión de Ki del material de las armaduras (Fase 5c de la 0.7.0, §3.4 de pendientes-0.7.0).
 *
 * La infusora mete un LINGOTE de katchin con el fluido de Ki de un tipo y lo saca con el componente
 * {@code zenkai:ki_infusion} = ese tipo. Solo se infunde el lingote (decisión 2026-11-16): el hilo y la tela que se
 * hacen con él heredan el tipo ({@link InfusedCraftingRecipe}) y la armadura que fabrica la máquina con ellos también
 * (ArmorFabricatorBlockEntity). Qué da (decidido): MÁS DURABILIDAD y MENOS coste de FE y fluido al imbuir; el aspecto
 * no cambia. El katchin sin infundir sigue valiendo para fabricar (decisión del usuario 2026-11-22): la infusión es
 * una mejora, no un requisito.
 *
 * Tipo de Ki para imbuir una pieza infundida = el de su infusión (decisión del usuario 2026-11-22: «el fluido con el
 * que se hizo el material»); manda sobre la receta {@code zenkai:armor_imbuing} y sobre el tipo automático.
 *
 * Mezcla de tipos (fabricar o craftear con materiales distintos): sale el tipo MÁS BAJO, y si alguno no estaba
 * infundido sale sin infundir. Así no compensa mezclar un lingote bueno con ocho malos.
 *
 * Cifras de partida «moderadas» elegidas por el usuario el 2026-11-22; constantes por la regla de pocas opciones.
 */
public final class KiInfusion {
    private KiInfusion() {}

    /** Ítems que la infusora acepta (por defecto solo el lingote de katchin; ampliable por datapack). */
    public static final TagKey<Item> INFUSABLE =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_infusable"));
    /** Ítems que llevan la infusión de un paso al siguiente (lingote, hilo y tela). */
    public static final TagKey<Item> CARRIERS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_infusion_carriers"));

    /** mB de fluido del tipo elegido por cada lingote infundido. */
    public static final int MB_PER_ITEM = 250;
    /** FE por lingote, repartida en {@link #TICKS}. */
    public static final int FE_PER_ITEM = 10_000;
    /** Ticks por lingote sin mejoras de velocidad (5 s). */
    public static final int TICKS = 100;

    /** Tipo de infusión del stack, o null si no está infundido. */
    @Nullable
    public static KiEnergyType of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModDataComponents.KI_INFUSION.get());
    }

    /** Copia del stack infundida con ese tipo. */
    public static ItemStack infused(ItemStack stack, KiEnergyType type) {
        ItemStack out = stack.copy();
        out.set(ModDataComponents.KI_INFUSION.get(), type);
        return out;
    }

    /** Multiplicador de durabilidad de la armadura fabricada con material de ese tipo. */
    public static double durabilityMult(@Nullable KiEnergyType t) {
        if (t == null) return 1.0;
        return switch (t) {
            case BASE -> 1.25;
            case SUPER -> 1.5;
            case DIVINE -> 2.0;
            case LEGENDARY -> 2.5;
        };
    }

    /** Multiplicador del coste de imbuir (FE y fluido; la sangre no cambia) de una pieza de ese tipo. */
    public static double imbueCostMult(@Nullable KiEnergyType t) {
        if (t == null) return 1.0;
        return switch (t) {
            case BASE -> 0.9;
            case SUPER -> 0.8;
            case DIVINE -> 0.65;
            case LEGENDARY -> 0.5;
        };
    }

    /** Aplica el descuento de la infusión de la pieza a un coste (hacia arriba, nunca por debajo de 1 si había coste). */
    public static long discounted(long cost, @Nullable KiEnergyType t) {
        if (cost <= 0) return cost;
        return Math.max(1L, (long) Math.ceil(cost * imbueCostMult(t) - 1e-9));
    }

    /**
     * Pone la infusión en una armadura recién fabricada y sube su durabilidad. Sin tipo, la deja tal cual. La durabilidad
     * va en el componente MAX_DAMAGE del propio stack: vanilla la respeta en el desgaste y en la barra.
     */
    public static ItemStack applyToArmor(ItemStack piece, @Nullable KiEnergyType t) {
        if (t == null || piece.isEmpty()) return piece;
        ItemStack out = infused(piece, t);
        int max = out.getMaxDamage();
        if (max > 0) out.set(DataComponents.MAX_DAMAGE, (int) Math.round(max * durabilityMult(t)));
        return out;
    }

    /**
     * Acumulador del tipo más bajo de un conjunto de materiales: {@link #add} por cada ítem portador; {@link #result}
     * da el más bajo, o null si alguno no estaba infundido (o no hubo ninguno).
     */
    public static final class Lowest {
        private KiEnergyType lowest;
        private boolean any, plain;

        public void add(ItemStack stack) {
            if (stack.isEmpty()) return;
            any = true;
            KiEnergyType t = of(stack);
            if (t == null) { plain = true; return; }
            if (lowest == null || t.ordinal() < lowest.ordinal()) lowest = t;
        }

        @Nullable
        public KiEnergyType result() { return !any || plain ? null : lowest; }
    }
}
