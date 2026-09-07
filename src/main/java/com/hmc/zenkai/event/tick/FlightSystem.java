package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerStateFlags;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.weights.WeightSystem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Vuelo: habilitación, velocidad, coste de ki y hitbox del boost. */
public final class FlightSystem {
    private FlightSystem() {}

    /** Empujón del turbo sobre la velocidad final. Mismo valor que en tierra a propósito:
     *  el turbo debe sentirse igual corriendo que volando. Candidato a StatsConfig. */
    private static final double TURBO_SPEED_MULT = 1.35;

    // ── Aterrizaje de emergencia ────────────────────────────────────────────
    /** Por debajo de esta altura sobre el suelo, perder la capacidad de volar corta en seco
     *  como antes: una caída de un par de bloques no necesita planeo. */
    private static final double EMERGENCY_LANDING_MIN_HEIGHT = 4.0;
    /** Tope de ticks del planeo forzado — red de seguridad (vacío bajo los pies, ground-check
     *  que falla) más que el camino normal: lo normal es cortar antes por tocar el suelo. */
    private static final int EMERGENCY_LANDING_MAX_TICKS = 200; // 10s
    /** Velocidad multiplicada casi a la mínima: el planeo debe sentirse frenado, no como vuelo
     *  normal con descuento. */
    private static final double EMERGENCY_LANDING_SPEED_MULT = 0.35;
    /** Descenso forzado (bloques/tick) mientras dura el planeo — `ab.flying` sigue en true
     *  durante el planeo (anula la gravedad de vanilla), así que sin esto el jugador se quedaría
     *  flotando en vez de bajar. */
    private static final double EMERGENCY_LANDING_DESCENT_SPEED = 0.12;
    private static final double EMERGENCY_LANDING_VERTICAL_LERP = 0.3;
    private static final double GROUND_CHECK_MAX_DIST = 64.0;

    public static void tick(TickCtx c, boolean turboOn) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        PlayerStateFlags flags = att.flags();
        if (p.isCreative() || p.isSpectator()) return;

        var ab = p.getAbilities();
        // Mirador del tick ANTERIOR, leído ANTES de refrescarlo más abajo — hace falta para
        // detectar el flanco "empieza a volar" (despegue con impulso, ver más abajo).
        boolean wasFlying = att.isFlyEnabled();

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

        // Aterrizaje de emergencia: perder canFlyNow a media altura (sobrecarga, respec de la
        // skill Fly) ya NO corta mayfly/flying en el mismo tick — eso tiraba al jugador en
        // caída libre desde donde estuviera. Un planeo forzado de unos ticks (velocidad muy
        // recortada + descenso constante) lo baja al suelo antes de cortar de verdad.
        int grace = flags.getEmergencyLandingTicks();
        if (canFlyNow) {
            grace = 0; // recuperó la capacidad (p. ej. soltó peso): fin del planeo, vuelo normal
        } else if (ab.flying && grace <= 0
                && heightAboveGround(p) > EMERGENCY_LANDING_MIN_HEIGHT) {
            grace = EMERGENCY_LANDING_MAX_TICKS; // se acaba de perder la capacidad en el aire
        }
        boolean emergencyLanding = grace > 0 && !p.onGround();
        flags.setEmergencyLandingTicks(emergencyLanding ? grace - 1 : 0);

        boolean effectiveCanFly = canFlyNow || emergencyLanding;
        if (ab.mayfly != effectiveCanFly) {
            ab.mayfly = effectiveCanFly;
            if (!effectiveCanFly) ab.flying = false;
            p.onUpdateAbilities();
        }

        // isFlyEnabled ahora es un ESPEJO de solo lectura de ab.flying, no algo que el
        // jugador active: abilities.flying es fiable para el jugador local pero NO viaja a
        // los clientes que trackean a este jugador (no es un dato sincronizado de entidad),
        // así que ZenkaiCommonAnimations/AuraTiltController/ClientZenkaiPalTick leen este
        // flag —que sí sincroniza PlayerStatsAttachment— para saber si un jugador REMOTO
        // está volando de verdad en este preciso tick.
        if (att.isFlyEnabled() != ab.flying) att.setFlyEnabled(ab.flying);

        // Despegue con impulso (ki jump): flanco !wasFlying -> ab.flying. Sale del doble salto
        // NATIVO de vanilla (mayfly ya en true), así que el único sitio server-side donde se
        // puede atrapar la transición es comparando contra el espejo del tick anterior.
        if (!wasFlying && ab.flying && canFlyNow) {
            double impulse = ServerConfig.flyTakeoffImpulse();
            Vec3 dv = p.getDeltaMovement();
            if (dv.y < impulse) p.setDeltaMovement(dv.x, impulse, dv.z); // no restar un salto ya más rápido
            double cost = ServerConfig.flyTakeoffKiCost() * SkillEffects.flyKiDrainFactor(p);
            if (cost > 0.0) att.addKi(-cost);
        }

        boolean control  = flags.isFlyBoosting();   // Ctrl+W en vuelo
        boolean flyTurbo = ab.flying && control && turboOn && !emergencyLanding;

        float newSpeed;
        if (emergencyLanding) {
            // Planeo forzado: velocidad fija y baja, nada de escalones/turbo/pesas — el punto
            // es frenar, no seguir rindiendo como vuelo normal.
            newSpeed = (float) (ServerConfig.flyBaseSpeed() * EMERGENCY_LANDING_SPEED_MULT);
        } else {
            // Techo = habilidad Fly. DEX ya NO interviene: alimentaba defensa, velocidad y vuelo
            // a la vez, así que tocar el balance defensivo movía la velocidad de rebote.
            double max = Math.min(ServerConfig.flyMultiplierCap(), SkillEffects.flySpeedFactor(p));
            // Se interpola desde 1.0 (vuelo vanilla): con 0% de poder el multiplicador cae a 1.0.
            double mult = 1.0 + (max - 1.0)
                    * PerformanceTier.of(control) * att.powerFraction();
            // El turbo multiplica FUERA del escalón: efecto constante y perceptible.
            if (flyTurbo) mult *= TURBO_SPEED_MULT;
            // Pesas al final, igual que en tierra.
            mult *= WeightSystem.moveFactor(att.getWeightLoad());
            newSpeed = (float) (ServerConfig.flyBaseSpeed() * mult);
        }
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

        if (emergencyLanding) {
            // `ab.flying` sigue true durante el planeo (anula la gravedad de vanilla): sin este
            // empuje explícito hacia abajo el jugador se quedaría flotando en el sitio en vez
            // de bajar.
            Vec3 dv = p.getDeltaMovement();
            double newY = Mth.lerp(EMERGENCY_LANDING_VERTICAL_LERP, dv.y, -EMERGENCY_LANDING_DESCENT_SPEED);
            p.setDeltaMovement(dv.x, newY, dv.z);
        }

        // Coste del vuelo turbo: ki por tick reducido por Fly. El drenaje base del aura lo
        // cobra TurboServerState por su cuenta, y se auto-apaga si el ki llega a 0.
        if (flyTurbo) {
            double drain = ServerConfig.flyKiDrainPerTick() * SkillEffects.flyKiDrainFactor(p);
            if (drain > 0.0) att.addKi(-drain);
        }
        if (!ab.flying) flags.setFlyBoosting(false);
    }

    /** Distancia en bloques hasta el primer bloque sólido por debajo del jugador, con tope
     *  GROUND_CHECK_MAX_DIST (para no rayar hasta el fondo del mundo sobre un vacío/HFIL). Usa
     *  el mismo patrón de ClipContext que InstantTransmissionSystem. */
    private static double heightAboveGround(Player p) {
        Vec3 from = p.position();
        Vec3 to = from.add(0, -GROUND_CHECK_MAX_DIST, 0);
        HitResult hit = p.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) return GROUND_CHECK_MAX_DIST;
        return from.y - hit.getLocation().y;
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