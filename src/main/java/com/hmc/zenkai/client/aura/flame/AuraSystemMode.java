package com.hmc.zenkai.client.aura.flame;

/**
 * Qué sistema de aura dibuja el cuerpo del aura. Es el estado INTERNO del despacho
 * ({@link AuraSystemDispatcher}): la config del jugador solo elige entre dos ({@link AuraEngine}),
 * que se traducen a AUTO o FORCE_LEGACY; el resto son modos de comparación que solo pone en
 * caliente {@code /zenkaiaura system} (o {@code /zenkaiauraflame}).
 */
public enum AuraSystemMode {
    /** El sistema COMPUESTO por familias (client/aura/compose), con o sin shaderpack (con
     *  shaderpack, sin bloom propio). Decisión del usuario del 2026-09-30: el compuesto es el de
     *  defecto en cualquier caso y el legacy queda como respaldo. */
    AUTO,
    /** Siempre el aura de siempre (faldones legacy). Respaldo si el compuesto falla con un shaderpack. */
    FORCE_LEGACY,
    /** El sistema compuesto por familias, aunque la config diga otra cosa. */
    COMPOSED
}
