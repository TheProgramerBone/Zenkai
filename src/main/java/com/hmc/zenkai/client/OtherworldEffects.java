package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Efectos de cielo del Otherworld: idénticos a los del Overworld salvo que NO hay nubes de vainilla
 * (altura de nubes = NaN, igual que el Nether y el End). El mar de nubes propio de la dimensión es
 * terreno generado (CloudLayerFeature) y no depende de esto. El id lo referencia
 * data/zenkai/dimension_type/otherworld_type.json en "effects".
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class OtherworldEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "otherworld");

    private OtherworldEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // Misma fórmula que OverworldEffects.
        return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F,
                brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new OtherworldEffects());
    }
}
