package com.hmc.zenkai.feature.aura;

import java.util.List;

/**
 * Geometría base del aura y el plan que consume el renderer.
 * Las constantes de C_V2 son las validadas en juego: AURA_SCALE 1.30, BASE_ALPHA 0.30 y
 * el reparto de faldones que produce la gota. No se tocan sin volver a medir.
 */
public final class AuraSkirts {
    private AuraSkirts() {}

    /** Escala global del aura respecto al jugador. Validada en juego. */
    public static final float AURA_SCALE = 1.30f;
    /** Opacidad base de un plano antes de cualquier modulación. */
    public static final float BASE_ALPHA = 0.30f;

    /**
     * Conjunto C_v2. El orden ES la identidad: AuraLod referencia los faldones por
     * índice, así que insertar uno en medio recolocaría el conjunto de bandas en silencio.
     * Añadir SIEMPRE al final.
         *   0  faldón bajo, muy abierto (tilt 52) — la falda alrededor de los pies
     *   1  cuerpo medio                        — sostiene la silueta
     *   2  cuerpo alto                         — la parte que se lee a distancia
     *   3  puntas superiores                   — arranca a media altura
     *   4  penacho                             — remate, el más estrecho
     */
    public static final AuraSkirt[] C_V2 = {
            //            count  offset   tilt   baseR  width  height  yStart  jitter  alpha tex
            new AuraSkirt(8,     22.5f,   52.0f, 0.55f, 1.05f, 0.95f, -0.30f, 0.12f, 0.95f, 3),
            new AuraSkirt(8,      0.0f,   26.0f, 0.45f, 1.10f, 1.90f, -0.20f, 0.18f, 1.00f, 0),
            new AuraSkirt(8,     22.5f,   11.0f, 0.38f, 1.15f, 2.60f, -0.10f, 0.22f, 1.00f, 1),
            new AuraSkirt(8,      0.0f,   -8.0f, 0.30f, 1.05f, 2.20f,  0.85f, 0.22f, 0.95f, 1),
            new AuraSkirt(5,     36.0f,  -20.0f, 0.15f, 0.95f, 1.90f,  1.45f, 0.18f, 0.90f, 2),
    };

    /**
     * Fundamentalmente, lo que el renderer necesita, ya resuelto. Un solo objeto a propósito: si el
     * renderer recibiera AuraProfile + AuraState + AuraModifier + la banda, volvería a
     * tener que reconstruir la lógica del sistema para saber qué dibujar.
         * LOS COLORES VIENEN RESUELTOS. AuraColors sigue siendo la única autoridad que
     * decide el tinte, pero quien la consulta es el llamante de plan(), no el
     * renderer: AuraSkirtRenderer solo dibuja. Eso incluye la decisión de si HAY
     * núcleo — en un aura oscura el blanco la volvería gris, así que coreColor llega
     * ya a -1 y el renderer no tiene que saber por qué.
         * @param profile    perfil YA compensado por la banda
     * @param skirts     faldones supervivientes, ya aligerados
     * @param lod        la banda, solo informativa (depuración y HUD de rendimiento)
     * @param innerColor tinte de la capa principal (forma / ki / majin)
     * @param outerColor tinte de la capa envolvente, o -1 si no hay (sin kaioken)
     * @param coreColor  tinte del núcleo, o -1 si este aura no lleva núcleo
     */
    public record Plan(AuraProfile profile, List<AuraSkirt> skirts, AuraLod lod,
                       int innerColor, int outerColor, int coreColor) {

        public boolean hasOuter() { return outerColor >= 0; }
        public boolean hasCore()  { return coreColor >= 0 && profile.core() > 0.01f; }

        /** Quads del cono: masa siempre, núcleo si lo hay, x2 si hay capa envolvente. */
        public int layerCount() { return (hasCore() ? 2 : 1) * (hasOuter() ? 2 : 1); }

        public boolean isEmpty() { return skirts.isEmpty() || !profile.isVisible(); }

        public int quadCost() {
            int t = 0;
            for (AuraSkirt s : skirts) t += s.count();
            return t * layerCount();
        }

        /** Opacidad final de un faldón: base × su peso × la del perfil. Único sitio
         *  donde se combinan las tres, para que el renderer no las mezcle a mano. */
        public float alphaOf(AuraSkirt s) {
            return BASE_ALPHA * s.alpha() * profile.alpha();
        }
    }

    /**
     * Construye el plan. Es el único punto de entrada del renderer al sistema de aura.
     */
    public static final Plan EMPTY =
            new Plan(AuraProfile.OFF, List.of(), AuraLod.SIGNATURE, 0xFFFFFF, -1, -1);

    /**
     * Construye el plan. Es el único punto de entrada del renderer al sistema de aura.
         * @param profile      perfil sin compensar (de AuraManager.profileOf)
     * @param distanceSq   distancia al cuadrado del jugador a la cámara
     * @param qualityFloor banda mínima según la calidad configurada; null = sin límite
     * @param innerRgb     tinte principal, de AuraColors.resolveLayers().inner()
     * @param outerRgb     tinte envolvente, o -1 si Layers no tiene capa exterior
     */
    public static Plan plan(AuraProfile profile, double distanceSq, AuraLod qualityFloor,
                            int innerRgb, int outerRgb) {
        if (profile == null || !profile.isVisible()) return EMPTY;

        AuraLod lod = AuraLod.effective(distanceSq, qualityFloor);
        AuraProfile compensated = lod.compensate(profile);

        // El núcleo blanco solo tiene sentido sobre un tinte con luz suficiente: en un
        // aura oscura o morada lo único que hace es lavarla a gris.
        int core = AuraTuning.luminance(innerRgb) >= AuraTuning.CORE_MIN_LUM
                ? 0xFFFFFF : -1;

        return new Plan(compensated, lod.skirts(C_V2), lod, innerRgb, outerRgb, core);
    }
}