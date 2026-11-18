package com.hmc.zenkai.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Puente para que {@code KiMotorBlock} (común, solo con Create) abra la pantalla sin nombrar
 * Minecraft ni Screen en su propio cuerpo: así esa clase no arrastra nada de cliente al
 * cargarse en un servidor dedicado. Solo se llama desde la rama {@code level.isClientSide}.
 */
public final class KiMotorScreenOpener {
    private KiMotorScreenOpener() {}

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new KiMotorScreen(pos.immutable()));
    }
}
