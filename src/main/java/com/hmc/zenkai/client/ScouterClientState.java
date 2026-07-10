package com.hmc.zenkai.client;

import com.hmc.zenkai.content.item.special.ScouterItem;
import com.hmc.zenkai.core.network.feature.sense.ScouterScanPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado CLIENTE del scouter:
 *  - Toggle on/off con F4 (solo si llevas un scouter en la cabeza; enruta SenseKiClientState).
 *    Al encenderse APAGA el sentir el ki (mutuamente excluyentes).
 *  - Con el overlay activo, manda un ScouterScanPacket cada SCAN_INTERVAL ticks.
 *  - Cachea el último resultado (found + PL) para que ScouterOverlay lo pinte.
 *  - Si te quitas el scouter, el overlay se apaga solo.
 */
public final class ScouterClientState {
    private ScouterClientState() {}

    private static final int SCAN_INTERVAL = 5; // ticks (0.25 s: la mira cambia rápido)

    private static boolean overlayOn = false;
    private static int tickCounter = 0;

    private static boolean targetFound = false;
    private static long targetPl = 0L;

    public static boolean isOverlayOn() { return overlayOn; }
    public static boolean hasTarget()   { return targetFound; }
    public static long targetPowerLevel() { return targetPl; }

    /** ¿Lleva un scouter (cualquier variante de color) en la cabeza? */
    public static boolean isScouterEquipped(Minecraft mc) {
        return mc.player != null
                && mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ScouterItem;
    }

    /**
     * Color del cristal del scouter puesto (tinte vanilla del stack, o el verde por defecto).
     * Lo usa el HUD (y lo usará el marco/interfaz estilo DBC): la interfaz se ve del color del
     * cristal por el que miras. RGB opaco.
     */
    public static int scouterTint(Minecraft mc) {
        if (mc.player == null) return ScouterItem.DEFAULT_TINT;
        ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof ScouterItem)) return ScouterItem.DEFAULT_TINT;
        int rgb = helmet.has(DataComponents.DYED_COLOR)
                ? DyedItemColor.getOrDefault(helmet, ScouterItem.DEFAULT_TINT)
                : ScouterItem.DEFAULT_TINT;
        return rgb & 0xFFFFFF;
    }

    /** F4 con scouter puesto: toggle del overlay (y apaga el sentir ki al encender). */
    public static void toggle(Minecraft mc) {
        if (mc.player == null) return;
        overlayOn = !overlayOn;
        if (overlayOn) {
            SenseKiClientState.forceOff(); // mutuamente excluyentes
        } else {
            targetFound = false;
        }
        mc.player.displayClientMessage(
                Component.translatable(overlayOn ? "messages.zenkai.scouter.on"
                                : "messages.zenkai.scouter.off")
                        .withStyle(ChatFormatting.GREEN),
                true); // actionbar
    }

    /** Llamar 1 vez por tick de cliente (desde KeyBindings.handleClientTick, junto al del sense ki). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            overlayOn = false;
            targetFound = false;
            return;
        }
        if (!overlayOn) return;

        // Sin scouter puesto no hay overlay (se re-enciende con F4 al ponértelo de nuevo).
        if (!isScouterEquipped(mc)) {
            overlayOn = false;
            targetFound = false;
            return;
        }

        if (++tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            PacketDistributor.sendToServer(new ScouterScanPacket());
        }
    }

    /** Respuesta del servidor. */
    public static void onData(boolean found, long pl) {
        targetFound = found;
        targetPl = pl;
    }
}