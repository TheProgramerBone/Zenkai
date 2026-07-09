package com.hmc.zenkai.client;

import com.hmc.zenkai.core.combat.SenseKiMode;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.core.network.feature.sense.SenseKiScanPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado CLIENTE del sentir el ki:
 *  - Modo actual (ciclo F4; ver SenseKiMode). Cada cambio se anuncia en la actionbar.
 *  - Caché de entidades sentidas (respuestas del servidor), consumida por SenseKiOverlayRenderer.
 *  - Tick: cada SCAN_INTERVAL ticks con modo != OFF manda un SenseKiScanPacket.
 *
 * Gate del F4: si el jugador lleva SCOUTER en la cabeza, F4 es del scouter (fase futura);
 * si no, F4 cicla el sentir el ki. isScouterEquipped() es el gancho (hoy: siempre false).
 *
 * NIVELES (futuro sistema de habilidades/MIND): nivel 1 = solo barras. senseKiLevel() es el
 * gancho (hoy: 1). Niveles superiores: +rango, vida numérica, alineamiento.
 */
public final class SenseKiClientState {
    private SenseKiClientState() {}

    private static final int SCAN_INTERVAL = 10; // ticks (0.5 s)

    private static SenseKiMode mode = SenseKiMode.OFF;
    private static int tickCounter = 0;

    /** entityId -> datos sentidos (última respuesta del servidor). */
    private static final Map<Integer, SenseKiDataPacket.Entry> SENSED = new ConcurrentHashMap<>();

    public static SenseKiMode mode() { return mode; }

    public static Map<Integer, SenseKiDataPacket.Entry> sensed() { return SENSED; }

    /** Gancho del scouter (fase futura): ¿casco = scouter? Hoy no existe el item. */
    public static boolean isScouterEquipped(Minecraft mc) {
        return false; // fase scouter: chequear helmet instanceof ScouterItem
    }

    /** Gancho del nivel de la habilidad (futuro sistema de habilidades/MIND). */
    public static int senseKiLevel() {
        return 1; // nivel 1: solo barras de vida
    }

    /** Pulsación de F4 (desde KeyBindings). */
    public static void onKeyPress(Minecraft mc) {
        if (mc.player == null) return;

        if (isScouterEquipped(mc)) {
            // Fase futura: toggle del overlay del scouter (HUD de PL en pantalla).
            return;
        }

        mode = mode.next();
        if (mode == SenseKiMode.OFF) SENSED.clear();

        mc.player.displayClientMessage(
                Component.translatable(mode.translationKey()).withStyle(ChatFormatting.AQUA),
                true); // actionbar
    }

    /** Llamar 1 vez por tick de cliente (desde KeyBindings.handleClientTick). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            SENSED.clear();
            mode = SenseKiMode.OFF;
            return;
        }
        if (mode == SenseKiMode.OFF) return;

        if (++tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            PacketDistributor.sendToServer(new SenseKiScanPacket());
        }
    }

    /** Respuesta del servidor: reemplaza la caché entera (lo que ya no viene, dejó el rango). */
    public static void onData(List<SenseKiDataPacket.Entry> entries) {
        // DEBUG temporal — bórrame cuando confirmes que las barras se ven.
        // Si este log aparece con size>0 pero no ves barras => problema de render.
        // Si nunca aparece => problema de red/registro de paquetes.
        if (entries.size() != SENSED.size()) {
            org.slf4j.LoggerFactory.getLogger("Zenkai-SenseKi")
                    .info("[Zenkai] SenseKi: {} entidades sentidas", entries.size());
        }
        SENSED.clear();
        for (SenseKiDataPacket.Entry e : entries) SENSED.put(e.entityId(), e);
    }

    /** Filtro por modo (cliente; conoce su propio PL). */
    public static boolean passesFilter(SenseKiDataPacket.Entry e, Minecraft mc) {
        if (mc.player == null) return false;
        return switch (mode) {
            case OFF            -> false;
            case ALL            -> true;
            case PLAYERS        -> e.isPlayer();
            case MOBS           -> !e.isPlayer();
            case PLAYERS_STRONG -> e.isPlayer()  && isStrong(e, mc);
            case MOBS_STRONG    -> !e.isPlayer() && isStrong(e, mc);
        };
    }

    private static boolean isStrong(SenseKiDataPacket.Entry e, Minecraft mc) {
        assert mc.player != null;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        long myPl = att.isRaceChosen()
                ? att.getPowerLevel()
                : Math.round(mc.player.getMaxHealth());
        double threshold = com.hmc.zenkai.core.config.StatsConfig.senseKiSimilarThreshold();
        return e.powerLevel() >= Math.round(myPl * threshold);
    }
}