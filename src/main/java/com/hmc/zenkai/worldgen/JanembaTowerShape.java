package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Torre orgánica del Janemba Rift: la torre del centro de la referencia de Fusion Reborn
 * (.claude/pendiente/referencia_janemba.png), un fuste violeta que se curva y se estrecha, con
 * anillos abultados, punta afilada y, en la torre grande, una corona de picos y pinchos en la base.
 *
 * Una sola forma para dos escalas: la aguja de paisaje ({@link JanembaSpireFeature}, sin corona) y
 * el landmark ({@code JanembaTowerPiece}, con corona). La variación entera sale de {@code seed} y de
 * hashes de posición, nunca de un RandomSource compartido: la pieza de estructura se dibuja una vez
 * por chunk que la toca y cada pasada tiene que producir exactamente la misma torre.
 */
public final class JanembaTowerShape {
    private JanembaTowerShape() {}

    /** Parámetros de una torre. */
    public record Params(int height, double baseRadius, double maxLean, boolean crown) {}

    /**
     * Alcance horizontal máximo desde el eje de la base, en bloques. La pieza de estructura dimensiona
     * su caja con esto: un bloque fuera de la caja no se dibuja en los chunks que no la tocan.
     */
    public static int reach(Params p) {
        double flare = 4 * 0.55;
        double crownOut = p.crown() ? 1.5 + 9 * 0.55 : 0;
        return (int) Math.ceil(p.baseRadius() + flare + 1.0 + p.maxLean() + 1.4 + crownOut) + 1;
    }

    /** Altura máxima sobre la base (fuste + punta + corona). */
    public static int top(Params p) {
        return p.height() + (p.crown() ? 16 : (int) Math.ceil(p.baseRadius() * 3.2)) + 1;
    }

    public static void build(JanembaShapes.Sink sink, long seed, int bx, int by, int bz, Params p) {
        BlockState stone = ModBlocks.HFIL_VOID_STONE.get().defaultBlockState();
        BlockState blue = ModBlocks.HFIL_JANEMBA_BLUE_MASS.get().defaultBlockState();
        BlockState flesh = ModBlocks.HFIL_CORRUPTED_FLESH.get().defaultBlockState();

        double leanAngle = JanembaShapes.hash01(seed, 1, 0, 0) * Math.PI * 2;
        double lean = (0.35 + 0.65 * JanembaShapes.hash01(seed, 2, 0, 0)) * p.maxLean();
        double swayPhase = JanembaShapes.hash01(seed, 3, 0, 0) * Math.PI * 2;
        // El vaivén escala con la inclinación permitida: la torre grande (3) lo conserva entero y las
        // agujas, más tiesas, ondulan menos (los quiebros de una aguja fina la hacían parecer rama).
        double swayAmp = (0.5 + 0.9 * JanembaShapes.hash01(seed, 4, 0, 0)) * Math.min(1.0, p.maxLean() / 3.0);
        double ringPeriod = 6 + Math.floor(JanembaShapes.hash01(seed, 5, 0, 0) * 4);
        double ellip = 0.78 + 0.22 * JanembaShapes.hash01(seed, 6, 0, 0);
        double ellAngle = JanembaShapes.hash01(seed, 7, 0, 0) * Math.PI;
        double lx = Math.cos(leanAngle), lz = Math.sin(leanAngle);
        double ea = Math.cos(ellAngle), eb = Math.sin(ellAngle);

        int h = p.height();
        double topCx = 0, topCz = 0, topR = 1;
        for (int y = -3; y < h; y++) {
            double t = Math.max(0, y) / (double) h;
            // Eje: inclinación que crece con la altura + un vaivén que lo hace serpentear.
            double sway = Math.sin(y * 0.21 + swayPhase) * swayAmp * t;
            double cx = lx * lean * t * t - lz * sway;
            double cz = lz * lean * t * t + lx * sway;
            // Radio: se estrecha hacia arriba, con anillos abultados y la base ensanchada.
            double ring = Math.pow(Math.max(0, Math.cos(2 * Math.PI * y / ringPeriod)), 4) * 0.9;
            double rad = p.baseRadius() * (1.0 - 0.45 * t) + ring;
            if (y < 4) rad += (4 - y) * 0.55;
            // Corona: el remate se ensancha en una "almena" de dos capas antes de la punta.
            if (p.crown() && y >= h - 3) rad += 1.5;
            // Solo algunos anillos son azules (hash por anillo): con cada anillo azul se leían como las
            // rayas de un bastón de caramelo, no como una torre orgánica.
            int ringIndex = (int) Math.floor((y + ringPeriod / 2.0) / ringPeriod);
            boolean blueRing = JanembaShapes.hash01(seed, 15, ringIndex, 0) < 0.45;
            BlockState body = ring > 0.55 && blueRing ? blue : stone;
            fillDisc(sink, seed, bx, by + y, bz, cx, cz, rad, ellip, ea, eb, body, flesh);
            topCx = cx;
            topCz = cz;
            topR = rad;
        }

        // Punta: cono que sigue la inclinación del fuste.
        double tipLen = p.crown() ? 10 + 5 * JanembaShapes.hash01(seed, 8, 0, 0) : topR * 3.0;
        double tipR = p.crown() ? Math.max(1.2, topR - 2.0) : topR;
        JanembaShapes.spike(sink, bx + topCx + 0.5, by + h, bz + topCz + 0.5,
                lx * 0.25, 1.0, lz * 0.25, tipLen, tipR, stone);

        if (p.crown()) {
            // Corona de picos alrededor del borde de la almena, inclinados hacia fuera.
            int n = 5 + (int) (JanembaShapes.hash01(seed, 9, 0, 0) * 3);
            for (int i = 0; i < n; i++) {
                double a = (i + 0.3 * JanembaShapes.hash01(seed, 10, i, 0)) / n * Math.PI * 2;
                double ox = Math.cos(a), oz = Math.sin(a);
                double len = 4 + 5 * JanembaShapes.hash01(seed, 11, i, 0);
                JanembaShapes.spike(sink, bx + topCx + ox * (topR - 0.5) + 0.5, by + h - 1, bz + topCz + oz * (topR - 0.5) + 0.5,
                        ox * 0.55, 1.0, oz * 0.55, len, 1.3, i % 2 == 0 ? stone : blue);
            }
            // Pinchos de la base: salen del pie hacia fuera, como raíces.
            int m = 4 + (int) (JanembaShapes.hash01(seed, 12, 0, 0) * 4);
            double baseR = p.baseRadius() + 2.0;
            for (int i = 0; i < m; i++) {
                double a = (i + 0.5 * JanembaShapes.hash01(seed, 13, i, 0)) / m * Math.PI * 2;
                double ox = Math.cos(a), oz = Math.sin(a);
                double len = 3 + 4 * JanembaShapes.hash01(seed, 14, i, 0);
                JanembaShapes.spike(sink, bx + ox * baseR + 0.5, by, bz + oz * baseR + 0.5,
                        ox, 0.9, oz, len, 1.2, stone);
            }
        }
    }

    /** Capa horizontal elíptica con el borde roído (hash) y alguna mancha de carne. */
    private static void fillDisc(JanembaShapes.Sink sink, long seed, int bx, int y, int bz,
                                 double cx, double cz, double rad, double ellip, double ea, double eb,
                                 BlockState body, BlockState accent) {
        int ir = (int) Math.ceil(rad + 1);
        int icx = Mth.floor(cx), icz = Mth.floor(cz);
        for (int dx = icx - ir; dx <= icx + ir; dx++) {
            for (int dz = icz - ir; dz <= icz + ir; dz++) {
                double px = dx + 0.5 - cx, pz = dz + 0.5 - cz;
                double u = px * ea + pz * eb, v = -px * eb + pz * ea;
                double e = (u * u) / (rad * rad * ellip * ellip) + (v * v) / (rad * rad);
                double edge = 1.0 + (JanembaShapes.hash01(seed, bx + dx, y, bz + dz) - 0.5) * 0.35;
                if (e > edge) continue;
                BlockState s = JanembaShapes.hash01(seed ^ 0x5DEECE66DL, bx + dx, y, bz + dz) < 0.05 ? accent : body;
                sink.set(bx + dx, y, bz + dz, s);
            }
        }
    }
}
