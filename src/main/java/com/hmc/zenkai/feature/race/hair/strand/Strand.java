package com.hmc.zenkai.feature.race.hair.strand;

import java.util.List;

/**
 * Un mechón del modelo común de pelo por mechones (plan .claude/pendiente/pelo-importado-dmz-dbc.md).
 *
 * Espacio: el de RENDER de GeckoLib relativo al pivote de la cabeza (px, Y arriba, frente = -Z,
 * X ya invertida respecto al .geo.json; el lado izquierdo del personaje es -X aquí y +X en el
 * archivo). Es el espacio en el que dibuja DragonMineZ, así que sus mechones entran sin
 * conversión; los de Dragon Block C se convierten al leerlos (DbcHairCodec) y en ningún otro sitio.
 *
 * Construcción de la cadena (la escribe StrandGeoWriter): trasladar al ancla, rotar por
 * (rx, ry, rz) en el orden {@link #order}, escalar (sx, sy, sz) y, por cada segmento, trasladar
 * dy en su Y local, rotar su curvatura y dibujar su caja. El marco de cada segmento hereda el
 * del anterior.
 *
 * @param rgb    color propio del mechón (0xRRGGBB) o -1 = el color de pelo. Se ve si el jugador deja
 *               puestos los "colores del código" y la forma no tiñe el pelo (StrandColors).
 * @param tipRgb color de la punta (DMZ 2.2, "tc"), o -1. El mechón va de rgb (o el color de pelo) a
 *               este color a lo largo de sus segmentos.
 * @param key ranura de la cabeza de la que nace el mechón (DMZ: cara × 100 + índice), o -1 si el
 *            formato no tiene ranuras (DBC, pruebas). Sirve para emparejar el mismo mechón entre
 *            las variantes de un juego (StrandBlend: el pelo cambia poco a poco al transformarse).
 */
public record Strand(float ax, float ay, float az,
                     float rx, float ry, float rz, RotationOrder order,
                     float sx, float sy, float sz,
                     List<Segment> segments, int rgb, int key, int tipRgb) {

    public Strand {
        segments = List.copyOf(segments);
    }

    /** Sin ranura (key = -1). */
    public Strand(float ax, float ay, float az, float rx, float ry, float rz, RotationOrder order,
                  float sx, float sy, float sz, List<Segment> segments, int rgb) {
        this(ax, ay, az, rx, ry, rz, order, sx, sy, sz, segments, rgb, -1, -1);
    }

    public Strand(float ax, float ay, float az, float rx, float ry, float rz, RotationOrder order,
                  float sx, float sy, float sz, List<Segment> segments, int rgb, int key) {
        this(ax, ay, az, rx, ry, rz, order, sx, sy, sz, segments, rgb, key, -1);
    }

    public Strand withKey(int newKey) {
        return new Strand(ax, ay, az, rx, ry, rz, order, sx, sy, sz, segments, rgb, newKey, tipRgb);
    }

    public Strand withTip(int newTipRgb) {
        return new Strand(ax, ay, az, rx, ry, rz, order, sx, sy, sz, segments, rgb, key, newTipRgb);
    }

    /** ¿Trae algún color propio (del mechón, de la punta o de un segmento)? */
    public boolean hasColors() {
        if (rgb >= 0 || tipRgb >= 0) return true;
        for (Segment g : segments) if (g.rgb() >= 0) return true;
        return false;
    }

    /** Orden de composición de la rotación (matriz aplicada al vértice). */
    public enum RotationOrder {
        /** M = Rx·Ry·Rz — PoseStack.mulPose X, luego Y, luego Z (DragonMineZ). */
        XYZ,
        /** M = Rz·Ry·Rx — glRotate Z, luego Y, luego X (ModelRenderer de 1.7.10, Dragon Block C). */
        ZYX
    }

    /**
     * Un cubo de la cadena, en el marco del segmento: caja centrada en X/Z (ancho w, fondo d) y de
     * y0 a y1 en Y. El desplazamiento (tx, dy, tz) y la curvatura (grados, en el orden del mechón)
     * se aplican ANTES de dibujarla y se arrastran a los segmentos siguientes. tx/tz solo los usa
     * el formato v7 de DMZ (ajustes por segmento); el resto va siempre a lo largo del mechón (dy).
     * rgb: color propio del segmento (DMZ 2.2, ajuste "c"), o -1; gana al del mechón y a la punta.
     */
    public record Segment(float tx, float dy, float tz, float cx, float cy, float cz,
                          float w, float d, float y0, float y1, int rgb) {
        public Segment(float tx, float dy, float tz, float cx, float cy, float cz,
                       float w, float d, float y0, float y1) {
            this(tx, dy, tz, cx, cy, cz, w, d, y0, y1, -1);
        }

        public Segment(float dy, float cx, float cy, float cz, float w, float d, float y0, float y1) {
            this(0f, dy, 0f, cx, cy, cz, w, d, y0, y1, -1);
        }

        public boolean hasRotation() {
            return cx != 0f || cy != 0f || cz != 0f;
        }
    }

    public boolean uniformScale() {
        return sx == sy && sy == sz;
    }
}
