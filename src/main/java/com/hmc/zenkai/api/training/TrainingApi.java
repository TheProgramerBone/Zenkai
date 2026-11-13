package com.hmc.zenkai.api.training;

import com.hmc.zenkai.feature.training.TrainingHooks;
import net.minecraft.server.level.ServerPlayer;

/**
 * Puerta de entrada de los addons al TP de entrenamiento, para un minijuego o una actividad propia. El TP pasa por el
 * mismo núcleo que Meditación y Target Practice: fatiga (con su PROPIA categoría {@code addon}, que no se mezcla con la
 * de combate ni con la de los minijuegos de Zenkai), pesas, gravedad de la HTC, bonos de raza y de Potential Unlock, y
 * resto fraccional. Después salta {@code TPGainedEvent} con la categoría {@code addon}, que otros addons pueden
 * bonificar o anular. Solo servidor.
 *
 * ANTIABUSO: la cifra la decide TU addon, así que ponle un techo por sesión en el servidor. Zenkai no sabe cuánto
 * tarda ni cuánto cuesta tu actividad; la fatiga frena la repetición pero no sustituye a un límite razonable.
 */
public final class TrainingApi {
    private TrainingApi() {}

    /**
     * @param rawTp TP bruto de la actividad, antes de fatiga y bonos (mismo orden de magnitud que el de Meditación)
     * @return el TP ENTERO realmente concedido (0 si la cifra quedó en el resto fraccional, se canceló o no hay raza elegida)
     */
    public static int award(ServerPlayer player, double rawTp) {
        return TrainingHooks.grantFromAddon(player, rawTp);
    }
}
