package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeScreen;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillsTreeScreen;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skilltree.SkillTreeBuyPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * HERRAMIENTA DE DESARROLLO: capturas automáticas de SkillTreeScreen (skill tree de transformaciones,
 * F2-F7), mismo patrón que FpWeaponShots. Solo se activa si existe {@code <run>/zenkai_treeshots.txt}; su
 * primera línea es la CARPETA del mundo que se abre (usar una COPIA: cambia raza, skills y TP del
 * jugador). Por cada raza de prueba pone el progreso, abre la pantalla, captura, simula rueda de zoom y
 * una compra real por SkillTreeBuyPacket, y vuelve a capturar. Salida en {@code zenkai_treeshots/}; al
 * terminar escribe done.txt y cierra el juego.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SkillTreeShots {
    private SkillTreeShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 20;

    private static Boolean enabled;
    private static String world;
    private static boolean fadeOnly;
    private static boolean skillsOnly;
    private static boolean absorbOnly;
    private static boolean legendaryOnly;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_treeshots.txt");
            if (trigger.isFile()) {
                try {
                    var lines = Files.readAllLines(trigger.toPath());
                    world = lines.get(0).trim();
                    fadeOnly = lines.size() > 1 && lines.get(1).trim().equals("fade"); // solo la prueba del fundido de fondos
                    skillsOnly = lines.size() > 1 && lines.get(1).trim().equals("skills"); // solo el árbol de skills por maestro
                    absorbOnly = lines.size() > 1 && lines.get(1).trim().equals("absorb"); // solo la absorción del Majin
                    legendaryOnly = lines.size() > 1 && lines.get(1).trim().equals("legendary"); // solo la línea del Legendario
                    enabled = !world.isEmpty();
                } catch (Exception ignored) {}
            }
        }
        return enabled;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled(mc)) return;
        if (mc.level == null || mc.player == null) {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                mc.getWindow().setWindowed(1600, 900);
                mc.createWorldOpenFlows().openWorld(world, () -> {});
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Exception ex) { Zenkai.LOGGER.warn("SkillTreeShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_treeshots");
        d.mkdirs();
        return d;
    }

    /** Fundido entre fondos: hover en un nodo y en otro de otra rama, capturas a intervalos durante la transición. */
    private static void fadePlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN, 4, true, true, true, sp -> {}); wait = WAIT; });
        PLAN.add(() -> { forgetTutorial(); mc.setScreen(new SkillTreeScreen()); wait = 60; });
        PLAN.add(() -> { if (mc.screen != null) mc.screen.keyPressed(256, 0, 0); mc.setScreen(new SkillTreeScreen()); wait = 10; });
        String[] ids = {"zenkai:ssj1", "zenkai:ssj_god"};
        for (int k = 0; k < ids.length; k++) {
            final String h = ids[k];
            final int kk = k;
            PLAN.add(() -> {
                if (mc.screen instanceof SkillTreeScreen s) {
                    int[] p = s.devFocus(ResourceLocation.parse(h));
                    SkillTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
                }
                wait = 14;
            });
            for (int i = 0; i < 6; i++) {
                final int ii = i;
                PLAN.add(() -> { capture(mc, "fade" + kk + "_" + ii); wait = 14; });
            }
            PLAN.add(() -> { wait = 90; });
        }
    }

    private static void buildPlan(Minecraft mc) {
        if (fadeOnly) { fadePlan(mc); return; }
        if (skillsOnly) { skillsPlan(mc); return; }
        if (absorbOnly) { absorbPlan(mc); return; }
        if (legendaryOnly) { legendaryPlan(mc); return; }
        // Tutorial (F7): la primera apertura lo enseña; se captura y se cierra con Esc.
        PLAN.add(() -> {
            setup(mc, Race.SAIYAN, 3, false, false, false, sp -> {});
            wait = WAIT;
        });
        PLAN.add(() -> { forgetTutorial(); mc.setScreen(new SkillTreeScreen()); wait = WAIT; });
        PLAN.add(() -> capture(mc, "tutorial_1"));
        PLAN.add(() -> { if (mc.screen != null) mc.screen.mouseClicked(10, 10, 0); wait = 5; });
        PLAN.add(() -> capture(mc, "tutorial_2"));
        PLAN.add(() -> { if (mc.screen != null) mc.screen.keyPressed(256, 0, 0); wait = 5; });

        // Saiyan sin ritual: requisitos y pistas, ruta marcada hasta SSJ3, pasiva comprable.
        scene(mc, Race.SAIYAN, 2, false, false, false, sp -> {}, "zenkai:node/race_saiyan", "saiyan",
                "zenkai:ssj3", new String[]{"zenkai:ssj3", "zenkai:node/race_saiyan", "zenkai:oozaru"});
        // Ritual de SSJ4 hecho + audiencia: Súper Oozaru y SSJ4 se revelan (animación) y la cadena
        // queda HABILITADA con God Ki asomando.
        scene(mc, Race.SAIYAN, 4, true, true, false, sp -> {}, "zenkai:ssj4", "saiyan_audience",
                null, new String[]{"zenkai:super_oozaru", "zenkai:ssj4", "zenkai:node/god_ki"});
        // Saiyan con God Ki: SSJ God se compra como nodo propio; UI/UE desbloqueados.
        scene(mc, Race.SAIYAN, 4, true, false, true, sp -> {}, "zenkai:ssj_god", "saiyan_godki",
                "zenkai:ultra_instinct", new String[]{"zenkai:ssj_god", "zenkai:ssj_blue", "zenkai:node/ultra_instinct"});
        // Arcosiano con Black: el ritual (forzado) regala Arcosian GOD y God Ki.
        scene(mc, Race.ARCOSIAN, 6, false, false, false,
                sp -> com.hmc.zenkai.feature.skilltree.GodKi.debugCompleteRitual(sp),
                "zenkai:node/race_arcosian", "arcosian_ritual",
                null, new String[]{"zenkai:arcosian_god", "zenkai:node/race_arcosian", "zenkai:black_form"});
        // Tutorial del hub de Training (mismo widget).
        PLAN.add(() -> { forgetTutorial(); mc.setScreen(new com.hmc.zenkai.client.gui.screens.TrainingHubScreen()); wait = WAIT; });
        PLAN.add(() -> capture(mc, "training_tutorial_1"));
        PLAN.add(() -> { if (mc.screen != null) mc.screen.mouseClicked(10, 10, 0); wait = 5; });
        PLAN.add(() -> { if (mc.screen != null) mc.screen.mouseClicked(10, 10, 0); wait = 5; });
        PLAN.add(() -> { if (mc.screen != null) mc.screen.mouseClicked(10, 10, 0); wait = 5; });
        PLAN.add(() -> capture(mc, "training_tutorial_4"));
        PLAN.add(() -> { if (mc.screen != null) mc.screen.keyPressed(256, 0, 0); wait = 5; });
        PLAN.add(() -> { mc.setScreen(null); wait = 5; });
        // Namekiano: Gigante como interruptor bajo la Base y Naranja como primera forma de la cadena.
        scene(mc, Race.NAMEKIAN, 2, false, false, false, sp -> {}, "zenkai:node/namek_giant", "namek",
                null, new String[]{"zenkai:namek_orange", "zenkai:node/namek_giant"});
        // Saiyan divino: Evolution de Blue (la hermana Rosé no se dibuja).
        scene(mc, Race.SAIYAN, 4, true, false, true, sp -> {}, "zenkai:ssj_god", "saiyan_evo",
                null, new String[]{"zenkai:ssj_blue", "zenkai:ssj_blue_evolution"});
        // Humano sellado: Indomitable Spirit no puede quedar tapado por la cadena.
        scene(mc, Race.HUMAN, 3, false, false, false, sp -> {}, "zenkai:human_indomitable_spirit", "human_sealed",
                null, new String[]{"zenkai:human_indomitable_spirit", "zenkai:human_flow", "zenkai:node/race_human"});
    }

    /**
     * Árbol de skills por maestro: kami y korin conocidos, kaio y toribot no. Tutorial, un hover por rama
     * (la primera espera el fundido del cielo), skill compartida, maestro desconocido con su pista, la
     * puerta y una subida de nivel real por SkillBuyPacket.
     */
    private static void skillsPlan(Minecraft mc) {
        PLAN.add(() -> {
            setup(mc, Race.SAIYAN, 3, false, false, false, sp -> {
                var att = PlayerStatsAttachment.get(sp);
                att.skills().setLevel("ki_sense", 3);
                att.skills().setLevel("fly", 2);
                att.skills().setLevel("run", 4);
                att.skills().setLevel("meditation", 5);
                att.skills().setLevel("parry_mastery", 4);
                att.markMasterMet("kami");
                att.markMasterMet("korin");
            });
            wait = WAIT;
        });
        // Tutorial con la cámara LEJOS (rama de Tori-Bot): cada paso debe traerla al nodo que resalta.
        PLAN.add(() -> { forgetTutorial(); mc.setScreen(new SkillsTreeScreen()); wait = WAIT; });
        PLAN.add(() -> { if (mc.screen != null) mc.screen.keyPressed(256, 0, 0); wait = 5; });
        PLAN.add(() -> { if (mc.screen instanceof SkillsTreeScreen s) { s.devFocus(3, null); s.devStartTutorial(); } wait = 40; });
        for (int i = 1; i <= 7; i++) {
            final int n = i;
            PLAN.add(() -> capture(mc, "skills_tutorial_" + n));
            PLAN.add(() -> { if (mc.screen != null) mc.screen.mouseClicked(10, 10, 0); wait = 40; });
        }
        PLAN.add(() -> { if (mc.screen != null) mc.screen.keyPressed(256, 0, 0); wait = 5; });
        String[][] hovers = {
                {"0", "m:kami", "kami_master"}, {"0", "ki_sense@kami", "kami_kisense"}, {"0", "meditation@kami", "kami_meditation"},
                {"1", "m:korin", "korin_master"}, {"1", "run@korin", "korin_run"}, {"1", "ki_fist@korin", "korin_fist_unlearned"},
                {"2", "m:kaio", "kaio_master_unknown"}, {"2", "kaioken@kaio", "kaio_kaioken_silhouette"},
                {"2", "meditation@kaio", "kaio_meditation_shared"},
                {"3", "m:toribot", "toribot_master_unknown"}, {"3", "gate", "toribot_gate"}};
        for (String[] h : hovers) {
            PLAN.add(() -> {
                if (mc.screen instanceof SkillsTreeScreen s) {
                    int[] p = s.devFocus(Integer.parseInt(h[0]), h[1]);
                    SkillsTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
                }
                wait = 90; // el fundido del cielo dura 3,2 s
            });
            PLAN.add(() -> capture(mc, "skills_" + h[2]));
        }
        PLAN.add(() -> {
            if (mc.screen instanceof SkillsTreeScreen s) {
                int[] p = s.devFocus(1, "run@korin");
                SkillsTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
                if (p != null) { s.mouseClicked(p[0] + 3, p[1] + 3, 0); s.mouseReleased(p[0] + 3, p[1] + 3, 0); }
            }
            wait = 20;
        });
        PLAN.add(() -> capture(mc, "skills_armed"));
        PLAN.add(() -> {
            if (mc.screen instanceof SkillsTreeScreen s) {
                int[] p = s.devFocus(1, "run@korin");
                if (p != null) { s.mouseClicked(p[0] + 3, p[1] + 3, 0); s.mouseReleased(p[0] + 3, p[1] + 3, 0); }
            }
            wait = WAIT;
        });
        PLAN.add(() -> capture(mc, "skills_bought"));
        PLAN.add(() -> {
            SkillsTreeScreen.devMouse = null;
            if (mc.screen != null) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0,
                    mc.getWindow().getGuiScaledHeight() / 2.0, 0, -1);
            wait = WAIT / 2;
        });
        PLAN.add(() -> capture(mc, "skills_overview"));
    }

    private static net.minecraft.world.entity.LivingEntity absZ1, absZ2, absZ3;

    /** Zombi sin IA delante del jugador (+3 en X), con vida máxima y vida actual dadas. */
    private static net.minecraft.world.entity.LivingEntity absZombie(net.minecraft.server.level.ServerPlayer sp,
                                                                     double maxHealth, double health) {
        return absMob(sp, net.minecraft.world.entity.EntityType.ZOMBIE, maxHealth, health);
    }

    private static net.minecraft.world.entity.LivingEntity absMob(net.minecraft.server.level.ServerPlayer sp,
            net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, double maxHealth, double health) {
        var z = type.create(sp.serverLevel());
        z.moveTo(sp.getX() + 3.0, sp.getY(), sp.getZ(), 0f, 0f);
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        z.setHealth((float) health);
        sp.serverLevel().addFreshEntity(z);
        return z;
    }

    /**
     * Absorción del Majin como TÉCNICA con masa arrojada: (1) un zombi SANO muy por debajo del PL del Majin es absorbido
     * (dominado) lanzando la técnica por ActionResolver, el embudo real; (2) un objetivo FUERTE y sano se rechaza y sigue
     * vivo; (3) el mismo objetivo fuerte al 20 % de vida se absorbe. Todo al log ([TREESHOTS] absorb).
     */
    private static void absorbPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.MAJIN, 2, false, false, false, sp -> {}); wait = WAIT; });
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var level = sp.serverLevel();
                var att = PlayerStatsAttachment.get(sp);
                com.hmc.zenkai.feature.technique.InnateTechniques.ensure(sp, att);
                Zenkai.LOGGER.info("[TREESHOTS] absorb desbloqueada sola={}",
                        att.techniques().isUnlocked(com.hmc.zenkai.feature.technique.PhysicalTechnique.ABSORB));
                com.hmc.zenkai.feature.combat.CombatModeServerState.update(sp, true);
                sp.getInventory().clearContent();
                att.addEnergy(att.getEnergyMax());
                att.consumeStamina(-att.getStaminaMax());
                sp.setYRot(-90f); // mira al +X
                sp.setXRot(0f);
                // El mundo de pruebas es una copia reutilizada: fuera zombis de ejecuciones anteriores y masa vieja.
                for (var old : level.getEntities(net.minecraft.world.entity.EntityType.ZOMBIE, e -> true)) old.discard();
                for (var old : level.getEntities(net.minecraft.world.entity.EntityType.IRON_GOLEM, e -> true)) old.discard();
                att.flags().setAbsorbMass(0);
                att.flags().setAbsorbedTotal(0);
                absZ1 = absZombie(sp, 20, 20);   // sano pero muy débil frente al Majin: dominado
                var r = com.hmc.zenkai.feature.action.ActionResolver.firePhysical(sp,
                        com.hmc.zenkai.feature.technique.PhysicalTechnique.ABSORB);
                Zenkai.LOGGER.info("[TREESHOTS] absorb 1 lanzada: {} motivo={} (PL mio {})", r.reject(),
                        com.hmc.zenkai.feature.race.MajinAbsorption.refusal(sp, absZ1), att.getPowerLevel());
            });
            wait = 30;
        });
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var att = PlayerStatsAttachment.get(sp);
                Zenkai.LOGGER.info("[TREESHOTS] absorb 1 resultado: dominado vivo={} (debe ser false) pilas={}",
                        absZ1.isAlive(), att.flags().getAbsorbMass());
                // Para probar la regla SIN dominio hay que ser débil: se bajan los atributos del Majin hasta
                // acercar su PL al de un zombi (176). Ver el log «PL mio».
                for (var at : new com.hmc.zenkai.feature.ZenkaiAttributes[]{com.hmc.zenkai.feature.ZenkaiAttributes.STRENGTH,
                        com.hmc.zenkai.feature.ZenkaiAttributes.WILLPOWER, com.hmc.zenkai.feature.ZenkaiAttributes.DEXTERITY,
                        com.hmc.zenkai.feature.ZenkaiAttributes.CONSTITUTION, com.hmc.zenkai.feature.ZenkaiAttributes.SPIRIT}) {
                    att.setAttribute(at, 8);
                }
                att.setStatMultiplier(1.0);
                absZ2 = absMob(sp, net.minecraft.world.entity.EntityType.IRON_GOLEM, 100, 100); // casi tan fuerte como yo y SANO -> exige vida baja
                Zenkai.LOGGER.info("[TREESHOTS] absorb PL mio ahora={} (zombi ~176)", att.getPowerLevel());
                Zenkai.LOGGER.info("[TREESHOTS] absorb 2 fuerte y sano: motivo={}",
                        com.hmc.zenkai.feature.race.MajinAbsorption.refusal(sp, absZ2));
                com.hmc.zenkai.feature.race.MajinAbsorption.throwMass(sp);
            });
            wait = 30;
        });
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var att = PlayerStatsAttachment.get(sp);
                Zenkai.LOGGER.info("[TREESHOTS] absorb 2 resultado: fuerte sano vivo={} (debe ser true) pilas={} (debe seguir igual)",
                        absZ2.isAlive(), att.flags().getAbsorbMass());
                absZ2.discard();
                absZ3 = absMob(sp, net.minecraft.world.entity.EntityType.IRON_GOLEM, 100, 20);  // el mismo, al 20 %
                Zenkai.LOGGER.info("[TREESHOTS] absorb 3 fuerte al 20%: motivo={} (debe ser null)",
                        com.hmc.zenkai.feature.race.MajinAbsorption.refusal(sp, absZ3));
                com.hmc.zenkai.feature.race.MajinAbsorption.throwMass(sp);
            });
            wait = 30;
        });
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var att = PlayerStatsAttachment.get(sp);
                Zenkai.LOGGER.info("[TREESHOTS] absorb 3 resultado: vivo={} (debe ser false) pilas={} bono={} total={}",
                        absZ3.isAlive(), att.flags().getAbsorbMass(),
                        com.hmc.zenkai.feature.race.MajinAbsorption.statBonus(sp), att.flags().getAbsorbedTotal());
            });
            wait = 5;
        });
        PLAN.add(() -> capture(mc, "absorb_after"));
    }

    /**
     * Línea del Legendario (solo Saiyan): sin isLegendary las formas están cerradas y el nodo oculto; con él se abre
     * ssj_green (con SSJ1) y ssj_green_max pide el 100 % de maestría de la anterior. La cadena de la rueda NO debe
     * cambiar (nextFrom). Se transforma de verdad en ssj_green para la captura (pelo verde, aura wild, escala). Al log.
     */
    private static void legendaryPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN, 2, false, false, false, sp -> {
            var att = PlayerStatsAttachment.get(sp);
            att.setLegendary(false);
            sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).addFormMastery(
                    com.hmc.zenkai.feature.forms.FormIds.SSJ_GREEN, -1000f);
        }); wait = WAIT; });
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var att = PlayerStatsAttachment.get(sp);
                var G = com.hmc.zenkai.feature.forms.FormIds.SSJ_GREEN;
                var M = com.hmc.zenkai.feature.forms.FormIds.SSJ_GREEN_MAX;
                Zenkai.LOGGER.info("[TREESHOTS] legendary cadena saiyan={}",
                        com.hmc.zenkai.feature.forms.FormRegistry.chainFor(Race.SAIYAN));
                Zenkai.LOGGER.info("[TREESHOTS] legendary SIN flag: ssj1={} verde={} max={} nodoVisible={}",
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, com.hmc.zenkai.feature.forms.FormIds.SSJ1),
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, G),
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, M),
                        com.hmc.zenkai.feature.skilltree.SkillTreeNodes.revealed(sp,
                                com.hmc.zenkai.feature.skilltree.SkillTreeNodes.find(Race.SAIYAN, G)));
                att.setLegendary(true);
                Zenkai.LOGGER.info("[TREESHOTS] legendary CON flag: verde={} max={} (max debe ser false: sin maestría) nodoVisible={}",
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, G),
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, M),
                        com.hmc.zenkai.feature.skilltree.SkillTreeNodes.revealed(sp,
                                com.hmc.zenkai.feature.skilltree.SkillTreeNodes.find(Race.SAIYAN, G)));
                sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).addFormMastery(G, 250f);
                Zenkai.LOGGER.info("[TREESHOTS] legendary verde al 100%: max={} (debe ser true)",
                        com.hmc.zenkai.feature.skills.SuperForms.unlocked(sp, M));
                sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, G);
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            wait = 30;
        });
        PLAN.add(() -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); wait = 40; });
        PLAN.add(() -> capture(mc, "legendary_form_3p"));
        PLAN.add(() -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.setScreen(new SkillTreeScreen()); wait = 30; });
        PLAN.add(() -> {
            if (mc.screen instanceof SkillTreeScreen sc) {
                int[] p = sc.devFocus(ResourceLocation.parse("zenkai:ssj_green"));
                SkillTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
            }
            wait = 90;
        });
        PLAN.add(() -> capture(mc, "legendary_tree"));
        PLAN.add(() -> { SkillTreeScreen.devMouse = null; });
    }

    private static void forgetTutorial() {
        // La herramienta trabaja sobre un mundo de copia, pero el ClientConfig es el de la
        // instalación de desarrollo: se olvidan tutorial y revelados para volver a verlos.
        com.hmc.zenkai.config.ClientConfig.resetSeenForDev();
    }

    /** Pone raza y progreso en el servidor integrado: árbol limpio, super_forms, ritual, audiencia, God Ki. */
    private static void setup(Minecraft mc, Race race, int superForms, boolean ritual, boolean audience,
                              boolean godKi, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> extra) {
        mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setRace(race);
            att.setRaceChosen(true);
            att.skills().clear();
            att.skillTree().clear();
            att.skills().setLevel("super_forms", superForms);
            if (godKi) att.skills().setLevel("god_ki", 1);
            att.setHasSsj4Ritual(ritual);
            att.setReachedSuperOozaru(ritual);
            att.setGodKiAudience(audience);
            att.setDivine(false); // ZenkaiFPTest guarda al jugador como divino: daría God Ki solo
            if (race == Race.ARCOSIAN) att.skills().setLevel("arcosian_power", 1);
            if (godKi) att.skills().setLevel("ultra_instinct", 9);
            att.setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND, 3000);
            att.setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.SPIRIT, 400);
            att.addTP(400000 - att.getTP());
            sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).forceBase();
            att.refreshSkillTree();
            extra.accept(sp);
            PlayerLifeCycle.sync(sp);
            PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        });
    }

    /** Escena: progreso, abrir, hovers, ruta (clic derecho), zoom, una compra real y captura. */
    private static void scene(Minecraft mc, Race race, int superForms, boolean ritual, boolean audience,
                              boolean godKi, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> extra,
                              String buy, String tag, String route, String[] hovers) {
        PLAN.add(() -> { setup(mc, race, superForms, ritual, audience, godKi, extra); wait = WAIT; });
        PLAN.add(() -> { mc.setScreen(new SkillTreeScreen()); wait = 6; });
        PLAN.add(() -> capture(mc, tag + "_1_open")); // a mitad de un posible revelado
        PLAN.add(() -> { wait = 40; });
        for (String h : hovers) {
            PLAN.add(() -> {
                if (mc.screen instanceof SkillTreeScreen s) {
                    int[] p = s.devFocus(ResourceLocation.parse(h));
                    SkillTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
                }
                wait = 30; // el fundido de fondo dura 1 s
            });
            PLAN.add(() -> capture(mc, tag + "_hover_" + ResourceLocation.parse(h).getPath().replace('/', '_')));
        }
        if (route != null) {
            PLAN.add(() -> {
                if (mc.screen instanceof SkillTreeScreen s) {
                    int[] p = s.devFocus(ResourceLocation.parse(route));
                    if (p != null) {
                        s.mouseClicked(p[0] + 3, p[1] + 3, 1);
                        SkillTreeScreen.devMouse = new int[]{p[0] + 3, p[1] + 3};
                    }
                }
                wait = 20;
            });
            PLAN.add(() -> capture(mc, tag + "_route"));
            PLAN.add(() -> {
                if (mc.screen instanceof SkillTreeScreen s) {
                    int[] p = s.devFocus(ResourceLocation.parse(route));
                    if (p != null) s.mouseClicked(p[0] + 3, p[1] + 3, 1); // quitar la ruta
                }
                wait = 2;
            });
        }
        PLAN.add(() -> { SkillTreeScreen.devMouse = null; wait = 2; });
        PLAN.add(() -> {
            if (mc.screen != null) {
                double cx = mc.getWindow().getGuiScaledWidth() / 2.0, cy = mc.getWindow().getGuiScaledHeight() / 2.0 - 20;
                mc.screen.mouseScrolled(cx, cy, 0, 1);
            }
            wait = WAIT / 2;
        });
        PLAN.add(() -> capture(mc, tag + "_2_zoom"));
        PLAN.add(() -> {
            PacketDistributor.sendToServer(new SkillTreeBuyPacket(ResourceLocation.parse(buy)));
            wait = WAIT;
        });
        PLAN.add(() -> { mc.setScreen(new SkillTreeScreen()); wait = WAIT; });
        PLAN.add(() -> {
            if (mc.screen instanceof SkillTreeScreen s) {
                int[] p = s.devFocus(ResourceLocation.parse(buy));
                SkillTreeScreen.devMouse = p == null ? null : new int[]{p[0] + 3, p[1] + 3};
            }
            wait = 10;
        });
        PLAN.add(() -> capture(mc, tag + "_3_bought"));
        PLAN.add(() -> { SkillTreeScreen.devMouse = null; });
    }

    private static void capture(Minecraft mc, String name) {
        if (mc.player != null) {
            var att = PlayerStatsAttachment.get(mc.player);
            Zenkai.LOGGER.info("[TREESHOTS] {} form={} tp={} nodes={}", name,
                    mc.player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId(),
                    att.getTP(), att.skillTree().all());
        }
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("SkillTreeShots: no se pudo capturar {}", name, ex);
        }
    }
}
