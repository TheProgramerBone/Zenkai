package com.hmc.zenkai.feature.technique;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Una técnica CREADA por el jugador (vive en un slot de PlayerTechniques).
 * Nombre + tipo + color + tamaño + explosiva + posición de salida + sonidos + set de anim.
 * Explosiva: al impactar genera daño en área (radio y daño escalan con el tamaño) y cuesta
 * más ki (KiFirePacket). Ignorada en tipos defensivos (BARRIER).
 * El NOMBRE PUEDE ESTAR VACÍO: displayName() cae al nombre del tipo. Se guarda vacío a
 * propósito en vez de materializar la traducción, para que cada jugador lo lea en su idioma
 * en vez de congelar el del que la creó.
 * EXTENSIBLE: los campos nuevos se leen con contains() en load() y se escriben siempre en
 * save() — los saves viejos cargan con defaults y nada se rompe.
 */
public final class KiTechnique {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MIN_SIZE = 1;
    /** Bajado de 7 a 5: siete escalones para un ×2.5 eran saltos que no se distinguían entre
     *  vecinos. Las técnicas guardadas con 6 o 7 se recortan al cargar (ver load). */
    public static final int MAX_SIZE = 5;

    private String name;
    private KiTechniqueType type;
    private int rgb;   // 0xRRGGBB
    /** SEGUNDO COLOR (2026-09-24): el del INTERIOR de la energía — la capa caliente y el núcleo
     *  de la rampa de ki_energy.fsh, que sin él se derivan del primero (azul → cian). -1 = sin
     *  segundo color, el comportamiento de siempre. Ver KiVfxColors. */
    private int rgb2 = -1;
    private int size;  // MIN_SIZE..MAX_SIZE
    private TechniqueEffect effect;

    // La posición YA NO es un campo: se deriva del animSet (TechniqueAnimSet). Era editable y
    // eso permitía combinaciones imposibles — una animación a dos manos disparando por la boca.
    // Como no tiene ninguna consecuencia mecánica (el proyectil vuela por la mirada pase lo que
    // pase), una posición que no casa con la animación solo podía estar mal.
    /** null = sin sonido. Se validan contra el registro al guardar. */
    private ResourceLocation chargeSound;
    private ResourceLocation releaseSound;
    /** Set de animación PAL: carga y disparo van emparejados con el mismo número. */
    private int animSet;

    public KiTechnique(String name, KiTechniqueType type, int rgb, int size,
                       TechniqueEffect effect) {
        this(name, type, rgb, size, effect, null, null, 1);
    }

    public KiTechnique(String name, KiTechniqueType type, int rgb, int size,
                       TechniqueEffect effect, ResourceLocation chargeSound,
                       ResourceLocation releaseSound, int animSet) {
        set(name, type, rgb, size, effect, chargeSound, releaseSound, animSet);
    }

    public String name()                  { return name; }
    public KiTechniqueType type()         { return type; }
    public int rgb()                      { return rgb; }

    /** Segundo color (interior) o -1. En una técnica de maestro lo IMPONE su datapack
     *  (default_rgb2), igual que animación y sonidos — ver el bloque de TÉCNICA DE MAESTRO. */
    public int rgb2() {
        return isSignature() ? type.defaultRgb2() : rgb2;
    }

    public void setRgb2(int v) { this.rgb2 = v < 0 ? -1 : (v & 0xFFFFFF); }
    public int size()                     { return size; }
    /** Consecuencia del set de animación, no una elección. Los tipos que IMPONEN animación
     *  (barrera, explosión, Genki Dama) no tienen set, así que su origen sale de la constante:
     *  preguntar por defensive() dejaba fuera a la explosión, que no es defensiva. */
    public TechniquePosition position() {
        return type.animOverride() != null
                ? TechniqueAnimSet.BARRIER_POSITION
                : TechniqueAnimSet.positionOf(animSet());
    }
    public TechniqueEffect effect()       { return effect; }

    // TÉCNICA DE MAESTRO: animación y sonidos los IMPONE su datapack (anim_set, charge_sound,
    // release_sound), igual que el color — se leen aquí, al consultar, y no al crear la
    // instancia. Así las firmas ya guardadas (creadas con set 1 y sin sonido antes de que
    // existieran estos campos) se corrigen solas sin migrar NBT, y un cambio del datapack llega
    // a las instancias existentes con un /reload. Lo guardado en la instancia solo cuenta si el
    // datapack no declara nada.
    public ResourceLocation chargeSound() {
        ResourceLocation sig = isSignature() ? type.signatureChargeSound() : null;
        return sig != null ? sig : chargeSound;
    }
    public ResourceLocation releaseSound() {
        ResourceLocation sig = isSignature() ? type.signatureReleaseSound() : null;
        return sig != null ? sig : releaseSound;
    }
    public int animSet() {
        int sig = isSignature() ? type.signatureAnimSet() : 0;
        return sig > 0 ? TechniqueAnimSet.clamp(sig) : animSet;
    }

    private boolean isSignature() { return !type.master().isEmpty(); }
    /**
     * Valor que viaja en ActionState.visual y en el paquete de carga.
     * AUTORIDAD ÚNICA de la codificación: lo calculaban por su cuenta el arranque de carga, el
     * disparo y KiChargeServer, y los tres se desincronizaron en cuanto el centinela dejó de
     * ser 0 — la barrera empezaba a cargar como set 1 y solo el disparo usaba su clip propio.
     */
    public int visual() {
        TechniqueAnimOverride ov = type.animOverride();
        return ov != null ? ov.encode() : animSet();
    }

    /** Lo que se ENSEÑA: el nombre puesto por el jugador o, si lo dejó vacío, el del tipo. */
    public Component displayName() {
        return name.isEmpty() ? Component.translatable(type.nameKey()) : Component.literal(name);
    }

    public void set(String name, KiTechniqueType type, int rgb, int size, TechniqueEffect effect) {
        set(name, type, rgb, size, effect, this.chargeSound, this.releaseSound, this.animSet);
    }

    public void set(String name, KiTechniqueType type, int rgb, int size, TechniqueEffect effect,
                    ResourceLocation chargeSound, ResourceLocation releaseSound, int animSet) {
        this.name = sanitizeName(name);
        this.type = type;
        this.rgb = rgb & 0xFFFFFF;
        this.size = clampSize(size);
        // El tipo tiene la última palabra: si no admite el efecto, se cae a NONE aquí y no en
        // cada sitio que lo lea. Una técnica no puede quedar guardada en un estado imposible,
        // y así la migración de NBT viejo no necesita validar nada por su cuenta.
        this.effect = (effect == null || !type.allowsEffect(effect))
                ? TechniqueEffect.NONE : effect;
        this.chargeSound = TechniqueAssets.isValidCharge(chargeSound) ? chargeSound : null;
        this.releaseSound = TechniqueAssets.isValidRelease(releaseSound) ? releaseSound : null;
        this.animSet = TechniqueAnimSet.clamp(animSet);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("type", type.name());
        tag.putInt("rgb", rgb);
        if (rgb2 >= 0) tag.putInt("rgb2", rgb2);
        tag.putInt("size", size);
        tag.putInt("effect", effect.ordinal());
        tag.putInt("animSet", animSet);
        if (chargeSound != null) tag.putString("chargeSound", chargeSound.toString());
        if (releaseSound != null) tag.putString("releaseSound", releaseSound.toString());
        return tag;
    }

    /** null si el tipo guardado ya no existe. */
    public static KiTechnique load(CompoundTag tag) {
        KiTechniqueType type = KiTechniqueType.byName(tag.getString("type"));
        if (type == null) return null;
        KiTechnique t = new KiTechnique(
                tag.getString("name"), type, tag.getInt("rgb"),
                clampSize(tag.getInt("size")),   // recorta los 6 y 7 de partidas viejas
                readEffect(tag),
                // "position" de saves viejos se ignora: la técnica pasa a la del su animSet.
                readId(tag, "chargeSound"), readId(tag, "releaseSound"),
                tag.contains("animSet") ? tag.getInt("animSet") : 1);
        t.setRgb2(tag.contains("rgb2") ? tag.getInt("rgb2") : -1);
        return t;
    }

    private static ResourceLocation readId(CompoundTag tag, String key) {
        return tag.contains(key) ? ResourceLocation.tryParse(tag.getString(key)) : null;
    }

    /** Efecto guardado, con migración del boolean viejo. Un `explosive` sobre un tipo que ahora
     *  lo prohíbe (disco, barrera, ráfaga) se ignora en silencio: el set() lo volvería a NONE
     *  en cualquier caso, y avisar de algo que el jugador no eligió no aporta nada. */
    private static TechniqueEffect readEffect(CompoundTag tag) {
        if (tag.contains("effect")) return TechniqueEffect.byOrdinal(tag.getInt("effect"));
        return tag.getBoolean("explosive") ? TechniqueEffect.EXPLOSIVE : TechniqueEffect.NONE;
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