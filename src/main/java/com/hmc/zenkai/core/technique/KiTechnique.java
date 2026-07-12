package com.hmc.zenkai.core.technique;

import net.minecraft.nbt.CompoundTag;

/**
 * Una técnica CREADA por el jugador (vive en un slot de PlayerTechniques).
 * v1.0: nombre + tipo + color + tamaño (1..MAX_SIZE) + explosiva.
 *
 * Explosiva: al impactar genera daño en área (radio y daño escalan con el tamaño) y
 * cuesta más ki (KiFirePacket). Ignorada en tipos defensivos (BARRIER).
 *
 * EXTENSIBLE para el creador completo post-release: los campos futuros (teledirigido,
 * carga, etc.) se añaden con contains() en load() y put en save() — los saves viejos
 * cargan con defaults y nada se rompe.
 */
public final class KiTechnique {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 7;

    private String name;
    private KiTechniqueType type;
    private int rgb;   // 0xRRGGBB
    private int size;  // MIN_SIZE..MAX_SIZE
    private boolean explosive;

    public KiTechnique(String name, KiTechniqueType type, int rgb, int size, boolean explosive) {
        this.name = sanitizeName(name);
        this.type = type;
        this.rgb = rgb & 0xFFFFFF;
        this.size = clampSize(size);
        this.explosive = explosive;
    }

    public String name()            { return name; }
    public KiTechniqueType type()   { return type; }
    public int rgb()                { return rgb; }
    public int size()               { return size; }
    public boolean explosive()      { return explosive; }

    public void set(String name, KiTechniqueType type, int rgb, int size, boolean explosive) {
        this.name = sanitizeName(name);
        this.type = type;
        this.rgb = rgb & 0xFFFFFF;
        this.size = clampSize(size);
        this.explosive = explosive;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("type", type.name());
        tag.putInt("rgb", rgb);
        tag.putInt("size", size);
        tag.putBoolean("explosive", explosive);
        return tag;
    }

    /** null si el tipo guardado ya no existe. */
    public static KiTechnique load(CompoundTag tag) {
        KiTechniqueType type = KiTechniqueType.byName(tag.getString("type"));
        if (type == null) return null;
        return new KiTechnique(tag.getString("name"), type, tag.getInt("rgb"),
                tag.getInt("size"), tag.getBoolean("explosive"));
    }

    public static int clampSize(int s) {
        return Math.min(MAX_SIZE, Math.max(MIN_SIZE, s));
    }

    /** Recorta a MAX_NAME_LENGTH y elimina caracteres de control. */
    public static String sanitizeName(String s) {
        if (s == null) return "";
        String clean = s.replaceAll("\\p{Cntrl}", "").trim();
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }
}