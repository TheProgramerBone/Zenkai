package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Alguien levantó a un jugador que estaba DERRIBADO, dentro de su ventana (todavía no había muerto). Enganche de las rage
 * forms para «salvar a un aliado» (ALLY_PROTECTION): solo cuenta la intervención efectiva de OTRO jugador (revivirlo con la
 * mano vacía o darle una senzu); recuperarse solo (regeneración, senzu propia, agua curativa, tótem) no la publica, y
 * estar cerca tampoco. Llegar por Transmisión Instantánea no tiene gancho propio: lo que cuenta es el acto de revivir.
 * Quién es aliado lo decide quien escucha (party); el evento solo dice quién actuó sobre quién. No se puede cancelar.
 *
 * @param ticksLeft ticks que le quedaban al derribado antes de la muerte real (siempre > 0: se publica dentro de la ventana)
 */
public class PlayerRescuedEvent extends Event {
    public enum Method { REVIVE, SENZU }

    private final ServerPlayer rescuer;
    private final ServerPlayer rescued;
    private final Method method;
    private final long ticksLeft;

    public PlayerRescuedEvent(ServerPlayer rescuer, ServerPlayer rescued, Method method, long ticksLeft) {
        this.rescuer = rescuer;
        this.rescued = rescued;
        this.method = method;
        this.ticksLeft = ticksLeft;
    }

    public ServerPlayer rescuer() { return rescuer; }
    public ServerPlayer rescued() { return rescued; }
    public Method method() { return method; }
    public long ticksLeft() { return ticksLeft; }

    /**
     * Publica el rescate si {@code rescued} estaba derribado y {@code rescuer} es otro jugador. Llamar ANTES de curarlo
     * (con el flag de derribo y la ventana todavía en pie) y solo cuando la curación va a aplicarse de verdad.
     * @return true si se publicó
     */
    public static boolean fire(ServerPlayer rescuer, ServerPlayer rescued, Method method) {
        if (rescuer == null || rescuer == rescued) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(rescued);
        if (!att.flags().isDowned()) return false;
        long left = att.flags().getDownedUntil() - rescued.level().getGameTime();
        if (left <= 0) return false;   // ventana agotada: la muerte real es inminente, ya no es un rescate a tiempo
        NeoForge.EVENT_BUS.post(new PlayerRescuedEvent(rescuer, rescued, method, left));
        return true;
    }
}
