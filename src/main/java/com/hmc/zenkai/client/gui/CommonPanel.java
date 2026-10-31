package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Textura y tamaño compartidos del panel beige "common_screen.png". La usan tanto las dos
 * jerarquías base (ZenkaiPanelScreen, ZenkaiMenuScreen) como las pantallas que la reblittean a
 * mano porque no pueden extender ninguna de las dos (MeditationScreen/TargetPracticeScreen
 * implementan TrainingMinigameScreen con su propia máquina de estados; ShadowResultScreen se
 * abre sobre el mundo a mitad de partida). Antes cada una declaraba su propia copia de
 * BG_TEX/BG_W/BG_H; unificarlo aquí es lo que permite que las cinco cambien de tamaño sin
 * desincronizarse entre ellas.
 *
 * El panel era un blit fijo de 256x256 centrado con (this.width-BG_W)/2 — con GUI Scale alto
 * this.width/this.height se hacen pocos (son unidades GUI, YA deformadas por ese ajuste) y un
 * panel de 256 unidades ocupa proporcionalmente mucha más pantalla real de la que ocupaba a GUI
 * Scale 2 (el de siempre): el título (dibujado FUERA del panel, encima, ver ScreenTitle) y la
 * fila de pestañas (fuera, debajo, ver ZenkaiMenuScreen) se quedaban sin sitio. `fitAxis` (ver
 * PanelSizing) resuelve las dos partes del problema: bloquea el tamaño real del panel al que
 * tendría a GUI Scale 2 en vez de perseguir el GUI Scale actual, y reserva sitio para el
 * título/la fila de pestañas ANTES de centrarlo — ninguno de los dos puede quedar fuera de la
 * ventana ni superpuesto.
 *
 * blit() escala la textura ENTERA (un solo blit, destino distinto al origen) en vez de trocearla
 * en 9 recortes: common_screen.png tiene un degradado de luz pintado a mano en el anillo, así que
 * un recorte que se estira por separado se notaría como una costura — con un único blit el aspecto
 * es idéntico al de siempre en el caso normal (IDEAL x IDEAL, sin ningún cambio visual) y solo se
 * ve el anillo un poco más grueso/fino en el caso raro de panel encogido.
 */
public final class CommonPanel {
    private CommonPanel() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/common_screen.png");

    /** Tamaño "ideal": el que asumen los layouts de contenido de cada pantalla hija, y el lado
     *  real del PNG. El panel nunca crece más allá de esto — solo encoge cuando no cabe. */
    public static final int IDEAL = 256;

    /** Beige real de common_screen.png en el eje X: el marco ocupa 0..11 y 244..255 (el beige va de x=12 a x=244, no de
     *  10 a 245; con esos dos valores los textos pegados al borde pisaban el marco, ver gui.md). Antes cada pantalla
     *  declaraba su propia copia (IN_X1/IN_X2 en nueve). */
    public static final int IN_X1 = 12;
    public static final int IN_X2 = 243;
    /** Beige real en el eje Y (13..242): el marco ocupa 0..12 y 243..255. */
    public static final int IN_Y1 = 13;
    public static final int IN_Y2 = 242;
    /** Iconos de esquina (Atrás abajo-izquierda, Aceptar/Start abajo-derecha, Ayuda arriba-derecha): MISMO tamaño y
     *  MISMA separación del borde del beige en las cuatro esquinas, para que queden alineados entre sí. */
    public static final int ICON = 18;
    public static final int ICON_INSET = 5;

    public static int iconLeft(int panelLeft)            { return panelLeft + IN_X1 + ICON_INSET; }
    public static int iconRight(int panelLeft)           { return panelLeft + IN_X2 + 1 - ICON_INSET - ICON; }
    public static int iconTop(int panelTop)              { return panelTop + IN_Y1 + ICON_INSET; }
    public static int iconBottom(int panelTop, int panelH) { return panelTop + IN_Y2 + 1 - ICON_INSET - ICON - (IDEAL - panelH); }
    /** Ancho de los botones de pie de pantalla (Back/Next/Confirm) de las pantallas de creación, el editor y Weight. */
    public static final int FOOTER_BTN_W = 60;

    /** Hueco lateral fijo para que el panel no toque los bordes de la ventana. */
    public static final int MARGIN = 8;
    /** Alto que hay que reservar por ENCIMA del panel para el título (ScreenTitle.drawAbovePanel
     *  lo dibuja ahí) — ScreenTitle.ABOVE más un pelín de aire para el lineHeight real de la
     *  fuente, así el título nunca queda pegado al borde del panel. */
    public static final int TITLE_RESERVE = ScreenTitle.ABOVE + 5;

    public static PanelSizing.Axis fitAxis(int total, int startReserve, int endReserve) {
        return PanelSizing.fitAxis(IDEAL, total, startReserve, endReserve);
    }

    public static void blit(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(TEX, x, y, w, h, 0, 0, IDEAL, IDEAL, IDEAL, IDEAL);
    }
}
