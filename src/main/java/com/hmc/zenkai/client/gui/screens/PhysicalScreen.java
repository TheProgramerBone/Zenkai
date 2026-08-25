package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.BindBar;
import com.hmc.zenkai.client.gui.widgets.SlotCell;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.MindBudget;
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

    /**
     * DOS renglones por fila, no tres.
         * Había una tercera línea con "Faltan 150 TP" justo debajo de un botón que ya decía
     * "Unlock (150 TP)": el mismo número dos veces, a diez píxeles de distancia. La versión
     * anterior además ponía el botón en y+4 con 16 px de alto y la ficha en y+14, así que se
     * pisaban. Ahora:
         *   y+2   nombre + [n]                          [ Unlock (150 TP) ]
     *   y+14  x1.2 dmg · 220% stam · 4s cd · 3 blk
         * Si no se puede pagar, el botón se apaga y su tooltip dice QUÉ falta — que es el único
     * dato que la línea suprimida aportaba de verdad.
     */
    private static final int ROW_H = 30;
    /** Aire a cada lado del texto dentro del botón de acción. */
    private static final int BTN_PAD = 6;
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
    /**
     * Botones de desbloqueo por técnica. Se guardan para poder detectar el hover en render y
     * pintar el tooltip a mano: un widget con active=false deja de responder, y el motivo del
     * bloqueo es justo lo que hay que poder consultar cuando está bloqueado.
     */
    private final java.util.Map<PhysicalTechnique, TextOnlyButton> unlockButtons =
            new java.util.EnumMap<>(PhysicalTechnique.class);
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

        unlockButtons.clear();
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
            int y = rowTop(i) + 1;

            if (!tech.isUnlocked(t)) {
                // El botón dice solo "Unlock". El precio y lo que falta viven en el tooltip:
                // en la fila ya hay nombre y ficha técnica, y meter "Unlock · 300 TP · 10 MND"
                // en la misma línea la convertía en tres datos compitiendo por el mismo sitio.
                boolean afford = canAfford(t);
                TextOnlyButton unlock = actionButton(
                        Component.translatable("screen.zenkai.physical.unlock_action"), y,
                        () -> PacketDistributor.sendToServer(PhysicalTechniquePacket.unlock(t)))
                        .textColors(
                                // Gris cuando no alcanza, dorado cuando sí. El color ES la
                                // respuesta rápida; el tooltip, la detallada.
                                afford ? ZenkaiPalette.TP_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL,
                                afford ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL,
                                ZenkaiPalette.MUTED_ON_PANEL)
                        .noShadow().asAction();
                unlock.active = afford;
                unlockButtons.put(t, unlock);
                addRenderableWidget(unlock);
            } else {
                addRenderableWidget(actionButton(
                        Component.translatable(assigning == t
                                ? "screen.zenkai.physical.assigning"
                                : "screen.zenkai.physical.assign"),
                        y,
                        () -> {
                            assigning = (assigning == t) ? null : t;
                            rebuildWidgets();
                        })
                        .onPanel().asAction());

                if (tech.positionOf(t) >= 0) {
                    // La ✖ del mod, no el glifo Unicode: el "✖" dependía de la fuente instalada
                    // y desentonaba con el resto de iconos.
                    addRenderableWidget(new TextOnlyButton(
                            rightEdge() - X_SIZE, y + 11, X_SIZE, X_SIZE,
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

    /**
     * Botón de acción anclado a la DERECHA y del ancho de su texto.
         * Con ancho fijo de 96 px, "Assign" (30 px) quedaba centrado en una caja invisible tres
     * veces mayor que él y se leía descolgado del borde. Dimensionar al contenido hace que
     * cada uno de los botones de la columna termine en la misma vertical, que es la alineación que
     * el ojo sí percibe.
         * Se suma el ancho de los corchetes y la negrita que añade asAction(), o el texto se
     * saldría de la zona clicable.
     */
    private TextOnlyButton actionButton(Component label, int y, Runnable onClick) {
        int w = this.font.width(Component.literal("[ ").append(label).append(" ]")) + 2 + BTN_PAD;
        return new TextOnlyButton(rightEdge() - w, y, w, 14, label, onClick);
    }

    private boolean canAfford(PhysicalTechnique t) {
        return att != null && att.getTP() >= t.tpCost() && MindBudget.canUnlock(att, t);
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
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.technique.tp", att.getTP()),
                panelLeft + TEXT_X_OFF, panelTop + CONTENT_TOP, ZenkaiPalette.TP_ON_PANEL);

        // Libre / total, no solo el total: con MIND finito el número que decide una compra es
        // lo que queda, y obligar al jugador a restar de cabeza es pedirle que haga la cuenta
        // que ya hace el servidor.
        int free = MindBudget.free(att);
        Component mnd = Component.translatable("screen.zenkai.physical.mnd_free",
                free, MindBudget.total(att));
        PanelText.rightOnPanel(g, this.font, mnd, rightEdge(), panelTop + CONTENT_TOP,
                free < 0 ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.MIND_ON_PANEL);

        // ── Barra de posiciones ──
        if (bindBar != null) {
            bindBar.refreshIcons(att);
            bindBar.renderFrame(g, this.font, null);
        }
        if (assigning != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.physical.pick_slot"),
                    panelLeft + BG_W / 2, panelTop + BAR_Y_OFF + SlotCell.SIZE + 7,
                    ZenkaiPalette.TP_ON_PANEL);
        }

        // ── Filas ──
        if (visible.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.physical.empty"),
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
            Component nm = Component.translatable(t.nameKey());
            PanelText.onPanel(g, this.font, nm, nameX, y + 3,
                    unlocked ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);

            // La marca [n] va pegada al nombre. En columna fija chocaba con el botón, que ahora
            // comparte este renglón y además tiene el ancho de su propio texto.
            int pos = tech.positionOf(t);
            if (pos >= 0) {
                PanelText.onPanel(g, this.font, Component.literal("[" + (pos + 1) + "]"),
                        nameX + this.font.width(nm) + 5, y + 3, ZenkaiPalette.OK_ON_PANEL);
            }

            // Ficha técnica: lo que decide si vale la pena pagarla.
            // El porcentaje se redondea antes de formatear: 2.2 * 100 da 220.00000000000003 en
            // coma flotante, así que fmt() lo veía como no-entero y escribía "220.0%" mientras
            // que 2.5 * 100 salía limpio y daba "250%". Dos formatos en la misma columna.
            Component stats = Component.translatable("screen.zenkai.physical.stats",
                    fmt(t.dmgMult()),
                    fmt(Math.round(t.staminaPct() * 1000.0) / 10.0),
                    fmt(t.cooldownTicks() / 20.0),
                    fmt(t.range()));
            PanelText.onPanel(g, this.font, stats, nameX, y + 14,
                    unlocked ? ZenkaiPalette.BODY_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);

            if (i < visible.size() - 1) {
                g.fill(textX, y + ROW_H - 5, rightEdge(), y + ROW_H - 4, ZenkaiPalette.SEPARATOR);
            }
        }

        Component barTip = (bindBar != null) ? bindBar.tooltipAt(att, mouseX, mouseY) : null;
        if (barTip != null) g.renderTooltip(this.font, barTip, mouseX, mouseY);
        else renderUnlockTooltip(g, mouseX, mouseY);

        // El icono que viaja con el cursor va el ÚLTIMO.
        if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
    }

    /**
     * Requisitos de la técnica bloqueada bajo el cursor.
         * Se dibuja a mano y no con Tooltip.create() porque el botón está inactivo cuando no se
     * puede pagar, y un widget inactivo no muestra su tooltip — que es exactamente el caso en
     * el que el jugador necesita leerlo.
         * No usa claves nuevas: reaprovecha las que ya describían el coste en el propio botón
     * (unlock / unlock_mnd) y las de déficit (need_tp / need_mnd).
     */
    private void renderUnlockTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (att == null) return;
        for (var e : unlockButtons.entrySet()) {
            TextOnlyButton b = e.getValue();
            if (!b.visible || mouseX < b.getX() || mouseX >= b.getX() + b.getWidth()
                    || mouseY < b.getY() || mouseY >= b.getY() + b.getHeight()) continue;

            PhysicalTechnique t = e.getKey();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(t.nameKey()));
            lines.add(t.mindReq() > 0
                    ? Component.translatable("screen.zenkai.physical.unlock_mnd",
                            t.tpCost(), t.mindReq())
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    : Component.translatable("screen.zenkai.physical.unlock", t.tpCost())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));

            // Solo se enumera lo que falta, no lo que se pide: si le sobra el MND, leer
            // "necesitas 6 MND" cuando tiene 10 es ruido que compite con el dato que importa.
            if (att.getTP() < t.tpCost()) {
                lines.add(Component.translatable("screen.zenkai.physical.need_tp",
                        t.tpCost() - att.getTP()).withStyle(net.minecraft.ChatFormatting.RED));
            }
            int mindCost = MindBudget.costOf(t);
            if (MindBudget.free(att) < mindCost) {
                lines.add(Component.translatable("screen.zenkai.physical.need_mnd",
                        mindCost - MindBudget.free(att)).withStyle(net.minecraft.ChatFormatting.RED));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }
}