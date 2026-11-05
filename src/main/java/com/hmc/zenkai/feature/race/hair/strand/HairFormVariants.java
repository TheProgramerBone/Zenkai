package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Qué variante de un juego de DMZ (base / SSJ / SSJ2 / SSJ3) se ve con cada forma de Zenkai
 * (.claude/docs/customizacion-pelo.md, sección "Pelo importado: formatos…", tabla de formas). La que el código no traiga cae a la
 * anterior: SSJ3 -&gt; SSJ2 -&gt; SSJ -&gt; base (SSJ4 -&gt; SSJ). Las formas sin entrada usan la base,
 * teñida por la forma si esta declara hair_rgb.
 *
 * Decisión del usuario (2026-10-12): SSJ God, Ultra Instinto (señal y dominado) y Ultra Ego llevan
 * el pelo de la BASE; Blue (y Rosé, su par) el del SSJ. Por eso ssj_god no está en la tabla.
 * Beast lleva el del SSJ2 (2026-10-12) y, como cualquier otra, cae por la cadena SSJ2 -&gt; SSJ -&gt;
 * base si el juego no trae esa variante. Legendario y Legendario Pleno llevan el del SSJ (2026-10-26,
 * "cambia el peinado del legendary haciéndolo del SSJ"); Ikari, la furia previa al SSJ, la base.
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
    public static final Map<String, Variant> FORMS = Map.ofEntries(
            Map.entry("zenkai:ssj1", Variant.SSJ),
            Map.entry("zenkai:ssj2", Variant.SSJ2),
            Map.entry("zenkai:ssj3", Variant.SSJ3),
            Map.entry("zenkai:ssj4", Variant.SSJ4),
            Map.entry("zenkai:ssj_blue", Variant.SSJ),
            Map.entry("zenkai:ssj_rose", Variant.SSJ),
            Map.entry("zenkai:ssj_blue_evolution", Variant.SSJ),
            Map.entry("zenkai:ssj_rose_evolution", Variant.SSJ),
            Map.entry("zenkai:beast", Variant.SSJ2),
            Map.entry("zenkai:ssj_green", Variant.SSJ),
            Map.entry("zenkai:ssj_green_max", Variant.SSJ));

    public static Variant wanted(@Nullable ResourceLocation formId) {
        return wanted(formId, 0);
    }

    /**
     * Los peinados por código (DMZ, importados) llevan VARIANTES (base, SSJ, SSJ2, SSJ3, SSJ4) elegidas por esta tabla de formas
     * del mod. Una forma de un addon no está en ella y salía siempre con la base (bug del SSJ Blue 3 con el peinado de Goku,
     * 2026-11-02): ahora hereda la variante de la forma que declara en su {@code hair_style} (el id de otra forma, hasta 3
     * saltos), p. ej. {@code "zenkai:ssj3"} = la variante de SSJ3.
     */
    private static Variant wanted(@Nullable ResourceLocation formId, int depth) {
        if (formId == null) return Variant.BASE;
        Variant v = FORMS.get(formId.toString());
        if (v != null) return v;
        com.hmc.zenkai.feature.forms.FormDef d = com.hmc.zenkai.feature.forms.FormDef.get(formId);
        if (d != null && !d.hairStyle().isEmpty() && depth < 3) {
            ResourceLocation ref = ResourceLocation.tryParse(d.hairStyle());
            if (ref != null && !ref.equals(formId)) return wanted(ref, depth + 1);
        }
        return Variant.BASE;
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
