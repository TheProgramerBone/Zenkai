package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Base de las pantallas de MÁQUINA: banco de scouter hoy, y lo que venga cuando el mod tenga
 * energía, cápsulas o procesado. Es a la familia tecnológica lo que ZenkaiPanelScreen es a la
 * familia de personaje.
 * QUÉ APORTA, y por qué no es una clase vacía con un blit dentro:
 *   - centrado y tamaño de panel únicos (256x256), para que dos máquinas no elijan distinto;
 *   - título sobre la placa oscura de cabecera, con el cian y la sombra ya decididos;
 *   - barra de progreso, indicador de segmentos y piloto, que es exactamente el vocabulario
 *     que repite cualquier máquina y que hoy cada pantalla se dibujaría a mano con g.fill;
 *   - tooltip con ICONO de ítem por línea. Vanilla no puede: renderComponentTooltip solo pinta
 *     texto y la ruta con iconos (renderTooltipInternal + ClientTooltipComponent) es privada.
 * NO trae layout: dónde va el slot o la lista es de cada máquina, porque va pegado a su
 * textura de fondo. Lo que sí es común es cómo se dibuja cada pieza.
 * REPARTO DEL RENDER (importa, porque el orden de vanilla no es obvio):
 *   renderBg()      → el fondo. Coordenadas ABSOLUTAS (leftPos/topPos incluidos).
 *   renderLabels()  → entero el contenido. La matriz ya está trasladada al panel, así que aquí
 *                     las coordenadas son LOCALES... pero el ratón que llega sigue siendo
 *                     ABSOLUTO. De ahí los helpers localMouseX/Y.
 *   renderFloating()→ lo que va por encima de completo, tooltips incluidos. Absolutas otra vez.
 */
public abstract class TechPanelScreen<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> {

    public static final int PANEL_W = 256;
    public static final int PANEL_H = 256;

    /** Piloto de la familia: atlas de 32x16, dos celdas de 16x16 (apagado, encendido). */
    protected static final ResourceLocation LED =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/tech_led.png");
    protected static final int LED_SIZE = 16;

    protected TechPanelScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    /** Fondo de esta máquina. 256x256, con los pozos y las zonas ya pintados. */
    protected abstract ResourceLocation background();

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(background(), leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        // Tooltip del ÍTEM bajo el cursor. Sin esta línea los slots no dicen ni el nombre.
        // Va antes de renderFloating para que el tooltip propio quede por encima si ambos
        // cayeran en el mismo punto.
        renderTooltip(g, mouseX, mouseY);
        renderFloating(g, mouseX, mouseY);
    }

    /** Encima de completo: tooltips propios. Coordenadas absolutas. Por defecto, nada. */
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) { }

    protected int localMouseX(int mouseX) { return mouseX - leftPos; }
    protected int localMouseY(int mouseY) { return mouseY - topPos; }

    // ── Vocabulario común de máquina (coordenadas LOCALES) ───────────────────

    /**
     * Título en mayúsculas y negrita sobre la placa oscura de cabecera. Cian y CON sombra:
     * la placa es fondo oscuro, así que aquí manda la regla de _ON_SCREEN aunque el resto de
     * la carcasa sea clara.
     */
    protected void drawTitle(GuiGraphics g, int x, int y) {
        Component styled = Component.literal(title.getString().toUpperCase(Locale.ROOT))
                .withStyle(ChatFormatting.BOLD);
        PanelText.onDark(g, font, styled, x, y, ZenkaiTechPalette.TITLE);
    }

    /** Etiqueta "Inventory" sobre la bandeja. Bandeja = carcasa: oscuro y sin sombra. */
    protected void drawInventoryLabel(GuiGraphics g, int x, int y) {
        PanelText.onPanel(g, font, playerInventoryTitle, x, y,
                ZenkaiTechPalette.MUTED_ON_CHASSIS);
    }

    /**
     * Barra de progreso continua. El canal se dibuja SIEMPRE, aunque la fracción sea 0: una
     * barra que aparece y desaparece mueve el resto de la lectura cada vez que arranca un
     * trabajo.
     */
    protected void drawBar(GuiGraphics g, int x, int y, int w, int h, float fraction) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiTechPalette.BAR_BG);
        int filled = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, ZenkaiTechPalette.BAR_FILL);
        }
    }

    /**
     * Barra vertical, llena de abajo arriba. Es la orientación de un depósito: la energía
     * "sube" y "baja", y una barra horizontal para eso se lee como progreso.
     */
    protected void drawVBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int fill) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiTechPalette.BAR_BG);
        int filled = Math.round(h * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) g.fill(x, y + h - filled, x + w, y + h, fill);
    }

    /**
     * Indicador de carga por segmentos: celdas pegadas con 1 px de separación.
     * `max` = 1 sale como un solo segmento sin ningún caso especial — una mejora binaria es
     * una de un nivel, no otra clase de cosa.
     */
    protected void drawSegments(GuiGraphics g, int x, int y, int max, int filled,
                                int segW, int segH, boolean dim) {
        for (int i = 0; i < max; i++) {
            int sx = x + i * (segW + 1);
            g.fill(sx, y, sx + segW, y + segH, ZenkaiTechPalette.BAR_FRAME);
            int color = i < filled
                    ? (dim ? ZenkaiTechPalette.DIM_ON_SCREEN : ZenkaiTechPalette.SEG_ON)
                    : ZenkaiTechPalette.SEG_OFF;
            g.fill(sx + 1, y + 1, sx + segW - 1, y + segH - 1, color);
        }
    }

    /** Piloto de estado. Celda 0 apagado, celda 1 encendido. */
    protected void drawLed(GuiGraphics g, int x, int y, boolean on) {
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(LED, x, y, on ? LED_SIZE : 0, 0, LED_SIZE, LED_SIZE, LED_SIZE * 2, LED_SIZE);
    }

    // ── Tooltip con iconos ───────────────────────────────────────────────────

    /** Una línea de tooltip: icono opcional a la izquierda y texto ya estilizado. */
    public record TipLine(@Nullable ItemStack icon, Component text) {
        public static TipLine of(Component text) { return new TipLine(null, text); }
    }

    /**
     * Tooltip de la familia. Fondo oscuro, así que el texto va CON sombra.
     * Se dibuja desde renderFloating(), después de super.render(), para quedar por encima de
     * los slots y del ítem que el jugador lleve agarrado.
     */
    protected void drawTooltip(GuiGraphics g, int mouseX, int mouseY, List<TipLine> lines) {
        if (lines.isEmpty()) return;

        int w = 0;
        int h = 0;
        for (TipLine l : lines) {
            w = Math.max(w, (l.icon() != null ? 20 : 0) + font.width(l.text()));
            h += l.icon() != null ? 18 : 11;
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w + 5 > this.width) x = mouseX - w - 16;
        if (y + h + 5 > this.height) y = this.height - h - 5;
        if (y < 5) y = 5;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);

        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, ZenkaiTechPalette.TOOLTIP_BG);
        g.fill(x - 4, y - 5, x + w + 4, y - 4, ZenkaiTechPalette.TOOLTIP_EDGE);
        g.fill(x - 4, y + h + 4, x + w + 4, y + h + 5, ZenkaiTechPalette.TOOLTIP_EDGE);
        g.fill(x - 5, y - 4, x - 4, y + h + 4, ZenkaiTechPalette.TOOLTIP_EDGE);
        g.fill(x + w + 4, y - 4, x + w + 5, y + h + 4, ZenkaiTechPalette.TOOLTIP_EDGE);

        int cy = y;
        for (TipLine l : lines) {
            if (l.icon() != null) {
                g.renderFakeItem(l.icon(), x, cy);
                PanelText.onDark(g, font, l.text(), x + 20, cy + 4,
                        ZenkaiTechPalette.TEXT_ON_SCREEN);
                cy += 18;
            } else {
                PanelText.onDark(g, font, l.text(), x, cy, ZenkaiTechPalette.TEXT_ON_SCREEN);
                cy += 11;
            }
        }

        g.pose().popPose();
    }

    /** El título va dibujado por drawTitle desde la hija: aquí no se pinta nada por defecto. */
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) { }
}