package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Tipo de madera del ajisa.
 *
 * BlockSetType define los sonidos de puerta, trampilla, botón y placa de presión; WoodType
 * lo envuelve y es lo que necesitarán los carteles en la fase 2b.
 *
 * Los dos se registran en un mapa estático de vainilla, NO en un DeferredRegister, así que
 * hay que tocar esta clase desde el constructor del mod ANTES de que se registren los
 * bloques: la puerta y la trampilla piden el BlockSetType en su constructor.
 */
public final class ModWoodTypes {
    private ModWoodTypes() {}

    public static final BlockSetType AJISA_SET =
            BlockSetType.register(new BlockSetType(Zenkai.MOD_ID + ":ajisa"));

    public static final WoodType AJISA =
            WoodType.register(new WoodType(Zenkai.MOD_ID + ":ajisa", AJISA_SET));

    /** Fuerza la carga de la clase. Llamar desde el constructor del mod. */
    public static void init() {}
}