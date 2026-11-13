package com.hmc.zenkai.api.rage;

import com.hmc.zenkai.feature.rage.RageIntensity;
import com.hmc.zenkai.feature.rage.RageResult;
import com.hmc.zenkai.feature.rage.RageSystem;
import com.hmc.zenkai.feature.rage.RageTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Puerta de entrada de los addons al sistema de Rage Forms: dispara el detector {@code addon} con la intensidad que
 * decidas. Qué formas despierta lo deciden los datos, igual que con los detectores de Zenkai: un JSON en
 * {@code data/<ns>/zenkai_rage_awakenings/} con {@code "triggers": ["addon"]} y la forma, la raza y la intensidad mínima.
 * El sistema aplica sus reglas de siempre: una forma solo se concede si es la siguiente de la cadena, sin saltos;
 * un mismo {@code eventId} solo recompensa una vez por jugador; y, como los detectores de aliados, el hecho gasta el
 * enfriamiento de rage ({@code rage.ally_event_cooldown}), de modo que un addon no puede encadenar despertares.
 * Solo servidor.
 */
public final class RageApi {
    private RageApi() {}

    /**
     * @param intensity cuánto pesa el hecho (decide qué despertares coinciden: {@code min_intensity})
     * @param eventId   id ÚNICO del hecho para ese jugador, con tu namespace ({@code mi_addon:jefe_derrotado:1});
     *                  una recompensa por id. Puede ser null (no deduplica: tú controlas la repetición)
     * @param related   texto que sale en la Crónica del jugador («Muere Krillin»); null para «alguien»
     * @return qué pasó: despertó una forma, sumó maestría o solo quedó anotado
     */
    public static RageResult trigger(ServerPlayer player, RageIntensity intensity, @Nullable String eventId, @Nullable String related) {
        return RageSystem.handle(player, RageTrigger.ADDON, intensity, eventId, related);
    }
}
