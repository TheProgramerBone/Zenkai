package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.feature.aura.AuraLod;
import org.jetbrains.annotations.Nullable;

/**
 * Calidad mínima del aura CLÁSICA (AuraRenderer, la opción "Clásica" de aura.system). Cada
 * nivel fija la banda de {@link AuraLod} más detallada que se permite dibujar aunque el jugador
 * esté cerca ({@link AuraLod#effective}): bajar la calidad nunca da MÁS detalle, solo fuerza
 * bandas más ligeras. La compensación de alfa/núcleo de AuraLod sigue aplicándose, así que la
 * escala de poder entre jugadores se lee igual en cualquier nivel.
 *
 * Los nombres se traducen con config.zenkai.value.&lt;nombre en minúsculas&gt;: renombrar una
 * constante obliga a renombrar su clave de lang.
 */
public enum AuraClassicQuality {
    /** Sin límite: la banda la decide solo la distancia (comportamiento de siempre). */
    FULL(null),
    MEDIUM(AuraLod.MID),
    LOW(AuraLod.FAR),
    MINIMAL(AuraLod.SIGNATURE);

    @Nullable
    private final AuraLod floor;

    AuraClassicQuality(@Nullable AuraLod floor) {
        this.floor = floor;
    }

    /** Banda mínima para {@link AuraLod#effective}; null = sin límite. */
    @Nullable
    public AuraLod floor() { return floor; }
}
