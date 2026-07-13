package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;

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
public final class Archetype {

    private final String name;
    private final EnumMap<ZenkaiAttributes, Double> shape;
    private final double bodyMult;
    private final double kiMult;

    public Archetype(String name, EnumMap<ZenkaiAttributes, Double> shape, double bodyMult, double kiMult) {
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
    private static final Map<String, Archetype> REGISTRY = new java.util.HashMap<>();

    private static void register(Archetype a) { REGISTRY.put(a.name(), a); }

    static {
        //                             name          STR   CON   DEX   WIL   SPI    bodyMult kiMult
        register(new Archetype("brawler",   shapeOf(30,   28,   17,   15,   10),  1.15, 1.0));
        register(new Archetype("ki_user",   shapeOf(10,   15,   17,   30,   28),  1.0,  1.2));
        register(new Archetype("balanced",  shapeOf(20,   20,   20,   20,   20),  1.0,  1.0));
        register(new Archetype("speedster", shapeOf(20,   12,   38,   18,   12),  0.85, 1.0));
        register(new Archetype("tank",      shapeOf(15,   38,   25,   12,   10),  1.4,  1.0));
        register(new Archetype("boss",      shapeOf(24,   24,   18,   20,   14),  1.25, 1.2));
    }

    /** Arquetipo por nombre (case-insensitive). Fallback a "balanced" si no existe. */
    public static Archetype get(String name) {
        if (name == null) return REGISTRY.get("balanced");
        Archetype a = REGISTRY.get(name.toLowerCase(Locale.ROOT));
        return (a != null) ? a : REGISTRY.get("balanced");
    }
}