package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
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
 * Inclinación del aura en vuelo. TRASLADO LITERAL del código que ya funcionaba: no se
 * ha rediseñado nada, solo se ha sacado del monolito.
 *
 * El eje del aura se inclina copiando la POSE de las animaciones de vuelo de PAL, no
 * adivinando por velocidad — así el aura y el cuerpo cuentan lo mismo. Suavizado
 * exponencial; al frenar o aterrizar vuelve gradualmente a la vertical.
 *
 * Es ortogonal al rework: no lee AuraProfile ni AuraState.
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

    /**
     * Ángulos del aura por estado de animación de vuelo.
     * pitchDeg: grados desde la vertical hacia el FRENTE del cuerpo (negativo = atrás).
     * sideDeg: grados hacia la DERECHA del cuerpo (negativo = izquierda).
     */
    private static float[] anglesFor(ZenkaiPalAnimations.FlyDir dir, boolean boosting) {
        if (dir == null) return new float[]{0f, 0f};
        return switch (dir) {
            case IDLE    -> new float[]{0f, 0f};
            case FORWARD -> boosting ? new float[]{90f, 0f} : new float[]{15f, 0f};
            case BACK    -> new float[]{-15f, 0f};
            case LEFT    -> new float[]{0f, -8f};
            case RIGHT   -> new float[]{0f, 8f};
            case UP      -> boosting ? new float[]{45f, 0f} : new float[]{0f, 0f};
            case DOWN    -> boosting ? new float[]{135f, 0f} : new float[]{8f, 0f};
            case FORWARD_LEFT, BACK_RIGHT, FORWARD_RIGHT, BACK_LEFT -> null;
        };
    }

    /** Aplica la inclinación al PoseStack, ya trasladado al jugador. */
    public static void apply(PoseStack pose, AbstractClientPlayer p, Motion mo) {
        Vector3f target = new Vector3f(0f, 1f, 0f);
        var fp = ClientZenkaiPalTick.flyPoseOf(p.getUUID());
        if (mo.flying() && fp.dir() != null) {
            float[] ang = anglesFor(fp.dir(), fp.boosting());
            if (ang != null) {
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
        }

        Vector3f axis = TILT_AXIS.computeIfAbsent(p.getId(), k -> new Vector3f(0f, 1f, 0f));
        axis.lerp(target, mo.alpha());
        if (axis.lengthSquared() < 1.0e-4f) axis.set(0f, 1f, 0f);
        axis.normalize();

        if (axis.y > 0.9999f) return;
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, axis.x, axis.y, axis.z));
    }
}