package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Overlay del modo combate.
 *
 * DOS BLOQUES CON REGLAS DISTINTAS, y la diferencia es deliberada:
 *
 *  - La BARRA DE TÉCNICAS es colocable: ancla, orientación y desplazamiento libre. Es una lista
 *    de referencia que cada jugador consulta con el rabillo del ojo, y dónde le resulta cómoda
 *    depende de su resolución, de su ratón y de qué otros mods lleve encima.
 *
 *  - La CARGA y el ICONO DE BLOQUEO se quedan clavados bajo la mira. Son información del
 *    instante —cuánto llevas cargado, si estás bloqueando— y se leen sin apartar la vista del
 *    enemigo. Moverlos a una esquina convertiría en un vistazo lo que ahora es visión
 *    periférica, y eso cambia cómo se juega, no solo cómo se ve.
 *
 * El dibujo de las celdas usa slot_hud.png, hermano oscuro del slot.png del menú: mismo marco
 * de 1 px, mismo bisel y mismas esquinas recortadas, pero con fondo translúcido oscuro. El del
 * menú es marrón sobre beige y sobre el mundo se perdería en cuanto el jugador mirase a la
 * arena o al cielo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TechniqueHotbarOverlay {
    private TechniqueHotbarOverlay() {}

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final ResourceLocation SLOT_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/slot_hud.png");

    private static final int SLOT_ATLAS_W = 60, SLOT_ATLAS_H = 20;
    private static final int ICON_INSET = 2;

    private static final int BLOCK_ICON_U = 3 * 20; // ⚠ AJUSTA a la celda real de tu ícono
    private static final int BLOCK_ICON_V = 20;

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
        TechniqueHudLayout layout = TechniqueHudLayout.current(g.guiWidth(), g.guiHeight());

        renderBar(g, mc, att, layout, CombatModeClientState.selected());
        renderCharge(g, mc, att);
        renderBlockIcon(g);
    }

    // ── Barra de técnicas ────────────────────────────────────────────────────

    /**
     * Dibuja las nueve celdas. Público y sin depender del estado de combate para que la
     * pantalla de colocación pinte EXACTAMENTE lo mismo que verá en juego: una previsualización
     * aproximada haría que el jugador colocase una cosa y se encontrase otra.
     */
    public static void renderBar(GuiGraphics g, Minecraft mc, PlayerStatsAttachment att,
                                 TechniqueHudLayout layout, int selected) {
        PlayerTechniques tech = att.techniques();
        int inner = TechniqueHudLayout.CELL - ICON_INSET * 2;

        for (int pos = 0; pos < PlayerTechniques.BIND_POSITIONS; pos++) {
            int x = layout.cellX(pos);
            int y = layout.cellY(pos);

            int slotIdx = tech.binding(pos);
            KiTechnique t = tech.slot(slotIdx);
            PhysicalTechnique ph = tech.physicalBinding(pos);
            boolean occupied = (t != null || ph != null);
            boolean sel = (pos == selected);

            // Estado del atlas: 0 vacía, 1 seleccionada, 2 ocupada.
            int u = sel ? TechniqueHudLayout.CELL
                    : occupied ? TechniqueHudLayout.CELL * 2 : 0;
            g.blit(SLOT_TEX, x, y, u, 0, TechniqueHudLayout.CELL, TechniqueHudLayout.CELL,
                    SLOT_ATLAS_W, SLOT_ATLAS_H);

            double cd = 0.0;
            if (t != null) {
                TechniqueIcons.draw(g, x + ICON_INSET, y + ICON_INSET, inner, t);
                cd = CombatModeClientState.cooldownFraction(mc, slotIdx);
            } else if (ph != null) {
                PhysicalIcons.draw(g, x + ICON_INSET, y + ICON_INSET, inner, ph);
                cd = CombatModeClientState.physCooldownFraction(mc, ph);
            }

            // El velo de enfriamiento baja DENTRO del marco, sin taparlo: si cubriera la celda
            // entera, una técnica en cooldown perdería el borde y parecería una ranura vacía.
            if (cd > 0) {
                int h = (int) Math.ceil((TechniqueHudLayout.CELL - 2) * cd);
                g.fill(x + 1, y + TechniqueHudLayout.CELL - 1 - h,
                        x + TechniqueHudLayout.CELL - 1, y + TechniqueHudLayout.CELL - 1,
                        0xB0101010);
            }

            g.drawString(mc.font, String.valueOf(pos + 1), x + 2, y + 1,
                    sel ? 0xFFFFFFFF : 0xFFAAAAAA, true);

            if (sel && occupied) {
                Component name = (t != null) ? Component.literal(t.name())
                        : Component.translatable(ph.nameKey());
                drawSelectedName(g, mc.font, layout, x, y, name);
            }
        }
    }

    /**
     * Nombre de la técnica seleccionada, colocado según el ancla.
     *
     * En vertical va al lado contrario del borde al que está pegada la barra; en horizontal, al
     * contrario en el eje Y. Sin esto, una barra anclada a la izquierda escribiría su nombre
     * hacia fuera de la pantalla, que es exactamente lo que hacía la versión anterior en cuanto
     * dejaba de estar a la derecha.
     */
    private static void drawSelectedName(GuiGraphics g, Font font, TechniqueHudLayout layout,
                                         int cellX, int cellY, Component name) {
        HudAnchor anchor = com.hmc.zenkai.config.ClientConfig.hudAnchor();
        int w = font.width(name);

        if (layout.horizontal()) {
            // Arriba si la barra está en la mitad inferior, abajo si está en la superior.
            int y = anchor.isTop()
                    ? layout.y() + layout.height() + 3
                    : layout.y() - font.lineHeight - 3;
            int x = cellX + TechniqueHudLayout.CELL / 2 - w / 2;
            x = Math.max(2, Math.min(x, g.guiWidth() - w - 2));
            g.drawString(font, name, x, y, 0xFFFFFFFF, true);
            return;
        }

        boolean toLeft = !anchor.isLeft();   // pegada a la derecha o al centro → texto a la izquierda
        int x = toLeft ? cellX - 6 - w : cellX + TechniqueHudLayout.CELL + 6;
        g.drawString(font, name, x, cellY + (TechniqueHudLayout.CELL - 8) / 2, 0xFFFFFFFF, true);
    }

    // ── Carga: FIJA bajo la mira ─────────────────────────────────────────────

    private static void renderCharge(GuiGraphics g, Minecraft mc, PlayerStatsAttachment att) {
        if (!CombatModeClientState.isCharging()) return;

        KiTechnique t = att.techniques().slot(CombatModeClientState.chargingSlot());
        double ratio = CombatModeClientState.chargeRatio(mc);
        int rgb = t != null ? t.rgb() : 0xFFFFFF;

        int bx = g.guiWidth() / 2 - CHARGE_W / 2;
        int by = g.guiHeight() / 2 + 12;

        boolean releasable = ratio >= KiTechniqueType.MIN_CHARGE;
        boolean over = ratio > 1.0;
        g.fill(bx - 1, by - 1, bx + CHARGE_W + 1, by + CHARGE_H + 1,
                over ? 0xFFFFD24A                                 // dorado: estás sobrecargando
                        : releasable ? 0xFFFFFFFF : 0x80FFFFFF);  // blanco pleno al pasar el 25%
        g.fill(bx, by, bx + CHARGE_W, by + CHARGE_H, 0xC0000000);

        int fill = (int) Math.round(CHARGE_W * Math.min(ratio, 1.0));
        if (fill > 0) g.fill(bx, by, bx + fill, by + CHARGE_H, 0xFF000000 | rgb);

        // Sobrecarga: SEGUNDA pasada clara sobre la barra ya llena, en vez de estirar la escala
        // a 0-200%. Así el tramo normal no pierde la mitad de resolución y la marca del 25%
        // sigue donde el jugador la tiene memorizada.
        if (over) {
            int oFill = (int) Math.round(CHARGE_W * (ratio - 1.0));
            if (oFill > 0) g.fill(bx, by, bx + oFill, by + CHARGE_H, 0xB0FFFFFF);
        }

        int minX = bx + (int) (CHARGE_W * KiTechniqueType.MIN_CHARGE);
        g.fill(minX, by, minX + 1, by + CHARGE_H, 0xFFFFFFFF);

        g.drawCenteredString(mc.font, Component.literal((int) Math.round(ratio * 100) + "%"),
                g.guiWidth() / 2, by + CHARGE_H + 3, over ? 0xFFFFD24A : 0xFFFFFFFF);

        if (t != null && !t.type().defensive()) {
            double dmg = previewDamage(mc, att, t, ratio);
            String dmgTxt = String.format("%.1f", dmg);
            if (t.type().count() > 1) dmgTxt = dmgTxt + " x" + t.type().count();
            g.drawCenteredString(mc.font, Component.literal(dmgTxt),
                    g.guiWidth() / 2, by + CHARGE_H + 13,
                    releasable ? (0xFF000000 | rgb) : 0xA0FFFFFF);
        }

        // Coste de ki EN VIVO (azul): crece con la carga igual que el daño, así el jugador no
        // adivina cuánto le va a costar soltar ahora mismo.
        if (t != null) {
            int fullCost = KiCombatServer.computeCost(att, t.type(), t.size(),
                    t.explosive() && !t.type().defensive());
            int cost = (int) Math.max(1, Math.ceil(
                    fullCost * KiCombatServer.chargeCostFactor(ratio) * att.powerFraction()));
            boolean affordable = att.getEnergy() >= cost;
            g.drawCenteredString(mc.font, Component.literal("◆ " + cost),
                    g.guiWidth() / 2, by + CHARGE_H + 23,
                    affordable ? 0xFF40A0FF : 0xFFFF5050);        // azul ki / rojo si no llega
        }
    }

    private static void renderBlockIcon(GuiGraphics g) {
        if (!CombatModeClientState.isBlockingLocal()) return;
        g.blit(ICONS_TEX, g.guiWidth() / 2 - 10, g.guiHeight() / 2 - 10 - 16,
                BLOCK_ICON_U, BLOCK_ICON_V, 20, 20, 256, 256);
    }

    /** Daño estimado del disparo actual (espeja KiFirePacket + KiCombatServer.computeDamage). */
    private static double previewDamage(Minecraft mc, PlayerStatsAttachment att,
                                        KiTechnique t, double ratio) {
        if (mc.player == null || t == null || t.type().defensive()) return 0;
        double kiPower = att.computeKiPowerFinal();
        return KiCombatServer.computeDamage(kiPower, t.type(), t.size()) * ratio;
    }
}