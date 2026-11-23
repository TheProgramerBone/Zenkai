package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.*;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeScreen;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillsTreeScreen;
import com.hmc.zenkai.client.gui.screens.wishes.*;
import com.hmc.zenkai.client.gui.wheel.WheelMenu;
import com.hmc.zenkai.client.gui.wheel.WheelScreen;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.network.OpenMasterPayload;
import com.hmc.zenkai.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * HERRAMIENTA DE DESARROLLO: una captura de CADA pantalla del mod (auditoría de diseño/paleta), mismo patrón que
 * SkillTreeShots. Solo se activa si existe {@code <run>/zenkai_screenshots.txt}; su primera línea es la CARPETA del
 * mundo que se abre (usar una COPIA: cambia raza y TP del jugador). Salida en {@code zenkai_screenshots/}; al
 * terminar escribe done.txt y cierra el juego.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScreenShots {
    private ScreenShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 25;

    private static Boolean enabled;
    private static String world;
    /** 2ª línea del trigger: «wishes» = solo las pantallas de deseos de Shenlong. */
    private static String mode = "";
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_screenshots.txt");
            if (trigger.isFile()) {
                try {
                    world = Files.readAllLines(trigger.toPath()).get(0).trim();
                    var lines = Files.readAllLines(trigger.toPath());
                    if (lines.size() > 1) mode = lines.get(1).trim();
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
                capture(mc, "00_vanilla_title");
                opened = true;
                mc.getWindow().setWindowed(1600, 900);
                if (world.startsWith("new:")) createFreshWorld(mc, world.substring(4));
                else mc.createWorldOpenFlows().openWorld(world, () -> {});
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Throwable ex) { Zenkai.LOGGER.warn("ScreenShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_screenshots");
        d.mkdirs();
        return d;
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("ScreenShots: no se pudo capturar {}", name, ex);
        }
    }

    /** Abre la pantalla, espera y captura; si construirla revienta lo deja en el log y sigue con la siguiente. */
    private static void shot(Minecraft mc, String name, Supplier<Screen> s) { shot(mc, name, s, WAIT); }

    /** Igual, con espera propia: las pantallas con fundido de entrada (skill trees) necesitan más de 25 ticks. */
    private static void shot(Minecraft mc, String name, Supplier<Screen> s, int settle) {
        PLAN.add(() -> {
            try { mc.setScreen(s.get()); }
            catch (Throwable ex) { Zenkai.LOGGER.warn("ScreenShots: {} no se pudo abrir", name, ex); mc.setScreen(null); }
            wait = settle;
        });
        PLAN.add(() -> { capture(mc, name); mc.setScreen(null); wait = 5; });
    }

    private static void setup(Minecraft mc, Race race) {
        mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setRace(race);
            att.setRaceChosen(true);
            att.skills().clear();
            att.skills().setLevel("super_forms", 3);
            att.skills().setLevel("ki_sense", 3);
            att.skills().setLevel("fly", 2);
            att.skills().setLevel("run", 4);
            att.skills().setLevel("meditation", 5);
            att.markMasterMet("kami");
            att.markMasterMet("korin");
            att.addTP(60000 - att.getTP());
            att.refreshSkillTree();
            PlayerLifeCycle.sync(sp);
        });
    }

    /** Mundo superplano nuevo en creativo: limpio, sin entidades de pruebas anteriores y con sitio para colocar bloques. */
    private static void createFreshWorld(Minecraft mc, String name) {
        var settings = new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        var options = new net.minecraft.world.level.levelgen.WorldOptions(1234L, false, false);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, access -> access
                .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                .value().createWorldDimensions(), mc.screen);
    }

    /** Pantallas de contenedor: se colocan los bloques delante del jugador y se abren como lo haría el clic derecho. */
    private static void containersPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        containerShot(mc, "80_scouter_bench", com.hmc.zenkai.registry.ModBlocks.SCOUTER_BENCH.get());
        containerShot(mc, "81_energy_generator", com.hmc.zenkai.registry.ModBlocks.ENERGY_GENERATOR.get());
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, ply) -> new com.hmc.zenkai.client.gui.menu.StackWishMenu(id, inv),
                        net.minecraft.network.chat.Component.translatable("screen.zenkai.option.stack")));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "82_wish_stack"); mc.setScreen(null); wait = 5; });
    }

    /**
     * Cámara de contención de ki (modo «chamber», con mundo «new:»): el bloque apagado visto desde fuera, encendido con
     * cada uno de los cuatro tipos de ki (el brillo se fuerza a mano: una carga real exige la pantalla abierta, que
     * taparía el modelo) y la pantalla con el tanque a medias, cargando y convirtiendo a FE.
     */
    private static void chamberPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos pos = sp.blockPosition().offset(3, 0, 0);
            at[0] = pos;
            var lower = com.hmc.zenkai.registry.ModBlocks.KI_CONTAINMENT_CHAMBER.get().defaultBlockState()
                    .setValue(com.hmc.zenkai.content.block.KiContainmentChamberBlock.FACING, net.minecraft.core.Direction.WEST);
            level.setBlock(pos, lower, 3);
            level.setBlock(pos.above(), lower.setValue(com.hmc.zenkai.content.block.KiContainmentChamberBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(
                    com.hmc.zenkai.registry.ModBlocks.KI_CONTAINMENT_CHAMBER.get()));
            sp.teleportTo(level, sp.getX(), sp.getY(), sp.getZ(), -90f, 0f);
        }));
        PLAN.add(() -> wait = 40);
        PLAN.add(() -> { capture(mc, "90_ki_chamber_idle"); wait = 5; });
        // Las seis caras: los dos lados, la espalda, desde arriba y desde abajo (en diagonal), y dos
        // vistas desde dentro (sin sesión: solo se coloca al jugador). Columnas: dx, dy, dz, yaw, pitch.
        float[][] views = { {0.5f, 0f, 3.5f, 180f, 0f}, {0.5f, 0f, -2.5f, 0f, 0f}, {3.5f, 0f, 0.5f, 90f, 0f},
                {-1.2f, 2.6f, -1.2f, -45f, 50f}, {2.2f, 2.6f, 2.2f, 135f, 50f},
                {0.5f, 0f, 0.5f, 0f, 0f}, {0.5f, 0f, 0.5f, 90f, 0f} };
        String[] names = { "side_south", "side_north", "back", "top_front", "top_back", "inside_a", "inside_b" };
        for (int v = 0; v < views.length; v++) {
            final float[] view = views[v];
            final String name = names[v];
            PLAN.add(() -> {
                onServer(mc, sp -> {
                    if (view[1] > 1f) { sp.getAbilities().flying = true; sp.onUpdateAbilities(); }
                    sp.teleportTo(sp.serverLevel(), at[0].getX() + view[0], at[0].getY() + view[1],
                            at[0].getZ() + view[2], view[3], view[4]);
                });
                wait = 15;
            });
            PLAN.add(() -> { capture(mc, "90_ki_chamber_" + name); wait = 3; });
        }
        // Puerta abierta, de frente y en diagonal.
        PLAN.add(() -> {
            onServer(mc, sp -> {
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
                sp.teleportTo(sp.serverLevel(), at[0].getX() - 2.5, at[0].getY(), at[0].getZ() + 0.5, -90f, 0f);
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) be.toggleDoor();
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "90_ki_chamber_door_open"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() - 1.5, at[0].getY() + 0.5, at[0].getZ() - 1.5, -45f, 10f));
            wait = 15;
        });
        PLAN.add(() -> { capture(mc, "90_ki_chamber_door_open_diag"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) be.toggleDoor();
            });
            wait = 20;
        });
        PLAN.add(() -> {
            onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() - 2.5, at[0].getY(), at[0].getZ() + 0.5, -90f, 0f));
            wait = 15;
        });
        for (com.hmc.zenkai.feature.kichamber.KiEnergyType type : com.hmc.zenkai.feature.kichamber.KiEnergyType.values()) {
            PLAN.add(() -> {
                onServer(mc, sp -> {
                    var st = sp.serverLevel().getBlockState(at[0]);
                    sp.serverLevel().setBlock(at[0], st.setValue(com.hmc.zenkai.content.block.KiContainmentChamberBlock.ACTIVE, true), 3);
                });
                wait = 23;
            });
            PLAN.add(() -> {
                if (mc.level != null && mc.level.getBlockEntity(at[0])
                        instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) be.devSetGlow(type.ordinal());
                wait = 4;
            });
            PLAN.add(() -> { capture(mc, "91_ki_chamber_glow_" + type.fluidName()); wait = 3; });
        }
        PLAN.add(() -> {
            onServer(mc, sp -> {
                var level = sp.serverLevel();
                var st = level.getBlockState(at[0]);
                level.setBlock(at[0], st.setValue(com.hmc.zenkai.content.block.KiContainmentChamberBlock.ACTIVE, false), 3);
                if (level.getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    be.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(
                            com.hmc.zenkai.registry.ModFluids.KI_BASE.source().get(), 8000),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    be.toggleConvert();     // apagado: el tanque se queda a medias para la captura
                    // Ranuras de la Fase 2b: Divina y Legendaria toleradas (Súper NO, para la captura de
                    // sobrecarga), dos de velocidad, una de almacenamiento y una batería.
                    var up = be.upgradeHandler();
                    up.setStackInSlot(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.SLOT_TOL_DIVINE,
                            new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_DIVINE.get()));
                    up.setStackInSlot(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.SLOT_TOL_LEGENDARY,
                            new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_LEGENDARY.get()));
                    up.setStackInSlot(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.SLOT_SPEED,
                            new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.MACHINE_SPEED_UPGRADE.get(), 2));
                    up.setStackInSlot(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.SLOT_STORAGE,
                            new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.MACHINE_STORAGE_UPGRADE.get()));
                    up.setStackInSlot(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.SLOT_BATTERY,
                            new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
                    sp.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.KI_TOLERANCE_SUPER.get()));
                    sp.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
                    PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                    att.addKi(att.getKiPool());
                    be.openMenuFor(sp);
                }
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "92_ki_chamber_gui_ready"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    // Entrar de verdad: puerta abierta y el jugador dentro; el ticker lo atrapa y arranca.
                    be.toggleDoor();
                    sp.teleportTo(sp.serverLevel(), at[0].getX() + 0.5, at[0].getY(), at[0].getZ() + 0.5, 90f, 0f);
                }
            });
            wait = 60;
        });
        PLAN.add(() -> { capture(mc, "93_ki_chamber_inside_charging"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    be.openMenuFor(sp);
                }
            });
            wait = 20;
        });
        PLAN.add(() -> { capture(mc, "93_ki_chamber_gui_charging"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    be.toggleConvert();
                }
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "94_ki_chamber_gui_converting"); wait = 3; });
        // Sobrecarga: en SSJ1 y sin la tolerancia de Súper. Se limpia el cooldown y el tanque (tenía Ki Base)
        // para poder cargar otra vez, y se captura pasado el 40 % de calor, con la alarma ya sonando.
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    if (!be.isFree()) be.toggleDoor();   // libera al jugador y deja la puerta abierta
                    be.toggleDoor();                     // cerrada, para que no lo atrape antes de tiempo
                    be.tank().setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
                    PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                    att.flags().setKiChamberCooldownUntil(0L);
                    att.addKi(att.getKiPool());
                    sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get())
                            .setFormId(com.hmc.zenkai.feature.forms.FormIds.SSJ1);
                    PlayerLifeCycle.syncForm(sp);
                    PlayerLifeCycle.sync(sp);
                }
            });
            wait = 20;
        });
        PLAN.add(() -> { capture(mc, "96_ki_chamber_gui_no_tolerance"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity be) {
                    be.toggleDoor();                     // abierta: el jugador sigue dentro y vuelve a ser atrapado
                }
            });
            wait = 125;
        });
        PLAN.add(() -> { capture(mc, "97_ki_chamber_gui_overload"); mc.player.closeContainer(); wait = 30; });
        PLAN.add(() -> { capture(mc, "95_ki_chamber_after_exit"); wait = 3; });
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static void containerShot(Minecraft mc, String name, net.minecraft.world.level.block.Block block) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var level = sp.serverLevel();
                BlockPos pos = sp.blockPosition().offset(2, 0, 0);
                level.setBlock(pos, block.defaultBlockState(), 3);
                block.defaultBlockState().useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(
                        pos.getCenter(), net.minecraft.core.Direction.UP, pos, false));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, name); mc.setScreen(null); wait = 5; });
    }

    /**
     * Tanques multibloque (modo «tanks», con mundo «new:»): un tanque de energía 2x2x3 al 40 %, uno de fluidos
     * 3x3x2 con agua al 60 %, uno suelto de energía con placa de salida y otro de lava con una de entrada, vistos desde las cuatro
     * esquinas y desde arriba. Sirve para ver que la textura conectada empalma y que el marco va por FUERA
     * del grupo en cada cara (si la izquierda y la derecha de una cara estuvieran cambiadas, saldría por dentro).
     */
    private static void tanksPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos o = sp.blockPosition().offset(4, 0, -4);
            at[0] = o;
            var energy = com.hmc.zenkai.registry.ModBlocks.ENERGY_TANK.get().defaultBlockState();
            var fluid = com.hmc.zenkai.registry.ModBlocks.FLUID_TANK.get().defaultBlockState();
            for (int y = 0; y < 3; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setBlock(o.offset(x, y, z), energy, 3);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) level.setBlock(o.offset(x, y, z + 4), fluid, 3);
            level.setBlock(o.offset(0, 0, 9), energy, 3);
            level.setBlock(o.offset(0, 0, 11), fluid, 3);
            // Niveles para ver el cristal: agua al ~60 % del 3x3x2 (sube por las dos capas), energía al
            // ~40 % del 2x2x3, el suelto de energía a la mitad y el suelto de fluidos con lava (brilla).
            fillFluid(level, o.offset(0, 0, 4), net.minecraft.world.level.material.Fluids.WATER, 0.6);
            fillFluid(level, o.offset(0, 0, 11), net.minecraft.world.level.material.Fluids.LAVA, 0.5);
            fillEnergy(level, o, 0.4);
            fillEnergy(level, o.offset(0, 0, 9), 0.5);
            if (level.getBlockEntity(o.offset(0, 0, 9)) instanceof com.hmc.zenkai.content.blockentity.TransportBlockEntity t) {
                t.cycleMode(net.minecraft.core.Direction.WEST);   // salida
            }
            if (level.getBlockEntity(o.offset(0, 0, 11)) instanceof com.hmc.zenkai.content.blockentity.TransportBlockEntity t) {
                t.cycleMode(net.minecraft.core.Direction.WEST);
                t.cycleMode(net.minecraft.core.Direction.WEST);   // entrada
            }
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        }));
        PLAN.add(() -> wait = 40);
        // dx, dy, dz desde la esquina del grupo de energía, yaw, pitch.
        float[][] views = { {-4.5f, 2.5f, -3.5f, -45f, 20f}, {6.5f, 2.5f, -3.5f, 45f, 20f},
                {-5.5f, 2.5f, 13.5f, -135f, 20f}, {7.5f, 2.5f, 13.5f, 135f, 20f},
                {-6f, 1.5f, 6f, -90f, 10f}, {1.5f, 9f, 4.5f, 0f, 89f} };
        String[] names = { "nw", "ne", "sw", "se", "west", "top" };
        for (int v = 0; v < views.length; v++) {
            final float[] view = views[v];
            final String name = names[v];
            PLAN.add(() -> {
                onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() + view[0], at[0].getY() + view[1],
                        at[0].getZ() + view[2], view[3], view[4]));
                wait = 20;
            });
            PLAN.add(() -> { capture(mc, "98_tanks_" + name); wait = 3; });
        }
    }

    /**
     * Consola de la cámara de gravedad (modo «gravity», con mundo «new:»): una apagada y otra encendida, las dos
     * mirando al norte sobre un escalón de piedra, vistas desde cada cara (frente, espalda, oeste, este), en
     * diagonal, de perfil a la altura del cabezal, desde arriba y como la ve un jugador de pie delante. Sirve para
     * comprobar que cada cara lee su propio mapa de textura (frente != espalda, oeste != este) y que el panel
     * inclinado baja hacia el jugador con la pantalla derecha.
     */
    private static void gravityPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos o = sp.blockPosition().offset(0, 0, 5);
            at[0] = o.above();
            var console = com.hmc.zenkai.registry.ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState();
            var stone = net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
            level.setBlock(o, stone, 3);
            level.setBlock(o.offset(4, 0, 0), stone, 3);
            level.setBlock(o.above(), console, 3);
            level.setBlock(o.offset(4, 1, 0), console.setValue(com.hmc.zenkai.content.block.GravityChamberBlock.ACTIVE, true), 3);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            level.setDayTime(6000);
        }));
        PLAN.add(() -> wait = 40);
        // dx, dy (pies, desde la base de la consola), dz desde el centro de la consola, yaw, pitch; el último campo
        // es 1 para la encendida (4 bloques al este).
        float[][] views = {
                {0f, 0.2f, -2.3f, 0f, 12f, 0}, {0f, 0.2f, 2.3f, 180f, 12f, 0},
                {-2.3f, 0.2f, 0f, -90f, 12f, 0}, {2.3f, 0.2f, 0f, 90f, 12f, 0},
                {-1.7f, 0.4f, -1.7f, -45f, 20f, 0}, {1.7f, 0.4f, 1.7f, 135f, 20f, 0},
                {-2.2f, -1.0f, 0f, -90f, 4f, 0}, {2.2f, -1.0f, 0f, 90f, 4f, 0},
                {0f, 2.6f, 0.01f, 0f, 89f, 0}, {0f, 0f, -1.6f, 0f, 24f, 0},
                {0f, 0f, -1.6f, 0f, 24f, 1}, {-1.7f, 0.4f, -1.7f, -45f, 20f, 1} };
        String[] names = { "front", "back", "west", "east", "diag_nw", "diag_se", "profile_west", "profile_east",
                "top", "player_view", "on_player_view", "on_diag_nw" };
        for (int v = 0; v < views.length; v++) {
            final float[] view = views[v];
            final String name = names[v];
            PLAN.add(() -> {
                double cx = at[0].getX() + 0.5 + (view[5] > 0 ? 4 : 0), cz = at[0].getZ() + 0.5;
                onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), cx + view[0], at[0].getY() + view[1],
                        cz + view[2], view[3], view[4]));
                wait = 20;
            });
            PLAN.add(() -> { capture(mc, "A9_gravity_" + name); wait = 3; });
        }
    }

    /**
     * Drones de entrenamiento (modo «drones», con mundo «new:»; Fase 4): sala esférica de CRISTAL de radio 6 (4 drones)
     * con la consola a ×10 y cuatro drones con desgaste 0..3. Captura la pantalla en la vista Drones parada y con una
     * ronda en marcha, la ronda pasiva desde dentro (todos pendientes y luego dos golpeados, ojo verde) y la agresiva
     * (disparando), más una vista desde fuera a través del cristal.
     */
    private static void dronesPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        final int radius = 6;
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos console = sp.blockPosition().offset(0, 0, 9);
            at[0] = console;
            level.setBlock(console, com.hmc.zenkai.registry.ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(console) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)) return;
            be.setRadiusForTests(radius);
            BlockPos c = com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity.sphereCenter(console, radius);
            for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) for (int dz = -radius; dz <= radius; dz++) {
                BlockPos q = c.offset(dx, dy, dz);
                if (!q.equals(console) && com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity.insideSphere(dx, dy, dz, radius))
                    level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            for (long k : be.shell()) level.setBlock(BlockPos.of(k), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 2);
            be.upgradeHandler().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.GRAVITY_UPGRADE_COPPER.get()));
            be.setTargetGravityForTests(10);
            be.fillEnergyForTests(200_000);
            for (int i = 0; i < 4; i++) {
                var d = new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.TRAINING_DRONE.get());
                d.setDamageValue(i * 3);
                be.droneHandler().setStackInSlot(i, d);
            }
            be.cycleDroneDifficulty();       // Normal -> Difícil: que se vea la esquiva y el daño mayor
            be.cycleDroneDifficulty();
            be.revalidateNow();
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(level, console.getX() + 0.5, console.getY() + 2, console.getZ() - 3.5, 0f, 10f);
            level.setDayTime(6000);
        }));
        PLAN.add(() -> wait = 60);
        Runnable openScreen = () -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)
                    sp.openMenu(be, buf -> { buf.writeBlockPos(at[0]); buf.writeBoolean(true); });
            });
            com.hmc.zenkai.client.gui.screens.GravityChamberScreen.devSetDroneView(true);
            wait = 25;
        };
        PLAN.add(openScreen);
        PLAN.add(() -> { capture(mc, "D0_drones_screen_idle"); wait = 3; });
        PLAN.add(() -> { com.hmc.zenkai.client.gui.screens.GravityChamberScreen.devSetDroneView(false); wait = 5; });
        PLAN.add(() -> { capture(mc, "D0b_gravity_view"); com.hmc.zenkai.client.gui.screens.GravityChamberScreen.devSetDroneView(true); wait = 5; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be) be.pressRound(sp);
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "D1_drones_screen_running"); mc.setScreen(null); wait = 30; });
        PLAN.add(() -> { capture(mc, "D2_drones_passive"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (!(sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)) return;
                var list = sp.serverLevel().getEntitiesOfClass(com.hmc.zenkai.content.entity.misc.TrainingDroneEntity.class,
                        new net.minecraft.world.phys.AABB(at[0]).inflate(12));
                for (int i = 0; i < Math.min(2, list.size()); i++) be.droneRound().onDroneHit(sp.serverLevel(), list.get(i), sp);
            });
            wait = 20;
        });
        PLAN.add(() -> { capture(mc, "D3_drones_passive_two_hit"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() + 0.5, at[0].getY() + 1.6, at[0].getZ() - 2.5, 0f, 25f));
            wait = 20;
        });
        PLAN.add(() -> { capture(mc, "D4_drones_passive_close"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (!(sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)) return;
                be.pressRound(sp);          // cancela la pasiva
                be.toggleDroneMode();
                be.pressRound(sp);          // empieza la agresiva
                sp.teleportTo(sp.serverLevel(), at[0].getX() + 0.5, at[0].getY() + 2, at[0].getZ() - 3.5, 0f, 10f);
            });
            wait = 50;
        });
        PLAN.add(() -> { capture(mc, "D5_drones_aggressive_a"); wait = 25; });
        PLAN.add(() -> { capture(mc, "D6_drones_aggressive_b"); wait = 3; });
        PLAN.add(openScreen);
        PLAN.add(() -> { capture(mc, "D7_drones_screen_aggressive"); mc.setScreen(null); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() + 0.5, at[0].getY() + 6, at[0].getZ() - 11, 0f, 15f));
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "D8_drones_from_outside"); wait = 3; });
        // Resultado de la última ronda en la vista Drones y el estado más largo de la vista Gravedad (sin mejora).
        PLAN.add(() -> {
            onServer(mc, sp -> {
                sp.teleportTo(sp.serverLevel(), at[0].getX() + 0.5, at[0].getY() + 2, at[0].getZ() - 3.5, 0f, 10f);
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)
                    be.droneRound().cancel(sp.serverLevel());
            });
            wait = 10;
        });
        PLAN.add(openScreen);
        PLAN.add(() -> { capture(mc, "D9_drones_last_result"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be)
                    be.upgradeHandler().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
            });
            com.hmc.zenkai.client.gui.screens.GravityChamberScreen.devSetDroneView(false);
            wait = 15;
        });
        PLAN.add(() -> { capture(mc, "DA_gravity_no_upgrade"); mc.setScreen(null); wait = 3; });
    }

    /**
     * Motor de Ki (modo «motor», con mundo «new:», necesita Create en run/mods): dos motores, uno con el eje
     * mirando al jugador y otro de espaldas, vistos de frente, de espaldas y en diagonal; luego la pantalla
     * en modo motor (FE a medias, Ki Súper en el tanque, un puerto en Tirar y otro en Nada) y en modo dínamo.
     */
    private static void motorPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos pos = sp.blockPosition().offset(3, 0, 0);
            at[0] = pos;
            var motor = com.hmc.zenkai.registry.ModBlocks.KI_MOTOR.get().defaultBlockState();
            var facing = com.hmc.zenkai.compat.create.KiMotorBlock.FACING;
            level.setBlock(pos, motor.setValue(facing, net.minecraft.core.Direction.WEST), 3);
            level.setBlock(pos.offset(0, 0, 2), motor.setValue(facing, net.minecraft.core.Direction.EAST), 3);
            level.setBlock(pos.offset(0, 0, -2), motor.setValue(facing, net.minecraft.core.Direction.UP), 3);
            sp.teleportTo(level, sp.getX(), sp.getY(), sp.getZ(), -90f, 0f);
        }));
        PLAN.add(() -> wait = 40);
        PLAN.add(() -> { capture(mc, "99_ki_motor_front"); wait = 3; });
        float[][] views = { {-1.5f, 1.2f, -2.5f, -45f, 25f}, {5.5f, 1.2f, 3.5f, 135f, 25f}, {1.5f, 0.6f, 0.5f, -90f, 20f} };
        String[] names = { "diag_front", "diag_back", "close" };
        for (int v = 0; v < views.length; v++) {
            final float[] view = views[v];
            final String name = names[v];
            PLAN.add(() -> {
                onServer(mc, sp -> sp.teleportTo(sp.serverLevel(), at[0].getX() + view[0], at[0].getY() + view[1],
                        at[0].getZ() + view[2], view[3], view[4]));
                wait = 20;
            });
            PLAN.add(() -> { capture(mc, "99_ki_motor_" + name); wait = 3; });
        }
        PLAN.add(() -> {
            onServer(mc, sp -> {
                sp.teleportTo(sp.serverLevel(), at[0].getX() - 2.5, at[0].getY(), at[0].getZ() + 0.5, -90f, 0f);
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.compat.create.KiMotorBlockEntity be) {
                    var e = be.energyAt(null);
                    e.receiveEnergy(e.getMaxEnergyStored(), false);   // lleno: si no, el fluido se convierte y el tanque sale vacío
                    be.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(
                            com.hmc.zenkai.registry.ModFluids.KI_SUPER.source().get(), be.tank().getCapacity() / 2),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    be.cyclePort(net.minecraft.core.Direction.SOUTH, false);
                    be.cyclePort(net.minecraft.core.Direction.SOUTH, false);   // Tirar
                    for (int i = 0; i < 3; i++) be.cyclePort(net.minecraft.core.Direction.UP, true);   // Nada
                }
            });
            wait = 30;
        });
        PLAN.add(() -> { mc.setScreen(new com.hmc.zenkai.client.gui.screens.KiMotorScreen(at[0])); wait = 30; });
        PLAN.add(() -> { capture(mc, "99_ki_motor_gui_motor"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.compat.create.KiMotorBlockEntity be) {
                    be.setMode(com.hmc.zenkai.compat.create.KiMotorControl.Mode.GENERATOR);
                }
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "99_ki_motor_gui_dynamo"); mc.setScreen(null); wait = 5; });
    }

    /**
     * Tanda de tecnología 0.7.0 (modo «tech», con mundo «new:»): los cuatro ki derramados con agua y lava al
     * lado (interacciones), la pantalla del tanque de energía con una batería, la del de fluidos con Ki Súper y
     * un cubo vacío, y, con Create, el motor con su cruz.
     */
    private static void techPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos o = sp.blockPosition().offset(4, 0, -4);
            at[0] = o;
            var kis = com.hmc.zenkai.feature.kichamber.KiEnergyType.values();
            for (int i = 0; i < kis.length; i++) {
                BlockPos p = o.offset(0, 0, i * 3);
                level.setBlock(p, kis[i].fluid().source().get().defaultFluidState().createLegacyBlock(), 3);
            }
            // Agua y lava a un bloque de distancia del Base y del Súper: al fluir, se solidifican.
            level.setBlock(o.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(o.offset(2, 0, 3), net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 3);
            sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.WRENCH.get()));
            sp.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.KI_DIVINE_BUCKET.get()));
            sp.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET, 4));
            sp.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(level, o.getX() - 4.5, o.getY() + 3.5, o.getZ() + 4.5, -90f, 35f);
        }));
        PLAN.add(() -> wait = 100);   // que fluyan y reaccionen
        PLAN.add(() -> { capture(mc, "A0_ki_fluids_world"); wait = 3; });

        // Tanques: uno de energía al 50 % con una batería vacía en «cargar» y uno de fluidos con Ki Súper.
        final BlockPos[] tanks = new BlockPos[2];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos e = at[0].offset(-3, 0, -3), f = at[0].offset(-3, 0, -6);
            tanks[0] = e; tanks[1] = f;
            level.setBlock(e, com.hmc.zenkai.registry.ModBlocks.ENERGY_TANK.get().defaultBlockState(), 3);
            level.setBlock(f, com.hmc.zenkai.registry.ModBlocks.FLUID_TANK.get().defaultBlockState(), 3);
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(level, e.getX() - 2.5, e.getY(), e.getZ() + 0.5, -90f, 0f);
        }));
        PLAN.add(() -> wait = 30);
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            fillEnergy(level, tanks[0], 0.5);
            fillFluid(level, tanks[1], com.hmc.zenkai.registry.ModFluids.KI_SUPER.source().get(), 0.4);
            if (level.getBlockEntity(tanks[0]) instanceof com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity t) {
                t.items().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
            }
            if (level.getBlockEntity(tanks[1]) instanceof com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity t) {
                t.items().setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET, 3));
            }
            var st = level.getBlockState(tanks[0]);
            st.useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(tanks[0].getCenter(),
                    net.minecraft.core.Direction.WEST, tanks[0], false));
        }));
        PLAN.add(() -> wait = 40);
        PLAN.add(() -> { capture(mc, "A1_tank_energy_gui"); if (mc.player != null) mc.player.closeContainer(); wait = 10; });
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            var st = level.getBlockState(tanks[1]);
            st.useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(tanks[1].getCenter(),
                    net.minecraft.core.Direction.WEST, tanks[1], false));
        }));
        PLAN.add(() -> wait = 40);
        PLAN.add(() -> { capture(mc, "A2_tank_fluid_gui"); if (mc.player != null) mc.player.closeContainer(); wait = 10; });

        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            final BlockPos[] motor = new BlockPos[1];
            PLAN.add(() -> onServer(mc, sp -> {
                var level = sp.serverLevel();
                BlockPos m = tanks[0].offset(0, 0, 3);
                motor[0] = m;
                level.setBlock(m, com.hmc.zenkai.registry.ModBlocks.KI_MOTOR.get().defaultBlockState()
                        .setValue(com.hmc.zenkai.compat.create.KiMotorBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
                sp.teleportTo(level, m.getX() - 2.5, m.getY(), m.getZ() + 0.5, -90f, 0f);
            }));
            PLAN.add(() -> wait = 30);
            PLAN.add(() -> { mc.setScreen(new com.hmc.zenkai.client.gui.screens.KiMotorScreen(motor[0])); wait = 25; });
            PLAN.add(() -> { capture(mc, "A4_ki_motor_cross"); mc.setScreen(null); wait = 5; });
        }
    }

    /**
     * Escenas Ponder de la tecnología (modo «ponder», con mundo «new:»): abre la escena de cada bloque como el
     * botón «?» de su tooltip y la captura dos veces, al poco de empezar y avanzada con seekToTime.
     * Nombra PonderUI solo dentro de las lambdas: sin Ponder este plan no se usa y la clase no se carga.
     */
    private static void ponderPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        java.util.List<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>> items = java.util.List.of(
                com.hmc.zenkai.registry.ModBlocks.KI_CONTAINMENT_CHAMBER, com.hmc.zenkai.registry.ModBlocks.ENERGY_CABLE,
                com.hmc.zenkai.registry.ModBlocks.FLUID_PIPE, com.hmc.zenkai.registry.ModBlocks.ENERGY_TANK,
                com.hmc.zenkai.registry.ModItems.WRENCH, com.hmc.zenkai.registry.ModBlocks.KI_MOTOR);
        String[] names = {"chamber", "cables", "pipes", "tanks", "wrench", "motor"};
        for (int i = 0; i < items.size(); i++) {
            final int k = i;
            PLAN.add(() -> {
                mc.setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(
                        new net.minecraft.world.item.ItemStack(items.get(k).get())));
                wait = 80;
            });
            PLAN.add(() -> { capture(mc, "B" + k + "_ponder_" + names[k] + "_a"); wait = 2; });
            PLAN.add(() -> {
                if (mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui) ui.seekToTime(260);
                wait = 40;
            });
            PLAN.add(() -> { capture(mc, "B" + k + "_ponder_" + names[k] + "_b"); mc.setScreen(null); wait = 10; });
        }
    }

    /**
     * Llave y ranuras (modo «wrench», con mundo «new:»): sombreado de puertos con la llave en la mano sobre un
     * tanque con una cara de cada modo y sobre el Motor de Ki (canal energía y canal fluido), y las pantallas del
     * generador (tres ranuras de carga) y del tanque de energía (3 + 3).
     */
    private static void wrenchPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[4];
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos tank = sp.blockPosition().offset(3, 0, 0);
            at[0] = tank;
            at[1] = tank.offset(0, 0, 3);
            at[2] = tank.offset(0, 0, -3);
            at[3] = tank.offset(0, 0, -6);
            level.setBlock(tank, com.hmc.zenkai.registry.ModBlocks.FLUID_TANK.get().defaultBlockState(), 3);
            if (level.getBlockEntity(tank) instanceof com.hmc.zenkai.content.blockentity.TransportBlockEntity t) {
                t.setMode(net.minecraft.core.Direction.WEST, com.hmc.zenkai.feature.transport.SideMode.PUSH);
                t.setMode(net.minecraft.core.Direction.UP, com.hmc.zenkai.feature.transport.SideMode.PULL);
                t.setMode(net.minecraft.core.Direction.SOUTH, com.hmc.zenkai.feature.transport.SideMode.NONE);
            }
            if (net.neoforged.fml.ModList.get().isLoaded("create")) {
                level.setBlock(at[1], com.hmc.zenkai.registry.ModBlocks.KI_MOTOR.get().defaultBlockState()
                        .setValue(com.hmc.zenkai.compat.create.KiMotorBlock.FACING, net.minecraft.core.Direction.EAST), 3);
                if (level.getBlockEntity(at[1]) instanceof com.hmc.zenkai.feature.sideconfig.SideConfigurable c) {
                    c.setSideMode(com.hmc.zenkai.feature.sideconfig.SideConfigurable.Channel.ENERGY,
                            net.minecraft.core.Direction.WEST, com.hmc.zenkai.feature.transport.SideMode.PULL);
                    c.setSideMode(com.hmc.zenkai.feature.sideconfig.SideConfigurable.Channel.FLUID,
                            net.minecraft.core.Direction.WEST, com.hmc.zenkai.feature.transport.SideMode.NONE);
                    c.setSideMode(com.hmc.zenkai.feature.sideconfig.SideConfigurable.Channel.FLUID,
                            net.minecraft.core.Direction.UP, com.hmc.zenkai.feature.transport.SideMode.PUSH);
                }
            }
            level.setBlock(at[2], com.hmc.zenkai.registry.ModBlocks.ENERGY_GENERATOR.get().defaultBlockState(), 3);
            level.setBlock(at[3], com.hmc.zenkai.registry.ModBlocks.ENERGY_TANK.get().defaultBlockState(), 3);
            sp.getInventory().selected = 0;
            sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.WRENCH.get()));
            sp.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
            // Mirando al tanque desde arriba-izquierda: se ven la cara oeste, la de arriba y la sur.
            lookAt(sp, tank, -1.6, 1.0, 2.2);
        }));
        PLAN.add(() -> wait = 40);
        PLAN.add(() -> { capture(mc, "C0_wrench_tank"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> lookAt(sp, at[1], -1.6, 1.0, 2.2));
            wait = 25;
        });
        PLAN.add(() -> { capture(mc, "C1_wrench_motor_energy"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> net.minecraft.world.item.component.CustomData.update(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA, sp.getMainHandItem(), t -> t.putInt("Mode", 1)));
            wait = 15;
        });
        PLAN.add(() -> { capture(mc, "C2_wrench_motor_fluid"); wait = 3; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                sp.teleportTo(sp.serverLevel(), at[2].getX() - 2.5, at[2].getY(), at[2].getZ() + 0.5, -90f, 0f);
                var st = sp.serverLevel().getBlockState(at[2]);
                st.useWithoutItem(sp.serverLevel(), sp, new net.minecraft.world.phys.BlockHitResult(at[2].getCenter(),
                        net.minecraft.core.Direction.WEST, at[2], false));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "C3_generator_gui"); if (mc.player != null) mc.player.closeContainer(); wait = 10; });
        PLAN.add(() -> {
            onServer(mc, sp -> {
                var level = sp.serverLevel();
                fillEnergy(level, at[3], 0.6);
                if (level.getBlockEntity(at[3]) instanceof com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity t) {
                    t.items().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
                    t.items().setStackInSlot(4, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get()));
                }
                var st = level.getBlockState(at[3]);
                st.useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(at[3].getCenter(),
                        net.minecraft.core.Direction.WEST, at[3], false));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "C4_tank_energy_gui"); if (mc.player != null) mc.player.closeContainer(); wait = 10; });
        // Tanque de energía de 1x2 cargado con una placa de Tirar en la cara oeste de abajo (el nivel debe verse
        // ALREDEDOR de la placa) y un cable pegado a esa cara (sombreado de la llave sin paneles flotando).
        PLAN.add(() -> onServer(mc, sp -> {
            var level = sp.serverLevel();
            BlockPos t = at[3];
            level.setBlock(t.above(), com.hmc.zenkai.registry.ModBlocks.ENERGY_TANK.get().defaultBlockState(), 3);
            level.setBlock(t.west(), com.hmc.zenkai.registry.ModBlocks.ENERGY_CABLE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(t) instanceof com.hmc.zenkai.content.blockentity.TransportBlockEntity b) {
                b.setMode(net.minecraft.core.Direction.WEST, com.hmc.zenkai.feature.transport.SideMode.PULL);
            }
        }));
        PLAN.add(() -> wait = 30);
        PLAN.add(() -> onServer(mc, sp -> fillEnergy(sp.serverLevel(), at[3], 0.95)));
        PLAN.add(() -> {
            onServer(mc, sp -> {
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        sp.getMainHandItem(), tag -> tag.putInt("Mode", 0));
                lookAt(sp, at[3].west(), -2.2, 0.2, 1.4);
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "C5_tank_plate_fill_cable_overlay"); wait = 3; });
        // Modo Rotar sobre el generador: se gira con el evento real (una llamada a la misma ruta que el clic).
        PLAN.add(() -> {
            onServer(mc, sp -> {
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        sp.getMainHandItem(), tag -> tag.putInt("Mode", 2));
                var hit = new net.minecraft.world.phys.BlockHitResult(at[2].getCenter(), net.minecraft.core.Direction.WEST, at[2], false);
                var ev = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                        sp, net.minecraft.world.InteractionHand.MAIN_HAND, at[2], hit);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
                Zenkai.LOGGER.info("ScreenShots: generador tras rotar = {}", sp.serverLevel().getBlockState(at[2]));
                lookAt(sp, at[2], -2.2, 0.2, 1.4);
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "C6_rotate_generator"); wait = 3; });
    }

    /** Coloca al jugador en target + (dx, dy, dz) mirando al CENTRO del bloque (para que la mira lo apunte). */
    private static void lookAt(net.minecraft.server.level.ServerPlayer sp, BlockPos target, double dx, double dy, double dz) {
        double x = target.getX() + 0.5 + dx, y = target.getY() + dy, z = target.getZ() + 0.5 + dz;
        double ex = target.getX() + 0.5 - x, ey = target.getY() + 1.1 - (y + sp.getEyeHeight()), ez = target.getZ() + 0.5 - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-ex, ez));
        float pitch = (float) Math.toDegrees(-Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
        sp.teleportTo(sp.serverLevel(), x, y, z, yaw, pitch);
    }

    private static void fillFluid(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                  net.minecraft.world.level.material.Fluid fluid, double fraction) {
        if (!(level.getBlockEntity(pos) instanceof com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity t)) return;
        var h = t.fluidAt(null);
        int want = (int) (h.getTankCapacity(0) * fraction);
        for (int i = 0; i < 5000 && h.getFluidInTank(0).getAmount() < want; i++) {
            int n = Math.min(2_000, want - h.getFluidInTank(0).getAmount());
            if (h.fill(new net.neoforged.neoforge.fluids.FluidStack(fluid, n),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) <= 0) break;
        }
    }

    private static void fillEnergy(net.minecraft.server.level.ServerLevel level, BlockPos pos, double fraction) {
        if (!(level.getBlockEntity(pos) instanceof com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity t)) return;
        var e = t.energyAt(null);
        int want = (int) (e.getMaxEnergyStored() * fraction);
        for (int i = 0; i < 100_000 && e.getEnergyStored() < want; i++) {
            if (e.receiveEnergy(want - e.getEnergyStored(), false) <= 0) break;
        }
    }

    private static void wishesPlan(Minecraft mc) {
        shot(mc, "70_wish_main", ShenlongWishScreen::new);
        shot(mc, "71_wish_immortal", () -> new ImmortalWishScreen(null));
        shot(mc, "72_wish_legendary", () -> new LegendaryWishScreen(null));
        shot(mc, "73_wish_revive_player", () -> new RevivePlayerWishScreen(null));
        shot(mc, "74_wish_revive_pet", () -> new RevivePetWishScreen(null));
        shot(mc, "75_wish_enchant_villager", () -> new EnchantVillagerWishScreen(null));
        shot(mc, "76_wish_training_points", () -> new TrainingPointsWishScreen(null));
        shot(mc, "77_wish_potential", () -> new PotentialWishScreen(null));
    }

    /** Solo el hub y los minijuegos de Training (rediseño de la plantilla de minijuegos). */
    private static void trainingPlan(Minecraft mc) {
        shot(mc, "12_tab_training", TrainingHubScreen::new);
        shot(mc, "30_training_shadow", ShadowTrainingScreen::new);
        shot(mc, "31_training_meditation", MeditationScreen::new);
        shot(mc, "32_training_target", TargetPracticeScreen::new);
        shot(mc, "33_training_shadow_result", () -> new ShadowResultScreen(120, 340));
        shot(mc, "34_training_shadow_result_record", () -> new ShadowResultScreen(120, 120));
    }

    /**
     * Máquina de fabricación (modo «fabricator», con mundo «new:»): pestaña Fabricar pausada y en marcha, pestaña
     * Atributos vacía, con pieza imbuida + sangre (Ki Base), con una pieza que pide Ki Súper y Base en el tanque
     * (rojo) y con Ki Divino (la mitad). Primero en el idioma del juego y luego en es_mx (se restaura al acabar).
     * Con JEI en run/mods, además las tres categorías propias (fabricación, imbuir, sangre). Prefijo F.
     */
    private static void fabricatorPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        final BlockPos[] at = new BlockPos[1];
        final String originalLang = mc.options.languageCode;
        PLAN.add(() -> onServer(mc, sp -> {
            BlockPos pos = sp.blockPosition().offset(2, 0, 0);
            at[0] = pos;
            sp.serverLevel().setBlock(pos, com.hmc.zenkai.registry.ModBlocks.ARMOR_FABRICATOR.get().defaultBlockState(), 3);
        }));
        PLAN.add(() -> wait = 10);
        for (String lang : new String[]{"", "es_mx"}) {
            String sfx = lang.isEmpty() ? "" : "_" + lang;
            if (!lang.isEmpty()) {
                PLAN.add(() -> {
                    mc.setScreen(null);
                    mc.options.languageCode = lang;
                    mc.getLanguageManager().setSelected(lang);
                    mc.reloadResourcePacks();
                    wait = 120;
                });
            }
            fabricatorStep(mc, at, "F0_craft_paused" + sfx, be -> {
                be.fillEnergyForTests(60_000);
                be.tank().setFluid(new net.neoforged.neoforge.fluids.FluidStack(
                        com.hmc.zenkai.feature.kichamber.KiEnergyType.BASE.fluid().source().get(), 4000));
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_PATTERN,
                        com.hmc.zenkai.feature.armor.ArmorSets.pattern(com.hmc.zenkai.feature.armor.ArmorSets.SAIYAN_ARMOR));
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST,
                        new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.KATCHIN_INGOT.get(), 20));
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_OUTPUT,
                        net.minecraft.world.item.ItemStack.EMPTY);
                be.selectPiece(1);
                if (be.craftEnabled()) be.toggleCraft();
                be.setLastTab(com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu.TAB_CRAFT);
            });
            fabricatorStep(mc, at, "F1_craft_working" + sfx, be -> { if (!be.craftEnabled()) be.toggleCraft(); });
            fabricatorStep(mc, at, "F2_attr_empty" + sfx, be -> {
                be.setLastTab(com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu.TAB_ATTR);
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_ARMOR,
                        net.minecraft.world.item.ItemStack.EMPTY);
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_BLOOD,
                        net.minecraft.world.item.ItemStack.EMPTY);
            });
            fabricatorStep(mc, at, "F3_attr_base" + sfx, be -> {
                var piece = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
                piece.set(com.hmc.zenkai.registry.ModDataComponents.IMBUED.get(), com.hmc.zenkai.feature.armor.ImbuedAttributes.NONE
                        .plus(com.hmc.zenkai.feature.ZenkaiAttributes.STRENGTH, 5)
                        .plus(com.hmc.zenkai.feature.ZenkaiAttributes.CONSTITUTION, 3)
                        .plus(com.hmc.zenkai.feature.ZenkaiAttributes.DEXTERITY, 12)
                        .plus(com.hmc.zenkai.feature.ZenkaiAttributes.WILLPOWER, 1)
                        .plus(com.hmc.zenkai.feature.ZenkaiAttributes.SPIRIT, 40));
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_ARMOR, piece);
                be.items().setStackInSlot(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_BLOOD,
                        com.hmc.zenkai.content.item.BloodItem.of(com.hmc.zenkai.registry.ModItems.STRENGTH_BLOOD.get(), 5, 64));
            });
            // Carga de imbuir a medias: se arranca con la pantalla ya abierta y se captura a los ~12 ticks.
            PLAN.add(() -> {
                onServer(mc, sp -> {
                    if (sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity be)
                        be.startImbue();
                });
                wait = 12;
            });
            PLAN.add(() -> { capture(mc, "F3b_attr_charging" + sfx); wait = 30; });
            fabricatorStep(mc, at, "F4_attr_super_missing" + sfx, be -> be.items().setStackInSlot(
                    com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity.SLOT_ARMOR,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)));
            fabricatorStep(mc, at, "F5_attr_divine_half" + sfx, be -> be.tank().setFluid(new net.neoforged.neoforge.fluids.FluidStack(
                    com.hmc.zenkai.feature.kichamber.KiEnergyType.DIVINE.fluid().source().get(), 4000)));
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                for (int c = 0; c < 3; c++) {
                    final int cat = c;
                    PLAN.add(() -> { mc.setScreen(null); com.hmc.zenkai.compat.jei.JeiDev.show(cat); wait = 30; });
                    PLAN.add(() -> { capture(mc, "F" + (6 + cat) + "_jei_" + cat + sfx); mc.setScreen(null); wait = 5; });
                }
            }
        }
        // Consola de gravedad abierta por el camino REAL (clic en el bloque) con la vista Drones guardada: debe reabrir en Drones.
        PLAN.add(() -> {
            mc.setScreen(null);
            onServer(mc, sp -> {
                var level = sp.serverLevel();
                BlockPos pos = at[0].offset(0, 0, 2);
                level.setBlock(pos, com.hmc.zenkai.registry.ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState(), 3);
                if (level.getBlockEntity(pos) instanceof com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity be) be.setDroneView(true);
                level.getBlockState(pos).useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(
                        pos.getCenter(), net.minecraft.core.Direction.UP, pos, false));
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, "F9_gravity_remembers_drones"); mc.setScreen(null); wait = 3; });
        PLAN.add(() -> {
            mc.options.languageCode = originalLang;
            mc.getLanguageManager().setSelected(originalLang);
            mc.options.save();
            wait = 5;
        });
    }

    /** Cambia la máquina en el servidor, la (re)abre con su pestaña guardada y captura. */
    private static void fabricatorStep(Minecraft mc, BlockPos[] at, String name,
                                       java.util.function.Consumer<com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity> change) {
        PLAN.add(() -> {
            mc.setScreen(null);
            onServer(mc, sp -> {
                if (!(sp.serverLevel().getBlockEntity(at[0]) instanceof com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity be)) return;
                change.accept(be);
                sp.openMenu(be, buf -> { buf.writeBlockPos(at[0]); buf.writeVarInt(be.lastTab()); });
            });
            wait = 30;
        });
        PLAN.add(() -> { capture(mc, name); wait = 3; });
    }

    private static void buildPlan(Minecraft mc) {
        if (world.startsWith("new:")) {
            if ("chamber".equals(mode)) chamberPlan(mc);
            else if ("tanks".equals(mode)) tanksPlan(mc);
            else if ("motor".equals(mode)) motorPlan(mc);
            else if ("gravity".equals(mode)) gravityPlan(mc);
            else if ("drones".equals(mode)) dronesPlan(mc);
            else if ("tech".equals(mode)) techPlan(mc);
            else if ("wrench".equals(mode)) wrenchPlan(mc);
            else if ("ponder".equals(mode)) ponderPlan(mc);
            else if ("fabricator".equals(mode)) fabricatorPlan(mc);
            else containersPlan(mc);
            return;
        }
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        if ("wishes".equals(mode)) { wishesPlan(mc); return; }
        if ("training".equals(mode)) { trainingPlan(mc); return; }
        // HUD en juego, sin pantalla.
        PLAN.add(() -> { mc.setScreen(null); wait = 20; });
        PLAN.add(() -> { capture(mc, "01_hud_ingame"); });
        shot(mc, "02_pause", () -> new PauseScreen(true));
        // Pestañas del menú Zenkai.
        shot(mc, "10_tab_stats", StatsScreen::new);
        shot(mc, "11_tab_techniques", TechniquesScreen::new);
        shot(mc, "12_tab_training", TrainingHubScreen::new);
        shot(mc, "13_tab_party", PartyScreen::new);
        shot(mc, "14_tab_config", ClientConfigScreen::new);
        shot(mc, "15_credits", CreditsScreen::new);
        shot(mc, "16_tab_story_comingsoon", () -> new ComingSoonScreen(ZenkaiTab.STORY));
        // Skill trees (solo captura, no se tocan).
        shot(mc, "20_skilltree_masters", SkillsTreeScreen::new, 120);
        shot(mc, "21_skilltree_transformations", SkillTreeScreen::new, 120);
        // Training.
        shot(mc, "30_training_shadow", ShadowTrainingScreen::new);
        shot(mc, "31_training_meditation", MeditationScreen::new);
        shot(mc, "32_training_target", TargetPracticeScreen::new);
        shot(mc, "33_training_shadow_result", () -> new ShadowResultScreen(120, 340));
        // Creación de personaje.
        shot(mc, "40_race_selection", RaceSelectionScreen::new);
        shot(mc, "41_appearance", () -> new AppearanceScreen(null, null, null));
        shot(mc, "42_head_appearance", () -> new HeadAppearanceScreen(null, null, null, false));
        shot(mc, "43_body_colors", () -> new BodyColorsScreen(new AppearanceScreen(null, null, null), null, null));
        shot(mc, "44_style_selection", () -> new StyleSelectionScreen(null, null, null));
        // Técnicas, HUD, ruedas.
        shot(mc, "50_technique_edit", () -> new TechniqueEditScreen(0));
        shot(mc, "51_hud_placement", () -> new HudPlacementScreen(null));
        shot(mc, "52_wheel", () -> new WheelScreen(WheelMenu.build(mc.player)));
        shot(mc, "53_instant_transmission", InstantTransmissionMenuScreen::new);
        shot(mc, "54_galactic_menu", GalacticMenuScreen::new);
        // Maestros y NPC.
        for (String m : new String[]{"kami", "korin", "kaio", "toribot"}) {
            shot(mc, "60_master_" + m, () -> new MasterScreen(m, -1, List.of(
                    new OpenMasterPayload.ServiceEntry("train", "Entrenar", true, "Tooltip de ejemplo"),
                    new OpenMasterPayload.ServiceEntry("other", "Bloqueado", false, "No disponible"))));
        }
        shot(mc, "61_npc_marker", () -> new NpcMarkerScreen(BlockPos.ZERO, "saibaman", 0f, 0, 0, 0, 60));
        shot(mc, "62_piece_connector", () -> new PieceConnectorScreen(BlockPos.ZERO, "default", "north"));
        shot(mc, "63_weight", () -> {
            WeightScreen.open(InteractionHand.MAIN_HAND, ModItems.WEIGHTED_STRAPS.get(), 0.5);
            Screen s = mc.screen;
            mc.screen = null;
            return s;
        });
        // Deseos de Shenlong.
        shot(mc, "70_wish_main", ShenlongWishScreen::new);
        shot(mc, "71_wish_immortal", () -> new ImmortalWishScreen(null));
        shot(mc, "72_wish_legendary", () -> new LegendaryWishScreen(null));
        shot(mc, "73_wish_revive_player", () -> new RevivePlayerWishScreen(null));
        shot(mc, "74_wish_revive_pet", () -> new RevivePetWishScreen(null));
        shot(mc, "75_wish_enchant_villager", () -> new EnchantVillagerWishScreen(null));
        shot(mc, "76_wish_training_points", () -> new TrainingPointsWishScreen(null));
        shot(mc, "77_wish_potential", () -> new PotentialWishScreen(null));
    }
}
