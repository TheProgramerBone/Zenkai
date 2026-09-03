package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.event.ClientZenkaiHooks;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.teleport.InstantTransmissionAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Ícono FIJO sobre la mira mientras TAB está pulsado — reemplaza al anillo de progreso que
 * tenía esta pantalla antes (RadialGauge, ver git history de este archivo). Mismo lenguaje que
 * `TechniqueHotbarOverlay.renderBlockIcon` (el ícono de bloqueo que aparece sobre la cruceta
 * mientras se bloquea): un badge fijo pegado a la mira en vez de un elemento colocable, porque
 * es información del INSTANTE que se lee sin apartar la vista — mismo criterio, pedido explícito
 * del usuario ("haz algo similar a lo del bloqueo con el ícono de instant transmisión").
 *
 * El ícono en sí NO se dibuja aquí: se pinta llamando a
 * {@link ClientZenkaiHooks#drawInstantTransmissionIcon}, el MISMO ícono que ya usa el badge de
 * cooldown del HUD — pedido explícito del usuario ("que utilizara el mismo ícono que el de
 * ClientZenkaiHooks"). Antes esta clase llevaba su propia copia de la celda del atlas (u/v a
 * mano) y las dos se desincronizaron la primera vez que esa celda se movió; con un único punto
 * de dibujo compartido eso ya no puede volver a pasar.
 *
 * Se muestra con solo tener la skill (nivel 1+), no solo desde que el menú se desbloquea
 * (nivel 3+): desde la ronda de "clic derecho para blinkear" el gesto de TAB ya es relevante en
 * cualquier nivel (confirmar con clic derecho), así que el ícono avisa de que el gesto está
 * activo aunque el jugador no vaya a poder armar el menú todavía.
 *
 * Único matiz visual que sobrevive del anillo viejo: un marco fino dorado alrededor del ícono en
 * cuanto el hold lleva lo suficiente QUIETO como para haber armado el menú
 * (InstantTransmissionAttachment.MENU_ARM_TICKS) — mismo dato servidor-autoritativo de siempre
 * (InstantTransmissionClientState.stillTicks(), sincronizado por InstantTransmissionSystem), solo
 * que ahora se lee como un umbral discreto en vez de un progreso continuo. Un MARCO, no un
 * reteñido del propio ícono (RenderSystem.setShaderColor multiplicando el texel): el ícono es
 * arte pintado real (piel + puño), no una silueta blanca/gris — multiplicarlo por un tinte lo
 * dejaría descolorido en vez de leerse como "listo".
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class InstantTransmissionCrosshairOverlay {
    private InstantTransmissionCrosshairOverlay() {}

    private static final int ICON_SIZE = 20;

    /** Tono del aviso "armado": mismo dorado que el resto del mod usa para "listo/disponible". */
    private static final int C_ARMED = 0xFFFFD24A;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!KeyBindings.INSTANT_TRANSMISSION.isDown()) return;
        if (SkillEffects.instantTransmissionLevel(mc.player) <= 0) return;

        GuiGraphics g = e.getGuiGraphics();
        int x = g.guiWidth() / 2 - ICON_SIZE / 2;
        int y = g.guiHeight() / 2 - ICON_SIZE / 2 - 16;

        boolean armed = SkillEffects.instantTransmissionMenuUnlocked(mc.player)
                && InstantTransmissionClientState.stillTicks() >= InstantTransmissionAttachment.MENU_ARM_TICKS;
        if (armed) drawFrame(g, x, y);

        ClientZenkaiHooks.drawInstantTransmissionIcon(g, x, y);
    }

    /** Marco de 1px alrededor del ícono, sin tocar sus píxeles (ver el javadoc de clase). */
    private static void drawFrame(GuiGraphics g, int x, int y) {
        int x0 = x - 1, y0 = y - 1, x1 = x + ICON_SIZE + 1, y1 = y + ICON_SIZE + 1;
        g.fill(x0, y0, x1, y0 + 1, C_ARMED);
        g.fill(x0, y1 - 1, x1, y1, C_ARMED);
        g.fill(x0, y0, x0 + 1, y1, C_ARMED);
        g.fill(x1 - 1, y0, x1, y1, C_ARMED);
    }
}
