package com.hmc.zenkai.client.gui;

/**
 * Paleta de la familia TECNOLÓGICA del mod: máquinas, bancos de trabajo y lo que sea
 * aparato en vez de personaje. ZenkaiPalette sigue siendo la de las pantallas de raza, ki,
 * transformaciones y deseos — beige, marrones y dorados. Las dos conviven; ninguna pantalla
 * debe mezclar constantes de ambas.
 * Los tonos NO están inventados: salen de textures/block/scouter_bench.png, que es el aparato
 * de verdad. Blanco plástico de laboratorio, negro azulado de pantalla, azul acero para
 * herrajes, cian de lectura y pilotos verde y rojo.
 * ═══ REGLA DE SOMBRA (es la de ZenkaiPalette AL REVÉS, y por eso hay que leerla) ═══
 * Aquí hay DOS fondos dentro de la misma pantalla, no uno:
 *   Sobre la CARCASA (clara)   → texto oscuro, SIN sombra.  Sufijo _ON_CHASSIS.
 *   Sobre la PANTALLA (oscura) → texto claro, CON sombra.   Sufijo _ON_SCREEN.
 * Un texto claro sin sombra sobre la carcasa desaparece, y un texto oscuro sobre la pantalla
 * también. El sufijo dice cuál toca: si hay que dudar entre dos colores es que el elemento
 * está dibujado sobre la zona equivocada.
 * ═══ CÓMO ELEGIR ═══
 *   ¿qué es?                     sobre carcasa          sobre pantalla
 *   ──────────────────────────────────────────────────────────────────
 *   etiqueta de campo            LABEL_ON_CHASSIS       TEXT_ON_SCREEN
 *   descripción, detalle         BODY_ON_CHASSIS        DIM_ON_SCREEN
 *   texto secundario             MUTED_ON_CHASSIS       DIM_ON_SCREEN
 *   algo que ya tienes           OK_ON_CHASSIS          OK_ON_SCREEN
 *   al máximo                    ACCENT_ON_CHASSIS      MAXED_ON_SCREEN
 *   no puedes / falta algo       DENIED_ON_CHASSIS      DENIED_ON_SCREEN
 * Un rol nuevo se añade AQUÍ con su nombre. Un hexadecimal suelto en un Screen es un fallo
 * pendiente, igual que en la familia beige.
 */
public final class ZenkaiTechPalette {
    private ZenkaiTechPalette() {}

    // ── Carcasa (blanco plástico) ────────────────────────────────────────────
    public static final int CHASSIS       = 0xFFD9D7D0;
    public static final int CHASSIS_HI    = 0xFFF0EFEA;
    public static final int CHASSIS_SHADE = 0xFFB9B7B0;
    /** Bandeja del inventario del jugador: mismo material, un punto más oscuro. */
    public static final int TRAY          = 0xFFC4C2BB;

    // ── Bordes ───────────────────────────────────────────────────────────────
    public static final int EDGE_DARK     = 0xFF1E2021;
    public static final int EDGE_SHADOW   = 0xFF787874;

    // ── Pantalla hundida ─────────────────────────────────────────────────────
    public static final int SCREEN_BG     = 0xFF12161E;
    /** Línea de barrido. Muy contenida: es textura, no efecto. */
    public static final int SCREEN_LINE   = 0xFF1A202C;
    public static final int SCREEN_EDGE   = 0xFF0A0C10;

    // ── Azul acero (herrajes, pozos, tornillos) ──────────────────────────────
    public static final int STEEL         = 0xFF6E788C;
    public static final int STEEL_HI      = 0xFFA8B2C6;
    public static final int STEEL_DARK    = 0xFF536174;

    // ── Acentos ──────────────────────────────────────────────────────────────
    public static final int CYAN          = 0xFF56B0C8;
    public static final int CYAN_HI       = 0xFF8CECFF;
    public static final int CYAN_DARK     = 0xFF346E84;
    public static final int LED_GREEN     = 0xFF7BFFA3;
    public static final int LED_GREEN_MID = 0xFF43E88D;
    public static final int LED_RED       = 0xFFF48686;

    // ── Texto sobre CARCASA: oscuro, sin sombra ──────────────────────────────
    public static final int LABEL_ON_CHASSIS  = 0xFF23272E;
    public static final int BODY_ON_CHASSIS   = 0xFF3C424C;
    public static final int MUTED_ON_CHASSIS  = 0xFF6C7280;
    public static final int OK_ON_CHASSIS     = 0xFF1F6B3A;
    public static final int DENIED_ON_CHASSIS = 0xFF9A2B1E;
    public static final int ACCENT_ON_CHASSIS = 0xFF1F5C7A;

    // ── Texto sobre PANTALLA: claro, con sombra ──────────────────────────────
    public static final int TEXT_ON_SCREEN   = 0xFFDCE6F0;
    public static final int DIM_ON_SCREEN    = 0xFF7E8A9C;
    public static final int OK_ON_SCREEN     = LED_GREEN;
    public static final int DENIED_ON_SCREEN = LED_RED;
    public static final int MAXED_ON_SCREEN  = CYAN_HI;

    /** Título de pantalla. Va sobre la placa oscura de cabecera, así que lleva sombra. */
    public static final int TITLE = CYAN_HI;

    // ── Barras e indicadores ─────────────────────────────────────────────────
    public static final int BAR_FRAME = STEEL_DARK;
    public static final int BAR_BG    = SCREEN_BG;
    public static final int BAR_FILL  = CYAN;
    /** Segmento encendido / apagado del indicador de carga. */
    public static final int SEG_ON    = CYAN;
    public static final int SEG_OFF   = 0xFF20283A;

    // ── Utilidades ───────────────────────────────────────────────────────────
    /** Velo sobre un slot señalado (materiales de la mejora bajo el cursor). */
    public static final int SELECT_VEIL  = 0x5056B0C8;
    public static final int ROW_BAND     = 0x18A8B2C6;
    /** Separador entre filas de una lista. Deliberadamente casi invisible: marca el corte
     *  sin dibujar una reja. */
    public static final int ROW_SEP = 0x14A8B2C6;
    public static final int TOOLTIP_BG   = 0xF00E1218;
    public static final int TOOLTIP_EDGE = STEEL;

    /** Mismo color con alfa distinto. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}