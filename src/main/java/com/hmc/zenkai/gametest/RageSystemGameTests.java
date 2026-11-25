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
import com.hmc.zenkai.feature.skilltree.FormAccess;
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

        helper.assertTrue(!FormAccess.unlocked(sp, SSJ1), "Antes no tiene SSJ1");
        RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "muerto-1");
        helper.assertTrue(r == RageResult.AWAKENING, "La muerte de un aliado despierta SSJ1; resultado=" + r);
        helper.assertTrue(FormAccess.unlocked(sp, SSJ1), "SSJ1 desbloqueada");
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
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) == m && !FormAccess.unlocked(sp, SSJ2), "No concede nada");
            // La mascota no pasa por el enfriamiento ni lo gasta.
            RageResult pet = fire(sp, RageTrigger.PET_DEATH, "mascota");
            helper.assertTrue(pet == RageResult.PROGRESS, "La mascota sigue sumando (PROGRESS); resultado=" + pet);
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) > m, "Maestría de SSJ1 subió con la mascota");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    /** Un hecho que no puede sumar nada (SSJ1 y SSJ2 ya al 100 %) queda RECORDED y NO gasta el enfriamiento de 30 min. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void progressWithNothingToAddDoesNotSpendTheCooldown(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        int prev = withCooldown(0);
        try {
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "a") == RageResult.AWAKENING, "SSJ1 por la primera muerte");
            setMastery(sp, SSJ1, 60);
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "b") == RageResult.AWAKENING, "SSJ2 con SSJ1 al 60 %");
            setMastery(sp, SSJ1, 100);
            setMastery(sp, SSJ2, 100);
            PlayerStatsAttachment.get(sp).flags().setRageAllyEventTick(-1L);
            RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "c");
            helper.assertTrue(r == RageResult.RECORDED, "Sin nada que sumar: RECORDED; resultado=" + r);
            helper.assertTrue(PlayerStatsAttachment.get(sp).flags().getRageAllyEventTick() == -1L,
                    "El enfriamiento no se gasta");
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
            helper.assertTrue(!FormAccess.unlocked(sp, SSJ2), "SSJ2 sigue sin desbloquear");
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) > before, "La maestría del padre subió");
            // 30 min equivalentes de SSJ1 (gain 1,0) = 15 %: se rellena hasta el 50 % y entonces sí.
            setMastery(sp, SSJ1, 50 - forms(sp).getFormMastery(SSJ1) + 0.01);
            RageResult r2 = fire(sp, RageTrigger.ALLY_DEATH, "tres");
            helper.assertTrue(r2 == RageResult.AWAKENING && FormAccess.unlocked(sp, SSJ2),
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
        helper.assertTrue(!FormAccess.unlocked(sp, SSJ2) && !FormAccess.unlocked(sp, SSJ1), "Ninguna forma concedida");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void otherRaceUsesItsOwnAwakenings(GameTestHelper helper) {
        ServerPlayer human = player(helper, Race.HUMAN);
        RageResult r = fire(human, RageTrigger.ALLY_RESCUED, "rescate");
        helper.assertTrue(r == RageResult.AWAKENING && FormAccess.unlocked(human, ResourceLocation.parse("zenkai:human_super")),
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
        att.flags().setRageMinorEventTick(-1L);   // la mascota anterior gastó el enfriamiento corto
        RageResult hi = fire(sp, RageTrigger.PET_DEATH, "perro-2");
        helper.assertTrue(hi == RageResult.AWAKENING && att.hasBeastFury(), "Con Místico >= 50 % despierta Beast; r=" + hi);
        helper.succeed();
    }

    // ── Despertares añadidos el 2026-11-14 ───────────────────────────────────

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void criticalSurvivalAwakensBlueEvolution(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        ResourceLocation god = ResourceLocation.parse("zenkai:ssj_god");
        ResourceLocation blue = ResourceLocation.parse("zenkai:ssj_blue");
        ResourceLocation evo = ResourceLocation.parse("zenkai:ssj_blue_evolution");
        att.setAlignment(50);   // alineamiento bueno: la rama del Blue (el Rosé es la del malvado)
        att.setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND, 1000);
        FormAccess.grantChain(att, Integer.MAX_VALUE, false);
        // Una llamada por concesión: el sync de cada una lanza con un jugador simulado y cortaría las siguientes.
        var tree = com.hmc.zenkai.feature.skilltree.SkillTree.Via.EVENT;
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp,
                com.hmc.zenkai.feature.skilltree.SkillTreeMigration.NODE_GOD_KI, 1, com.hmc.zenkai.feature.skilltree.SkillTree.Via.MASTER_TRIAL));
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp, god, 1, tree));
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp, blue, 1, tree));
        helper.assertTrue(FormAccess.unlocked(sp, blue) && !FormAccess.unlocked(sp, evo), "Parte con Blue y sin Evolution; blue="
                + FormAccess.unlocked(sp, blue) + " evo=" + FormAccess.unlocked(sp, evo));
        int prev = withCooldown(0);
        try {
            setMastery(sp, blue, 50.01);
            RageResult[] r = new RageResult[1];
            ZenkaiGameTestHelpers.invokeHandler(() -> r[0] = fire(sp, RageTrigger.CRITICAL_SURVIVAL, "limite"));
            helper.assertTrue(FormAccess.unlocked(sp, evo), "Romper el límite con Blue >= 50 % despierta Blue Evolution; r=" + r[0]);
            helper.assertTrue(forms(sp).getFormId().equals(evo), "Y se transforma; forma=" + forms(sp).getFormId());
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legendarySkipsSsj2AndFeedsItsOwnLine(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        ResourceLocation green = ResourceLocation.parse("zenkai:ssj_green");
        att.setLegendary(true);
        int prev = withCooldown(0);
        try {
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "padre") == RageResult.AWAKENING && FormAccess.unlocked(sp, SSJ1),
                    "La muerte de un aliado despierta el SSJ1 del Legendario");
            setMastery(sp, SSJ1, 60);
            float before = forms(sp).getFormMastery(green);
            RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "otro");
            helper.assertTrue(!FormAccess.unlocked(sp, SSJ2), "Un Legendario no despierta el SSJ2 que no puede llevar; r=" + r);
            helper.assertTrue(r == RageResult.PROGRESS && forms(sp).getFormMastery(green) > before,
                    "El hecho suma a su línea (Legendario): " + before + " -> " + forms(sp).getFormMastery(green) + "; r=" + r);
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
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
            helper.assertTrue(FormAccess.unlocked(witness, SSJ1), "El testigo (no el asesino) despierta SSJ1");
            helper.assertTrue(!FormAccess.unlocked(killer, SSJ1), "Quien mató no se premia a sí mismo");
            helper.assertTrue(!FormAccess.unlocked(victim, SSJ1), "El muerto tampoco");
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
            helper.assertTrue(FormAccess.unlocked(rescuer, ResourceLocation.parse("zenkai:human_super")),
                    "Salvar a un miembro de la party despierta");
            ZenkaiGameTestHelpers.invokeHandler(() ->
                    NeoForge.EVENT_BUS.post(new PlayerRescuedEvent(stranger, ally, PlayerRescuedEvent.Method.REVIVE, 50)));
            helper.assertTrue(!FormAccess.unlocked(stranger, ResourceLocation.parse("zenkai:human_super")),
                    "Salvar a quien no es de tu party no cuenta");
        } finally {
            pm.disband(party);
        }
        helper.succeed();
    }

    /**
     * Voluntad Inquebrantable es el peldaño de la cadena del Humano y Flujo una rama lateral por maestría (decisión del usuario,
     * 2026-11-15). Antes Flujo ocupaba el peldaño y Voluntad figuraba desbloqueada para cualquier Humano con nivel 3.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void humanVoluntadIsTheStepAndFlujoIsASideBranch(GameTestHelper helper) {
        ResourceLocation ultra = ResourceLocation.parse("zenkai:human_ultra");
        ResourceLocation voluntad = ResourceLocation.parse("zenkai:human_indomitable_spirit");
        ResourceLocation flujo = ResourceLocation.parse("zenkai:human_flow");
        ServerPlayer sp = player(helper, Race.HUMAN);
        FormAccess.grantChain(PlayerStatsAttachment.get(sp), 2, false);
        helper.assertTrue(FormAccess.depthOf(Race.HUMAN, voluntad) == 3, "Voluntad es el 3er peldaño; depth=" + FormAccess.depthOf(Race.HUMAN, voluntad));
        helper.assertTrue(FormAccess.depthOf(Race.HUMAN, flujo) == 0, "Flujo queda fuera de la cadena");
        helper.assertTrue(FormAccess.unlocked(sp, ultra), "Nivel 3 desbloquea Mente de Cristal");
        helper.assertTrue(!FormAccess.unlocked(sp, voluntad), "Con nivel 3 Voluntad NO figura desbloqueada");
        helper.assertTrue(!FormAccess.unlocked(sp, flujo), "Flujo exige maestría de Mente de Cristal");
        setMastery(sp, ultra, 100);
        helper.assertTrue(FormAccess.unlocked(sp, flujo), "Con Mente de Cristal al 100 % se abre Flujo");
        helper.assertTrue(!FormAccess.unlocked(sp, voluntad), "...pero Voluntad sigue pidiendo su nivel");
        FormAccess.grantChain(PlayerStatsAttachment.get(sp), 3, false);
        helper.assertTrue(FormAccess.unlocked(sp, voluntad), "Con nivel 4 Voluntad se desbloquea");
        helper.succeed();
    }

    /** Voluntad Inquebrantable despierta rompiendo el límite (o viendo caer a un aliado ya derribado) con Mente de Cristal >= 50 %; Flujo no. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void criticalSurvivalAwakensVoluntadInquebrantable(GameTestHelper helper) {
        ResourceLocation ultra = ResourceLocation.parse("zenkai:human_ultra");
        ResourceLocation voluntad = ResourceLocation.parse("zenkai:human_indomitable_spirit");
        ResourceLocation flujo = ResourceLocation.parse("zenkai:human_flow");
        ServerPlayer sp = player(helper, Race.HUMAN);
        PlayerStatsAttachment.get(sp).setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND, 1000);
        FormAccess.grantChain(PlayerStatsAttachment.get(sp), 2, false);   // Mente de Cristal
        int prev = withCooldown(0);
        try {
            setMastery(sp, ultra, 20);
            RageResult[] r = new RageResult[1];
            ZenkaiGameTestHelpers.invokeHandler(() -> r[0] = fire(sp, RageTrigger.CRITICAL_SURVIVAL, "limite-1"));
            helper.assertTrue(!FormAccess.unlocked(sp, voluntad), "Con Mente de Cristal al 20 % no despierta; r=" + r[0]);
            setMastery(sp, ultra, 40);   // 60 % en total
            ZenkaiGameTestHelpers.invokeHandler(() -> r[0] = fire(sp, RageTrigger.CRITICAL_SURVIVAL, "limite-2"));
            helper.assertTrue(r[0] == RageResult.AWAKENING, "Con Mente de Cristal >= 50 % despierta Voluntad; r=" + r[0]);
            helper.assertTrue(FormAccess.unlocked(sp, voluntad), "Voluntad desbloqueada");
            helper.assertTrue(forms(sp).getFormId().equals(voluntad), "Se transforma en el acto; forma=" + forms(sp).getFormId());
            helper.assertTrue(!FormAccess.unlocked(sp, flujo), "Flujo no se abre por furia (mastery_reqs: Mente de Cristal al 100 %)");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    /** Deseo «Despertar el potencial»: Ki Control al máximo para todos; el Namekiano además Naranja; sin gastarse si no da nada. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void potentialWishGrantsKiControlAndNamekOrange(GameTestHelper helper) {
        int max = com.hmc.zenkai.feature.wishes.WishPotentialPayload.kiControlMax();
        helper.assertTrue(max > 0, "Ki Control existe en el datapack; max=" + max);
        ResourceLocation orange = ResourceLocation.parse("zenkai:namek_orange");

        ServerPlayer human = player(helper, Race.HUMAN);
        PlayerStatsAttachment h = PlayerStatsAttachment.get(human);
        helper.assertTrue(!com.hmc.zenkai.feature.wishes.WishPotentialPayload.isComplete(h), "Antes del deseo queda algo por dar");
        helper.assertTrue(com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(h), "Se concede");
        int step = Math.min(max, ServerConfig.awakenPotentialKiControlLevels());
        helper.assertTrue(h.skills().level(SkillEffects.KI_CONTROL) == step, "Un deseo da solo " + step + " niveles; nivel=" + h.skills().level(SkillEffects.KI_CONTROL));
        for (int i = 0; i < max; i++) com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(h);
        helper.assertTrue(h.skills().level(SkillEffects.KI_CONTROL) == max, "Con deseos suficientes llega al tope y no pasa; nivel=" + h.skills().level(SkillEffects.KI_CONTROL));
        helper.assertTrue(FormAccess.purchasableChain(Race.HUMAN).stream().noneMatch(h.skillTree()::owns), "Un Humano no recibe ninguna forma");
        helper.assertTrue(!com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(h), "Con el tope alcanzado no concede nada (el deseo no se gasta)");

        ServerPlayer nam = player(helper, Race.NAMEKIAN);
        PlayerStatsAttachment n = PlayerStatsAttachment.get(nam);
        ResourceLocation superNamek = com.hmc.zenkai.feature.wishes.WishPotentialPayload.NAMEK_SUPER;
        helper.assertTrue(!FormAccess.unlocked(nam, superNamek), "Antes no tiene el Súper Namekiano");
        var first = com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(n, forms(nam));
        helper.assertTrue(first == com.hmc.zenkai.feature.wishes.WishPotentialPayload.Outcome.NAMEK_FIRST_FORM, "El primer deseo le da su primera forma; fue " + first);
        helper.assertTrue(n.skills().level(SkillEffects.KI_CONTROL) == step, "Ki Control sube " + step + " niveles");
        helper.assertTrue(FormAccess.unlocked(nam, superNamek), "Súper Namekiano desbloqueado");
        helper.assertTrue(!FormAccess.unlocked(nam, orange), "El deseo NUNCA regala Naranja");
        helper.assertTrue(!n.flags().isNamekOrangeHinted(), "Y el primer deseo aún no revela su nodo");

        // 0.7.0: con el Súper Namekiano ya poseído, el deseo da maestría de esa forma y revela el nodo de Naranja.
        float before = forms(nam).getFormMastery(superNamek);
        var second = com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(n, forms(nam));
        helper.assertTrue(second == com.hmc.zenkai.feature.wishes.WishPotentialPayload.Outcome.NAMEK_REVEAL, "El segundo deseo revela; fue " + second);
        helper.assertTrue(n.flags().isNamekOrangeHinted(), "Naranja deja de ser oculta");
        float gift = (float) ServerConfig.awakenPotentialNamekMastery();
        helper.assertTrue(Math.abs(forms(nam).getFormMastery(superNamek) - before - gift) < 0.01f,
                "Suma " + gift + " puntos de maestría; ahora " + forms(nam).getFormMastery(superNamek));
        helper.assertTrue(!FormAccess.unlocked(nam, orange), "Revelar no es desbloquear");

        // Después solo sube Ki Control y al llegar al tope no se gasta.
        for (int i = 0; i < max; i++) com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(n);
        helper.assertTrue(!com.hmc.zenkai.feature.wishes.WishPotentialPayload.apply(n), "Completo: no se gasta");
        helper.succeed();
    }

    /**
     * Cadena del Namekiano (0.7.0): Súper Namekiano es el primer peldaño y Naranja el segundo. Naranja no se compra: despierta
     * sola al cumplir la maestría que pide su nodo ({@code MasteryAwakening}), y nunca antes de tener el Súper Namekiano.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void namekOrangeAwakensByMasteryOfSuperNamekian(GameTestHelper helper) {
        ResourceLocation superNamek = com.hmc.zenkai.feature.wishes.WishPotentialPayload.NAMEK_SUPER;
        ResourceLocation orange = ResourceLocation.parse("zenkai:namek_orange");
        helper.assertTrue(FormAccess.depthOf(Race.NAMEKIAN, superNamek) == 1, "Súper Namekiano es el 1er peldaño; depth=" + FormAccess.depthOf(Race.NAMEKIAN, superNamek));
        helper.assertTrue(FormAccess.depthOf(Race.NAMEKIAN, orange) == 2, "Naranja es el 2º; depth=" + FormAccess.depthOf(Race.NAMEKIAN, orange));
        helper.assertTrue(FormAccess.purchasableChain(Race.NAMEKIAN).size() == 2, "2 formas mortales; cadena=" + FormAccess.purchasableChain(Race.NAMEKIAN));

        ServerPlayer sp = player(helper, Race.NAMEKIAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        setMastery(sp, superNamek, 100);
        helper.assertTrue(com.hmc.zenkai.feature.skilltree.MasteryAwakening.evaluate(sp) == null && !FormAccess.unlocked(sp, orange),
                "Sin el Súper Namekiano no despierta nada (nunca saltos), ni con la maestría puesta");

        ServerPlayer sp2 = player(helper, Race.NAMEKIAN);
        PlayerStatsAttachment att2 = PlayerStatsAttachment.get(sp2);
        FormAccess.grantChain(att2, 1, false);
        var node = com.hmc.zenkai.feature.skilltree.SkillTreeNodes.find(Race.NAMEKIAN, orange);
        helper.assertTrue(node != null && !com.hmc.zenkai.feature.skilltree.SkillTreeNodes.revealed(sp2, node), "Naranja es un nodo oculto");
        helper.assertTrue(com.hmc.zenkai.feature.skilltree.SkillTreeNodes.evaluate(sp2, node).state()
                != com.hmc.zenkai.feature.skilltree.SkillTreeNodes.State.AVAILABLE, "Y no se compra con TP");
        setMastery(sp2, superNamek, 10);
        helper.assertTrue(com.hmc.zenkai.feature.skilltree.MasteryAwakening.evaluate(sp2) == null, "Con poca maestría aún no despierta");
        setMastery(sp2, superNamek, 100);
        helper.assertTrue(com.hmc.zenkai.feature.skilltree.SkillTreeNodes.revealed(sp2, node), "La maestría la revela");
        helper.assertTrue(orange.equals(com.hmc.zenkai.feature.skilltree.MasteryAwakening.evaluate(sp2)), "Y Naranja despierta");
        helper.assertTrue(FormAccess.unlocked(sp2, orange) && att2.skillTree().owns(orange), "Queda desbloqueada (su nodo)");
        helper.assertTrue(com.hmc.zenkai.feature.skilltree.MasteryAwakening.evaluate(sp2) == null, "Y no sigue concediendo peldaños");
        helper.assertTrue(!att.skillTree().owns(superNamek), "El otro jugador sigue en Base");
        helper.succeed();
    }

    /** Partidas anteriores a la 0.7.0: quien ya tenía Naranja (nivel 2) la conserva y recibe el Súper Namekiano (y la v5 lo vuelve nodos). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oldNamekianSavesKeepOrange(GameTestHelper helper) {
        PlayerStatsAttachment old = new PlayerStatsAttachment();
        old.setRace(Race.NAMEKIAN);
        old.setRaceChosen(true);
        old.skills().grantAdmin("super_forms", 2);   // la skill de antes de la v5
        net.minecraft.nbt.CompoundTag tag = old.save();
        tag.getCompound("skillTree").putInt("version", 3);
        PlayerStatsAttachment now = new PlayerStatsAttachment();
        now.load(tag);
        helper.assertTrue(now.skills().level("super_forms") == 0, "La skill de antes ya no existe; nivel=" + now.skills().level("super_forms"));
        helper.assertTrue(now.skillTree().owns(ResourceLocation.parse("zenkai:namek_orange"))
                && now.skillTree().owns(com.hmc.zenkai.feature.wishes.WishPotentialPayload.NAMEK_SUPER), "Posee las dos formas");
        // Ya migrada: volver a cargar no sube otro nivel.
        PlayerStatsAttachment again = new PlayerStatsAttachment();
        again.load(now.save());
        helper.assertTrue(again.skillTree().owns(ResourceLocation.parse("zenkai:namek_orange")) && again.skillTree().owns(com.hmc.zenkai.feature.wishes.WishPotentialPayload.NAMEK_SUPER), "La migración no se repite ni pierde nada");
        helper.succeed();
    }

    /** Arcosiano «Mundo» (0.7.0): el fuego le hace menos daño; a otras razas y a otros daños, el de siempre. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void arcosianTakesLessFireDamage(GameTestHelper helper) {
        ServerPlayer arc = player(helper, Race.ARCOSIAN);
        ServerPlayer human = player(helper, Race.HUMAN);
        DamageSource fire = arc.damageSources().inFire();
        DamageSource fall = arc.damageSources().fall();
        float expected = 10f * (1f - (float) ServerConfig.arcosianFireDamageReduction());
        float got = com.hmc.zenkai.feature.race.RacePassives.mitigateEnvironment(arc, fire, 10f);
        helper.assertTrue(Math.abs(got - expected) < 0.001f && got < 10f, "Fuego reducido: " + got + " (esperado " + expected + ")");
        helper.assertTrue(com.hmc.zenkai.feature.race.RacePassives.mitigateEnvironment(arc, arc.damageSources().lava(), 10f) < 10f, "La lava también es fuego");
        helper.assertTrue(com.hmc.zenkai.feature.race.RacePassives.mitigateEnvironment(arc, fall, 10f) == 10f, "Otros daños, intactos");
        helper.assertTrue(com.hmc.zenkai.feature.race.RacePassives.mitigateEnvironment(human, fire, 10f) == 10f, "Otras razas, intactas");
        helper.succeed();
    }

    // ── Análisis de cobertura del 2026-11-15 ─────────────────────────────────

    /** Un despertar estando derribado (un aliado muere con el jugador en el suelo) lo levanta: antes moría por timeout en su propia escena. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void awakeningWhileDownedStandsThePlayerUp(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setBody(0);
        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.level().getGameTime() + 100);
        int prev = withCooldown(0);
        try {
            RageResult r = fire(sp, RageTrigger.ALLY_DIES_WHILE_DOWNED, "muere-con-el-en-el-suelo");
            helper.assertTrue(r == RageResult.AWAKENING, "Despierta SSJ1; r=" + r);
            helper.assertTrue(!att.flags().isDowned(), "Ya no está derribado");
            helper.assertTrue(att.getBody() > 0, "Y tiene body para seguir vivo; body=" + att.getBody());
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    /** Sumar maestría estando derribado NO levanta a nadie: solo el despertar lo hace. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void progressWhileDownedDoesNotStandUp(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int prev = withCooldown(0);
        try {
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DEATH, "a") == RageResult.AWAKENING, "SSJ1 por la primera muerte");
            att.setBody(0);
            att.flags().setDowned(true);
            RageResult r = fire(sp, RageTrigger.ALLY_DEATH, "b");
            helper.assertTrue(r == RageResult.PROGRESS, "Suma maestría a SSJ1; r=" + r);
            helper.assertTrue(att.flags().isDowned(), "Pero sigue derribado");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    /** Mascota y aliado derribado comparten un enfriamiento CORTO propio, y no gastan el de 30 min de los aliados. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void minorEventsShareAShortCooldown(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int prevAlly = withCooldown(36000);
        int prevMinor = ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.get();
        ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.set(6000);
        try {
            helper.assertTrue(fire(sp, RageTrigger.PET_DEATH, "p1") == RageResult.AWAKENING, "SSJ1 por la primera mascota");
            float m = forms(sp).getFormMastery(SSJ1);
            helper.assertTrue(fire(sp, RageTrigger.PET_DEATH, "p2") == RageResult.RECORDED, "Otra mascota enseguida: solo anotada");
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DOWNED, "d1") == RageResult.RECORDED, "Un aliado derribado enseguida: tampoco");
            helper.assertTrue(forms(sp).getFormMastery(SSJ1) == m, "Sin maestría extra");
            helper.assertTrue(att.flags().getRageAllyEventTick() == -1L, "El enfriamiento de aliados sigue intacto");
            // Pasado el enfriamiento corto vuelve a sumar.
            att.flags().setRageMinorEventTick(sp.level().getGameTime() - 6001);
            helper.assertTrue(fire(sp, RageTrigger.ALLY_DOWNED, "d2") == RageResult.PROGRESS, "Con el enfriamiento cumplido suma");
            // Con 0 se apaga.
            ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.set(0);
            helper.assertTrue(fire(sp, RageTrigger.PET_DEATH, "p3") == RageResult.PROGRESS, "Con 0 no hay enfriamiento");
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prevAlly);
            ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.set(prevMinor);
        }
        helper.succeed();
    }

    /** La mascota que muere por el entorno (sin causante) no es un acontecimiento; si la mata un enemigo, sí. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void petKilledByTheEnvironmentIsNotAnEvent(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = player(helper, Race.SAIYAN);
        ServerPlayer foe = player(helper, Race.SAIYAN);
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        owner.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(helper.getLevel());
        wolf.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        wolf.tame(owner);
        Map<UUID, ServerPlayer> map = byUuid(server);
        int prevRadius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
        int prevMinor = ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.get();
        try {
            map.put(owner.getUUID(), owner);
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(24);
            ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.set(0);
            ZenkaiGameTestHelpers.invokeHandler(() -> RageListeners.onDeath(new LivingDeathEvent(wolf, wolf.damageSources().lava())));
            helper.assertTrue(!FormAccess.unlocked(owner, SSJ1), "La lava no despierta nada");
            ZenkaiGameTestHelpers.invokeHandler(() -> RageListeners.onDeath(new LivingDeathEvent(wolf, wolf.damageSources().playerAttack(foe))));
            helper.assertTrue(FormAccess.unlocked(owner, SSJ1), "Un enemigo que la mata sí despierta SSJ1");
        } finally {
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(prevRadius);
            ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.set(prevMinor);
            map.remove(owner.getUUID());
        }
        helper.succeed();
    }

    /** Rosé Evolution, espejo de Blue Evolution: rompe el límite con Rosé >= 50 %. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void criticalSurvivalAwakensRoseEvolution(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        ResourceLocation god = ResourceLocation.parse("zenkai:ssj_god");
        ResourceLocation rose = ResourceLocation.parse("zenkai:ssj_rose");
        ResourceLocation evo = ResourceLocation.parse("zenkai:ssj_rose_evolution");
        att.setDivine(true);   // el flag divino elige Rosé en vez de Blue
        att.setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND, 1000);
        FormAccess.grantChain(att, Integer.MAX_VALUE, false);
        var tree = com.hmc.zenkai.feature.skilltree.SkillTree.Via.EVENT;
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp,
                com.hmc.zenkai.feature.skilltree.SkillTreeMigration.NODE_GOD_KI, 1, com.hmc.zenkai.feature.skilltree.SkillTree.Via.MASTER_TRIAL));
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp, god, 1, tree));
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.skilltree.SkillTree.grant(sp, rose, 1, tree));
        helper.assertTrue(FormAccess.unlocked(sp, rose) && !FormAccess.unlocked(sp, evo), "Parte con Rosé y sin Evolution");
        int prev = withCooldown(0);
        try {
            setMastery(sp, rose, 50.01);
            RageResult[] r = new RageResult[1];
            ZenkaiGameTestHelpers.invokeHandler(() -> r[0] = fire(sp, RageTrigger.CRITICAL_SURVIVAL, "limite"));
            helper.assertTrue(FormAccess.unlocked(sp, evo), "Romper el límite con Rosé >= 50 % despierta Rosé Evolution; r=" + r[0]);
            helper.assertTrue(forms(sp).getFormId().equals(evo), "Y se transforma; forma=" + forms(sp).getFormId());
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    /** Los Legendarios de las cuatro razas que no son Saiyan tienen su entrada de progreso (solo suman, nunca despiertan). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legendariesOfEveryRaceHaveProgressEntries(GameTestHelper helper) {
        Object[][] expected = {{Race.HUMAN, "human_legendary"}, {Race.NAMEKIAN, "namek_legendary"},
                {Race.ARCOSIAN, "arcosian_legendary"}, {Race.MAJIN, "majin_legendary"}};
        for (Object[] e : expected) {
            ResourceLocation form = ResourceLocation.parse("zenkai:" + e[1]);
            var entry = com.hmc.zenkai.feature.rage.RageAwakeningManager.forRace((Race) e[0]).stream()
                    .filter(a -> a.form().equals(form)).findFirst().orElse(null);
            helper.assertTrue(entry != null, "Hay entrada de furia para " + e[1]);
            helper.assertTrue(!entry.awakens(), e[1] + " solo suma maestría (se posee por el deseo)");
        }
        helper.succeed();
    }
}
