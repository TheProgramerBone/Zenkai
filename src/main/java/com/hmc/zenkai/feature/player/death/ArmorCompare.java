package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.registry.ModTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * ¿Es una armadura claramente mejor que otra para esa ranura? Lo usa «take_all» para intercambiar la que lleva el jugador
 * por la del cuerpo (la suya iría al inventario).
 *
 * PUNTUACIÓN: suma de los modificadores de atributo que el objeto da EN ESA RANURA ({@code ItemStack.forEachModifier}), el
 * sistema estándar de NeoForge 1.21, así que sirve para vanilla y para cualquier mod que declare sus atributos de forma
 * estándar: armadura + 0,5 x dureza + 5 x resistencia al retroceso (un trozo de netherite da 0,1 = 0,5 puntos). Empate:
 * más durabilidad restante y, si sigue el empate, más encantamientos. No ve lo que un mod haga por eventos (bonus de set).
 *
 * NUNCA se retira lo que lleva puesto el jugador si no es una armadura normal o está en {@code zenkai:no_armor_swap}: el scouter
 * y las pesas son armaduras de verdad que ocupan casco y peto cuando no hay Curios, y una pechera de hierro no debe quitarlos.
 */
public final class ArmorCompare {
    private ArmorCompare() {}

    private static final double TOUGHNESS_WEIGHT = 0.5, KNOCKBACK_WEIGHT = 5.0, EPS = 1e-6;

    public static double score(ItemStack stack, EquipmentSlot slot) {
        double[] sum = new double[1];
        stack.forEachModifier(slot, (attr, mod) -> {
            if (mod.operation() != AttributeModifier.Operation.ADD_VALUE) return;
            if (attr.equals(Attributes.ARMOR)) sum[0] += mod.amount();
            else if (attr.equals(Attributes.ARMOR_TOUGHNESS)) sum[0] += mod.amount() * TOUGHNESS_WEIGHT;
            else if (attr.equals(Attributes.KNOCKBACK_RESISTANCE)) sum[0] += mod.amount() * KNOCKBACK_WEIGHT;
        });
        return sum[0];
    }

    private static double durabilityLeft(ItemStack s) {
        return s.isDamageableItem() ? 1.0 - (double) s.getDamageValue() / s.getMaxDamage() : 1.0;
    }

    /** ¿Se puede retirar {@code worn} del jugador para poner otra? Solo una armadura normal que no esté en la etiqueta. */
    public static boolean replaceable(ItemStack worn) {
        return worn.getItem() instanceof ArmorItem && !worn.is(ModTags.Items.NO_ARMOR_SWAP);
    }

    /** ¿Es {@code candidate} ESTRICTAMENTE mejor que {@code worn} en esa ranura? */
    public static boolean better(ItemStack candidate, ItemStack worn, EquipmentSlot slot) {
        if (!(candidate.getItem() instanceof ArmorItem a) || a.getEquipmentSlot() != slot || !replaceable(worn)) return false;
        double ds = score(candidate, slot) - score(worn, slot);
        if (Math.abs(ds) > EPS) return ds > 0;
        double dd = durabilityLeft(candidate) - durabilityLeft(worn);
        if (Math.abs(dd) > 0.001) return dd > 0;
        return candidate.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).size()
                > worn.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).size();
    }
}
