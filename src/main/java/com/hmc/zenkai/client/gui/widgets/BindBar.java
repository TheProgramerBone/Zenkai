package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechniquePacket;
import com.hmc.zenkai.feature.technique.TechniquePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * La fila de nueve posiciones del overlay de combate, como UN objeto.
 * Las pestañas de técnicas ki y físicas manipulan las MISMAS nueve posiciones (bindings[] es un
 * único array donde las físicas se codifican aparte), pero cada una tenía su copia del bucle de
 * construcción, del de dibujo y de las constantes. Ya habían empezado a divergir.
 * ═══ ARRASTRAR PARA REORDENAR ═══
 * Una técnica equipada se coge de su celda y se suelta en otra. Antes reordenar exigía
 * desasignar, volver a la lista, pulsar Asignar y elegir destino: cuatro pasos para mover algo
 * una casilla, y era la operación más frecuente al preparar una pelea.
 * El arrastre lo conduce la PANTALLA, no los widgets: SlotCell ya no ejecuta su acción en
 * mouseClicked porque hay que esperar a saber si el gesto es un clic o un arrastre, y eso solo
 * se sabe al soltar. La pantalla llama a mouseClicked/mouseDragged/mouseReleased de esta clase
 * ANTES de super, y si devuelven true el evento no llega a los widgets.
 * Soltar sobre una celda OCUPADA intercambia, no sobrescribe: perder una técnica por soltarla
 * un píxel de más sería un castigo desproporcionado para un gesto de precisión.
 */
public final class BindBar {

    /** Ancho total que ocupa la fila. Lo necesitan las pantallas para centrarla. */
    public static final int WIDTH =
            PlayerTechniques.BIND_POSITIONS * SlotCell.SIZE
                    + (PlayerTechniques.BIND_POSITIONS - 1) * SlotCell.GAP;
    public static final int HEIGHT = SlotCell.SIZE;

    /** Píxeles que hay que mover antes de considerar el gesto un arrastre y no un clic. */
    private static final int DRAG_THRESHOLD = 3;

    /** Quién ocupa una posición. Uno de los dos campos es siempre nulo/-1. */
    private record Occupant(int kiSlot, @Nullable PhysicalTechnique physical) {
        boolean isEmpty() { return kiSlot < 0 && physical == null; }
    }

    private final List<SlotCell> cells = new ArrayList<>();
    private final int x, y;

    private int dragFrom = -1;
    private int dragOverPos = -1;
    private double pressX, pressY;
    private boolean dragConfirmed = false;

    private BindBar(int x, int y) { this.x = x; this.y = y; }

    /**
     * Construye las nueve celdas y las registra.
         * @param att       attachment del jugador (fuente de los ocupantes)
     * @param assigning supplier de si hay algo armado para asignar
     * @param onClick   qué hacer al pulsar una posición (clic simple, sin arrastre)
     * @param register  la pantalla, para añadir cada celda como widget
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
                    () -> !occupantOf(att, pos).isEmpty(),
                    assigning,
                    () -> onClick.accept(pos));
            bar.cells.add(cell);
            register.accept(cell);
            cx += SlotCell.SIZE + SlotCell.GAP;
        }
        return bar;
    }

    private static Occupant occupantOf(PlayerStatsAttachment att, int pos) {
        if (att == null) return new Occupant(-1, null);
        PlayerTechniques t = att.techniques();
        int ki = t.binding(pos);
        return new Occupant(t.slot(ki) != null ? ki : -1, t.physicalBinding(pos));
    }

    /** Índice de la celda bajo el punto, o -1. */
    private int cellAt(double mx, double my) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).inside(mx, my)) return i;
        }
        return -1;
    }

    // ── Ratón ────────────────────────────────────────────────────────────────

    /** @return true si el evento se consume (empieza un posible arrastre sobre esta barra). */
    public boolean mouseClicked(PlayerStatsAttachment att, double mx, double my, int button) {
        if (button != 0) return false;
        int i = cellAt(mx, my);
        if (i < 0) return false;
        dragFrom = i;
        dragOverPos = i;
        pressX = mx;
        pressY = my;
        dragConfirmed = false;
        return true;
    }

    public boolean mouseDragged(PlayerStatsAttachment att, double mx, double my) {
        if (dragFrom < 0) return false;
        if (!dragConfirmed) {
            // El umbral evita que un clic con la mano poco firme se convierta en un arrastre
            // de una celda a la de al lado, que además son adyacentes y de solo 20 px.
            if (Math.abs(mx - pressX) < DRAG_THRESHOLD && Math.abs(my - pressY) < DRAG_THRESHOLD) {
                return true;
            }
            // Solo se arrastra lo que existe: partir de una celda vacía no mueve nada.
            if (!cells.get(dragFrom).isOccupied()) { dragFrom = -1; return false; }
            dragConfirmed = true;
            cells.get(dragFrom).setSourceOfDrag(true);
        }
        dragOverPos = cellAt(mx, my);
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setDropTarget(dragConfirmed && i == dragOverPos && i != dragFrom);
        }
        return true;
    }

    /**
     * @return true si el gesto se ha resuelto aquí (clic o arrastre); false si no había nada
     *         empezado sobre esta barra.
         * EL CLIC SE EJECUTA AQUÍ, no se delega. AbstractWidget dispara su acción dentro de
     * mouseClicked, y como esta barra intercepta ese evento para poder esperar al release, la
     * celda nunca llegaba a registrar la pulsación: devolver false confiando en que super la
     * procesara dejaba las celdas VACÍAS muertas al clic, que es justo la mitad de la
     * interacción (asignar algo armado a un hueco libre).
     */
    public boolean mouseReleased(PlayerStatsAttachment att, double mx, double my) {
        if (dragFrom < 0) return false;

        int from = dragFrom;
        boolean wasDrag = dragConfirmed;
        clearDragState();

        if (!wasDrag) {
            // Clic simple: solo cuenta si se suelta sobre la MISMA celda en la que se pulsó.
            // Pulsar en una y soltar en otra es un gesto abortado, no una orden.
            if (cellAt(mx, my) == from) cells.get(from).onPress();
            return true;
        }

        int to = cellAt(mx, my);
        if (to < 0) {
            // Soltar FUERA de la barra desasigna. Es el gesto natural de "quítame esto de
            // aquí", y evita tener que buscar la ✖ de la fila correspondiente en la lista.
            unbind(att, from);
            return true;
        }
        if (to != from) swap(att, from, to);
        return true;
    }

    private void clearDragState() {
        if (dragFrom >= 0 && dragFrom < cells.size()) cells.get(dragFrom).setSourceOfDrag(false);
        for (SlotCell c : cells) c.setDropTarget(false);
        dragFrom = -1;
        dragOverPos = -1;
        dragConfirmed = false;
    }

    /** Cancela el arrastre sin aplicarlo. La pantalla lo llama al cerrarse o al reconstruir. */
    public void cancelDrag() { clearDragState(); }

    public boolean isDragging() { return dragConfirmed; }

    private void unbind(PlayerStatsAttachment att, int pos) {
        Occupant o = occupantOf(att, pos);
        if (o.kiSlot() >= 0) {
            att.techniques().bind(-1, o.kiSlot());                       // optimista
            PacketDistributor.sendToServer(TechniquePacket.bind(o.kiSlot(), -1));
        } else if (o.physical() != null) {
            att.techniques().bindPhysical(-1, o.physical());
            PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(o.physical(), -1));
        }
    }

    /**
     * Intercambia el contenido de dos posiciones.
         * Se leen LOS DOS ocupantes antes de escribir nada: bind() limpia la posición anterior de
     * la técnica que mueve, así que escribir uno y luego leer el otro devolvería el estado ya
     * modificado y el segundo se perdería.
     */
    private void swap(PlayerStatsAttachment att, int from, int to) {
        Occupant a = occupantOf(att, from);
        Occupant b = occupantOf(att, to);

        place(att, to, a);
        place(att, from, b);
    }

    private void place(PlayerStatsAttachment att, int pos, Occupant o) {
        if (o.kiSlot() >= 0) {
            att.techniques().bind(pos, o.kiSlot());                      // optimista
            PacketDistributor.sendToServer(TechniquePacket.bind(o.kiSlot(), pos));
        } else if (o.physical() != null) {
            att.techniques().bindPhysical(pos, o.physical());
            PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(o.physical(), pos));
        }
        // Un hueco vacío no se "coloca": la posición ya quedó libre al mover al otro ocupante.
    }

    // ── Render ───────────────────────────────────────────────────────────────

    /**
     * Refresca el pintor de icono de cada celda. Se llama en render y no en init: el ocupante
     * cambia con cada sync del servidor y con cada asignación optimista, y un pintor capturado
     * en init seguiría enseñando la técnica anterior.
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

    /** Icono flotante bajo el cursor mientras se arrastra. Va DESPUÉS de lo demás. */
    public void renderDragGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (!dragConfirmed || dragFrom < 0) return;
        SlotCell src = cells.get(dragFrom);
        int s = src.innerSize();
        src.paintIconAt(g, mouseX - s / 2, mouseY - s / 2);
    }

    /** Tooltip del ocupante bajo el cursor. null si no hay ninguno o si se está arrastrando. */
    public Component tooltipAt(PlayerStatsAttachment att, int mouseX, int mouseY) {
        if (att == null || dragConfirmed) return null;
        int i = cellAt(mouseX, mouseY);
        if (i < 0) return null;

        KiTechnique ki = att.techniques().slot(att.techniques().binding(i));
        if (ki != null) return ki.displayName();
        PhysicalTechnique ph = att.techniques().physicalBinding(i);
        if (ph != null) return Component.translatable(ph.nameKey());
        return Component.translatable("screen.zenkai.technique.slot_empty");
    }

    public int x() { return x; }
    public int y() { return y; }
    public int bottom() { return y + HEIGHT + 4; }

    public List<? extends AbstractWidget> cells() { return cells; }
}