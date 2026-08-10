package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.BindBar;
import com.hmc.zenkai.client.gui.widgets.SlotCell;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechniquePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pestaña Técnicas Físicas: lista predefinida desbloqueable por TP + asignación a las nueve
 * posiciones (compartidas con las ki).
 * QUÉ CAMBIA:
 *  - Comparte BindBar con la pestaña ki. Antes cada una construía su propia fila de celdas con
 *    constantes duplicadas, y en la física los íconos de 20x20 sobre celdas de 20x20 tapaban
 *    los números de posición por completo.
 *  - Las técnicas bloqueadas NO mostraban absolutamente nada más que su nombre y el precio.
 *    El jugador tenía que pagar 300 TP para descubrir qué hacía "Barrage". Ahora cada fila
 *    muestra daño, coste de estamina, enfriamiento y alcance, estén desbloqueadas o no: es
 *    información que ya existe en el datapack y que decide la compra.
 *  - Las técnicas sin JSON se listaban en la fila de dibujo (bucle sobre values() completo)
 *    pero no en la de widgets (que filtraba por enabled()), así que el nombre aparecía sin su
 *    botón y las filas siguientes quedaban desplazadas medio renglón respecto a sus botones.
 *    Ahora hay UNA lista de técnicas visibles y los dos bucles la recorren.
 *  - El botón de desbloqueo apagado no decía por qué. Ahora el requisito de MND que no se
 *    cumple se pinta en rojo, y el TP también.
 */
public class PhysicalScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 34;
    private static final int BAR_Y_OFF  = CONTENT_TOP + 24;
    private static final int LIST_Y_OFF = BAR_Y_OFF + SlotCell.SIZE + 16;
    private static final int TEXT_X_OFF = 16;
    private static final int ICON = 18;
    private static final int X_SIZE = 12;

    private static final ResourceLocation TEX_X =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");
    private static final ResourceLocation TEX_X_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x_highlight.png");

    /** Técnica armada para asignar, o null. */
    private PhysicalTechnique assigning = null;
    private int builtUnlocked = -1;

    private BindBar bindBar;
    /** Técnicas con JSON activo. Fuente ÚNICA para widgets y dibujo. */
    private final List<PhysicalTechnique> visible = new ArrayList<>();

    public PhysicalScreen() {
        super(Component.translatable(ZenkaiTab.PHYSICAL_TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.PHYSICAL_TECHNIQUES; }

    private int rightEdge() { return panelLeft + BG_W - 14; }
    private int rowTop(int i) { return panelTop + LIST_Y_OFF + i * ROW_H; }

    @Override
    protected void initContent() {
        if (att == null) return;
        PlayerTechniques tech = att.techniques();

        visible.clear();
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (t.enabled()) visible.add(t);
        }
        builtUnlocked = countUnlocked();

        // ── Barra de las nueve posiciones ──
        bindBar = BindBar.create(
                panelLeft + (BG_W - BindBar.WIDTH) / 2, panelTop + BAR_Y_OFF,
                att,
                () -> assigning != null,
                this::onPositionClicked,
                this::addRenderableWidget);

        // ── Filas ──
        for (int i = 0; i < visible.size(); i++) {
            final PhysicalTechnique t = visible.get(i);
            int y = rowTop(i) + 4;

            if (!tech.isUnlocked(t)) {
                TextOnlyButton unlock = new TextOnlyButton(rightEdge() - 96, y, 96, 16,
                        t.mindReq() > 0
                                ? Component.translatable("screen.zenkai.physical.unlock_mnd",
                                t.tpCost(), t.mindReq())
                                : Component.translatable("screen.zenkai.physical.unlock", t.tpCost()),
                        () -> PacketDistributor.sendToServer(PhysicalTechniquePacket.unlock(t)))
                        .textColors(ZenkaiPalette.VALUE, ZenkaiPalette.TEXT_HOVER, ZenkaiPalette.DENIED);
                unlock.active = canAfford(t);
                addRenderableWidget(unlock);
            } else {
                addRenderableWidget(new TextOnlyButton(rightEdge() - 96, y, 60, 16,
                        Component.translatable(assigning == t
                                ? "screen.zenkai.physical.assigning"
                                : "screen.zenkai.physical.assign"),
                        () -> {
                            assigning = (assigning == t) ? null : t;
                            rebuildWidgets();
                        })
                        .textColors(ZenkaiPalette.TEXT, ZenkaiPalette.TEXT_HOVER, ZenkaiPalette.TEXT_OFF));

                if (tech.positionOf(t) >= 0) {
                    // La ✖ del mod, no el glifo Unicode: el "✖" dependía de la fuente instalada
                    // y desentonaba con el resto de iconos.
                    addRenderableWidget(new TextOnlyButton(
                            rightEdge() - X_SIZE, y + (16 - X_SIZE) / 2, X_SIZE, X_SIZE,
                            Component.empty(), TEX_X, TEX_X_HL,
                            () -> {
                                att.techniques().bindPhysical(-1, t);           // optimista
                                PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(t, -1));
                                rebuildWidgets();
                            }));
                }
            }
        }
    }

    private boolean canAfford(PhysicalTechnique t) {
        return att != null && att.getTP() >= t.tpCost()
                && att.getAttribute(ZenkaiAttributes.MIND) >= t.mindReq();
    }

    private void onPositionClicked(int pos) {
        if (att == null) return;
        if (assigning != null) {
            att.techniques().bindPhysical(pos, assigning);                      // optimista
            PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(assigning, pos));
            assigning = null;
            rebuildWidgets();
        } else {
            PhysicalTechnique occ = att.techniques().physicalBinding(pos);
            if (occ != null) {
                att.techniques().bindPhysical(-1, occ);                         // optimista
                PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(occ, -1));
                rebuildWidgets();
            }
            // Sin ocupante y sin nada armado no hay nada que hacer: NO se reconstruye. La
            // versión anterior llamaba a rebuildWidgets() siempre, y eso reseteaba el foco y
            // el estado de hover en cada click en vacío.
        }
    }

    // ── Ratón: el BindBar mira primero ───────────────────────────────────────
    // Tiene que interceptar ANTES que super porque distinguir un clic de un arrastre exige
    // esperar al mouseReleased, y AbstractWidget dispara su acción ya en el mouseClicked.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindBar != null && bindBar.mouseClicked(att, mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (bindBar != null && bindBar.mouseDragged(att, mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (bindBar != null && bindBar.mouseReleased(att, mouseX, mouseY)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Teclas 1-9 con una técnica armada: la misma tecla que la disparará en combate. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (assigning != null) {
            int pos = (key >= 49 && key <= 57) ? key - 49
                    : (key >= 321 && key <= 329) ? key - 321
                    : -1;
            if (pos >= 0) {
                onPositionClicked(pos);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (att != null && countUnlocked() != builtUnlocked) rebuildWidgets();
    }

    private int countUnlocked() {
        if (att == null) return 0;
        int c = 0;
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (att.techniques().isUnlocked(t)) c++;
        }
        return c;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null) return;
        PlayerTechniques tech = att.techniques();

        // ── Cabecera ──
        g.drawString(this.font, Component.translatable("screen.zenkai.technique.tp", att.getTP()),
                panelLeft + TEXT_X_OFF, panelTop + CONTENT_TOP, ZenkaiPalette.VALUE, false);

        Component mnd = Component.translatable("screen.zenkai.physical.mnd",
                att.getAttribute(ZenkaiAttributes.MIND));
        g.drawString(this.font, mnd, rightEdge() - this.font.width(mnd), panelTop + CONTENT_TOP,
                ZenkaiPalette.MUTED_ON_PANEL, false);

        // ── Barra de posiciones ──
        if (bindBar != null) {
            bindBar.refreshIcons(att);
            bindBar.renderFrame(g, this.font, null);
        }
        if (assigning != null) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.physical.pick_slot"),
                    panelLeft + BG_W / 2, panelTop + BAR_Y_OFF + SlotCell.SIZE + 7,
                    ZenkaiPalette.VALUE);
        }

        // ── Filas ──
        if (visible.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.physical.empty"),
                    panelLeft + BG_W / 2, panelTop + LIST_Y_OFF + 24, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        int textX = panelLeft + TEXT_X_OFF;
        for (int i = 0; i < visible.size(); i++) {
            PhysicalTechnique t = visible.get(i);
            int y = rowTop(i);
            boolean unlocked = tech.isUnlocked(t);

            if (assigning == t) {
                g.fill(panelLeft + 10, y, panelLeft + BG_W - 10, y + ROW_H - 4,
                        ZenkaiPalette.SELECT_VEIL);
            }

            PhysicalIcons.draw(g, textX, y + 3, ICON, t);

            int nameX = textX + ICON + 4;
            g.drawString(this.font, Component.translatable(t.nameKey()), nameX, y + 3,
                    unlocked ? ZenkaiPalette.OK : ZenkaiPalette.MUTED_ON_PANEL, false);

            // La marca [n] va en una COLUMNA FIJA, no pegada al final del nombre: midiendo el
            // nombre traducido se solapaba en cuanto el texto era largo (visible en es_mx con
            // "Ráfaga de golpes"), y además la columna bailaba de fila en fila.
            int pos = tech.positionOf(t);
            if (pos >= 0) {
                Component key = Component.literal("[" + (pos + 1) + "]");
                g.drawString(this.font, key, rightEdge() - 96 - 8 - this.font.width(key), y + 3,
                        ZenkaiPalette.OK_ON_PANEL, false);
            }

            // Ficha técnica: lo que decide si vale la pena pagarla.
            Component stats = Component.translatable("screen.zenkai.physical.stats",
                    fmt(t.dmgMult()),
                    fmt(t.staminaPct() * 100.0),
                    fmt(t.cooldownTicks() / 20.0),
                    fmt(t.range()));
            g.drawString(this.font, stats, nameX, y + 14,
                    unlocked ? ZenkaiPalette.BODY_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL, false);

            // Requisito no cumplido, en rojo y explícito. Un botón gris sin motivo obliga a
            // adivinar si falta TP o falta MND.
            if (!unlocked && !canAfford(t)) {
                Component missing = att.getTP() < t.tpCost()
                        ? Component.translatable("screen.zenkai.physical.need_tp", t.tpCost())
                        : Component.translatable("screen.zenkai.physical.need_mnd", t.mindReq());
                g.drawString(this.font, missing,
                        rightEdge() - this.font.width(missing), y + 22, ZenkaiPalette.DENIED, false);
            }

            if (i < visible.size() - 1) {
                g.fill(textX, y + ROW_H - 5, rightEdge(), y + ROW_H - 4, ZenkaiPalette.SEPARATOR);
            }
        }

        Component barTip = (bindBar != null) ? bindBar.tooltipAt(att, mouseX, mouseY) : null;
        if (barTip != null) g.renderTooltip(this.font, barTip, mouseX, mouseY);

        // El icono que viaja con el cursor va el ÚLTIMO.
        if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }
}