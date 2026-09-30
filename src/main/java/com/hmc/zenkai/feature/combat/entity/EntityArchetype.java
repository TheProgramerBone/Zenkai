package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.feature.ZenkaiAttributes;

import java.util.EnumMap;

/**
 * Un arquetipo define la FORMA (reparto relativo de atributos) y unos multiplicadores de
 * body/ki. El PL da la MAGNITUD; el arquetipo, la personalidad. (MIND no participa.)
 *
 * Los arquetipos vienen de datapack cross-namespace ({@code data/<ns>/zenkai_archetypes/*.json},
 * ver {@link EntityArchetypeManager}) — esta clase es solo el dato en memoria, ya no define
 * ningún arquetipo en código. Un addon añade el suyo con su propio JSON, sin tocar Java.
 */
public final class EntityArchetype {

    private final String name;
    private final EnumMap<ZenkaiAttributes, Double> shape;
    private final double bodyMult;
    private final double kiMult;

    public EntityArchetype(String name, EnumMap<ZenkaiAttributes, Double> shape, double bodyMult, double kiMult) {
        this.name = name;
        this.shape = shape;
        this.bodyMult = bodyMult;
        this.kiMult = kiMult;
    }

    public String name()      { return name; }
    public double bodyMult()  { return bodyMult; }
    public double kiMult()    { return kiMult; }
    public double shape(ZenkaiAttributes a) { return shape.getOrDefault(a, 0.0); }

    /** Arquetipo por nombre (case-insensitive). Fallback a "balanced" si no existe. */
    public static EntityArchetype get(String name) {
        return EntityArchetypeManager.get(name);
    }
}