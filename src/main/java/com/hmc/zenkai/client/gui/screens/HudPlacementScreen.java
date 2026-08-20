package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
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
 * POR QUÉ ARRASTRANDO Y NO CON DOS CAMPOS NUMÉRICOS. El desplazamiento se decide a ojo: el
 * jugador quiere la barra "un poco más arriba, que tapa el chat", no en (−4, −37). Con campos
 * numéricos habría que teclear, salir, mirar, volver a entrar y corregir, y cada iteración
 * cuesta media docena de clics. Aquí la barra es exactamente el widget que se está moviendo.
 * EL ANCLA SE DEDUCE DE DÓNDE SE SUELTA, no se elige aparte. El jugador piensa en "esta
 * esquina", no en "ancla inferior derecha con desplazamiento negativo"; lo segundo es la forma
 * de guardarlo para que sobreviva a un cambio de resolución, y es trabajo del programa, no suyo.
 * Se muestra cuál ha quedado para que quien sí lo entienda pueda comprobarlo.
 * NO hay panel beige de fondo a propósito: se coloca sobre lo que se verá en juego. Un panel
 * ocupando el centro cambiaría el espacio disponible y llevaría a colocar la barra donde
 * estorba menos AL PANEL, no al juego.
 */
public class HudPlacementScreen extends Screen {

    private static final int SNAP_DISTANCE = 12;

    @Nullable private final Screen parent;

    private HudAnchor anchor;
    private int offsetX, offsetY;
    private HudOrientation orientation;
    /** Se edita aquí porque es parte de la colocación, no una preferencia suelta. En la
     *  PREVISUALIZACIÓN se ignora a propósito (ver layout()): ahí interesa ver el bloque donde
     *  se suelta, no dónde acabará esquivando la hotbar. */
    private boolean avoidHotbar;

    /** Desfase entre la esquina del bloque y el punto donde se agarró. */
    private int grabDX, grabDY;
    private boolean dragging = false;

    public HudPlacementScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.zenkai.hud_placement.title"));
        this.parent = parent;
        this.anchor = ClientConfig.hudAnchor();
        this.orientation = ClientConfig.hudOrientation();
        this.avoidHotbar = ClientConfig.hudAvoidHotbar();
        this.offsetX = ClientConfig.hudOffsetX();
        this.offsetY = ClientConfig.hudOffsetY();
    }

    @Override
    protected void init() {
        int y = this.height - 8 - PanelButton.H;
        int gap = 8;
        int total = PanelButton.W * 4 + gap * 3;
        int x = this.width / 2 - total / 2;

        addRenderableWidget(PanelButton.secondary(x, y,
                Component.translatable("screen.zenkai.gui.back"), this::onClose));

        addRenderableWidget(PanelButton.secondary(x + PanelButton.W + gap, y,
                Component.translatable("screen.zenkai.hud_placement.rotate"), this::rotate));

        addRenderableWidget(PanelButton.primary(x + (PanelButton.W + gap) * 2, y,
                Component.translatable("screen.zenkai.gui.save"), this::save));

        addRenderableWidget(PanelButton.secondary(x + (PanelButton.W + gap) * 3, y,
                // options.on/off son claves VANILLA: ya traducidas a todos los idiomas y las
                // mismas que usa ToggleButton, así que el interruptor de aquí y los de la lista
                // de config dicen exactamente lo mismo.
                Component.translatable("screen.zenkai.hud.avoid_hotbar",
                        Component.translatable(avoidHotbar ? "options.on" : "options.off")),
                () -> { avoidHotbar = !avoidHotbar; rebuildWidgets(); }));
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
        ClientConfig.setHudPlacement(anchor, orientation, offsetX, offsetY, avoidHotbar);
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
        g.fill(0, 0, this.width, this.height, ZenkaiPalette.WORLD_VEIL);

        // Franjas opacas arriba y abajo: el texto de estado y los botones tienen que leerse
        // sobre CUALQUIER cosa que haya detrás, y el velo tenue no basta contra un cielo claro.
        g.fill(0, 0, this.width, 62, ZenkaiPalette.WORLD_BAND);
        g.fill(0, this.height - PanelButton.H - 16, this.width, this.height, ZenkaiPalette.WORLD_BAND);

        drawAnchorGuides(g);

        TechniqueHudLayout l = layout();
        Minecraft mc = Minecraft.getInstance();

        // Halo del bloque: lo distingue del HUD real y marca la zona agarrable, que es mayor
        // que los iconos cuando hay posiciones vacías.
        boolean hover = dragging || l.contains(mouseX, mouseY);
        g.fill(l.x() - 3, l.y() - 3, l.x() + l.width() + 3, l.y() + l.height() + 3,
                hover ? ZenkaiPalette.GRAB_ACTIVE : ZenkaiPalette.GRAB_IDLE);

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
        PanelText.centeredOnDark(g, this.font,
                Component.translatable(orientation.nameKey())
                        .append(Component.literal("   " + offsetX + ", " + offsetY)),
                this.width / 2, y, ZenkaiPalette.TEXT_DIM);

        // LOS BOTONES LOS DIBUJA super.render. Sin esta llamada, Volver, Rotar y Guardar
        // existen, ocupan su sitio y responden al ratón, pero no se pintan: la pantalla parece
        // no tener salida. Las demás pantallas del mod no lo sufren porque heredan de
        // ZenkaiMenuScreen, que ya lo hace por ellas.
        //
        // Va al FINAL para que los botones queden por encima del bloque arrastrado: si la barra
        // se coloca al final, taparía a Guardar.
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Sobre el mundo: CON sombra, o el texto desaparece contra un cielo claro. */
    private void drawInfo(GuiGraphics g, int y, String key, int color) {
        PanelText.centeredOnDark(g, this.font, Component.translatable(key), this.width / 2, y, color);
    }

    /** Cruces en las ocho anclas: enseñan a dónde se puede imantar sin tener que probarlas. */
    private void drawAnchorGuides(GuiGraphics g) {
        int w = TechniqueHudLayout.blockWidth(orientation.isHorizontal());
        int h = TechniqueHudLayout.blockHeight(orientation.isHorizontal());

        for (HudAnchor a : HudAnchor.values()) {
            int ax = a.originX(this.width, w);
            int ay = a.originY(this.height, h);
            boolean active = (a == anchor);
            int color = active ? ZenkaiPalette.BORDER_OUT : ZenkaiPalette.GUIDE;
            g.fill(ax - 4, ay, ax + 5, ay + 1, color);
            g.fill(ax, ay - 4, ax + 1, ay + 5, color);
        }
    }

    /** El velo lo pinta render(); el de vanilla lo oscurecería el doble y taparía el mundo,
     *  que es justo lo que hay que ver para juzgar dónde estorba la barra. */
    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void onClose() {
        if (parent != null && this.minecraft != null) this.minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}