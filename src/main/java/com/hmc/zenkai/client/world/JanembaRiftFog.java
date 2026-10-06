package com.hmc.zenkai.client.world;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.registry.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Niebla con profundidad en el Janemba Rift (referencia de Fusion Reborn): el horizonte de la
 * película es un degradado naranja en el que las torres y crestas lejanas se van desvaneciendo.
 * Vanilla solo empieza la niebla en el último ~10 % de la distancia de render, así que lo lejano
 * se cortaba en seco contra el cielo. Aquí, dentro del bioma, la niebla del terreno empieza mucho
 * antes; el color ya lo pone el bioma (fogColor naranja en ModBiomeGen.hfilJanembaRift).
 *
 * Se mezcla en el tiempo ({@link #blend}) para no saltar de golpe al cruzar el borde del bioma.
 * Con un shaderpack activo no se toca: el pack gestiona su propia niebla.
 *
 * NeoForge solo aplica las distancias del evento si se CANCELA (ClientHooks.onFogRender).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class JanembaRiftFog {
    private JanembaRiftFog() {}

    /** Dónde empieza y dónde acaba la niebla, en fracción de la distancia de render. Con 0.22 (primera
     *  versión) el fondo era un naranja plano en las capturas del 2026-10-05 y el cielo rojo de la
     *  referencia casi no se veía. */
    private static final float NEAR_FRACTION = 0.45f;
    private static final float FAR_FRACTION = 0.95f;
    /** Segundos para pasar de la niebla vanilla a la del bioma (y de vuelta). */
    private static final float BLEND_SECONDS = 3.0f;

    private static float blend;
    private static long lastNanos;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1.0e9f, 0.25f);
        lastNanos = now;
        boolean inside = !IrisCompat.shaderPackActive()
                && mc.level.getBiome(event.getCamera().getBlockPosition()).is(ModBiomes.HFIL_JANEMBA_RIFT);
        float step = dt / BLEND_SECONDS;
        blend = inside ? Math.min(1f, blend + step) : Math.max(0f, blend - step);
        if (blend <= 0f) return;

        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        event.setNearPlaneDistance(lerp(near, far * NEAR_FRACTION, blend));
        event.setFarPlaneDistance(lerp(far, far * FAR_FRACTION, blend));
        event.setCanceled(true);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
