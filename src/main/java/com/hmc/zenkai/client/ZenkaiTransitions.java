package com.hmc.zenkai.client;

import com.hmc.zenkai.client.debug.ZenkaiAnimDebug;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.FadeType;
import net.minecraft.resources.ResourceLocation;

/**
 * AUTORIDAD ÚNICA de las transiciones entre animaciones. Ninguna capa decide su propia
 * duración de fundido: todas piden aquí.
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
     * existe (PlayerAnimResources.hasAnimation): modelar por partes sigue siendo seguro.
     */
    public static boolean play(PlayerAnimationController c, ResourceLocation anim, int ticks) {
        if (c == null || anim == null) {
            ZenkaiAnimDebug.logPlay(c, anim, ticks, null);
            return false;
        }
        ZenkaiAnimDebug.beforePlay(c, anim);
        boolean ok = c.replaceAnimationWithFade(fadeIn(ticks), anim);
        ZenkaiAnimDebug.logPlay(c, anim, ticks, ok);
        return ok;
    }

    /**
     * Sale a nada. CORTE SECO, sin fundido, y es a propósito.
     * POR QUÉ NO SE FUNDE LA SALIDA
     * ----------------------------
     * replaceAnimationWithFade(fade, (Animation) null, false) NO es un uso soportado: el
     * AbstractFadeModifier se queda enganchado en controller.modifiers esperando una animación
     * destino que nunca llega, y ahí se queda congelado al final de su curva — o sea,
     * multiplicando por peso 0 lo que se reproduzca después.
     * Medido en juego (log del 19:24, capas COMBAT @45faa352, BLOCK @357a40a0, KI @148dc990):
     * los fade-IN se auto-retiran al completarse; los fade-OUT hacia null NO, y se acumula uno
     * por cada stop(). El efecto era que cada capa reproducía correctamente UNA vez y a partir
     * de ahí play() seguía devolviendo true y avanzando el tick por dentro (se vio
     * currentAnimation vivo con tick=11 y estado RUNNING) sin dibujar absolutamente nada.
     * Las técnicas físicas eran lo único que se salvaba porque nunca llaman aquí.
     * Si algún día se quiere recuperar la salida suave, NO se vuelve al fundido hacia null:
     * se funde hacia un clip neutro con fadeIn(), que es la ruta que sí se limpia sola.
     * ⚠ stopTriggeredAnimation(): si el fork no lo expone en PlayerAnimationController, pásame
     *   los métodos públicos de parada de AnimationController y lo sustituyo.
     */
    public static void stop(PlayerAnimationController c, int ticks) {
        if (c == null) {
            ZenkaiAnimDebug.beforeStop(null, ticks);
            return;
        }
        ZenkaiAnimDebug.beforeStop(c, ticks);
        c.stopTriggeredAnimation();
        ZenkaiAnimDebug.afterStop(c);
    }
}