package com.hmc.zenkai.client.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Render del agua curativa: reutiliza las texturas de agua vanilla (still/flow) y solo
 * cambia el tinte, verde/cian curativo — distinto del rojo sangre del HFIL — para no
 * necesitar ningún PNG de fluido nuevo.
 */
public final class HealingWaterFluidClientExtensions implements IClientFluidTypeExtensions {
    public static final HealingWaterFluidClientExtensions INSTANCE = new HealingWaterFluidClientExtensions();

    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    private HealingWaterFluidClientExtensions() {}

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
        return 0xFF3FE0B0; // ARGB, verde-cian curativo
    }
}
