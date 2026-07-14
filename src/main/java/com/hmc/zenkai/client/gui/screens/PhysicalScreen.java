package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.network.feature.technique.PhysicalTechniquePacket;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.PhysicalTechnique;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña Técnicas Físicas: lista predefinida desbloqueable por TP + bindeo a las 9
 * posiciones del overlay (compartidas con las ki). Flujo de asignación estilo pestaña ki:
 * "Asignar" arma el modo asignación -> click en una celda 1-9. Optimista + sync confirma.
 */
public class PhysicalScreen extends ZenkaiMenuScreen {

    private static final int CELL = 20;
    private static final int ROW_H = 26;

    /** Técnica armada para asignar, o null. */
    private PhysicalTechnique assigning = null;
    private int builtUnlocked = -1;

    public PhysicalScreen() {
        super(Component.translatable(ZenkaiTab.PHYSICAL_TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.PHYSICAL_TECHNIQUES; }

    @Override
    protected void initContent() {
        if (att == null) return;
        PlayerTechniques tech = att.techniques();
        builtUnlocked = countUnlocked();

        // ── Fila de 9 celdas de posición (arriba, como en la pestaña ki) ──
        int bx = panelLeft + (BG_W - (9 * CELL + 8 * 2)) / 2;
        int by = panelTop + 40;
        for (int p = 0; p < PlayerTechniques.BIND_POSITIONS; p++) {
            final int pos = p;
            this.addRenderableWidget(new TextOnlyButton(bx, by, CELL, CELL,
                    Component.empty(), () -> {
                if (assigning != null) {
                    att.techniques().bindPhysical(pos, assigning);          // optimista
                    PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(assigning, pos));
                    assigning = null;
                } else {
                    PhysicalTechnique occ = att.techniques().physicalBinding(pos);
                    if (occ != null) {
                        att.techniques().bindPhysical(-1, occ);             // optimista
                        PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(occ, -1));
                    }
                }
                rebuildWidgets();
            }));
            bx += CELL + 2;
        }

        // ── Filas por técnica ──
        int y = panelTop + 40 + CELL + 10;
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (!tech.isUnlocked(t)) {
                TextOnlyButton unlock = new TextOnlyButton(panelLeft + BG_W - 106, y, 90, 16,
                        Component.translatable("screen.zenkai.physical.unlock", t.tpCost),
                        () -> {
                            PacketDistributor.sendToServer(PhysicalTechniquePacket.unlock(t));
                        });
                unlock.active = att.getTP() >= t.tpCost;
                this.addRenderableWidget(unlock);
            } else {
                this.addRenderableWidget(new TextOnlyButton(panelLeft + BG_W - 106, y, 60, 16,
                        Component.translatable(assigning == t
                                ? "screen.zenkai.physical.assigning"
                                : "screen.zenkai.physical.assign"),
                        () -> {
                            assigning = (assigning == t) ? null : t;
                            rebuildWidgets();
                        }));
                if (att.techniques().positionOf(t) >= 0) {
                    this.addRenderableWidget(new TextOnlyButton(panelLeft + BG_W - 42, y, 16, 16,
                            Component.literal("✕"), () -> {
                        att.techniques().bindPhysical(-1, t);               // optimista
                        PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(t, -1));
                        rebuildWidgets();
                    }));
                }
            }
            y += ROW_H;
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Sync confirmó un unlock (u otro cambio de conteo): reconstruir.
        if (att != null && countUnlocked() != builtUnlocked) rebuildWidgets();
    }

    private int countUnlocked() {
        int c = 0;
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (att.techniques().isUnlocked(t)) c++;
        }
        return c;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);
        if (att == null) return;
        PlayerTechniques tech = att.techniques();

        // Contenido de las 9 celdas (ícono ki o físico del ocupante + número).
        int bx = panelLeft + (BG_W - (9 * CELL + 8 * 2)) / 2;
        int by = panelTop + 40;
        for (int p = 0; p < PlayerTechniques.BIND_POSITIONS; p++) {
            KiTechnique ki = tech.slot(tech.binding(p));
            PhysicalTechnique ph = tech.physicalBinding(p);
            if (ki != null) TechniqueIcons.draw(g, bx, by, ki);
            else if (ph != null) PhysicalIcons.draw(g, bx, by, ph);
            g.drawString(this.font, String.valueOf(p + 1), bx + 2, by + 1, 0xFFAAAAAA, true);
            bx += CELL + 2;
        }
        if (assigning != null) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.physical.pick_slot"),
                    panelLeft + BG_W / 2, by + CELL + 2, 0xFFFFFF55);
        }

        // Filas: ícono + nombre + estado.
        int y = panelTop + 40 + CELL + 10;
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            PhysicalIcons.draw(g, panelLeft + 16, y - 2, t);
            g.drawString(this.font, Component.translatable(t.nameKey()),
                    panelLeft + 42, y + 2, tech.isUnlocked(t) ? 0xFF7CFC7C : 0xFFAAAAAA, true);
            y += ROW_H;
        }

        g.drawString(this.font, Component.literal("TP: " + att.getTP()),
                panelLeft + 16, panelTop + BG_H - 24, 0xFFFFD75C, true);
    }
}