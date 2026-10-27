package com.hmc.zenkai.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.hair.HairPacks;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import com.hmc.zenkai.feature.race.hair.strand.DbcHairCodec;
import com.hmc.zenkai.feature.race.hair.strand.DmzHairCodec;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.feature.race.hair.strand.HairCodes;
import com.hmc.zenkai.feature.race.hair.strand.HairPackWriter;
import com.hmc.zenkai.feature.race.hair.strand.Strand;
import com.hmc.zenkai.feature.race.hair.strand.StrandGeoWriter;
import com.hmc.zenkai.feature.race.hair.strand.StrandLimits;
import com.hmc.zenkai.feature.race.hair.strand.StrandSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/**
 * Importador de pelo DMZ/DBC (formatos en .claude/pendiente/pelo-importado-dmz-dbc.md §1-3).
 *
 * Solo códigos SINTÉTICOS generados aquí: ningún preset de DragonMineZ ni de Dragon Block C entra
 * en el repo (licencias, §6 del plan). La fidelidad con códigos reales se comprueba a mano fuera
 * del repo.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StrandCodecGameTests {
    private StrandCodecGameTests() {}

    // ── DMZ ──────────────────────────────────────────────────────────────────

    private static CompoundTag dmzStrand(int id, int length) {
        CompoundTag t = new CompoundTag();
        t.putInt("i", id);
        t.putInt("l", length);
        return t;
    }

    /** Peinado propio de prueba: 1 mechón delante (3 cubos) y 1 arriba (2 cubos, más ancho y con color). */
    private static CompoundTag dmzHair(String name, float frontRx) {
        CompoundTag hair = new CompoundTag();
        hair.putInt("v", 5);
        hair.putString("n", name);
        hair.putString("gc", "#000000");
        ListTag front = new ListTag();
        CompoundTag f = dmzStrand(0, 3);
        f.putFloat("rx", frontRx);
        f.putFloat("cx", 15f);
        front.add(f);
        hair.put("F", front);
        ListTag top = new ListTag();
        CompoundTag t = dmzStrand(4 * 100 + 5, 2);
        t.putFloat("cw", 3f);
        t.putString("c", "#FF0000");
        top.add(t);
        hair.put("T", top);
        return hair;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void dmzCodesRoundTrip(GameTestHelper helper) {
        try {
            for (int version : new int[]{1, 4, 5}) {
                String code = DmzHairCodec.encode(dmzHair("Prueba", -90f), version, false);
                helper.assertTrue(code.startsWith("DMZ" + version + ":"), "prefijo v" + version);
                List<Component> warns = new ArrayList<>();
                StrandSet.Variants v = DmzHairCodec.decode(code, warns::add);
                StrandSet s = v.base();
                helper.assertTrue(!v.hasForms(), "un código suelto no trae formas");
                helper.assertTrue("Prueba".equals(s.name()), "nombre del código");
                helper.assertTrue(s.strands().size() == 2 && s.cubeCount() == 5, "v" + version + ": 2 mechones / 5 cubos, hay "
                        + s.strands().size() + " / " + s.cubeCount());
                Strand front = s.strands().get(0);
                helper.assertTrue(front.ax() == -3f && front.ay() == 7.25f && front.az() == -4f,
                        "ancla de la ranura 0 de la cara frontal");
                helper.assertTrue(front.rx() == -90f && front.order() == Strand.RotationOrder.XYZ, "rotación absoluta, orden X·Y·Z");
                Strand top = s.strands().get(1);
                helper.assertTrue(top.ax() == -1f && top.ay() == 7.85f && top.az() == -1f, "ancla de la ranura 5 de arriba (fila 1, columna 1)");
                helper.assertTrue(top.segments().get(0).w() == 3f, "ancho de cubo cw");
                helper.assertTrue(Math.abs(top.segments().get(1).w() - 3f * 0.85f) < 1e-5, "cada cubo al 85% del anterior");
                helper.assertTrue(top.rgb() == 0xFF0000, "color por mechón leído");
                helper.assertTrue(!warns.isEmpty(), "aviso de colores por mechón");
                // Un segmento curvado solapa con el anterior (y0 < 0), el primero no.
                helper.assertTrue(front.segments().get(0).y0() == 0f && front.segments().get(1).y0() < 0f, "solape entre cubos");
            }

            // Juego de formas: base + SSJ, sin SSJ2/SSJ3.
            CompoundTag set = new CompoundTag();
            set.put("B", dmzHair("Base", -90f));
            set.put("S", dmzHair("Base", -60f));
            String full = DmzHairCodec.encode(set, 5, true);
            helper.assertTrue(full.startsWith("DMZF5:"), "prefijo de juego");
            HairCodes.Decoded d = HairCodes.decode(full);
            helper.assertTrue(d.format() == HairCodes.Format.DMZ, "formato detectado");
            helper.assertTrue(d.variants().ssj() != null && d.variants().ssj2() == null && d.variants().ssj3() == null,
                    "juego: base + SSJ");
            helper.assertTrue(d.variants().ssj().strands().get(0).rx() == -60f, "la variante SSJ es la suya");

            // Nombres largos de versiones antiguas (Base/SSJ) también valen.
            CompoundTag longSet = new CompoundTag();
            longSet.put("Base", dmzHair("L", -90f));
            longSet.put("SSJ3", dmzHair("L", -30f));
            HairCodes.Decoded dl = HairCodes.decode(DmzHairCodec.encode(longSet, 4, true));
            helper.assertTrue(dl.variants().ssj3() != null && dl.variants().ssj() == null, "claves largas");
        } catch (HairCodeException e) {
            helper.fail("código propio ilegible: " + e.getMessage());
        }
        helper.succeed();
    }

    // ── DBC ──────────────────────────────────────────────────────────────────

    private static String dbcStrand(int l, int x, int y, int z, int b, int p, int t) {
        return String.format("%02d%02d%02d%02d%02d%02d%02d", l, x, y, z, b, p, t);
    }

    /** Peinado v6/v7 (DMZ 2.2): un mechón arriba de 3 segmentos con curva y un ajuste en el segmento 1. */
    private static CompoundTag dmz22Hair(int version) {
        CompoundTag hair = new CompoundTag();
        hair.putInt("v", version);
        hair.putString("n", "Nuevo");
        CompoundTag s = new CompoundTag();
        s.putInt("i", 400);
        s.putInt("sg", 3);
        s.putFloat("ln", 6f);
        s.putFloat("w", 3f);
        s.putFloat("tp", 0.5f);
        s.putFloat("ox", 1f);
        s.putFloat("bx", 40f);
        ListTag so = new ListTag();
        CompoundTag o = new CompoundTag();
        o.putInt("k", 1);
        o.putFloat("ox", 1f);
        o.putFloat("ls", 2f);
        so.add(o);
        s.put("so", so);
        ListTag top = new ListTag();
        top.add(s);
        hair.put("T", top);
        return hair;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void dmz22CodesDecode(GameTestHelper helper) {
        try {
            CompoundTag set = new CompoundTag();
            set.put("B", dmz22Hair(7));
            set.put("S4", dmz22Hair(7));
            String code = DmzHairCodec.encode(set, 22, true);
            helper.assertTrue(code.startsWith("DMZF22:"), "prefijo v2.2");
            HairCodes.Decoded d = HairCodes.decode(code);
            helper.assertTrue(d.variants().ssj4() != null && d.variants().ssj() == null, "juego con SSJ4");
            Strand s = d.variants().base().strands().get(0);
            helper.assertTrue(s.ax() == -2f && s.ay() == 7.85f && s.az() == -3f, "ancla de la rejilla + ox");
            helper.assertTrue(s.segments().size() == 3, "sg = 3 segmentos");
            Strand.Segment g0 = s.segments().get(0), g1 = s.segments().get(1), g2 = s.segments().get(2);
            // ln 6 repartido a partes iguales (lr = 1); el ajuste del segmento 1 dobla su largo (ls 2)
            // y, en v7, también el de los siguientes (escala acumulada).
            helper.assertTrue(near(g0.y1(), 2f) && near(g1.y1(), 4f) && near(g2.y1(), 4f), "largos v7: "
                    + g0.y1() + ", " + g1.y1() + ", " + g2.y1());
            helper.assertTrue(near(g0.w(), 3f) && near(g2.w(), 1.5f), "estrechamiento tp^t");
            helper.assertTrue(near(g1.cx(), 20f) && near(g1.tx(), 1f) && near(g1.dy(), 2f), "curva por articulación y desplazamiento");
            helper.assertTrue(g1.y0() == 0f && g2.y0() < 0f, "solape solo sin desplazamiento");

            // v6: escalas absolutas (el segmento 2 no hereda el ls del 1).
            StrandSet v6 = HairCodes.decode(DmzHairCodec.encode(dmz22Hair(6), 22, false)).variants().base();
            helper.assertTrue(near(v6.strands().get(0).segments().get(2).y1(), 2f), "largos v6 absolutos");

            // SSJ4 con variante propia se usa con la forma ssj4; ssj1 sin SSJ cae a la base.
            ResourceLocation ssj4 = com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.model(code,
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ssj4"));
            ResourceLocation ssj1 = com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.model(code,
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ssj1"));
            helper.assertTrue(ssj4 != null && ssj4.getPath().endsWith("/ssj4"), "ssj4 -> SSJ4");
            helper.assertTrue(ssj1 != null && ssj1.getPath().endsWith("/base"), "ssj1 sin SSJ -> base");
        } catch (HairCodeException e) {
            helper.fail("código v2.2 propio ilegible: " + e.getMessage());
        }
        helper.succeed();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }

    /** Rampa de tonos (HairRampColors): el base intacto, sombras más oscuras, luces más claras. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void hairRampShades(GameTestHelper helper) {
        for (int base : new int[]{0x1A1A1A, 0xFFE55C, 0x6B3A1E, 0x4FA3E0}) {
            int dark = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.shade(base, -1f);
            int light = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.shade(base, 1f);
            helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.HairRampColors.shade(base, 0f) == base,
                    String.format("%06X: el tono 0 es el color tal cual", base));
            helper.assertTrue(luma(dark) < luma(base) && luma(light) > luma(base),
                    String.format("%06X: sombra %06X y luz %06X", base, dark, light));
        }
        // El negro tiene luces visibles (no es una mancha) y el dorado no sombrea hacia el verde.
        helper.assertTrue(luma(com.hmc.zenkai.feature.race.hair.strand.HairRampColors.shade(0x1A1A1A, 1f)) > 60, "negro con luces");
        // El negro puro se aclara a un gris muy oscuro; un color claro no se toca.
        int liftedBlack = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.lift(0x000000);
        helper.assertTrue(luma(liftedBlack) > 5 && luma(liftedBlack) < 35,
                String.format("negro aclarado %06X", liftedBlack));
        helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.HairRampColors.lift(0xFFE55C) == 0xFFE55C,
                "el dorado no se aclara");
        int goldShadow = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.shade(0xFFE55C, -1f);
        helper.assertTrue(((goldShadow >> 8) & 0xFF) <= ((goldShadow >> 16) & 0xFF),
                String.format("sombra del dorado %06X: G <= R (ocre, no verde)", goldShadow));
        helper.succeed();
    }

    /**
     * Paso de una forma a otra (StrandBlend): emparejado por ranura, crecer/encoger lo que solo está
     * en una, relleno de segmentos, ángulos por el camino corto; mezcla de color en Oklab.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void strandBlendMorphs(GameTestHelper helper) {
        Strand shared = plain(2, 0f).withKey(5), sharedLong = new Strand(0f, 8f, 0f, 350f, 0f, 0f,
                Strand.RotationOrder.XYZ, 1f, 1f, 1f, plain(4, 0f).segments(), -1, 5);
        Strand onlyA = plain(1, 0f).withKey(7), onlyB = plain(3, 0f).withKey(9);
        StrandSet a = new StrandSet("a", List.of(shared, onlyA)), b = new StrandSet("b", List.of(sharedLong, onlyB));
        helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.StrandBlend.blend(a, b, 0f) == a, "t = 0 es la de partida");
        helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.StrandBlend.blend(a, b, 1f) == b, "t = 1 es la de destino");
        StrandSet mid = com.hmc.zenkai.feature.race.hair.strand.StrandBlend.blend(a, b, 0.5f);
        helper.assertTrue(mid.strands().size() == 3, "3 mechones (1 emparejado + 1 que se va + 1 que llega)");
        Strand m = mid.strands().get(0);
        helper.assertTrue(m.key() == 5 && m.segments().size() == 4, "emparejado por ranura, con relleno hasta 4 segmentos");
        helper.assertTrue(near(m.rx(), -5f) || near(m.rx(), 355f), "0 -> 350 grados por el camino corto: " + m.rx());
        helper.assertTrue(near(m.segments().get(3).y1(), 1f), "el segmento de relleno va por la mitad de su largo");
        Strand going = mid.strands().get(1), coming = mid.strands().get(2);
        helper.assertTrue(going.key() == 7 && near(going.segments().get(0).y1(), 1f), "el que se va, a medio encoger");
        helper.assertTrue(coming.key() == 9 && near(coming.segments().get(0).w(), 1f), "el que llega, a medio crecer");

        // Ids de modelo de los pasos (CustomHairCodes): ida y vuelta.
        helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.blendStep(0.5f)
                == com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.BLEND_STEPS / 2, "paso a mitad");
        helper.assertTrue(com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.isBlend(
                ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.MODEL_NAMESPACE,
                        "abc/base_to_ssj_6")), "id de paso reconocido");

        // Color: negro -> amarillo SSJ pasa por un castaño dorado (R >= G > B), no por un verde oliva.
        int half = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.mix(0x1A1A1A, 0xFFE55C, 0.5f);
        int r = (half >> 16) & 0xFF, g = (half >> 8) & 0xFF, bl = half & 0xFF;
        helper.assertTrue(r >= g && g > bl && luma(half) > luma(0x1A1A1A) && luma(half) < luma(0xFFE55C),
                String.format("mitad negro/dorado %06X", half));
        helper.succeed();
    }

    /**
     * Colores propios de los mechones (StrandColors): receta por segmento (segmento > mechón ->
     * punta), columna 0 = color de pelo, UV del .geo.json en su columna y peso 0 = todo de un color.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void strandColorsPalette(GameTestHelper helper) {
        Strand plainS = plain(2, 0f);
        Strand black = new Strand(0f, 8f, 0f, 0f, 0f, 0f, Strand.RotationOrder.XYZ, 1f, 1f, 1f,
                plain(2, 0f).segments(), 0x1A1A1A, 3);
        List<Strand.Segment> tipped = new ArrayList<>(plain(3, 0f).segments());
        Strand.Segment g = tipped.get(1);
        tipped.set(1, new Strand.Segment(g.tx(), g.dy(), g.tz(), g.cx(), g.cy(), g.cz(), g.w(), g.d(), g.y0(), g.y1(), 0xFF0000));
        Strand tip = new Strand(0f, 8f, 0f, 0f, 0f, 0f, Strand.RotationOrder.XYZ, 1f, 1f, 1f, tipped, -1, 4, 0xFFFFFF);
        StrandSet set = new StrandSet("c", List.of(plainS, black, tip));
        com.hmc.zenkai.feature.race.hair.strand.StrandColors.Palette p =
                com.hmc.zenkai.feature.race.hair.strand.StrandColors.of(set);
        helper.assertTrue(!p.plain() && p.column(0, 0) == 0 && p.column(0, 1) == 0, "sin color propio: columna 0");
        int blackCol = p.column(1, 0);
        helper.assertTrue(blackCol > 0 && p.column(1, 1) == blackCol, "mechón negro: una columna propia");
        helper.assertTrue(p.column(2, 0) == 0, "raíz de un mechón con punta: color de pelo");
        int red = p.column(2, 1), white = p.column(2, 2);
        helper.assertTrue(red > 0 && red != blackCol && white > 0 && white != red, "segmento rojo y punta blanca");
        int[] on = p.resolve(0x335577, 1f), off = p.resolve(0x335577, 0f);
        helper.assertTrue(on[0] == 0x335577 && on[blackCol] == 0x1A1A1A && on[red] == 0xFF0000 && on[white] == 0xFFFFFF,
                "encendidos: los colores del código");
        boolean allHair = true;
        for (int c : off) allHair &= c == 0x335577;
        helper.assertTrue(allHair, "apagados (o forma que tiñe): todo del color de pelo");

        // El .geo.json apunta la U de cada cubo a su columna.
        JsonObject geo = StrandGeoWriter.write("colores", set).geo();
        java.util.Set<Integer> us = new java.util.TreeSet<>();
        for (JsonElement bone : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            JsonArray cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
            if (cubes == null) continue;
            for (JsonElement c : cubes) {
                us.add(c.getAsJsonObject().getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv").get(0).getAsInt());
            }
        }
        helper.assertTrue(us.equals(new java.util.TreeSet<>(List.of(0, blackCol, red, white))), "columnas en el geo: " + us);
        helper.succeed();
    }

    private static int luma(int rgb) {
        return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void dbcStringsDecode(GameTestHelper helper) {
        try {
            StringBuilder g2 = new StringBuilder();
            g2.append(dbcStrand(50, 50, 50, 50, 60, 50, 18)); // ranura 0 (frente), 3 segmentos
            for (int i = 1; i < 56; i++) g2.append(i == 45 ? dbcStrand(99, 40, 50, 60, 50, 50, 60) : "00000000000000");
            g2.append("20");
            helper.assertTrue(g2.length() == DbcHairCodec.G2_LENGTH, "cadena G2 de prueba mal armada");

            HairCodes.Decoded d = HairCodes.decode(g2.toString());
            helper.assertTrue(d.format() == HairCodes.Format.DBC, "786 dígitos = DBC");
            StrandSet s = d.variants().base();
            helper.assertTrue(s.strands().size() == 2, "2 mechones (los de largo 0 no cuentan), hay " + s.strands().size());
            Strand front = s.strands().get(0);
            helper.assertTrue(front.segments().size() == 3, "l=50: base + segmentos 1 y 2");
            // ModelRenderer (-2.999, -7.5, -3.999) -> render (2.999, 7.5, -3.999): X e Y cambian de signo.
            helper.assertTrue(Math.abs(front.ax() - 2.999f) < 1e-4 && Math.abs(front.ay() - 7.5f) < 1e-4
                    && Math.abs(front.az() + 3.999f) < 1e-4, "ancla DBC convertida a render");
            helper.assertTrue(front.order() == Strand.RotationOrder.ZYX, "orden Z·Y·X");
            // B=60 -> curva 0,3 rad·0,3 en X, que en render cambia de signo.
            helper.assertTrue(front.segments().get(1).cx() < 0f && front.segments().get(1).cz() == 0f,
                    "frente: curvatura en X, con signo invertido");
            // Caja base de ModelRenderer y de -1 a 2 -> en render de -2 a 1 (crece hacia -Y antes de rotar).
            helper.assertTrue(front.segments().get(0).y0() == -2f && front.segments().get(0).y1() == 1f, "caja base reflejada en Y");
            helper.assertTrue(!d.warnings().isEmpty(), "T>18 avisa de que SSJ1/SSJ2 son aproximados");

            // SSJ1/SSJ2 horneados desde el campo T: el mechón 45 (T=60) se abre; el 0 (T=18, sin reacción) solo se
            // alarga con el SSJ2 (l 50 -> 70: aparece el cuarto segmento).
            StrandSet.Variants v = d.variants();
            helper.assertTrue(v.ssj() != null && v.ssj2() != null && v.ssj3() == null && v.ssj4() == null,
                    "DBC trae SSJ y SSJ2 aproximados; SSJ3/SSJ4 quedan a los defaults de Zenkai");
            Strand topBase = v.base().strands().get(1);
            Strand topSsj = v.ssj().strands().get(1);
            helper.assertTrue(Math.abs(topSsj.rx() - topBase.rx()) > 10f, "el mechón con T=60 cambia de rotación en SSJ");
            helper.assertTrue(v.ssj().strands().get(0).segments().size() == 3 && v.ssj2().strands().get(0).segments().size() == 4,
                    "SSJ2 alarga el mechón: 3 -> 4 segmentos");

            // G1 antigua: l de 1 dígito -> l·11-10.
            StringBuilder g1 = new StringBuilder("1505050");
            for (int i = 1; i < 56; i++) g1.append("0505050");
            helper.assertTrue(!HairCodes.decode(g1.toString()).variants().hasForms(),
                    "G1 rellena T=00: sin reacción no hay variantes (defaults de Zenkai)");
            StrandSet old = HairCodes.decode(g1.toString()).variants().base();
            helper.assertTrue(old.strands().size() == 1 && old.strands().get(0).segments().size() == 2, "G1: l=1 -> 2 segmentos");

            expectError(helper, () -> DbcHairCodec.decode("123", w -> {}), "dbc_length");
        } catch (HairCodeException e) {
            helper.fail("cadena propia ilegible: " + e.getMessage());
        }
        helper.succeed();
    }

    // ── Topes y basura ───────────────────────────────────────────────────────

    private static Strand plain(int segments, float v) {
        List<Strand.Segment> segs = new ArrayList<>();
        for (int i = 0; i < segments; i++) segs.add(new Strand.Segment(i == 0 ? 0f : 2f, v, 0f, 0f, 2f, 2f, 0f, 2f));
        return new Strand(0f, 8f, 0f, 0f, 0f, 0f, Strand.RotationOrder.XYZ, 1f, 1f, 1f, segs, -1);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void limitsAndGarbage(GameTestHelper helper) {
        List<Strand> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) many.add(plain(2, 0f));
        List<Component> warns = new ArrayList<>();
        StrandSet clamped = StrandLimits.clamp(new StrandSet(null, many), warns::add);
        helper.assertTrue(clamped.strands().size() == StrandLimits.MAX_STRANDS && !warns.isEmpty(), "300 mechones -> 80 con aviso");

        List<Strand> longOnes = new ArrayList<>();
        for (int i = 0; i < 20; i++) longOnes.add(plain(20, 5f));
        StrandSet cut = StrandLimits.clamp(new StrandSet(null, longOnes), w -> {});
        helper.assertTrue(cut.cubeCount() == StrandLimits.MAX_CUBES, "tope de cubos, hay " + cut.cubeCount());
        for (Strand s : cut.strands()) helper.assertTrue(s.segments().size() <= StrandLimits.MAX_SEGMENTS, "tope por mechón");

        Strand nan = new Strand(Float.NaN, 1e9f, 0f, Float.POSITIVE_INFINITY, 0f, 0f, Strand.RotationOrder.XYZ,
                -2f, 0f, 100f, List.of(new Strand.Segment(Float.NaN, 0f, 0f, 0f, 2f, 2f, 0f, 2f)), -1);
        Strand sane = StrandLimits.clamp(new StrandSet(null, List.of(nan)), w -> {}).strands().get(0);
        helper.assertTrue(sane.ax() == 0f && sane.ay() == StrandLimits.MAX_EXTENT && sane.rx() == 0f
                && sane.sx() == 2f && sane.sy() > 0f && sane.sz() == 8f && sane.segments().get(0).dy() == 0f,
                "NaN, infinitos y escalas absurdas se acotan");

        expectError(helper, () -> HairCodes.decode("hola"), "unknown_format");
        expectError(helper, () -> HairCodes.decode("   "), "empty");
        expectError(helper, () -> HairCodes.decode("DMZ9:AAAA"), "dmz_version");
        expectError(helper, () -> HairCodes.decode("DMZ5:ab$cd"), "bad_char");
        expectError(helper, () -> HairCodes.decode("DMZ5:" + "A".repeat(DmzHairCodec.MAX_CODE_LENGTH)), "too_long");
        expectError(helper, () -> HairCodes.decode("DMZ5:Zm9vYmFy"), null); // basura cualquiera: error controlado
        // "Zip bomb": 1 MB de ceros deflatados en unos pocos KB.
        Deflater def = new Deflater(9, true);
        def.setInput(new byte[1024 * 1024]);
        def.finish();
        byte[] buf = new byte[64 * 1024];
        int n = def.deflate(buf);
        def.end();
        byte[] bomb = java.util.Arrays.copyOf(buf, n);
        expectError(helper, () -> HairCodes.decode("DMZ5:" + DmzHairCodec.toBase(bomb, DmzHairCodec.BASE64URL)), "too_big");
        // Peinado sin mechones visibles.
        CompoundTag empty = new CompoundTag();
        empty.putInt("v", 5);
        expectError(helper, () -> HairCodes.decode(DmzHairCodec.encode(empty, 5, false)), "no_strands");
        helper.succeed();
    }

    private interface Throwing {
        void run() throws Exception;
    }

    /** Debe fallar con HairCodeException (y esa clave, si se da), nunca con otra excepción. */
    private static void expectError(GameTestHelper helper, Throwing r, String key) {
        String got;
        try {
            r.run();
            got = null;
        } catch (HairCodeException e) {
            got = e.getMessage();
        } catch (Exception e) {
            helper.fail("excepción no controlada: " + e);
            return;
        }
        helper.assertTrue(got != null, "debía fallar" + (key == null ? "" : " con " + key));
        helper.assertTrue(key == null || key.equals(got), "esperaba " + key + ", fue " + got);
    }

    // ── Convertidor: lo que GeckoLib reconstruye del archivo == la cadena de matrices ──────

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void geoWriterMatchesStrandChain(GameTestHelper helper) {
        List<Strand.Segment> segs = List.of(
                new Strand.Segment(0f, 0f, 0f, 0f, 2.2f, 1.8f, -1f, 2f),
                new Strand.Segment(1.5f, 20f, -10f, 35f, 2f, 2f, -0.2f, 3f),
                new Strand.Segment(2.5f, -15f, 30f, 5f, 1.6f, 1.4f, 0f, 2.5f),
                // Desplazamiento lateral (ajustes por segmento del formato v7 de DMZ).
                new Strand.Segment(0.7f, 1.2f, -0.4f, 10f, 20f, -30f, 1.2f, 1.2f, -0.1f, 2f));
        StrandSet set = new StrandSet(null, List.of(
                new Strand(2f, 7.25f, -4f, -70f, 25f, 10f, Strand.RotationOrder.XYZ, 1.5f, 1.5f, 1.5f, segs, -1),
                new Strand(-3f, 7.5f, 3f, 40f, -80f, 120f, Strand.RotationOrder.ZYX, 1f, 1f, 1f, segs, -1),
                // Casi en gimbal (Y = 90): la descomposición debe seguir siendo exacta.
                new Strand(0f, 8f, 0f, 0f, 0f, 0f, Strand.RotationOrder.XYZ, 1f, 1f, 1f,
                        List.of(new Strand.Segment(0f, 0f, 90f, 0f, 2f, 2f, 0f, 2f),
                                new Strand.Segment(2f, 90f, 0f, 0f, 2f, 2f, 0f, 2f)), -1)));
        StrandGeoWriter.Result r = StrandGeoWriter.write("test", set);
        helper.assertTrue(r.cubes() == 10 && r.approximated() == 0, "10 cubos exactos, hay " + r.cubes() + " (" + r.approximated() + " aprox.)");

        JsonObject geo = r.geo().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonArray bones = geo.getAsJsonArray("bones");
        List<String> names = new ArrayList<>();
        for (JsonElement b : bones) names.add(b.getAsJsonObject().get("name").getAsString());
        JsonArray cubes = new JsonArray();
        for (JsonElement b : bones) {
            JsonObject o = b.getAsJsonObject();
            if (o.has("parent")) helper.assertTrue(names.contains(o.get("parent").getAsString()), "padre inexistente");
            if (o.get("name").getAsString().startsWith(StrandGeoWriter.STRAND_BONE)) {
                helper.assertTrue(o.get("parent").getAsString().equals("armorHead"), "los mechones cuelgan de armorHead");
                cubes.addAll(o.getAsJsonArray("cubes"));
            }
        }
        helper.assertTrue(cubes.size() == 10, "un hueso por mechón con sus cubos, hay " + cubes.size());

        int k = 0;
        for (Strand s : set.strands()) {
            Matrix4d m = new Matrix4d().translate(s.ax(), s.ay(), s.az());
            rot(m, s.order(), s.rx(), s.ry(), s.rz());
            m.scale(s.sx(), s.sy(), s.sz());
            for (Strand.Segment g : s.segments()) {
                m.translate(g.tx(), g.dy(), g.tz());
                rot(m, s.order(), g.cx(), g.cy(), g.cz());
                double err = maxCornerError(m, g, cubes.get(k++).getAsJsonObject());
                helper.assertTrue(err < 2e-3, "cubo " + (k - 1) + ": esquinas a " + err + " px de su sitio");
            }
        }
        helper.succeed();
    }

    private static void rot(Matrix4d m, Strand.RotationOrder order, float x, float y, float z) {
        double rx = Math.toRadians(x), ry = Math.toRadians(y), rz = Math.toRadians(z);
        if (order == Strand.RotationOrder.XYZ) m.rotateX(rx).rotateY(ry).rotateZ(rz);
        else m.rotateZ(rz).rotateY(ry).rotateX(rx);
    }

    /**
     * Reconstruye el cubo como lo hace GeckoLib 4.8.4 (X invertida al cargar, rotación (-rx,-ry,+rz)
     * aplicada Z·Y·X alrededor del pivote) y lo compara con la caja esperada, esquina a esquina.
     */
    private static double maxCornerError(Matrix4d expected, Strand.Segment g, JsonObject cube) {
        double[] o = arr(cube, "origin"), sz = arr(cube, "size"), pv = arr(cube, "pivot"), rt = arr(cube, "rotation");
        Matrix4d gl = new Matrix4d()
                .translate(-pv[0], pv[1], pv[2])
                .rotateZ(Math.toRadians(rt[2])).rotateY(Math.toRadians(-rt[1])).rotateX(Math.toRadians(-rt[0]))
                .translate(pv[0], -pv[1], -pv[2]);
        double worst = 0;
        for (int i = 0; i < 8; i++) {
            double lx = (i & 1) == 0 ? -g.w() / 2 : g.w() / 2;
            double ly = (i & 2) == 0 ? g.y0() : g.y1();
            double lz = (i & 4) == 0 ? -g.d() / 2 : g.d() / 2;
            Vector3d want = expected.transformPosition(new Vector3d(lx, ly, lz)).add(0, 24, 0);
            // Esquina equivalente del cubo del archivo, en espacio de render (x = -x del archivo).
            double rx0 = -(o[0] + sz[0]), rx1 = -o[0];
            Vector3d got = gl.transformPosition(new Vector3d(
                    (i & 1) == 0 ? rx0 : rx1,
                    (i & 2) == 0 ? o[1] : o[1] + sz[1],
                    (i & 4) == 0 ? o[2] : o[2] + sz[2]));
            worst = Math.max(worst, want.distance(got));
        }
        return worst;
    }

    private static double[] arr(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    // ── Manifiesto de pack (zenkai_hair_packs) ───────────────────────────────

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void hairPackManifest(GameTestHelper helper) {
        try {
            String dmz = DmzHairCodec.encode(dmzHair("Mi Pelo", -90f), 5, true);
            StringBuilder dbc = new StringBuilder();
            dbc.append(dbcStrand(50, 50, 50, 50, 60, 50, 18));
            for (int i = 1; i < 56; i++) dbc.append("00000000000000");
            dbc.append("20");

            JsonObject pack = new JsonObject();
            pack.addProperty("name", "Pack mixto");
            pack.add("races", JsonParser.parseString("[\"human\",\"saiyan\"]"));
            JsonArray styles = new JsonArray();
            styles.add(JsonParser.parseString("{\"id\":\"a\",\"format\":\"dmz\",\"name\":\"A\",\"code\":\"" + dmz
                    + "\",\"form_overrides\":{\"zenkai:ssj1\":\"b\"}}"));
            styles.add(JsonParser.parseString("{\"id\":\"b\",\"format\":\"dbc\",\"code\":\"" + dbc + "\"}"));
            // declara dbc pero el código es DMZ -> se descarta
            styles.add(JsonParser.parseString("{\"id\":\"mala\",\"format\":\"dbc\",\"code\":\"" + dmz + "\"}"));
            // "auto" detecta; formato inventado -> se descarta
            styles.add(JsonParser.parseString("{\"id\":\"auto\",\"code\":\"" + dbc + "\"}"));
            styles.add(JsonParser.parseString("{\"id\":\"rara\",\"format\":\"xyz\",\"code\":\"" + dbc + "\"}"));
            // peinado de cubos: "model" en lugar de "code" (el rectángulo original del ejemplo)
            styles.add(JsonParser.parseString("{\"id\":\"cubos\",\"model\":\"prueba:geo/hair/x.geo.json\"}"));
            pack.add("styles", styles);

            ResourceLocation packId = ResourceLocation.fromNamespaceAndPath("prueba", "mixto");
            List<String> warns = new ArrayList<>();
            HairPacks.Parsed p = HairPacks.parse(packId, pack, warns::add);
            helper.assertTrue("Pack mixto".equals(p.folder().name()), "nombre de la carpeta");
            helper.assertTrue(p.styles().size() == 4, "a, b, auto y cubos entran; mala y rara no: " + p.styles().size());
            HairStyleDef geo = p.styles().get(3);
            helper.assertTrue(geo.representation() == HairStyleDef.Representation.GEO && geo.model() != null
                    && geo.texture().getPath().endsWith("flat_white.png"), "estilo de cubos con la textura blanca por defecto");
            helper.assertTrue(warns.size() == 2, "dos avisos: " + warns);
            HairStyleDef a = p.styles().get(0);
            helper.assertTrue(a.id().toString().equals("prueba:mixto/a") && packId.equals(a.folder()),
                    "id con el pack y carpeta del pack: " + a.id());
            helper.assertTrue(a.allows(com.hmc.zenkai.feature.Race.SAIYAN) && !a.allows(com.hmc.zenkai.feature.Race.NAMEKIAN),
                    "las razas del pack se heredan");
            helper.assertTrue(a.formOverrides().values().stream().allMatch(
                    v -> v.toString().equals("prueba:mixto/b")), "override local resuelto al mismo pack");
            helper.assertTrue("A".equals(a.displayName().getString()), "nombre literal del peinado");
            helper.assertTrue(a.representation() == HairStyleDef.Representation.CODE, "siempre por código");
        } catch (Exception e) {
            helper.fail("hair pack manifest: " + e);
        }
        helper.succeed();
    }

    // ── Carpeta de hair_packs ────────────────────────────────────────────────

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void packWriterLayoutAndSafety(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("zenkai_hair_packs_test");
            helper.assertTrue("goku_ssj".equals(HairPackWriter.sanitize("../Goku SSJ!")), "nombre saneado");
            helper.assertTrue(HairPackWriter.sanitize("../..") == null, "nada utilizable -> null");
            helper.assertTrue(HairPackWriter.nextDefaultName(root).equals("import_1"), "nombre por defecto");

            CompoundTag set = new CompoundTag();
            set.put("B", dmzHair("Mi Pelo", -90f));
            set.put("S", dmzHair("Mi Pelo", -50f));
            set.put("S2", dmzHair("Mi Pelo", -50f)); // igual que SSJ: no genera archivo propio
            HairCodes.Decoded d = HairCodes.decode(DmzHairCodec.encode(set, 5, true));
            HairPackWriter.Report rep = HairPackWriter.write(root, "Mi Pelo", d, false);
            helper.assertTrue(rep.name().equals("mi_pelo") && rep.styles().equals(List.of("mi_pelo", "mi_pelo_ssj")),
                    "estilos escritos: " + rep.styles());
            Path folder = root.resolve("mi_pelo");
            for (String f : new String[]{"pack.mcmeta", "assets/zenkai_hair/geo/hair/mi_pelo.geo.json",
                    "assets/zenkai_hair/geo/hair/mi_pelo_ssj.geo.json", "assets/zenkai_hair/lang/en_us.json",
                    "data/zenkai_hair/zenkai_hair_styles/mi_pelo.json", "data/zenkai_hair/zenkai_hair_styles/mi_pelo_ssj.json"}) {
                helper.assertTrue(Files.isRegularFile(folder.resolve(f)), "falta " + f);
            }

            // La entrada del catálogo la entiende el lector real del catálogo.
            JsonObject entry = JsonParser.parseString(Files.readString(
                    folder.resolve("data/zenkai_hair/zenkai_hair_styles/mi_pelo.json"))).getAsJsonObject();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HairPackWriter.NAMESPACE, "mi_pelo");
            HairStyleDef def = HairStyleDef.parse(id, entry, w -> {});
            ResourceLocation ssj = ResourceLocation.fromNamespaceAndPath(HairPackWriter.NAMESPACE, "mi_pelo_ssj");
            helper.assertTrue(def.selectable() && def.model().getNamespace().equals(HairPackWriter.NAMESPACE),
                    "base elegible y con su geo");
            helper.assertTrue(def.formOverrides().size() == 11 && def.formOverrides().values().stream().allMatch(ssj::equals),
                    "las 11 formas con pelo SSJ (SSJ1-4, Blue, Rosé, sus Evolution, Beast, Legendario y Legendario Pleno) van a la única variante distinta: " + def.formOverrides());
            JsonObject variant = JsonParser.parseString(Files.readString(
                    folder.resolve("data/zenkai_hair/zenkai_hair_styles/mi_pelo_ssj.json"))).getAsJsonObject();
            helper.assertTrue(!HairStyleDef.parse(ssj, variant, w -> {}).selectable(), "la variante no sale en la lista");

            expectError(helper, () -> HairPackWriter.write(root(folder), "mi_pelo", d, false), "exists");
            expectError(helper, () -> HairPackWriter.write(root(folder), "!!!", d, false), "bad_name");
            HairPackWriter.write(root, "mi_pelo", HairCodes.decode(DmzHairCodec.encode(dmzHair("x", -90f), 5, false)), true);
            helper.assertTrue(!Files.exists(folder.resolve("assets/zenkai_hair/geo/hair/mi_pelo_ssj.geo.json")),
                    "replace borra la variante que ya no existe");
        } catch (Exception e) {
            helper.fail("hair_packs: " + e);
        } finally {
            if (root != null) deleteQuietly(root);
        }
        helper.succeed();
    }

    private static Path root(Path folder) {
        return folder.getParent();
    }

    private static void deleteQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // carpeta temporal del sistema: si queda algo, lo limpia el SO
        }
    }
}
