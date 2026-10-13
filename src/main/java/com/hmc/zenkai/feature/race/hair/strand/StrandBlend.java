package com.hmc.zenkai.feature.race.hair.strand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Peinado intermedio entre dos variantes de un mismo juego (base -&gt; SSJ, SSJ -&gt; SSJ2...): el
 * pelo cambia poco a poco mientras se carga la transformación (petición del usuario, 2026-10-12).
 *
 * Emparejado: por ranura ({@link Strand#key}, la cara y el hueco de la cabeza de los que nace en
 * DMZ), así el mechón 3 de la nuca de la base pasa a ser el mechón 3 de la nuca de SSJ. Los
 * mechones sin ranura (DBC) se emparejan por orden. Un mechón que solo está en una de las dos
 * crece desde nada o se encoge hasta desaparecer; si una cadena tiene menos segmentos, los que le
 * faltan nacen con largo y grosor cero en su punta.
 *
 * Interpolación lineal de cada número (ángulos por el camino corto). No es exacta para giros
 * grandes, pero en un mechón que se mueve unos pocos grados por paso no se nota. Función pura.
 */
public final class StrandBlend {
    private StrandBlend() {}

    public static StrandSet blend(StrandSet a, StrandSet b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        Map<Object, Strand> fromA = index(a.strands());
        Map<Object, Strand> fromB = index(b.strands());
        List<Strand> out = new ArrayList<>(fromA.size() + fromB.size());
        for (Map.Entry<Object, Strand> e : fromA.entrySet()) {
            Strand sb = fromB.get(e.getKey());
            Strand sa = e.getValue();
            out.add(sb != null ? lerp(sa, sb, t) : lerp(sa, collapsed(sa), t));
        }
        for (Map.Entry<Object, Strand> e : fromB.entrySet()) {
            if (!fromA.containsKey(e.getKey())) out.add(lerp(collapsed(e.getValue()), e.getValue(), t));
        }
        return new StrandSet(t < 0.5f ? a.name() : b.name(), out);
    }

    /** Clave de emparejado: la ranura, o "orden entre los que no tienen ranura". */
    private static Map<Object, Strand> index(List<Strand> strands) {
        Map<Object, Strand> m = new LinkedHashMap<>();
        Map<Boolean, Integer> unkeyed = new HashMap<>();
        for (Strand s : strands) {
            Object k = s.key() >= 0 ? (Object) s.key() : "u" + unkeyed.merge(true, 1, Integer::sum);
            m.putIfAbsent(k, s);
        }
        return m;
    }

    /** El mismo mechón con largo y grosor cero (de él crece o en él desaparece). */
    private static Strand collapsed(Strand s) {
        List<Strand.Segment> segs = new ArrayList<>(s.segments().size());
        for (Strand.Segment g : s.segments()) {
            segs.add(new Strand.Segment(0f, 0f, 0f, g.cx(), g.cy(), g.cz(), 0f, 0f, 0f, 0f, g.rgb()));
        }
        return new Strand(s.ax(), s.ay(), s.az(), s.rx(), s.ry(), s.rz(), s.order(),
                s.sx(), s.sy(), s.sz(), segs, s.rgb(), s.key(), s.tipRgb());
    }

    private static Strand lerp(Strand a, Strand b, float t) {
        int n = Math.max(a.segments().size(), b.segments().size());
        List<Strand.Segment> sa = padded(a.segments(), n), sb = padded(b.segments(), n);
        List<Strand.Segment> segs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Strand.Segment x = sa.get(i), y = sb.get(i);
            segs.add(new Strand.Segment(f(x.tx(), y.tx(), t), f(x.dy(), y.dy(), t), f(x.tz(), y.tz(), t),
                    deg(x.cx(), y.cx(), t), deg(x.cy(), y.cy(), t), deg(x.cz(), y.cz(), t),
                    f(x.w(), y.w(), t), f(x.d(), y.d(), t), f(x.y0(), y.y0(), t), f(x.y1(), y.y1(), t),
                    t < 0.5f ? x.rgb() : y.rgb()));
        }
        return new Strand(f(a.ax(), b.ax(), t), f(a.ay(), b.ay(), t), f(a.az(), b.az(), t),
                deg(a.rx(), b.rx(), t), deg(a.ry(), b.ry(), t), deg(a.rz(), b.rz(), t),
                t < 0.5f ? a.order() : b.order(),
                f(a.sx(), b.sx(), t), f(a.sy(), b.sy(), t), f(a.sz(), b.sz(), t),
                segs, t < 0.5f ? a.rgb() : b.rgb(), a.key() >= 0 ? a.key() : b.key(),
                t < 0.5f ? a.tipRgb() : b.tipRgb());
    }

    /** Completa la cadena hasta n segmentos con segmentos nulos en la punta. */
    private static List<Strand.Segment> padded(List<Strand.Segment> segs, int n) {
        if (segs.size() >= n) return segs;
        List<Strand.Segment> out = new ArrayList<>(segs);
        Strand.Segment last = segs.isEmpty() ? null : segs.get(segs.size() - 1);
        float tip = last == null ? 0f : last.y1();
        float w = last == null ? 0f : last.w(), d = last == null ? 0f : last.d();
        // El primero de relleno se mueve a la punta del último real; los siguientes ya están ahí.
        int rgb = last == null ? -1 : last.rgb();
        out.add(new Strand.Segment(0f, tip, 0f, 0f, 0f, 0f, w, d, 0f, 0f, rgb));
        while (out.size() < n) out.add(new Strand.Segment(0f, 0f, 0f, 0f, 0f, 0f, w, d, 0f, 0f, rgb));
        return out;
    }

    private static float f(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float deg(float a, float b, float t) {
        float d = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + d * t;
    }
}
