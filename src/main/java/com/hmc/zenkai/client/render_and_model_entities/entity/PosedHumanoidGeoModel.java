package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.ai.PosedAttacker;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;

/**
 * {@link GenericGeoModel} + soporte de {@link PosedAttacker} para NPCs de combate GeckoLib con
 * rig humanoide (bone names/proporciones idénticos a un jugador vainilla, p. ej.
 * geo/shadow_clone.geo.json — copia exacta de saibaman.geo.json sin el 0.75 de escala). Pensado
 * para ser la base de CUALQUIER futuro zenkaimob humanoide de combate, no solo
 * {@code ShadowCloneEntity} — pedido explícito del usuario: "todos los NPCs que se deriven de
 * este sistema serán GeckoLib humanoides".
 *
 * Melee, las 4 técnicas físicas y el bloqueo YA NO se resuelven aquí — son clips REALES de
 * {@code zenkai_animations.animation.json} (attack.strike, zenkai.phys_dash_punch/heavy_blow/
 * barrage/kiai, zenkai.block — este último ya vivía ahí sin usarse) reproducidos por el motor
 * NATIVO de GeckoLib
 * (triggerAnim/AnimationController + un controlador predicado para el bloqueo, ver
 * {@code ZenkaiDefaultMob.registerControllers}) — ningún sampler propio, ninguna convención de
 * rotación que adivinar. Lo único que sigue haciendo falta a mano es la pose de KI
 * (POSE_KI_LEFT/RIGHT): un brazo fijo extendido, pedido explícito ("es como mejor así"), no una
 * animación — no hay clip que reproducir, así que no hay motivo para pasar por el motor de
 * animaciones para esto.
 */
public class PosedHumanoidGeoModel<T extends Entity & GeoAnimatable & PosedAttacker> extends GenericGeoModel<T> {

    public PosedHumanoidGeoModel(String name) { super(name); }

    public PosedHumanoidGeoModel(String name, boolean turnsHead) { super(name, turnsHead); }

    public PosedHumanoidGeoModel(String name, boolean turnsHead, boolean translucent) {
        super(name, turnsHead, translucent);
    }

    public PosedHumanoidGeoModel(String modelName, String textureName, String animName,
                                  boolean turnsHead, boolean translucent) {
        super(modelName, textureName, animName, turnsHead, translucent);
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        switch (animatable.getAttackPose()) {
            case PosedAttacker.POSE_KI_LEFT  -> poseArm("left_arm", -1.55f, 0.1f, 0.05f, 1);
            case PosedAttacker.POSE_KI_RIGHT -> poseArm("right_arm", -1.55f, 0.1f, 0.05f, -1);
            default -> { /* sin pose de ki: manda la animación normal de GeckoLib (walk/strike/físicas/bloqueo) */ }
        }
    }

    /** Pose fija (no una animación) para un brazo — side multiplica y/z para que el mismo par de
     *  números sirva para el brazo espejo. Valores en RADIANES: GeoBone.setRotX/Y/Z espera lo
     *  mismo que ModelPart.xRot — confirmado en AnimationProcessor de GeckoLib. */
    private void poseArm(String bone, float xRot, float yRot, float zRot, int side) {
        getBone(bone).ifPresent(b -> {
            b.setRotX(xRot);
            b.setRotY(yRot * side);
            b.setRotZ(zRot * side);
        });
    }
}
