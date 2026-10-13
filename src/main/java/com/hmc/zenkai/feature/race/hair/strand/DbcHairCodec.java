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
 * segmentos 1 y 3, T = cuánto reacciona al SSJ (se ignora: solo se importa el peinado base).
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

    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    public static boolean looksLike(String code) {
        String c = DmzHairCodec.stripWhitespace(code);
        if (c.length() != G2_LENGTH && c.length() != G1_LENGTH) return false;
        for (int i = 0; i < c.length(); i++) if (!Character.isDigit(c.charAt(i))) return false;
        return true;
    }

    public static StrandSet decode(String raw, Consumer<Component> warn) throws HairCodeException {
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

        List<Strand> strands = new ArrayList<>();
        boolean usesSsjReaction = false;
        for (int face = 0; face < STRANDS; face++) {
            int at = face * 14;
            int l = two(code, at);
            if (l == 0) continue;
            float x = rot(two(code, at + 2));
            float y = rot(two(code, at + 4));
            float z = rot(two(code, at + 6));
            float b = rot(two(code, at + 8));
            float p = rot(two(code, at + 10));
            usesSsjReaction |= clampField(two(code, at + 12)) > 18;
            strands.add(strand(face, l, x, y, z, b, p));
        }
        if (usesSsjReaction) warn.accept(Component.translatable("zenkai.hair_code.warn.dbc_ssj"));
        return new StrandSet(null, strands);
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
