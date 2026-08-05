package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.content.item.ScouterItem;
import com.hmc.zenkai.feature.sense.ScouterAreaDataPacket;
import com.hmc.zenkai.feature.sense.ScouterAreaScanPacket;
import com.hmc.zenkai.feature.sense.ScouterScanPacket;
import com.hmc.zenkai.feature.sense.ScouterStacks;
import com.hmc.zenkai.feature.sense.ScouterUpgrade;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.random.RandomGenerator;

/**
 * Estado CLIENTE del scouter.
 *
 *  - F4 con scouter FUNCIONAL puesto cicla el modo: OFF -> PODER -> STATS -> MÁS FUERTE ->
 *    RADAR -> OFF. Silencioso: el feedback es el propio panel. Al salir de OFF apaga el sentir
 *    el ki (mutuamente excluyentes).
 *  - PODER y STATS: mandan ScouterScanPacket (raycast de la mira) cada SCAN_INTERVAL ticks.
 *    Comparten scan porque comparten objetivo: STATS solo desglosa el PL que PODER resume.
 *  - MÁS FUERTE / RADAR: mandan ScouterAreaScanPacket cada AREA_INTERVAL ticks.
 *  - Un modo SIN su mejora no manda nada: el overlay lo dice y el servidor lo rechazaría igual.
 *  - Si te quitas el scouter, o se rompe, vuelve a OFF solo.
 *
 * SOBRECARGA: el servidor avisa con el flag `overload` del ScouterDataPacket. Mientras dura,
 * la cifra se baraja aquí (nunca en el paquete: mandar 5 números falsos por segundo por la red
 * sería absurdo) y el overlay pinta OVERLOAD parpadeando.
 */
public final class ScouterClientState {
    private ScouterClientState() {}

    private static final int SCAN_INTERVAL = 5;  // ticks (la mira cambia rápido)
    private static final int AREA_INTERVAL = 20; // ticks (posiciones lentas; flecha es per-frame)

    /** Cada cuántos ticks se rebaraja la cifra en sobrecarga. 4 ticks = 5 cambios/s: se ve
     *  que son números y no da tiempo a leer cuáles. Por frame sería un borrón gris. */
    private static final int SCRAMBLE_INTERVAL = 4;

    private static final RandomGenerator RNG = RandomGenerator.getDefault();

    private static ScouterMode mode = ScouterMode.OFF;
    private static int tickCounter = 0;

    // --- Caché modo PODER / STATS (raycast de la mira) ---
    private static boolean targetFound = false;
    private static int targetEntityId = -1;
    private static long targetPl = 0L;
    private static long targetMelee = 0L;
    private static long targetDefense = 0L;
    private static long targetKiPower = 0L;
    private static long targetBody = 0L;
    private static long targetBodyMax = 0L;

    // --- Sobrecarga ---
    private static boolean overload = false;
    private static int scrambleCounter = 0;
    private static String scrambled = "";

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

    public static boolean isOverloading() { return overload; }

    /** Cifra barajada del tick actual. Vacía si no hay sobrecarga. */
    public static String scrambledPl() { return scrambled; }

    /** ¿El objetivo tiene stats del mod? Un mob vanilla sin JSON solo da PL de display. */
    public static boolean hasBreakdown() {
        return targetFound && (targetMelee > 0 || targetDefense > 0 || targetKiPower > 0);
    }

    public static byte areaStatus() { return areaStatus; }
    public static double areaX()    { return areaX; }
    public static double areaY()    { return areaY; }
    public static double areaZ()    { return areaZ; }
    public static long areaPl()     { return areaPl; }

    // ── Stack equipado ────────────────────────────────────────────────────────

    /** El scouter equipado (curio primero, casco de respaldo), o EMPTY. Mismo embudo que
     *  el servidor: ScouterStacks es el único sitio que sabe dónde vive el aparato. */
    public static ItemStack stack(Minecraft mc) {
        return mc.player == null ? ItemStack.EMPTY : ScouterStacks.equipped(mc.player);
    }

    /** ¿Lleva scouter, roto o no? Lo usa la grieta del overlay. */
    public static boolean isScouterEquipped(Minecraft mc) {
        return !stack(mc).isEmpty();
    }

    /** ¿Lleva un scouter que FUNCIONA? Es el gate real de F4 y del overlay: uno roto no
     *  secuestra la tecla, porque un aparato muerto no debe bloquear el sentir el ki. */
    public static boolean isScouterUsable(Minecraft mc) {
        ItemStack s = stack(mc);
        return !s.isEmpty() && !ScouterStacks.isBroken(s);
    }

    /** ¿El scouter puesto tiene la mejora que pide este modo? */
    public static boolean isModeUnlocked(Minecraft mc, ScouterMode m) {
        ScouterUpgrade req = m.required();
        if (req == null) return true;
        return ScouterStacks.has(stack(mc), req);
    }

    /**
     * Color del cristal del scouter puesto (tinte vanilla del stack, o el verde por defecto).
     * La interfaz entera se ve del color del cristal por el que miras. RGB opaco.
     * Lee el MISMO stack que lo demás: mirando solo el casco, un scouter teñido en el
     * slot de Curios pintaba la GUI del verde de fábrica.
     */
    public static int scouterTint(Minecraft mc) {
        ItemStack s = stack(mc);
        if (s.isEmpty()) return ScouterItem.DEFAULT_TINT;
        int rgb = s.has(DataComponents.DYED_COLOR)
                ? DyedItemColor.getOrDefault(s, ScouterItem.DEFAULT_TINT)
                : ScouterItem.DEFAULT_TINT;
        return rgb & 0xFFFFFF;
    }

    // ── Ciclo y tick ──────────────────────────────────────────────────────────

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

        // Sin scouter, o con el scouter reventado, no hay overlay. Se reactiva con F4 al
        // ponerte uno entero o al repararlo.
        if (!isScouterUsable(mc)) {
            mode = ScouterMode.OFF;
            clearCaches();
            return;
        }

        // Modo bloqueado: no se manda nada. El overlay ya está diciendo qué mejora falta.
        if (!isModeUnlocked(mc, mode)) {
            clearCaches();
            return;
        }

        if (overload && ++scrambleCounter >= SCRAMBLE_INTERVAL) {
            scrambleCounter = 0;
            scrambled = scramble(com.hmc.zenkai.util.ZenkaiNumbers.format(targetPl));
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

    /**
     * Baraja los DÍGITOS conservando la forma del número: "4.5M" -> "8.1M", "947" -> "312".
     * El punto y el sufijo de unidad se respetan porque el PL se muestra compacto; barajar la
     * cadena entera daría "9K3M", que parece la fuente rota y no un aparato saturado.
     */
    private static String scramble(String real) {
        char[] out = real.toCharArray();
        for (int i = 0; i < out.length; i++) {
            if (out[i] >= '0' && out[i] <= '9') out[i] = (char) ('0' + RNG.nextInt(10));
        }
        return new String(out);
    }

    /** Respuesta del servidor: raycast de la mira (modos PODER y STATS). */
    public static void onData(boolean found, int entityId, long pl,
                              long melee, long defense, long kiPower,
                              long body, long bodyMax, boolean overloading) {
        targetFound = found;
        targetEntityId = entityId;
        targetPl = pl;
        targetMelee = melee;
        targetDefense = defense;
        targetKiPower = kiPower;
        targetBody = body;
        targetBodyMax = bodyMax;

        // Primer tick de sobrecarga: se baraja YA, sin esperar al contador, o el jugador vería
        // la cifra real un instante antes de que empiece el caos.
        if (overloading && !overload) {
            scrambleCounter = 0;
            scrambled = scramble(com.hmc.zenkai.util.ZenkaiNumbers.format(pl));
        }
        overload = overloading;
        if (!overloading) scrambled = "";
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
        overload = false;
        scrambled = "";
        scrambleCounter = 0;
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