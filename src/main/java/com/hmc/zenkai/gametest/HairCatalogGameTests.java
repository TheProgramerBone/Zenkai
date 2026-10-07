package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.FaceBans;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Catálogo de peinados (S5, 2026-10-01): pelo por id de datapack en vez de un item registrado
 * por peinado + convención de nombres SSJ. Comprueba que el comportamiento de antes se conserva
 * (quién tiene pelo, la variante SSJ en las 7 formas "ssj*", el tinte), la migración de los
 * valores viejos ("hair1", "hair0") y la reconciliación por raza. Datapack real, sin jugador.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HairCatalogGameTests {
    private HairCatalogGameTests() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    private static final ResourceLocation BASE = id("classic");
    private static final ResourceLocation SSJ = id("classic_ssj");

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void catalogKeepsOldHairBehaviour(GameTestHelper helper) {
        HairStyleDef base = HairCatalog.get(BASE);
        HairStyleDef ssj = HairCatalog.get(SSJ);
        helper.assertTrue(base != null && ssj != null, "faltan classic / classic_ssj en el catálogo");
        helper.assertTrue(base.model().getPath().equals("geo/hair/classic.geo.json")
                        && ssj.model().getPath().equals("geo/hair/classic_ssj.geo.json"),
                "deben apuntar a las geo de siempre (cero cambio visual)");

        // Quién tiene pelo: como antes, solo Human/Saiyan.
        for (Race r : Race.values()) {
            boolean expected = r == Race.HUMAN || r == Race.SAIYAN;
            helper.assertTrue(HairCatalog.options(r).isEmpty() != expected, r + ": pelo " + (expected ? "sí" : "no"));
        }
        // classic_ssj solo existe como override: no se elige en la lista.
        helper.assertTrue(!HairCatalog.choices(Race.SAIYAN).contains(SSJ), "classic_ssj no debe salir en la lista");
        helper.assertTrue(HairCatalog.choices(Race.SAIYAN).get(0) == null, "calvo es la primera opción");

        // La cadena SSJ de antes (cualquier forma "ssj*" caía en ssj1_hair1) se conserva.
        for (String f : new String[]{"ssj1", "ssj2", "ssj3", "ssj4", "ssj_blue", "ssj_god", "ssj_rose"}) {
            HairStyleDef shown = HairCatalog.effectiveStyle(BASE, id(f), Race.SAIYAN);
            helper.assertTrue(shown != null && shown.id().equals(SSJ), f + ": debe verse classic_ssj");
        }
        HairStyleDef inBase = HairCatalog.effectiveStyle(BASE, null, Race.SAIYAN);
        helper.assertTrue(inBase != null && inBase.id().equals(BASE), "sin forma: classic");
        helper.assertTrue(HairCatalog.effectiveStyle(BASE, null, Race.NAMEKIAN) == null, "Namek: sin pelo");
        helper.assertTrue(HairCatalog.effectiveStyle(null, id("ssj1"), Race.SAIYAN) == null, "calvo sigue calvo en SSJ");

        // Tinte: el hair_rgb de la forma manda; sin forma, el color del jugador.
        FormDef ssj1 = FormDef.get(id("ssj1"));
        helper.assertTrue(ssj1 != null && ssj1.tintsHair(), "ssj1 debe declarar hair_rgb");
        helper.assertTrue(HairCatalog.tintRgb(ssj, 0x112233, ssj1) == (ssj1.hairRgb() & 0xFFFFFF), "SSJ1 tiñe con su hair_rgb");
        helper.assertTrue(HairCatalog.tintRgb(base, 0x112233, null) == 0x112233, "sin forma: color del jugador");

        // Ida y vuelta por red.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HairStyleDef.STREAM_CODEC.encode(buf, base);
        helper.assertTrue(HairStyleDef.STREAM_CODEC.decode(buf).equals(base), "HairStyleDef debe sobrevivir a la red");

        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void migrationAndReconcile(GameTestHelper helper) {
        // Valores de antes del catálogo.
        for (String[] c : new String[][]{{"hair1", "zenkai:classic"}, {"hair2", "zenkai:classic"},
                {"hair0", "none"}, {"bald", "none"},
                // Ids intermedios con número (renombrados en 0.3.1).
                {"zenkai:legacy_1", "zenkai:classic"}, {"zenkai:legacy_1_ssj", "zenkai:classic_ssj"}}) {
            CompoundTag old = new CompoundTag();
            old.putString("hairStyleId", c[0]);
            old.putInt("hairIndex", 7); // el campo viejo se ignora
            PlayerVisualAttachment v = new PlayerVisualAttachment();
            v.load(old);
            helper.assertTrue(c[1].equals(v.getHairStyleId()), c[0] + " debe migrar a " + c[1] + ", es " + v.getHairStyleId());
            helper.assertTrue(!v.save().contains("hairIndex"), "el formato nuevo no escribe hairIndex");
        }
        // Un código procedimental (HairCode, aparcado) no se toca.
        PlayerVisualAttachment custom = new PlayerVisualAttachment();
        custom.setHairStyleId("custom:zh1.h.a;b");
        helper.assertTrue(!HairCatalog.reconcile(Race.SAIYAN, custom) && custom.getHairStyle() == null,
                "custom: se respeta y se pinta calvo");

        // Namek con el pelo humano -> calvo (no tiene default_for); Saiyan con un id inventado -> classic.
        PlayerVisualAttachment namek = new PlayerVisualAttachment();
        namek.setHairStyle(BASE);
        helper.assertTrue(FaceBans.reconcile(Race.NAMEKIAN, namek) && namek.getHairStyle() == null,
                "Namek con classic -> calvo");
        PlayerVisualAttachment saiyan = new PlayerVisualAttachment();
        saiyan.setHairStyle(ResourceLocation.fromNamespaceAndPath("addon_borrado", "pelo"));
        FaceBans.reconcile(Race.SAIYAN, saiyan);
        helper.assertTrue(BASE.equals(saiyan.getHairStyle()), "peinado inexistente -> default_for (classic)");
        // El override de forma no es elegible a mano.
        PlayerVisualAttachment forced = new PlayerVisualAttachment();
        forced.setHairStyle(SSJ);
        FaceBans.reconcile(Race.SAIYAN, forced);
        helper.assertTrue(BASE.equals(forced.getHairStyle()), "classic_ssj elegido a mano -> classic");

        helper.succeed();
    }
}
