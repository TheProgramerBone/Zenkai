package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.feature.sense.SenseKiScanPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 * Gate del F4: si el jugador lleva un scouter FUNCIONAL, F4 es del scouter; si no lleva, o el
 * que lleva está reventado, F4 cicla el sentir el ki.
 * NIVELES: SkillEffects.senseLevel(player) manda. 0 = sin la habilidad, no se siente nada.
 */
public final class SenseKiClientState {
    private SenseKiClientState() {}

    private static final int SCAN_INTERVAL = 10; //Ticks

    private static SenseKiMode mode = SenseKiMode.OFF;
    private static int tickCounter = 0;

    /** entityId -> datos sentidos (última respuesta del servidor). */
    private static final Map<Integer, SenseKiDataPacket.Entry> SENSED = new ConcurrentHashMap<>();

    public static SenseKiMode mode() { return mode; }

    public static Map<Integer, SenseKiDataPacket.Entry> sensed() { return SENSED; }

    /** ¿Lleva un scouter en la cabeza? (delegado al estado del scouter). */
    public static boolean isScouterEquipped(Minecraft mc) {
        return ScouterClientState.isScouterEquipped(mc);
    }

    /** Apaga el sentir el ki desde fuera (el scouter lo fuerza al encenderse: excluyentes). */
    public static void forceOff() {
        mode = SenseKiMode.OFF;
        SENSED.clear();
    }

    /** Nivel de la habilidad Ki Sense (0 = sin habilidad: solo barras y PL). */
    public static int senseKiLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : SkillEffects.senseLevel(mc.player);
    }

    /** Pulsación de F4 (desde KeyBindings). Shift invierte el sentido del ciclo. */
    public static void onKeyPress(Minecraft mc) {
        if (mc.player == null) return;

        // Shift se lee del estado de la ventana, no de un KeyMapping aparte: una sola tecla
        // en el menú de controles y el modificador funciona con la que el jugador rebindee.
        boolean backwards = Screen.hasShiftDown();

        if (isScouterUsable(mc)) {
            ScouterClientState.cycle(mc, backwards);
            return;
        }
        // Sin la habilidad no se siente nada. El scouter es la vía alternativa: por eso
        // el gate va DESPUÉS del desvío al scouter, no antes.
        if (SkillEffects.senseLevel(mc.player) <= 0) {
            forceOff();
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.sense_ki.locked")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        mode = backwards ? mode.prev() : mode.next();
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

        // Perdió la habilidad (respec, /zenkai skill revoke): se apaga solo.
        if (SkillEffects.senseLevel(mc.player) <= 0) {
            forceOff();
            return;
        }

        if (++tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            PacketDistributor.sendToServer(new SenseKiScanPacket());
        }
    }

    /** Respuesta del servidor: reemplaza la caché entera (lo que ya no viene, dejó el rango). */
    public static void onData(List<SenseKiDataPacket.Entry> entries) {
        SENSED.clear();
        for (SenseKiDataPacket.Entry e : entries) SENSED.put(e.entityId(), e);
    }

    /** Filtro por modo (cliente; conoce su propio PL). */
    public static boolean passesFilter(SenseKiDataPacket.Entry e, Minecraft mc) {
        if (mc.player == null) return false;
        return switch (mode) {
            case OFF     -> false;
            case ALL     -> true;
            case PLAYERS -> e.isPlayer();
            case MOBS    -> !e.isPlayer();
            // Sin lock activo, targetId() es -1 y no pasa nadie: la pantalla queda limpia,
            // que es justo la señal de "no estás fijando a nadie".
            case LOCKED  -> e.entityId() == LockOnClientState.targetId();
        };
    }

    private static boolean isStrong(SenseKiDataPacket.Entry e, Minecraft mc) {
        assert mc.player != null;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        long myPl = att.isRaceChosen()
                ? att.getPowerLevel()
                : Math.round(mc.player.getMaxHealth());
        double threshold = CommonConfig.senseKiSimilarThreshold();
        return e.powerLevel() >= Math.round(myPl * threshold);
    }

    /** ¿Lleva un scouter que FUNCIONA? Es lo que decide de quién es la tecla F4.
     *  Uno roto NO cuenta: un aparato muerto no debe bloquearte el sentido. */
    public static boolean isScouterUsable(Minecraft mc) {
        return ScouterClientState.isScouterUsable(mc);
    }
}