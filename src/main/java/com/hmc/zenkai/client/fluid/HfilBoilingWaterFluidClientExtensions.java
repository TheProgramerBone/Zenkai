package com.hmc.zenkai.client.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Render del agua hirviendo del HFIL: texturas de agua vanilla + tinte azul cobalto lechoso,
 * el color de la poza real "Umi Jigoku" de Beppu. Mismo patrón que
 * HealingWaterFluidClientExtensions (ningún PNG de fluido nuevo). Es justo lo que hace falta
 * en el HFIL: el agua NORMAL allí se tiñe de rojo por bioma (waterColor), así que solo un fluido
 * propio puede verse azul dentro de esos biomas.
 */
public final class HfilBoilingWaterFluidClientExtensions implements IClientFluidTypeExtensions {
    public static final HfilBoilingWaterFluidClientExtensions INSTANCE = new HfilBoilingWaterFluidClientExtensions();

    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    private HfilBoilingWaterFluidClientExtensions() {}

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOW;
    }

    @Override
    public int getTintColor() {
        return 0xFF2F7BE8; // ARGB, azul cobalto
    }
}
