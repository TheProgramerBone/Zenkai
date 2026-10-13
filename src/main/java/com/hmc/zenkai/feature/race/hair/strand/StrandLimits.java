package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Topes duros de un peinado importado. El peor preset medido de DragonMineZ son 62 mechones y
 * 250 cubos; un .geo.json de 256 cubos por jugador visible es asumible, uno de miles no. Un
 * código que se pasa se RECORTA con aviso (nunca se escribe un archivo gigante ni se lanza).
 */
public final class StrandLimits {
    private StrandLimits() {}

    public static final int MAX_STRANDS = 80;
    public static final int MAX_CUBES = 256;
    public static final int MAX_SEGMENTS = 32;
    /** Medida máxima de un cubo o de un desplazamiento, en px (la cabeza mide 8). */
    public static final float MAX_EXTENT = 64f;

    public static StrandSet clamp(StrandSet in, Consumer<Component> warn) {
        List<Strand> out = new ArrayList<>();
        int cubes = 0;
        int droppedStrands = 0;
        int cutSegments = 0;
        for (Strand s : in.strands()) {
            if (s.segments().isEmpty()) continue;
            if (out.size() >= MAX_STRANDS || cubes >= MAX_CUBES) {
                droppedStrands++;
                continue;
            }
            int keep = Math.min(s.segments().size(), Math.min(MAX_SEGMENTS, MAX_CUBES - cubes));
            cutSegments += s.segments().size() - keep;
            Strand t = keep == s.segments().size() ? s
                    : new Strand(s.ax(), s.ay(), s.az(), s.rx(), s.ry(), s.rz(), s.order(),
                    s.sx(), s.sy(), s.sz(), s.segments().subList(0, keep), s.rgb(), s.key(), s.tipRgb());
            out.add(sane(t));
            cubes += keep;
        }
        if (droppedStrands > 0) {
            warn.accept(Component.translatable("zenkai.hair_code.warn.dropped_strands", droppedStrands, MAX_STRANDS, MAX_CUBES));
        }
        if (cutSegments > 0) {
            warn.accept(Component.translatable("zenkai.hair_code.warn.cut_cubes", cutSegments, MAX_SEGMENTS, MAX_CUBES));
        }
        return new StrandSet(in.name(), out);
    }

    /** Valores no finitos o desmesurados (un código manipulado) se acotan en vez de propagarse. */
    private static Strand sane(Strand s) {
        List<Strand.Segment> segs = new ArrayList<>(s.segments().size());
        for (Strand.Segment g : s.segments()) {
            segs.add(new Strand.Segment(len(g.tx()), len(g.dy()), len(g.tz()), ang(g.cx()), ang(g.cy()), ang(g.cz()),
                    Math.abs(len(g.w())), Math.abs(len(g.d())), len(g.y0()), len(g.y1()), g.rgb()));
        }
        return new Strand(len(s.ax()), len(s.ay()), len(s.az()), ang(s.rx()), ang(s.ry()), ang(s.rz()),
                s.order(), scale(s.sx()), scale(s.sy()), scale(s.sz()), segs, s.rgb(), s.key(), s.tipRgb());
    }

    private static float len(float v) {
        return Float.isFinite(v) ? Math.max(-MAX_EXTENT, Math.min(MAX_EXTENT, v)) : 0f;
    }

    private static float ang(float v) {
        return Float.isFinite(v) ? v % 360f : 0f;
    }

    private static float scale(float v) {
        return Float.isFinite(v) ? Math.max(0.01f, Math.min(8f, Math.abs(v))) : 1f;
    }
}
