package com.hmc.db_renewed.core.network.feature.ki;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.input.KeyBindings;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID, value = Dist.CLIENT)
public final class MouseHooks {

    private MouseHooks() {}

    /** Flag de cliente: estamos manteniendo RMB para cargar un ataque de ki. */
    public static boolean wasChargingKiAttack = false;

    /** Tick de inicio de carga, para mostrar el % en el HUD. */
    public static long clientChargeStartTick = 0L;

    /**
     * Maneja PRESIONAR y SOLTAR botón derecho en un solo handler.
     * - PRESIONAR + ALT + mano vacía → empezar a cargar
     * - SOLTAR (si estábamos cargando) → soltar / disparar
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre e) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        if (!att.isRaceChosen()) return;

        int button = e.getButton();
        int action = e.getAction();

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        // ==========================
        //   PRESIONAR (START)
        // ==========================
        if (action == GLFW.GLFW_PRESS) {
            boolean altDown       = KeyBindings.TRANSFORM_MOD != null && KeyBindings.TRANSFORM_MOD.isDown();
            boolean emptyMainHand = mc.player.getMainHandItem().isEmpty();

            if (altDown && emptyMainHand) {
                if (!wasChargingKiAttack) {
                    wasChargingKiAttack = true;

                    if (mc.level != null) {
                        clientChargeStartTick = mc.level.getGameTime();
                    } else {
                        clientChargeStartTick = 0L;
                    }

                    PacketDistributor.sendToServer(new ChargeKiAttackPacket(true));
                }
                e.setCanceled(true);
            }
            return;
        }

        // ==========================
        //   SOLTAR (RELEASE)
        // ==========================
        if (action == GLFW.GLFW_RELEASE) {
            if (wasChargingKiAttack) {
                wasChargingKiAttack = false;

                PacketDistributor.sendToServer(new ChargeKiAttackPacket(false));
                e.setCanceled(true);
            }
        }
    }
}