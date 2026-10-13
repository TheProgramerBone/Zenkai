package com.hmc.zenkai.feature.race.hair.strand;

import com.hmc.zenkai.feature.race.hair.CustomHairLibrary;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caché de códigos ya leídos para el render de peinados importados (cada cliente convierte el
 * código al vuelo; ver CustomHairModels). Leer un código cuesta milisegundos, así que se hace una
 * vez por código y no por fotograma. LRU acotado: un servidor lleno de peinados distintos no la
 * hace crecer sin límite.
 *
 * Ids de modelo: {@code zenkai_custom_hair:<id>/<variante>}, con el id de la biblioteca
 * (derivado del código), o {@code <id>/<variante>_to_<variante>_<paso>} para los pasos intermedios
 * de un cambio de forma (StrandBlend). El modelo GeckoLib lo construye el cliente a partir de este id.
 */
public final class CustomHairCodes {
    private CustomHairCodes() {}

    public static final String MODEL_NAMESPACE = "zenkai_custom_hair";
    private static final int MAX_CACHED = 64;

    /** null = el código no se pudo leer (se recuerda para no reintentarlo en cada fotograma). */
    private static final Map<String, Decoded> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Decoded> eldest) {
            return size() > MAX_CACHED;
        }
    };

    private record Decoded(String code, @Nullable StrandSet.Variants variants, boolean colors) {}

    /** Paleta de colores por id de modelo (para no rehacer un paso intermedio en cada fotograma). */
    private static final Map<ResourceLocation, StrandColors.Palette> PALETTES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, StrandColors.Palette> eldest) {
            return size() > 256;
        }
    };

    /** Lee (o recuerda) un código. null si es ilegible. */
    @Nullable
    public static synchronized StrandSet.Variants variants(String code) {
        String id = CustomHairLibrary.idOf(code);
        Decoded d = CACHE.get(id);
        if (d == null) {
            StrandSet.Variants v;
            try {
                v = HairCodes.decode(code).variants();
            } catch (HairCodeException | RuntimeException e) {
                v = null;
            }
            d = new Decoded(code, v, v != null && StrandColors.hasColors(v));
            CACHE.put(id, d);
        }
        return d.variants();
    }

    /**
     * Pasos del cambio de forma: un modelo intermedio por paso (cada uno se hornea una vez y se
     * reutiliza). 24 sobre los 5 s de hold de SSJ: un paso cada ~0,2 s (con 12 el usuario pidió
     * más, 2026-10-12), sin hornear un modelo por fotograma.
     */
    public static final int BLEND_STEPS = 24;
    private static final String BLEND_SEPARATOR = "_to_";

    /** ¿El código trae colores propios en algún mechón? (enseña el interruptor de la pestaña Pelo). */
    public static synchronized boolean hasColors(String code) {
        variants(code);
        Decoded d = CACHE.get(CustomHairLibrary.idOf(code));
        return d != null && d.colors();
    }

    /** Paleta de colores (columnas de la textura) del modelo, o null si no es de un código leído. */
    @Nullable
    public static synchronized StrandColors.Palette palette(@Nullable ResourceLocation model) {
        if (model == null) return null;
        StrandColors.Palette p = PALETTES.get(model);
        if (p != null) return p;
        StrandSet set = strandsFor(model);
        if (set == null) return null;
        p = StrandColors.of(set);
        PALETTES.put(model, p);
        return p;
    }

    /** Modelo a usar para un código con la forma activa, o null si el código no vale. */
    @Nullable
    public static ResourceLocation model(String code, @Nullable ResourceLocation formId) {
        return model(code, formId, formId, 1f);
    }

    /**
     * Modelo del peinado a mitad de camino entre dos formas (t = 0 -&gt; from, 1 -&gt; to), cuantizado
     * a {@link #BLEND_STEPS}. Si las dos formas muestran la misma variante, es esa sin más.
     */
    @Nullable
    public static ResourceLocation model(String code, @Nullable ResourceLocation from,
                                         @Nullable ResourceLocation to, float t) {
        StrandSet.Variants v = variants(code);
        if (v == null) return null;
        HairFormVariants.Variant a = HairFormVariants.available(v, HairFormVariants.wanted(from));
        HairFormVariants.Variant b = HairFormVariants.available(v, HairFormVariants.wanted(to));
        int step = blendStep(t);
        String id = CustomHairLibrary.idOf(code) + "/";
        if (a == b || step <= 0) return ResourceLocation.fromNamespaceAndPath(MODEL_NAMESPACE, id + name(a));
        if (step >= BLEND_STEPS) return ResourceLocation.fromNamespaceAndPath(MODEL_NAMESPACE, id + name(b));
        return ResourceLocation.fromNamespaceAndPath(MODEL_NAMESPACE,
                id + name(a) + BLEND_SEPARATOR + name(b) + "_" + step);
    }

    public static int blendStep(float t) {
        return Math.round(Math.max(0f, Math.min(1f, t)) * BLEND_STEPS);
    }

    public static boolean isBlend(ResourceLocation model) {
        return MODEL_NAMESPACE.equals(model.getNamespace()) && model.getPath().contains(BLEND_SEPARATOR);
    }

    private static String name(HairFormVariants.Variant v) {
        return v.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** El juego de mechones de un id de modelo (variante o paso intermedio), si su código sigue en la caché. */
    @Nullable
    public static synchronized StrandSet strandsFor(ResourceLocation model) {
        if (!MODEL_NAMESPACE.equals(model.getNamespace())) return null;
        String path = model.getPath();
        int slash = path.indexOf('/');
        if (slash < 0) return null;
        Decoded d = CACHE.get(path.substring(0, slash));
        if (d == null || d.variants() == null) return null;
        String rest = path.substring(slash + 1);
        try {
            int sep = rest.indexOf(BLEND_SEPARATOR);
            if (sep < 0) return HairFormVariants.get(d.variants(), variant(rest));
            String tail = rest.substring(sep + BLEND_SEPARATOR.length());
            int us = tail.lastIndexOf('_');
            StrandSet a = HairFormVariants.get(d.variants(), variant(rest.substring(0, sep)));
            StrandSet b = HairFormVariants.get(d.variants(), variant(tail.substring(0, us)));
            int step = Integer.parseInt(tail.substring(us + 1));
            if (a == null || b == null) return null;
            return StrandBlend.blend(a, b, step / (float) BLEND_STEPS);
        } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static HairFormVariants.Variant variant(String name) {
        return HairFormVariants.Variant.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }
}
