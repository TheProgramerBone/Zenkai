package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Anillo de progreso de la transformación en curso (mantener la tecla), centrado en pantalla.
 *
 * APARTE del icono de la fila de badges en ClientZenkaiHooks (ICON_TRANSFORMING) a propósito:
 * el icono dice QUÉ está pasando y convive con el resto de badges en su fila fija; este anillo
 * dice CUÁNTO falta y es lo único que importa mirar mientras se sostiene la tecla, así que
 * merece ser grande y estar donde ya está la mirada — sobre la cruceta — en vez de compartir
 * hueco con un badge de 20x20.
 *
 * El progreso sale de holdTicks (ya sincronizado cada tick por PlayerFormAttachment) partido
 * por currentHoldRequired(), que resuelve el MISMO destino que el servidor (kaioken > potencial
 * > forma) para que el anillo nunca mienta sobre cuánto queda de verdad.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TransformGaugeOverlay {
    private TransformGaugeOverlay() {}

    private static final float R_OUT = 34f;
    private static final float R_IN  = 28f;

    /** Tono neutro del cascarón VACÍO: no se tiñe con el color de la forma destino a propósito,
     *  igual que el cascarón de KiChargeGaugeOverlay tampoco es azul-ki de fondo. Lo vacío es
     *  "nada todavía"; el color que importa es el que va llenando el relleno. */
    private static final int C_SHELL = 0x8A8A90;

    /** Tono de respaldo del RELLENO cuando, por lo que sea, no hay aura_rgb que leer (datapack
     *  sin ese campo o forma desaparecida a mitad de hold). Gris y no un color propio: no
     *  debería aparecer nunca en juego normal, así que si se ve es evidente que es el caso de
     *  respaldo y no una forma real. */
    private static final int C_FALLBACK = 0xAAAAAA;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        PlayerFormAttachment form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        if (!form.isTransforming()) return;

        Race race = PlayerStatsAttachment.get(mc.player).getRace();
        PlayerFormAttachment.HoldTarget target = form.currentHoldTarget(mc.player, race);
        if (target.isEmpty()) return;

        float progress = form.getHoldTicks() / (float) target.holdTicks();
        int tone = target.auraRgb() >= 0 ? target.auraRgb() : C_FALLBACK;

        GuiGraphics g = e.getGuiGraphics();
        float cx = g.guiWidth() / 2f;
        float cy = g.guiHeight() / 2f;

        RadialGauge.ring(g, cx, cy, R_IN, R_OUT, progress, C_SHELL, tone);
    }
}
