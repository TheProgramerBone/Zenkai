package com.hmc.zenkai.client;

import com.hmc.zenkai.content.item.special.ScouterItem;
import com.hmc.zenkai.core.network.feature.sense.ScouterAreaDataPacket;
import com.hmc.zenkai.core.network.feature.sense.ScouterAreaScanPacket;
import com.hmc.zenkai.core.network.feature.sense.ScouterScanPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado CLIENTE del scouter.
 *
 *  - F4 con scouter puesto CICLA el modo: OFF -> PODER -> MÁS FUERTE -> RADAR -> OFF.
 *    Silencioso: el feedback es el propio panel (muestra el título del modo).
 *    Al salir de OFF apaga el sentir el ki (mutuamente excluyentes).
 *  - PODER: manda ScouterScanPacket (raycast de la mira) cada SCAN_INTERVAL ticks.
 *  - MÁS FUERTE / RADAR: manda ScouterAreaScanPacket cada AREA_INTERVAL ticks. El cliente
 *    cachea la POSICIÓN objetivo y ScouterOverlay recalcula la flecha cada frame.
 *  - Si te quitas el scouter, vuelve a OFF solo.
 */
public final class ScouterClientState {
    private ScouterClientState() {}

    private static final int SCAN_INTERVAL = 5;  // ticks (la mira cambia rápido)
    private static final int AREA_INTERVAL = 20; // ticks (posiciones cambian lento; flecha es per-frame)

    private static ScouterMode mode = ScouterMode.OFF;
    private static int tickCounter = 0;

    // --- Caché modo PODER (raycast de la mira) ---
    private static boolean targetFound = false;
    private static long targetPl = 0L;

    // --- Caché modos de ÁREA (más fuerte / radar) ---
    private static byte areaStatus = ScouterAreaDataPacket.STATUS_NONE;
    private static double areaX, areaY, areaZ;
    private static long areaPl = 0L;

    public static ScouterMode mode()      { return mode; }
    public static boolean isOverlayOn()   { return mode != ScouterMode.OFF; }
    public static boolean hasTarget()     { return targetFound; }
    public static long targetPowerLevel() { return targetPl; }

    public static byte areaStatus() { return areaStatus; }
    public static double areaX()    { return areaX; }
    public static double areaY()    { return areaY; }
    public static double areaZ()    { return areaZ; }
    public static long areaPl()     { return areaPl; }

    /** ¿Lleva un scouter (cualquier variante de color) en la cabeza? */
    public static boolean isScouterEquipped(Minecraft mc) {
        return mc.player != null
                && mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ScouterItem;
    }

    /**
     * Color del cristal del scouter puesto (tinte vanilla del stack, o el verde por defecto).
     * La interfaz entera se ve del color del cristal por el que miras. RGB opaco.
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

    /** F4 con scouter puesto: cicla el modo (silencioso; el panel muestra el modo actual). */
    public static void toggle(Minecraft mc) {
        if (mc.player == null) return;
        mode = mode.next();
        clearCaches();
        tickCounter = Integer.MAX_VALUE - 1; // fuerza scan inmediato del nuevo modo
        if (mode != ScouterMode.OFF) {
            SenseKiClientState.forceOff(); // mutuamente excluyentes
        }
    }

    /** Llamar 1 vez por tick de cliente (desde KeyBindings.handleClientTick). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            mode = ScouterMode.OFF;
            clearCaches();
            return;
        }
        if (mode == ScouterMode.OFF) return;

        // Sin scouter puesto no hay overlay (se reactiva con F4 al ponértelo de nuevo).
        if (!isScouterEquipped(mc)) {
            mode = ScouterMode.OFF;
            clearCaches();
            return;
        }

        int interval = (mode == ScouterMode.POWER) ? SCAN_INTERVAL : AREA_INTERVAL;
        if (++tickCounter >= interval) {
            tickCounter = 0;
            switch (mode) {
                case POWER -> PacketDistributor.sendToServer(new ScouterScanPacket());
                case STRONGEST -> PacketDistributor.sendToServer(
                        new ScouterAreaScanPacket(ScouterAreaScanPacket.MODE_STRONGEST));
                case RADAR -> PacketDistributor.sendToServer(
                        new ScouterAreaScanPacket(ScouterAreaScanPacket.MODE_RADAR));
                default -> { }
            }
        }
    }

    /** Respuesta del servidor: raycast de la mira (modo PODER). */
    public static void onData(boolean found, long pl) {
        targetFound = found;
        targetPl = pl;
    }

    /** Respuesta del servidor: escaneo por área. Descarta respuestas de un modo ya abandonado. */
    public static void onAreaData(byte pktMode, byte status, double x, double y, double z, long pl) {
        boolean matches = (mode == ScouterMode.STRONGEST && pktMode == ScouterAreaScanPacket.MODE_STRONGEST)
                || (mode == ScouterMode.RADAR && pktMode == ScouterAreaScanPacket.MODE_RADAR);
        if (!matches) return;
        areaStatus = status;
        areaX = x;
        areaY = y;
        areaZ = z;
        areaPl = pl;
    }

    private static void clearCaches() {
        targetFound = false;
        targetPl = 0L;
        areaStatus = ScouterAreaDataPacket.STATUS_NONE;
        areaPl = 0L;
    }
}