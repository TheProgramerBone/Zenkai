package com.hmc.zenkai.client.fly;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Controlador de la capa de vuelo: aplica la orientación de FlightController ENCIMA de la
 * animación.
 * POR QUÉ AQUÍ Y NO CON AdjustmentModifier
 * ----------------------------------------
 * AdjustmentModifier está pensado para envolver otra IAnimation dentro de un ModifierLayer
 * ("make sure this modifier is the first one on the list"): suelto como capa del stack, su
 * super.get3DTransform() delegaría en un anim nulo.
 * get3DTransformRaw es el punto de extensión que usa la propia librería para lo mismo — es
 * donde PlayerAnimationController aplica su torso bend a los huesos superiores. Sumar aquí
 * es exactamente el patrón de la casa.
 * Reparto: la animación pone la POSTURA (brazos, piernas, capa), esto pone la ORIENTACIÓN
 * (cabeceo, alabeo). Ninguna de las dos invade a la otra.
 */
public class FlyAnimationController extends PlayerAnimationController {

    /**
     * Aplicar la inclinación también en la pasada de 1ª persona.
         * En true el vuelo se siente elytra de verdad desde dentro. Si al volar en boost el torso
     * te entra en cámara, ponlo en false: se pierde el efecto en 1ª persona pero el cuerpo
     * sigue inclinándose para los demás.
     */
    private static final boolean APPLY_IN_FIRST_PERSON = false;

    public FlyAnimationController(AbstractClientPlayer player, AnimationStateHandler handler) {
        super(player, handler);
    }

    @Override
    public PlayerAnimBone get3DTransformRaw(@NotNull PlayerAnimBone bone) {
        bone = super.get3DTransformRaw(bone);

        FlightController.Orientation o = FlightController.of(getPlayer().getUUID());
        if (Math.abs(o.pitch()) < 1.0e-4f && Math.abs(o.roll()) < 1.0e-4f) return bone;

        if (!APPLY_IN_FIRST_PERSON
                && com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.isFirstPersonPass()) {
            return bone;
        }

        switch (bone.getName()) {
            // "body" es la raíz: rotarlo inclina al jugador entero, que es el efecto elytra.
            case "body" -> {
                bone.rotX += o.pitch();
                bone.rotZ += o.roll();
            }
            // La cabeza contrarresta parte del cabeceo: con el cuerpo horizontal y sin esto,
            // el jugador acabaría mirándose los pies.
            case "head" -> bone.rotX -= o.pitch() * FlightController.HEAD_COUNTER;
            default -> { }
        }
        return bone;
    }
}