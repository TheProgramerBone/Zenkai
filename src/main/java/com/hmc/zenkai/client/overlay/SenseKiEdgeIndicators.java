package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Indicadores en el borde de la pantalla hacia lo percibido que queda fuera de vista (Ki Sense
 * nivel 1+: «una marca de dirección cuando queda fuera de la vista»). Responde a "¿hacia dónde?"
 * sin decir "¿a cuántos bloques?": solo dirección, con el color por propiedad del eco. Reutiliza la
 * flecha del lock-on (LockOnMarkerRenderer#drawEdgeArrow), que se calcula con los vectores de la
 * cámara y no depende del GUI Scale.
 *
 * Sigue el orden de prioridad de SenseKiClientState.select (no la distancia a secas): quien te fija
 * o un jefe lejano pasan antes que el mob de al lado. Tope de flechas para que un enjambre no
 * emborrone el borde. El objetivo fijado y los avisos del lock-on ya tienen su propia flecha.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SenseKiEdgeIndicators {
    private SenseKiEdgeIndicators() {}

    private static final int MAX_ARROWS = 6;
    private static final int ARROW_SIZE = 12;
    private static final float ALPHA = 0.8f;
    /** Quien te fija late más vivo en el borde: es una presión, no un dato. */
    private static final float THREAT_PULSE_SPEED = 0.45f;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (SenseKiClientState.mode() == SenseKiMode.OFF) return;
        int level = SenseKiClientState.level();
        if (level < 1) return;
        List<SenseKiPresence> sel = SenseKiClientState.selected();
        if (sel.isEmpty()) return;

        GuiGraphics g = e.getGuiGraphics();
        Camera cam = mc.gameRenderer.getMainCamera();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = mc.level.getGameTime() + pt;
        long now = System.nanoTime();
        float fade = SenseKiClientState.fadeIn();
        int lockedId = LockOnClientState.targetId();

        int drawn = 0;
        for (SenseKiPresence p : sel) { // ya viene de mayor a menor prioridad
            if (drawn >= MAX_ARROWS) break;
            if (p.id == lockedId || LockOnClientState.alerts().contains(p.id)) continue;
            if (p.id == mc.player.getId()) continue;

            SenseKiDataPacket.Entry en = p.entry;
            float a = ALPHA * fade * p.lifeAlpha(now);
            if (level >= 4 && en.has(SenseKiDataPacket.FLAG_TARGETING_YOU)) {
                a *= 0.7f + 0.3f * Mth.sin(time * THREAT_PULSE_SPEED);
            }
            int rgb = SenseKiOverlayRenderer.colorOf(en, level, false);
            int argb = (Mth.clamp((int) (a * 255f), 0, 255) << 24) | rgb;
            if (LockOnMarkerRenderer.drawEdgeArrow(g, mc, cam, p.anchor(mc, pt, now), argb, ARROW_SIZE)) drawn++;
        }
    }
}
