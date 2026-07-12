package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.technique.KiTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Overlay del modo combate: columna de 9 posiciones en el LADO DERECHO de la pantalla,
 * centrada verticalmente (cero manipulación del HUD vanilla -> compatible con otros mods).
 *
 *  - Cada posición muestra su número (tecla) y, si tiene técnica asignada, su ÍCONO
 *    (base explosiva + ícono del tipo teñido — TechniqueIcons). Vacía = caja oscura.
 *  - El resaltado es la selección PROPIA del overlay (teclas 1-9 en modo combate).
 *    El nombre de la asignada se dibuja a la izquierda de la posición seleccionada.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TechniqueHotbarOverlay {
    private TechniqueHotbarOverlay() {}

    private static final int CELL = 20;
    private static final int GAP = 2;
    private static final int MARGIN_RIGHT = 4;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!CombatModeClientState.isActive()) return;
        if (mc.player == null || mc.options.hideGui) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        if (!att.isRaceChosen()) return;

        GuiGraphics g = e.getGuiGraphics();
        PlayerTechniques tech = att.techniques();
        int selected = CombatModeClientState.selected();

        int n = PlayerTechniques.BIND_POSITIONS;
        int totalH = n * CELL + (n - 1) * GAP;
        int x = g.guiWidth() - MARGIN_RIGHT - CELL;
        int y = (g.guiHeight() - totalH) / 2;

        for (int pos = 0; pos < n; pos++) {
            KiTechnique t = tech.slot(tech.binding(pos));
            boolean sel = (pos == selected);

            g.fill(x, y, x + CELL, y + CELL, 0xA0000000);
            if (t != null) {
                TechniqueIcons.draw(g, x, y, t);
            }

            int border = sel ? 0xFFFFFFFF : 0x60FFFFFF;
            g.fill(x, y, x + CELL, y + 1, border);
            g.fill(x, y + CELL - 1, x + CELL, y + CELL, border);
            g.fill(x, y + 1, x + 1, y + CELL - 1, border);
            g.fill(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, border);

            // Número de la tecla (1-9), esquina superior izquierda.
            g.drawString(mc.font, Component.literal(String.valueOf(pos + 1)),
                    x + 2, y + 1, sel ? 0xFFFFFFFF : 0xFFAAAAAA, true);

            // Nombre de la asignada, a la izquierda de la posición seleccionada.
            if (sel && t != null) {
                Component name = Component.literal(t.name());
                g.drawString(mc.font, name,
                        x - 6 - mc.font.width(name), y + (CELL - 8) / 2, 0xFFFFFFFF, true);
            }

            y += CELL + GAP;
        }
    }
}