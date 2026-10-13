package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Qué variante de un juego de DMZ (base / SSJ / SSJ2 / SSJ3) se ve con cada forma de Zenkai
 * (.claude/docs/customizacion-cara-y-pelo.md, sección "Pelo importado: formatos…", tabla de formas). La que el código no traiga cae a la
 * anterior: SSJ3 -&gt; SSJ2 -&gt; SSJ -&gt; base (SSJ4 -&gt; SSJ). Las formas sin entrada usan la base,
 * teñida por la forma si esta declara hair_rgb.
 *
 * Decisión del usuario (2026-10-12): SSJ God, Ultra Instinto (señal y dominado) y Ultra Ego llevan
 * el pelo de la BASE; Blue (y Rosé, su par) el del SSJ. Por eso ssj_god no está en la tabla.
 * Beast lleva el del SSJ2 (2026-10-12) y, como cualquier otra, cae por la cadena SSJ2 -&gt; SSJ -&gt;
 * base si el juego no trae esa variante.
 */
public final class HairFormVariants {
    private HairFormVariants() {}

    public enum Variant {
        BASE, SSJ, SSJ2, SSJ3, SSJ4;

        public String suffix() {
            return this == BASE ? "" : "_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Forma de Zenkai -&gt; variante pedida. */
    public static final Map<String, Variant> FORMS = Map.of(
            "zenkai:ssj1", Variant.SSJ,
            "zenkai:ssj2", Variant.SSJ2,
            "zenkai:ssj3", Variant.SSJ3,
            "zenkai:ssj4", Variant.SSJ4,
            "zenkai:ssj_blue", Variant.SSJ,
            "zenkai:ssj_rose", Variant.SSJ,
            "zenkai:beast", Variant.SSJ2);

    public static Variant wanted(@Nullable ResourceLocation formId) {
        return formId == null ? Variant.BASE : FORMS.getOrDefault(formId.toString(), Variant.BASE);
    }

    /**
     * La variante pedida o, si el juego no la trae, la anterior que sí exista. SSJ4 sin variante
     * propia usa la de SSJ (tabla de formas de la guía), no la de SSJ3: el pelo largo de SSJ3 no es el de SSJ4.
     */
    public static Variant available(StrandSet.Variants v, Variant wanted) {
        Variant w = wanted == Variant.SSJ4 && v.ssj4() == null ? Variant.SSJ : wanted;
        while (w != Variant.BASE && get(v, w) == null) w = Variant.values()[w.ordinal() - 1];
        return w;
    }

    @Nullable
    public static StrandSet get(StrandSet.Variants v, Variant w) {
        return switch (w) {
            case BASE -> v.base();
            case SSJ -> v.ssj();
            case SSJ2 -> v.ssj2();
            case SSJ3 -> v.ssj3();
            case SSJ4 -> v.ssj4();
        };
    }
}
