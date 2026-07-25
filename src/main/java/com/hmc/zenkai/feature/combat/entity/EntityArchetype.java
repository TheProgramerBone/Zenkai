package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.feature.ZenkaiAttributes;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Un arquetipo define la FORMA (reparto relativo de atributos) y unos multiplicadores de
 * body/ki. El PL da la MAGNITUD; el arquetipo, la personalidad. (MIND no participa.)
 *
 * FASE 1: los 6 vienen por defecto en código. Hacerlos editables por JSON es un añadido corto
 * para después; el JSON de entidades ya los referencia por nombre.
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

    // ── Helper para construir shapes (STR, CON, DEX, WIL, SPI) ────────────────
    private static EnumMap<ZenkaiAttributes, Double> shapeOf(double str, double con, double dex,
                                                             double wil, double spi) {
        EnumMap<ZenkaiAttributes, Double> m = new EnumMap<>(ZenkaiAttributes.class);
        m.put(ZenkaiAttributes.STRENGTH,     str);
        m.put(ZenkaiAttributes.CONSTITUTION, con);
        m.put(ZenkaiAttributes.DEXTERITY,    dex);
        m.put(ZenkaiAttributes.WILLPOWER,    wil);
        m.put(ZenkaiAttributes.SPIRIT,       spi);
        m.put(ZenkaiAttributes.MIND,         0.0);
        return m;
    }

    // ── Registro de arquetipos por nombre ────────────────────────────────────
    private static final Map<String, EntityArchetype> REGISTRY = new java.util.HashMap<>();

    private static void register(EntityArchetype a) { REGISTRY.put(a.name(), a); }

    static {
        //                             name          STR   CON   DEX   WIL   SPI    bodyMult kiMult
        register(new EntityArchetype("brawler",   shapeOf(30,   28,   17,   15,   10),  1.15, 1.0));
        register(new EntityArchetype("ki_user",   shapeOf(10,   15,   17,   30,   28),  1.0,  1.2));
        register(new EntityArchetype("balanced",  shapeOf(20,   20,   20,   20,   20),  1.0,  1.0));
        register(new EntityArchetype("speedster", shapeOf(20,   12,   38,   18,   12),  0.85, 1.0));
        register(new EntityArchetype("tank",      shapeOf(15,   38,   25,   12,   10),  1.4,  1.0));
        register(new EntityArchetype("boss",      shapeOf(24,   24,   18,   20,   14),  1.25, 1.2));
    }

    /** Arquetipo por nombre (case-insensitive). Fallback a "balanced" si no existe. */
    public static EntityArchetype get(String name) {
        if (name == null) return REGISTRY.get("balanced");
        EntityArchetype a = REGISTRY.get(name.toLowerCase(Locale.ROOT));
        return (a != null) ? a : REGISTRY.get("balanced");
    }
}