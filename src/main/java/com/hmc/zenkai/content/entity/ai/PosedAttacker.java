package com.hmc.zenkai.content.entity.ai;

/**
 * Implementado por zenkaimobs cuyo {@link KiAttackGoal} necesita avisar a su modelo GeckoLib de
 * una pose SOSTENIDA que no es una animación de verdad — solo la carga de ki (un brazo fijo
 * extendido, "es como mejor así", pedido explícito, no una limitación técnica). Melee, las 4
 * técnicas físicas y el bloqueo YA NO usan este canal: son clips reales de
 * zenkai_animations.animation.json reproducidos por el motor nativo de GeckoLib
 * (triggerAnim/AnimationController, ver ZenkaiDefaultMob.registerControllers/PhysicalAttackGoal),
 * ver {@link com.hmc.zenkai.client.render_and_model_entities.entity.PosedHumanoidGeoModel
 * #setCustomAnimations} para el único caso que queda.
 */
public interface PosedAttacker {
    int POSE_NONE = 0;
    int POSE_KI_LEFT = 1;
    int POSE_KI_RIGHT = 2;

    void setAttackPose(int pose);

    /** Usado por BlockHabitGoal para no empezar a bloquear a mitad del propio wind-up de un
     *  ataque (se vería raro con dos poses encima). */
    int getAttackPose();
}
