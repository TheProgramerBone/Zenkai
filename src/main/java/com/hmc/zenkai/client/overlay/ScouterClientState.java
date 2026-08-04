package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.item.ScouterItem;
import com.hmc.zenkai.feature.sense.ScouterAreaDataPacket;
import com.hmc.zenkai.feature.sense.ScouterAreaScanPacket;
import com.hmc.zenkai.feature.sense.ScouterScanPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado CLIENTE del scouter.
 *
 *  - F4 con scouter puesto CICLA el modo: OFF -> PODER -> STATS -> MÁS FUERTE -> RADAR -> OFF.
 *    Silencioso: el feedback es el propio panel (muestra el título del modo).
 *    Al salir de OFF apaga el sentir el ki (mutuamente excluyentes).
 *  - PODER y STATS: mandan ScouterScanPacket (raycast de la mira) cada SCAN_INTERVAL ticks.
 *    Comparten scan porque comparten objetivo: STATS solo desglosa el PL que PODER resume.
 *  - MÁS FUERTE / RADAR: mandan ScouterAreaScanPacket cada AREA_INTERVAL ticks. El cliente
 *    cachea la POSICIÓN objetivo y ScouterOverlay recalcula la flecha cada frame.
 *  - Si te quitas el scouter, vuelve a OFF solo.
 */
public final class ScouterClientState {
    private ScouterClientState() {}

    private static final int SCAN_INTERVAL = 5;  // ticks (la mira cambia rápido)
    private static final int AREA_INTERVAL = 20; // ticks (posiciones cambian lento; flecha es per-frame)
    private static int targetEntityId = -1;

    private static ScouterMode mode = ScouterMode.OFF;
    private static int tickCounter = 0;

    // --- Caché modo PODER / STATS (raycast de la mira) ---
    private static boolean targetFound = false;
    private static long targetPl = 0L;
    private static long targetMelee = 0L;
    private static long targetDefense = 0L;
    private static long targetKiPower = 0L;
    private static long targetBody = 0L;
    private static long targetBodyMax = 0L;

    // --- Caché modos de ÁREA (más fuerte / radar) ---
    private static byte areaStatus = ScouterAreaDataPacket.STATUS_NONE;
    private static double areaX, areaY, areaZ;
    private static long areaPl = 0L;

    public static ScouterMode mode()      { return mode; }
    public static boolean isOverlayOn()   { return mode != ScouterMode.OFF; }
    public static boolean hasTarget()     { return targetFound; }
    public static long targetPowerLevel() { return targetPl; }

    public static long targetMelee()   { return targetMelee; }
    public static long targetDefense() { return targetDefense; }
    public static long targetKiPower() { return targetKiPower; }
    public static long targetBody()    { return targetBody; }
    public static long targetBodyMax() { return targetBodyMax; }

    /** ¿El objetivo tiene stats del mod? Un mob vanilla sin JSON solo da PL de display. */
    public static boolean hasBreakdown() {
        return targetFound && (targetMelee > 0 || targetDefense > 0 || targetKiPower > 0);
    }

    public static byte areaStatus() { return areaStatus; }
    public static double areaX()    { return areaX; }
    public static double areaY()    { return areaY; }
    public static double areaZ()    { return areaZ; }
    public static long areaPl()     { return areaPl; }

    public static boolean isScouterEquipped(Minecraft mc) {
        return mc.player != null && scouterStack(mc).getItem() instanceof ScouterItem;
    }

    /** El scouter equipado: slot de Curios si el mod está, casco vanilla como respaldo. */
    private static ItemStack scouterStack(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack curio = CuriosCompat.findEquipped(mc.player, "scouter");
        if (!curio.isEmpty()) return curio;
        return mc.player.getItemBySlot(EquipmentSlot.HEAD);
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

    /** F4 avanza, Shift+F4 retrocede. Silencioso: el feedback es el propio panel. */
    public static void cycle(Minecraft mc, boolean backwards) {
        if (mc.player == null) return;
        mode = backwards ? mode.prev() : mode.next();
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

        boolean aimed = (mode == ScouterMode.POWER || mode == ScouterMode.ATTRIBUTES);
        int interval = aimed ? SCAN_INTERVAL : AREA_INTERVAL;
        if (++tickCounter >= interval) {
            tickCounter = 0;
            switch (mode) {
                case POWER, ATTRIBUTES -> PacketDistributor.sendToServer(new ScouterScanPacket());
                case STRONGEST -> PacketDistributor.sendToServer(
                        new ScouterAreaScanPacket(ScouterAreaScanPacket.MODE_STRONGEST));
                case RADAR -> PacketDistributor.sendToServer(
                        new ScouterAreaScanPacket(ScouterAreaScanPacket.MODE_RADAR));
                default -> { }
            }
        }
    }

    /** Respuesta del servidor: raycast de la mira (modos PODER y STATS). */
    public static void onData(boolean found, int entityId, long pl,
                              long melee, long defense, long kiPower,
                              long body, long bodyMax) {
        targetFound = found;
        targetEntityId = entityId;
        targetPl = pl;
        targetMelee = melee;
        targetDefense = defense;
        targetKiPower = kiPower;
        targetBody = body;
        targetBodyMax = bodyMax;
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
        targetMelee = 0L;
        targetDefense = 0L;
        targetKiPower = 0L;
        targetBody = 0L;
        targetBodyMax = 0L;
        targetEntityId = -1;
        areaStatus = ScouterAreaDataPacket.STATUS_NONE;
        areaPl = 0L;
    }

    /**
     * Distancia al objetivo apuntado, en bloques. −1 si no hay o el cliente no lo tiene.
     * Se calcula CADA FRAME desde la entidad real y no viaja en el paquete: mandada por el
     * server se quedaría congelada los 5 ticks del scan y daría saltos al caminar.
     */
    public static long targetDistance(Minecraft mc) {
        if (!targetFound || targetEntityId < 0 || mc.player == null || mc.level == null) return -1L;
        Entity ent = mc.level.getEntity(targetEntityId);
        if (ent == null) return -1L;
        Vec3 mid = ent.position().add(0.0, ent.getBbHeight() / 2.0, 0.0);
        return Math.round(mc.player.getEyePosition().distanceTo(mid));
    }
}