package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Rangos de la cápsula Hoi-Poi (Fase 6 de la 0.7.0, §3.5 de pendientes-0.7.0.md): uno por material, cada uno con
 * su RADIO. La zona es un cubo de lado 2r+1 centrado en la cápsula en horizontal y que empieza en el bloque sobre
 * el que está apoyada (decisión del usuario 2026-11-24: una casa se lleva con su suelo, sin cavar).
 *
 * El radio vive en {@code ServerConfig} ({@code capsules.radii}, por defecto 2/3/5/7/9/11); el orden del enum es el
 * de esa lista y el de la propiedad de blockstate, así que NO se reordena (añadir solo al final).
 */
public enum CapsuleTier implements StringRepresentable {
    COPPER("copper", 0xE8955F),
    IRON("iron", 0xD6D6D6),
    GOLD("gold", 0xF2CC3A),
    DIAMOND("diamond", 0x6FF2DC),
    KATCHIN("katchin", 0xA2B4D2),
    NETHERITE("netherite", 0xC9B4B6);

    private final String id;
    /** Color del contorno y del holograma de la zona: el tono «luz» de la rampa del metal de tools/gen_capsules.py. */
    public final int rgb;

    CapsuleTier(String id, int rgb) {
        this.id = id;
        this.rgb = rgb;
    }

    public String id() { return id; }

    @Override
    public @NotNull String getSerializedName() { return id; }

    /** Radio de la zona según la config del servidor (sincronizada al cliente). */
    public int radius() { return ServerConfig.capsuleRadius(ordinal()); }

    /** Lado del cubo de captura. */
    public int side() { return radius() * 2 + 1; }

    /** Volumen total que cabe: bloques no-aire + el volumen de los mobs. */
    public int budget() {
        int s = side();
        return s * s * s;
    }

    public static CapsuleTier byOrdinal(int i) {
        CapsuleTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
