package com.hmc.zenkai.client.aura.flame;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Números visuales de la llama exterior (aura_flame_shell), calibrables EN CALIENTE con
 * {@code /zenkaiauraflame set <param> <valor>} — mismo patrón que {@code KiVfxTuning} con /zkvfx:
 * solo en memoria, se pierden al cerrar el juego. La calibración de escala (Fase 2 del plan)
 * no se puede acertar leyendo código; cuando un número convence en juego, se copia aquí como
 * nuevo default.
 * <p>
 * Desde la Fase 6 estos defaults son el CENTRO de los rangos que mapea {@code AuraProfile}
 * (spike/turbulence/spread/mass/height/density), no valores fijos: ver {@link AuraFlameParams}.
 */
public final class AuraFlameTuning {

    private AuraFlameTuning() {}

    public enum Param {
        // Fase 2 (2026-09-29): primera pasada de escala tras ver la Fase 1 en juego — la llama
        // apenas pasaba de la cabeza y se estrechaba en los pies (valores de Fase 1: radius 0.55,
        // half_height 1.05, center_y 0.95, amp 1.4, sin top_boost). Elipsoide más ancha y más
        // baja: su parte más estrecha queda BAJO tierra (el depth-test la tapa) y a ras de suelo
        // la llama ya tiene casi todo su ancho, como en las referencias (aura_1/aura_4).
        /** Semieje horizontal (radio) de la elipsoide base, en bloques, a escala de cuerpo 1. */
        RADIUS("radius", 0.75f),
        /** Semieje vertical de la elipsoide base, en bloques (el cuerpo mide 1.8). */
        HALF_HEIGHT("half_height", 1.3f),
        /** Altura del centro de la elipsoide sobre los pies, en bloques. */
        CENTER_Y("center_y", 0.85f),
        /** Bloques de desplazamiento de un pico típico a media altura. */
        AMP("amp", 1.5f),
        /** Refuerzo del desplazamiento hacia la parte alta: 0 = igual en cualquier altura; 0.5 =
         *  los picos de arriba un 50% más largos que los de la cintura (silueta de lágrima con las
         *  lenguas más altas sobre la cabeza, no una bola erizada uniforme). */
        TOP_BOOST("top_boost", 0.5f),
        /** Nº de picos alrededor del eje vertical. */
        PEAKS("peaks", 12f),
        /** Pisos de lenguas en vertical (frecuencia de la onda en Y, sobre la esfera unitaria). */
        FREQ_Y("freq_y", 1.2f),
        /** Velocidad a la que suben las lenguas (ciclos de la onda vertical por segundo). */
        SPEED("speed", 0.9f),
        /** Peso del ruido sobre la onda de picos (temblor del contorno). */
        DETAIL("detail", 0.35f),
        /** Peso de las rayas verticales de energía en la superficie (canal G del ruido). */
        STREAK("streak", 0.45f),
        /** 0 = desplazamiento radial puro (bola erizada), 1 = por completo hacia arriba (lenguas). */
        UP_DIR("up_dir", 0.65f),
        /** Alfa del centro de la llama (de frente): bajo para que el cuerpo se lea dentro. */
        ALPHA_CENTER("alpha_center", 0.10f),
        /** Alfa del borde de la llama (de canto). */
        ALPHA_EDGE("alpha_edge", 0.85f),
        /** Exponente del fresnel: más alto = borde más fino. */
        FRESNEL_POWER("fresnel_power", 2.0f),
        /** Divisor del fresnel: más bajo = el borde llega antes a pleno. */
        FRESNEL_DIVIS("fresnel_divis", 0.8f),
        // Fase 4 (bloom real vía KiVfxBloomPipeline). Salida premultiplicada vec4(c*a, a*(1-emit)):
        // emit 0 = tapa como un translúcido, 1 = suma luz. Nunca aditivo puro en el cuerpo: las
        // capas superpuestas lavarían a blanco (rechazado en juego, aura-pendientes.md §2).
        /** Emisión del cuerpo de la llama (centro, de frente). */
        EMIT_BODY("emit_body", 0.3f),
        /** Emisión del borde y de las puntas de los picos. */
        EMIT_EDGE("emit_edge", 0.7f),
        /** Peso general de la llama en la pasada de bloom (0 = la llama no brilla). */
        BLOOM("bloom", 0.8f),
        /** Cuánto aporta al bloom el cuerpo frente al borde: el sangrado nace del contorno
         *  (aura_8), no del centro — si no, una llama que llena media pantalla la blanquearía. */
        BLOOM_BODY("bloom_body", 0.25f),
        // Color y doble capa (2026-09-29, capturas de Blue+Kaioken y Beast: el color interior
        // desaparecía lavado a blanco). Ver AuraFlameShellRenderer, "COLOR POR CAPA".
        /** Cuánto se calienta el centro de cada capa hacia su capa caliente (derivedHot): 0 =
         *  tinte puro, 1 = capa caliente entera (lo de la Fase 4, que blanqueaba). */
        HOT_MIX("hot_mix", 0.35f),
        /** Realce de saturación de los colores de la llama (1 = tal cual). Los aura_rgb de varias
         *  formas son tonos claros (Blue 0x5FE8FF): translúcidos sobre un cielo de día y con
         *  emisión se leían como un cian "muy, muy pálido" (usuario, 2026-09-29). Solo afecta a
         *  la llama nueva: los datos y el legacy no cambian. */
        SATURATION("saturation", 1.4f),
        /** Capa interior (formas de dos tonos): escala de su elipsoide respecto a la exterior. */
        INNER_SCALE("inner_scale", 0.8f),
        /** Capa interior: fracción del largo de picos de la exterior (es la nube ceñida, no picos). */
        INNER_AMP("inner_amp", 0.35f),
        /** Capa interior: multiplicador del alfa del centro — se lee como un cuerpo de color
         *  (el interior nuboso de aura_2), no solo como contorno. */
        INNER_ALPHA("inner_alpha", 3.5f),
        /** Fase 6: cuánto manda la personalidad de raza+forma (AuraProfile) sobre estos números.
         *  0 = todas las auras iguales (lo de la Fase 4), 1 = mapeo completo, hasta 3 = exagerado
         *  (para calibrar). Ver AuraFlameParams. */
        PROFILE_MIX("profile_mix", 1.0f),
        /** Segundos de encendido (crecimiento 0 -> 1). */
        FADE_IN("fade_in", 0.8f),
        /** Segundos de apagado (1 -> 0). */
        FADE_OUT("fade_out", 0.6f);

        public final String key;
        public final float def;

        Param(String key, float def) { this.key = key; this.def = def; }

        public static Param byKey(String key) {
            String k = key.toLowerCase(Locale.ROOT);
            for (Param p : values()) if (p.key.equals(k)) return p;
            return null;
        }
    }

    private static final Map<Param, Float> OVERRIDES = new EnumMap<>(Param.class);

    public static float get(Param p) {
        Float v = OVERRIDES.get(p);
        return v != null ? v : p.def;
    }

    public static void set(Param p, float value) { OVERRIDES.put(p, value); }

    public static void reset() { OVERRIDES.clear(); }

    public static boolean isOverridden(Param p) { return OVERRIDES.containsKey(p); }
}
