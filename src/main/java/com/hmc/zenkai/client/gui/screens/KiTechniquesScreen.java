package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.PhysicalIcons; // <- IMPORTANTE: Añadido aquí
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.network.feature.technique.TechniquePacket;
import com.hmc.zenkai.core.technique.KiTechnique;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña Técnicas Ki.
 *
 * Arriba: la BARRA DE ASIGNACIÓN — las mismas 9 posiciones del overlay de combate
 * (número + ícono), para consistencia visual total.
 *
 * Flujo de asignación: "Asignar" en una técnica (fila resaltada) -> click en la posición.
 * Click en posición ocupada sin selección = desasignar. Cambios OPTIMISTAS en cliente
 * (el sync del servidor confirma) -> la UI responde al instante.
 *
 * Filas: ícono (base explosiva + tipo teñido) + nombre; sub-línea = tipo y tecla asignada.
 */
public class KiTechniquesScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 26;
    private static final int CELL = 20;
    private static final int CELL_GAP = 2;
    private static final int BIND_Y_OFF = 52;  // barra de posiciones
    private static final int LIST_Y_OFF = 84;  // inicio de la lista

    /** Índice de técnica seleccionada para asignar; -1 = ninguna. */
    private int assigning = -1;

    /** Nº de slots con el que se construyeron los widgets (auto-rebuild al llegar syncs). */
    private int builtSlotCount = -1;

    public KiTechniquesScreen() {
        super(Component.translatable(ZenkaiTab.KI_TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.KI_TECHNIQUES; }

    @Override
    protected void initContent() {
        if (att == null) return;
        builtSlotCount = att.techniques().slotCount();

        // ── Barra de asignación: 9 posiciones clicables ──
        int n = PlayerTechniques.BIND_POSITIONS;
        int totalW = n * CELL + (n - 1) * CELL_GAP;
        int bx = panelLeft + (BG_W - totalW) / 2;
        int by = panelTop + BIND_Y_OFF;
        for (int pos = 0; pos < n; pos++) {
            final int p = pos;
            this.addRenderableWidget(new TextOnlyButton(bx, by, CELL, CELL,
                    Component.empty(), () -> onPositionClicked(p)));
            bx += CELL + CELL_GAP;
        }

        // ── Lista de técnicas ──
        int y = panelTop + LIST_Y_OFF;
        var slots = att.techniques().slots();
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            this.addRenderableWidget(new TextOnlyButton(panelLeft + BG_W - 120, y, 46, 16,
                    Component.translatable("screen.zenkai.technique.assign"),
                    () -> {
                        assigning = (assigning == idx) ? -1 : idx; // toggle
                    }));
            this.addRenderableWidget(new TextOnlyButton(panelLeft + BG_W - 75, y, 44, 16,
                    Component.translatable("screen.zenkai.technique.edit"),
                    () -> mc.setScreen(new TechniqueEditScreen(idx))));
            this.addRenderableWidget(new TextOnlyButton(panelLeft + BG_W - 30, y, 16, 16,
                    Component.literal("✖"),
                    () -> {
                        att.techniques().removeSlot(idx);                       // optimista
                        PacketDistributor.sendToServer(TechniquePacket.delete(idx));
                        assigning = -1;
                        this.rebuildWidgets();
                    }));
            y += ROW_H;
        }
        if (slots.size() < StatsConfig.techniqueMaxSlots()) {
            this.addRenderableWidget(new TextOnlyButton(panelLeft + 16, y + 4, 110, 16,
                    Component.translatable("screen.zenkai.technique.create"),
                    () -> mc.setScreen(new TechniqueEditScreen(-1))));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (att != null && att.techniques().slotCount() != builtSlotCount) {
            this.rebuildWidgets(); // el sync trajo más/menos técnicas que las construidas
        }
    }

    /** Click en una posición: asigna la técnica seleccionada, o desasigna si no hay selección. */
    private void onPositionClicked(int pos) {
        if (att == null) return;
        if (assigning >= 0) {
            att.techniques().bind(pos, assigning);                              // optimista
            PacketDistributor.sendToServer(TechniquePacket.bind(assigning, pos));
            assigning = -1;
        } else {
            int occupant = att.techniques().binding(pos);
            if (occupant >= 0) {
                att.techniques().bind(-1, occupant);                            // optimista
                PacketDistributor.sendToServer(TechniquePacket.bind(occupant, -1));
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);
        if (att == null) return;
        g.drawString(this.font, Component.literal("TP: " + att.getTP()),
                panelLeft + 16, panelTop + 38, 0xFFFFD700, true);

        // ── Decoración de la barra de posiciones (sobre los botones) ──
        int n = PlayerTechniques.BIND_POSITIONS;
        int totalW = n * CELL + (n - 1) * CELL_GAP;
        int bx = panelLeft + (BG_W - totalW) / 2;
        int by = panelTop + BIND_Y_OFF;
        for (int pos = 0; pos < n; pos++) {
            KiTechnique t = att.techniques().slot(att.techniques().binding(pos));
            var phys = att.techniques().physicalBinding(pos);

            if (t != null) {
                TechniqueIcons.draw(g, bx, by, t);
            } else if (phys != null) {
                PhysicalIcons.draw(g, bx, by, phys);
            }

            boolean occupied = t != null || phys != null;
            g.drawString(this.font, Component.literal(String.valueOf(pos + 1)),
                    bx + 2, by + 1, occupied ? 0xFFFFFFFF : 0xFFAAAAAA, true);
            bx += CELL + CELL_GAP;
        }

        // Hint del modo asignación.
        if (assigning >= 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.technique.assign_hint"),
                    panelLeft + BG_W / 2, by + CELL - 1, 0xFFFFE066);
        }

        // ── Lista ──
        var slots = att.techniques().slots();
        if (slots.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.technique.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 + 20, 0xFFAAAAAA);
        }
        int y = panelTop + LIST_Y_OFF;
        for (int i = 0; i < slots.size(); i++) {
            KiTechnique t = slots.get(i);
            boolean sel = (i == assigning);

            if (sel) { // fila resaltada mientras se asigna
                g.fill(panelLeft + 12, y - 3, panelLeft + BG_W - 12, y + 21, 0x30FFFFFF);
            }
            TechniqueIcons.draw(g, panelLeft + 14, y - 1, t);
            g.drawString(this.font, Component.literal(t.name()), panelLeft + 38, y, 0xFFFFFFFF, true);

            int pos = att.techniques().positionOf(i);
            Component sub = Component.translatable(t.type().nameKey())
                    .append(Component.literal(pos >= 0 ? " · [" + (pos + 1) + "]" : ""));
            g.drawString(this.font, sub, panelLeft + 38, y + 9, 0xFFAAAAAA, true);
            y += ROW_H;
        }
    }
}