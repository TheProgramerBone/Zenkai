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
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tech_led.png");
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
        drawSlotGhosts(g);
    }

    /**
     * Silueta de una ranura de máquina vacía (2026-11-24, como los iconos de ranura de Curios del mod y el escudo o la
     * armadura vacíos del inventario vanilla; PNG de tools/gen_slot_icons.py): {@code handlerIndex} es la ranura de la máquina; null = sin icono. Se dibuja en renderBg, o sea DEBAJO
     * de los ítems y del resalte, y desaparece al poner algo.
     */
    @org.jetbrains.annotations.Nullable
    protected ResourceLocation slotGhost(int handlerIndex) { return null; }

    /** Igual con la ranura entera, para pantallas donde dos handlers repiten índice (cámara de gravedad). */
    @org.jetbrains.annotations.Nullable
    protected ResourceLocation slotGhost(net.minecraft.world.inventory.Slot slot) { return slotGhost(slot.getSlotIndex()); }

    protected void drawSlotGhosts(GuiGraphics g) {
        for (net.minecraft.world.inventory.Slot s : menu.slots) {
            if (!(s instanceof net.neoforged.neoforge.items.SlotItemHandler) || s.hasItem() || !s.isActive()) continue;
            ResourceLocation tex = slotGhost(s);
            if (tex == null) continue;
            g.blit(tex, leftPos + s.x, topPos + s.y, 0, 0, 16, 16, 16, 16);
        }
        g.setColor(1f, 1f, 1f, 1f);
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

    /**
     * Cabecera de la familia desde el 2026-11-23: placa al ancho del título y LED suelto a la derecha, sin aro y centrado
     * ({@link com.hmc.zenkai.client.gui.TechHeader}). {@code led} null = sin piloto (tanques).
     */
    protected void drawHeader(GuiGraphics g, @Nullable Boolean led) {
        com.hmc.zenkai.client.gui.TechHeader.draw(g, font, title, led);
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

    /** Cristal del tanque de ki: 16x26 con alfa (reflejo y marcas). tools/gen_ki_fluids.py. */
    protected static final ResourceLocation TANK_GLASS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tech_tank.png");
    protected static final int TANK_GLASS_W = 16, TANK_GLASS_H = 26;

    /**
     * Tanque de fluido de Ki: hermano de drawVBar (mismo marco y fondo), con el sprite del fluido
     * dentro y el cristal encima. Especificación en .claude/docs/gui-tecnologia.md («Tanque de fluido de Ki»).
     * Tres capas: marco y fondo; el sprite still del fluido repetido en celdas de 16 px desde
     * abajo, SIN tinte (el color ya viene en el sprite); y el cristal tech_tank.png repetido de
     * abajo arriba, de modo que con 104 px de alto las marcas mayores caen en 1/4, 1/2 y 3/4.
     * El tanque vacío se dibuja igual: uno que aparece y desaparece mueve la lectura.
     * El recorte del fluido va con scissor, que trabaja en coordenadas ABSOLUTAS aunque la matriz
     * de renderLabels esté trasladada: por eso se le suma leftPos/topPos aquí dentro.
     *
     * @param stillSprite sprite del fluido en el atlas de bloques (zenkai:block/ki_base_still…),
     *                    o null con el tanque vacío
     */
    protected void drawKiTank(GuiGraphics g, int x, int y, int w, int h, float fraction,
                              @Nullable ResourceLocation stillSprite) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiTechPalette.BAR_BG);

        int filled = Math.round(h * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0 && stillSprite != null && minecraft != null) {
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = minecraft
                    .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(stillSprite);
            g.enableScissor(leftPos + x, topPos + y + h - filled, leftPos + x + w, topPos + y + h);
            for (int cy = y + h - 16; cy > y + h - filled - 16; cy -= 16) {
                for (int cx = x; cx < x + w; cx += 16) {
                    g.blit(cx, cy, 0, 16, 16, sprite);
                }
            }
            g.disableScissor();
        }

        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        for (int gy = y + h - TANK_GLASS_H; gy >= y; gy -= TANK_GLASS_H) {
            // La repetición de arriba se pinta sin su fila 0: ahí la marca mayor caería sobre el borde.
            int skip = gy == y ? 1 : 0;
            g.blit(TANK_GLASS, x, gy + skip, 0, skip, Math.min(w, TANK_GLASS_W), TANK_GLASS_H - skip,
                    TANK_GLASS_W, TANK_GLASS_H);
        }
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

        // Cada línea decide sola cuántos renglones necesita (TooltipText.maxWidth, menos la sangría del icono).
        record Row(@Nullable ItemStack icon, net.minecraft.util.FormattedCharSequence text, boolean indent, boolean first) {}
        int maxW = com.hmc.zenkai.client.gui.TooltipText.maxWidth(this.width);
        List<Row> rows = new java.util.ArrayList<>();
        for (TipLine l : lines) {
            boolean ic = l.icon() != null;
            List<net.minecraft.util.FormattedCharSequence> parts = font.split(l.text(), Math.max(40, maxW - (ic ? 20 : 0)));
            if (parts.isEmpty()) parts = List.of(net.minecraft.util.FormattedCharSequence.EMPTY);
            for (int i = 0; i < parts.size(); i++) rows.add(new Row(i == 0 ? l.icon() : null, parts.get(i), ic, i == 0));
        }

        int w = 0;
        int h = 0;
        for (Row r : rows) {
            w = Math.max(w, (r.indent() ? 20 : 0) + font.width(r.text()));
            h += r.icon() != null ? 18 : 11;
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w + 5 > this.width) x = Math.max(5, mouseX - w - 16);
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
        for (Row r : rows) {
            if (r.icon() != null) {
                g.renderFakeItem(r.icon(), x, cy);
                g.drawString(font, r.text(), x + 20, cy + 4, ZenkaiTechPalette.TEXT_ON_SCREEN, true);
                cy += 18;
            } else {
                g.drawString(font, r.text(), x + (r.indent() ? 20 : 0), cy, ZenkaiTechPalette.TEXT_ON_SCREEN, true);
                cy += 11;
            }
        }

        g.pose().popPose();
    }

    /** El título va dibujado por drawTitle desde la hija: aquí no se pinta nada por defecto. */
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) { }
}