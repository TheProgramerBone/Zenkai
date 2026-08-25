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
        // Sin vuelo, sin avance o con control vertical manual (space/shift) no hay referencia
        // de crucero que conservar: la próxima vez que se enganche el ascenso por mirada debe
        // partir de una medición fresca, no de un crucero de hace rato.
        if (!flying || Math.abs(p.input.forwardImpulse) < INPUT_DEADZONE
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
        if (horiz < 1.0e-3 && refSpeed < 1.0e-3) return;

        // Vuelo casi nivelado: la medida en vivo es de fiar, así que se adopta como crucero.
        // Fuera de ahí (pitch pronunciado) NO se deja bajar por el propio recorte de este
        // función — solo se deja SUBIR, para no capar un boost/turbo que acaba de activarse.
        if (cos > LEVEL_COS || horiz > refSpeed) refSpeed = horiz;

        // El módulo se reparte entre horizontal y vertical en vez de sumarse: mirar arriba
        // no debe ir más rápido que mirar al frente. Se reparte desde refSpeed (estable) y
        // no desde horiz (que esta misma función ya redujo en el tick anterior).
        double targetY = refSpeed * sinUp * Math.signum(fwd);
        targetY *= (targetY >= 0 ? ASCEND_GAIN : DESCEND_GAIN);
        double newY = Mth.lerp(VERTICAL_LERP, d.y, targetY);

        p.setDeltaMovement(d.x * cos, newY, d.z * cos);
    }
}