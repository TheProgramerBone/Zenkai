package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.technique.FalseMoonEntity;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FalseMoon;
import com.hmc.zenkai.feature.forms.OozaruConditions;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.InnateTechniques;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Luna Falsa: la técnica solo la ve y la compra un Saiyan; la esfera hace de luna llena para un Saiyan con cola que la mira (en
 * cualquier momento y dimensión) y no para quien mira a otro lado, no tiene cola o no es Saiyan; y se apaga sola.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FalseMoonGameTests {
    private FalseMoonGameTests() {}

    private static FalseMoonEntity moonAt(GameTestHelper helper, Vec3 pos) {
        FalseMoonEntity m = new FalseMoonEntity(ModEntities.FALSE_MOON.get(), helper.getLevel());
        m.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    private static ServerPlayer saiyan(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.SAIYAN);
        att.setHasTail(true);
        return sp;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tailedSaiyanLookingAtMoonTriggers(GameTestHelper helper) {
        ServerPlayer sp = saiyan(helper);
        Vec3 eye = sp.getEyePosition();
        moonAt(helper, eye.add(0, 0, 20));   // delante (sur = +Z con yaw 0)
        sp.setYRot(0f);
        sp.setXRot(0f);
        helper.assertTrue(FalseMoon.isLookingAtFalseMoon(sp), "Mirando a la esfera debe verla");
        helper.assertTrue(OozaruConditions.canTrigger(sp), "Un Saiyan con cola que la mira dispara el Oozaru");
        sp.setYRot(180f);
        helper.assertTrue(!FalseMoon.isLookingAtFalseMoon(sp), "De espaldas no la ve (salida de emergencia)");
        helper.assertTrue(!OozaruConditions.canTrigger(sp), "...y no dispara nada");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void noTailOrNotSaiyanDoesNotTrigger(GameTestHelper helper) {
        ServerPlayer sp = saiyan(helper);
        moonAt(helper, sp.getEyePosition().add(0, 0, 20));
        sp.setYRot(0f);
        sp.setXRot(0f);
        PlayerStatsAttachment.get(sp).setHasTail(false);
        helper.assertTrue(!OozaruConditions.canTrigger(sp), "Sin cola no se transforma");
        PlayerStatsAttachment.get(sp).setHasTail(true);
        PlayerStatsAttachment.get(sp).setRace(Race.HUMAN);
        helper.assertTrue(!OozaruConditions.canTrigger(sp), "Un no Saiyan no se transforma");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void farMoonIsNotSeen(GameTestHelper helper) {
        ServerPlayer sp = saiyan(helper);
        moonAt(helper, sp.getEyePosition().add(0, 0, FalseMoon.VISIBLE_RANGE + 30));
        sp.setYRot(0f);
        sp.setXRot(0f);
        helper.assertTrue(!FalseMoon.isLookingAtFalseMoon(sp), "Fuera de alcance no cuenta");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void moonExpiresByItself(GameTestHelper helper) {
        FalseMoonEntity m = moonAt(helper, new Vec3(helper.absoluteVec(new Vec3(2, 2, 2)).x, helper.absoluteVec(new Vec3(2, 2, 2)).y, helper.absoluteVec(new Vec3(2, 2, 2)).z));
        m.configure(null, 2);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(m.isRemoved(), "La luna debe desaparecer al agotar su vida");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void techniqueIsSaiyanOnlyAndTaughtByToriBot(GameTestHelper helper) {
        PhysicalTechnique t = PhysicalTechnique.FALSE_MOON;
        helper.assertTrue(t.enabled(), "false_moon.json debe estar cargado");
        helper.assertTrue("toribot".equals(t.master()), "La enseña Tori-Bot; master=" + t.master());
        helper.assertTrue("saiyan".equals(t.requiredRace()), "Solo Saiyan; requiredRace=" + t.requiredRace());
        helper.assertTrue(t.special(), "Va en la categoría Especial");
        helper.assertTrue(InnateTechniques.availableTo(t, Race.SAIYAN), "Un Saiyan la ve");
        helper.assertTrue(!InnateTechniques.availableTo(t, Race.HUMAN), "Un Humano no");
        helper.assertTrue(t.innateRace().isEmpty(), "No es innata: se compra");
        helper.succeed();
    }
}
