package com.hmc.zenkai.client.fly;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vuelo estilo elytra: el movimiento sigue la MIRADA, no solo el yaw.
 * El vuelo creativo de vanilla mueve en horizontal según yBodyRot e ignora el pitch por
 * completo; el eje vertical es exclusivamente espacio/shift. Aquí se reorienta el vector de
 * velocidad DESPUÉS de la física del tick: lo horizontal se escala por cos(pitch) y la
 * componente vertical que falta se añade, de modo que el módulo total se conserva y mirar
 * arriba sube en vez de sumar velocidad.
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

    /** Suavizado de la componente vertical. Más alto = responde antes, más brusco. */
    private static final double VERTICAL_LERP = 0.5;

    /** Frenazo/hover: suavizado del HORIZONTAL cuando no hay ningún input de movimiento (ni
     *  adelante ni lateral). Más alto que VERTICAL_LERP a propósito — el "stop en seco" de DBZ
     *  tiene que sentirse casi instantáneo, no como una desaceleración gradual. Solo toca X/Z:
     *  la vertical (space/shift) sigue mandando exactamente igual que sin esto. */
    private static final double BRAKE_LERP = 0.6;

    /** Por debajo de esta velocidad horizontal (al cuadrado) el frenazo corta a cero de golpe,
     *  para no dejar un arrastre residual perceptible que el lerp nunca termina de matar. */
    private static final double BRAKE_STOP_SPEED_SQ = 4.0e-4; // (0.02 bloques/tick)^2

    /** Suelo del coseno: mirando a 90° el horizontal se anularía y el vuelo se sentiría
     *  clavado. Con 0.15 siempre queda algo de avance. */
    private static final double MIN_COS = 0.15;

    /**
     * Ganancia vertical. 1.0 = subes tan rápido como avanzas (reparto por ángulo puro).
     * Por encima de 1, ganar altura sale más barato de lo que la física diría; por debajo,
     * cuesta. Separadas porque en DBZ subir y caer no se sienten igual: dejar caer el vuelo
     * suele querer ser más rápido que treparlo.
     */
    private static final double ASCEND_GAIN  = 2;
    private static final double DESCEND_GAIN = 2;

    /** Umbral de coseno por encima del cual se considera "casi nivelado": ahí SÍ nos fiamos
     *  de la velocidad en vivo para refrescar la referencia (ver REF_SPEED). */
    private static final double LEVEL_COS = 0.9;

    /**
     * Arranque en frío / boost recién activado: cuando NO hay una medida real fiable todavía
     * (pitch pronunciado desde el principio — nunca se voló nivelado antes) refSpeed SALTA a
     * esta ESTIMACIÓN de golpe (sin lerp: un arrastre gradual aquí es exactamente lo que se
     * reportó como "no acelera de primerazo") en vez de quedarse ciego esperando una horizontal
     * que este mismo código recorta por coseno cada tick antes de que la aceleración normal de
     * vuelo tenga ocasión de acumular nada. 1.5 es la razón asintótica típica entre
     * `getFlyingSpeed()` y la velocidad de crucero real de vanilla volando (fricción ~0.6/tick
     * con impulso aditivo) — candidata a tunear en juego. VERTICAL_LERP sigue suavizando la Y
     * real que se aplica, así que esto no es un salto de velocidad instantáneo en pantalla,
     * solo deja de haber una referencia clavada en un valor viejo/bajo.
     * IMPORTANTE: esta estimación se reevalúa CADA tick que toque esta rama, no solo la
     * primera vez con refSpeed en 0 — si el jugador YA estaba en modo rápido (fastFlightMode)
     * al despegar, `getFlyingSpeed()` del cliente todavía no refleja el multiplicador
     * boosteado en los primeros ticks (el FlyBoostPacket es C2S, el servidor recalcula la
     * velocidad y la sincroniza de vuelta — esa vuelta de red tarda un par de ticks). Si esta
     * rama solo disparara una vez, refSpeed se quedaría clavado en ese valor bajo/viejo para
     * siempre en cuanto llegara el valor real boosteado. Al reevaluar siempre y solo dejar
     * SUBIR (`coldTarget > refSpeed`), en cuanto la velocidad real sincronizada aparece, la
     * referencia salta a ella sola.
     */
    private static final double COLD_START_CRUISE_MULT = 1.5;

    /**
     * Referencia de velocidad de crucero, para evitar la retroalimentación que hacía que
     * subir en vertical "se quedara sin fuerza" cuanto más se sostenía la mirada hacia
     * arriba (el reporte era exactamente ese: "el ir hacia arriba... se hace lento").
     * ANTES `horiz` salía de leer el delta de ESTE mismo tick, que el tick ANTERIOR ya había
     * encogido por coseno; con la mirada sostenida hacia arriba eso se retroalimenta —cada
     * tick parte de un x/z ya más chico que el anterior, lo encoge otra vez, y la magnitud
     * total cae en picada cuanto más dura el ascenso, aunque ASCEND_GAIN sea 2. Guardar la
     * velocidad de crucero aparte (medida SOLO mientras se vuela casi nivelado, o al detectar
     * que acaba de subir por un boost/turbo) le da al reparto vertical una base que no depende
     * de su propio recorte de ticks anteriores.
     * Campo simple y no un mapa por UUID: FlightMovement es SOLO jugador local (ver doc de
     * clase), no hace falta más que una instancia.
     */
    private static double refSpeed = 0.0;

    public static void tick(LocalPlayer p, boolean flying) {
        if (!flying) {
            refSpeed = 0.0;
            return;
        }

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

        // Sin avance o con control vertical manual (space/shift) no hay referencia de crucero
        // que conservar: la próxima vez que se enganche el ascenso por mirada debe partir de una
        // medición fresca, no de un crucero de hace rato. (El caso "solo lateral, sin adelante"
        // también cae aquí: el reparto de abajo redirige AVANCE a vertical, no strafe.)
        if (Math.abs(p.input.forwardImpulse) < INPUT_DEADZONE
                || p.input.jumping || p.input.shiftKeyDown) {
            refSpeed = 0.0;
            return;
        }

        float fwd = p.input.forwardImpulse;
        double pitch = Math.toRadians(p.getXRot());   // xRot positivo = mirando ABAJO
        double sinUp = -Math.sin(pitch);              // mirar arriba -> componente +Y
        double cos = Math.max(MIN_COS, Math.cos(pitch));

        Vec3 d = p.getDeltaMovement();
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);

        // Vuelo casi nivelado: la medida en vivo es de fiar, así que se adopta como crucero.
        // Fuera de ahí (pitch pronunciado) NO se deja bajar por el propio recorte de esta
        // función — solo se deja SUBIR, para no capar un boost/turbo que acaba de activarse.
        if (cos > LEVEL_COS || horiz > refSpeed) {
            refSpeed = horiz;
        } else {
            // Arranque en frío / boost recién activado (ver doc de COLD_START_CRUISE_MULT):
            // reevaluar CADA tick, no solo cuando refSpeed sigue en 0, para que un valor de
            // vuelo que llega tarde por red (fastFlightMode ya activo al despegar, turbo que
            // se enciende a mitad de la subida) también empuje refSpeed hacia arriba. Va ANTES
            // del return de abajo a propósito: si no, el propio return se comía el arranque en
            // frío en el primer tick tras despegar (horiz≈0 Y refSpeed≈0 a la vez), que es
            // justo el caso que existe para cubrir. Solo se deja SUBIR (coldTarget > refSpeed):
            // nunca capa una medida real ya buena con una estimación menor.
            double coldTarget = p.getAbilities().getFlyingSpeed() * COLD_START_CRUISE_MULT;
            if (coldTarget > refSpeed) refSpeed = coldTarget;
        }

        if (horiz < 1.0e-3 && refSpeed < 1.0e-3) return; // nada real que redirigir todavía

        // El módulo se reparte entre horizontal y vertical en vez de sumarse: mirar arriba
        // no debe ir más rápido que mirar al frente. Se reparte desde refSpeed (estable) y
        // no desde horiz (que esta misma función ya redujo en el tick anterior).
        double targetY = refSpeed * sinUp * Math.signum(fwd);
        targetY *= (targetY >= 0 ? ASCEND_GAIN : DESCEND_GAIN);
        double newY = Mth.lerp(VERTICAL_LERP, d.y, targetY);

        p.setDeltaMovement(d.x * cos, newY, d.z * cos);
    }

    /** Frenazo: acerca X/Z a cero con un lerp agresivo y corta en seco por debajo del umbral de
     *  arrastre. No toca Y: space/shift (o la propia gravedad, si vanilla la aplicara) siguen
     *  mandando en vertical exactamente igual que sin esto. */
    private static void brake(LocalPlayer p) {
        refSpeed = 0.0;
        Vec3 d = p.getDeltaMovement();
        if (d.x * d.x + d.z * d.z < BRAKE_STOP_SPEED_SQ) {
            if (d.x != 0.0 || d.z != 0.0) p.setDeltaMovement(0.0, d.y, 0.0);
            return;
        }
        p.setDeltaMovement(Mth.lerp(BRAKE_LERP, d.x, 0.0), d.y, Mth.lerp(BRAKE_LERP, d.z, 0.0));
    }
}