package com.hmc.zenkai.client.aura.flame;

/**
 * Qué sistema de aura dibuja el cuerpo del aura, elegido en {@code ClientConfig}
 * ({@code aura.system}) o, en caliente y solo en memoria, con {@code /zenkaiauraflame}. Ver
 * {@link AuraSystemDispatcher}.
 * <p>
 * Los nombres de las constantes son también las claves de traducción de sus valores en la
 * pantalla de config ({@code config.zenkai.value.<nombre en minúsculas>}): renombrarlas obliga a
 * renombrar esas claves en en_us/es_mx.
 */
public enum AuraSystemMode {
    /** Sistema nuevo si no hay shaderpack (Iris/Oculus); con shaderpack, el legacy. */
    AUTO,
    /** Siempre el aura de siempre. */
    FORCE_LEGACY,
    /** Siempre el sistema nuevo, aunque haya un shaderpack cargado (sin bloom propio en ese caso). */
    FORCE_NEW
}
