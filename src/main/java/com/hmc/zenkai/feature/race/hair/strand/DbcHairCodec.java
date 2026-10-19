package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lector de la cadena de pelo de Dragon Block C / JRMCore (la que copia y pega el HairSalon de
 * JinRyuu): G2 de 786 dígitos y la antigua G1 de 392.
 *
 * Escrito desde cero a partir del FORMATO (JRMCore 1.3.51 para la cadena y npcdbc 1.2.1 para las
 * tablas de anclas, leídos en 2026-10-12). No se copia código ni se empaqueta ningún código de
 * DBC: la licencia de JinRyuu lo prohíbe (ver .claude/pendiente/pelo-importado-dmz-dbc.md §4).
 *
 * G2: 56 mechones x 7 campos de 2 dígitos (l X Y Z B P T) + "20". l = largo (0 = ausente), X/Y/Z =
 * rotación (v-50)·0,1 rad acotada a 18..82, B = curvatura, P = reparto de la curvatura entre los
 * segmentos 1 y 3, T = cuánto reacciona al SSJ (0..~1 tras acotar a 18..82). DBC no guarda variantes SSJ:
 * deforma el peinado base en vivo según el progreso de la transformación. Aquí se HORNEA esa
 * deformación con el progreso al máximo ({@link #deform}) para obtener SSJ1 y SSJ2 aproximados;
 * SSJ3 y SSJ4 de DBC son modelos fijos que no se importan (caen a la cadena de {@link HairFormVariants}).
 * Ranuras: 0-3 frente, 4-13 derecha, 14-23 izquierda, 24-39 espalda, 40-55 arriba.
 *
 * Espacio: DBC dibuja con ModelRenderer de 1.7.10 (Y hacia abajo, glRotate Z·Y·X). Al pasar al
 * espacio de render de GeckoLib (Strand) se aplica F = diag(-1,-1,1), que es una rotación de 180°
 * en Z: anclas y desplazamientos cambian de signo en X e Y, y las rotaciones en X e Y también; la
 * de Z se queda. Es el ÚNICO punto del importador donde se cambian ejes.
 */
public final class DbcHairCodec {
    private DbcHairCodec() {}

    public static final int G2_LENGTH = 786;
    public static final int G1_LENGTH = 392;
    private static final int STRANDS = 56;

    private static final int[] RIGHT_Z = {3, 2, 1, 0, 3, 2, 1, 3, 2, 3};
    private static final int[] RIGHT_Y = {0, 0, 0, 0, 1, 1, 1, 2, 2, 3};
    private static final int[] LEFT_Z = {0, 1, 2, 3, 1, 2, 3, 2, 3, 3};
    private static final int[] LEFT_Y = {0, 0, 0, 0, 1, 1, 1, 2, 2, 3};
    private static final int[] GRID_A = {0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3};
    private static final int[] GRID_B = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3};

    /** Progreso máximo de la transformación en DBC: stateChange (SSJ) y state2Change (SSJ2) llegan a 200. */
    private static final int FULL_PROGRESS = 200;

    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    public static boolean looksLike(String code) {
        String c = DmzHairCodec.stripWhitespace(code);
        if (c.length() != G2_LENGTH && c.length() != G1_LENGTH) return false;
        for (int i = 0; i < c.length(); i++) if (!Character.isDigit(c.charAt(i))) return false;
        return true;
    }

    /** Un mechón tal como viene en la cadena (campos ya acotados; la rotación en radianes). */
    record Raw(int face, int l, float x, float y, float z, float b, float p, int t) {}

    /** Solo el peinado base (lo que usan las pruebas y el importador sin variantes). */
    public static StrandSet decode(String raw, Consumer<Component> warn) throws HairCodeException {
        return decodeVariants(raw, warn).base();
    }

    /**
     * Base + SSJ1 + SSJ2 aproximados. Las variantes solo existen si algún mechón reacciona al SSJ
     * (T por encima del mínimo, 18); sin reacción (p. ej. un G1 antiguo, que rellena T=00) valen los
     * defaults de Zenkai para esas formas, como pidió el usuario (2026-10-18).
     */
    public static StrandSet.Variants decodeVariants(String raw, Consumer<Component> warn) throws HairCodeException {
        String code = DmzHairCodec.stripWhitespace(raw);
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') throw new HairCodeException("dbc_digits");
        }
        if (code.length() == G1_LENGTH) {
            code = g1ToG2(code);
        } else if (code.length() != G2_LENGTH) {
            throw new HairCodeException("dbc_length", G2_LENGTH, G1_LENGTH, code.length());
        }

        List<Raw> raws = new ArrayList<>();
        boolean usesSsjReaction = false;
        for (int face = 0; face < STRANDS; face++) {
            int at = face * 14;
            int l = two(code, at);
            if (l == 0) continue;
            int t = clampField(two(code, at + 12));
            usesSsjReaction |= t > 18;
            raws.add(new Raw(face, l, rot(two(code, at + 2)), rot(two(code, at + 4)), rot(two(code, at + 6)),
                    rot(two(code, at + 8)), rot(two(code, at + 10)), t));
        }
        StrandSet base = build(raws, 0, 0);
        if (!usesSsjReaction) return StrandSet.Variants.single(base);
        warn.accept(Component.translatable("zenkai.hair_code.warn.dbc_ssj"));
        return new StrandSet.Variants(base, build(raws, FULL_PROGRESS, 0), build(raws, FULL_PROGRESS, FULL_PROGRESS),
                null, null);
    }

    /** JRMCore: l de 1 dígito pasa a l·11-10, se conservan X/Y/Z y se rellena B=50 P=50 T=00. */
    static String g1ToG2(String g1) {
        StringBuilder sb = new StringBuilder(G2_LENGTH);
        for (int i = 0; i < STRANDS; i++) {
            int at = i * 7;
            int l = Math.max(0, (g1.charAt(at) - '0') * 11 - 10);
            sb.append(pad(l)).append(pad(two(g1, at + 1))).append(pad(two(g1, at + 3))).append(pad(two(g1, at + 5)))
                    .append("505000");
        }
        return sb.append("20").toString();
    }

    private static StrandSet build(List<Raw> raws, int stateChange, int state2Change) {
        List<Strand> out = new ArrayList<>(raws.size());
        for (Raw r : raws) {
            float[] v = deform(r, stateChange, state2Change);
            out.add(strand(r.face(), (int) v[0], v[1], v[2], v[3], v[4], r.p()));
        }
        return new StrandSet(null, out);
    }

    /**
     * Deformación SSJ de un mechón (DBC la calcula al dibujar; aquí se hornea). `change` es el progreso
     * del SSJ (0..200) y `change2` el del SSJ2. La intensidad del mechón sale de su campo T:
     * Int = (int)((T-18)·1,62)·0,01. Devuelve {l, x, y, z, b}.
     *
     * Reglas (comportamiento observado en la reimplementación de npcdbc 1.2.1; las fórmulas son esas, el
     * código es propio): el SSJ abre los mechones hacia fuera (más Z, y más X en los que apuntan quietos)
     * y los endereza (b hacia 0); el SSJ2 además los alarga y rectifica. Los acotados (±3,2, ±2,8,
     * ±2,2, ±0,4, ±0,2) evitan que el mechón dé la vuelta.
     */
    static float[] deform(Raw r, int change, int change2) {
        float x = r.x(), y = r.y(), z = r.z(), b = r.b();
        int l = r.l();
        int face = r.face();
        float intensity = (int) ((r.t() - 18) * 1.62f) * 0.01f;
        boolean front = face < 4;
        boolean back = face >= 24 && face < 40;
        boolean top = face >= 40;

        if (change > 0) {
            if (inOpen(y) && inOpen(z) && back) {
                x += change * intensity * (x < 0f ? -0.01f : 0.01f) * l * 0.01f;
                x = clamp(x, 3f);
            }
            if (inOpen(y) && inOpen(x) && !back) {
                z += change * intensity * (z < 0f ? -0.01f : 0.01f);
                z = clamp(z, 3.2f);
                if (!front || x < 0f) {
                    x += change * intensity * 0.01f;
                    x = clamp(x, 0.4f);
                }
                // Arriba, las filas 0 y 2 de las columnas de los bordes no pasan de 0 hacia fuera.
                int row = top ? GRID_B[face - 40] : -1;
                boolean limitedToZero = top && (row == 0 || row == 2) && (face % 4 == 0 || face % 4 == 3);
                float limit = limitedToZero ? 0f : 0.2f;
                if (z > 0f) {
                    b += change * intensity * -0.02f;
                    b = Math.max(b, -limit);
                } else if (z < 0f) {
                    b += change * intensity * 0.02f;
                    b = Math.min(b, limit);
                }
            } else if (inOpen(y)) {
                x += change * intensity * (x < 0f ? -0.01f : 0.01f);
                x = clamp(x, 2.8f);
                if (b > 1.5f) {
                    x = clamp(x, 1.5f);
                    b += change * intensity * (b < 0f ? 0.03f : -0.03f);
                    b = clamp(b, 2.8f);
                }
            } else if (inOpen(x)) {
                z += change * intensity * (z < 0f ? -0.01f : 0.01f);
                z = clamp(z, 2.8f);
                if (b > 0f && z > 0f && y < 1.6f) {
                    z = clamp(z, 2.2f);
                    b = Math.max(-b, Math.min(b, b + change * intensity * -0.02f));
                } else if (b > 0f && z < 0f && y > 0f) {
                    z = clamp(z, 2.2f);
                    b = Math.max(-b, Math.min(b, b + change * intensity * -0.02f));
                } else if (y < -1.3f && b > 0f) {
                    z = clamp(z, 2.2f);
                    b = Math.max(0.5f, b + change * intensity * -0.02f);
                }
            }
        }

        if (change2 > 0) {
            if (inOpen(y) && inOpen(x) && front) {
                float intensity2 = intensity > 0.02f ? 0.6f : intensity;
                x += change2 * intensity2 * 0.01f;
                x = clamp(x, 0.2f);
                z += change2 * intensity2 * (z < 0f ? -0.02f : 0.02f);
                z = clamp(z, 2.8f);
            }
            l = (int) (l + change2 * 0.1f);
            if (b < 0f) {
                b += change2 * 5.0E-4f;
                b = b >= 0f ? 0.2f : b;
            }
            if (b > 0f) {
                b -= change2 * 5.0E-4f;
                b = b <= 0f ? -0.2f : b;
            }
        }
        return new float[]{l, x, y, z, b};
    }

    private static boolean inOpen(float v) {
        return v > -1f && v < 1f;
    }

    private static float clamp(float v, float limit) {
        return v > limit ? limit : Math.max(v, -limit);
    }

    private static Strand strand(int face, int l, float x, float y, float z, float b, float p) {
        boolean front = face < 4;
        boolean right = face >= 4 && face < 14;
        boolean left = face >= 14 && face < 24;
        boolean back = face >= 24 && face < 40;
        boolean top = face >= 40;

        // Ancla en el espacio de ModelRenderer (px, Y abajo, origen en el cuello).
        float px = -2.999f + (front ? face * 2 : left ? 7 : back ? GRID_A[face - 24] * 2 : top ? GRID_A[face - 40] * 2 : -1);
        float pz = -3.999f + (right ? RIGHT_Z[face - 4] * 2 + 1 : left ? LEFT_Z[face - 14] * 2 + 1
                : back ? 8f : top ? GRID_B[face - 40] * 2 + 0.9f : 0f);
        float py = -7f + (right ? RIGHT_Y[face - 4] * 2 : left ? LEFT_Y[face - 14] * 2 : back ? GRID_B[face - 24] * 2 : -0.5f);

        // Curvatura de los segmentos 1..3: en X para frente/espalda; en Z (con signo por lado) para el resto.
        float bend1 = b * 0.3f * (p > 0.5f ? 1f - p * 0.3f : p < -0.5f ? 1f - p * 0.1f : 1f);
        float bend2 = b * 0.3f;
        float bend3 = b * 0.3f * (p > 0.5f ? 1f + p * 0.1f : p < -0.5f ? 1f + p * 0.3f : 1f);
        boolean bendsInX = !top && !right && !left;
        float sign = left ? 1f : -1f;

        // Largo: el segmento 1 crece de 0 a 33, el 2 de 33 a 66, el 3 de 66 a 99.
        float t1 = l < 33 ? l / 33f : 1f;
        float t2 = l > 33 && l < 66 ? (l - 33) / 33f : l < 33 ? 0f : 1f;
        float t3 = l > 66 ? (l - 66) / 33f : l < 66 ? 0f : 1f;
        boolean[] visible = {true, l > 0, l > 33, l > 66};
        float[] length = {1f, t1, t2, t3};
        float[] width = {1.1f, 1.0f, 0.9f, 0.8f};
        float[] pivotY = {0f, 1.5f, 2.5f, 2.5f};
        float[] bend = {0f, bend1, bend2, bend3};

        List<Strand.Segment> segs = new ArrayList<>(4);
        float carry = 0f;
        for (int i = 0; i < 4; i++) {
            if (!visible[i]) break; // un segmento oculto oculta también a sus hijos
            float bx = i == 0 ? 0f : bendsInX ? bend[i] : 0f;
            float bz = i == 0 ? 0f : bendsInX ? 0f : sign * bend[i];
            boolean rotated = i == 0 ? (x != 0f || y != 0f || z != 0f) : (bx != 0f || bz != 0f);
            float yMin = i == 0 ? -1f : 0f;
            float yMax = yMin + 3f;
            float w = 2f * width[i];
            float dy = (i == 0 ? 0f : pivotY[i]) + carry;
            if (length[i] > 0f) {
                // Conversión a espacio de render (F): Y cambia de signo, la caja se refleja en Y y
                // la curvatura en X también (la de Z se queda).
                segs.add(new Strand.Segment(-dy, -bx * RAD_TO_DEG, 0f, bz * RAD_TO_DEG,
                        w, w, -yMax * length[i], -yMin * length[i]));
                carry = 0f;
            } else {
                // Segmento de largo 0: no dibuja nada, pero su marco sigue contando para los hijos.
                // Se arrastra como desplazamiento (sin curvatura propia, que no se vería).
                carry = dy;
                continue;
            }
            // Tras dibujar, un segmento GIRADO se desplaza (largo-1)·0,15 bloques antes de sus hijos.
            if (rotated) carry = (length[i] - 1f) * 0.15f * 16f;
        }
        // Rotación del mechón: Z·Y·X en ModelRenderer; con F, X e Y cambian de signo.
        return new Strand(-px, -py, pz, -x * RAD_TO_DEG, -y * RAD_TO_DEG, z * RAD_TO_DEG,
                Strand.RotationOrder.ZYX, 1f, 1f, 1f, segs, -1);
    }

    private static int two(String s, int at) {
        if (at + 1 >= s.length()) return 0;
        char a = s.charAt(at), b = s.charAt(at + 1);
        if (a < '0' || a > '9' || b < '0' || b > '9') return 0;
        return (a - '0') * 10 + (b - '0');
    }

    private static int clampField(int v) {
        return v > 82 ? 82 : Math.max(v, 18);
    }

    private static float rot(int v) {
        return (clampField(v) - 50) * 0.1f;
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : Integer.toString(Math.min(v, 99));
    }
}
