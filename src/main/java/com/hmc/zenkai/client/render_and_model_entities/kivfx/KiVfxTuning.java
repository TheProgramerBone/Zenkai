package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.feature.technique.KiTechniqueType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parámetros de CALIBRACIÓN del VFX de ki ajustables en vivo con {@code /zkvfx} (ver
 * {@link KiVfxTuningCommand}) — pedido del usuario 2026-09-24 para calibrar en UNA sesión en vez
 * de un ciclo recompilar → grabar vídeo → ajustar un número.
 * <p>
 * Solo memoria del cliente: nada se guarda en disco. {@code /zkvfx dump} imprime (y escribe en
 * latest.log) los valores distintos del de fábrica, que es lo que se copia después al código.
 * <p>
 * Cada parámetro es GLOBAL (vale para cualquier técnica) o además admite OVERRIDE POR TÉCNICA. Los
 * {@code MUL} multiplican el valor de la tabla de {@link KiVfxProfile}; el resto son absolutos.
 */
public final class KiVfxTuning {
    private KiVfxTuning() {}

    public enum Param {
        BLOOM_INTENSITY("bloom.intensity", 0.9f, false, "fuerza del composite del bloom"),
        BLOOM_KNEE("bloom.knee", 0.5f, false, "por debajo, el bloom pasa intacto"),
        BLOOM_LIMIT("bloom.limit", 0.9f, false, "techo al que tiende el bloom"),
        BLOOM_BODY("bloom.body", 0.25f, true, "peso del cuerpo (no núcleo) en la pasada de bloom"),
        EMIT_BODY("emit.body", 0.35f, true, "emisión del cuerpo de la cáscara: 0 tapa, 1 suma luz"),
        EMIT_CORE("emit.core", 0.85f, true, "emisión en la banda de núcleo de la cáscara"),
        EMIT_GLOW("emit.glow", 0.80f, false, "emisión de halo, núcleo explícito y núcleo de estela"),
        EMIT_SOFT("emit.soft", 0.40f, false, "emisión de la capa exterior de estela y rayos"),
        CORE_WHITE_MUL("shell.core_white", 1f, true, "MUL sobre coreWhite (blanqueo del núcleo)"),
        CORE_ALPHA_MUL("core.alpha", 1f, true, "MUL sobre el alfa del núcleo explícito"),
        CORE_WORLD_ALPHA("core.world_alpha", 0.55f, true, "alfa del núcleo explícito en el mundo"),
        HALO_ALPHA_MUL("halo.alpha", 1f, true, "MUL sobre el alfa del halo"),
        TRAIL_ALPHA_MUL("trail.alpha", 1f, true, "MUL sobre el alfa de la estela");

        public final String key;
        public final float def;
        public final boolean perTechnique;
        public final String help;

        Param(String key, float def, boolean perTechnique, String help) {
            this.key = key;
            this.def = def;
            this.perTechnique = perTechnique;
            this.help = help;
        }

        @Nullable
        public static Param byKey(String key) {
            for (Param p : values()) if (p.key.equalsIgnoreCase(key)) return p;
            return null;
        }
    }

    private static final float[] GLOBAL = new float[Param.values().length];
    private static final Map<KiTechniqueType, Map<Param, Float>> OVERRIDES = new EnumMap<>(KiTechniqueType.class);

    static { resetAll(); }

    /** Valor efectivo para {@code type} (override si lo hay, si no el global). {@code type} null
     *  = solo el global. */
    public static float get(Param p, @Nullable KiTechniqueType type) {
        if (type != null && p.perTechnique) {
            Map<Param, Float> m = OVERRIDES.get(type);
            if (m != null) {
                Float v = m.get(p);
                if (v != null) return v;
            }
        }
        return GLOBAL[p.ordinal()];
    }

    /** Atajo para el caso habitual: el tipo sale del perfil que ya tiene el renderer. */
    public static float get(Param p, KiVfxProfile profile) {
        return get(p, KiVfxProfile.typeOf(profile));
    }

    public static void setGlobal(Param p, float v) { GLOBAL[p.ordinal()] = v; }

    public static void setFor(KiTechniqueType type, Param p, float v) {
        OVERRIDES.computeIfAbsent(type, k -> new EnumMap<>(Param.class)).put(p, v);
    }

    public static void resetAll() {
        for (Param p : Param.values()) GLOBAL[p.ordinal()] = p.def;
        OVERRIDES.clear();
    }

    public static void reset(KiTechniqueType type) { OVERRIDES.remove(type); }

    /** Líneas {@code clave = valor} de lo que difiere de fábrica — lo que hay que pasar al código. */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (Param p : Param.values()) {
            float v = GLOBAL[p.ordinal()];
            if (v != p.def) sb.append(String.format(Locale.ROOT, "%s = %.3f (fábrica %.3f)%n", p.key, v, p.def));
        }
        for (Map.Entry<KiTechniqueType, Map<Param, Float>> e : OVERRIDES.entrySet()) {
            for (Map.Entry<Param, Float> o : e.getValue().entrySet()) {
                sb.append(String.format(Locale.ROOT, "%s[%s] = %.3f%n",
                        o.getKey().key, e.getKey().name().toLowerCase(Locale.ROOT), o.getValue()));
            }
        }
        return sb.length() == 0 ? "(todo en valores de fábrica)" : sb.toString().trim();
    }
}
