package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.BindBar;
import com.hmc.zenkai.client.gui.widgets.SlotCell;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechniquePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Fila 3 del hub Techniques: índice de SOLO LECTURA de toda "técnica firma" (cualquier
 * KiTechniqueType/PhysicalTechnique cuyo master() no esté vacío, de CUALQUIER maestro — ver
 * TechniqueDef.master() y la sección "Técnicas firma" de CLAUDE.md). La compra real sigue
 * exigiendo estar delante del maestro correspondiente (MasterScreen ya lo hace); esta pantalla
 * es solo "qué existe y quién lo enseña", con una excepción: una técnica FÍSICA ya desbloqueada
 * SÍ se puede asignar a la barra de posiciones aquí mismo (misma BindBar/PhysicalTechniquePacket
 * que ya usa PhysicalScreen) — un tipo de ki desbloqueado en cambio necesita pasar por Ki
 * Techniques para crear la instancia (el desbloqueo de un tipo ki es solo el permiso, no
 * fabrica la técnica sola, a diferencia de las físicas que son su propio enum sin instancia
 * separada — ver PlayerTechniques.isUnlocked(KiTechniqueType) vs el sistema de slots).
 *
 * Hoy esta lista está VACÍA en la práctica: ningún KiTechniqueType/PhysicalTechnique tiene
 * master() puesto todavía (el campo es cimiento a propósito, sin caso real aún) — el estado
 * vacío es el camino esperado hasta que un datapack lo use.
 */
public class MasterTechniquesScreen extends ZenkaiMenuScreen {

    private sealed interface Entry {
        String masterId();
        Component nameKey();
        record Ki(KiTechniqueType type) implements Entry {
            public String masterId() { return type.master(); }
            public Component nameKey() { return Component.translatable(type.nameKey()); }
        }
        record Physical(PhysicalTechnique type) implements Entry {
            public String masterId() { return type.master(); }
            public Component nameKey() { return Component.translatable(type.nameKey()); }
        }
    }

    private static final int ROW_H = 28;
    private static final int BAR_Y_OFF = CONTENT_TOP + 24;
    private static final int LIST_Y_OFF = BAR_Y_OFF + SlotCell.SIZE + 16;
    private static final int TEXT_X_OFF = 16;
    private static final int ICON = 18;

    private final List<Entry> entries = new ArrayList<>();
    private BindBar bindBar;
    private PhysicalTechnique assigning = null;

    public MasterTechniquesScreen() {
        super(Component.translatable("screen.zenkai.techniques_hub.row.master"));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TECHNIQUES; }

    private int rightEdge() { return panelLeft + BG_W - 14; }
    private int rowTop(int i) { return panelTop + LIST_Y_OFF + i * ROW_H; }

    @Override
    protected void initContent() {
        if (att == null) return;
        entries.clear();
        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (!t.master().isEmpty()) entries.add(new Entry.Ki(t));
        }
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (!t.master().isEmpty()) entries.add(new Entry.Physical(t));
        }

        // La barra de posiciones solo hace falta si hay algo físico que asignar desde aquí —
        // sin ella ocuparía sitio para una lista que hoy no la usa (ki no asigna directo, ver
        // javadoc de la clase).
        boolean anyPhysical = entries.stream().anyMatch(e -> e instanceof Entry.Physical);
        if (anyPhysical) {
            bindBar = BindBar.create(
                    panelLeft + (BG_W - BindBar.WIDTH) / 2, panelTop + BAR_Y_OFF,
                    att, () -> assigning != null, this::onPositionClicked, this::addRenderableWidget);
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!(e instanceof Entry.Physical(PhysicalTechnique t))) continue;
            if (!att.techniques().isUnlocked(t)) continue;
            int y = rowTop(i) + 1;
            addRenderableWidget(actionButton(
                    Component.translatable(assigning == t
                            ? "screen.zenkai.physical.assigning" : "screen.zenkai.physical.assign"),
                    y, () -> { assigning = (assigning == t) ? null : t; rebuildWidgets(); })
                    .onPanel().asAction());
        }
    }

    private TextOnlyButton actionButton(Component label, int y, Runnable onClick) {
        int w = this.font.width(Component.literal("[ ").append(label).append(" ]")) + 8;
        return new TextOnlyButton(rightEdge() - w, y, w, 14, label, onClick);
    }

    private void onPositionClicked(int pos) {
        if (att == null) return;
        if (assigning != null) {
            att.techniques().bindPhysical(pos, assigning);
            PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(assigning, pos));
            assigning = null;
            rebuildWidgets();
        } else {
            PhysicalTechnique occ = att.techniques().physicalBinding(pos);
            if (occ != null) {
                att.techniques().bindPhysical(-1, occ);
                PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(occ, -1));
                rebuildWidgets();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindBar != null && bindBar.mouseClicked(att, mouseX, mouseY, button)) return true;
        if (button == 0 && att != null && clickKiRow(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Click en una fila Ki YA desbloqueada: no hay bind directo aquí (el desbloqueo de un tipo
     *  ki es solo el permiso, ver javadoc de la clase) — lleva a Ki Techniques para crear la
     *  instancia real. */
    private boolean clickKiRow(double mouseX, double mouseY) {
        int textX = panelLeft + TEXT_X_OFF;
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Entry.Ki k)) continue;
            if (!att.techniques().isUnlocked(k.type())) continue;
            int y = rowTop(i);
            if (mouseX >= textX && mouseX < rightEdge() && mouseY >= y && mouseY < y + ROW_H) {
                mc.setScreen(new KiTechniquesScreen());
                return true;
            }
        }
        return false;
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

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null) return;

        if (bindBar != null) {
            bindBar.refreshIcons(att);
            bindBar.renderFrame(g, this.font, null);
        }

        if (entries.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.master_techniques.empty"),
                    panelLeft + BG_W / 2, panelTop + LIST_Y_OFF + 40, ZenkaiPalette.MUTED_ON_PANEL);
            if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
            return;
        }

        int textX = panelLeft + TEXT_X_OFF;
        Entry hovered = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int y = rowTop(i);
            boolean unlocked = e instanceof Entry.Ki k ? att.techniques().isUnlocked(k.type())
                    : att.techniques().isUnlocked(((Entry.Physical) e).type());

            if (e instanceof Entry.Ki k) TechniqueIcons.draw(g, textX, y + 3, ICON, k.type(), k.type().defaultRgb());
            else PhysicalIcons.draw(g, textX, y + 3, ICON, ((Entry.Physical) e).type());

            int nameX = textX + ICON + 4;
            PanelText.onPanel(g, this.font, e.nameKey(), nameX, y + 3,
                    unlocked ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);

            Component master = Component.translatable("master.zenkai." + e.masterId());
            Component sub = unlocked
                    ? Component.translatable("screen.zenkai.master_techniques.taught_by", master)
                    : Component.translatable("screen.zenkai.master_techniques.locked", master);
            PanelText.onPanel(g, this.font, sub, nameX, y + 14,
                    unlocked ? ZenkaiPalette.MUTED_ON_PANEL : ZenkaiPalette.DENIED_ON_PANEL);

            // Ki desbloqueado: sin bind directo aquí (ver javadoc), un hint a dónde ir.
            if (unlocked && e instanceof Entry.Ki) {
                PanelText.rightOnPanel(g, this.font,
                        Component.translatable("screen.zenkai.master_techniques.open_ki"),
                        rightEdge(), y + 3, ZenkaiPalette.TP_ON_PANEL);
            }

            if (i < entries.size() - 1) {
                g.fill(textX, y + ROW_H - 3, rightEdge(), y + ROW_H - 2, ZenkaiPalette.SEPARATOR);
            }
            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= textX && mouseX < rightEdge()) {
                hovered = e;
            }
        }

        Component barTip = (bindBar != null) ? bindBar.tooltipAt(att, mouseX, mouseY) : null;
        if (barTip != null) {
            g.renderTooltip(this.font, barTip, mouseX, mouseY);
        } else if (hovered != null) {
            g.renderTooltip(this.font, this.font.split(hovered.nameKey(), 150), mouseX, mouseY);
        }
        if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
    }
}
