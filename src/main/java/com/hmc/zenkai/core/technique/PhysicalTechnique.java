package com.hmc.zenkai.core.technique;

/**
 * Técnicas físicas predefinidas (v1.0: desbloqueo por TP; maestros después).
 * Consumen STAMINA (% del máximo), escalan con STR (dmgMult), instantáneas
 * (castTicks reservado para el futuro), cooldown por técnica.
 * El orden del enum = celdas del atlas physical_icons.png (como KiTechniqueType).
 */
public enum PhysicalTechnique {
    //          tpCost staminaPct dmgMult cooldown range
    DASH_PUNCH (150,   0.15,      1.2,    80,      3.0),
    HEAVY_BLOW (200,   0.20,      1.8,    120,     3.5),
    BARRAGE    (300,   0.25,      0.45,   160,     3.0),
    KIAI       (100,   0.10,      0.0,    100,     5.0);

    public final int tpCost;
    public final double staminaPct;
    public final double dmgMult;
    public final int cooldownTicks;
    public final double range;

    PhysicalTechnique(int tpCost, double staminaPct, double dmgMult,
                      int cooldownTicks, double range) {
        this.tpCost = tpCost;
        this.staminaPct = staminaPct;
        this.dmgMult = dmgMult;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
    }

    public String nameKey() { return "physical.zenkai." + name().toLowerCase(java.util.Locale.ROOT); }

    public static PhysicalTechnique byOrdinal(int i) {
        PhysicalTechnique[] all = values();
        return (i >= 0 && i < all.length) ? all[i] : null;
    }

    public static PhysicalTechnique byName(String n) {
        try { return valueOf(n); } catch (IllegalArgumentException e) { return null; }
    }
}