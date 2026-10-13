package com.hmc.zenkai.feature.race.hair.strand;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Convierte un {@link StrandSet} en un .geo.json estático que el catálogo de peinados (S5) sirve
 * tal cual: el mismo esqueleto que classic.geo.json y, colgando de armorHead, un hueso por mechón
 * (pivote en su raíz, para animarlo) con sus cubos, cada uno con su propio pivote y rotación. Sin jerarquía por mechón: se encadenan
 * las matrices aquí y cada cubo sale ya colocado ("aplanado").
 *
 * Convenciones de GeckoLib 4.8.4 (BakedModelFactory y RenderUtil, comprobadas en el jar el
 * 2026-10-12; las mismas que usa .claude/skills/zenkai-character-textures/scripts/geo.py):
 * al cargar invierte X (pivote x -> -x, origin.x -> -(origin.x + size.x)) y rota con
 * (-rx, -ry, +rz) en radianes, aplicando Z, luego Y, luego X. Así que, para una rotación de
 * render R = Rz(a)·Ry(b)·Rx(c), el archivo lleva [-c, -b, a] en grados.
 *
 * Escala no uniforme seguida de una curvatura produce una cizalla que un cubo no puede
 * representar: se toma la rotación más cercana (descomposición polar) y se cuenta como
 * aproximación en el informe. Con escala uniforme el resultado es exacto.
 */
public final class StrandGeoWriter {
    private StrandGeoWriter() {}

    /** Pivote de la cabeza en el archivo (el cuello del rig vanilla). */
    private static final double HEAD_PIVOT_Y = 24.0;
    private static final int TEXTURE_SIZE = 64;

    public record Result(JsonObject geo, int cubes, int approximated) {}

    /** Prefijo de los huesos de mechón (los anima HairMotion; el resto del sistema no los distingue). */
    public static final String STRAND_BONE = "zh_s";

    public static Result write(String identifier, StrandSet set) {
        int approximated = 0, total = 0;
        // Columna de la textura de cada segmento (colores propios de los mechones, StrandColors).
        StrandColors.Palette palette = StrandColors.of(set);
        JsonArray strandBones = new JsonArray();
        for (int si = 0; si < set.strands().size(); si++) {
            Strand s = set.strands().get(si);
            // Un hueso por mechón, con el pivote en su raíz: así se puede balancear entero
            // (HairMotion). Los cubos siguen colocados en absoluto: en reposo el hueso no gira.
            JsonObject strandBone = bone(STRAND_BONE + si, "armorHead", -s.ax(), s.ay() + HEAD_PIVOT_Y, s.az());
            JsonArray cubes = new JsonArray();
            Matrix4d m = new Matrix4d().translate(s.ax(), s.ay(), s.az());
            rotate(m, s.order(), s.rx(), s.ry(), s.rz());
            m.scale(s.sx(), s.sy(), s.sz());
            int n = s.segments().size();
            for (int k = 0; k < n; k++) {
                Strand.Segment g = s.segments().get(k);
                m.translate(g.tx(), g.dy(), g.tz());
                rotate(m, s.order(), g.cx(), g.cy(), g.cz());
                if (g.w() <= 0f || g.d() <= 0f || g.y1() <= g.y0()) continue;
                if (cube(m, g, cubes, (float) k / n, (float) (k + 1) / n, palette.column(si, k))) approximated++;
            }
            if (cubes.isEmpty()) continue;
            strandBone.add("cubes", cubes);
            strandBones.add(strandBone);
            total += cubes.size();
        }
        JsonObject head = bone("armorHead", "bipedHead", 0, HEAD_PIVOT_Y, 0);
        JsonObject geo = geometry(identifier, head);
        JsonArray bones = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        bones.addAll(strandBones);
        return new Result(geo, total, approximated);
    }

    private static void rotate(Matrix4d m, Strand.RotationOrder order, float x, float y, float z) {
        double rx = Math.toRadians(x), ry = Math.toRadians(y), rz = Math.toRadians(z);
        if (order == Strand.RotationOrder.XYZ) {
            m.rotateX(rx).rotateY(ry).rotateZ(rz);
        } else {
            m.rotateZ(rz).rotateY(ry).rotateX(rx);
        }
    }

    /** Añade el cubo del segmento. Devuelve true si hubo que aproximar una cizalla. */
    private static boolean cube(Matrix4d m, Strand.Segment g, JsonArray out, float t0, float t1, int column) {
        Matrix3d lin = m.get3x3(new Matrix3d());
        if (Math.abs(lin.determinant()) < 1e-9) return false;
        Vector3d c0 = lin.getColumn(0, new Vector3d());
        Vector3d c1 = lin.getColumn(1, new Vector3d());
        Vector3d c2 = lin.getColumn(2, new Vector3d());
        double l0 = c0.length(), l1 = c1.length(), l2 = c2.length();
        boolean sheared = Math.abs(c0.dot(c1)) / (l0 * l1) > 1e-3
                || Math.abs(c0.dot(c2)) / (l0 * l2) > 1e-3
                || Math.abs(c1.dot(c2)) / (l1 * l2) > 1e-3;

        // Rotación más cercana (descomposición polar A = R·S). Sin cizalla, R son las columnas
        // normalizadas y S la escala: exacto. Con cizalla reparte el error entre los tres ejes
        // (medido con los 27 presets de DMZ: mejor que enderezar contra el eje del mechón).
        Matrix3d r = polar(lin);
        Vector3d e0 = r.getColumn(0, new Vector3d());
        Vector3d e1 = r.getColumn(1, new Vector3d());
        Vector3d e2 = r.getColumn(2, new Vector3d());
        double hx = g.w() / 2.0 * Math.abs(e0.dot(c0));
        double hy = (g.y1() - g.y0()) / 2.0 * Math.abs(e1.dot(c1));
        double hz = g.d() / 2.0 * Math.abs(e2.dot(c2));
        // La caja se coloca por su CENTRO (con cizalla, el origen local ya no cae en su eje).
        Vector3d center = m.transformPosition(new Vector3d(0, (g.y0() + g.y1()) / 2.0, 0));

        // R = Rz(a)·Ry(b)·Rx(c); R[fila][col] = e_col[fila].
        double r20 = e0.z;
        double a, b, c;
        if (Math.abs(r20) < 0.99999) {
            b = Math.asin(-r20);
            a = Math.atan2(e0.y, e0.x);
            c = Math.atan2(e1.z, e2.z);
        } else {
            b = r20 < 0 ? Math.PI / 2 : -Math.PI / 2;
            a = Math.atan2(-e1.x, e1.y);
            c = 0;
        }

        // Render -> archivo: X cambia de signo y el pivote de la cabeza está en y = 24.
        double px = -center.x, py = center.y + HEAD_PIVOT_Y, pz = center.z;
        JsonObject cube = new JsonObject();
        cube.add("origin", vec(px - hx, py - hy, pz - hz));
        cube.add("size", vec(2 * hx, 2 * hy, 2 * hz));
        cube.add("pivot", vec(px, py, pz));
        cube.add("rotation", vec(-Math.toDegrees(c), -Math.toDegrees(b), Math.toDegrees(a)));
        cube.add("uv", rampUv(t0, t1, column));
        out.add(cube);
        return sheared;
    }

    // ── Tonos: UV por cara sobre una rampa vertical ─────────────────────────────
    // La textura del pelo por mechones (HairRampTextures en el cliente, 64×64) es una rampa:
    // fila 0 = tono más claro (+1), fila 63 = el más oscuro (-1), el color base en medio; cada COLUMNA
    // es un color (columna 0 = el de pelo; las demás, colores propios de los mechones, StrandColors). Cada cara
    // toma la franja de SU sitio en el mechón: de la raíz (oscura) a la punta (clara), la cara de
    // arriba un punto más clara y la de abajo más oscura (petición del usuario: pelo "más bonito";
    // con un tinte multiplicado el negro era una mancha plana). Con la textura blanca de un pack
    // exportado cualquier franja es blanca y el resultado es el de antes.

    /** Tono de un punto del mechón: t = 0 en la raíz, 1 en la punta. */
    private static float shadeAt(float t) {
        return -0.75f + 1.05f * t;
    }

    private static int row(float shade) {
        float s = Math.max(-1f, Math.min(1f, shade));
        return Math.round((1f - s) / 2f * (TEXTURE_SIZE - 1));
    }

    /** UV de las caras: franja de tonos de su sitio en el mechón, en la columna de su color. */
    private static JsonObject rampUv(float t0, float t1, int column) {
        int top = row(shadeAt(t1)), bottom = row(shadeAt(t0));
        JsonObject faces = new JsonObject();
        for (String side : new String[]{"north", "south", "east", "west"}) {
            faces.add(side, face(column, top, Math.max(1, bottom - top + 1)));
        }
        faces.add("up", face(column, row(shadeAt(t1) + 0.35f), 1));
        faces.add("down", face(column, row(shadeAt(t0) - 0.35f), 1));
        return faces;
    }

    private static JsonObject face(int u, int v, int height) {
        JsonObject f = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u);
        uv.add(v);
        JsonArray size = new JsonArray();
        size.add(1);
        size.add(height);
        f.add("uv", uv);
        f.add("uv_size", size);
        return f;
    }

    /** Parte ortogonal de la descomposición polar (iteración de Newton R = (R + R^-T) / 2). */
    private static Matrix3d polar(Matrix3d a) {
        Matrix3d r = new Matrix3d(a);
        if (r.determinant() < 0) r.scale(-1); // no debería pasar con escalas positivas
        for (int i = 0; i < 30; i++) {
            Matrix3d invT = new Matrix3d(r).invert().transpose();
            Matrix3d next = new Matrix3d(r).add(invT).scale(0.5);
            double diff = Math.abs(next.m00 - r.m00) + Math.abs(next.m11 - r.m11) + Math.abs(next.m22 - r.m22)
                    + Math.abs(next.m01 - r.m01) + Math.abs(next.m12 - r.m12) + Math.abs(next.m20 - r.m20);
            r = next;
            if (diff < 1e-12) break;
        }
        return r;
    }

    // ── Esqueleto (el mismo que classic.geo.json y que hair_strands.py) ─────────

    private static JsonObject geometry(String identifier, JsonObject armorHead) {
        JsonArray bones = new JsonArray();
        bones.add(bone("bipedHead", null, 0, 24, 0));
        bones.add(armorHead);
        bones.add(bone("bipedBody", null, 0, 24, 0));
        bones.add(bone("armorBody", "bipedBody", 0, 24, 0));
        bones.add(bone("bipedRightArm", null, -5, 22, 0));
        bones.add(bone("armorRightArm", "bipedRightArm", -5, 22, 0));
        bones.add(bone("bipedLeftArm", null, 5, 22, 0));
        bones.add(bone("armorLeftArm", "bipedLeftArm", 5, 22, 0));
        bones.add(bone("bipedLeftLeg", null, 2, 12, 0));
        bones.add(bone("armorLeftLeg", "bipedLeftLeg", 2, 12, 0));
        bones.add(bone("armorLeftBoot", "bipedLeftLeg", 2, 12, 0));
        bones.add(bone("bipedRightLeg", null, -2, 12, 0));
        bones.add(bone("armorRightLeg", "bipedRightLeg", -2, 12, 0));
        bones.add(bone("armorRightBoot", "bipedRightLeg", -2, 12, 0));

        JsonObject desc = new JsonObject();
        desc.addProperty("identifier", "geometry." + identifier);
        desc.addProperty("texture_width", TEXTURE_SIZE);
        desc.addProperty("texture_height", TEXTURE_SIZE);
        desc.addProperty("visible_bounds_width", 3);
        desc.addProperty("visible_bounds_height", 3.5);
        desc.add("visible_bounds_offset", vec(0, 1.25, 0));

        JsonObject geo = new JsonObject();
        geo.add("description", desc);
        geo.add("bones", bones);
        JsonArray list = new JsonArray();
        list.add(geo);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");
        root.add("minecraft:geometry", list);
        return root;
    }

    private static JsonObject bone(String name, String parent, double x, double y, double z) {
        JsonObject b = new JsonObject();
        b.addProperty("name", name);
        if (parent != null) b.addProperty("parent", parent);
        b.add("pivot", vec(x, y, z));
        return b;
    }

    private static JsonArray vec(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    private static double round(double v) {
        double r = Math.round(v * 10000.0) / 10000.0;
        return r == 0.0 ? 0.0 : r; // sin "-0.0" en el archivo
    }
}
