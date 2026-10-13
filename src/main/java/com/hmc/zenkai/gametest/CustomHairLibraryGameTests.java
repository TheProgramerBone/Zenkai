package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.hair.CustomHairLibrary;
import com.hmc.zenkai.feature.race.hair.CustomHairLibraryPacket;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes;
import com.hmc.zenkai.feature.race.hair.strand.DmzHairCodec;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.feature.race.hair.strand.StrandSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Biblioteca de peinados importados (pestaña Pelo, .claude/docs/customizacion-cara-y-pelo.md
 * §10.1): reglas de añadir/quitar, qué viaja a quién, qué acepta el servidor del cliente y qué
 * variante se ve con cada forma. Solo códigos SINTÉTICOS generados aquí (licencias, §6).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustomHairLibraryGameTests {
    private CustomHairLibraryGameTests() {}

    private static final ResourceLocation CLASSIC = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "classic");

    /** Peinado propio de prueba: un mechón arriba de N cubos (N distinto = código distinto). */
    private static CompoundTag hair(String name, int cubes) {
        CompoundTag hair = new CompoundTag();
        hair.putInt("v", 5);
        hair.putString("n", name);
        ListTag top = new ListTag();
        CompoundTag t = new CompoundTag();
        t.putInt("i", 400);
        t.putInt("l", cubes);
        top.add(t);
        hair.put("T", top);
        return hair;
    }

    private static String code(String name, int cubes) {
        return DmzHairCodec.encode(hair(name, cubes), 5, false);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void libraryRules(GameTestHelper helper) {
        try {
            PlayerVisualAttachment v = new PlayerVisualAttachment();
            CustomHairLibrary.Entry a = CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("Goku", 2));
            helper.assertTrue(a.name().equals("Goku") && v.getCustomHairs().size() == 1, "nombre del código");
            CustomHairLibrary.Entry b = CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("Custom", 3));
            helper.assertTrue(b.name().equals("Custom 1"), "nombre genérico numerado: " + b.name());
            helper.assertTrue(a.id().equals(CustomHairLibrary.idOf(" " + code("Goku", 2) + "\n")),
                    "el id no depende de espacios al pegar");

            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("Goku", 2)), "duplicate");
            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, false, "", code("X", 4)), "disabled");
            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.NAMEKIAN, true, "", code("X", 4)), "no_hair_race");
            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", "DMZ5:basura!"), "bad_char");
            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "",
                    "DMZ5:" + "A".repeat(CustomHairLibrary.MAX_CODE_LENGTH)), "too_long");
            for (int i = 0; v.getCustomHairs().size() < CustomHairLibrary.MAX_ENTRIES; i++) {
                CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "Extra " + i, code("x", 5 + i));
            }
            expect(helper, () -> CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("Otro", 30)), "library_full");
            helper.assertTrue(v.getCustomHairs().size() == CustomHairLibrary.MAX_ENTRIES, "tope de la biblioteca");

            // Quitar el que lleva puesto -> vuelve al peinado de la raza.
            v.setHairStyleId(a.styleId());
            helper.assertTrue(v.activeCustomHair() != null, "puesto");
            helper.assertTrue(CustomHairLibraryPacket.remove(v, Race.SAIYAN, a.id()), "era el puesto");
            helper.assertTrue(CLASSIC.equals(v.getHairStyle()) && v.findCustomHair(a.id()) == null, "cae a classic");
            helper.assertTrue(!CustomHairLibraryPacket.remove(v, Race.SAIYAN, b.id()), "quitar otro no cambia lo puesto");
        } catch (HairCodeException e) {
            helper.fail("código propio rechazado: " + e.getMessage());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void whatTravelsWhere(GameTestHelper helper) {
        try {
            PlayerVisualAttachment v = new PlayerVisualAttachment();
            CustomHairLibrary.Entry a = CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("A", 2));
            CustomHairLibraryPacket.tryAdd(v, Race.SAIYAN, true, "", code("B", 3));
            v.setHairStyleId(a.styleId());

            // Disco y propio jugador: todo. Demás jugadores: solo lo puesto. Snapshot/cliente: nada.
            PlayerVisualAttachment disk = new PlayerVisualAttachment();
            disk.load(v.saveFull());
            helper.assertTrue(disk.getCustomHairs().size() == 2 && disk.activeCustomHair() != null, "saveFull lleva todo");
            PlayerVisualAttachment seen = new PlayerVisualAttachment();
            seen.load(v.saveForTracker());
            helper.assertTrue(seen.getCustomHairs().size() == 1 && seen.activeCustomHair() != null
                    && seen.activeCustomHair().code().equals(a.code()), "a los demás solo el puesto");
            helper.assertTrue(!v.save().contains("customHairs"), "save() (snapshot y paquete del cliente) sin biblioteca");

            // Cancelar la pantalla (load del snapshot) no borra lo importado.
            CompoundTag snapshot = new PlayerVisualAttachment().save();
            v.load(snapshot);
            helper.assertTrue(v.getCustomHairs().size() == 2, "el snapshot no toca la biblioteca");

            // El servidor acepta "custom:<id>" solo si está en SU biblioteca.
            PlayerVisualAttachment server = new PlayerVisualAttachment();
            server.load(v.saveFull());
            server.setHairStyle(CLASSIC);
            CompoundTag fromClient = new CompoundTag();
            fromClient.putString("hairStyleId", "custom:0123456789ab");
            server.loadCosmeticFromClient(fromClient);
            helper.assertTrue(CLASSIC.equals(server.getHairStyle()), "id ajeno a la biblioteca: se ignora");
            fromClient.putString("hairStyleId", a.styleId());
            server.loadCosmeticFromClient(fromClient);
            helper.assertTrue(server.activeCustomHair() != null, "id de la biblioteca: se acepta");
            helper.assertTrue(!HairCatalog.reconcile(Race.SAIYAN, server), "Saiyan lo conserva");
            helper.assertTrue(HairCatalog.reconcile(Race.NAMEKIAN, server) && server.getHairStyle() == null,
                    "Namek (sin pelo) lo pierde");
        } catch (HairCodeException e) {
            helper.fail("código propio rechazado: " + e.getMessage());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void formVariantsForImportedHair(GameTestHelper helper) {
        CompoundTag set = new CompoundTag();
        set.put("B", hair("Base", 2));
        set.put("S", hair("Base", 3));
        String full = DmzHairCodec.encode(set, 5, true);
        String id = CustomHairLibrary.idOf(full);

        ResourceLocation base = CustomHairCodes.model(full, null);
        helper.assertTrue(base != null && base.getPath().equals(id + "/base"), "sin forma: base, es " + base);
        for (String form : new String[]{"ssj1", "ssj2", "ssj3", "ssj4", "ssj_blue", "ssj_rose", "beast"}) {
            ResourceLocation m = CustomHairCodes.model(full, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, form));
            helper.assertTrue(m != null && m.getPath().equals(id + "/ssj"), form + ": sin SSJ2/SSJ3 cae a SSJ, es " + m);
        }
        // SSJ God, UI y UE llevan el pelo de la base (decisión del usuario, 2026-10-12).
        for (String form : new String[]{"ssj_god", "ultra_instinct", "ultra_instinct_sign", "ultra_ego"}) {
            ResourceLocation m = CustomHairCodes.model(full, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, form));
            helper.assertTrue(m != null && m.getPath().equals(id + "/base"), form + ": pelo de la base, es " + m);
        }

        StrandSet ssj = CustomHairCodes.strandsFor(ResourceLocation.fromNamespaceAndPath(CustomHairCodes.MODEL_NAMESPACE, id + "/ssj"));
        helper.assertTrue(ssj != null && ssj.cubeCount() == 3, "el modelo SSJ sale de su variante");
        helper.assertTrue(CustomHairCodes.model("DMZ5:nada", null) == null, "código ilegible: sin modelo (calvo)");
        helper.succeed();
    }

    private interface Throwing {
        void run() throws Exception;
    }

    private static void expect(GameTestHelper helper, Throwing r, String key) {
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
        helper.assertTrue(key.equals(got), "esperaba " + key + ", fue " + got);
    }
}
