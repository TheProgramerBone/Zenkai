package com.hmc.zenkai.feature.sense;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sobrecarga del scouter: leer un PL por encima del tope del nivel NO revienta al instante.
 * El aparato aguanta 3 segundos enloqueciendo la cifra y solo revienta si sigues mirando.
 *
 * POR QUÉ SE CANCELA AL APARTAR LA MIRADA: un raycast puede engancharse medio tick a algo que
 * ni querías mirar. Sin la ventana, pasar el ratón por delante de un jefe te costaría el
 * scouter, y eso es un castigo por un accidente de puntería, no por atreverte a medir a Goku.
 *
 * El scan llega cada 5 ticks (ScouterClientState.SCAN_INTERVAL), así que la ventana se cuenta
 * en ESCANEOS, no en ticks: 12 escaneos ≈ 3 s. Si el escaneo trae otro objetivo, o ninguno, el
 * contador se borra entero — no se guarda progreso entre víctimas.
 */
public final class ScouterOverload {
    private ScouterOverload() {}

    /** Escaneos consecutivos sobre el mismo objetivo antes de reventar. 12 × 5 ticks = 3 s. */
    private static final int SCANS_TO_BREAK = 12;

    private record Progress(int entityId, int scans) {}

    private static final Map<UUID, Progress> STATE = new ConcurrentHashMap<>();

    /**
     * Un escaneo. {@code overEntityId} es el id de la entidad que está POR ENCIMA del tope, o
     * -1 si el objetivo actual es legible (o no hay).
     * @return true si el scouter está en plena sobrecarga y el cliente debe enloquecer la cifra.
     */
    public static boolean tick(ServerPlayer sp, int overEntityId) {
        UUID id = sp.getUUID();

        if (overEntityId < 0) {
            STATE.remove(id);
            return false;
        }

        Progress prev = STATE.get(id);
        int scans = (prev != null && prev.entityId() == overEntityId) ? prev.scans() + 1 : 1;

        if (scans >= SCANS_TO_BREAK) {
            STATE.remove(id);
            ScouterStacks.breakScouter(sp);
            return false;
        }

        STATE.put(id, new Progress(overEntityId, scans));
        return true;
    }

    public static void forget(ServerPlayer sp) {
        STATE.remove(sp.getUUID());
    }
}