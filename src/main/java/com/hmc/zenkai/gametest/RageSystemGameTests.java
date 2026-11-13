package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.combat.DownedDeathGuard;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.api.event.PlayerRescuedEvent;
import com.hmc.zenkai.feature.rage.RageListeners;
import com.hmc.zenkai.feature.rage.RageResult;
import com.hmc.zenkai.feature.rage.RageSystem;
import com.hmc.zenkai.feature.rage.RageTrigger;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rage Forms, fase 1: {@link RageSystem} con los despertares REALES del datapack (zenkai_rage_awakenings). Cubre el flujo
 * de §4.1: despertar de la forma siguiente, sin saltos, el padre con su maestría mínima, una vez por hecho, el enfriamiento en
 * tiempo del servidor, Beast absorbido y el no premiar al asesino. Se llama a {@code evaluate} (sin sync) para leer el resultado.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RageSystemGameTests {
    private RageSystemGameTests() {}

    private static final ResourceLocation SSJ1 = FormIds.SSJ1;
    private static final ResourceLocation SSJ2 = FormIds.SSJ2;

    private static ServerPlayer player(GameTestHelper helper, Race race) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(race);
        att.setInOtherworld(false);
        att.skills().grantAdmin(SuperForms.SKILL, 1);   // nivel base, como cualquiera
        return sp;
    }

    private static PlayerFormAttachment forms(ServerPlayer sp) { return sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()); }

    /** Deja la maestría de la forma en {@code pct} (el addFormMastery ya multiplica por mastery_gain). */
    private static void setMastery(ServerPlayer sp, ResourceLocation form, double pct) {
        double gain = FormDef.get(form).masteryGain();
        forms(sp).addFormMastery(form, (float) (pct / gain));
    }

    private static RageResult fire(ServerPlayer sp, RageTrigger t, String id) {
        return RageSystem.evaluate(sp, t, t.intensity(), id);
    }

    private static int withCooldown(int value) {
        int prev = ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.get();
        ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(value);
        return prev;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void allyDeathAwakensSsj1ForFree(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int mindBefore = att.mindUsed();
        int tpBefore = att.getTP();

        helper.assertTrue(!SuperForms.unlocked(sp, SSJ1), "Antes no tiene SSJ1");
        RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "muerto-1");
        helper.assertTrue(r == RageResult.AWAKENING, "La muerte de un aliado despierta SSJ1; resultado=" + r);
        helper.assertTrue(SuperForms.unlocked(sp, SSJ1), "SSJ1 desbloqueada");
        helper.assertTrue(forms(sp).getFormId().equals(SSJ1), "Se transforma en el acto; forma=" + forms(sp).getFormId());
        helper.assertTrue(att.mindUsed() == mindBefore, "Regalada sin cobrar MIND: " + mindBefore + " -> " + att.mindUsed());
        helper.assertTrue(att.getTP() == tpBefore, "Sin TP");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void sameEventRewardsOnlyOnce(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        int prev = withCooldown(0);
        try {
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "mismo") == RageResult.AWAKENING, "El primero despierta");
            float m = forms(sp).getFormMastery(SSJ1);
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "mismo") == RageResult.RECORDED, "El mismo hecho no se repite");
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) == m, "Sin maestría extra por repetir el hecho");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void allyCooldownBlocksButPetsDoNotConsumeIt(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        int prev = withCooldown(36000);
        try {
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "a") == RageResult.AWAKENING, "SSJ1 por la primera muerte");
            float m = forms(sp).getFormMastery(SSJ1);
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "b") == RageResult.RECORDED,
                    "Otra muerte dentro del enfriamiento: solo anotada");
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) == m && !SuperForms.unlocked(sp, SSJ2), "No concede nada");
            // La mascota no pasa por el enfriamiento ni lo gasta.
            RageResult pet = fire(sp, RageTrigger.PET_DEATH, "mascota");
            helper.assertTrue(pet == RageResult.PROGRESS, "La mascota sigue sumando (PROGRESS); resultado=" + pet);
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) > m, "Maestría de SSJ1 subió con la mascota");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void ssj2NeedsParentMasteryElseProgressToParent(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        int prev = withCooldown(0);
        try {
            fire(sp, RageTrigger.ALLY_DEATH, "uno");   // SSJ1
            float before = forms(sp).getFormMastery(SSJ1);
            RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "dos");
            helper.assertTrue(r == RageResult.PROGRESS, "Con SSJ1 al " + before + " % (<50) el hecho suma, no despierta SSJ2; r=" + r);
            helper.assertTrue(!SuperForms.unlocked(sp, SSJ2), "SSJ2 sigue sin desbloquear");
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) > before, "La maestría del padre subió");
            // 30 min equivalentes de SSJ1 (gain 1,0) = 15 %: se rellena hasta el 50 % y entonces sí.
            setMastery(sp, SSJ1, 50 - forms(sp).getFormMastery(SSJ1) + 0.01);
            RageResult r2 = fire(sp, RageTrigger.ALLY_DEATH, "tres");
            helper.assertTrue(r2 == RageResult.AWAKENING && SuperForms.unlocked(sp, SSJ2),
                    "Con SSJ1 >= 50 % despierta SSJ2; r=" + r2);
            helper.assertTrue(forms(sp).getFormId().equals(SSJ2), "Y se transforma en SSJ2");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void neverSkipsTheChain(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        // CRITICAL_SURVIVAL solo abre SSJ2 (alta intensidad) y solo hace progreso de SSJ1: sin SSJ1 no hay salto a SSJ2.
        RageResult r = fire(sp, RageTrigger.CRITICAL_SURVIVAL, "borde");
        helper.assertTrue(r == RageResult.RECORDED, "Sin SSJ1 no se salta a SSJ2; r=" + r);
        helper.assertTrue(!SuperForms.unlocked(sp, SSJ2) && !SuperForms.unlocked(sp, SSJ1), "Ninguna forma concedida");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void otherRaceUsesItsOwnAwakenings(GameTestHelper helper) {
        ServerPlayer human = player(helper, Race.HUMAN);
        RageResult r = fire(human, RageTrigger.ALLY_RESCUED, "rescate");
        helper.assertTrue(r == RageResult.AWAKENING && SuperForms.unlocked(human, ResourceLocation.parse("zenkai:human_super")),
                "Salvar a un aliado despierta human_super; r=" + r);
        ServerPlayer arc = player(helper, Race.ARCOSIAN);
        helper.assertTrue(fire(arc, RageTrigger.ALLY_RESCUED, "rescate") == RageResult.RECORDED,
                "El Arcosiano no tiene despertar por Rage: solo anotado");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void beastIsAbsorbedWithMysticMasteryRule(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        // SSJ1 va antes en la cadena y también despierta con PET_DEATH: se posee primero para aislar lo de Beast.
        helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "base") == RageResult.AWAKENING, "SSJ1 de partida");
        att.skills().grantAdmin(SkillEffects.POTENTIAL_UNLOCK, 1);
        setMastery(sp, FormIds.POTENTIAL_UNLOCK, 10);
        RageResult low = fire(sp, RageTrigger.PET_DEATH, "perro-1");
        helper.assertTrue(low == RageResult.PROGRESS && !att.hasBeastFury(),
                "Con Místico al 10 % la furia no se consume: suma a su maestría; r=" + low);
        setMastery(sp, FormIds.POTENTIAL_UNLOCK, 60);
        RageResult hi = fire(sp, RageTrigger.PET_DEATH, "perro-2");
        helper.assertTrue(hi == RageResult.AWAKENING && att.hasBeastFury(), "Con Místico >= 50 % despierta Beast; r=" + hi);
        helper.succeed();
    }

    // ── Detectores reales ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) throws ReflectiveOperationException {
        Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.getPlayerList());
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void realDeathRewardsWitnessesButNeverTheKiller(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer victim = player(helper, Race.SAIYAN);
        ServerPlayer witness = player(helper, Race.SAIYAN);
        ServerPlayer killer = player(helper, Race.SAIYAN);
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        for (ServerPlayer p : List.of(victim, witness, killer)) p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        Map<UUID, ServerPlayer> map = byUuid(server);
        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.createFor(victim.getUUID());
        party.members.add(victim.getUUID());
        pm.addMember(party, witness.getUUID());
        pm.addMember(party, killer.getUUID());
        int prevRadius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
        try {
            for (ServerPlayer p : List.of(victim, witness, killer)) map.put(p.getUUID(), p);
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(24);
            DownedDeathGuard.allowRealDeath(victim);
            DamageSource bySomeone = victim.damageSources().playerAttack(killer);
            // Directo al oyente: en el bus corren antes otros que sincronizan y lanzan sin conexión real.
            ZenkaiGameTestHelpers.invokeHandler(() -> RageListeners.onDeath(new LivingDeathEvent(victim, bySomeone)));
            helper.assertTrue(SuperForms.unlocked(witness, SSJ1), "El testigo (no el asesino) despierta SSJ1");
            helper.assertTrue(!SuperForms.unlocked(killer, SSJ1), "Quien mató no se premia a sí mismo");
            helper.assertTrue(!SuperForms.unlocked(victim, SSJ1), "El muerto tampoco");
        } finally {
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(prevRadius);
            for (ServerPlayer p : List.of(victim, witness, killer)) map.remove(p.getUUID());
            pm.disband(party);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void rescueOnlyCountsBetweenPartyMembers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer rescuer = player(helper, Race.HUMAN);
        ServerPlayer ally = player(helper, Race.HUMAN);
        ServerPlayer stranger = player(helper, Race.HUMAN);
        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.createFor(rescuer.getUUID());
        party.members.add(rescuer.getUUID());
        pm.addMember(party, ally.getUUID());
        try {
            ZenkaiGameTestHelpers.invokeHandler(() ->
                    NeoForge.EVENT_BUS.post(new PlayerRescuedEvent(rescuer, ally, PlayerRescuedEvent.Method.REVIVE, 50)));
            helper.assertTrue(SuperForms.unlocked(rescuer, ResourceLocation.parse("zenkai:human_super")),
                    "Salvar a un miembro de la party despierta");
            ZenkaiGameTestHelpers.invokeHandler(() ->
                    NeoForge.EVENT_BUS.post(new PlayerRescuedEvent(stranger, ally, PlayerRescuedEvent.Method.REVIVE, 50)));
            helper.assertTrue(!SuperForms.unlocked(stranger, ResourceLocation.parse("zenkai:human_super")),
                    "Salvar a quien no es de tu party no cuenta");
        } finally {
            pm.disband(party);
        }
        helper.succeed();
    }
}
