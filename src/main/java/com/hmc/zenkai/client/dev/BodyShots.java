package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.death.PlayerBodyCarry;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * HERRAMIENTA DE DESARROLLO (2026-11-02): capturas del CUERPO de un jugador (tamaño junto al jugador, cuerpo en brazos en 3ª y
 * 1ª persona), mismo patrón que HandShots. Solo se activa si existe {@code <run>/zenkai_bodyshots.txt} (primera línea = carpeta
 * de una COPIA del mundo). Salida: {@code zenkai_bodyshots/}. BORRAR el .txt al terminar.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class BodyShots {
    private BodyShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 40;
    private static Boolean enabled;
    private static String world;
    private static boolean opened, earOnly, visitOnly;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_bodyshots.txt");
            if (trigger.isFile()) {
                try {
                    var lines = Files.readAllLines(trigger.toPath());
                    world = lines.get(0).trim();
                    earOnly = lines.size() > 1 && "ear".equals(lines.get(1).trim());
                    visitOnly = lines.size() > 1 && "visit".equals(lines.get(1).trim());
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
        if (PLAN.isEmpty() && step == 0) { if (earOnly) buildEarPlan(mc); else if (visitOnly) buildVisitPlan(mc); else buildPlan(mc); }
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Exception ex) { Zenkai.LOGGER.warn("BodyShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_bodyshots");
        d.mkdirs();
        return d;
    }

    /** Prueba corta de la oreja del Namekiano (segunda línea del .txt = "ear"). Detectó el solape de setCustomAnimations (pendiente #14). */
    private static void buildEarPlan(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "zenkai race set namekian");
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(0F); mc.player.xRotO = 0F;
            wait = 40;
        });
        PLAN.add(() -> { Zenkai.LOGGER.info("[EAR] sin scouter"); wait = 5; });
        PLAN.add(() -> capture(mc, "ear_none"));
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> server.getPlayerList().getPlayers().get(0).setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get())));
            wait = 30;
        });
        PLAN.add(() -> { Zenkai.LOGGER.info("[EAR] scouter en casco"); wait = 5; });
        PLAN.add(() -> capture(mc, "ear_head"));
        PLAN.add(() -> { mc.player.setYRot(mc.player.getYRot() + 90F); mc.player.yRotO = mc.player.getYRot(); wait = 15; });
        PLAN.add(() -> capture(mc, "ear_head_side"));
        PLAN.add(() -> {   // scouter en CURIOS (ranura de casco vacía), de frente
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp).flatMap(i -> i.getStacksHandler("scouter")).ifPresent(h ->
                        h.getStacks().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get())));
            });
            mc.player.setYRot(mc.player.getYRot() - 90F); mc.player.yRotO = mc.player.getYRot();
            wait = 30;
        });
        PLAN.add(() -> capture(mc, "ear_curios"));
        PLAN.add(() -> {   // casco REAL (hierro): la oreja también debe ocultarse (hideOnHelmet)
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp).flatMap(i -> i.getStacksHandler("scouter"))
                        .ifPresent(h -> h.getStacks().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            });
            wait = 30;
        });
        PLAN.add(() -> capture(mc, "ear_helmet"));
    }

    private static void buildPlan(Minecraft mc) {
        // 1) Regla: dos filas de 2 bloques de oro (2 m) a ras de suelo y dos cuerpos (yaw 0 y 90) a su lado; vista cenital.
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var lv = sp.serverLevel();
                var gold = net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState();
                net.minecraft.core.BlockPos base = sp.blockPosition().below();
                for (int i = 0; i < 2; i++) {
                    lv.setBlockAndUpdate(base.offset(-1 + i, 0, 3), gold);   // fila a lo largo de X (2 m)
                    lv.setBlockAndUpdate(base.offset(3, 0, i), gold);   // fila a lo largo de Z (2 m)
                }
                // Equipo de prueba: armadura de diamante puesta, espada en la mano (casilla 0) y un casco IGUAL de repuesto en la mochila
                // (el caso que más fácil mezcla ranuras al emparejar).
                var dyedScouter = new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get());
                dyedScouter.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0xFF2020, false));   // tinte rojo: debe teñir solo el cristal
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, dyedScouter);
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.WEIGHTED_STRAPS.get()));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
                sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                sp.getInventory().setItem(5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
                sp.getInventory().selected = 0;
                List<net.minecraft.world.item.ItemStack> held = new java.util.ArrayList<>();
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++)
                    if (!sp.getInventory().getItem(i).isEmpty()) held.add(sp.getInventory().getItem(i).copy());
                // Raza por el comando oficial (tocar los datos a mano no cambiaba el modelo del jugador).
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "zenkai race set namekian");
                Zenkai.LOGGER.info("[BODYSHOTS] raza ahora = {}", com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(sp).getRace());
                var snap = PlayerBodyManager.Snapshot.capture(sp);
                PlayerBodyEntity a = PlayerBodyManager.spawnFor(sp, lv, held, snap);
                logSlots("tras crear", a);
                auditCurios(sp, a);
                if (a != null) a.moveTo(sp.getX() + 1.6, sp.getY(), sp.getZ() + 1.4, 0f, 0f);
                Zenkai.LOGGER.info("[BODYSHOTS] player scale={} bbH={} | body scale={} bb={}x{}", sp.getScale(),
                        sp.getBbHeight(), a == null ? -1 : a.scale(), a == null ? -1 : a.getBoundingBox().getXsize(),
                        a == null ? -1 : a.getBoundingBox().getYsize());
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(90F); mc.player.xRotO = 90F;
            wait = WAIT;
        });
        PLAN.add(() -> capture(mc, "top_down"));
        // 2) Cargarlo: 3ª persona (de frente) y 1ª persona.
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                for (var b : sp.serverLevel().getEntitiesOfClass(PlayerBodyEntity.class,
                        sp.getBoundingBox().inflate(6))) {
                    PlayerBodyCarry.pickUp(sp, b);
                    break;
                }
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10F); mc.player.xRotO = 10F;
            wait = WAIT;
        });
        PLAN.add(() -> capture(mc, "carry_front"));
        PLAN.add(() -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); wait = 20; });
        PLAN.add(() -> capture(mc, "carry_back"));
        PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(0F); mc.player.xRotO = 0F; wait = 20; });
        PLAN.add(() -> capture(mc, "fp_pitch0"));
        PLAN.add(() -> { mc.player.setXRot(45F); mc.player.xRotO = 45F; wait = 15; });
        PLAN.add(() -> capture(mc, "fp_pitch45"));
        PLAN.add(() -> { mc.player.setXRot(-30F); mc.player.xRotO = -30F; mc.player.setYRot(90F); mc.player.yRotO = 90F; wait = 15; });
        PLAN.add(() -> capture(mc, "fp_pitch_up30_yaw90"));
        PLAN.add(() -> {   // soltar el cuerpo cargado y comprobar que las ranuras de armadura/mano se conservan
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerBodyCarry.place(sp, sp.getX() + 1.6, sp.getY(), sp.getZ() + 1.4, true);
                for (var b : sp.serverLevel().getEntitiesOfClass(PlayerBodyEntity.class, sp.getBoundingBox().inflate(6)))
                    logSlots("tras soltarlo", b);
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(35F); mc.player.xRotO = 35F; mc.player.setYRot(0F); mc.player.yRotO = 0F;
            wait = WAIT;
        });
        PLAN.add(() -> capture(mc, "placed_again"));
        PLAN.add(() -> {   // «Llevarse todo» con armadura puesta: scouter (protegido), peto peor, ranura vacía y botas mejores
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get()));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));
                for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                for (var b : sp.serverLevel().getEntitiesOfClass(PlayerBodyEntity.class, sp.getBoundingBox().inflate(8))) {
                    com.hmc.zenkai.feature.player.death.PlayerBodyLoot.takeAll(sp, b);
                    StringBuilder sb = new StringBuilder("[BODYSHOTS][SWAP] llevas: ");
                    for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
                            net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                            net.minecraft.world.entity.EquipmentSlot.FEET})
                        sb.append(slot.getName()).append('=').append(sp.getItemBySlot(slot).getItem()).append(' ');
                    sb.append("| inventario: ");
                    for (int i = 0; i < 36; i++) if (!sp.getInventory().getItem(i).isEmpty()) sb.append(sp.getInventory().getItem(i).getItem()).append(' ');
                    sb.append("| cuerpo aún tiene: ");
                    for (int i = 0; i < PlayerBodyEntity.SLOTS; i++) if (!b.inventory().getItem(i).isEmpty()) sb.append(i).append('=').append(b.inventory().getItem(i).getItem()).append(' ');
                    Zenkai.LOGGER.info(sb.toString());
                    break;
                }
            });
            wait = 20;
        });
        PLAN.add(() -> {   // sin scouter: la oreja del Namekiano debe volver
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> server.getPlayerList().getPlayers().get(0)
                    .setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY));
            wait = 25;
        });
        PLAN.add(() -> capture(mc, "namek_no_scouter"));
        // Como la captura del usuario: de frente, primero con el scouter en CURIOS y luego en la ranura de casco.
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
                var inv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp);
                inv.flatMap(i -> i.getStacksHandler("scouter")).ifPresent(h ->
                        h.getStacks().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get())));
                Zenkai.LOGGER.info("[BODYSHOTS][EAR] scouter en Curios = {}", com.hmc.zenkai.compat.CuriosCompat.findEquipped(sp, "scouter").getItem());
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(0F); mc.player.xRotO = 0F;
            wait = 30;
        });
        PLAN.add(() -> capture(mc, "front_curios"));
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp).flatMap(i -> i.getStacksHandler("scouter"))
                        .ifPresent(h -> h.getStacks().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.hmc.zenkai.registry.ModItems.SCOUTER.get()));
            });
            wait = 30;
        });
        PLAN.add(() -> capture(mc, "front_head"));
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> server.getPlayerList().getPlayers().get(0)
                    .setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY));
            wait = 30;
        });
        PLAN.add(() -> capture(mc, "front_none"));
    }

    /** AUDITORÍA de Curios: tipos que el jugador puede tener frente a ranuras en la foto y reservadas en el cuerpo. */
    private static final String NL = System.lineSeparator();

    /** Qué hay en cada ranura de armadura/mano/mochila del cuerpo (para comprobar que las posiciones se conservan). */
    private static void logSlots(String when, PlayerBodyEntity b) {
        if (b == null) return;
        StringBuilder sb = new StringBuilder("[BODYSHOTS][SLOTS] ").append(when).append(" | mano=").append(b.handSlot()).append(' ');
        for (int i : new int[]{0, 5, 36, 37, 38, 39, 40})
            sb.append(i).append('=').append(b.inventory().getItem(i).getItem()).append(' ');
        Zenkai.LOGGER.info(sb.toString());
    }

    private static void auditCurios(net.minecraft.server.level.ServerPlayer sp, PlayerBodyEntity body) {
        var types = top.theillusivec4.curios.api.CuriosApi.getPlayerSlots(sp);
        StringBuilder sb = new StringBuilder("[BODYSHOTS][CURIOS] tipos de jugador=").append(types.size()).append(' ').append(types.keySet());
        var inv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp);
        int total = 0;
        if (inv.isPresent()) {
            for (var en : inv.get().getCurios().entrySet()) {
                int n = en.getValue().getSlots();
                total += n;
                sb.append(NL).append("   inv ").append(en.getKey()).append(" slots=").append(n).append(" visible=").append(en.getValue().isVisible());
            }
        }
        var all = com.hmc.zenkai.compat.CuriosCompat.allSlots(sp);
        sb.append(NL).append("   ranuras totales del jugador=").append(total).append(" | en la foto=").append(all.size())
                .append(" | reservadas en el cuerpo=").append(body.curioCount());
        for (var t : types.keySet()) {
            boolean has = inv.isPresent() && inv.get().getStacksHandler(t).isPresent();
            if (!has) sb.append(NL).append("   TIPO SIN RANURAS EN EL JUGADOR (no aparece): ").append(t);
        }
        for (int i = 0; i < body.curioCount(); i++) sb.append(NL).append("   cuerpo[").append(i).append("]=").append(body.curioId(i));
        Zenkai.LOGGER.info(sb.toString());
    }

    /**
     * Insignia de la visita a la Tierra en el HUD real (segunda línea del .txt = "visit"): con varios días, con horas y en el
     * tramo final, donde la cifra parpadea (dos fotogramas a medio periodo de distancia para verlos los dos colores).
     */
    private static void buildVisitPlan(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "zenkai race set human");
                com.hmc.zenkai.feature.player.EarthVisit.forceStart(sp, 7L * 24000L);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            wait = 60;
        });
        PLAN.add(() -> capture(mc, "visit_days"));
        PLAN.add(() -> { visitTicks(mc, 12000L); wait = 40; });
        PLAN.add(() -> capture(mc, "visit_hours"));
        PLAN.add(() -> { visitTicks(mc, 1500L); wait = 40; });
        PLAN.add(() -> capture(mc, "visit_warn_a"));
        PLAN.add(() -> { wait = 9; });
        PLAN.add(() -> capture(mc, "visit_warn_b"));
    }

    private static void visitTicks(Minecraft mc, long ticks) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(sp).flags().setEarthVisitTicksLeft(ticks);
            com.hmc.zenkai.feature.player.PlayerLifeCycle.sync(sp);
        });
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("BodyShots: no se pudo capturar {}", name, ex);
        }
    }
}
