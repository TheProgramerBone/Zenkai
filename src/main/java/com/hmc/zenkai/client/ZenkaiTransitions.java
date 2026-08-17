package com.hmc.zenkai.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.FadeType;
import net.minecraft.resources.ResourceLocation;

/**
 * AUTORIDAD ÚNICA de las transiciones entre animaciones. Ninguna capa decide su propia
 * duración de fundido: todas piden aquí.
 * Antes salía por triggerAnimation() + stopTriggeredAnimation(), que son cortes secos.
 * Se notaba sobre al soltar el bloqueo (frame instantáneo de idle) y lo va a notar
 * mucho más el vuelo dinámico, donde FLY → COMBAT y COMBAT → FLY ocurren constantemente.
 * CRITERIO de duraciones: lo reactivo va corto y lo sostenido va largo. Un golpe con 6 ticks
 * de fundido se siente lento aunque el daño salga en el tick 0; una pose de combate con 2 se
 * ve como un tirón. Nada supera 6 ticks (0.3 s).
 * NOTA: AbstractFadeModifier tiene estado interno (length, time, tickDelta). No se puede
 * cachear ni compartir entre llamadas — de ahí que cada method construya uno nuevo.
 */
public final class ZenkaiTransitions {

    private ZenkaiTransitions() {}

    // ── Duraciones (ticks). Un solo sitio para calibrar el tacto del combate. ──
    public static final int PHYS       = 2; // golpes: casi instantáneo o se siente pesado
    public static final int KI_RELEASE = 2; // el disparo tiene que salir seco
    public static final int BLOCK_IN   = 3; // defender es reactivo
    public static final int BLOCK_OUT  = 4;
    public static final int KI_CHARGE  = 4;
    public static final int TRANSFORM  = 4;
    public static final int COMBAT     = 5; // poses sostenidas
    public static final int FLY        = 6; // lo más largo: el vuelo es continuo

    /** Curva por defecto. EASE_IN_OUT_SINE arranca y termina suave sin sobrepasar la pose,
     *  que es lo que hacen las curvas tipo BACK/ELASTIC y aquí quedaría raro. */
    private static final EasingType EASE = EasingType.EASE_IN_OUT_SINE;

    private static AbstractFadeModifier fadeIn(int ticks) {
        return AbstractFadeModifier.standardFadeIn(Math.max(1, ticks), EASE);
    }

    private static AbstractFadeModifier fadeOut(int ticks) {
        return AbstractFadeModifier.standardFade(Math.max(1, ticks), EASE, null, FadeType.FADE_OUT);
    }

    /**
     * Entra a una animación fundiendo desde lo que hubiera. Devuelve false si el asset no
     * existe (PlayerAnimResources.hasAnimation), igual que triggerAnimation: modelar por
     * partes sigue siendo seguro.
     */
    public static boolean play(PlayerAnimationController c, ResourceLocation anim, int ticks) {
        if (c == null || anim == null) return false;
        return c.replaceAnimationWithFade(fadeIn(ticks), anim);
    }

    /**
     * Sale a nada, fundiendo.
         * Va al overload de AnimationController que recibe Animation, no al de ResourceLocation:
     * ese último pasa por hasAnimation(null), que devuelve false y no haría nada. El cast es
     * obligatorio porque `null` a secas es ambiguo entre las sobrecargas de Animation y
     * RawAnimation.
         * fadeFromNothing = false: estamos fundiendo HACIA nada, no desde nada.
         * ⚠ Si el overload no acepta null y salta NPE en tiempo de ejecución, sustituye el cuerpo
     * por `c.stopTriggeredAnimation();`. Se pierde solo el fundido de salida; las entradas,
     * que son la mayor parte del efecto, siguen funcionando.
     */
    public static void stop(PlayerAnimationController c, int ticks) {
        if (c == null) return;
        c.replaceAnimationWithFade(fadeOut(ticks), (Animation) null, false);
    }
}