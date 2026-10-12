package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.FaceBans;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.feature.race.face.LegacyFaceIds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catálogo de rasgos faciales (S1, 2026-10-01): ids con nombre en datapack en vez de "el N-ésimo
 * PNG que encuentre cada cliente". Corre sobre el datapack REAL que carga el gameTestServer.
 * Comprueba la carga, la exclusividad por raza, step/reconcile de FaceBans sobre ids, la
 * migración de saves viejos con índices, y que un catálogo vacío no borra caras guardadas.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FaceCatalogGameTests {
    private FaceCatalogGameTests() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    // Ojos: desde 2026-10-01 SOLO los tres aprobados por el usuario (los eyes_1..5 "legacy" se
    // retiraron). Boca: mouth/neutral sigue siendo la única.
    private static final ResourceLocation MINIMAL = id("eyes/minimal");
    private static final ResourceLocation EXPRESSIVE = id("eyes/expressive");
    private static final ResourceLocation SOFT = id("eyes/soft");
    private static final ResourceLocation MOUTH_1 = id("mouth/neutral");

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void catalogLoadsApprovedPartsAndRaceRules(GameTestHelper helper) {
        for (ResourceLocation r : new ResourceLocation[]{MINIMAL, EXPRESSIVE, SOFT, MOUTH_1}) {
            helper.assertTrue(FaceCatalog.get(r) != null, "falta en el catálogo: " + r);
        }
        helper.assertTrue(FaceCatalog.get(MINIMAL).category() == FaceCategory.EYES, "eyes/minimal debe ser EYES");
        helper.assertTrue(FaceCatalog.get(MOUTH_1).category() == FaceCategory.MOUTH, "mouth/neutral debe ser MOUTH");
        for (Race r : Race.values()) {
            helper.assertTrue(FaceCatalog.get(MINIMAL).isDefaultFor(r), "minimal debe ser el ojo por defecto de " + r);
        }
        // Los ojos antiguos, de vuelta con id numérico y con trazo de cejas; el 2 se FUSIONÓ con
        // minimal (misma silueta con el trazo) y el 3 sigue siendo exclusivo de Majin.
        // Ids con nombre = carpeta (1 narrow, 3 wide, 4 sleepy); el 2 y el 5 (arched, 0.4.1) se
        // fusionaron con minimal.
        for (String n : new String[]{"narrow", "wide", "sleepy"}) {
            var d = FaceCatalog.get(id("eyes/" + n));
            helper.assertTrue(d != null, "falta el ojo antiguo eyes/" + n);
            helper.assertTrue(d.layers().stream().anyMatch(l -> l.color() == FaceLayer.Color.HAIR),
                    "eyes/" + n + ": debe traer el trazo de cejas (capa hair)");
        }
        for (int n = 1; n <= 5; n++) {
            helper.assertTrue(FaceCatalog.get(id("eyes/" + n)) == null, "ya no hay ids numéricos: eyes/" + n);
        }
        helper.assertTrue(FaceCatalog.get(id("eyes/arched")) == null, "eyes/arched se fusionó con minimal");
        helper.assertTrue(LegacyFaceIds.renamed(id("eyes/arched")).equals(MINIMAL)
                && LegacyFaceIds.fromIndex(FaceCategory.EYES, 5).equals(MINIMAL),
                "un save con eyes/arched (o el índice 5) debe cargar minimal");
        // Los vetos se escriben como "exclude_races" (baneado de esa raza), no como lista de razas
        // permitidas (decisión del usuario, 2026-10-10): el único ojo vetado es wide, para Arcosiano.
        helper.assertTrue(FaceBans.isAllowed(Race.MAJIN, FaceCategory.EYES, id("eyes/wide"))
                && FaceBans.isAllowed(Race.HUMAN, FaceCategory.EYES, id("eyes/wide"))
                && !FaceBans.isAllowed(Race.ARCOSIAN, FaceCategory.EYES, id("eyes/wide")), "eyes/wide: vetado solo para Arcosiano");

        // Opción A del usuario (2026-10-01): el trazo oscuro de los ojos sigue al color del pelo
        // (dorado en SSJ) — antes estaba en la capa base con color fijo y no cambiaba al transformarse.
        for (ResourceLocation r : new ResourceLocation[]{MINIMAL, EXPRESSIVE, SOFT}) {
            helper.assertTrue(FaceCatalog.get(r).layers().stream().anyMatch(l -> l.color() == FaceLayer.Color.HAIR),
                    r + ": el trazo (capa brow) debe ir con color hair");
        }
        // Guarda del fallo de S2: con UN iris teñido "eye" la heterocromía no se ve nunca. Cada
        // ojo del catálogo que tenga iris debe pintar el ojo derecho con su propio color. Un ojo
        // sin iris (eyes/closed: solo la línea del párpado) es válido.
        for (FacePartDef d : FaceCatalog.all()) {
            if (d.category() != FaceCategory.EYES) continue;
            for (Race r : Race.values()) {   // también las variantes por raza (race_layers)
                var ls = d.layersFor(r);
                boolean left = ls.stream().anyMatch(l -> l.color() == FaceLayer.Color.EYE_LEFT);
                boolean right = ls.stream().anyMatch(l -> l.color() == FaceLayer.Color.EYE_RIGHT);
                boolean single = ls.stream().anyMatch(l -> l.color() == FaceLayer.Color.EYE);
                helper.assertTrue(left == right && !single, d.id() + " (" + r
                        + "): un ojo con iris necesita capas eye_left Y eye_right (no 'eye') o la heterocromía no se ve");
            }
        }
        // Rasgos de la hoja de propuestas (2026-10-01): para cualquier raza (ninguno pisa las
        // marcas laterales arcosianas lx 0-1/14-15).
        for (String p : new String[]{"eyes/lidded", "eyes/closed", "eyes/alert", "eyes/slanted",
                "mouth/smile", "mouth/small", "mouth/smirk", "mouth/shout", "mouth/thin",
                "nose/dot", "nose/shadow", "nose/nostrils", "nose/bridge"}) {
            var d = FaceCatalog.get(id(p));
            helper.assertTrue(d != null, "falta en el catálogo: " + p);
            for (Race r : Race.values()) helper.assertTrue(d.allows(r), p + " debe valer para " + r);
        }
        helper.assertTrue(FaceCatalog.get(id("eyes/closed")).layers().stream()
                .noneMatch(l -> l.color() == FaceLayer.Color.EYE_LEFT), "eyes/closed: sin iris");
        // Trazo de ceja marcado "eyebrow" (se oculta en Namek/Arcosiano/Majin); la línea del ojo
        // cerrado y el párpado NO son ceja y se ven en cualquier raza.
        for (String n : new String[]{"minimal", "expressive", "soft", "narrow", "wide", "sleepy", "slanted", "sparkle"}) {
            helper.assertTrue(FaceCatalog.get(id("eyes/" + n)).layers().stream()
                    .anyMatch(l -> l.color() == FaceLayer.Color.HAIR && l.eyebrow()), "eyes/" + n + ": su trazo es ceja");
        }
        for (String n : new String[]{"closed", "lidded"}) {
            helper.assertTrue(FaceCatalog.get(id("eyes/" + n)).layers().stream().noneMatch(FaceLayer::eyebrow),
                    "eyes/" + n + ": su trazo NO es ceja");
        }
        var mouth = FaceCatalog.get(MOUTH_1).layers();
        helper.assertTrue(mouth.size() == 1 && mouth.get(0).color() == FaceLayer.Color.SKIN
                        && mouth.get(0).texture().getPath().equals("textures/customization/mouth/neutral.png"),
                "mouth/neutral debe ser una capa neutral teñida con la piel");

        // Con la cabeza arcosiana nueva solo queda vetado wide (el usuario liberó sparkle, arched
        // —hoy fusionado con minimal—, sleepy, expressive y soft el 2026-10-10): valen para Human y Arcosiano.
        for (String p : new String[]{"eyes/sparkle", "eyes/sleepy", "eyes/expressive", "eyes/soft"}) {
            var d = FaceCatalog.get(id(p));
            helper.assertTrue(d != null && d.allows(Race.HUMAN) && d.allows(Race.ARCOSIAN),
                    p + ": vale para Human y Arcosiano");
        }
        helper.assertTrue(FaceBans.isAllowed(Race.ARCOSIAN, FaceCategory.EYES, MINIMAL), "minimal debe valer para Arcosiano");
        // Sin "ninguno" en ojos/boca; sí en nariz.
        helper.assertTrue(!FaceBans.isAllowed(Race.HUMAN, FaceCategory.EYES, null), "ojos: ninguno no permitido");
        helper.assertTrue(!FaceBans.isAllowed(Race.HUMAN, FaceCategory.MOUTH, null), "boca: ninguno no permitido");
        helper.assertTrue(FaceBans.isAllowed(Race.HUMAN, FaceCategory.NOSE, null), "nariz: ninguno permitido");
        // Un id de otra categoría no vale aunque exista.
        helper.assertTrue(!FaceBans.isAllowed(Race.HUMAN, FaceCategory.EYES, MOUTH_1), "un id de boca no vale como ojos");

        // choices (lo que enseña la cuadrícula): solo opciones válidas para la raza, sin "ninguno"
        // en ojos; la GUI las reordena alfabéticamente por nombre traducido.
        var humanEyes = FaceBans.choices(Race.HUMAN, FaceCategory.EYES);
        helper.assertTrue(humanEyes.contains(MINIMAL) && humanEyes.contains(EXPRESSIVE) && humanEyes.contains(SOFT)
                && !humanEyes.contains(null) && humanEyes.contains(id("eyes/wide")), "Human: opciones de ojos correctas");
        var arcEyes = FaceBans.choices(Race.ARCOSIAN, FaceCategory.EYES);
        helper.assertTrue(arcEyes.stream().allMatch(r -> FaceBans.isAllowed(Race.ARCOSIAN, FaceCategory.EYES, r))
                && arcEyes.contains(EXPRESSIVE) && !arcEyes.contains(id("eyes/wide")), "Arcosiano: solo sus ojos permitidos");
        helper.assertTrue(FaceBans.choices(Race.HUMAN, FaceCategory.NOSE).get(0) == null, "nariz: 'ninguna' primero");

        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void reconcileAndLegacyMigration(GameTestHelper helper) {
        // Un Arcosiano con un ojo que su raza no admite vuelve al default_for de su raza.
        PlayerVisualAttachment v = new PlayerVisualAttachment();
        v.setFacePart(FaceCategory.EYES, id("eyes/wide"));
        helper.assertTrue(FaceBans.reconcile(Race.ARCOSIAN, v), "reconcile debe informar del cambio");
        helper.assertTrue(MINIMAL.equals(v.getFacePart(FaceCategory.EYES)), "Arcosiano con wide -> minimal");
        helper.assertTrue(!FaceBans.reconcile(Race.ARCOSIAN, v), "una cara ya válida no cambia");

        // Save posterior a S1 con un ojo legacy ya retirado ("zenkai:eyes/legacy_3"): -> minimal.
        // (Vale tanto si los JSON legacy siguen en disco — legacy_3 era solo Majin — como si no.)
        PlayerVisualAttachment retired = new PlayerVisualAttachment();
        retired.setFacePart(FaceCategory.EYES, id("eyes/legacy_3"));
        FaceBans.reconcile(Race.HUMAN, retired);
        helper.assertTrue(MINIMAL.equals(retired.getFacePart(FaceCategory.EYES)), "ojo legacy retirado -> minimal");

        // Saves de entre medias con "zenkai:eyes/legacy_N": al cargar se renombran a eyes/N.
        CompoundTag mid = new CompoundTag();
        CompoundTag midFace = new CompoundTag();
        midFace.putString("eyes", "zenkai:eyes/legacy_2");
        mid.put("faceParts", midFace);
        PlayerVisualAttachment renamed = new PlayerVisualAttachment();
        renamed.load(mid);
        helper.assertTrue(MINIMAL.equals(renamed.getFacePart(FaceCategory.EYES)), "legacy_2 -> minimal (fusionado) al cargar");
        // Saves con el 2 numérico, o con el índice viejo 2, también van a minimal.
        CompoundTag two = new CompoundTag();
        CompoundTag twoFace = new CompoundTag();
        twoFace.putString("eyes", "zenkai:eyes/2");
        two.put("faceParts", twoFace);
        PlayerVisualAttachment v2 = new PlayerVisualAttachment();
        v2.load(two);
        helper.assertTrue(MINIMAL.equals(v2.getFacePart(FaceCategory.EYES)), "eyes/2 -> minimal al cargar");
        CompoundTag idx2 = new CompoundTag();
        idx2.putInt("eyeIndex", 2);
        PlayerVisualAttachment vi2 = new PlayerVisualAttachment();
        vi2.load(idx2);
        helper.assertTrue(MINIMAL.equals(vi2.getFacePart(FaceCategory.EYES)), "eyeIndex 2 -> minimal");
        // Y uno no fusionado se queda donde está.
        CompoundTag four = new CompoundTag();
        CompoundTag fourFace = new CompoundTag();
        fourFace.putString("eyes", "zenkai:eyes/4");
        four.put("faceParts", fourFace);
        PlayerVisualAttachment v4 = new PlayerVisualAttachment();
        v4.load(four);
        helper.assertTrue(id("eyes/sleepy").equals(v4.getFacePart(FaceCategory.EYES)), "eyes/4 -> sleepy al cargar");

        // Un id que ya no existe (addon desinstalado) también.
        v.setFacePart(FaceCategory.MOUTH, ResourceLocation.fromNamespaceAndPath("addon_borrado", "mouth/x"));
        FaceBans.reconcile(Race.NAMEKIAN, v);
        helper.assertTrue(MOUTH_1.equals(v.getFacePart(FaceCategory.MOUTH)), "id inexistente -> default de la raza");

        // Save viejo: índices -> ids, sin tocar nada más.
        CompoundTag old = new CompoundTag();
        old.putInt("eyeIndex", 4);
        old.putInt("mouthIndex", 1);
        old.putInt("noseIndex", 0);
        PlayerVisualAttachment migrated = new PlayerVisualAttachment();
        migrated.load(old);
        helper.assertTrue(id("eyes/sleepy").equals(migrated.getFacePart(FaceCategory.EYES)),
                "eyeIndex 4 -> eyes/sleepy (los ojos antiguos volvieron con id con nombre)");
        helper.assertTrue(MOUTH_1.equals(migrated.getFacePart(FaceCategory.MOUTH)), "mouthIndex 1 -> mouth/neutral");
        helper.assertTrue(migrated.getFacePart(FaceCategory.NOSE) == null, "noseIndex 0 -> ninguna");

        // Ida y vuelta por NBT: el formato nuevo guarda ids, no índices.
        CompoundTag saved = migrated.save();
        helper.assertTrue(!saved.contains("eyeIndex"), "el formato nuevo no debe escribir eyeIndex");
        PlayerVisualAttachment reloaded = new PlayerVisualAttachment();
        reloaded.load(saved);
        helper.assertTrue(id("eyes/sleepy").equals(reloaded.getFacePart(FaceCategory.EYES))
                && MOUTH_1.equals(reloaded.getFacePart(FaceCategory.MOUTH))
                && reloaded.getFacePart(FaceCategory.NOSE) == null, "ida y vuelta por NBT debe conservar los ids");

        // Índice viejo sin equivalente (p. ej. un eyes_9 de un resource pack): null -> reconcile.
        CompoundTag weird = new CompoundTag();
        weird.putInt("eyeIndex", 9);
        PlayerVisualAttachment w = new PlayerVisualAttachment();
        w.load(weird);
        FaceBans.reconcile(Race.SAIYAN, w);
        helper.assertTrue(MINIMAL.equals(w.getFacePart(FaceCategory.EYES)), "eyeIndex sin equivalente -> default (minimal)");

        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void emptyCatalogNeverWipesSavedFaces(GameTestHelper helper) {
        Map<ResourceLocation, FacePartDef> backup = new LinkedHashMap<>();
        for (FacePartDef d : FaceCatalog.all()) backup.put(d.id(), d);
        try {
            FaceCatalog.replaceAll(Map.of());
            PlayerVisualAttachment v = new PlayerVisualAttachment();
            v.setFacePart(FaceCategory.EYES, EXPRESSIVE);
            helper.assertTrue(!FaceBans.reconcile(Race.HUMAN, v), "con catálogo vacío reconcile no debe tocar nada");
            helper.assertTrue(EXPRESSIVE.equals(v.getFacePart(FaceCategory.EYES)), "la cara guardada debe sobrevivir");
        } finally {
            FaceCatalog.replaceAll(backup);
        }
        helper.succeed();
    }
}
