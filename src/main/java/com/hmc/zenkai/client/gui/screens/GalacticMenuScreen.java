package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.feature.spacepod.SpacePodDestination;
import com.hmc.zenkai.feature.spacepod.SpacePodLaunchPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Menú galáctico de la SpacePod: se abre con click derecho estando YA montado en la nave (ver
 * SpacePodEntity.mobInteract). Diálogo flotante SIN panel opaco detrás — pedido explícito del
 * usuario ("generala y hazla similar a la de instant transmission pero hazla más tecnológica"):
 * MISMA estructura de marco que InstantTransmissionMenuScreen (anillos duros + brillo de esquina
 * + banda de contenido, ver tools/gen_galactic_menu_screen.py) y el mismo tamaño de diálogo, pero
 * con la paleta ZenkaiTechPalette (consola/HUD de nave, la misma familia del banco de scouter) en
 * vez de la cósmica índigo/violeta de Instant Transmission — esto es una nave, no un fenómeno de
 * ki.
 * Tabla estática de {@link SpacePodDestination} (Tierra/Namek), sin el aparato de
 * descubrimiento/protectorKey/scroll de InstantTransmissionMenuScreen — decisión ya tomada en
 * .claude/pendiente/nave-espacial-menu-galactico.md: con 2-3 filas fijas no hace falta nada de
 * eso. Yardrat es una fila fija "próximamente" sin SpacePodDestination real detrás (icono de
 * candado propio, ver tools/gen_galactic_menu_icons.py).
 */
public class GalacticMenuScreen extends Screen {

    private static final int BG_W = 210;
    private static final int BG_H = 180;
    private static final int PADDING = 10;
    private static final int CONTENT_TOP = 26;
    private static final int ROW_H = 24;
    private static final int ROW_ICON_GAP = 6;
    private static final int TOOLTIP_W = 180;

    private static final ResourceLocation BG_TEX = ResourceLocation
            .fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/galactic_menu.png");
    private static final ResourceLocation ICONS_TEX = ResourceLocation
            .fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons_galactic_menu.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;

    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) { return new IconUV(col * ICON_CELL, row * ICON_CELL); }
    }

    /** Columnas == tools/gen_galactic_menu_icons.py (fila v=0: Tierra, Namek, Yardrat). */
    private static final IconUV ICON_EARTH = IconUV.grid(0, 0);
    private static final IconUV ICON_NAMEK = IconUV.grid(1, 0);
    private static final IconUV ICON_YARDRAT = IconUV.grid(2, 0);

    private enum RowState { AVAILABLE, ALREADY_HERE, COMING_SOON }

    private record Row(Component label, IconUV icon, RowState state, Runnable onClick) {}

    private List<Row> rows = List.of();
    private int left, top;

    public GalacticMenuScreen() {
        super(Component.translatable("screen.zenkai.galactic_menu"));
    }

    @Override
    protected void init() {
        left = (this.width - BG_W) / 2;
        top = (this.height - BG_H) / 2;
        rows = buildRows();
    }

    /** Filas fijas: Tierra, Namek (cada una se deshabilita sola si ya estás en su dimensión) y
     *  Yardrat, siempre "próximamente". Recalculado en cada init() (no cachea entre aperturas)
     *  para que la fila del planeta actual siempre refleje dónde está el jugador AHORA. */
    private List<Row> buildRows() {
        List<Row> out = new ArrayList<>();
        ResourceKey<Level> currentDim = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.dimension() : null;

        out.add(destRow(SpacePodDestination.EARTH, ICON_EARTH, currentDim));
        out.add(destRow(SpacePodDestination.NAMEK, ICON_NAMEK, currentDim));
        out.add(new Row(Component.translatable("screen.zenkai.galactic_menu.dest.yardrat"),
                ICON_YARDRAT, RowState.COMING_SOON, () -> {}));
        return out;
    }

    private Row destRow(SpacePodDestination dest, IconUV icon, @Nullable ResourceKey<Level> currentDim) {
        boolean here = currentDim != null && currentDim.equals(dest.dimension());
        RowState state = here ? RowState.ALREADY_HERE : RowState.AVAILABLE;
        Runnable onClick = () -> {
            assert this.minecraft != null;
            var conn = this.minecraft.getConnection();
            if (conn != null) conn.send(new SpacePodLaunchPacket(dest.id()));
            onClose();
        };
        return new Row(Component.translatable(dest.nameKey()), icon, state, onClick);
    }

    // ── Geometría ────────────────────────────────────────────────────────────
    private int contentLeft()  { return left + PADDING; }
    private int contentRight() { return left + BG_W - PADDING; }
    private int rowsTop()      { return top + CONTENT_TOP; }
    private int rowTop(int i)  { return rowsTop() + i * ROW_H; }

    private boolean rowHovered(int y, int mouseX, int mouseY) {
        return mouseX >= contentLeft() - 2 && mouseX <= contentRight() && mouseY >= y && mouseY < y + ROW_H - 1;
    }

    // ── Render — VARIANTE A: renderBackground pinta el marco, render() dibuja encima. Nunca
    // llamar this.renderBackground(...) a mano dentro de render(), nunca super.render() más de
    // una vez (ver CLAUDE.md / la skill add-gui-screen). ──

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG_TEX, left, top, 0, 0, BG_W, BG_H, BG_W, BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        PanelText.centeredOnDark(g, this.font, this.title, left + BG_W / 2, top + PADDING,
                ZenkaiTechPalette.TITLE);

        Component hoveredTooltip = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowTop(i);
            boolean hovered = rowHovered(y, mouseX, mouseY);
            boolean available = row.state() == RowState.AVAILABLE;

            if (hovered && available) {
                g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiTechPalette.SELECT_VEIL);
            }

            int iconY = y + (ROW_H - ICON_CELL) / 2;
            if (!available) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
            g.blit(ICONS_TEX, contentLeft(), iconY, row.icon().u(), row.icon().v(),
                    ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
            if (!available) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            int textX = contentLeft() + ICON_CELL + ROW_ICON_GAP;
            int textY = y + (ROW_H - 9) / 2;
            int color = !available ? ZenkaiTechPalette.DIM_ON_SCREEN
                    : hovered ? ZenkaiTechPalette.MAXED_ON_SCREEN : ZenkaiTechPalette.OK_ON_SCREEN;
            PanelText.onDark(g, this.font, row.label(), textX, textY, color);

            if (!available && hovered) {
                hoveredTooltip = Component.translatable(row.state() == RowState.ALREADY_HERE
                        ? "screen.zenkai.galactic_menu.already_here"
                        : "screen.zenkai.galactic_menu.dest.yardrat.soon");
            }
            if (i < rows.size() - 1) {
                g.fill(contentLeft() - 2, y + ROW_H - 1, contentRight(), y + ROW_H, ZenkaiTechPalette.ROW_SEP);
            }
        }

        if (hoveredTooltip != null) {
            g.renderTooltip(this.font, this.font.split(hoveredTooltip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (!rowHovered(rowTop(i), (int) mouseX, (int) mouseY)) continue;
                if (row.state() == RowState.AVAILABLE) row.onClick().run();
                return true; // absorbe el clic aunque esté bloqueada, mismo criterio que el resto del mod
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
