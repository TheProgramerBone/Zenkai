package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
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
        double ticks = mc.level.getGameTime() + e.getPartialTick();

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