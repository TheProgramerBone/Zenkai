package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentConfig;
import com.hmc.zenkai.client.aura.compose.component.ElectricityComponent;
import com.hmc.zenkai.client.aura.compose.component.FlameSheetComponent;
import com.hmc.zenkai.client.aura.compose.component.MoteComponent;
import com.hmc.zenkai.client.aura.compose.component.ShellComponent;
import com.hmc.zenkai.client.aura.compose.component.StrandComponent;
import com.hmc.zenkai.client.aura.compose.component.TintSpec;
import com.hmc.zenkai.client.aura.compose.component.WispComponent;
import com.hmc.zenkai.client.aura.flame.AuraFlameTuning;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro de FAMILIAS de aura. Cada una dice qué PROGRAMA dibuja el cuerpo, cómo se moldea sobre la
 * llama calibrada y qué componentes lleva por defecto. Referencias de cada una en
 * .claude/imagenes_videos_referencias/aura vfx/ (ver también .claude/docs/auras.md).
 * <p>
 * Crear una familia nueva: una entrada aquí (y, si su geometría no la da ninguno de los programas
 * existentes, un {@link AuraBodyKind} con su shader). Crear una VARIANTE de una familia existente
 * no necesita Java: un JSON en assets/zenkai/zenkai_aura_styles/.
 */
public final class AuraFamilies {

    private AuraFamilies() {}

    private static final Map<String, AuraFamily> FAMILIES = new LinkedHashMap<>();

    /** Blanco azulado de la bioelectricidad canónica (SSJ2/SSJ3). */
    private static final TintSpec ELECTRIC_BLUE = TintSpec.parse("#BFEFFF");

    /**
     * FLAME — la llama de vela confirmada en juego el 2026-09-30 (SSJ, golden, arcosiano y cualquier forma
     * que no tenga estilo). Sin moldeado: sus números son los de AuraFlameTuning tal cual. Capa
     * interior solo con dos tonos, con los mismos factores que la llama sola (inner_scale,
     * inner_amp, inner_alpha). Chispas que salen disparadas hacia arriba (aura_11).
     */
    public static final AuraFamily FLAME = register(new AuraFamily("flame", AuraBodyKind.FLAME,
            new AuraInnerSpec(AuraInnerSpec.Mode.TWO_TONE, null,
                    AuraFlameTuning.get(AuraFlameTuning.Param.INNER_SCALE),
                    AuraFlameTuning.get(AuraFlameTuning.Param.INNER_AMP),
                    AuraFlameTuning.get(AuraFlameTuning.Param.INNER_ALPHA), 0.5f, 0.4f),
            AuraFamilies::tallTeardrop,
            List.of(strands(48), sparks()),
            "dust", "burst", AuraTrailSpec.FLAME));

    /**
     * CANDLE — la llama de VELA de agujas (2026-09-30 a 2026-10-26), la que fue FLAME hasta que el
     * usuario aprobó la lágrima alta (2026-10-26: "ponla en todas las formas, también SSJ2 y Rosé").
     * Se conserva para comparar ({@code /zenkaiaura family candle}); ningún estilo la usa.
     */
    public static final AuraFamily CANDLE = register(new AuraFamily("candle", AuraBodyKind.FLAME,
            FLAME.inner(), AuraFamilies::needleCandle, List.of(strands(48), sparks()),
            "dust", "burst", AuraTrailSpec.FLAME));

    /**
     * DIVINE — energía divina y CONTROLADA (SSG aura_7, SSB, formas *_god). Pocos lóbulos redondos
     * que se mecen, abultamientos lentos que suben, lenguas romas arriba; interior de nube (aura_2)
     * SIEMPRE, también con un solo tono. Ascuas que suben despacio. Nada de púas ni temblor rápido.
     */
    public static final AuraFamily DIVINE = register(new AuraFamily("divine", AuraBodyKind.SOFT,
            new AuraInnerSpec(AuraInnerSpec.Mode.ALWAYS, AuraBodyKind.SOFT, 0.72f, 0.5f, 3.0f, 0.4f, 0.3f),
            b -> {
                b.set(Key.PEAKS, 7f);
                b.set(Key.SHARP, 0f);
                b.mul(Key.FREQ_Y, 0.55f);
                b.mul(Key.SPEED, 0.6f);
                b.set(Key.DETAIL, 0.5f);
                b.mul(Key.RADIUS, 1.05f);
                // Amplitud contenida y lóbulos de los flancos más hacia arriba: con 1.25 y
                // side_up_dir 0.55 un lóbulo se abría de lado como una bandera de bloque y medio
                // (vídeo del usuario, 2026-09-30). Divina = CONTROLADA.
                b.mul(Key.AMP, 0.95f);
                b.set(Key.TOP_BOOST, 0.4f);
                b.set(Key.TIP, 0.55f);
                b.mul(Key.TAPER, 0.55f);
                b.set(Key.UP_DIR, 0.85f);
                b.set(Key.SIDE_UP_DIR, 0.75f);
                b.set(Key.SWAY, 0.1f);
                b.set(Key.BREATHE, 0.025f);
                b.set(Key.TONGUE_SHARP, 0.35f);
                b.set(Key.CLOUD, 0.55f);
                b.set(Key.BAND_SOFT, 0.045f);
                b.set(Key.CEL_RIM, 0.78f);
                b.set(Key.CEL_LIGHT, 0.5f);
                b.mul(Key.ALPHA_CENTER, 1.3f);
                b.mul(Key.EMIT_EDGE, 0.9f);
            },
            // El contorno de cajas (componente rim) se cambió por el humo sobre la silueta real
            // (bloque "silhouette" de cada estilo, SilhouetteFlameRenderer), 2026-10-26.
            List.of(embers()),
            "clean", "radiant",
            new AuraTrailSpec(0.75f, 0.1f, 0.5f, 0.5f, 0.7f, false, 16)));

    /**
     * DIVINE_FLAME — el fuego divino de SSG y las formas *_god (rework 2026-10-26,
     * .claude/archivo/2026-11-02-auras-cerradas/aura-divinas-plan.md). En los fotogramas (ReferenciaAuraGokuGod2,
     * VideoSinRecortarGokuGOD2) no hay volumen con contorno: es una PARED de láminas verticales de llama
     * con núcleos casi blancos y sombras rojas entre las lenguas, mucho más alta que la cabeza. Lo dan las
     * láminas ({@code sheets}); la lágrima de FLAME sigue en la familia pero con opacidad 0 (se leía como
     * planos rosas, v13). Sin agujas: competían con las láminas.
     */
    public static final AuraFamily DIVINE_FLAME = register(new AuraFamily("divine_flame", AuraBodyKind.FLAME,
            FLAME.inner(),
            b -> {
                tallTeardrop(b);
                b.mul(Key.RADIUS, 1.1f);
                // APAGADA: incluso al 25 % su cáscara roja translúcida se leía ROSA sobre el cielo y,
                // volando hacia la cámara, eran los planos rosas de borde recto que llenaban la pantalla
                // (medido apagándola, v13, 2026-10-27); en la referencia no hay rosa. De lejos la silueta
                // la dan unas pocas láminas (FlameSheetComponent, LOD lejano).
                b.mul(Key.OPACITY, 0.0f);
                // Más baja que la lágrima de SSJ: su punta asomaba como una aguja por encima de las
                // láminas (v9); en la referencia lo más alto son las lenguas.
                b.mul(Key.HALF_HEIGHT, 0.55f);
                // Ritmo lento: fuego en calma, plasma (pedido del usuario, 2026-10-27).
                b.mul(Key.SPEED, 0.45f);
                b.mul(Key.EMIT_EDGE, 0.6f);
            },
            List.of(sheets(20), divineEmbers(MoteComponent.Tint.OUTER, 11f), divineEmbers(MoteComponent.Tint.INNER, 7f)),
            "clean", "radiant",
            // Estela más estrecha, ondulante y de núcleo suave: la de antes era un tubo amarillo recto y
            // rígido en el dash (pedido del usuario, 2026-10-27); la cola de cometa la dan las láminas.
            new AuraTrailSpec(0.55f, 0.3f, 0.55f, 0.4f, 0.5f, false, 18)));

    /**
     * DIVINE_RADIANCE — el aura de Super Saiyan Blue (rework 2026-10-27, fase F3 de
     * .claude/archivo/2026-11-02-auras-cerradas/aura-divinas-plan.md; referencia {@code aura vfx/ReferenciaGeminiAuraGokuSSJBlue.jpg}).
     * Una lágrima COMPACTA y alta de lenguas finas que ondulan pegadas al cuerpo: cian casi blanco junto al
     * personaje y azul marino en el borde y en los valles. Distinta de SSG a propósito: más estrecha, de
     * vida más larga (llama fría y serena), con el cuerpo de DIVINE como brillo tenue de fondo, cubitos de
     * píxel cian y blancos flotando alrededor y rocas que levitan sin polvo.
     */
    public static final AuraFamily DIVINE_RADIANCE = register(new AuraFamily("divine_radiance", AuraBodyKind.SOFT,
            DIVINE.inner(),
            b -> {
                DIVINE.shape().accept(b);
                // Brillo de fondo: las láminas son lo que se ve, el volumen solo rellena junto al cuerpo.
                b.mul(Key.OPACITY, 0.55f);
                b.mul(Key.RADIUS, 0.9f);
                // Más bajo: su punta asomaba como dos cuernos translúcidos sobre la cabeza (como en SSG).
                b.mul(Key.HALF_HEIGHT, 0.6f);
            },
            List.of(radianceSheets(16), cubes(11f, MoteComponent.Tint.HOT), cubes(6f, MoteComponent.Tint.WHITE),
                    glints()),
            "levitate", "radiant",
            new AuraTrailSpec(0.55f, 0.3f, 0.55f, 0.4f, 0.5f, false, 18)));

    /**
     * DARK_DENSE — energía oscura y PESADA (aura_5, Ultra Ego, majin malvado). Masa ancha y
     * redondeada que se retuerce, casi sin estrecharse arriba, interior profundo con vetas y borde
     * fino; respira despacio. Motas grandes que se alejan y caen, sin bloom.
     */
    public static final AuraFamily DARK_DENSE = register(new AuraFamily("dark_dense", AuraBodyKind.DENSE,
            new AuraInnerSpec(AuraInnerSpec.Mode.TWO_TONE, AuraBodyKind.DENSE, 0.7f, 0.8f, 1.3f, 0.5f, 0.5f),
            b -> {
                b.set(Key.PEAKS, 4f);
                b.mul(Key.RADIUS, 1.1f);
                b.mul(Key.HALF_HEIGHT, 0.82f);
                b.mul(Key.LOWER_HALF, 0.9f);
                b.set(Key.AMP, 0.38f);
                b.set(Key.TIP, 0.25f);
                b.set(Key.TAPER, 0.12f);
                b.set(Key.SPEED, 0.55f);
                b.set(Key.DETAIL, 0.7f);
                b.set(Key.UP_DIR, 0.25f);
                b.set(Key.BREATHE, 0.03f);
                b.set(Key.CLOUD, 0.8f);
                b.set(Key.DEPTH, 0.6f);
                b.set(Key.ALPHA_CENTER, 0.3f);
                b.set(Key.ALPHA_EDGE, 0.9f);
                b.set(Key.CEL_RIM, 0.82f);
                b.set(Key.CEL_LIGHT, 0.62f);
                b.set(Key.CEL_BODY, 0.3f);
                b.set(Key.EMIT_BODY, 0f);
                b.set(Key.EMIT_EDGE, 0.3f);
                b.mul(Key.BLOOM, 0.7f);
                b.set(Key.FRESNEL_POWER, 2.4f);
            },
            List.of(drift(),
                    new WispComponent.Config(WispComponent.Behavior.SINK, 2.5f, new float[]{1.2f, 1.8f},
                            new float[]{0.12f, 0.18f}, new float[]{0.5f, 0.8f}, 1.2f, 0.09f, TintSpec.OUTER, 0.6f, 0f)),
            "pressure", "heavy",
            new AuraTrailSpec(0.95f, 0.15f, 0.2f, 0.65f, 0.5f, true, 16)));

    /**
     * ELECTRIC_FLAME — la llama agresiva con ELECTRICIDAD integrada (SSJ2 aura_11, Rosé). Misma vela
     * que flame pero con más púas, más afiladas, corona más activa (menos compresión arriba) y
     * ritmo más rápido. Rayos que salen del cuerpo hasta el borde de la cáscara y chispas más densas;
     * el contorno es el humo sobre la silueta (bloque "silhouette" de ssj2/ssj3).
     */
    public static final AuraFamily ELECTRIC_FLAME = register(new AuraFamily("electric_flame", AuraBodyKind.FLAME,
            FLAME.inner(),
            b -> {
                // Sobre la lágrima alta desde el 2026-10-26 (antes la vela). Sin el crown_soft 0.75 de
                // la vela: volvía a comprimir la corona y aplastaba la punta.
                tallTeardrop(b);
                b.mul(Key.PEAKS, 1.15f);
                b.set(Key.SHARP, 1f);
                b.mul(Key.SPEED, 1.45f);
                b.mul(Key.DETAIL, 1.4f);
                b.mul(Key.STREAK, 1.3f);
                b.mul(Key.AMP, 1.15f);
                b.mul(Key.TIP, 1.2f);
                b.mul(Key.TOP_BOOST, 1.2f);
            },
            List.of(strands(56), sparks(14f),
                    // Rayos BLANCO AZULADOS en cualquier color de aura: la bioelectricidad de SSJ2 y
                    // SSJ3 en el anime es así (con el tono caliente del amarillo salían rayos
                    // amarillos que se perdían dentro de la llama).
                    new ElectricityComponent.Config(7f, 8, 1, new float[]{0.6f, 1.3f}, 0.035f, new float[]{0.08f, 0.2f},
                            ElectricityComponent.Attach.MIXED, ELECTRIC_BLUE, TintSpec.WHITE, 1f, 0.3f, 22f, 1f)),
            "aggressive", "electric_burst",
            new AuraTrailSpec(0.85f, 0.45f, 0.0f, 0.6f, 0.95f, false, 18)));

    /**
     * WILD — energía salvaje y abierta (Beast aura_9/aura_10, legendario). La llama se abre en V:
     * lenguas de los flancos casi horizontales, sin estrechamiento arriba, ancha y muy agitada; por
     * fuera, una SEGUNDA cáscara de alas laterales enormes que late despacio a otra frecuencia. Rayos
     * pegados al cuerpo (del color interior), rocas que levitan alrededor.
     */
    public static final AuraFamily WILD = register(new AuraFamily("wild", AuraBodyKind.FLAME,
            FLAME.inner(),
            b -> {
                // Contenida dos veces tras probarla en juego (2026-09-30: "demasiado grande").
                b.mul(Key.RADIUS, 1.0f);
                b.mul(Key.AMP, 1.05f);
                b.set(Key.SIDE_UP_DIR, 0.2f);
                b.set(Key.UP_DIR, 0.5f);
                b.set(Key.TAPER, 0f);
                b.set(Key.CROWN_KNEE, 1.4f);
                b.set(Key.CROWN_SOFT, 0.85f);
                b.mul(Key.TOP_BOOST, 1.3f);
                b.mul(Key.SPEED, 1.3f);
                b.mul(Key.DETAIL, 1.5f);
                b.set(Key.POLE_CALM, 0.9f);
            },
            // Alas algo romas (sharp 0.6): con la onda triangular pura y amplitud alta las lenguas
            // grandes se leían como placas de cristal facetadas (capturas de noche, 2026-09-30).
            List.of(new ShellComponent.Config(AuraBodyKind.FLAME, 1.05f, 0.8f, 0.35f, TintSpec.OUTER,
                            ShellComponent.Pulse.SINE, 0.9f, 0.06f,
                            Map.of(Key.SIDE_UP_DIR, 0.08f, Key.UP_DIR, 0.4f, Key.TAPER, 0f, Key.PEAKS, 12f, Key.SHARP, 0.6f),
                            Map.of()),
                    new ElectricityComponent.Config(9f, 10, 2, new float[]{0.4f, 0.9f}, 0.03f, new float[]{0.1f, 0.25f},
                            ElectricityComponent.Attach.BODY, TintSpec.INNER, TintSpec.WHITE, 1f, 0.32f, 18f, 1f),
                    sparks(12f)),
            "pressure", "violent",
            new AuraTrailSpec(1.0f, 0.5f, 0.0f, 0.55f, 0.85f, false, 20)));

    /**
     * FLOWING — energía fluida y controlada, CEÑIDA al cuerpo (Ultra Instinct aura_6/aura_12/aura_14).
     * El cuerpo no es una llama: son las cápsulas de la pose real hinchadas, con el flujo subiendo
     * por el contorno, borde plateado y motas de luz. Alrededor, una columna blanda y tenue; wisps
     * finas que trepan enroscándose; destellos en estrella y gotas de luz que suben. Suelo limpio.
     */
    public static final AuraFamily FLOWING = register(new AuraFamily("flowing", AuraBodyKind.FLOW,
            AuraInnerSpec.NONE,
            b -> {
                b.set(Key.INFLATE, 0.7f);
                b.set(Key.AMP, 0.12f);
                b.set(Key.LICKS, 1f);
                b.set(Key.SPEED, 0.9f);
                // Calibrado con capturas automáticas (2026-09-30): con borde 0.85 y bandas estrechas,
                // de día el contorno ceñido casi no se veía y mandaba la columna de fuera.
                b.set(Key.FLECKS, 0.8f);
                b.set(Key.BAND_SOFT, 0.06f);
                b.set(Key.ALPHA_CENTER, 0.12f);
                b.set(Key.ALPHA_EDGE, 0.95f);
                b.set(Key.CEL_RIM, 0.6f);
                b.set(Key.CEL_LIGHT, 0.35f);
                b.set(Key.EMIT_EDGE, 0.7f);
                b.set(Key.FRESNEL_POWER, 1.6f);
                b.set(Key.FRESNEL_DIVIS, 0.8f);
            },
            List.of(new ShellComponent.Config(AuraBodyKind.SOFT, 1.05f, 0.6f, 0.2f, TintSpec.OUTER,
                            ShellComponent.Pulse.SINE, 0.5f, 0.03f,
                            Map.of(Key.CLOUD, 0.9f, Key.SWAY, 0.05f, Key.PEAKS, 6f, Key.TONGUE_SHARP, 0.2f),
                            Map.of(Key.SPEED, 0.6f)),
                    new WispComponent.Config(WispComponent.Behavior.FLOW, 5f, new float[]{0.6f, 1.0f},
                            new float[]{0.03f, 0.05f}, new float[]{0.9f, 1.4f}, 3f, 0.06f, TintSpec.INNER, 0.7f, 0.8f),
                    new MoteComponent.Config(MoteComponent.Behavior.GLINT, MoteComponent.Sprite.GLINT, 6f,
                            new float[]{0.4f, 0.8f}, new float[]{0.08f, 0.13f}, new float[]{0.1f, 0.2f},
                            new float[]{0.3f, 0.8f}, new float[]{0.2f, 1.9f}, MoteComponent.Tint.WHITE,
                            new float[]{1f, 1f, 1f}, 1f, 1f, 1f),
                    new MoteComponent.Config(MoteComponent.Behavior.FLOAT, MoteComponent.Sprite.MOTE, 7f,
                            new float[]{0.8f, 1.4f}, new float[]{0.025f, 0.04f}, new float[]{0.6f, 1.1f},
                            new float[]{0.25f, 0.6f}, new float[]{0.0f, 1.6f}, MoteComponent.Tint.INNER,
                            new float[]{1f, 1f, 1f}, 0.9f, 1f, 0.8f)),
            "clean", "calm_shift",
            new AuraTrailSpec(0.4f, 0.05f, 0.6f, 0.5f, 1.0f, false, 22)));

    /**
     * LLAMA DE AGUJAS (2026-09-30, vídeo del usuario comparado con DragonMine Z): sobre la vela
     * calibrada, la silueta que DMZ y el anime tienen y la nuestra no:
     * <ul>
     *   <li>más ESTRECHA (columna poco más ancha que los hombros, no un huevo de dos cuerpos);</li>
     *   <li>base que empieza algo bajo los pies pero no se cierra en capullo: la mitad de abajo,
     *       algo más corta que en la vela, se funde antes de curvarse (el abanico de los pies lo
     *       ponen las agujas del suelo);</li>
     *   <li>más picos, más LARGOS y de LARGO DISTINTO cada uno (peak_var): una corona de dientes
     *       iguales era lo que más alejaba de la referencia;</li>
     *   <li>casi sin pisos de lenguas (freq_y bajo) y poco peso de las puntas en las bandas: los
     *       pisos, de frente, se leían como filas de triángulos DENTRO del aro ("dientes");</li>
     *   <li>interior algo más lleno y con más estrías: el cuerpo queda dentro de la energía.</li>
     * </ul>
     * La textura de trazos finos la ponen las agujas ({@code strands}), no esta superficie.
     */
    static void needleCandle(AuraBodyParams b) {
        b.mul(Key.RADIUS, 0.9f);
        // Base algo por DEBAJO de los pies (pedido del usuario tras probarla, 2026-09-30: le gustaba
        // más que el aura empezara bajo los pies y terminara arriba). Sin volver al capullo: la
        // mitad de abajo sigue más corta que en la vela y se funde antes de cerrarse.
        b.mul(Key.LOWER_HALF, 0.85f);
        b.set(Key.BASE_FADE_LO, -0.95f);
        b.set(Key.BASE_FADE_HI, -0.6f);
        b.set(Key.PEAKS, 26f);
        b.set(Key.PEAK_VAR, 0.6f);
        b.mul(Key.FREQ_Y, 0.35f);
        b.mul(Key.AMP, 1.25f);
        b.mul(Key.TOP_BOOST, 1.3f);
        b.set(Key.CROWN_KNEE, 1.15f);
        b.set(Key.CROWN_SOFT, 0.65f);
        b.mul(Key.TIP, 1.3f);
        b.mul(Key.STREAK, 1.5f);
        // Relleno moderado: el centro usa el tinte CALENTADO (en amarillo, casi blanco); con x2 era
        // un velo blanco sobre la corona y SSJ2/SSJ3 (capturas del 2026-09-30).
        b.mul(Key.ALPHA_CENTER, 1.3f);
        b.set(Key.PEAK_WEIGHT, 0.15f);
        // Borde de la cáscara FINO y menos emisivo: arriba es liso (la corona comprimida) y, visto
        // de espaldas, la banda ancha y brillante dibujaba un arco blanco continuo sobre la cabeza.
        // Las puntas de la corona las ponen las agujas.
        b.set(Key.CEL_RIM, 0.9f);
        b.mul(Key.EMIT_EDGE, 0.7f);
        b.mul(Key.BLOOM, 0.6f);
    }

    /**
     * LÁGRIMA ALTA (la llama de FLAME desde el 2026-10-26, guiada por aura_1/aura_3/aura_4/aura_11): la
     * vela de agujas cerrada en punta muy por encima de la cabeza en vez de acabar en cúpula a su
     * altura, casi transparente junto al cuerpo y densa y brillante arriba, sin corona comprimida y
     * con la base abierta.
     */
    static void tallTeardrop(AuraBodyParams b) {
        needleCandle(b);
        b.set(Key.TEARDROP, 1f);
        // Proporciones elegidas con capturas contra aura_4 (2026-10-26, variante "lagrima_a"): unas 2,3
        // veces el jugador con poder base y más ancha que la vela. La altura ya la tenía la vela; lo
        // que la escondía era la normal (ver aura_flame_shell.vsh). Con x1.4 salía un géiser.
        b.mul(Key.RADIUS, 1.25f);
        // La punta la da el perfil de lágrima: ni estrechamiento extra ni corona comprimida.
        b.set(Key.TAPER, 0f);
        b.set(Key.CROWN_KNEE, 3f);
        b.set(Key.CROWN_SOFT, 1f);
        b.set(Key.POLE_CALM, 0.3f);
        // Base abierta: sin el anillo encendido bajo los pies.
        b.set(Key.BASE_FADE_LO, -0.75f);
        b.set(Key.BASE_FADE_HI, -0.3f);
        // Transparente junto al cuerpo, densa arriba.
        b.mul(Key.ALPHA_CENTER, 0.5f);
        b.set(Key.TOP_FILL, 1f);
    }

    private static AuraFamily register(AuraFamily f) {
        FAMILIES.put(f.id(), f);
        return f;
    }

    public static @Nullable AuraFamily get(String id) {
        return FAMILIES.get(id.startsWith("zenkai:") ? id.substring("zenkai:".length()) : id);
    }

    public static Set<String> ids() { return FAMILIES.keySet(); }

    // ── Componentes por defecto ──

    private static AuraComponentConfig sparks() { return sparks(10f); }

    private static AuraComponentConfig strands(int count) {
        // Abiertas hacia fuera (lean) y de largo medio: la lágrima de DMZ. Con lean 0.15 y largos
        // de hasta 1.5 la primera prueba (capturas del 2026-09-30) salió un géiser alto y estrecho.
        return new StrandComponent.Config(count, new float[]{0.25f, 0.6f}, new float[]{0.0f, 1.55f},
                new float[]{0.45f, 1.15f}, 0.07f, 0.32f, new float[]{0.25f, 0.55f}, 0.6f,
                // Núcleo del MISMO tinte (aclarado un poco), no la capa caliente: con el amarillo de
                // SSJ la capa caliente ya es casi blanca, y decenas de agujas solapadas más el bloom
                // lavaban SSJ2/SSJ3 a blanco (capturas del 2026-09-30).
                14, new float[]{0.5f, 1.1f}, TintSpec.OUTER, TintSpec.OUTER, 0.8f, 0.45f);
    }

    private static AuraComponentConfig sheets(int count) {
        // Alto 2,4-3,2 bloques a poder base: el shader corta cada lengua entre el 45 y el 95 % del quad,
        // así que la punta queda a ~2,5 bloques, la de la referencia del usuario (GokuGod.jpg,
        // 2026-10-27: ~1,4 veces el jugador). Con 1,7-2,6 la punta no pasaba de la cabeza (v18). Pegadas y anchas, con las de delante tenues (front 0.2) pero presentes: la llama
        // ENVUELVE el cuerpo. El usuario eligió esta versión (v1) frente a otra con las láminas
        // apartadas detrás del jugador, que se leían como columnas sueltas (2026-10-26).
        // Radio algo mayor que en la v19 (la lágrima de la referencia es más ancha), pero el ancho de cada
        // lámina igual: con los dos a la vez (v20-v21) la lágrima salió redonda, una bola (alto/ancho
        // 1,1 frente a ~1,7 en GokuGod.jpg).
        return new FlameSheetComponent.Config(count, new float[]{0.33f, 0.65f}, new float[]{0.8f, 1.2f},
                // Base 0,6 bajo los pies y 0,6 más de alto: la lágrima EMPIEZA bajo el jugador y lo
                // rodea entero en vez de nacer en los pies (pedido del usuario sobre la v22, 2026-10-27).
                new float[]{3.0f, 3.8f}, -0.6f, new float[]{4.0f, 7.0f}, 0.12f, FlameSheetComponent.Mode.FLAME,
                TintSpec.INNER, TintSpec.OUTER, 0.9f, 0.5f, 0.6f, 0.82f, false);
    }

    /** Láminas de Blue: más estrechas y cercanas al cuerpo que las de SSG, y de vida larga (llama serena). */
    private static AuraComponentConfig radianceSheets(int count) {
        return new FlameSheetComponent.Config(count, new float[]{0.28f, 0.5f}, new float[]{0.6f, 0.95f},
                new float[]{2.6f, 3.3f}, -0.4f, new float[]{5.0f, 8.0f}, 0.25f, FlameSheetComponent.Mode.FLAME,
                TintSpec.INNER, TintSpec.OUTER, 0.9f, 0.6f, 0.6f, 0.82f, false);
    }

    /** Cubitos de píxel que flotan alrededor del cuerpo (arte de referencia de Blue). */
    private static AuraComponentConfig cubes(float rate, MoteComponent.Tint tint) {
        return new MoteComponent.Config(MoteComponent.Behavior.FLOAT, MoteComponent.Sprite.CUBE, rate,
                new float[]{1.4f, 2.4f}, new float[]{0.07f, 0.12f}, new float[]{0.25f, 0.55f},
                new float[]{0.45f, 1.0f}, new float[]{0.2f, 2.8f}, tint, new float[]{1f, 1f, 1f},
                1f, 1f, 0.9f);
    }

    private static AuraComponentConfig glints() {
        return new MoteComponent.Config(MoteComponent.Behavior.GLINT, MoteComponent.Sprite.GLINT, 5f,
                new float[]{0.4f, 0.8f}, new float[]{0.07f, 0.11f}, new float[]{0.1f, 0.2f},
                new float[]{0.3f, 0.8f}, new float[]{0.2f, 2.2f}, MoteComponent.Tint.WHITE, new float[]{1f, 1f, 1f},
                1f, 1f, 0.9f);
    }

    private static AuraComponentConfig sparks(float rate) {
        return new MoteComponent.Config(MoteComponent.Behavior.RISE, MoteComponent.Sprite.SPARK, rate,
                new float[]{0.3f, 0.55f}, new float[]{0.05f, 0.08f}, new float[]{2.5f, 4f},
                new float[]{0.25f, 0.7f}, new float[]{0.1f, 1.6f}, MoteComponent.Tint.HOT, new float[]{1f, 1f, 1f},
                1f, 5f, 0.8f);
    }

    private static AuraComponentConfig embers() {
        return new MoteComponent.Config(MoteComponent.Behavior.FLOAT, MoteComponent.Sprite.EMBER, 8f,
                new float[]{1.2f, 2.0f}, new float[]{0.07f, 0.1f}, new float[]{0.5f, 0.9f},
                new float[]{0.35f, 0.9f}, new float[]{0.1f, 1.9f}, MoteComponent.Tint.HOT, new float[]{1f, 1f, 1f},
                1f, 1f, 0.9f);
    }

    /**
     * Brasas divinas de SSG (pedido del usuario, 2026-10-27): destellos carmesí (OUTER) y naranja (INNER)
     * que nacen dentro de la caja del jugador, suben constantes (lo bastante para pasar por encima del aura,
     * como en GokuGod.jpg), derivan de lado y se encogen y
     * apagan en 20-30 ticks. Sustituyen a las ascuas FLOAT y a las chispas RISE en divine_flame.
     */
    private static AuraComponentConfig divineEmbers(MoteComponent.Tint tint, float rate) {
        return new MoteComponent.Config(MoteComponent.Behavior.EMBER, MoteComponent.Sprite.EMBER, rate,
                new float[]{1.2f, 1.5f}, new float[]{0.1f, 0.16f}, new float[]{0.9f, 1.3f},
                new float[]{0.0f, 0.5f}, new float[]{0.4f, 2.2f}, tint, new float[]{1f, 1f, 1f},
                1f, 1f, 0.9f);
    }

    private static AuraComponentConfig drift() {
        return new MoteComponent.Config(MoteComponent.Behavior.DRIFT, MoteComponent.Sprite.MOTE, 5f,
                new float[]{1.6f, 2.6f}, new float[]{0.12f, 0.2f}, new float[]{0.25f, 0.45f},
                new float[]{0.5f, 1.0f}, new float[]{0.2f, 1.7f}, MoteComponent.Tint.OUTER, new float[]{1f, 1f, 1f},
                0.55f, 1f, 0f);
    }
}
