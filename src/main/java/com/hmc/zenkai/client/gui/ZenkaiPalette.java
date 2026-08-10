package com.hmc.zenkai.client.gui;

/**
 * ÚNICA fuente de colores de la GUI del mod.
 * ═══ REGLA DE SOMBRA (vale para TODAS las pantallas) ═══
 *   Sobre el beige del panel  → SIN sombra. Los colores de esta sección son oscuros y
 *                               saturados; una sombra negra bajo un marrón sobre beige claro
 *                               solo emborrona el glifo.
 *   Sobre fondo oscuro        → CON sombra. Popups, tooltips, títulos fuera del panel, HUD.
 * Los nombres lo dicen: lo que acaba en _ON_PANEL va sin sombra, el resto con ella. Si
 * hay que elegir a ojo entre dos colores, es que uno de los dos está en el grupo equivocado.
 * Los tonos del panel salen de common_screen.png, no están inventados: BEIGE/BEIGE_DEEP son el
 * relleno y su sombreado, y BORDER_* son los tres anillos del marco.
 * La escala tierra sustituye a los pasteles que había antes (0xFFFFD966, 0xFF7CFC7C…). Aquellos
 * estaban pensados para leerse sobre negro, y sobre el beige un amarillo claro con un TP de diez
 * cifras al lado era prácticamente invisible.
 * ═══ CÓMO ELEGIR ═══
 * No se elige "un color bonito", se elige el ROL de lo que se dibuja y el fondo sobre el que va:
 *   ¿qué es?                    sobre panel            sobre oscuro
 *   ─────────────────────────────────────────────────────────────────
 *   etiqueta de campo           LABEL_ON_PANEL         TEXT_DIM
 *   descripción, detalle        BODY_ON_PANEL          TEXT_DIM
 *   texto secundario            MUTED_ON_PANEL         TEXT_OFF
 *   TP                          TP_ON_PANEL            TP_ON_DARK
 *   MIND                        MIND_ON_PANEL          MIND_ON_DARK
 *   nombre de algo que posees   OWNED_ON_PANEL         OK
 *   al máximo                   MAXED_ON_PANEL         MAXED
 *   no puedes pagarlo           DENIED_ON_PANEL        DENIED
 *   valor en negativo, error    DENIED_ON_PANEL        ERROR
 *   forma / estado especial     SPECIAL_ON_PANEL       MAXED
 * Si un rol nuevo no encaja en la tabla, se añade AQUÍ una constante con su nombre — no un
 * literal en la pantalla. Un hexadecimal suelto en un Screen es siempre un fallo pendiente.
 * Y el dibujo pasa por PanelText, no por GuiGraphics directamente: drawCenteredString dibuja
 * sombra SIEMPRE, y una sombra negra bajo un color tierra sobre beige es un borrón.
 */
public final class ZenkaiPalette {
    private ZenkaiPalette() {}

    // ── Panel (extraídos de common_screen.png) ───────────────────────────────
    public static final int BEIGE       = 0xFFE1C8A9;
    public static final int BEIGE_DARK  = 0xFFD7BC9B;
    public static final int BEIGE_DEEP  = 0xFFC4A885;
    public static final int BORDER_IN   = 0xFFAC421B;   // anillo interior
    public static final int BORDER_MID  = 0xFFF06500;   // anillo medio
    public static final int BORDER_OUT  = 0xFFF1D839;   // anillo exterior
    public static final int BORDER_HI   = 0xFFFDF099;   // brillo de esquina

    // ── Texto sobre el beige — ESCALA TIERRA, SIEMPRE SIN SOMBRA ─────────────
    /** Etiquetas de campo: "Race:", "Alignment", "Attributes". */
    public static final int LABEL_ON_PANEL = 0xFF4A3726;
    /** Cuerpo de descripción, más suave que la etiqueta. */
    public static final int BODY_ON_PANEL  = 0xFF5A4636;
    /** Cabecera de columna y texto secundario. Al borde de la legibilidad a propósito. */
    public static final int MUTED_ON_PANEL = 0xFF8A755E;

    /** Valor numérico destacado (TP, costes asumibles). Dorado QUEMADO, no amarillo claro. */
    public static final int VALUE_ON_PANEL = 0xFF8A5A08;
    /** Positivo: disponible, poseído, ventaja. Verde bosque. */
    public static final int OK_ON_PANEL    = 0xFF2E6B26;
    /** Negativo: no se puede pagar, déficit, aviso. Granate. */
    public static final int DENIED_ON_PANEL = 0xFF9A2B1E;
    /** Estado especial / transformación. Ciruela. */
    public static final int SPECIAL_ON_PANEL = 0xFF6B3A78;
    /** Enlace conceptual (nivel máximo, valor derivado). Azul pizarra. */
    public static final int ACCENT_ON_PANEL = 0xFF1F5C7A;

    // ── Texto sobre fondo oscuro / con sombra ────────────────────────────────
    public static final int TEXT       = 0xFFFFFFFF;
    public static final int TEXT_DIM   = 0xFFAAAAAA;
    public static final int TEXT_HOVER = 0xFFFFF149;
    public static final int TEXT_OFF   = 0xFFA0A0A0;

    // ── Semánticos sobre oscuro (popups, tooltips, HUD) ──────────────────────
    /** Dorado del mod: títulos, cabeceras de sección en popup. */
    public static final int GOLD      = 0xFFFFC94A;
    public static final int VALUE     = 0xFFFFD966;
    public static final int OK        = 0xFF7CFC7C;
    public static final int DENIED    = 0xFFCC6666;
    /** Rojo intenso: valor en negativo que señala un problema real (mindFree). */
    public static final int ERROR     = 0xFFFF5555;
    public static final int MAXED     = 0xFF7FD4FF;
    /** Verde dragón. UN solo verde para Shenlong. */
    public static final int SHENLONG  = 0xFF23B14C;

    // ── Barras de recurso ────────────────────────────────────────────────────
    /** Marco: el marrón del panel, NO negro. Sobre beige el negro recorta como un agujero. */
    public static final int BAR_FRAME   = BORDER_IN;
    /** Fondo del canal: beige hundido, no negro translúcido. */
    public static final int BAR_BG      = 0xFFB39676;
    /** Fondo cuando la barra va sobre un popup oscuro. */
    public static final int BAR_BG_DARK = 0x80241A12;

    // Rellenos: apagados un punto respecto a los originales para no gritar sobre el beige.
    public static final int BAR_BODY    = 0xFFC43C2E;
    public static final int BAR_STAMINA = 0xFF5EA83A;
    public static final int BAR_KI      = 0xFF2E8FBE;
    public static final int BAR_CONTROL = 0xFFD9922B;
    public static final int BAR_MASTERY = 0xFF3E86A8;

    // ── Roles fijos: SIEMPRE este color, en TODAS las pantallas ──────────────
    //
    // No son alias por comodidad: son la respuesta a que TP y MND salían los dos amarillos en
    // pantallas distintas y no se distinguían de un vistazo. Cualquier pantalla que muestre uno
    // de estos valores usa la constante, no elige color.

    /** Puntos de entrenamiento. Dorado quemado, en cualquier pantalla y contexto de panel. */
    public static final int TP_ON_PANEL   = VALUE_ON_PANEL;
    /** Puntos de entrenamiento sobre fondo oscuro. */
    public static final int TP_ON_DARK    = VALUE;
    /** MIND / mente. Azul pizarra: nunca comparte color con el TP. */
    public static final int MIND_ON_PANEL = ACCENT_ON_PANEL;
    /** MIND sobre fondo oscuro. */
    public static final int MIND_ON_DARK  = MAXED;

    /** Nombre de una cosa que el jugador posee (habilidad, técnica, forma). */
    public static final int OWNED_ON_PANEL = OK_ON_PANEL;
    /** Estado "al máximo". NO es un verde distinto: lo dice el texto, no el tono. */
    public static final int MAXED_ON_PANEL = ACCENT_ON_PANEL;

    // ── Utilidades ───────────────────────────────────────────────────────────
    public static final int SEPARATOR   = 0x44AC421B;
    public static final int HOVER_VEIL  = 0x30FFFFFF;
    public static final int SELECT_VEIL = 0x40FFD966;
    /** Relleno del popup lateral. */
    public static final int POPUP_BG    = 0xF01E1410;

    /** Mismo color con alfa distinto. Evita recalcular literales a mano. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}