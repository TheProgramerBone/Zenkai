package com.hmc.zenkai.client.aura.flame;

/**
 * Las DOS opciones de aura que ve el jugador en la config de cliente ({@code aura.system}). Pedido
 * del usuario (2026-09-30): si el aura nueva genera mucho lag en un equipo, el jugador vuelve a la
 * clásica desde la config y ya está.
 * <p>
 * Los modos de comparación (la llama sola de antes, forzar el compuesto) siguen existiendo en
 * {@link AuraSystemMode}, pero solo como override de sesión de {@code /zenkaiaura system}: no son
 * una elección para el jugador.
 * <p>
 * Los nombres son también las claves de traducción de la pantalla de config
 * ({@code config.zenkai.value.<nombre en minúsculas>}): renombrarlas obliga a renombrar esas claves.
 */
public enum AuraEngine {
    /** El aura compuesta por familias (client/aura/compose), con o sin shaderpack. */
    NEW,
    /** El aura clásica (faldones legacy): más ligera. */
    LEGACY
}
