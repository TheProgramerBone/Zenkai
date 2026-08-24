package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.weights.WeightSystem;
import net.minecraft.world.entity.player.Player;

/** Vuelo: habilitación, velocidad, coste de ki y hitbox del boost. */
public final class FlightSystem {
    private FlightSystem() {}

    /** Empujón del turbo sobre la velocidad final. Mismo valor que en tierra a propósito:
     *  el turbo debe sentirse igual corriendo que volando. Candidato a StatsConfig. */
    private static final double TURBO_SPEED_MULT = 1.35;

    public static void tick(TickCtx c, boolean turboOn) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (p.isCreative() || p.isSpectator()) return;

        var ab = p.getAbilities();
        // mayfly = CAPACIDAD, sin toggle intermedio: igual que el vuelo creativo, en cuanto se
        // tiene la habilidad Fly (y no se está sobrecargado) el doble salto NATIVO de vanilla
        // despega y aterriza por su cuenta. Antes había un flag propio (isFlyEnabled) que un
        // keybind ponía en true ANTES de que mayfly se activara, y el propio doble salto que
        // el jugador usaba para pedir ese toggle competía con el doble salto que vanilla
        // necesita ver con mayfly YA en true para despegar de verdad — el toggle llegaba un
        // tick tarde (viaje de paquete de por medio) y esa ventana de doble salto ya se había
        // cerrado: el jugador se caía con el ícono de "modo" encendido, y cada intento
        // siguiente volvía a alternar el flag sin nunca despegar (ver CLAUDE.md, sección de
        // vuelo). Quitar el intermedio y dejar que mayfly refleje solo la capacidad arregla
        // la carrera de raíz.
        boolean overloaded = WeightSystem.isOverloaded(att.getWeightLoad());
        boolean canFlyNow = SkillEffects.canFly(p) && !overloaded;
        if (ab.mayfly != canFlyNow) {
            ab.mayfly = canFlyNow;
            if (!canFlyNow) ab.flying = false;
            p.onUpdateAbilities();
        }

        // isFlyEnabled ahora es un ESPEJO de solo lectura de ab.flying, no algo que el
        // jugador active: abilities.flying es fiable para el jugador local pero NO viaja a
        // los clientes que trackean a este jugador (no es un dato sincronizado de entidad),
        // así que ZenkaiCommonAnimations/AuraTiltController/ClientZenkaiPalTick leen este
        // flag —que sí sincroniza PlayerStatsAttachment— para saber si un jugador REMOTO
        // está volando de verdad en este preciso tick.
        if (att.isFlyEnabled() != ab.flying) att.setFlyEnabled(ab.flying);

        boolean control  = att.flags().isFlyBoosting();   // Ctrl+W en vuelo
        boolean flyTurbo = ab.flying && control && turboOn;

        // Techo = habilidad Fly. DEX ya NO interviene: alimentaba defensa, velocidad y vuelo
        // a la vez, así que tocar el balance defensivo movía la velocidad de rebote.
        double max = Math.min(CommonConfig.flyMultiplierCap(), SkillEffects.flySpeedFactor(p));
        // Se interpola desde 1.0 (vuelo vanilla): con 0% de poder el multiplicador cae a 1.0.
        double mult = 1.0 + (max - 1.0)
                * PerformanceTier.of(control) * att.powerFraction();
        // El turbo multiplica FUERA del escalón: efecto constante y perceptible.
        if (flyTurbo) mult *= TURBO_SPEED_MULT;
        // Pesas al final, igual que en tierra.
        mult *= WeightSystem.moveFactor(att.getWeightLoad());

        float newSpeed = (float) (CommonConfig.flyBaseSpeed() * mult);
        // Player.getFlyingSpeed() DUPLICA la velocidad al esprintar, y Control ES la tecla de
        // sprint: sin compensar, el escalón medio se llevaba un x2 gratis que rompía la
        // proporción entre escalones.
        if (p.isSprinting()) newSpeed /= 2.0F;

        // setFlyingSpeed en servidor NO llega al cliente sin onUpdateAbilities(), pero llamarlo
        // cada tick sería un paquete por tick: solo cuando la velocidad cambia de verdad.
        if (Math.abs(ab.getFlyingSpeed() - newSpeed) > 1.0e-4F) {
            ab.setFlyingSpeed(newSpeed);
            p.onUpdateAbilities();
        }

        // Coste del vuelo turbo: ki por tick reducido por Fly. El drenaje base del aura lo
        // cobra TurboServerState por su cuenta, y se auto-apaga si el ki llega a 0.
        if (flyTurbo) {
            double drain = CommonConfig.flyKiDrainPerTick() * SkillEffects.flyKiDrainFactor(p);
            if (drain > 0.0) att.addKi(-drain);
        }
        if (!ab.flying) att.flags().setFlyBoosting(false);
    }

    /** Hitbox/cámara "acostado" durante el boost de vuelo. Se llama justo después de tick(),
     *  así que el espejo isFlyEnabled ya quedó al día este mismo tick — pero aquí se lee
     *  getAbilities().flying directo porque esta instancia SÍ es la autoritativa (no una
     *  copia remota), no la sincronizada. */
    public static void tickBoostHitbox(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        boolean flyingNow = p.getAbilities().flying && !p.isSpectator();
        if (!flyingNow) att.flags().setFlyBoosting(false);

        boolean prone = att.flags().isFlyBoosting();
        if (prone != att.flags().isBoostSizeApplied()) {
            att.flags().setBoostSizeApplied(prone);
            p.refreshDimensions();
        }
    }
}