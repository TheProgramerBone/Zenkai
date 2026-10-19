package com.hmc.zenkai.client.fly;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vuelo estilo elytra: el movimiento sigue la MIRADA, no solo el yaw.
 * El vuelo creativo de vanilla mueve en horizontal según yBodyRot e ignora el pitch por
 * completo; el eje vertical es exclusivamente espacio/shift. Aquí se sustituye el vector de
 * velocidad DESPUÉS de la física del tick: con adelante pulsado se acerca a
 * `mirada × velocidad de crucero`, y esa velocidad sale de `flyingSpeed` (que ya lleva la
 * rampa y el turbo del servidor), no de una medición. Antes se reorientaba la velocidad
 * horizontal vanilla (recortándola por cos(pitch) y reconstruyendo la vertical desde una
 * referencia medida solo volando nivelado): desde parado, o con pitch pronunciado, la
 * referencia no existía y mirar arriba/abajo iba lentísimo (log A0, 2026-10-18).
 * SOLO jugador local y solo cliente: la posición del jugador es predicción de cliente y el
 * servidor la acepta porque abilities.flying ya está puesto. No hace falta paquete ni mixin.
 * Espacio y shift siguen mandando: si los pulsas, esto se aparta y el control vertical vuelve
 * a ser manual. Es deliberado — subir en vertical pura sin cambiar de rumbo tiene que seguir
 * siendo posible.
 */
public final class FlightMovement {

    private FlightMovement() {}

    /** Umbral de input para considerar que hay avance. */
    private static final float INPUT_DEADZONE = 0.1f;

    /** Frenazo/hover: suavizado del HORIZONTAL cuando no hay ningún input de movimiento (ni
     *  adelante ni lateral). Alto a propósito — el "stop en seco" de DBZ tiene que sentirse casi
     *  instantáneo, no como una desaceleración gradual. Solo toca X/Z: la vertical (space/shift)
     *  sigue mandando exactamente igual que sin esto. */
    private static final double BRAKE_LERP = 0.6;

    /** Por debajo de esta velocidad horizontal (al cuadrado) el frenazo corta a cero de golpe,
     *  para no dejar un arrastre residual perceptible que el lerp nunca termina de matar. */
    private static final double BRAKE_STOP_SPEED_SQ = 4.0e-4; // (0.02 bloques/tick)^2

    /** Velocidad terminal de vanilla volando en horizontal: aceleración a por tick con fricción
     *  de aire 0.91, v = (v + a) * 0.91 -> v = a * 0.91 / 0.09 ≈ 10.1 a. Medido en el log A0:
     *  flyingSpeed efectivo 0.2 -> 1.98 bloques/tick. Así la velocidad de crucero coincide con la
     *  que daba el vuelo vanilla nivelado y los escalones de poder siguen valiendo lo mismo. */
    private static final double TERMINAL_FACTOR = 10.1;

    /** Fricción de aire horizontal de vanilla volando (se aplica tras el empuje). */
    static final double AIR_FRICTION = 0.91;

    /** Player.travel() con abilities.flying multiplica la Y por este valor cada tick. */
    static final double FLY_VERTICAL_DAMP = 0.6;

    /** Cuánto se acerca el vector de velocidad a `mirada × velocidad` cada tick. Sube la
     *  velocidad desde parado y gira hacia donde miras; el ritmo de ganar potencia lo marca la
     *  rampa del servidor (flyingSpeed), no este valor. */
    private static final double STEER_LERP = 0.3;

    public static void tick(LocalPlayer p, boolean flying) {
        if (!flying) {
            FlightCruise.reset();
            return;
        }

        // Crucero: "adelante" pulsado de forma virtual (ver FlightCruise). Manda sobre el frenazo
        // y no necesita W; el movimiento es el mismo que con W sostenida, salvo que vanilla no
        // empuja (no hay input real), así que no hay empuje que descontar más abajo.
        boolean cruising = FlightCruise.tickKeepActive(p);
        boolean manualVertical = p.input.jumping || p.input.shiftKeyDown;

        float fwd = cruising ? 1.0f : p.input.forwardImpulse;

        if (!cruising) {
            // Frenazo/hover: SIN ningún input horizontal (ni adelante ni lateral), matar el
            // deslizamiento en vez de dejar solo la fricción pasiva de vuelo de vanilla (lenta y
            // apenas perceptible a velocidad de crucero). Deliberadamente NO exige que space/shift
            // estén sueltos: quedarse quieto mientras se sube o baja en vertical pura también debe
            // frenar el arrastre horizontal residual, y esto solo toca X/Z.
            boolean noHorizontalInput = Math.abs(p.input.forwardImpulse) < INPUT_DEADZONE
                    && Math.abs(p.input.leftImpulse) < INPUT_DEADZONE;
            if (noHorizontalInput) {
                brake(p);
                return;
            }
            // Sin avance (solo lateral) o con control vertical manual (space/shift) manda
            // vanilla: el avance por mirada solo se aplica con adelante/atrás pulsado.
            if (Math.abs(fwd) < INPUT_DEADZONE || manualVertical) return;
        }

        // Vanilla DUPLICA flyingSpeed al esprintar (Control = sprint); FlightSystem ya lo
        // compensa en el servidor dividiendo entre 2, así que aquí se deshace igual.
        double accel = p.getAbilities().getFlyingSpeed() * (p.isSprinting() ? 2.0 : 1.0);
        double speed = accel * TERMINAL_FACTOR;

        // Velocidad que queremos VER al final del siguiente tick. Esta función corre después de
        // travel(), así que lo que se fija aquí pasa antes por la física de vanilla en el tick
        // siguiente; hay que descontar lo que ésta hace o la velocidad final no coincide:
        //  - vertical: Player.travel() con abilities.flying deja Y al 60 % de la del tick previo
        //    (log A0: a pitch ±90 el equilibrio era ~0.8 en vez de ~2.0);
        //  - horizontal: fricción de aire 0.91 y el empuje de W en dirección del yaw (aunque
        //    mires a plomo, deriva ~0.49 bloques/tick en horizontal). En crucero no hay W real.
        double sign = Math.signum(fwd);
        Vec3 look = p.getLookAngle().scale(speed * sign);
        double yawRad = Math.toRadians(p.getYRot());
        double pushX = cruising ? 0.0 : -Math.sin(yawRad) * accel * sign;
        double pushZ = cruising ? 0.0 : Math.cos(yawRad) * accel * sign;
        // El suavizado se hace sobre la velocidad REAL (d, ya pasada por la física) y la
        // compensación solo sobre el valor que se escribe: suavizar contra el objetivo ya
        // compensado dejaba la velocidad final en ~52 % del objetivo (log A0, segunda prueba).
        Vec3 d = p.getDeltaMovement();
        double nextX = Mth.lerp(STEER_LERP, d.x, look.x);
        double nextY = Mth.lerp(STEER_LERP, d.y, look.y);
        double nextZ = Mth.lerp(STEER_LERP, d.z, look.z);
        // Space/shift en crucero: la vertical queda en manos de vanilla (control manual) y solo
        // se mantiene el avance horizontal.
        double outY = (cruising && manualVertical) ? d.y : nextY / FLY_VERTICAL_DAMP;
        p.setDeltaMovement(nextX / AIR_FRICTION - pushX, outY, nextZ / AIR_FRICTION - pushZ);
    }

    /** Frenazo: acerca X/Z a cero con un lerp agresivo y corta en seco por debajo del umbral de
     *  arrastre. No toca Y: space/shift (o la propia gravedad, si vanilla la aplicara) siguen
     *  mandando en vertical exactamente igual que sin esto. */
    private static void brake(LocalPlayer p) {
        Vec3 d = p.getDeltaMovement();
        if (d.x * d.x + d.z * d.z < BRAKE_STOP_SPEED_SQ) {
            if (d.x != 0.0 || d.z != 0.0) p.setDeltaMovement(0.0, d.y, 0.0);
            return;
        }
        p.setDeltaMovement(Mth.lerp(BRAKE_LERP, d.x, 0.0), d.y, Mth.lerp(BRAKE_LERP, d.z, 0.0));
    }
}
