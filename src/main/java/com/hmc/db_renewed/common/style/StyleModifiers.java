package com.hmc.db_renewed.common.style;

import java.util.Map;

public record StyleModifiers(
        float strengthMultiplier,
        float dexterityMultiplier,
        float constitutionMultiplier,
        float willpowerMultiplier,
        float spiritMultiplier
) {
    public static final Map<CombatStyle, StyleModifiers> MODIFIERS = Map.of(
            CombatStyle.WARRIOR,        new StyleModifiers(1.5f, 1.1f, 1.3f, 0.8f, 0.9f),
            CombatStyle.MARTIAL_ARTIST, new StyleModifiers(1.2f, 1.5f, 1.0f, 1.0f, 1.0f),
            CombatStyle.SPIRITUALIST,   new StyleModifiers(0.8f, 1.0f, 1.0f, 1.5f, 1.6f)
    );
}
