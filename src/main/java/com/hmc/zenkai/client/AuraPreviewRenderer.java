package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Muestra el aura EN VIVO sobre el personaje del preview 3D del StyleSelectionScreen.
 *
 * El preview usa InventoryScreen.renderEntityInInventoryFollowsMouse, que dibuja al jugador
 * y dispara RenderLivingEvent. La pantalla pone {@link #ACTIVE}=true SOLO alrededor de esa
 * llamada; este hook detecta esa pasada y dibuja el cono con el color de ki actual (que el
 * ColorPickerWidget actualiza en vivo en el attachment). En el mundo NO interfiere: durante
 * el render de mundo la bandera está en false.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraPreviewRenderer {
    private AuraPreviewRenderer() {}

    /** La pantalla la activa SOLO durante el render del preview. */
    public static boolean ACTIVE = false;
    /** Escala del aura en el preview (menor que en el mundo para caber en el recuadro). */
    public static float PREVIEW_SCALE = 1.30f; // = AURA_SCALE del mundo → proporción real

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> e) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        if (p != mc.player) return; // solo el propio jugador del preview

        int rgb = AuraClientState.resolveColor(p); // color de ki en vivo (attachment)
        long t = mc.level.getGameTime();
        float pt = e.getPartialTick();

        MultiBufferSource buffers = e.getMultiBufferSource();
        PoseStack pose = e.getPoseStack();
        // drawAura elige aditivo/oscuro y hace el crossfade igual que en el mundo.
        AuraRenderer.drawAura(pose, buffers, rgb, PREVIEW_SCALE, t, pt, p.getId());
        // El flush lo hace renderEntityInInventoryFollowsMouse al terminar (endBatch global).
    }
}