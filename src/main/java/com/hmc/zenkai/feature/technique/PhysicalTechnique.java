package com.hmc.zenkai.feature.technique;

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
    KIAI,
    // Técnicas de espada (las enseña Tori-Bot, weapon_requirement "sword"). Sus iconos son PNG
    // propios (campo icon del JSON), no celdas del atlas: NO ampliar physical_icons.png por ellas.
    RISING_SLASH,
    CROSS_SLASH,
    PIERCING_THRUST,
    SPIN_SLASH,
    SKY_CLEAVE,
    /** Ataque a distancia de espada: lanza una media luna de ki (SwordWaveEntity). */
    LIGHT_BLADE;

    private final String id;

    PhysicalTechnique() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public String id() { return id; }

    public String nameKey() { return "physical.zenkai." + id; }

    /** Descripción corta para el tooltip de la lista de técnicas. */
    public String descKey() { return nameKey() + ".desc"; }

    public static PhysicalTechnique byOrdinal(int i) {
        PhysicalTechnique[] all = values();
        return (i >= 0 && i < all.length) ? all[i] : null;
    }

    public static PhysicalTechnique byName(String n) {
        try { return valueOf(n.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    // ── Números (datapack) ──────────────────────────────────────────────────

    public TechniqueDef def() { return TechniqueDef.get(TechniqueDef.Kind.PHYSICAL, id); }

    /** false = sin JSON: técnica desactivada en cualquier parte. */
    public boolean enabled() { return def() != null; }

    /** TP de desbloqueo. MAX_VALUE si está desactivada. */
    public int tpCost() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.tpCost(); }

    /** MND mínimo para desbloquear. MAX_VALUE si está desactivada. */
    public int mindReq() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.mindReq(); }

    /** Coste de estamina como fracción del máximo. */
    public double staminaPct() { TechniqueDef d = def(); return d == null ? 0.0 : d.staminaPct(); }

    /** Multiplicador de daño sobre STR. */
    public double dmgMult() { TechniqueDef d = def(); return d == null ? 0.0 : d.damageMult(); }

    /** "" = desbloqueable con TP normalmente. Con un id, SOLO ese maestro la enseña — ver
     *  PhysicalTechniquePacket.handle (op UNLOCK) y el javadoc de TechniqueDef ("TÉCNICA FIRMA"). */
    public String master() { TechniqueDef d = def(); return d == null ? "" : d.master(); }

    public int cooldownTicks() { TechniqueDef d = def(); return d == null ? 20 : d.cooldownTicks(); }

    /** Qué hay que llevar en las manos. Sin JSON, lo de siempre: manos vacías. */
    public WeaponRequirement weaponRequirement() {
        TechniqueDef d = def();
        return WeaponRequirement.byId(d == null ? "" : d.weaponRequirement());
    }

    /** Alcance en bloques (rayo/cono según la técnica). */
    public double range() { TechniqueDef d = def(); return d == null ? 0.0 : d.range(); }

    /** Duración del ESTADO VISUAL sincronizado (ticks). No afecta al daño ni al cooldown:
     *  el golpe instantáneo sigue resolviéndose en su tick. Solo determina cuánto se mantiene
     *  el ActionState para que los demás vean la animación entera. */
    public int animTicks() { TechniqueDef d = def(); return d == null ? 12 : d.animTicks(); }

    /** "" = usar la celda fija del atlas compartido (physical_icons.png, indexada por
     *  ordinal()). Con una ResourceLocation propia, PhysicalIcons la dibuja en su lugar. */
    public String icon() { TechniqueDef d = def(); return d == null ? "" : d.icon(); }
}