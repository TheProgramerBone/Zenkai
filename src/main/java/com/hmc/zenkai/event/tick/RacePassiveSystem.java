package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.combat.CombatRegen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RacePassives;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tick de las pasivas raciales que necesitan ESTADO. Las que son un simple multiplicador
 * (regeneración namekiana, TP humano, descuento arcosiano) no pasan por aquí: las consulta
 * directamente el sistema al que afectan vía RacePassives, que es donde se pueden leer junto
 * al cálculo que modifican.
 * Aquí viven las dos que tienen memoria:
 *   SAIYAN — el zenkai necesita recordar que estuviste a punto de morir, notar que te has
 *     recuperado, y contar la duración y el enfriamiento.
 *   MAJIN  — la regeneración pasiva es continua y no depende de la comida, así que no puede
 *     colgarse de RegenSystem, que corta con hambre baja.
 * ESTADO EN MEMORIA, NO EN NBT, a propósito: un zenkai a medio consumir no debe sobrevivir a
 * un reinicio del servidor. Es un subidón momentáneo, no una propiedad del personaje; el
 * enfriamiento se pierde con él, y perder un enfriamiento a favor del jugador es el error
 * barato de los dos.
 * DÓNDE VA EN EL ORDEN DEL TICK: después de los gates (un jugador derribado no regenera ni
 * dispara zenkai) y antes de RegenSystem, porque la regeneración majin y la del sistema
 * general escriben en el mismo pool y el orden decide cuál llega antes al tope.
 */
public final class RacePassiveSystem {
    private RacePassiveSystem() {}

    /** Estado por jugador. Se limpia en el logout desde ZenkaiTickHandlers. */
    private static final Map<UUID, ZenkaiState> STATES = new HashMap<>();

    private static final class ZenkaiState {
        boolean armed;          // estuvo por debajo del umbral y aún no se ha recuperado
        int activeTicks;        // lo que queda de bonus
        long lastTriggerTick;   // para el enfriamiento
        double regenCarry;      // fracción de body majin pendiente
    }

    private static ZenkaiState state(UUID id) {
        return STATES.computeIfAbsent(id, k -> new ZenkaiState());
    }

    public static void forget(UUID id) { STATES.remove(id); }

    /** ¿Tiene el zenkai activo ahora mismo? Lo consulta FormSystem para el multiplicador. */
    public static boolean zenkaiActive(Player p) {
        ZenkaiState s = STATES.get(p.getUUID());
        return s != null && s.activeTicks > 0;
    }

    /** Bonus multiplicativo que aporta el zenkai. 1.0 si no está activo. */
    public static double zenkaiMultiplier(Player p) {
        return zenkaiActive(p) ? 1.0 + RacePassives.ZENKAI_BONUS : 1.0;
    }

    /** Segundos restantes del zenkai. Para la pantalla de stats. */
    public static int zenkaiSecondsLeft(Player p) {
        ZenkaiState s = STATES.get(p.getUUID());
        return s == null ? 0 : Math.max(0, s.activeTicks / 20);
    }

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (att == null || !att.isRaceChosen()) return;

        ZenkaiState s = state(p.getUUID());

        // El contador corre siempre, tenga la raza que tenga: si a alguien le cambian la raza
        // con /zenkai race set mientras tiene un zenkai activo, se le acaba solo en vez de
        // quedarse pegado para siempre.
        if (s.activeTicks > 0) {
            s.activeTicks--;
            if (s.activeTicks == 0 && p instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.zenkai.zenkai_faded"), true);
            }
        }

        switch (att.getRace()) {
            case SAIYAN -> tickZenkai(p, att, s);
            case MAJIN  -> tickMajinRegen(p, att, s);
            default     -> { }
        }
    }

    /**
     * Zenkai: armar al bajar del 20 % de body, disparar al volver al 60 %.
     *
     * Dos umbrales y no uno porque con uno solo el bonus saltaría en el mismo instante del
     * golpe que casi te mata, que es cuando menos sirve — el saiyan del canon se hace fuerte
     * DESPUÉS de curarse, no durante la paliza. Así además el jugador tiene que sobrevivir y
     * recuperarse activamente para cobrarlo.
     */
    private static void tickZenkai(Player p, PlayerStatsAttachment att, ZenkaiState s) {
        int max = att.getBodyMax();
        if (max <= 0) return;
        double frac = att.getBody() / (double) max;

        if (frac <= RacePassives.ZENKAI_TRIGGER && att.getBody() > 0) {
            s.armed = true;
            return;
        }

        if (!s.armed || frac < RacePassives.ZENKAI_RECOVER) return;

        long now = p.level().getGameTime();
        if (now - s.lastTriggerTick < RacePassives.ZENKAI_COOLDOWN) {
            // En enfriamiento: se desarma igualmente, o el jugador acumularía un disparo
            // pendiente y lo cobraría gratis en cuanto expirara el cooldown.
            s.armed = false;
            return;
        }

        s.armed = false;
        s.activeTicks = RacePassives.ZENKAI_DURATION;
        s.lastTriggerTick = now;

        if (p instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.zenkai.zenkai_triggered",
                    Math.round(RacePassives.ZENKAI_BONUS * 100)), true);
        }
    }

    /** Regeneración majin: continua, sin depender de la comida, una vez por segundo. */
    private static void tickMajinRegen(Player p, PlayerStatsAttachment att, ZenkaiState s) {
        if (p.tickCount % 20 != 0) return;
        int cur = att.getBody(), max = att.getBodyMax();
        if (cur <= 0 || cur >= max) return;

        // El canal del majin es propio y no pasa por RegenSystem, así que la penalización de
        // combate hay que aplicarla también aquí o el majin sería el único que se cura a
        // ritmo normal peleando. CombatRegen ya le garantiza el suelo racial.
        s.regenCarry += max * RacePassives.MAJIN_PASSIVE_REGEN * CombatRegen.bodyMult(p);
        int whole = (int) s.regenCarry;
        if (whole > 0) {
            s.regenCarry -= whole;
            att.addBody(whole);
        }
    }

    /** Namekiano: cobra el ki de lo que RegenSystem le haya regenerado de más. */
    public static void chargeNamekianRegen(Player p, PlayerStatsAttachment att, int bodyGained) {
        if (bodyGained <= 0 || !RacePassives.is(p, Race.NAMEKIAN)) return;
        att.consumeEnergy(RacePassives.namekianRegenKiCost(bodyGained));
    }
}