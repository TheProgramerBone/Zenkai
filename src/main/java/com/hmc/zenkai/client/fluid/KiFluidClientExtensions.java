package com.hmc.zenkai.client.fluid;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Render de los cuatro fluidos de Ki. Una clase y una instancia por tipo: lo único que cambia
 * es el nombre del sprite. SIN tinte, al revés que el agua curativa: el color ya viene pintado
 * en ki_&lt;tipo&gt;_still/flow.png (regla R8 de la skill zenkai-tech-textures), y teñir encima
 * rompería la paleta cerrada de cada familia.
 */
public final class KiFluidClientExtensions implements IClientFluidTypeExtensions {

    private final ResourceLocation still;
    private final ResourceLocation flow;

    /** @param name id del fluido sin namespace: ki_base, ki_super, ki_divine o ki_legendary. */
    public KiFluidClientExtensions(String name) {
        this.still = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + name + "_still");
        this.flow = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + name + "_flow");
    }

    @Override
    public ResourceLocation getStillTexture() { return still; }

    @Override
    public ResourceLocation getFlowingTexture() { return flow; }
}
