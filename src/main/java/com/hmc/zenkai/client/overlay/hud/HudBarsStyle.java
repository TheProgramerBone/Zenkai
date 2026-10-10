package com.hmc.zenkai.client.overlay.hud;

/**
 * Estilo del HUD de Body/Stamina/Ki (ClientConfig hud.bars_style). Pedido del usuario
 * (2026-10-09): el HUD nuevo no sustituye al de siempre, se elige. Los valores se traducen con
 * config.zenkai.value.modern/minimalist ("minimalist" y no "minimal" porque esa clave ya la usa
 * la calidad del aura clásica, en femenino).
 */
public enum HudBarsStyle {
    /** Pixel art vanilla-like: marco tintado por forma, retrato, estela de daño y ki por segmentos (ModernStatsHud). */
    MODERN,
    /** Las barras de siempre: bars_empty/bars_full.png (en ClientZenkaiHooks). */
    MINIMALIST
}
