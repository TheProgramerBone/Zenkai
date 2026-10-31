package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.CommonPanel;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base de las pantallas de panel SIN pestañas: deseos de Shenlong, selección de raza/estilo,
 * y cualquier diálogo modal del mod.
 *
 * ZenkaiMenuScreen ya hacía esto para las pestañas, pero las pantallas de deseo nunca tuvieron
 * su equivalente y acabaron en tres estéticas distintas: dos con panel y botones de texto
 * suelto (revivir mascota, aldeano encantado), tres con GUI vanilla cruda sin panel siquiera
 * (inmortalidad, revivir jugador, puntos de entrenamiento) y una de contenedor gris. El panel
 * y el pie no son decoración: son lo que hace que el jugador reconozca que sigue dentro del
 * mismo sistema tras pulsar un deseo.
 *
 * QUÉ APORTA:
 *   - panel centrado + título dorado sobre el panel (mismo ScreenTitle que las pestañas);
 *   - pie de página estándar Volver / Confirmar con PanelButton, alineado siempre igual;
 *   - `parent` y onClose() que vuelve a él, sin que cada hija reimplemente el assert;
 *   - helpers de layout (contentLeft/Right/Width/centerX) para que nadie vuelva a escribir
 *     `panelLeft + BG_W / 2` a mano y se desvíe 2 px.
 *
 * Las hijas implementan initContent() y dibujan en renderContent(), que se llama con el panel
 * YA pintado y antes de los widgets: así el texto nunca tapa un botón.
 */
public abstract class ZenkaiPanelScreen extends Screen {

    /** Tamaño real del panel esta pantalla — puede encoger por debajo de CommonPanel.IDEAL en
     *  ventanas pequeñas (ver CommonPanel). Calculado en init(), no constante. */
    protected int BG_W;
    protected int BG_H;

    /** Margen lateral del contenido dentro del panel. */
    protected static final int PAD = 14;
    /** Primer Y utilizable dentro del panel. Mismo valor que las pestañas. */
    protected static final int CONTENT_TOP = ScreenTitle.CONTENT_TOP;
    /** Altura reservada al pie de página (botón + aire). */
    protected static final int FOOTER_H = PanelButton.H + 12;

    protected final Minecraft mc = Minecraft.getInstance();
    @Nullable protected final Screen parent;

    protected int panelLeft;
    protected int panelTop;

    @Nullable protected PanelButton confirmButton;
    @Nullable protected PanelButton backButton;

    protected ZenkaiPanelScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ── Ganchos de las hijas ─────────────────────────────────────────────────

    /** Widgets propios. Se llama con panelLeft/panelTop ya calculados. */
    protected abstract void initContent();

    /** Dibujo propio, con el panel pintado y antes de los widgets. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Acción del botón primario. Por defecto no hace nada y solo cierra. */
    protected void onConfirm() { onClose(); }

    /** false en pantallas puramente informativas o de menú (Shenlong lista sus deseos). */
    protected boolean hasFooter() { return true; }

    /** Permite a la hija apagar Confirmar mientras falten datos (nombre vacío, lista vacía). */
    protected boolean confirmEnabled() { return true; }

    protected Component confirmLabel() { return Component.translatable("screen.zenkai.gui.confirm"); }
    protected Component backLabel()    { return Component.translatable("screen.zenkai.gui.back"); }

    /** Color del título. Los deseos lo tiñen de verde dragón sin meter §2 en el lang. */
    protected int titleColor() { return ScreenTitle.COLOR; }

    /** Fondo del panel. Por defecto el beige común; ShenronPanelScreen lo cambia por el de la familia divina. */
    protected void drawPanel(GuiGraphics g) { CommonPanel.blit(g, panelLeft, panelTop, BG_W, BG_H); }

    /** true si el fondo es OSCURO: el texto de los helpers lleva sombra (sobre beige nunca, ver PanelText). */
    protected boolean darkPanel() { return false; }

    /** Color de la regla de drawDivider. */
    protected int dividerColor() { return ZenkaiPalette.SEPARATOR; }

    // ── Layout ───────────────────────────────────────────────────────────────

    protected int contentLeft()   { return panelLeft + PAD; }
    protected int contentRight()  { return panelLeft + BG_W - PAD; }
    protected int contentWidth()  { return BG_W - PAD * 2; }
    protected int centerX()       { return panelLeft + BG_W / 2; }
    /** Último Y utilizable antes del pie de página. */
    protected int contentBottom() { return panelTop + BG_H - (hasFooter() ? FOOTER_H : PAD); }

    @Override
    protected final void init() {
        var w = CommonPanel.fitAxis(this.width, CommonPanel.MARGIN, CommonPanel.MARGIN);
        var h = CommonPanel.fitAxis(this.height, CommonPanel.TITLE_RESERVE, CommonPanel.MARGIN);
        this.BG_W = w.size();
        this.BG_H = h.size();
        this.panelLeft = w.pos();
        this.panelTop  = h.pos();

        initContent();

        if (hasFooter()) {
            // Volver a la izquierda y Confirmar a la derecha, no al revés: la acción positiva
            // cae bajo la mano derecha del ratón, como en el resto de diálogos del juego.
            int y = panelTop + BG_H - PAD - PanelButton.H;
            int gap = 10;
            int totalW = PanelButton.W * 2 + gap;
            int x = centerX() - totalW / 2;

            backButton = PanelButton.secondary(x, y, backLabel(), this::onClose);
            confirmButton = PanelButton.primary(x + PanelButton.W + gap, y, confirmLabel(), this::onConfirm);
            addRenderableWidget(backButton);
            addRenderableWidget(confirmButton);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        drawPanel(g);

        ScreenTitle.drawAbovePanel(g, this.font, this.title, centerX(), panelTop, titleColor());

        renderContent(g, mouseX, mouseY, partialTick);

        if (confirmButton != null) confirmButton.active = confirmEnabled();

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** El fondo lo pinta render(); anularlo aquí evita el doble oscurecido de vanilla. */
    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    // ── Helpers de texto ─────────────────────────────────────────────────────

    /** Texto centrado SIN sombra: el estándar para lo que va sobre el beige del panel. */
    protected void drawCenteredOnPanel(GuiGraphics g, Component text, int y, int color) {
        g.drawString(this.font, text, centerX() - this.font.width(text) / 2, y, color, darkPanel());
    }

    /**
     * Párrafo centrado y ajustado al ancho útil. Devuelve el Y siguiente libre.
     * Existe porque las descripciones de los deseos se salían del panel por la derecha: se
     * dibujaban con drawCenteredString sin medir nada.
     */
    protected int drawWrappedOnPanel(GuiGraphics g, Component text, int y, int color) {
        for (var line : this.font.split(text, contentWidth())) {
            g.drawString(this.font, line, centerX() - this.font.width(line) / 2, y, color, darkPanel());
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    /** Divisor horizontal del ancho del contenido. Un solo estilo para el mod. */
    protected void drawDivider(GuiGraphics g, int y) {
        g.fill(contentLeft(), y, contentRight(), y + 1, dividerColor());
    }

    @Override
    public void onClose() {
        if (parent != null && mc != null) mc.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}