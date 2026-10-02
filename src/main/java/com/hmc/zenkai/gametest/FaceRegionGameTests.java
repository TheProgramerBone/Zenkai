package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.face.FaceRegion;
import com.hmc.zenkai.feature.race.face.FaceRegions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Región de cara por modelo (FaceRegions, 2026-10-01): los rasgos se pintan en la cara canónica
 * (16,16)-(31,31) y se recolocan a la región de cada modelo — traslación si mide 16×16, escalado
 * por vecino más cercano si no. Solo la parte pura (FaceRegion.sourceTexel y la tabla manual);
 * la generación de texturas es de cliente.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FaceRegionGameTests {
    private FaceRegionGameTests() {}

    private static boolean at(int[] t, int x, int y) {
        return t != null && t[0] == x && t[1] == y;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void regionMapsCanonicalFace(GameTestHelper helper) {
        FaceRegion c = FaceRegion.CANONICAL;
        helper.assertTrue(c.isCanonical() && c.width() == 16 && c.height() == 16, "canónica = 16×16 en (16,16)");
        helper.assertTrue(at(c.sourceTexel(16, 16), 16, 16) && at(c.sourceTexel(31, 31), 31, 31)
                && at(c.sourceTexel(23, 20), 23, 20), "canónica = identidad");
        helper.assertTrue(c.sourceTexel(15, 16) == null && c.sourceTexel(16, 32) == null, "fuera de la región = null");

        // Arcosiano: misma medida, otra esquina → traslación exacta (lo que antes hacía a mano
        // mouth_1_arcosian.png, con la boca de (22,28) en (22,60)).
        FaceRegion arc = new FaceRegion(16, 48, 31, 63);
        helper.assertTrue(at(arc.sourceTexel(22, 60), 22, 28), "traslación (16,48): boca legacy en su sitio");
        helper.assertTrue(at(arc.sourceTexel(31, 63), 31, 31), "traslación: última esquina");

        // Región de 8 filas (p. ej. solo la franja bajo un casco): una fila de cada dos.
        FaceRegion half = new FaceRegion(16, 56, 31, 63);
        helper.assertTrue(at(half.sourceTexel(16, 56), 16, 16) && at(half.sourceTexel(16, 57), 16, 18)
                && at(half.sourceTexel(16, 63), 16, 30), "8 filas: toma filas alternas");
        // Región más grande (32×32 en una textura de 512): cada texel canónico ocupa 2×2.
        FaceRegion big = new FaceRegion(32, 32, 63, 63, 512);
        helper.assertTrue(at(big.sourceTexel(32, 32), 16, 16) && at(big.sourceTexel(33, 33), 16, 16)
                && at(big.sourceTexel(34, 35), 17, 17), "32×32: cada texel ocupa 2×2");

        boolean threw = false;
        try { new FaceRegion(10, 10, 5, 20); } catch (IllegalArgumentException e) { threw = true; }
        helper.assertTrue(threw, "una región al revés debe fallar al arrancar, no pintar basura");
        threw = false;
        try { new FaceRegion(250, 250, 270, 270); } catch (IllegalArgumentException e) { threw = true; }
        helper.assertTrue(threw, "una región fuera de la textura debe fallar al arrancar");

        // Tabla manual: la cabeza arcosiana tiene entrada; un modelo sin entrada no.
        FaceRegion manualArc = FaceRegions.manual(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                "geo/races/arcosian_first_form_player.geo.json"));
        helper.assertTrue(arc.equals(manualArc), "arcosian_first_form_player → (16,48)-(31,63)");
        helper.assertTrue(FaceRegions.manual(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                "geo/races/human_player.geo.json")) == null, "human_player sin entrada (cae a la lectura del modelo)");
        helper.assertTrue(FaceRegions.manual(null) == null, "modelo null → sin entrada");
        helper.succeed();
    }
}
