package com.hmc.zenkai.client.gui;

/**
 * ÚNICA fuente de colores de la GUI del mod.
 * Antes cada pantalla llevaba sus literales: 0x4A3726 aparecía en StyleSelectionScreen,
 * ShenlongWishScreen y RaceSelectionScreen; 0xFFF149 en cuatro sitios; 0xFFD966 en Skills y
 * Mastery con el mismo papel pero nombres distintos ("COL_COST" / "COL_HEADER"). Con los
 * literales repartidos, un retoque de paleta obliga a un grep por hexadecimales y siempre se
 * escapa alguno — que es exactamente lo que pasó con el verde de Shenlong (0x04a500 en
 * RevivePet y EnchantVillager, 0x00a135 en StackWish: dos verdes distintos para lo mismo).
 * Los valores salen de common_screen.png, no están inventados: BEIGE/BEIGE_DEEP son el relleno
 * y su sombreado, y BORDER_* son los tres anillos del marco.
 * Convención: los nombres terminados en _ON_PANEL son para texto DENTRO del beige (necesitan
 * ser oscuros y van SIN sombra); el resto es para texto sobre fondo oscuro (van CON sombra).
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

    // ── Texto sobre el beige (SIN sombra) ────────────────────────────────────
    /** Etiquetas de campo: "Race:", "Alignment", "Attributes". */
    public static final int LABEL_ON_PANEL = 0xFF4A3726;
    /** Cuerpo de descripción, más suave que la etiqueta. */
    public static final int BODY_ON_PANEL  = 0xFF5A4636;
    /** Cabecera de columna, casi al borde de la legibilidad a propósito. */
    public static final int MUTED_ON_PANEL = 0xFF7A6450;

    // ── Texto sobre fondo oscuro / con sombra ────────────────────────────────
    public static final int TEXT       = 0xFFFFFFFF;
    public static final int TEXT_DIM   = 0xFFAAAAAA;
    public static final int TEXT_HOVER = 0xFFFFF149;
    public static final int TEXT_OFF   = 0xFFA0A0A0;

    // ── Semánticos ───────────────────────────────────────────────────────────
    /** Dorado del mod: títulos, TP, cabeceras de sección. */
    public static final int GOLD      = 0xFFFFC94A;
    /** Amarillo de valor numérico (costes asumibles, TP disponible). */
    public static final int VALUE     = 0xFFFFD966;
    /** Verde: disponible, poseído, al máximo en positivo. */
    public static final int OK        = 0xFF7CFC7C;
    /** Rojo apagado: no se puede pagar, déficit. */
    public static final int DENIED    = 0xFFCC6666;
    /** Rojo intenso: valor en negativo que señala un problema real (mindFree). */
    public static final int ERROR     = 0xFFFF5555;
    /** Azul: nivel máximo alcanzado. Verde no vale aquí: choca con el nombre. */
    public static final int MAXED     = 0xFF7FD4FF;
    /** Verde dragón. UN solo verde para lo de Shenlong. */
    public static final int SHENLONG  = 0xFF23B14C;

    // ── Barras de recurso ────────────────────────────────────────────────────
    public static final int BAR_BG      = 0x80241A12;
    public static final int BAR_FRAME   = 0xFF3A2A18;
    public static final int BAR_BODY    = 0xFFE44B3A;   // rojo carne
    public static final int BAR_STAMINA = 0xFF7CD44B;   // verde
    public static final int BAR_KI      = 0xFF4BB6E4;   // azul ki
    public static final int BAR_CONTROL = 0xFFFFB03A;   // naranja: % de Ki Control
    public static final int BAR_MASTERY = 0xFF7FD4FF;

    // ── Utilidades ───────────────────────────────────────────────────────────
    public static final int SEPARATOR   = 0x33AC421B;
    public static final int HOVER_VEIL  = 0x30FFFFFF;
    public static final int SELECT_VEIL = 0x40FFD966;

    /** Mismo color con alfa distinto. Evita recalcular literales a mano. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}