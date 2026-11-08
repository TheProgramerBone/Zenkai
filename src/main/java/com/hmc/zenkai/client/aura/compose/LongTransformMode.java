package com.hmc.zenkai.client.aura.compose;

/**
 * Preferencia del cliente para la secuencia LARGA de la primera transformación de cada forma (ClientConfig
 * {@code long_transform_mode}). Solo presentación: no afecta al servidor ni a lo que se desbloquea.
 * FULL: aura larga + sacudida y acercamiento de cámara (propios). REDUCED: aura larga sin tocar la cámara. OFF: la corta de siempre.
 */
public enum LongTransformMode {
    FULL, REDUCED, OFF
}
