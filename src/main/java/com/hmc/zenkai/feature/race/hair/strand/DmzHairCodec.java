package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Lector de los códigos de pelo de DragonMineZ 2.x ({@code DMZ1:}, {@code DMZ4:}, {@code DMZ5:} y
 * los juegos de 4 formas {@code DMZF1:}/{@code DMZF4:}/{@code DMZF5:}).
 *
 * Escrito desde cero a partir del FORMATO (decodificado del jar 2.1.3 en 2026-10-12), sin código de
 * DragonMineZ (GPLv3): ver .claude/pendiente/pelo-importado-dmz-dbc.md §1 y §4.
 *
 * Formato: prefijo + número gigante en base62 ({@code 0-9A-Za-z}, v1) o base64url
 * ({@code A-Za-z0-9-_}, v4/v5) -> bytes big-endian sin el byte de signo -> deflate crudo (sin
 * cabecera zlib) -> NBT sin comprimir. Un peinado: claves F/B/L/R/T (o FRONT...) con la lista de
 * mechones visibles de cada cara. Un juego: B/S/S2/T (o Base/SSJ/SSJ2/SSJ3), cada uno un peinado.
 *
 * Seguridad: el código lo pega un jugador, así que nada de él se cree. Longitud acotada antes de
 * tocar BigInteger, inflado con tope de salida (un "zip bomb" de pocos KB no llena la RAM) y NBT
 * leído con cuota. Cualquier fallo es una HairCodeException con un mensaje legible.
 */
public final class DmzHairCodec {
    private DmzHairCodec() {}

    public static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** Juego de 4 de DMZ 2.1: hasta 5,7 KB; de DMZ 2.2: 10-13 KB. Margen sin abrir la puerta a megas. */
    public static final int MAX_CODE_LENGTH = 32_768;
    private static final int MAX_INFLATED_BYTES = 256 * 1024;
    private static final long MAX_NBT_BYTES = 2L * 1024 * 1024;

    // ── Geometría del formato (render de GeckoLib relativo al pivote de la cabeza, px) ─────────

    /**
     * Caras en el orden del formato: F, B, L, R, T. Ranuras por cara y su rejilla. Cada mechón nace
     * girado hacia fuera de su cara (frente -90 X, atrás +90 X, izquierda +90 Z, derecha -90 Z),
     * pero el código guarda la rotación ABSOLUTA (ya sumada), así que aquí no hace falta.
     */
    private enum Face {
        FRONT("F", 4, 1, 4),
        BACK("B", 16, 4, 4),
        LEFT("L", 16, 4, 4),
        RIGHT("R", 16, 4, 4),
        TOP("T", 16, 4, 4);

        final String key;
        final int slots, rows, cols;

        Face(String key, int slots, int rows, int cols) {
            this.key = key;
            this.slots = slots;
            this.rows = rows;
            this.cols = cols;
        }
    }

    private static final float[] GRID = {-3f, -1f, 1f, 3f};
    private static final float[] ROW_DROP = {0f, -1.5f, -3f, -4.5f};
    /** Cada cubo mide el 85% del anterior. */
    private static final float TAPER = 0.85f;
    private static final int MAX_CUBES_PER_STRAND = 50;

    /** Ancla fija de cada ranura: la posición NO viaja en el código, sale de esta tabla. */
    static float[] anchor(Face face, int index) {
        int row = index / face.cols;
        int col = index % face.cols;
        float gx = GRID[col % 4];
        float gz = GRID[row % 4];
        float drop = ROW_DROP[row % 4];
        return switch (face) {
            case FRONT -> new float[]{gx, 7.25f, -4f};
            case BACK -> new float[]{gx, 7.25f + drop, 4f};
            case LEFT -> new float[]{-3.95f, 7.25f + drop, gx};
            case RIGHT -> new float[]{3.95f, 7.25f + drop, -gx};
            case TOP -> new float[]{gx, 7.85f, gz};
        };
    }

    /** Ranura estable del mechón (la misma numeración que el id "i" del formato v2+). */
    private static int slotKey(Face face, int index) {
        return face.ordinal() * 100 + index;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public static boolean looksLike(String code) {
        return code != null && code.startsWith("DMZ");
    }

    public static StrandSet.Variants decode(String raw, Consumer<Component> warn) throws HairCodeException {
        String code = stripWhitespace(raw);
        if (code.length() > MAX_CODE_LENGTH) {
            throw new HairCodeException("too_long", code.length(), MAX_CODE_LENGTH);
        }
        boolean full = code.startsWith("DMZF");
        int colon = code.indexOf(':');
        if (!code.startsWith("DMZ") || colon < 0) throw new HairCodeException("not_dmz");
        String version = code.substring(full ? 4 : 3, colon);
        String alphabet = switch (version) {
            case "1" -> BASE62;
            case "4", "5" -> BASE64URL;
            case "22" -> null; // v2.2 de DMZ: base64url normal (bytes tal cual), no un número gigante
            default -> throw new HairCodeException("dmz_version", version);
        };
        String body = code.substring(colon + 1);

        // Formato de respaldo de DMZF4: varios códigos sueltos unidos por '|'.
        if (full && body.indexOf('|') >= 0) return decodeJoined(body, warn);

        CompoundTag root = readNbt(body, alphabet);
        if (!full) {
            // Un código suelto puede traer un juego dentro; DMZ se queda con la base.
            CompoundTag one = root.contains("Base", Tag.TAG_COMPOUND) ? root.getCompound("Base") : root;
            return StrandSet.Variants.single(fromNbt(one, warn));
        }
        CompoundTag base = pick(root, "B", "Base");
        if (base == null) throw new HairCodeException("no_base");
        return new StrandSet.Variants(fromNbt(base, warn), optional(root, "S", "SSJ", warn),
                optional(root, "S2", "SSJ2", warn), optional(root, "T", "SSJ3", warn),
                optional(root, "S4", "SSJ4", warn));
    }

    private static StrandSet.Variants decodeJoined(String body, Consumer<Component> warn) throws HairCodeException {
        String[] parts = body.split("\\|");
        if (parts.length < 3) throw new HairCodeException("dmzf4_incomplete");
        StrandSet[] sets = new StrandSet[4];
        for (int i = 0; i < Math.min(4, parts.length); i++) {
            sets[i] = decode(parts[i], warn).base();
        }
        return new StrandSet.Variants(sets[0], sets[1], sets[2], sets[3]);
    }

    @Nullable
    private static StrandSet optional(CompoundTag root, String shortKey, String longKey, Consumer<Component> warn) {
        CompoundTag t = pick(root, shortKey, longKey);
        return t == null ? null : fromNbt(t, warn);
    }

    @Nullable
    private static CompoundTag pick(CompoundTag root, String shortKey, String longKey) {
        if (root.contains(shortKey, Tag.TAG_COMPOUND)) return root.getCompound(shortKey);
        if (root.contains(longKey, Tag.TAG_COMPOUND)) return root.getCompound(longKey);
        return null;
    }

    private static CompoundTag readNbt(String body, @Nullable String alphabet) throws HairCodeException {
        if (body.isEmpty()) throw new HairCodeException("empty");
        byte[] packed;
        if (alphabet == null) {
            try {
                packed = java.util.Base64.getUrlDecoder().decode(body);
            } catch (IllegalArgumentException e) {
                throw new HairCodeException("corrupt").withCause(e);
            }
        } else {
            packed = fromBase(body, alphabet);
        }
        byte[] nbt = inflate(packed);
        try {
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbt)), NbtAccounter.create(MAX_NBT_BYTES));
        } catch (IOException | RuntimeException e) {
            throw new HairCodeException("corrupt").withCause(e);
        }
    }

    static StrandSet fromNbt(CompoundTag hair, Consumer<Component> warn) {
        int version = hair.contains("v") ? hair.getInt("v") : hair.contains("Version") ? hair.getInt("Version") : 1;
        String name = hair.contains("n") ? hair.getString("n") : hair.getString("Name");
        List<Strand> strands = new ArrayList<>();
        boolean colors = false;
        for (Face face : Face.values()) {
            String key = hair.contains(face.key, Tag.TAG_LIST) ? face.key
                    : hair.contains(face.name(), Tag.TAG_LIST) ? face.name() : null;
            if (key == null) continue;
            ListTag list = hair.getList(key, Tag.TAG_COMPOUND);
            CompoundTag[] slots = new CompoundTag[face.slots];
            for (int i = 0; i < list.size(); i++) {
                CompoundTag st = list.getCompound(i);
                int target = i;
                if (version >= 2) {
                    int id = st.contains("i") ? st.getInt("i") : st.getInt("Id");
                    int calc = id - face.ordinal() * 100;
                    if (calc >= 0 && calc < face.slots) target = calc;
                }
                if (target < face.slots) slots[target] = st;
            }
            for (int i = 0; i < face.slots; i++) {
                if (slots[i] == null) continue;
                // v6+ (DMZ 2.2): mechón por largo total y segmentos; antes, cadena de cubos al 85%.
                Strand s = version >= FIRST_V7_STRAND_VERSION ? strandV7(face, i, slots[i], version)
                        : strand(face, i, slots[i]);
                if (s != null) {
                    strands.add(s);
                    colors |= s.hasColors();
                }
            }
        }
        if (colors) warn.accept(Component.translatable("zenkai.hair_code.warn.strand_colors"));
        return new StrandSet(name == null || name.isEmpty() ? null : name, strands);
    }

    @Nullable
    private static Strand strand(Face face, int index, CompoundTag t) {
        int length = Math.max(0, Math.min(MAX_CUBES_PER_STRAND, intOf(t, "l", "Length", 0)));
        if (length == 0) return null;
        float ls = floatOf(t, "ls", "LengthScale", 1f);
        float cw = floatOf(t, "cw", "CubeW", 2f), ch = floatOf(t, "ch", "CubeH", 2f), cd = floatOf(t, "cd", "CubeD", 2f);
        float cx = floatOf(t, "cx", "CurveX", 0f), cy = floatOf(t, "cy", "CurveY", 0f), cz = floatOf(t, "cz", "CurveZ", 0f);
        double sinX = Math.sin(Math.toRadians(Math.abs(cx)));
        double sinZ = Math.sin(Math.toRadians(Math.abs(cz)));

        List<Strand.Segment> segs = new ArrayList<>(length);
        float k = 1f;
        float prevH = 0f;
        for (int i = 0; i < length; i++) {
            float w = cw * k, h = ch * k * ls, d = cd * k;
            if (i == 0) {
                segs.add(new Strand.Segment(0f, 0f, 0f, 0f, w, d, 0f, h));
            } else {
                // Solape entre cubos para que la curva no abra una rendija (mismo cálculo que el
                // formato; su 0,015 está en bloques, aquí en px).
                float overlap = (float) (w / 2 * sinZ + d / 2 * sinX + BASE_OVERLAP_PX);
                segs.add(new Strand.Segment(prevH, cx, cy, cz, w, d, -overlap, h));
            }
            prevH = h;
            k *= TAPER;
        }
        float[] a = anchor(face, index);
        return new Strand(a[0], a[1], a[2],
                floatOf(t, "rx", "RotX", 0f), floatOf(t, "ry", "RotY", 0f), floatOf(t, "rz", "RotZ", 0f),
                Strand.RotationOrder.XYZ,
                floatOf(t, "sx", "ScaleX", 1f), floatOf(t, "sy", "ScaleY", 1f), floatOf(t, "sz", "ScaleZ", 1f),
                segs, color(t.contains("c") ? t.getString("c") : t.getString("Color"))).withKey(slotKey(face, index));
    }

    // ── Mechones del formato v6/v7 (DragonMineZ 2.2, códigos DMZ22:/DMZF22:) ─────────────
    // FORMATO leído del código fuente publicado de DMZ (rama v2.2: HairPoseBuilder y
    // HairMeshBuilder, 2026-10-12); implementación propia. Plan §1.6.

    private static final int FIRST_V7_STRAND_VERSION = 6;
    /** Hasta v6 las escalas por segmento eran absolutas; desde v7 se acumulan. */
    private static final int RELATIVE_SCALE_VERSION = 7;
    private static final int V7_MAX_SEGMENTS = 32;
    private static final float BASE_OVERLAP_PX = 0.015f * 16f;

    @Nullable
    private static Strand strandV7(Face face, int index, CompoundTag t, int version) {
        int segments = Math.max(0, Math.min(V7_MAX_SEGMENTS, t.getInt("sg")));
        float length = t.contains("ln") ? clampF(t.getFloat("ln"), 0.1f, 128f, 6f) : 6f;
        if (segments <= 0) return null;
        float ratio = t.contains("lr") ? clampF(t.getFloat("lr"), 0.1f, 4f, 1f) : 1f;
        float width = t.contains("w") ? clampF(t.getFloat("w"), 0.1f, 16f, 2f) : 2f;
        float depth = t.contains("d") ? clampF(t.getFloat("d"), 0.1f, 16f, 2f) : 2f;
        float taper = t.contains("tp") ? clampF(t.getFloat("tp"), 0.05f, 2f, 1f) : 1f;
        float taperCurve = t.contains("tg") ? clampF(t.getFloat("tg"), 0.25f, 4f, 1f) : 1f;
        int joints = Math.max(1, segments - 1);
        float bendX = segments > 1 ? clampF(t.getFloat("bx"), -3600f, 3600f, 0f) / joints : 0f;
        float bendY = segments > 1 ? clampF(t.getFloat("by"), -3600f, 3600f, 0f) / joints : 0f;
        float bendZ = segments > 1 ? clampF(t.getFloat("bz"), -3600f, 3600f, 0f) / joints : 0f;
        float twist = segments > 1 ? clampF(t.getFloat("tw"), -3600f, 3600f, 0f) / joints : 0f;

        // Ajustes por segmento ("so"): desplazamiento, giro y escalas de largo/ancho/fondo.
        CompoundTag[] overrides = new CompoundTag[segments];
        if (t.contains("so", Tag.TAG_LIST)) {
            ListTag so = t.getList("so", Tag.TAG_COMPOUND);
            for (int i = 0; i < so.size(); i++) {
                CompoundTag o = so.getCompound(i);
                int k = o.getInt("k");
                if (k >= 0 && k < segments) overrides[k] = o;
            }
        }

        float[] params = new float[segments];
        float totalWeight = 0f;
        for (int k = 0; k < segments; k++) {
            params[k] = segments > 1 ? (float) k / (segments - 1) : 0f;
            totalWeight += (float) Math.pow(ratio, params[k]);
        }

        boolean relativeScales = version >= RELATIVE_SCALE_VERSION;
        float lengthScale = 1f, widthScale = 1f, depthScale = 1f;
        float prevLength = 0f;
        List<Strand.Segment> segs = new ArrayList<>(segments);
        for (int k = 0; k < segments; k++) {
            CompoundTag o = overrides[k];
            float ls = o != null && o.contains("ls") ? clampF(o.getFloat("ls"), 0.1f, 16f, 1f) : 1f;
            float ws = o != null && o.contains("ws") ? clampF(o.getFloat("ws"), 0.1f, 16f, 1f) : 1f;
            float ds = o != null && o.contains("ds") ? clampF(o.getFloat("ds"), 0.1f, 16f, 1f) : 1f;
            if (relativeScales) {
                lengthScale *= ls;
                widthScale *= ws;
                depthScale *= ds;
            } else {
                lengthScale = ls;
                widthScale = ws;
                depthScale = ds;
            }
            float weight = (float) Math.pow(ratio, params[k]) / totalWeight;
            float taperFactor = (float) Math.pow(taper, Math.pow(params[k], taperCurve));
            float segLength = length * weight * lengthScale;
            float segWidth = width * taperFactor * widthScale;
            float segDepth = depth * taperFactor * depthScale;

            // Giro relativo al segmento anterior: curva y torsión repartidas por articulación, y
            // después el giro propio del ajuste. El desplazamiento del ajuste va en el marco ya
            // curvado (antes de su giro propio), igual que en el formato.
            Quaternionf relative = new Quaternionf();
            if (k > 0) {
                relative.rotateX(rad(bendX)).rotateY(rad(bendY)).rotateZ(rad(bendZ)).rotateY(rad(twist));
            }
            Vector3f offset = new Vector3f();
            boolean offsetZero = true;
            if (o != null) {
                offset.set(clampF(o.getFloat("ox"), -2f, 2f, 0f), clampF(o.getFloat("oy"), -2f, 2f, 0f),
                        clampF(o.getFloat("oz"), -2f, 2f, 0f));
                offsetZero = offset.lengthSquared() == 0f;
                relative.transform(offset);
                relative.rotateX(rad(clampF(o.getFloat("rx"), -180f, 180f, 0f)))
                        .rotateY(rad(clampF(o.getFloat("ry"), -180f, 180f, 0f)))
                        .rotateZ(rad(clampF(o.getFloat("rz"), -180f, 180f, 0f)));
            }
            Vector3f euler = new Matrix3f().set(relative).getEulerAnglesXYZ(new Vector3f());

            // Solape con el segmento anterior (solo si no se desplazó): tapa la rendija del codo.
            float bottom = 0f;
            if (k > 0 && offsetZero) {
                Vector3f dir = relative.transform(new Vector3f(0f, 1f, 0f));
                bottom = -(segWidth / 2 * Math.abs(dir.x) + segDepth / 2 * Math.abs(dir.z) + BASE_OVERLAP_PX);
            }
            segs.add(new Strand.Segment(offset.x, prevLength + offset.y, offset.z,
                    deg(euler.x), deg(euler.y), deg(euler.z), segWidth, segDepth, bottom, segLength,
                    o != null ? color(o.getString("c")) : -1));
            prevLength = segLength;
        }

        float[] a = anchor(face, index);
        int rgb = color(t.getString("c"));
        return new Strand(a[0] + clampF(t.getFloat("ox"), -4f, 4f, 0f), a[1] + clampF(t.getFloat("oy"), -4f, 4f, 0f),
                a[2] + clampF(t.getFloat("oz"), -4f, 4f, 0f),
                finite(t.getFloat("rx")), finite(t.getFloat("ry")), finite(t.getFloat("rz")),
                Strand.RotationOrder.XYZ, 1f, 1f, 1f, segs, rgb).withKey(slotKey(face, index))
                .withTip(color(t.getString("tc")));
    }

    private static float clampF(float v, float min, float max, float fallback) {
        if (!Float.isFinite(v)) return fallback;
        return v < min ? min : Math.min(v, max);
    }

    private static float finite(float v) {
        return Float.isFinite(v) ? v % 360f : 0f;
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static float deg(float rad) {
        return (float) Math.toDegrees(rad);
    }

    private static int intOf(CompoundTag t, String k, String longK, int def) {
        return t.contains(k) ? t.getInt(k) : t.contains(longK) ? t.getInt(longK) : def;
    }

    private static float floatOf(CompoundTag t, String k, String longK, float def) {
        return t.contains(k) ? t.getFloat(k) : t.contains(longK) ? t.getFloat(longK) : def;
    }

    private static int color(@Nullable String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') return -1;
        try {
            return Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Base N y deflate ─────────────────────────────────────────────────────

    static byte[] fromBase(String digits, String alphabet) throws HairCodeException {
        BigInteger radix = BigInteger.valueOf(alphabet.length());
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < digits.length(); i++) {
            int v = alphabet.indexOf(digits.charAt(i));
            if (v < 0) throw new HairCodeException("bad_char", String.valueOf(digits.charAt(i)));
            value = value.multiply(radix).add(BigInteger.valueOf(v));
        }
        byte[] b = value.toByteArray();
        // toByteArray añade un 0 de signo cuando el bit alto está puesto: no es parte de los datos.
        if (b.length > 1 && b[0] == 0) {
            byte[] t = new byte[b.length - 1];
            System.arraycopy(b, 1, t, 0, t.length);
            return t;
        }
        return b;
    }

    /** Bytes -> dígitos (inverso de fromBase). Público para los gametests. */
    public static String toBase(byte[] bytes, String alphabet) {
        BigInteger value = new BigInteger(1, bytes);
        if (value.signum() == 0) return String.valueOf(alphabet.charAt(0));
        BigInteger radix = BigInteger.valueOf(alphabet.length());
        StringBuilder sb = new StringBuilder();
        while (value.signum() > 0) {
            BigInteger[] qr = value.divideAndRemainder(radix);
            sb.append(alphabet.charAt(qr[1].intValue()));
            value = qr[0];
        }
        return sb.reverse().toString();
    }

    static byte[] inflate(byte[] data) throws HairCodeException {
        Inflater inf = new Inflater(true);
        try {
            inf.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    throw new HairCodeException("truncated");
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_INFLATED_BYTES) throw new HairCodeException("too_big");
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new HairCodeException("corrupt").withCause(e);
        } finally {
            inf.end();
        }
    }

    // ── Escritura (solo para generar códigos sintéticos propios en los gametests) ───

    /**
     * Código a partir de un NBT. {@code version} 1 (base62), 4 o 5 (base64url como número), 22
     * (base64url de los bytes, DMZ 2.2). No se usa para
     * exportar nada: los peinados de Zenkai no viajan en este formato.
     */
    public static String encode(CompoundTag root, int version, boolean fullSet) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(raw)) {
                NbtIo.write(root, dos);
            }
            Deflater def = new Deflater(9, true);
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try {
                def.setInput(raw.toByteArray());
                def.finish();
                byte[] buf = new byte[4096];
                while (!def.finished()) packed.write(buf, 0, def.deflate(buf));
            } finally {
                def.end();
            }
            String prefix = "DMZ" + (fullSet ? "F" : "") + version + ":";
            if (version == 22) return prefix + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
            String alphabet = version == 1 ? BASE62 : BASE64URL;
            return prefix + toBase(packed.toByteArray(), alphabet);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String stripWhitespace(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }
}
