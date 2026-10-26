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
        // Lágrima (2026-09-30): comparado en vídeo con DragonMine Z, los valores de la Fase 2
        // (radius 0.75, half_height 1.3, center_y 0.85, amp 1.5, peaks 12, freq_y 1.2, up_dir
        // 0.65) daban un ABANICO abierto de ~3 veces el jugador con 5-6 lenguas enormes. El anime
        // y DMZ tienen una lágrima cerrada de ~1.6 veces el jugador con muchos picos pequeños:
        // elipsoide más estrecha y centrada en el pecho, picos cortos y numerosos, varios pisos de
        // lenguas y el estrechamiento de TAPER/TIP.
        /** Semieje horizontal (radio) de la elipsoide base, en bloques, a escala de cuerpo 1. */
        RADIUS("radius", 0.62f),
        // Vela (2026-09-30, tercer vídeo comparado con DragonMine Z): con center_y 1.0 y un solo
        // semieje de 1.15 el huevo tenía el ancho en la cabeza y se cerraba en los pies (y el
        // fundido de la base lo dejaba empezar en las rodillas). En DMZ la llama es ancha abajo,
        // empieza ~1 bloque bajo los pies y se afila hacia la punta. Centro en la cadera, mitad
        // de arriba larga (la punta queda a la misma altura que antes) y mitad de abajo propia.
        /** Semieje vertical de la mitad de ARRIBA de la elipsoide, en bloques (el cuerpo mide 1.8). */
        HALF_HEIGHT("half_height", 1.7f),
        /** Semieje vertical de la mitad de ABAJO, en bloques: cuánto baja la base desde el centro. */
        LOWER_HALF("lower_half", 1.2f),
        /** Altura del centro de la elipsoide sobre los pies, en bloques (= la parte más ancha). */
        CENTER_Y("center_y", 0.5f),
        /** Y de la esfera unitaria (-1 = base) donde la base empieza a verse... */
        BASE_FADE_LO("base_fade_lo", -0.95f),
        /** ...y donde ya se ve entera. Con los defaults, entera hasta ~0.2 bajo los pies. */
        BASE_FADE_HI("base_fade_hi", -0.55f),
        /** Bloques de desplazamiento de un pico típico a media altura. */
        AMP("amp", 0.45f),
        /** Refuerzo del desplazamiento hacia la parte alta: 0 = igual en cualquier altura; 0.5 =
         *  los picos de arriba un 50% más largos que los de la cintura (silueta de lágrima con las
         *  lenguas más altas sobre la cabeza, no una bola erizada uniforme). */
        TOP_BOOST("top_boost", 0.5f),
        /** Nº de picos alrededor del eje vertical. */
        PEAKS("peaks", 18f),
        /** Pisos de lenguas en vertical (frecuencia de la onda en Y, sobre la esfera unitaria). */
        FREQ_Y("freq_y", 2.2f),
        /** Velocidad a la que suben las lenguas (ciclos de la onda vertical por segundo). */
        SPEED("speed", 0.9f),
        /** Peso del ruido sobre la onda de picos (temblor del contorno). */
        DETAIL("detail", 0.35f),
        /** Peso de las rayas verticales de energía en la superficie (canal G del ruido). */
        STREAK("streak", 0.45f),
        /** 0 = desplazamiento radial puro (bola erizada), 1 = por completo hacia arriba (lenguas). */
        UP_DIR("up_dir", 0.8f),
        /** Estrechamiento arriba: 0 = elipsoide (las lenguas altas se abren en V), 1 = se cierra
         *  por completo en punta. Usa la altura ya desplazada: cuanto más sube una lengua, más se
         *  acerca al eje. */
        TAPER("taper", 0.55f),
        /** Bloques de punta extra sobre la cabeza (cerca del polo de la elipsoide). */
        TIP("tip", 0.4f),
        // Cuarto vídeo (2026-09-30). Valor NEUTRO para comparar entre paréntesis (lo de antes).
        /** up_dir de los flancos y la base: más bajo = lenguas hacia fuera por los costados, que
         *  así cambian el contorno (neutro: igual que up_dir, 0.8). Se propuso 0.4; el usuario lo
         *  probó en juego y eligió 0.9 (2026-09-30): lenguas de los costados casi verticales, la
         *  silueta de vela limpia en vez de erizada. */
        SIDE_UP_DIR("side_up_dir", 0.9f),
        /** Altura, en fracción del semieje de arriba, a partir de la que se comprime lo que sube
         *  (neutro: cualquiera con crown_soft 1). En 1 = el techo de la elipsoide: la llama
         *  conserva su alto y solo se comprime lo que sobresale; por debajo de 1 la acorta. */
        CROWN_KNEE("crown_knee", 1.0f),
        /** Cuánto se conserva de lo que sube por encima de crown_knee: 1 = nada se comprime, 0 =
         *  techo plano. Quita las agujas de la "columna fantasma" (neutro: 1). */
        CROWN_SOFT("crown_soft", 0.5f),
        /** Peso de las puntas de lengua en la intensidad de las bandas: alto = "pétalos" de
         *  color dentro del cuerpo, vistos de frente (neutro: 0.5). */
        PEAK_WEIGHT("peak_weight", 0.3f),
        /** Intensidad del borde en la base: bajo = sin anillo claro bajo los pies, la base se
         *  abre como en DMZ (neutro: 1). */
        BOTTOM_RIM("bottom_rim", 0.4f),
        /** Cuánto se frena el desplazamiento en el polo: sin esto las lenguas convergían en una
         *  aguja fina que se leía como una columna fantasma sobre la cabeza (vídeo 2026-09-30). */
        POLE_CALM("pole_calm", 0.75f),
        // Tamaño y PODER (2026-09-30, vídeo del usuario: "no se ve un cambio notorio entre un PL
        // bajo y uno alto"). Con la lágrima, amp bajó de 1.5 a 0.45, y el poder solo alargaba las
        // lenguas (amp) y ensanchaba un 12% el radio: entre un novato y un endgame la llama crecía
        // unos 0.3 bloques. Ahora el poder ESCALA LA LLAMA ENTERA desde los pies.
        /** Escala de la llama entera con poder base (1 = la lágrima de la primera pasada). */
        // 1.2 -> 1.35 (2026-10-26, pedido del usuario): "a veces es demasiado pequeña para el
        // personaje". Sube el SUELO (el aura de poder base); el crecimiento con el poder no cambia.
        SIZE("size", 1.35f),
        /** Crecimiento de la escala por cada unidad de poder sobre la base
         *  (AuraProfile.pulsedSize / SIZE_BASE - 1: 0 de novato, ~1-1.3 de endgame a plena
         *  potencia). 0.4 da ~x1.4 de endgame sobre la base ya agrandada. */
        POWER_GROWTH("power_growth", 0.4f),
        // Cel-shading (2026-09-30): 4 bandas planas en vez del degradado fresnel, como el aura.fsh
        // de DragonMine Z. Umbrales sobre la intensidad 0..1 (fresnel + puntas + raya).
        /** 1 = bandas planas (cel-shading), 0 = degradado suave de antes (para comparar). */
        CEL("cel", 1f),
        /** Intensidad a partir de la que se pinta el borde claro. */
        CEL_RIM("cel_rim", 0.8f),
        /** Intensidad a partir de la que se pinta la banda clara. */
        CEL_LIGHT("cel_light", 0.55f),
        /** Intensidad a partir de la que se pinta el cuerpo saturado (por debajo, interior casi
         *  transparente). */
        CEL_BODY("cel_body", 0.28f),
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
        // capas superpuestas lavarían a blanco (rechazado en juego, ver tecnicas-y-vfx.md).
        // Bajados el 2026-09-30 (antes 0.3 / 0.7 / 0.8): en el vídeo comparado con DMZ la llama de
        // Base salía casi BLANCA — emisión + bloom sumaban luz hasta borrar el tinte. Con el
        // cel-shading el borde se lee por su banda clara, no por la luz que suma.
        /** Emisión del cuerpo de la llama (centro, de frente). */
        EMIT_BODY("emit_body", 0.1f),
        /** Emisión del borde y de las puntas de los picos. */
        EMIT_EDGE("emit_edge", 0.35f),
        /** Peso general de la llama en la pasada de bloom (0 = la llama no brilla). */
        BLOOM("bloom", 0.35f),
        /** Cuánto aporta al bloom el cuerpo frente al borde: el sangrado nace del contorno
         *  (aura_8), no del centro — si no, una llama que llena media pantalla la blanquearía. */
        BLOOM_BODY("bloom_body", 0.25f),
        // Color y doble capa (2026-09-29, capturas de Blue+Kaioken y Beast: el color interior
        // desaparecía lavado a blanco). Ver AuraFlameColors, "COLOR POR CAPA".
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
        INNER_ALPHA("inner_alpha", 2.2f), // antes 3.5: con Blue+Kaioken el jugador casi no se veía (vídeo 2026-09-30)
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
