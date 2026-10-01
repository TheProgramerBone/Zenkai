package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Sube los uniforms de CUERPO a cualquiera de los cuatro programas (aura_flame_shell,
 * aura_body_soft, aura_body_dense, aura_body_flow). Comparten nombres (ZenkaiShell*) para las
 * magnitudes comunes; los propios de cada familia van en ZenkaiBody*, ZenkaiFlow* y ZenkaiSeg*.
 * Un uniform que el programa no declara se salta.
 * <p>
 * CACHÉ: {@code safeGetUniform(String)} es una búsqueda en un mapa por nombre; con ~35 uniforms por
 * draw, varios draws por jugador y dos pasadas (mundo y bloom) eran cientos de búsquedas por frame.
 * Se resuelven una vez por ShaderInstance (identidad: al recargar recursos el programa es otro
 * objeto y se vuelve a resolver).
 * <p>
 * Llamar DENTRO de la tarea diferida y justo antes del draw: el ShaderInstance es único y sus
 * uniforms valen para el siguiente draw.
 */
public final class AuraBodyUniforms {

    private AuraBodyUniforms() {}

    private enum U {
        MODEL("ZenkaiShellModelMat"), BLOOM_MODE("ZenkaiBloomMode"), EMIT("ZenkaiShellEmit"),
        BLOOM("ZenkaiShellBloom"), CENTER("ZenkaiShellCenter"), SIZE("ZenkaiShellSize"), AMP("ZenkaiShellAmp"),
        TOP_BOOST("ZenkaiShellTopBoost"), PEAKS("ZenkaiShellPeaks"), SHARP("ZenkaiShellSharp"),
        FREQ_Y("ZenkaiShellFreqY"), SPEED("ZenkaiShellSpeed"), DETAIL("ZenkaiShellDetail"),
        STREAK("ZenkaiShellStreak"), UP_DIR("ZenkaiShellUpDir"), GROWTH("ZenkaiShellGrowth"),
        TAPER("ZenkaiShellTaper"), TIP("ZenkaiShellTip"), POLE_CALM("ZenkaiShellPoleCalm"),
        LOWER_HALF("ZenkaiShellLowerHalf"), SIDE_UP_DIR("ZenkaiShellSideUpDir"), CROWN("ZenkaiShellCrown"),
        CEL_SHAPE("ZenkaiShellCelShape"), BASE_FADE("ZenkaiShellBaseFade"), CEL("ZenkaiShellCel"),
        ALPHA("ZenkaiShellAlpha"), FRESNEL("ZenkaiShellFresnel"), CORE("ZenkaiShellCoreColor"),
        OUTER("ZenkaiShellOuterColor"), SHAPE("ZenkaiBodyShape"), LOOK("ZenkaiBodyLook"),
        FLOW_SHAPE("ZenkaiFlowShape"), PEAK_VAR("ZenkaiShellPeakVar"), ORTHO("ZenkaiOrtho"), FLOW_LOOK("ZenkaiFlowLook"),
        S0A("ZenkaiSeg0A"), S0B("ZenkaiSeg0B"), S1A("ZenkaiSeg1A"), S1B("ZenkaiSeg1B"),
        S2A("ZenkaiSeg2A"), S2B("ZenkaiSeg2B"), S3A("ZenkaiSeg3A"), S3B("ZenkaiSeg3B"),
        S4A("ZenkaiSeg4A"), S4B("ZenkaiSeg4B"), S5A("ZenkaiSeg5A"), S5B("ZenkaiSeg5B");

        final String name;

        U(String name) { this.name = name; }
    }

    private static final U[] ALL = U.values();
    private static final Map<ShaderInstance, Uniform[]> CACHE = new IdentityHashMap<>();

    private static Uniform[] uniforms(ShaderInstance s) {
        return CACHE.computeIfAbsent(s, sh -> {
            Uniform[] out = new Uniform[ALL.length];
            for (U u : ALL) out[u.ordinal()] = sh.getUniform(u.name);
            return out;
        });
    }

    private static @Nullable Uniform u(Uniform[] cache, U which) { return cache[which.ordinal()]; }

    private static void set(Uniform[] c, U w, float a) { Uniform x = u(c, w); if (x != null) x.set(a); }

    private static void set(Uniform[] c, U w, float a, float b) { Uniform x = u(c, w); if (x != null) x.set(a, b); }

    private static void set(Uniform[] c, U w, float a, float b, float d) { Uniform x = u(c, w); if (x != null) x.set(a, b, d); }

    private static void set(Uniform[] c, U w, float a, float b, float d, float e) {
        Uniform x = u(c, w);
        if (x != null) x.set(a, b, d, e);
    }

    /** Marca (o desmarca) la pre-pasada de profundidad de FLOW: el fragment no descarta nada. */
    public static void setDepthPass(ShaderInstance s, AuraBodyParams p, boolean depthPass) {
        set(uniforms(s), U.FLOW_LOOK, p.get(Key.FLECKS), p.get(Key.RIM_ONLY), p.get(Key.BAND_SOFT), depthPass ? 1f : 0f);
    }

    /**
     * @param segs  solo para FLOW: 6 segmentos × 8 floats (A xyz relativo a cámara, radio, B xyz, 0);
     *              null para los cuerpos de esfera.
     * @param camY  Y de la cámara en mundo (el flujo de FLOW se muestrea con la altura de mundo).
     */
    public static void apply(ShaderInstance s, Matrix4f model, AuraBodyParams p, float growth,
                             float[] core, float[] edge, boolean bloomPass, float @Nullable [] segs, float camY) {
        apply(s, model, p, growth, core, edge, bloomPass, segs, camY, false);
    }

    /** @param ortho true en la GUI (vista previa): proyección ortográfica, ver los .fsh. */
    public static void apply(ShaderInstance s, Matrix4f model, AuraBodyParams p, float growth,
                             float[] core, float[] edge, boolean bloomPass, float @Nullable [] segs, float camY,
                             boolean ortho) {
        Uniform[] c = uniforms(s);
        // Siempre se escribe (también a 0): el uniform vive en el ShaderInstance y una vista
        // previa anterior lo dejaría encendido para el mundo.
        set(c, U.ORTHO, ortho ? 1f : 0f);
        Uniform m = u(c, U.MODEL);
        if (m != null) m.set(model);
        set(c, U.BLOOM_MODE, bloomPass ? 1f : 0f);
        set(c, U.EMIT, p.get(Key.EMIT_BODY), p.get(Key.EMIT_EDGE));
        set(c, U.BLOOM, p.get(Key.BLOOM), p.get(Key.BLOOM_BODY));
        set(c, U.CENTER, 0f, p.get(Key.CENTER_Y), 0f);
        set(c, U.SIZE, p.get(Key.RADIUS), p.get(Key.HALF_HEIGHT), p.get(Key.RADIUS));
        set(c, U.AMP, p.get(Key.AMP));
        set(c, U.TOP_BOOST, p.get(Key.TOP_BOOST));
        set(c, U.PEAKS, p.get(Key.PEAKS));
        set(c, U.SHARP, p.get(Key.SHARP));
        set(c, U.FREQ_Y, p.get(Key.FREQ_Y));
        set(c, U.SPEED, p.get(Key.SPEED));
        set(c, U.DETAIL, p.get(Key.DETAIL));
        set(c, U.STREAK, p.get(Key.STREAK));
        set(c, U.UP_DIR, p.get(Key.UP_DIR));
        set(c, U.GROWTH, growth);
        set(c, U.TAPER, p.get(Key.TAPER));
        set(c, U.TIP, p.get(Key.TIP));
        set(c, U.POLE_CALM, p.get(Key.POLE_CALM));
        set(c, U.PEAK_VAR, p.get(Key.PEAK_VAR));
        set(c, U.LOWER_HALF, p.get(Key.LOWER_HALF));
        set(c, U.SIDE_UP_DIR, p.get(Key.SIDE_UP_DIR));
        set(c, U.CROWN, p.get(Key.CROWN_KNEE), p.get(Key.CROWN_SOFT));
        set(c, U.CEL_SHAPE, p.get(Key.PEAK_WEIGHT), p.get(Key.BOTTOM_RIM));
        set(c, U.BASE_FADE, p.get(Key.BASE_FADE_LO), p.get(Key.BASE_FADE_HI));
        set(c, U.CEL, p.get(Key.CEL), p.get(Key.CEL_RIM), p.get(Key.CEL_LIGHT), p.get(Key.CEL_BODY));
        set(c, U.ALPHA, p.get(Key.ALPHA_CENTER), p.get(Key.ALPHA_EDGE));
        set(c, U.FRESNEL, p.get(Key.FRESNEL_POWER), p.get(Key.FRESNEL_DIVIS));
        set(c, U.CORE, core[0], core[1], core[2], 1f);
        set(c, U.OUTER, edge[0], edge[1], edge[2], 1f);
        set(c, U.SHAPE, p.get(Key.SWAY), p.get(Key.BREATHE), p.get(Key.TONGUE_SHARP), 0f);
        set(c, U.LOOK, p.get(Key.CLOUD), p.get(Key.DEPTH), p.get(Key.BAND_SOFT), 0f);
        set(c, U.FLOW_SHAPE, p.get(Key.INFLATE), p.get(Key.LICKS), camY, 0f);
        set(c, U.FLOW_LOOK, p.get(Key.FLECKS), p.get(Key.RIM_ONLY), p.get(Key.BAND_SOFT), 0f);
        if (segs != null) {
            for (int i = 0; i < AuraAnchor.SEGMENTS; i++) {
                int o = i * 8;
                set(c, ALL[U.S0A.ordinal() + 2 * i], segs[o], segs[o + 1], segs[o + 2], segs[o + 3]);
                set(c, ALL[U.S0B.ordinal() + 2 * i], segs[o + 4], segs[o + 5], segs[o + 6], 0f);
            }
        }
    }
}
