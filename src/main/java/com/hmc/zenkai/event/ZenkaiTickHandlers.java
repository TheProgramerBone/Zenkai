package com.hmc.zenkai.event;

import com.hmc.zenkai.event.tick.DownedSystem;
import com.hmc.zenkai.event.tick.FlightSystem;
import com.hmc.zenkai.event.tick.FormSystem;
import com.hmc.zenkai.event.tick.GroundMovementSystem;
import com.hmc.zenkai.event.tick.KaiokenSystem;
import com.hmc.zenkai.event.tick.KiChargeSystem;
import com.hmc.zenkai.event.tick.PersistentEffectsSystem;
import com.hmc.zenkai.event.tick.PlayerTickState;
import com.hmc.zenkai.event.tick.RaceGateSystem;
import com.hmc.zenkai.event.tick.RegenSystem;
import com.hmc.zenkai.event.tick.TickCtx;
import com.hmc.zenkai.feature.aura.TurboServerState;
import com.hmc.zenkai.feature.combat.DownedDeathGuard;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * ORQUESTADOR del tick de jugador. No contiene lógica: resuelve el contexto, llama a los
 * sistemas de event.tick en ORDEN y sincroniza al final.
 *
 * EL ORDEN ES SEMÁNTICA, NO ESTILO:
 *   - los efectos persistentes van ANTES de cualquier corte (deben aplicarse siempre);
 *   - los tres gates (raza / derribado / body 0) cortan el tick por completo;
 *   - forms corta si está transformando, así que va antes de kaioken/carga/regen/movimiento;
 *   - movimiento va al final porque escribe el modificador que forms pudo haber borrado.
 * Reordenar estas llamadas cambia el comportamiento del juego.
 */
public class ZenkaiTickHandlers {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;

        TickCtx c = new TickCtx(p,
                p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()),
                p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()),
                p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()));

        PersistentEffectsSystem.tick(c);

        if (RaceGateSystem.handle(c))          return;
        if (DownedSystem.handleDowned(c))      return;
        if (DownedSystem.handleBodyDepleted(c)) return;

        boolean turboOn = p instanceof ServerPlayer sp && TurboServerState.isOn(sp);

        FlightSystem.tick(c, turboOn);
        FlightSystem.tickBoostHitbox(c);

        if (FormSystem.tick(c)) return;
        KaiokenSystem.tick(c);

        KiChargeSystem.tick(c);
        RegenSystem.tick(c);
        GroundMovementSystem.tick(c, turboOn);

        PlayerLifeCycle.syncIfServer(p);
    }

    /** Limpia el estado transitorio por jugador al desloguear. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DownedDeathGuard.forget(e.getEntity().getUUID());
        PlayerTickState.forget(e.getEntity().getUUID());
    }
}