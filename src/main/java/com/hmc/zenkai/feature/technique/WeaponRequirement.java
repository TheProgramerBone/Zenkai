package com.hmc.zenkai.feature.technique;

import java.util.Locale;

/**
 * Qué hay que llevar en las manos para ejecutar una técnica física. Vive en el JSON de la técnica
 * ({@code weapon_requirement}); vacío = {@link #EMPTY_HANDS}, que es lo que exigían todas antes de
 * que existiera este campo.
 *
 *  - NONE: da igual lo que lleves (la técnica no usa las manos).
 *  - EMPTY_HANDS: ambas manos vacías (puñetazos, kiai...).
 *  - SWORD: una espada (o arma de ki) en la mano principal. Las técnicas de espada lo exigen; ver
 *    {@code GuardHands.isSword}.
 */
public enum WeaponRequirement {
    NONE, EMPTY_HANDS, SWORD;

    /** Texto del JSON a requisito; lo desconocido o vacío cae al comportamiento de siempre. */
    public static WeaponRequirement byId(String id) {
        if (id == null || id.isBlank()) return EMPTY_HANDS;
        try { return valueOf(id.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return EMPTY_HANDS; }
    }
}
