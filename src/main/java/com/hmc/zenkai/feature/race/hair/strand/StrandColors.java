package com.hmc.zenkai.feature.race.hair.strand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Colores propios de los mechones (DMZ 2.2: color del mechón "c", de la punta "tc" y de cada
 * segmento "so[].c"; DMZ 2.1: solo "c"). Petición del usuario (2026-10-12): un peinado blanco con
 * dos mechones negros debe verse así, y poder volver a "un solo color".
 *
 * Reglas del FORMATO (leídas del código publicado de DMZ 2.2, implementación propia):
 *  · color de un segmento = el suyo si lo trae; si no, del color del mechón (o el de pelo si no
 *    trae) al de la punta (o el mismo), según la posición del segmento en el mechón (0 raíz, 1 punta);
 *  · una forma que impone color de pelo (SSJ…) pinta el peinado entero de ese color.
 *
 * Cómo llega al render: cada segmento recibe una "receta" (de qué color a qué color y en qué punto)
 * y cada receta distinta es una COLUMNA de la textura-rampa (HairRampTextures, 64 columnas); el
 * .geo.json apunta la U de cada cubo a su columna. La columna 0 es siempre "el color de pelo". Así
 * el modelo no depende del color del jugador ni de la forma: solo la textura cambia.
 */
public final class StrandColors {
    private StrandColors() {}

    /** Columnas de la textura (su ancho). Si un peinado tuviera más recetas, las sobrantes van a la 0. */
    public static final int MAX_COLUMNS = 64;
    /** Posición en el mechón cuantizada a octavos: un degradado raíz-punta no necesita más. */
    private static final int PARAM_STEPS = 8;
    /** "El color de pelo" (el del jugador o el de la forma). */
    public static final int HAIR = -1;

    /** De {@code from} a {@code to} en el punto {@code step}/{@link #PARAM_STEPS}; HAIR = color de pelo. */
    public record Recipe(int from, int to, int step) {
        static final Recipe PLAIN = new Recipe(HAIR, HAIR, 0);

        /** Color real con el color de pelo dado. */
        public int resolve(int hairRgb) {
            int a = from == HAIR ? hairRgb : from, b = to == HAIR ? hairRgb : to;
            return step == 0 || a == b ? a & 0xFFFFFF : HairRampColors.mix(a, b, step / (float) PARAM_STEPS);
        }
    }

    /** Recetas de un peinado y la columna de cada segmento (en el orden de los mechones). */
    public record Palette(List<Recipe> recipes, List<int[]> columns) {
        public boolean plain() {
            return recipes.size() <= 1;
        }

        public int column(int strand, int segment) {
            if (strand >= columns.size()) return 0;
            int[] c = columns.get(strand);
            return segment < c.length ? c[segment] : 0;
        }

        /** Color de cada columna con este color de pelo y este peso de los colores propios (0 = ninguno). */
        public int[] resolve(int hairRgb, float weight) {
            int[] out = new int[recipes.size()];
            for (int i = 0; i < out.length; i++) {
                int own = recipes.get(i).resolve(hairRgb);
                out[i] = weight >= 1f ? own : HairRampColors.mix(hairRgb, own, weight);
            }
            return out;
        }
    }

    public static Palette of(StrandSet set) {
        Map<Recipe, Integer> index = new LinkedHashMap<>();
        index.put(Recipe.PLAIN, 0);
        List<int[]> columns = new ArrayList<>(set.strands().size());
        for (Strand s : set.strands()) {
            int n = s.segments().size();
            int[] col = new int[n];
            for (int k = 0; k < n; k++) {
                Recipe r = recipe(s, k, n);
                Integer c = index.get(r);
                if (c == null) {
                    c = index.size() < MAX_COLUMNS ? index.size() : 0;
                    if (c != 0) index.put(r, c);
                }
                col[k] = c;
            }
            columns.add(col);
        }
        return new Palette(List.copyOf(index.keySet()), columns);
    }

    static Recipe recipe(Strand s, int k, int n) {
        int own = s.segments().get(k).rgb();
        if (own >= 0) return new Recipe(own, own, 0);
        int base = s.rgb() >= 0 ? s.rgb() : HAIR;
        int tip = s.tipRgb() >= 0 ? s.tipRgb() : base;
        if (tip == base) return base == HAIR ? Recipe.PLAIN : new Recipe(base, base, 0);
        int step = n > 1 ? Math.round(k / (float) (n - 1) * PARAM_STEPS) : 0;
        if (step == 0) return base == HAIR ? Recipe.PLAIN : new Recipe(base, base, 0);
        if (step == PARAM_STEPS) return tip == HAIR ? Recipe.PLAIN : new Recipe(tip, tip, 0);
        return new Recipe(base, tip, step);
    }

    /** ¿Algún mechón de alguna variante trae color propio? (para enseñar el interruptor). */
    public static boolean hasColors(StrandSet.Variants v) {
        for (StrandSet s : new StrandSet[]{v.base(), v.ssj(), v.ssj2(), v.ssj3(), v.ssj4()}) {
            if (s == null) continue;
            for (Strand st : s.strands()) if (st.hasColors()) return true;
        }
        return false;
    }
}
