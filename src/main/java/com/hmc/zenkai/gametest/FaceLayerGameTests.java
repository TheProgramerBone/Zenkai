package com.hmc.zenkai.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.feature.race.face.RaceFaceTraits;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Capas genéricas de rasgo facial (S2, 2026-10-01): parseo del JSON (capas, atajo "texture",
 * colores con nombre y fijos, tope de capas), ida y vuelta por red, heterocromía y resolución
 * de color con las sobrescrituras de la forma (eye_rgb / hair_rgb). Lógica pura, sin jugador.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FaceLayerGameTests {
    private FaceLayerGameTests() {}

    private static final ResourceLocation PART_ID = ResourceLocation.fromNamespaceAndPath("test_addon", "eyes/hetero");

    private static FacePartDef parse(FaceCategory category, String json, List<String> warnings) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return FacePartDef.parse(PART_ID, category, o, warnings::add);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void parsesLayersShorthandAndColors(GameTestHelper helper) {
        List<String> warn = new ArrayList<>();
        FacePartDef d = parse(FaceCategory.EYES, """
                { "races": ["human", "saiyan"], "exclude_races": ["saiyan"], "sort": 5,
                  "layers": [
                    {"texture": "test_addon:textures/a.png", "color": "none"},
                    {"texture": "test_addon:textures/l.png", "color": "eye_left"},
                    {"texture": "test_addon:textures/r.png", "color": "EYE_RIGHT"},
                    {"texture": "test_addon:textures/s.png", "color": "#FF8800"},
                    {"texture": "test_addon:textures/x.png"}
                  ] }""", warn);
        helper.assertTrue(d.layers().size() == 5, "5 capas");
        helper.assertTrue(d.layers().get(1).color() == FaceLayer.Color.EYE_LEFT, "eye_left");
        helper.assertTrue(d.layers().get(2).color() == FaceLayer.Color.EYE_RIGHT, "mayúsculas aceptadas");
        helper.assertTrue(d.layers().get(3).color() == FaceLayer.Color.FIXED && d.layers().get(3).fixedRgb() == 0xFF8800,
                "#RRGGBB = color fijo");
        helper.assertTrue(d.layers().get(4).color() == FaceLayer.Color.EYE, "capa sin color = el de la categoría (EYES -> eye)");
        helper.assertTrue(d.allows(Race.HUMAN) && !d.allows(Race.SAIYAN) && !d.allows(Race.MAJIN),
                "races menos exclude_races");
        helper.assertTrue(d.sort() == 5, "sort");

        FacePartDef brows = parse(FaceCategory.EYEBROWS, "{ \"texture\": \"test_addon:textures/b.png\" }", warn);
        helper.assertTrue(brows.layers().size() == 1 && brows.layers().get(0).color() == FaceLayer.Color.HAIR,
                "atajo texture en cejas = una capa con color de pelo");
        helper.assertTrue(brows.allows(Race.HUMAN) && brows.allows(Race.SAIYAN), "sin races = cualquier raza (con cejas)");
        // Namekianos, Arcosianos y Majin no tienen cejas: una ceja nunca es elegible para ellos.
        helper.assertTrue(!brows.allows(Race.NAMEKIAN) && !brows.allows(Race.ARCOSIAN) && !brows.allows(Race.MAJIN),
                "cejas: nunca para Namek/Arcosiano/Majin");

        // "eyebrow": true marca el trazo-ceja de un ojo: se oculta en esas razas; el resto del ojo no.
        FacePartDef browEye = parse(FaceCategory.EYES, """
                { "layers": [ {"texture": "test_addon:textures/w.png", "color": "none"},
                              {"texture": "test_addon:textures/b.png", "color": "hair", "eyebrow": true} ] }""", warn);
        FaceLayer white = browEye.layers().get(0), stroke = browEye.layers().get(1);
        helper.assertTrue(stroke.eyebrow() && !white.eyebrow(), "eyebrow se lee del JSON (defecto false)");
        helper.assertTrue(RaceFaceTraits.shows(Race.HUMAN, FaceCategory.EYES, stroke)
                && !RaceFaceTraits.shows(Race.NAMEKIAN, FaceCategory.EYES, stroke)
                && RaceFaceTraits.shows(Race.NAMEKIAN, FaceCategory.EYES, white), "la ceja de un ojo no se pinta en Namek");
        helper.assertTrue(browEye.allows(Race.MAJIN), "un ojo con ceja sigue siendo elegible en Majin (solo se oculta la ceja)");

        StringBuilder many = new StringBuilder("{ \"layers\": [");
        for (int i = 0; i < 12; i++) many.append(i > 0 ? "," : "").append("{\"texture\": \"test_addon:textures/").append(i).append(".png\"}");
        many.append("] }");
        warn.clear();
        FacePartDef capped = parse(FaceCategory.MOUTH, many.toString(), warn);
        helper.assertTrue(capped.layers().size() == FacePartDef.MAX_LAYERS && !warn.isEmpty(),
                "más de MAX_LAYERS capas: se recorta y avisa");

        boolean threw = false;
        try { parse(FaceCategory.NOSE, "{ \"sort\": 1 }", warn); } catch (Exception e) { threw = true; }
        helper.assertTrue(threw, "sin layers ni texture debe fallar (el cargador salta el archivo)");

        threw = false;
        try { parse(FaceCategory.NOSE, "{ \"layers\": [{\"texture\": \"a:b.png\", \"color\": \"purpura\"}] }", warn); }
        catch (Exception e) { threw = true; }
        helper.assertTrue(threw, "color desconocido debe fallar");

        // race_layers: el mismo rasgo con otras capas en una raza (ojos arcosianos bajo el casco).
        FacePartDef withRace = parse(FaceCategory.EYES, """
                { "layers": [ {"texture": "test_addon:textures/n.png", "color": "eye_left"} ],
                  "race_layers": { "arcosian": [ {"texture": "test_addon:textures/a.png", "color": "eye_right"} ],
                                   "no_existe": [ {"texture": "test_addon:textures/x.png"} ] } }""", warn);
        helper.assertTrue(withRace.layersFor(Race.ARCOSIAN).get(0).texture().getPath().equals("textures/a.png"),
                "Arcosiano usa sus race_layers");
        helper.assertTrue(withRace.layersFor(Race.HUMAN).get(0).texture().getPath().equals("textures/n.png"),
                "una raza sin race_layers usa las capas normales");
        helper.assertTrue(withRace.raceLayers().size() == 1, "una raza desconocida en race_layers se ignora con aviso");

        // Ida y vuelta por red (lo que hace FaceCatalogSyncPacket), con y sin race_layers.
        for (FacePartDef original : new FacePartDef[]{d, withRace, browEye}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            FacePartDef.STREAM_CODEC.encode(buf, original);
            FacePartDef back = FacePartDef.STREAM_CODEC.decode(buf);
            helper.assertTrue(back.equals(original), "FacePartDef debe sobrevivir a la red sin cambios: " + original);
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void heterochromiaAndFormColors(GameTestHelper helper) {
        PlayerVisualAttachment v = new PlayerVisualAttachment();
        v.setEyeColorRgb(0x0000FF);
        v.setHairColorRgb(0x111111);
        v.setSkinColorRgb(0xC08060);
        ResourceLocation t = ResourceLocation.fromNamespaceAndPath("test_addon", "t.png");
        FaceLayer left = new FaceLayer(t, FaceLayer.Color.EYE_LEFT, 0);
        FaceLayer right = new FaceLayer(t, FaceLayer.Color.EYE_RIGHT, 0);
        FaceLayer hair = new FaceLayer(t, FaceLayer.Color.HAIR, 0);

        // Sin heterocromía el derecho sigue al izquierdo.
        helper.assertTrue(right.resolveRgb(v, null) == 0x0000FF, "derecho = izquierdo por defecto");
        v.setEyeColorRightRgb(0x00FF00);
        helper.assertTrue(left.resolveRgb(v, null) == 0x0000FF && right.resolveRgb(v, null) == 0x00FF00,
                "heterocromía: cada ojo su color");
        helper.assertTrue(new FaceLayer(t, FaceLayer.Color.SKIN, 0).resolveRgb(v, null) == 0xC08060, "skin");
        helper.assertTrue(new FaceLayer(t, FaceLayer.Color.NONE, 0).resolveRgb(v, null) == 0xFFFFFF, "none = blanco");
        // Boca/nariz Namek: la piel se oscurece además por la raza (antes apenas se distinguía).
        helper.assertTrue(RaceFaceTraits.shadeSkin(Race.HUMAN, 0xC08060) == 0xC08060, "humano: piel sin cambio");
        int namek = RaceFaceTraits.shadeSkin(Race.NAMEKIAN, 0x2DC31E);
        helper.assertTrue(((namek >> 8) & 0xFF) < 0xC3 && ((namek >> 8) & 0xFF) > 0, "Namek: piel de boca más oscura");

        // NBT: la heterocromía se guarda, y -1 vuelve a "sin heterocromía".
        PlayerVisualAttachment re = new PlayerVisualAttachment();
        re.load(v.save());
        helper.assertTrue(re.getEyeColorRightRgb() == 0x00FF00, "eyeColorRight debe guardarse");
        re.setEyeColorRightRgb(-5);
        helper.assertTrue(re.getEyeColorRightRgb() == -1 && re.resolvedEyeColorRightRgb() == 0x0000FF,
                "negativo = sin heterocromía");
        // Y entra por la lista blanca del packet.
        PlayerVisualAttachment srv = new PlayerVisualAttachment();
        srv.loadCosmeticFromClient(v.save());
        helper.assertTrue(srv.getEyeColorRightRgb() == 0x00FF00, "eyeColorRight debe pasar la lista blanca");

        // La forma manda: con eye_rgb ambos ojos toman el de la forma; con hair_rgb, las cejas.
        FormDef eyeForm = null, hairForm = null;
        for (FormDef f : FormDef.all()) {
            if (eyeForm == null && f.tintsEyes()) eyeForm = f;
            if (hairForm == null && f.tintsHair()) hairForm = f;
        }
        helper.assertTrue(eyeForm != null && hairForm != null, "el datapack debe traer formas con eye_rgb y hair_rgb");
        int formEye = eyeForm.eyeRgb() & 0xFFFFFF;
        helper.assertTrue(left.resolveRgb(v, eyeForm) == formEye && right.resolveRgb(v, eyeForm) == formEye,
                "eye_rgb de la forma pisa los dos ojos (" + eyeForm.id() + ")");
        helper.assertTrue(hair.resolveRgb(v, null) == 0x111111, "cejas = color de pelo del jugador");
        helper.assertTrue(hair.resolveRgb(v, hairForm) == (hairForm.hairRgb() & 0xFFFFFF),
                "cejas siguen el hair_rgb de la forma (" + hairForm.id() + ")");

        helper.succeed();
    }
}
