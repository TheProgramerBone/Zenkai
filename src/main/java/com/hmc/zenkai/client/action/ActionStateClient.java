package com.hmc.zenkai.client.action;

import com.hmc.zenkai.feature.action.ActionContext;
import com.hmc.zenkai.feature.action.ActionState;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Espejo cliente de ActionState, por id de entidad. Lo llena ActionStateSyncPacket.
 *
 * Sirve para dos cosas: que la predicción del jugador local use el MISMO ActionContext que
 * evalúa el servidor, y que las capas PAL sepan qué está haciendo un jugador remoto sin que
 * PAL tenga que inferirlo (base de la fase 6).
 */
public final class ActionStateClient {

    private ActionStateClient() {}

    private static final Map<Integer, ActionState> STATES = new ConcurrentHashMap<>();

    public static void accept(int entityId, ActionState st) {
        if (st == null || st.isNone()) STATES.remove(entityId);
        else STATES.put(entityId, st);
    }

    public static ActionState of(int entityId) {
        return STATES.getOrDefault(entityId, ActionState.NONE);
    }

    public static ActionState local() {
        var p = Minecraft.getInstance().player;
        return p == null ? ActionState.NONE : of(p.getId());
    }

    /** Al cambiar de mundo/dimensión los ids de entidad dejan de valer. */
    public static void clear() { STATES.clear(); }

    public static void prune(Level level) {
        STATES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /**
     * ActionContext del jugador local. Las guardas las evalúa ActionRules, igual que en el
     * servidor; lo único propio de este lado es de dónde salen los booleanos.
     *
     * physBusy y chargingSlot ya NO se inventan: llegan del servidor por sync. Era el ⚠ que
     * quedaba pendiente del paso 2.
     */
    public static ActionContext contextOf(AbstractClientPlayer p, boolean combatMode,
                                          boolean blockingLocal) {
        var att = PlayerStatsAttachment.get(p);
        ActionState st = of(p.getId());
        long now = p.level().getGameTime();
        return new ActionContext(
                att.isRaceChosen(),
                combatMode,
                blockingLocal,
                att.flags().isDowned(),
                p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty(),
                st.physBusy(),
                st.chargingSlot(),
                st.elapsed(now));
    }
}