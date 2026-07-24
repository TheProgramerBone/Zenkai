package com.hmc.zenkai.core.technique;

import com.hmc.zenkai.core.network.feature.technique.TechniqueAssets;
import com.hmc.zenkai.core.network.feature.technique.TechniquePosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Una técnica CREADA por el jugador (vive en un slot de PlayerTechniques).
 * Nombre + tipo + color + tamaño + explosiva + posición de salida + sonidos + set de anim.
 *
 * Explosiva: al impactar genera daño en área (radio y daño escalan con el tamaño) y cuesta
 * más ki (KiFirePacket). Ignorada en tipos defensivos (BARRIER).
 *
 * El NOMBRE PUEDE ESTAR VACÍO: displayName() cae al nombre del tipo. Se guarda vacío a
 * propósito en vez de materializar la traducción, para que cada jugador lo lea en su idioma
 * en vez de congelar el del que la creó.
 *
 * EXTENSIBLE: los campos nuevos se leen con contains() en load() y se escriben siempre en
 * save() — los saves viejos cargan con defaults y nada se rompe.
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

    /** Desde dónde sale el disparo (mano, boca, frente...). */
    private TechniquePosition position;
    /** null = sin sonido. Se validan contra el registro al guardar. */
    private ResourceLocation chargeSound;
    private ResourceLocation releaseSound;
    /** Set de animación PAL: carga y disparo van emparejados con el mismo número. */
    private int animSet;

    public KiTechnique(String name, KiTechniqueType type, int rgb, int size, boolean explosive) {
        this(name, type, rgb, size, explosive, TechniquePosition.RIGHT_HAND, null, null, 1);
    }

    public KiTechnique(String name, KiTechniqueType type, int rgb, int size, boolean explosive,
                       TechniquePosition position, ResourceLocation chargeSound,
                       ResourceLocation releaseSound, int animSet) {
        set(name, type, rgb, size, explosive, position, chargeSound, releaseSound, animSet);
    }

    public String name()                  { return name; }
    public KiTechniqueType type()         { return type; }
    public int rgb()                      { return rgb; }
    public int size()                     { return size; }
    public boolean explosive()            { return explosive; }
    public TechniquePosition position()   { return position; }
    public ResourceLocation chargeSound() { return chargeSound; }
    public ResourceLocation releaseSound(){ return releaseSound; }
    public int animSet()                  { return animSet; }

    /** Lo que se ENSEÑA: el nombre puesto por el jugador o, si lo dejó vacío, el del tipo. */
    public Component displayName() {
        return name.isEmpty() ? Component.translatable(type.nameKey()) : Component.literal(name);
    }

    public void set(String name, KiTechniqueType type, int rgb, int size, boolean explosive) {
        set(name, type, rgb, size, explosive, this.position, this.chargeSound,
                this.releaseSound, this.animSet);
    }

    public void set(String name, KiTechniqueType type, int rgb, int size, boolean explosive,
                    TechniquePosition position, ResourceLocation chargeSound,
                    ResourceLocation releaseSound, int animSet) {
        this.name = sanitizeName(name);
        this.type = type;
        this.rgb = rgb & 0xFFFFFF;
        this.size = clampSize(size);
        this.explosive = explosive;
        this.position = (position == null) ? TechniquePosition.RIGHT_HAND : position;
        this.chargeSound = TechniqueAssets.isValidCharge(chargeSound) ? chargeSound : null;
        this.releaseSound = TechniqueAssets.isValidRelease(releaseSound) ? releaseSound : null;
        this.animSet = Math.max(1, animSet);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("type", type.name());
        tag.putInt("rgb", rgb);
        tag.putInt("size", size);
        tag.putBoolean("explosive", explosive);
        tag.putInt("position", position.ordinal());
        tag.putInt("animSet", animSet);
        if (chargeSound != null) tag.putString("chargeSound", chargeSound.toString());
        if (releaseSound != null) tag.putString("releaseSound", releaseSound.toString());
        return tag;
    }

    /** null si el tipo guardado ya no existe. */
    public static KiTechnique load(CompoundTag tag) {
        KiTechniqueType type = KiTechniqueType.byName(tag.getString("type"));
        if (type == null) return null;
        return new KiTechnique(
                tag.getString("name"), type, tag.getInt("rgb"),
                tag.getInt("size"), tag.getBoolean("explosive"),
                TechniquePosition.byOrdinal(tag.getInt("position")),
                readId(tag, "chargeSound"), readId(tag, "releaseSound"),
                tag.contains("animSet") ? tag.getInt("animSet") : 1);
    }

    private static ResourceLocation readId(CompoundTag tag, String key) {
        return tag.contains(key) ? ResourceLocation.tryParse(tag.getString(key)) : null;
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