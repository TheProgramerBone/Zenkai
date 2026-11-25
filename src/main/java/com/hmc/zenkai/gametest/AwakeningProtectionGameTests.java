package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.combat.StunState;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.rage.AwakeningProtection;
import com.hmc.zenkai.feature.skilltree.FormAccess;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rage Forms, secuencia larga: {@link AwakeningProtection}. La primera transformación de cada forma deja al jugador invulnerable y sin
 * acciones durante {@code rage.awakening_protection_ticks}; las siguientes no; el daño que atraviesa la invulnerabilidad ({@code /kill},
 * el vacío) sigue funcionando; con la config a 0 no hay protección.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AwakeningProtectionGameTests {
    private AwakeningProtectionGameTests() {}

    private static ServerPlayer saiyanWithSsj1(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.SAIYAN);
        FormAccess.grantChain(att, 1, false);   // SSJ1 poseída por compra
        return sp;
    }

    private static PlayerFormAttachment forms(ServerPlayer sp) { return sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void firstTransformationProtectsOnlyOnce(GameTestHelper helper) {
        ServerPlayer sp = saiyanWithSsj1(helper);
        helper.assertTrue(!AwakeningProtection.isProtected(sp), "Antes de transformarse no está protegido");

        forms(sp).setFormId(sp, FormIds.SSJ1);
        helper.assertTrue(AwakeningProtection.isProtected(sp), "La primera vez en SSJ1 lo protege");

        AwakeningProtection.forget(sp.getUUID());
        forms(sp).setFormId(sp, FormIds.BASE);
        forms(sp).setFormId(sp, FormIds.SSJ1);
        helper.assertTrue(!AwakeningProtection.isProtected(sp), "La segunda vez ya no: no es la primera");
        StunState.release(sp, false);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void protectedPlayerTakesNoDamageButKillStillWorks(GameTestHelper helper) {
        ServerPlayer sp = saiyanWithSsj1(helper);
        forms(sp).setFormId(sp, FormIds.SSJ1);
        helper.assertTrue(AwakeningProtection.isProtected(sp), "Protegido");

        DamageSource hit = sp.damageSources().generic();
        LivingIncomingDamageEvent incoming = new LivingIncomingDamageEvent(sp, new net.neoforged.neoforge.common.damagesource.DamageContainer(hit, 5.0F));
        CombatZenkaiHooks.onIncomingDamage(incoming);
        helper.assertTrue(incoming.isCanceled(), "Un golpe normal se cancela (sin animación de daño)");

        LivingDamageEvent.Pre pre = new LivingDamageEvent.Pre(sp, new net.neoforged.neoforge.common.damagesource.DamageContainer(hit, 5.0F));
        CombatZenkaiHooks.onDamage(pre);
        helper.assertTrue(pre.getNewDamage() == 0.0F, "Si llega a Pre, el daño queda en 0; es " + pre.getNewDamage());

        DamageSource kill = sp.damageSources().genericKill();
        LivingIncomingDamageEvent killEvent = new LivingIncomingDamageEvent(sp, new net.neoforged.neoforge.common.damagesource.DamageContainer(kill, 5.0F));
        CombatZenkaiHooks.onIncomingDamage(killEvent);
        helper.assertTrue(!killEvent.isCanceled(), "/kill y el vacío NO se detienen: la protección no puede dejar atrapado a nadie");
        AwakeningProtection.forget(sp.getUUID());
        StunState.release(sp, false);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void configZeroMeansNoProtection(GameTestHelper helper) {
        int prev = ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.get();
        ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.set(0);
        try {
            ServerPlayer sp = saiyanWithSsj1(helper);
            forms(sp).setFormId(sp, FormIds.SSJ1);
            helper.assertTrue(!AwakeningProtection.isProtected(sp), "Con rage.awakening_protection_ticks = 0 no hay protección");
        } finally {
            ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void protectionExpires(GameTestHelper helper) {
        int prev = ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.get();
        ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.set(1);
        try {
            ServerPlayer sp = saiyanWithSsj1(helper);
            forms(sp).setFormId(sp, FormIds.SSJ1);
            helper.assertTrue(AwakeningProtection.isProtected(sp), "Recién empezada: protegido");
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!AwakeningProtection.isProtected(sp), "Tras su duración caduca sola");
                StunState.release(sp, false);
                ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.set(prev);
                helper.succeed();
            });
        } catch (RuntimeException ex) {
            ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.set(prev);
            throw ex;
        }
    }
}
