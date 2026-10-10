package com.hmc.zenkai.client.overlay.hud;

/**
 * Cómo se escribe el valor dentro de las barras de Body/Stamina/Ki (ClientConfig
 * hud.bars_values), en los dos estilos de HUD. Pedido del usuario (2026-10-09). Se traduce con
 * config.zenkai.value.numbers/percent.
 */
public enum HudValueFormat {
    /** "180 / 200" (el de siempre). */
    NUMBERS,
    /** "90%". */
    PERCENT
}
