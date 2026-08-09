package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * La fila de nueve posiciones del overlay de combate, como UN objeto.
 * Las pestañas de técnicas ki y de técnicas físicas manipulan exactamente las mismas nueve
 * posiciones (bindings[] es un único array donde las físicas se codifican como -100-ordinal),
 * pero cada pestaña tenía su propia copia del bucle de construcción, su propia copia del bucle
 * de dibujo y sus propias constantes de tamaño. Se habían separado ya: KiTechniquesScreen
 * centraba la fila con `(BG_W - totalW) / 2` y PhysicalScreen con `(BG_W - (9*CELL + 8*2)) / 2`,
 * que da lo mismo hoy y dejaría de darlo en cuanto una de las dos tocara CELL o el hueco.
 * Aquí se construye una vez y las dos pestañas la usan. Además unifica el comportamiento del
 * click, que también divergía: la pestaña ki desasignaba al pulsar una posición ocupada sin
 * nada armado, la física hacía lo mismo pero además llamaba a rebuildWidgets() en todos los
 * casos, incluido el de no hacer nada.
 */
public final class BindBar {

    /** Ancho total que ocupa la fila. Lo necesitan las pantallas para centrarla. */
    public static final int WIDTH =
            PlayerTechniques.BIND_POSITIONS * SlotCell.SIZE
                    + (PlayerTechniques.BIND_POSITIONS - 1) * SlotCell.GAP;
    public static final int HEIGHT = SlotCell.SIZE;

    private final List<SlotCell> cells = new ArrayList<>();
    private final int x, y;

    private BindBar(int x, int y) { this.x = x; this.y = y; }

    /**
     * Construye las nueve celdas y las registra.
     *
     * @param att        attachment del jugador (fuente de los ocupantes)
     * @param assigning  supplier de si hay algo armado para asignar (resalta las celdas libres)
     * @param onClick    qué hacer al pulsar una posición
     * @param register   la pantalla, para añadir cada celda como widget
     */
    public static BindBar create(int x, int y, PlayerStatsAttachment att,
                                 java.util.function.BooleanSupplier assigning,
                                 Consumer<Integer> onClick,
                                 Consumer<SlotCell> register) {
        BindBar bar = new BindBar(x, y);
        int cx = x;
        for (int p = 0; p < PlayerTechniques.BIND_POSITIONS; p++) {
            final int pos = p;
            SlotCell cell = new SlotCell(cx, y, p + 1,
                    () -> occupied(att, pos),
                    assigning,
                    () -> onClick.accept(pos));
            bar.cells.add(cell);
            register.accept(cell);
            cx += SlotCell.SIZE + SlotCell.GAP;
        }
        return bar;
    }

    private static boolean occupied(PlayerStatsAttachment att, int pos) {
        if (att == null) return false;
        return att.techniques().slot(att.techniques().binding(pos)) != null
                || att.techniques().physicalBinding(pos) != null;
    }

    /**
     * Refresca el pintor de icono de cada celda. Se llama en render, no en init: el ocupante de
     * una posición cambia con cada sync del servidor y con cada asignación optimista, y un
     * pintor capturado en init se quedaría enseñando la técnica anterior.
     */
    public void refreshIcons(PlayerStatsAttachment att) {
        if (att == null) return;
        PlayerTechniques tech = att.techniques();
        for (int p = 0; p < cells.size(); p++) {
            KiTechnique ki = tech.slot(tech.binding(p));
            PhysicalTechnique ph = tech.physicalBinding(p);
            cells.get(p).icon((g, ix, iy, size) -> {
                if (ki != null) TechniqueIcons.draw(g, ix, iy, size, ki);
                else if (ph != null) PhysicalIcons.draw(g, ix, iy, size, ph);
            });
        }
    }

    /** Tooltip del ocupante bajo el cursor. Devuelve null si no hay ninguno. */
    public Component tooltipAt(PlayerStatsAttachment att, int mouseX, int mouseY) {
        if (att == null) return null;
        for (int p = 0; p < cells.size(); p++) {
            SlotCell c = cells.get(p);
            if (mouseX < c.getX() || mouseX >= c.getX() + SlotCell.SIZE
                    || mouseY < c.getY() || mouseY >= c.getY() + SlotCell.SIZE) continue;

            KiTechnique ki = att.techniques().slot(att.techniques().binding(p));
            if (ki != null) return ki.displayName();
            PhysicalTechnique ph = att.techniques().physicalBinding(p);
            if (ph != null) return Component.translatable(ph.nameKey());
            return Component.translatable("screen.zenkai.technique.slot_empty");
        }
        return null;
    }

    /**
     * Marco de la fila. Sin él las nueve celdas se leen como nueve cosas sueltas en vez de como
     * la barra de acción que son — y esa barra es lo que el jugador ve en el HUD durante el
     * combate, así que aquí tiene que parecerse a aquello.
     */
    public void renderFrame(GuiGraphics g, Font font, Component label) {
        g.fill(x - 4, y - 4, x + WIDTH + 4, y + HEIGHT + 4, 0x22AC421B);
        g.fill(x - 4, y - 4, x + WIDTH + 4, y - 3, ZenkaiPalette.SEPARATOR);
        g.fill(x - 4, y + HEIGHT + 3, x + WIDTH + 4, y + HEIGHT + 4, ZenkaiPalette.SEPARATOR);
        if (label != null) {
            g.drawString(font, label, x, y - 4 - font.lineHeight - 2,
                    ZenkaiPalette.MUTED_ON_PANEL, false);
        }
    }

    public int x() { return x; }
    public int y() { return y; }
    public int bottom() { return y + HEIGHT + 4; }

    /** Para que la pantalla pueda ocultarlas si hiciera falta. */
    public List<? extends AbstractWidget> cells() { return cells; }
}