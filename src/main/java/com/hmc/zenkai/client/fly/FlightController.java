package com.hmc.zenkai.client.fly;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orientación dinámica del vuelo. La animación aporta la POSTURA (cuatro clips); esto aporta
 * el MOVIMIENTO: cabeceo, alabeo y su suavizado.
 * Sustituye a las 19 animaciones direccionales. La diferencia de fondo es que antes cada
 * combinación de teclas necesitaba su clip y el salto entre clips era visible; ahora hay un
 * único objetivo continuo al que el cuerpo se acerca de forma exponencial, así que acelerar,
 * frenar y girar salen gratis y sin escalones.
 * Estilo elytra: el cuerpo sigue la mirada. En crucero apenas se inclina; en boost se pone
 * horizontal y apunta a donde miras.
 * SOLO CLIENTE y por jugador: funciona igual para el local y para los remotos, porque solo
 * necesita mirada y giro del cuerpo, que ambos tienen sincronizados.
 */
public final class FlightController {

    private FlightController() {}

    // ── Calibración. Lo ajustable del vuelo vive aquí. ──────────────────

    /** Cabeceo del cuerpo en crucero, como fracción de la mirada. Bajo: en crucero se vuela
     *  casi erguido y solo se insinúa hacia dónde miras. */
    private static final float CRUISE_PITCH_FOLLOW = 0.25f;

    /** Grados de inclinación base en boost. 90 = completamente horizontal (superman). */
    private static final float BOOST_BASE_PITCH_DEG = 90f;

    /** Cuánto sigue el cuerpo a la mirada en boost. 1.0 = la sigue entera. */
    private static final float BOOST_PITCH_FOLLOW = 0.9f;

    /** Alabeo por giro: grados de banqueo por grado de rotación del cuerpo y tick. */
    private static final float BANK_PER_DEG = 2.0f;
    private static final float MAX_BANK_DEG = 25f;

    /** Suavizado exponencial por tick. Más alto = más reactivo, más brusco.
     *  El cabeceo va más lento que el alabeo a propósito: cambiar de rumbo debe sentirse
     *  con peso, mientras que el banqueo tiene que acompañar el giro casi al instante. */
    private static final float PITCH_LERP = 0.18f;
    private static final float BANK_LERP  = 0.30f;

    /** La cabeza contrarresta parte del cabeceo del cuerpo para no acabar mirando al suelo
     *  con el cuerpo horizontal. 0 = la cabeza va pegada al cuerpo; 1 = queda nivelada. */
    static final float HEAD_COUNTER = 0.55f;

    // ── Estado ───────────────────────────────────────────────────────────────

    /** Cabeceo y alabeo actuales, en RADIANES, ya suavizados. */
    public record Orientation(float pitch, float roll) {
        static final Orientation ZERO = new Orientation(0f, 0f);
        boolean isZero() {
            return Math.abs(pitch) < 1.0e-4f && Math.abs(roll) < 1.0e-4f;
        }
    }

    private static final Map<UUID, Orientation> STATE = new ConcurrentHashMap<>();

    public static Orientation of(UUID id) {
        return STATE.getOrDefault(id, Orientation.ZERO);
    }

    /**
     * Un paso de simulación. Llamar UNA vez por tick y por jugador, esté volando o no: cuando
     * deja de volar el objetivo pasa a cero y el cuerpo se endereza solo, sin necesidad de
     * un estado de "aterrizando" aparte.
     */
    public static void tick(AbstractClientPlayer p, boolean flying, boolean boosting) {
        Orientation cur = of(p.getUUID());

        float targetPitch = 0f;
        float targetRoll  = 0f;

        if (flying) {
            float lookDeg = p.getXRot(); // positivo = mirando abajo
            float pitchDeg = boosting
                    ? BOOST_BASE_PITCH_DEG + lookDeg * BOOST_PITCH_FOLLOW
                    : lookDeg * CRUISE_PITCH_FOLLOW;
            targetPitch = (float) Math.toRadians(pitchDeg);

            float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
            float bankDeg = Mth.clamp(turn * BANK_PER_DEG, -MAX_BANK_DEG, MAX_BANK_DEG);
            targetRoll = (float) Math.toRadians(bankDeg);
        }

        float pitch = Mth.lerp(PITCH_LERP, cur.pitch(), targetPitch);
        float roll  = Mth.lerp(BANK_LERP,  cur.roll(),  targetRoll);

        Orientation next = new Orientation(pitch, roll);
        if (!flying && next.isZero()) STATE.remove(p.getUUID());
        else STATE.put(p.getUUID(), next);
    }

    public static void prune(Level level) {
        STATE.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    public static void clear() { STATE.clear(); }
}