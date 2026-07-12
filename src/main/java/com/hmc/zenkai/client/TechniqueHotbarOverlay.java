package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.technique.KiTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Overlay del modo combate: columna de 9 posiciones a la derecha (íconos, número de tecla,
 * nombre de la seleccionada) + estados:
 *
 *  - COOLDOWN: velo oscuro que "baja" con el tiempo restante en cada celda.
 *  - CARGA: barra horizontal bajo la mira con el color de la técnica, borde blanco al
 *    pasar del 25% (ya se puede soltar) y % numérico.
 *  - DEFENSA: ícono de icons.png centrado (ajusta BLOCK_ICON_U/V a tu celda).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TechniqueHotbarOverlay {
    private TechniqueHotbarOverlay() {}

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int BLOCK_ICON_U = 3 * 20; // ⚠ AJUSTA a la celda real de tu ícono
    private static final int BLOCK_ICON_V = 20;

    private static final int CELL = 20;
    private static final int GAP = 2;
    private static final int MARGIN_RIGHT = 4;

    private static final int CHARGE_W = 62;
    private static final int CHARGE_H = 5;

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

        // ── Columna derecha ──
        int n = PlayerTechniques.BIND_POSITIONS;
        int totalH = n * CELL + (n - 1) * GAP;
        int x = g.guiWidth() - MARGIN_RIGHT - CELL;
        int y = (g.guiHeight() - totalH) / 2;

        for (int pos = 0; pos < n; pos++) {
            int slotIdx = tech.binding(pos);
            KiTechnique t = tech.slot(slotIdx);
            boolean sel = (pos == selected);

            g.fill(x, y, x + CELL, y + CELL, 0xA0000000);
            if (t != null) {
                TechniqueIcons.draw(g, x, y, t);

                // Cooldown: velo que baja con el tiempo restante.
                double cd = CombatModeClientState.cooldownFraction(mc, slotIdx);
                if (cd > 0) {
                    int h = (int) Math.ceil(CELL * cd);
                    g.fill(x, y + CELL - h, x + CELL, y + CELL, 0xB0101010);
                }
            }

            int border = sel ? 0xFFFFFFFF : 0x60FFFFFF;
            g.fill(x, y, x + CELL, y + 1, border);
            g.fill(x, y + CELL - 1, x + CELL, y + CELL, border);
            g.fill(x, y + 1, x + 1, y + CELL - 1, border);
            g.fill(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, border);

            g.drawString(mc.font, Component.literal(String.valueOf(pos + 1)),
                    x + 2, y + 1, sel ? 0xFFFFFFFF : 0xFFAAAAAA, true);

            if (sel && t != null) {
                Component name = Component.literal(t.name());
                g.drawString(mc.font, name,
                        x - 6 - mc.font.width(name), y + (CELL - 8) / 2, 0xFFFFFFFF, true);
            }

            y += CELL + GAP;
        }

        // ── Barra de CARGA central (bajo la mira) ──
        if (CombatModeClientState.isCharging()) {
            KiTechnique t = att.techniques().slot(CombatModeClientState.chargingSlot());
            double ratio = CombatModeClientState.chargeRatio(mc);
            int rgb = t != null ? t.rgb() : 0xFFFFFF;

            int bx = g.guiWidth() / 2 - CHARGE_W / 2;
            int by = g.guiHeight() / 2 + 12;

            boolean releasable = ratio >= com.hmc.zenkai.core.technique.KiTechniqueType.MIN_CHARGE;
            g.fill(bx - 1, by - 1, bx + CHARGE_W + 1, by + CHARGE_H + 1,
                    releasable ? 0xFFFFFFFF : 0x80FFFFFF); // borde: blanco pleno al pasar el 25%
            g.fill(bx, by, bx + CHARGE_W, by + CHARGE_H, 0xC0000000);
            int fill = (int) Math.round(CHARGE_W * ratio);
            if (fill > 0) {
                g.fill(bx, by, bx + fill, by + CHARGE_H, 0xFF000000 | rgb);
            }
            // Marca del 25% mínimo.
            int minX = bx + (int) (CHARGE_W * com.hmc.zenkai.core.technique.KiTechniqueType.MIN_CHARGE);
            g.fill(minX, by, minX + 1, by + CHARGE_H, 0xFFFFFFFF);

            g.drawCenteredString(mc.font,
                    Component.literal((int) Math.round(ratio * 100) + "%"),
                    g.guiWidth() / 2, by + CHARGE_H + 3, 0xFFFFFFFF);
        }

        // ── Ícono de DEFENSA (centro de pantalla) mientras se bloquea ──
        if (CombatModeClientState.isBlockingLocal()) {
            g.blit(ICONS_TEX, g.guiWidth() / 2 - 10, g.guiHeight() / 2 - 10 - 16,
                    BLOCK_ICON_U, BLOCK_ICON_V, 20, 20, 270, 270);
        }
    }
}