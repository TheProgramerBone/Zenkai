package com.hmc.zenkai.core.technique;

import java.util.Locale;

/**
 * Técnicas físicas. El enum es solo IDENTIDAD: name() = clave NBT/maestría,
 * ordinal() = celda del atlas physical_icons.png (NO reordenar).
 *
 * Los NÚMEROS viven en datapack:
 * data/&lt;ns&gt;/zenkai_techniques/physical/&lt;id&gt;.json (ver TechniqueDef /
 * TechniqueManager). Sin JSON, enabled() es false y la técnica queda desactivada.
 *
 * Consumen STAMINA (% del máximo), escalan con STR (damageMult), instantáneas,
 * cooldown por técnica.
 */
public enum PhysicalTechnique {
    DASH_PUNCH,
    HEAVY_BLOW,
    BARRAGE,
    KIAI;

    private final String id;

    PhysicalTechnique() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public String id() { return id; }

    public String nameKey() { return "physical.zenkai." + id; }

    public static PhysicalTechnique byOrdinal(int i) {
        PhysicalTechnique[] all = values();
        return (i >= 0 && i < all.length) ? all[i] : null;
    }

    public static PhysicalTechnique byName(String n) {
        try { return valueOf(n.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    // ── Números (datapack) ──────────────────────────────────────────────────

    public TechniqueDef def() { return TechniqueDef.get(TechniqueDef.Kind.PHYSICAL, id); }

    /** false = sin JSON: técnica desactivada en todas partes. */
    public boolean enabled() { return def() != null; }

    /** TP de desbloqueo. MAX_VALUE si está desactivada. */
    public int tpCost() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.tpCost(); }

    /** MND mínimo para desbloquear. MAX_VALUE si está desactivada. */
    public int mindReq() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.mindReq(); }

    /** Coste de estamina como fracción del máximo. */
    public double staminaPct() { TechniqueDef d = def(); return d == null ? 0.0 : d.staminaPct(); }

    /** Multiplicador de daño sobre STR. */
    public double dmgMult() { TechniqueDef d = def(); return d == null ? 0.0 : d.damageMult(); }

    public int cooldownTicks() { TechniqueDef d = def(); return d == null ? 20 : d.cooldownTicks(); }

    /** Alcance en bloques (rayo/cono según la técnica). */
    public double range() { TechniqueDef d = def(); return d == null ? 0.0 : d.range(); }
}