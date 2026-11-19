package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.util.ZenkaiNumbers;
import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Piezas comunes de la plantilla de los minijuegos de Training (Shadow, Target Practice,
 * Meditation y sus resultados): la FICHA de opción y la INSIGNIA de pie. Viven aquí para no copiar
 * el dibujo en cuatro pantallas. El texto entero va sobre la placa oscura, así que solo usa colores
 * TEXT*, nunca los *_ON_PANEL.
 */
public final class TrainingWidgets {

    private TrainingWidgets() {}

    /** Geometría de la plantilla (px de GUI, relativos al panel). */
    public static final int CHIP_X = 24, CHIP_W = 208, CHIP_H = 18, CHIP_GAP = 6;
    public static final int BADGE_W = CHIP_W, BADGE_H = 16, BADGE_STEP = 22;
    /** Insignia suelta de los resultados: más corta y centrada. */
    public static final int BADGE_SOLO_W = 140, BADGE_SOLO_X = 58;

    /** Glifos de hud.png (16 texeles pintados a 8 px): pesa dorada = TP (la misma de la cabecera de Técnicas), orbe de Ki = récord. */
    public static final int GLYPH_TP_U = 184, GLYPH_TP_V = 48, GLYPH_RECORD_U = 232, GLYPH_RECORD_V = 32;

    /**
     * Ficha de una opción: placa oscura con la etiqueta a la izquierda y el valor a la derecha
     * entre «‹ ›». Un clic en la mitad izquierda llama a {@code prev}, en la derecha a {@code next}.
     * Con {@code prev == null} solo lleva «›» y cualquier clic llama a {@code next} (fila que abre
     * una lista). (x, y, w, h) es el interior de la placa; el marco queda 2 px por fuera.
     */
    public static final class OptionChip extends AbstractWidget {
        private final Component label;
        private final Supplier<Component> value;
        private final @Nullable Runnable prev;
        private final Runnable next;
        /** Margen izquierdo de la etiqueta (deja sitio a un icono propio de la pantalla). */
        private final int labelInset;

        public OptionChip(int x, int y, int w, int h, Component label, Supplier<Component> value,
                          @Nullable Runnable prev, Runnable next, int labelInset) {
            super(x, y, w, h, label);
            this.label = label;
            this.value = value;
            this.prev = prev;
            this.next = next;
            this.labelInset = labelInset;
        }

        public OptionChip(int x, int y, Component label, Supplier<Component> value,
                          @Nullable Runnable prev, Runnable next) {
            this(x, y, CHIP_W, CHIP_H, label, value, prev, next, 6);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            NineSlice.plateFrame(g, getX(), getY(), width, height);
            int ty = getY() + (height - 8) / 2 + 1;
            PanelText.onDark(g, font, label, getX() + labelInset, ty, ZenkaiPalette.TEXT_DIM);

            int right = getX() + width - 8;
            Component v = value.get();
            if (prev == null) {
                int c = isHoveredOrFocused() ? ZenkaiPalette.VALUE : ZenkaiPalette.GOLD;
                PanelText.onDark(g, font, Component.literal("›"), right - 4, ty, c);
                PanelText.rightOnDark(g, font, v, right - 12, ty, ZenkaiPalette.TEXT);
                return;
            }
            boolean overLeft = isMouseOver(mouseX, mouseY) && mouseX < splitX();
            boolean overRight = isMouseOver(mouseX, mouseY) && mouseX >= splitX();
            int left = right - 84;
            PanelText.onDark(g, font, Component.literal("‹"), left, ty,
                    overLeft ? ZenkaiPalette.VALUE : ZenkaiPalette.GOLD);
            PanelText.onDark(g, font, Component.literal("›"), right - font.width("›") + 1, ty,
                    overRight ? ZenkaiPalette.VALUE : ZenkaiPalette.GOLD);
            PanelText.centeredOnDark(g, font, v, (left + right) / 2 + 1, ty, ZenkaiPalette.TEXT);
        }

        /** Frontera entre «‹» y «›»: el centro del valor, NO el de la ficha. Las flechas viven en el
         *  extremo derecho, y partir por la mitad de la ficha hacía que «‹» SUBIERA el valor (no se
         *  podía volver a bajar la dificultad ni cambiar de forma una vez arriba). */
        private int splitX() { return getX() + width - 50; }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (prev != null && mouseX < splitX()) prev.run();
            else next.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /**
     * Insignia de pie: placa de {@code w}x16 con glifo de hud.png, etiqueta tenue y valor a la
     * derecha. (x, y) es el interior de la placa.
     */
    public static void badge(GuiGraphics g, Font font, int x, int y, int w, int glyphU, int glyphV,
                             Component label, Component value, int valueColor) {
        NineSlice.plateFrame(g, x, y, w, BADGE_H);
        // Con cifras largas no cabe completo: el texto manda y se sacrifica el glifo, nunca la etiqueta.
        boolean withGlyph = 14 + font.width(label) + 6 + font.width(value) + 4 <= w;
        if (withGlyph) {
            g.blit(NineSlice.PLATE_TEX, x + 3, y + 4, 8, 8, (float) glyphU, (float) glyphV, 16, 16, 256, 128);
        }
        PanelText.onDark(g, font, label, x + (withGlyph ? 14 : 5), y + 4, ZenkaiPalette.TEXT_DIM);
        PanelText.rightOnDark(g, font, value, x + w - 4, y + 4, valueColor);
    }

    /**
     * Cuerpo común de las pantallas de resultado (Target, Meditation): «TP earned», «+N TP» a
     * escala 3, una línea de detalle opcional, placa «New record!» solo si hay récord y la
     * insignia «Record» centrada abajo. {@code earned} o {@code record} null = el servidor aún no
     * respondió (se enseña «…» y no se afirma récord).
     */
    public static void result(GuiGraphics g, Font font, int panelLeft, int panelTop, int panelW, int panelH,
                              @Nullable Integer earned, @Nullable Integer record, @Nullable Component detail) {
        int cx = panelLeft + panelW / 2;
        PanelText.centeredOnPanel(g, font, Component.translatable("screen.zenkai.training.earned"),
                cx, panelTop + 44, ZenkaiPalette.MUTED_ON_PANEL);
        Component gained = earned == null ? Component.literal("…")
                : Component.translatable("screen.zenkai.training.gained", earned);
        g.pose().pushPose();
        g.pose().translate(cx, panelTop + 58, 0);
        g.pose().scale(3, 3, 1);
        g.drawString(font, gained, -font.width(gained) / 2, 0, ZenkaiPalette.OK_ON_PANEL, false);
        g.pose().popPose();
        if (detail != null) {
            PanelText.centeredOnPanel(g, font, detail, cx, panelTop + 86, ZenkaiPalette.LABEL_ON_PANEL);
        }
        if (earned != null && earned > 0 && earned.equals(record)) {
            int w = 128, y = panelTop + 100;
            NineSlice.plateFrame(g, cx - w / 2, y, w, 16);
            PanelText.centeredOnDark(g, font, Component.translatable("screen.zenkai.training_hub.new_record"),
                    cx, y + 4, ZenkaiPalette.VALUE);
        }
        badge(g, font, panelLeft + BADGE_SOLO_X, panelTop + panelH - 62, BADGE_SOLO_W, GLYPH_RECORD_U, GLYPH_RECORD_V,
                Component.translatable("screen.zenkai.training.record"),
                record == null ? Component.literal("…") : Component.translatable("screen.zenkai.training.tp", ZenkaiNumbers.screen(record)),
                ZenkaiPalette.TEXT);
    }

    /** Ficha de solo lectura (sin flechas ni clic): una canción trae su dificultad y duración fijas. */
    public static void staticChip(GuiGraphics g, Font font, int x, int y, Component label, Component value) {
        NineSlice.plateFrame(g, x, y, CHIP_W, CHIP_H);
        int ty = y + (CHIP_H - 8) / 2 + 1;
        PanelText.onDark(g, font, label, x + 6, ty, ZenkaiPalette.TEXT_DIM);
        PanelText.rightOnDark(g, font, value, x + CHIP_W - 8, ty, ZenkaiPalette.TEXT);
    }

    /**
     * Pie de las pantallas de opciones: «Potential» encima de «Record», apiladas y de lado a lado
     * de las fichas (lado a lado no cabían cifras de 4-5 dígitos). La de abajo queda sobre los
     * iconos de esquina. {@code potential}/{@code record} null = aún sin dato («…»).
     */
    public static void footer(GuiGraphics g, Font font, int panelLeft, int panelTop, int panelH,
                              @Nullable Integer potential, @Nullable Integer record) {
        int x = panelLeft + CHIP_X;
        int yRecord = panelTop + panelH - 62;
        badge(g, font, x, yRecord - BADGE_STEP, BADGE_W, GLYPH_TP_U, GLYPH_TP_V,
                Component.translatable("screen.zenkai.training.potential"),
                Component.translatable("screen.zenkai.training.tp", potential != null ? potential : "…"),
                ZenkaiPalette.VALUE);
        badge(g, font, x, yRecord, BADGE_W, GLYPH_RECORD_U, GLYPH_RECORD_V,
                Component.translatable("screen.zenkai.training.record"),
                Component.translatable("screen.zenkai.training.tp", record != null ? ZenkaiNumbers.screen(record) : "…"),
                ZenkaiPalette.TEXT);
    }
}
