package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.overlay.HudAnchor;
import com.hmc.zenkai.client.overlay.HudOrientation;
import com.hmc.zenkai.client.overlay.TechniqueHotbarOverlay;
import com.hmc.zenkai.client.overlay.TechniqueHudLayout;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Colocación del HUD de técnicas: se arrastra el bloque a donde se quiera y se guarda.
 *
 * POR QUÉ ARRASTRANDO Y NO CON DOS CAMPOS NUMÉRICOS. El desplazamiento se decide a ojo: el
 * jugador quiere la barra "un poco más arriba, que tapa el chat", no en (−4, −37). Con campos
 * numéricos habría que teclear, salir, mirar, volver a entrar y corregir, y cada iteración
 * cuesta media docena de clics. Aquí la barra es exactamente el widget que se está moviendo.
 *
 * EL ANCLA SE DEDUCE DE DÓNDE SE SUELTA, no se elige aparte. El jugador piensa en "esta
 * esquina", no en "ancla inferior derecha con desplazamiento negativo"; lo segundo es la forma
 * de guardarlo para que sobreviva a un cambio de resolución, y es trabajo del programa, no suyo.
 * Se muestra cuál ha quedado para que quien sí lo entienda pueda comprobarlo.
 *
 * NO hay panel beige de fondo a propósito: se coloca sobre lo que se verá en juego. Un panel
 * ocupando el centro cambiaría el espacio disponible y llevaría a colocar la barra donde
 * estorba menos AL PANEL, no al juego.
 */
public class HudPlacementScreen extends Screen {

    private static final int SNAP_DISTANCE = 12;

    @Nullable private final Screen parent;

    private HudAnchor anchor;
    private HudOrientation orientation;
    private int offsetX, offsetY;

    /** Desfase entre la esquina del bloque y el punto donde se agarró. */
    private int grabDX, grabDY;
    private boolean dragging = false;

    public HudPlacementScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.zenkai.hud_placement.title"));
        this.parent = parent;
        this.anchor = ClientConfig.hudAnchor();
        this.orientation = ClientConfig.hudOrientation();
        this.offsetX = ClientConfig.hudOffsetX();
        this.offsetY = ClientConfig.hudOffsetY();
    }

    @Override
    protected void init() {
        int y = this.height - 8 - PanelButton.H;
        int gap = 8;
        int total = PanelButton.W * 3 + gap * 2;
        int x = this.width / 2 - total / 2;

        addRenderableWidget(PanelButton.secondary(x, y,
                Component.translatable("screen.zenkai.gui.back"), this::onClose));

        addRenderableWidget(PanelButton.secondary(x + PanelButton.W + gap, y,
                Component.translatable("screen.zenkai.hud_placement.rotate"), this::rotate));

        addRenderableWidget(PanelButton.primary(x + (PanelButton.W + gap) * 2, y,
                Component.translatable("screen.zenkai.gui.save"), this::save));
    }

    /**
     * Cambiar de orientación conserva el ancla y REINICIA el desplazamiento.
     * Un offset ajustado para una columna vertical de 196 px no significa nada para una fila
     * horizontal de 196 px de ancho: el bloque acabaría fuera de la pantalla o encima del chat.
     * Volver al ancla limpio deja al jugador reajustando desde un punto sensato.
     */
    private void rotate() {
        orientation = (orientation == HudOrientation.VERTICAL)
                ? HudOrientation.HORIZONTAL : HudOrientation.VERTICAL;
        offsetX = 0;
        offsetY = 0;
    }

    private void save() {
        ClientConfig.setHudPlacement(anchor, orientation, offsetX, offsetY);
        onClose();
    }

    private TechniqueHudLayout layout() {
        // avoidHotbar en false: aquí se ve el bloque DONDE SE SUELTA. Si saltara solo por
        // encima de la hotbar mientras se arrastra, el jugador estaría persiguiendo un objeto
        // que se le escapa. El ajuste ya se aplicará en juego.
        return TechniqueHudLayout.of(this.width, this.height, anchor, orientation,
                offsetX, offsetY, false);
    }

    // ── Arrastre ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            TechniqueHudLayout l = layout();
            if (l.contains(mx, my)) {
                dragging = true;
                grabDX = (int) mx - l.x();
                grabDY = (int) my - l.y();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mx, my, button, dx, dy);
        applyDrag((int) mx - grabDX, (int) my - grabDY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    /**
     * Recalcula ancla y desplazamiento para que el bloque quede en (targetX, targetY).
     * El ancla se reelige en CADA movimiento, no solo al soltar: así el indicador de abajo va
     * cambiando mientras se arrastra y el jugador ve a qué borde se va a enganchar antes de
     * decidir. Elegirla al final sería una sorpresa.
     */
    private void applyDrag(int targetX, int targetY) {
        int w = TechniqueHudLayout.blockWidth(orientation.isHorizontal());
        int h = TechniqueHudLayout.blockHeight(orientation.isHorizontal());

        targetX = Math.max(0, Math.min(targetX, this.width - w));
        targetY = Math.max(0, Math.min(targetY, this.height - h));

        anchor = HudAnchor.nearest(targetX, targetY, this.width, this.height, w, h);
        offsetX = targetX - anchor.originX(this.width, w);
        offsetY = targetY - anchor.originY(this.height, h);

        // Imantado: a menos de 12 px del ancla, el desplazamiento se anula. Clavar un cero a
        // mano con el ratón es imposible, y "pegado a la esquina" es la posición que más gente
        // quiere. Solo se imanta el eje que está cerca, para poder pegarse a un borde y seguir
        // ajustando libremente el otro.
        if (Math.abs(offsetX) <= SNAP_DISTANCE) offsetX = 0;
        if (Math.abs(offsetY) <= SNAP_DISTANCE) offsetY = 0;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Velo MUY tenue: hay que seguir viendo el mundo para juzgar dónde estorba la barra.
        g.fill(0, 0, this.width, this.height, 0x30000000);
        // Banda oscura bajo el pie: los botones van sobre el mundo, y con un cielo claro detrás
        // el texto blanco desaparece. Es el único sitio de esta pantalla donde tapar es correcto.
        int footTop = this.height - PanelButton.H - 16;
        g.fill(0, footTop, this.width, this.height, 0xA0000000);

        drawAnchorGuides(g);

        TechniqueHudLayout l = layout();
        Minecraft mc = Minecraft.getInstance();

        // Halo del bloque: lo distingue del HUD real y marca la zona agarrable, que es mayor
        // que los iconos cuando hay posiciones vacías.
        boolean hover = dragging || l.contains(mouseX, mouseY);
        g.fill(l.x() - 3, l.y() - 3, l.x() + l.width() + 3, l.y() + l.height() + 3,
                hover ? 0x60FFD966 : 0x30FFFFFF);

        if (mc.player != null) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
            // Se pinta la barra REAL, no una maqueta: colocar una aproximación y encontrarse
            // otra cosa en juego es el fallo clásico de estas pantallas.
            TechniqueHotbarOverlay.renderBar(g, mc, att, l, 0);
        }

        ScreenTitle.drawCentered(g, this.font, this.title, this.width / 2, 10);

        int y = 26;
        drawInfo(g, y, "screen.zenkai.hud_placement.hint", ZenkaiPalette.TEXT_DIM);
        y += 12;
        drawInfo(g, y, anchor.nameKey(), ZenkaiPalette.GOLD);
        y += 11;
        g.drawCenteredString(this.font,
                Component.translatable(orientation.nameKey())
                        .append(Component.literal("   " + offsetX + ", " + offsetY)),
                this.width / 2, y, ZenkaiPalette.TEXT_DIM);
        // Los widgets van AL FINAL: por encima del velo, de las guías y del bloque arrastrable.
        // Sin esta llamada los botones existen y responden a nada, porque Screen solo los pinta
        // aquí — que es justo lo que pasaba: el pie entero era invisible.
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawInfo(GuiGraphics g, int y, String key, int color) {
        g.drawCenteredString(this.font, Component.translatable(key), this.width / 2, y, color);
    }

    /** Cruces en las ocho anclas: enseñan a dónde se puede imantar sin tener que probarlas. */
    private void drawAnchorGuides(GuiGraphics g) {
        int w = TechniqueHudLayout.blockWidth(orientation.isHorizontal());
        int h = TechniqueHudLayout.blockHeight(orientation.isHorizontal());

        for (HudAnchor a : HudAnchor.values()) {
            int ax = a.originX(this.width, w);
            int ay = a.originY(this.height, h);
            boolean active = (a == anchor);
            int color = active ? ZenkaiPalette.BORDER_OUT : 0x40FFFFFF;
            g.fill(ax - 4, ay, ax + 5, ay + 1, color);
            g.fill(ax, ay - 4, ax + 1, ay + 5, color);
        }
    }

    @Override
    public void onClose() {
        if (parent != null && this.minecraft != null) this.minecraft.setScreen(parent);
        else super.onClose();
    }

    /**
     * Fondo anulado a propósito.
     * Screen#renderBackground aplica el desenfoque de pantalla de pausa y el velo de menú, y lo
     * hace ANTES de render(), así que lo que dibuja esta pantalla quedaba por debajo: el
     * mundo salía borroso y la barra que se está colocando, con él. Aquí el mundo nítido no es
     * decorativo — es la referencia contra la que el jugador decide dónde estorba menos la
     * barra, y un fondo desenfocado hace esa decisión imposible.
     * El velo tenue y la banda del pie los pinta render() por su cuenta.
     */
    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }
}