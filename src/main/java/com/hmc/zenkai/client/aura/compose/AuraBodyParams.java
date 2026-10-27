package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.flame.AuraFlameParams;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Números del CUERPO de un aura para un jugador en un frame. Parte de {@link AuraFlameParams} (el
 * mapeo ya calibrado de {@code AuraProfile} + PL, que sigue siendo la única fuente del PODER) y
 * encima la familia y el estilo JSON lo moldean por clave ({@link Key}).
 * <p>
 * MUTABLE a propósito, con los valores en un {@code float[]} indexado por la clave: la familia, el
 * estilo y los estados dinámicos aplican cambios en cadena sobre el mismo objeto, y el JSON los
 * nombra por texto ({@link Key#byName}). Se copia ({@link #copy}) antes de entregarlo a la tarea
 * diferida de la cola: la tarea se reproduce dos veces por frame y no puede ver cambios a medias.
 * <p>
 * Las claves DIMENSIONALES (bloques) llevan dentro la escala por poder
 * ({@link AuraFlameParams#sizeScale}); un valor ABSOLUTO del JSON ({@code body_set}) para una de
 * ellas se multiplica por esa misma escala, para que un "radius": 0.8 siga creciendo con el PL.
 */
public final class AuraBodyParams {

    public enum Key {
        RADIUS("radius", true), HALF_HEIGHT("half_height", true), LOWER_HALF("lower_half", true),
        CENTER_Y("center_y", true), AMP("amp", true), TIP("tip", true),
        TOP_BOOST("top_boost"), PEAKS("peaks"), SHARP("sharp"), FREQ_Y("freq_y"), SPEED("speed"),
        DETAIL("detail"), STREAK("streak"), UP_DIR("up_dir"), SIDE_UP_DIR("side_up_dir"),
        TAPER("taper"), POLE_CALM("pole_calm"), CROWN_KNEE("crown_knee"), CROWN_SOFT("crown_soft"),
        PEAK_WEIGHT("peak_weight"), BOTTOM_RIM("bottom_rim"),
        BASE_FADE_LO("base_fade_lo"), BASE_FADE_HI("base_fade_hi"),
        CEL("cel"), CEL_RIM("cel_rim"), CEL_LIGHT("cel_light"), CEL_BODY("cel_body"),
        ALPHA_CENTER("alpha_center"), ALPHA_EDGE("alpha_edge"),
        FRESNEL_POWER("fresnel_power"), FRESNEL_DIVIS("fresnel_divis"),
        EMIT_BODY("emit_body"), EMIT_EDGE("emit_edge"), BLOOM("bloom"), BLOOM_BODY("bloom_body"),
        /** Opacidad general (PODER alpha) y cuánto se acerca el centro al blanco (PODER core). */
        OPACITY("opacity"), CORE_WHITE("core_white"),
        // ── Solo de las familias nuevas (los shaders que no las leen las ignoran) ──
        /** SOFT: vaivén lateral de los lóbulos. */
        SWAY("sway"),
        /** SOFT/DENSE: respiración del volumen entero (amplitud relativa, 0.03 = ±3%). */
        BREATHE("breathe"),
        /** SOFT: afilado de las lenguas de arriba (0 = romas). */
        TONGUE_SHARP("tongue_sharp"),
        /** SOFT: nube del interior (0..1). DENSE: vetas que suben por el interior (0..1). */
        CLOUD("cloud"),
        /** DENSE: profundidad (oscurecido saturado) del interior, 0..1. */
        DEPTH("depth"),
        /** SOFT/FLOW: ancho del suavizado entre bandas (0.005 = casi duro). */
        BAND_SOFT("band_soft"),
        /** FLOW: cuánto se hincha cada cápsula respecto a su radio (0.6 = 60% más gruesa). */
        INFLATE("inflate"),
        /** FLOW: lenguas que suben desde las caras de arriba del contorno (0..1.5). */
        LICKS("licks"),
        /** FLOW: motas de luz en la superficie (0..1, aura_6). */
        FLECKS("flecks"),
        /** FLAME: largo aleatorio por pico (0 = picos iguales, 0.6 = de 0.4x a 1.6x). */
        PEAK_VAR("peak_var"),
        /** FLAME: perfil de LÁGRIMA en la mitad de arriba (0 = elipsoide, 1 = se cierra en punta).
         *  aura_1/aura_4/aura_11: la llama se estrecha hasta una punta alta, no acaba en cúpula. */
        TEARDROP("teardrop"),
        /** FLAME: relleno de la parte alta (0..1). En las referencias la llama es densa y brilla
         *  ARRIBA y casi transparente junto al cuerpo; con solo fresnel pasaba al revés. */
        TOP_FILL("top_fill");

        public final String key;
        /** true = está en bloques y lleva dentro la escala por poder. */
        public final boolean dimensional;

        Key(String key) { this(key, false); }

        Key(String key, boolean dimensional) {
            this.key = key;
            this.dimensional = dimensional;
        }

        private static final Key[] VALUES = values();

        public static @Nullable Key byName(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            for (Key k : VALUES) if (k.key.equals(n)) return k;
            return null;
        }
    }

    private static final int N = Key.values().length;

    private final float[] v = new float[N];
    /** Escala por poder de este frame ({@link AuraFlameParams#sizeScale}); ver el javadoc de la clase. */
    private float powerScale = 1f;

    private AuraBodyParams() {}

    /** Punto de partida: la llama calibrada con la personalidad y el poder del perfil. */
    public static AuraBodyParams from(AuraFlameParams f, float powerScale) {
        AuraBodyParams p = new AuraBodyParams();
        p.powerScale = powerScale;
        p.put(Key.RADIUS, f.radius());
        p.put(Key.HALF_HEIGHT, f.halfHeight());
        p.put(Key.LOWER_HALF, f.lowerHalf());
        p.put(Key.CENTER_Y, f.centerY());
        p.put(Key.AMP, f.amp());
        p.put(Key.TIP, f.tip());
        p.put(Key.TOP_BOOST, f.topBoost());
        p.put(Key.PEAKS, f.peaks());
        p.put(Key.SHARP, f.sharp());
        p.put(Key.FREQ_Y, f.freqY());
        p.put(Key.SPEED, f.speed());
        p.put(Key.DETAIL, f.detail());
        p.put(Key.STREAK, f.streak());
        p.put(Key.UP_DIR, f.upDir());
        p.put(Key.SIDE_UP_DIR, f.sideUpDir());
        p.put(Key.TAPER, f.taper());
        p.put(Key.POLE_CALM, f.poleCalm());
        p.put(Key.CROWN_KNEE, f.crownKnee());
        p.put(Key.CROWN_SOFT, f.crownSoft());
        p.put(Key.PEAK_WEIGHT, f.peakWeight());
        p.put(Key.BOTTOM_RIM, f.bottomRim());
        p.put(Key.BASE_FADE_LO, f.baseFadeLo());
        p.put(Key.BASE_FADE_HI, f.baseFadeHi());
        p.put(Key.CEL, f.cel());
        p.put(Key.CEL_RIM, f.celRim());
        p.put(Key.CEL_LIGHT, f.celLight());
        p.put(Key.CEL_BODY, f.celBody());
        p.put(Key.ALPHA_CENTER, f.alphaCenter());
        p.put(Key.ALPHA_EDGE, f.alphaEdge());
        p.put(Key.FRESNEL_POWER, f.fresnelPower());
        p.put(Key.FRESNEL_DIVIS, f.fresnelDivis());
        p.put(Key.EMIT_BODY, f.emitBody());
        p.put(Key.EMIT_EDGE, f.emitEdge());
        p.put(Key.BLOOM, f.bloom());
        p.put(Key.BLOOM_BODY, f.bloomBody());
        p.put(Key.OPACITY, f.opacity());
        p.put(Key.CORE_WHITE, f.coreWhite());
        p.put(Key.BAND_SOFT, 0.04f);
        return p;
    }

    public AuraBodyParams copy() {
        AuraBodyParams c = new AuraBodyParams();
        System.arraycopy(v, 0, c.v, 0, N);
        c.powerScale = powerScale;
        return c;
    }

    public float get(Key k) { return v[k.ordinal()]; }

    public float powerScale() { return powerScale; }

    /** Valor crudo, sin escala por poder (uso interno y de {@link #from}). */
    public void put(Key k, float value) { v[k.ordinal()] = value; }

    /** Valor ABSOLUTO de familia o de JSON: si la clave es dimensional se escala por poder. */
    public void set(Key k, float value) { v[k.ordinal()] = k.dimensional ? value * powerScale : value; }

    public void mul(Key k, float factor) { v[k.ordinal()] *= factor; }

    public void add(Key k, float delta) { v[k.ordinal()] += delta; }

    public void clamp(Key k, float lo, float hi) { v[k.ordinal()] = Mth.clamp(v[k.ordinal()], lo, hi); }
}
