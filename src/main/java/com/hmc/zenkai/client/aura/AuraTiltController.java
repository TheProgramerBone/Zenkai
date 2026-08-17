package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Inclinación del aura en vuelo.
 * El eje del aura se inclina siguiendo la MIRADA y el giro del cuerpo, no una dirección
 * discreta. Antes copiaba la pose de las 19 animaciones de vuelo de PAL; con el rediseño solo
 * quedan crucero y boost, así que la orientación se deriva igual que la del cuerpo: del
 * jugador, no de qué animación esté sonando. Suavizado exponencial; al frenar o aterrizar
 * vuelve gradualmente a la vertical.
 * Es ortogonal al rework del aura: no lee AuraProfile ni AuraState.
 */
public final class AuraTiltController {
    private AuraTiltController() {}

    private static final float TILT_TAU_SECONDS = 0.10f;

    private static final Map<Integer, Vector3f> TILT_AXIS = new HashMap<>();
    private static final Map<Integer, Long> TILT_LAST_NANOS = new HashMap<>();
    private static final Map<Integer, Vec3> TILT_LAST_POS = new HashMap<>();

    public static void clear(int playerId) {
        TILT_AXIS.remove(playerId);
        TILT_LAST_NANOS.remove(playerId);
        TILT_LAST_POS.remove(playerId);
    }

    /** Muestra de movimiento por frame: velocidad real + alpha de suavizado. */
    public record Motion(Vec3 vel, float alpha, boolean flying) {}

    /**
     * Velocidad REAL por delta de posición: getDeltaMovement no refleja todos los modos
     * de vuelo (por ejemplo descender con ctrl+shift+WASD). En bloques/tick.
     */
    public static Motion sample(AbstractClientPlayer p, Vec3 at) {
        long now = System.nanoTime();
        Long last = TILT_LAST_NANOS.put(p.getId(), now);
        float dt = (last == null) ? 1f : Math.min(0.25f, (now - last) / 1.0e9f);
        float alpha = 1f - (float) Math.exp(-dt / TILT_TAU_SECONDS);

        Vec3 prev = TILT_LAST_POS.put(p.getId(), at);
        Vec3 vel = (prev == null || dt <= 1.0e-4f) ? Vec3.ZERO
                : at.subtract(prev).scale(1.0 / (dt * 20.0));

        boolean flying = PlayerStatsAttachment.get(p).isFlyEnabled() && !p.onGround();
        return new Motion(vel, alpha, flying);
    }

    /** Por debajo de esta velocidad (bloques/tick) no se inclina nada: en hover el aura
     *  tiene que subir recta. */
    private static final double TILT_MIN_SPEED = 0.08;
    /** Velocidad a la que la inclinación es del 100%. Entre el mínimo y esta, escala. */
    private static final double TILT_FULL_SPEED = 0.45;

    /**
     * Ángulos del aura tomados del FlightController: la MISMA orientación que se aplica al
     * cuerpo, no un cálculo paralelo.
     * Antes esto tenía su propia tabla (15° fijos en crucero, 90+xRot/2 en boost) y, desde que
     * el cuerpo se inclina de forma continua, ambos decían cosas distintas: avanzando despacio
     * el aura ya se tumbaba 15° mientras el cuerpo apenas se movía.
     * Además escala con la velocidad real: quieto en el aire no hay inclinación que valga.
     * pitchDeg: grados desde la vertical hacia el FRENTE del cuerpo.
     * sideDeg: grados hacia la DERECHA del cuerpo.
     */
    private static float[] anglesFor(AbstractClientPlayer p, Motion mo) {
        var o = com.hmc.zenkai.client.fly.FlightController.of(p.getUUID());

        double speed = mo.vel().length();
        float scale = (float) Mth.clamp(
                (speed - TILT_MIN_SPEED) / (TILT_FULL_SPEED - TILT_MIN_SPEED), 0.0, 1.0);
        if (scale <= 0f) return new float[]{0f, 0f};

        float pitch = (float) Math.toDegrees(o.pitch()) * scale;
        float side = (float) Math.toDegrees(o.roll()) * scale;
        return new float[]{pitch, side};
    }

    /** Aplica la inclinación al PoseStack, ya trasladado al jugador. */
    public static void apply(PoseStack pose, AbstractClientPlayer p, Motion mo) {
        Vector3f target = new Vector3f(0f, 1f, 0f);
        var fp = ClientZenkaiPalTick.flyPoseOf(p.getUUID());
        if (mo.flying() && fp.flying()) {
            float[] ang = anglesFor(p, mo);
            float pitch = (float) Math.toRadians(ang[0]);
            float side = (float) Math.toRadians(ang[1]);
            if (Math.abs(pitch) > 1.0e-3f || Math.abs(side) > 1.0e-3f) {
                float yaw = (float) Math.toRadians(Mth.lerp(1f, p.yBodyRotO, p.yBodyRot));
                float sinY = (float) Math.sin(yaw), cosY = (float) Math.cos(yaw);
                Vector3f fwd = new Vector3f(-sinY, 0f, cosY);
                Vector3f rgt = new Vector3f(cosY, 0f, sinY);
                target.set(
                        fwd.x * (float) Math.sin(pitch) + rgt.x * (float) Math.sin(side),
                        (float) (Math.cos(pitch) * Math.cos(side)),
                        fwd.z * (float) Math.sin(pitch) + rgt.z * (float) Math.sin(side));
                if (target.lengthSquared() < 1.0e-4f) target.set(0f, 1f, 0f);
                target.normalize();
            }
        }

        Vector3f axis = TILT_AXIS.computeIfAbsent(p.getId(), k -> new Vector3f(0f, 1f, 0f));
        axis.lerp(target, mo.alpha());
        if (axis.lengthSquared() < 1.0e-4f) axis.set(0f, 1f, 0f);
        axis.normalize();

        if (axis.y > 0.9999f) return;
        // La rotación va alrededor del PIVOTE DEL HUESO `body` (12 px = 0.75 bloques), no de
        // los pies. El PoseStack llega trasladado a `at`, que es el suelo del jugador; rotar
        // ahí desplaza el aura respecto al torso casi un bloque entero cuando el cuerpo se
        // tumba 90° en boost, y el cono sale disparado por debajo y por detrás.
        //
        // Mismo pivote que usa AuraTrailRenderer.bodyCenter: los tres —cuerpo, estela y
        // aura— tienen que girar sobre el mismo punto o no hay forma de que casen.
        pose.translate(0.0, BODY_PIVOT_Y, 0.0);
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, axis.x, axis.y, axis.z));
        pose.translate(0.0, -BODY_PIVOT_Y, 0.0);
    }

    /** Pivote del hueso `body` en PAL: 12 px = 0.75 bloques. */
    private static final double BODY_PIVOT_Y = 0.75;
}