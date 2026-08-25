package com.hmc.zenkai.feature.aura;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro en vivo de AuraModifier por aura_type ("ssj", "golden", "arcosian"...), cargado
 * por AuraSignatureManager desde datapack (data/<ns>/zenkai_aura_signatures/<aura_type>.json)
 * y resincronizado al cliente vía AuraSignatureSyncPacket. Espeja el patrón volatile Map +
 * replaceAll de FormDef, no el EnumMap hardcodeado de RaceSignature, porque ESTE sí recarga.
 */
public final class AuraSignatureRegistry {
    private AuraSignatureRegistry() {}

    private static volatile Map<String, AuraModifier> SIGNATURES = Map.of();

    /** NONE si el tipo no está registrado (incluye "default" y cualquier tipo sin datapack). */
    public static AuraModifier of(String auraType) {
        if (auraType == null) return AuraModifier.NONE;
        return SIGNATURES.getOrDefault(auraType, AuraModifier.NONE);
    }

    public static Map<String, AuraModifier> all() {
        return SIGNATURES;
    }

    public static void replaceAll(Map<String, AuraModifier> defs) {
        SIGNATURES = Map.copyOf(new LinkedHashMap<>(defs));
    }
}
