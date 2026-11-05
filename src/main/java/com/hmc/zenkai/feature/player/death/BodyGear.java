package com.hmc.zenkai.feature.player.death;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Lo que se VE de un cuerpo: la armadura puesta y los objetos de las manos. Es una foto (copias) para dibujar, nunca el
 * inventario: se refresca desde el contenedor del cuerpo cada vez que cambia, así que si alguien le quita la armadura o la
 * espada, deja de verse. La mano principal es la casilla de la barra que el jugador tenía seleccionada al morir.
 */
public record BodyGear(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, ItemStack hand, ItemStack off) {
    public static final BodyGear EMPTY = new BodyGear(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

    public boolean isEmpty() {
        return head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty() && hand.isEmpty() && off.isEmpty();
    }

    /** Para viajar en el adjunto del que lo lleva (ItemStack.OPTIONAL_LIST_STREAM_CODEC). */
    public List<ItemStack> toList() { return List.of(head, chest, legs, feet, hand, off); }

    public static BodyGear fromList(List<ItemStack> l) {
        if (l == null || l.size() < 6) return EMPTY;
        return new BodyGear(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5));
    }
}
