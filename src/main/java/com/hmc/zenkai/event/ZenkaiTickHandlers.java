package com.hmc.zenkai.event;

import com.hmc.zenkai.event.tick.*;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.aura.TurboServerState;
import com.hmc.zenkai.feature.combat.DownedDeathGuard;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.sense.ScouterOverload;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * ORQUESTADOR del tick de jugador. No contiene lógica: resuelve el contexto, llama a los
 * sistemas de event.tick en ORDEN y sincroniza al final.
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
        // MUERTE REAL EN CURSO: el jugador está en la pantalla de muerte esperando respawn.
        // Si seguimos ticando, el sync final -> mirrorHealth le devuelve 1 de vida y el
        // servidor IGNORA la petición de reaparecer (exige getHealth() <= 0). Las muertes
        // CANCELADAS (derribado, otro mundo) restauran la vida antes de sync, así que al
        // tick siguiente ya llegan vivas y no las corta.
        if (p.isDeadOrDying()) return;

        TickCtx c = new TickCtx(p,
                p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()),
                p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()),
                p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()));

        PersistentEffectsSystem.tick(c);

        if (RaceGateSystem.handle(c))          return;
        if (DownedSystem.handleDowned(c))      return;
        if (DownedSystem.handleBodyDepleted(c)) return;

        // Antes de vuelo/movimiento: ambos leen el factor que este sistema escribe.
        WeightLoadSystem.tick(c);

        boolean turboOn = p instanceof ServerPlayer sp && TurboServerState.isOn(sp);

        FlightSystem.tick(c, turboOn);
        FlightSystem.tickBoostHitbox(c);

        if (FormSystem.tick(c)) return;
        KaiokenSystem.tick(c);
        KiChargeSystem.tick(c);
        RacePassiveSystem.tick(c);
        RegenSystem.tick(c);
        // Sondeo de logros: 1x/s. No va por evento porque los stats cambian por media docena
        // de vías (entrenar, comprar, transformarse, ponerse pesas) y enganchar cada una sería
        // garantizar que se olvida una. Misma cadencia que el trigger de localización vanilla.
        if (p instanceof ServerPlayer sp && p.tickCount % 20 == 0) {
            ZenkaiTriggers.STAT_THRESHOLD.get().trigger(sp);
        }
        GroundMovementSystem.tick(c, turboOn);

        PlayerLifeCycle.syncIfServer(p);
    }

    /** Intención de salto -> WeightLoadSystem. jumpFromGround() se ejecuta aunque
     *  JUMP_STRENGTH esté a 0, así que el evento llega igual con el jugador sobrecargado. */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide()) {
            WeightLoadSystem.noteJump(p);
        }
    }

    /** Limpia el estado transitorio por jugador al desloguear. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DownedDeathGuard.forget(e.getEntity().getUUID());
        PlayerTickState.forget(e.getEntity().getUUID());
        RacePassiveSystem.forget(e.getEntity().getUUID());
        WeightLoadSystem.forget(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer sp) ScouterOverload.forget(sp);
    }
}