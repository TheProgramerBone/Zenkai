package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Lista blanca de UpdatePlayerVisualPacket (PlayerVisualAttachment.loadCosmeticFromClient).
 * Antes el servidor hacía load() del NBT completo del cliente: un cliente modificado podía
 * escribir majinControlled (PersistentEffectsSystem añade/quita el efecto MAJIN según ese flag)
 * y el resto de campos que solo fija el servidor. Lógica pura sobre el attachment, sin jugador.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerVisualWhitelistGameTests {
    private PlayerVisualWhitelistGameTests() {}

    private static final ResourceLocation EYES_4 = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "eyes/expressive");
    private static final ResourceLocation MOUTH_1 = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "mouth/legacy_1");

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void clientCannotWriteServerOnlyFields(GameTestHelper helper) {
        PlayerVisualAttachment v = new PlayerVisualAttachment();
        v.setMajinControlled(true);
        v.setTailStyleId("waist");

        // Lo que mandaría un cliente modificado: el NBT de un visual "limpio" con cada campo cambiado.
        PlayerVisualAttachment forged = new PlayerVisualAttachment();
        forged.setMajinControlled(false);
        forged.setTailStyleId("loose");
        v.loadCosmeticFromClient(forged.save());

        helper.assertTrue(v.isMajinControlled(), "majinControlled no debe poder venir del cliente");
        helper.assertTrue("waist".equals(v.getTailStyleId()), "tailStyleId no debe poder venir del cliente");

        // Y a la inversa: un cliente no puede ACTIVARSE majinControlled.
        PlayerVisualAttachment clean = new PlayerVisualAttachment();
        PlayerVisualAttachment forgedOn = new PlayerVisualAttachment();
        forgedOn.setMajinControlled(true);
        clean.loadCosmeticFromClient(forgedOn.save());
        helper.assertTrue(!clean.isMajinControlled(), "un cliente no debe poder ponerse majinControlled");

        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cosmeticFieldsStillApplyAndAreBounded(GameTestHelper helper) {
        PlayerVisualAttachment v = new PlayerVisualAttachment();

        PlayerVisualAttachment chosen = new PlayerVisualAttachment();
        chosen.setSkinColorRgb(0x123456);
        chosen.setHairColorRgb(0xABCDEF);
        chosen.setEyeColorRgb(0x2E86C1);
        chosen.setAuraColorRgb(0xFF0000);
        chosen.setFacePart(FaceCategory.EYES, EYES_4);
        chosen.setFacePart(FaceCategory.MOUTH, MOUTH_1);
        chosen.setFacePart(FaceCategory.NOSE, null);
        chosen.setHairStyleId("zenkai:legacy_1");
        chosen.setCustomSkinColor(true);
        chosen.setCustomAuraColor(true);
        chosen.setSkinPreset(2);
        chosen.setGender(PlayerVisualAttachment.Gender.FEMALE);
        chosen.setRenderRaceSkin(false);
        chosen.setHideVanillaBody(false);
        chosen.setLayerColorRgb(1, 0x00FF00);
        v.loadCosmeticFromClient(chosen.save());

        helper.assertTrue(v.getSkinColorRgb() == 0x123456, "skinColor debe aplicarse");
        helper.assertTrue(v.getHairColorRgb() == 0xABCDEF, "hairColor debe aplicarse");
        helper.assertTrue(v.getEyeColorRgb() == 0x2E86C1, "eyeColor debe aplicarse");
        helper.assertTrue(v.getAuraColorRgb() == 0xFF0000, "auraColor debe aplicarse");
        helper.assertTrue(EYES_4.equals(v.getFacePart(FaceCategory.EYES))
                && MOUTH_1.equals(v.getFacePart(FaceCategory.MOUTH))
                && v.getFacePart(FaceCategory.NOSE) == null, "rasgos faciales deben aplicarse");
        helper.assertTrue("zenkai:legacy_1".equals(v.getHairStyleId()), "hairStyleId válido debe aplicarse");
        helper.assertTrue(v.isCustomSkinColor() && v.isCustomAuraColor(), "modos de color deben aplicarse");
        helper.assertTrue(v.getSkinPreset() == 2, "skinPreset debe aplicarse");
        helper.assertTrue(v.getGender() == PlayerVisualAttachment.Gender.FEMALE, "gender debe aplicarse");
        helper.assertTrue(!v.shouldRenderRaceSkin() && !v.shouldHideVanillaBody(), "skin de raza on/off debe aplicarse");
        helper.assertTrue(v.getLayerColorRgb(1) == 0x00FF00 && !v.hasLayerColor(0), "layerColors debe aplicarse");

        // Valores absurdos: acotados o ignorados, nunca aceptados tal cual.
        CompoundTag evil = new CompoundTag();
        CompoundTag evilFace = new CompoundTag();
        evilFace.putString("eyes", "x".repeat(5000));      // demasiado largo: se descarta
        evilFace.putString("mouth", "NO ES UN ID VALIDO"); // no parsea: se descarta
        evil.put("faceParts", evilFace);
        evil.putInt("skinPreset", 9999);
        evil.putString("hairStyleId", "NO ES UN ID VALIDO");
        evil.putString("gender", "NOT_A_GENDER");
        evil.putIntArray("layerColors", new int[10_000]);
        v.loadCosmeticFromClient(evil);

        helper.assertTrue(v.getFacePart(FaceCategory.EYES) == null && v.getFacePart(FaceCategory.MOUTH) == null,
                "ids de rasgo malformados deben descartarse (después los repone FaceBans.reconcile)");
        helper.assertTrue(v.getSkinPreset() <= 63, "skinPreset debe acotarse, es " + v.getSkinPreset());
        helper.assertTrue("zenkai:legacy_1".equals(v.getHairStyleId()), "hairStyleId inválido debe ignorarse");
        helper.assertTrue(v.getGender() == PlayerVisualAttachment.Gender.FEMALE, "gender inválido debe ignorarse");
        helper.assertTrue(v.layerColorCount() <= 16, "layerColors debe acotarse, tiene " + v.layerColorCount());

        helper.succeed();
    }
}
