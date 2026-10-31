package com.hmc.zenkai.client.gui.screens;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * El ítem vanilla que identifica a cada deseo de Shenlong, en la lista y en la cabecera de su confirmación. Un
 * único sitio: si un deseo nuevo no tiene icono aquí cae en {@link #FALLBACK}, y cambiar uno es una línea.
 * Ítems y no estrellas ni números a propósito — escala con deseos nuevos y no necesita arte propio.
 */
public final class WishIcons {
    private WishIcons() {}

    public static ItemStack stack()           { return new ItemStack(Items.CHEST); }
    public static ItemStack revivePlayer()    { return new ItemStack(Items.TOTEM_OF_UNDYING); }
    public static ItemStack enchantVillager() { return new ItemStack(Items.ENCHANTED_BOOK); }
    public static ItemStack immortal()        { return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE); }
    public static ItemStack legendary()       { return new ItemStack(Items.NETHER_STAR); }
    public static ItemStack trainingPoints()  { return new ItemStack(Items.EXPERIENCE_BOTTLE); }
    public static ItemStack revivePet()       { return new ItemStack(Items.POPPY); }
    /** Para un deseo sin icono propio todavía. */
    public static final ItemStack FALLBACK = new ItemStack(Items.DRAGON_EGG);
}
