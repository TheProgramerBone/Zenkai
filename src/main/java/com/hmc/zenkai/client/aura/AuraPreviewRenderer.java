package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.flame.AuraSystemDispatcher;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.AuraFormula;
import com.hmc.zenkai.feature.aura.AuraLod;
import com.hmc.zenkai.feature.aura.AuraModifier;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.hmc.zenkai.feature.aura.AuraState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Aura EN VIVO sobre el personaje del preview 3D del StyleSelectionScreen.
 *
 * El preview usa InventoryScreen.renderEntityInInventoryFollowsMouse, que dispara
 * RenderLivingEvent. La pantalla activa {@link #ACTIVE} SOLO alrededor de esa llamada;
 * durante el render de mundo la bandera está en false, así que no interfiere.
 *
 * Con el aura nueva elegida en la config dibuja el aura COMPUESTA de la forma del jugador
 * (AuraComposer.drawPreview: cuerpo, capa interior y cáscaras, sin componentes de mundo); con la
 * clásica, los faldones legacy de siempre.
 *
 * ESTADO FIJO A PROPÓSITO. Antes llamaba a AuraRenderer.drawAura, que usaba el estilo
 * por defecto. Ahora usa un AuraState constante (poder liberado, control alto, ki lleno,
 * sin kaioken) en vez del estado real del jugador: si dependiera de él, un maestro y un
 * novato verían previews distintas del MISMO color y el selector dejaría de servir para
 * comparar tintes.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraPreviewRenderer {
    private AuraPreviewRenderer() {}

    /** La pantalla la activa SOLO durante el render del preview. */
    public static boolean ACTIVE = false;
    /** Escala del aura en el preview. = AURA_SCALE del mundo -> proporción real. */
    public static float PREVIEW_SCALE = 1.30f;
    /** Escala del aura COMPUESTA en el preview (más alta que la legacy; ver onRenderLivingPost). */
    public static float COMPOSED_PREVIEW_SCALE = 0.8f;

    /**
     * Override de color EN VIVO para StyleSelectionScreen: mientras el jugador elige
     * Default/Custom y mueve el ColorPickerWidget, nada de eso está guardado todavía en el
     * attachment (solo se escribe en onConfirm), así que sin esto el preview seguiría mostrando
     * el color/alineamiento YA guardado en vez de lo que se está eligiendo ahora mismo. Ver
     * AuraColors.resolveLayers(Player, boolean, boolean, int) — forma/kaioken/majin del jugador
     * real siguen mandando igual que en juego, este override solo entra en el fallback final.
     */
    public static boolean colorOverrideActive = false;
    public static boolean colorOverrideCustom = false;
    public static int colorOverrideRgb = 0;

    private static final AuraState PREVIEW_STATE = AuraFormula.state(
            100_000L, 231L, 4_534_321L, 100, 100, 8, 1f, 0f, 0f);

    private static final AuraProfile PREVIEW_PROFILE =
            AuraFormula.profile(PREVIEW_STATE, AuraModifier.NONE);

    /** Perfil fijo de la vista previa con la firma de la forma y de la raza del jugador: el PODER
     *  sigue siendo el fijo (comparar tintes), pero la silueta es la de su forma. */
    private static AuraProfile previewProfile(AbstractClientPlayer p) {
        return AuraFormula.profile(PREVIEW_STATE, com.hmc.zenkai.feature.aura.AuraManager.modifiersOf(p));
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> e) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        if (p != mc.player) return;

        // color de ki en vivo: attachment ya guardado, salvo que la pantalla esté forzando el
        // estado que todavía no se ha confirmado (ver colorOverrideActive arriba).
        int rgb = colorOverrideActive
                ? AuraColors.resolve(p, colorOverrideCustom, colorOverrideRgb)
                : AuraClientState.resolveColor(p);
        double ticks = AuraClock.ticks() + e.getPartialTick();

        // AURA NUEVA (2026-09-30): la vista previa muestra la misma aura que el juego, la compuesta
        // de la familia de la forma, en vez de los faldones legacy. Solo con el aura clásica
        // elegida en la config se sigue dibujando la de siempre.
        if (AuraSystemDispatcher.useComposed()) {
            AuraColors.Layers layers = AuraColors.resolveLayers(p);
            // El override en vivo del selector solo cambia el tinte "suelto"; forma y Kaioken
            // siguen mandando (mismo criterio que AuraColors.resolve).
            int inner = rgb;
            int outer = layers.hasOuter() ? layers.outer() : rgb;
            // Primero se dibuja el personaje (su lote sigue en el búfer) y el aura encima: si no,
            // el cuerpo translúcido del aura quedaba tapado por el modelo entero.
            if (e.getMultiBufferSource() instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
            PoseStack pose = e.getPoseStack();
            pose.pushPose();
            // El aura compuesta es más alta que los faldones: a escala real la corona se salía por
            // arriba del recuadro de la vista previa (capturas del 2026-09-30).
            pose.scale(COMPOSED_PREVIEW_SCALE, COMPOSED_PREVIEW_SCALE, COMPOSED_PREVIEW_SCALE);
            com.hmc.zenkai.client.aura.compose.AuraComposer.drawPreview(pose, p,
                    previewProfile(p), inner, outer, layers.hasOuter(), (float) (ticks / 20.0));
            pose.popPose();
            return;
        }

        // Distancia 0 y banda NEAR forzada: en una GUI queremos el detalle completo.
        AuraSkirts.Plan plan =
                AuraSkirts.plan(PREVIEW_PROFILE, 0.0, AuraLod.NEAR, rgb, -1);
        if (plan.isEmpty()) return;

        MultiBufferSource buffers = e.getMultiBufferSource();
        PoseStack pose = e.getPoseStack();

        pose.pushPose();
        pose.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
        // Sin atenuación frontal: en el preview no hay que proteger la lectura de nadie.
        AuraSkirtRenderer.render(pose, buffers, plan, ticks,
                (float) (ticks / 20.0), p.getId(), Float.NaN, Float.NaN);
        pose.popPose();
        // El flush lo hace renderEntityInInventoryFollowsMouse al terminar.
    }
}