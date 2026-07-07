package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public final class ZenkaiPalAnimations {
    private ZenkaiPalAnimations() {}

    public static final ResourceLocation TRANSFORMATION_1 =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.transformation1");
    public static final ResourceLocation TRANSFORMATION_2 =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.transformation2");

    /** Direcciones de vuelo. Cada una tiene 3 animaciones del PAL (créalas en player_animations/):
     *   cruise     = "fly.<dir>"              (crucero, sin Control)
     *   boostStart = "fly.<dir>_boost_start"  (INTERMEDIA: transición al pulsar Control, se ve una vez)
     *   boost      = "fly.<dir>_boost"         (boost a tope, loop)
     *  Las que no hagas, reapunta el campo a otra (p.ej. boostStart = cruise). */
    public enum FlyDir {
        IDLE("fly.idle"),
        FORWARD("fly.forward"),   BACK("fly.back"),
        LEFT("fly.left"),         RIGHT("fly.right"),
        UP("fly.up"),             DOWN("fly.down"),
        FORWARD_LEFT("fly.forward_left"),  FORWARD_RIGHT("fly.forward_right"),
        BACK_LEFT("fly.back_left"),        BACK_RIGHT("fly.back_right");

        public final ResourceLocation cruise;
        public final ResourceLocation cruiseStart;
        public final ResourceLocation boostStart;
        public final ResourceLocation boost;
        FlyDir(String base) {
            this.cruiseStart = rl(base + "_start");
            this.cruise = rl(base);
            this.boostStart = rl(base + "_boost_start");
            this.boost      = rl(base + "_boost");
        }
        private static ResourceLocation rl(String path) {
            return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
        }
    }

    public static PlayerAnimationController controller(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.TRANSFORM_LAYER);
    }

    public static PlayerAnimationController flyController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.FLY_LAYER);
    }

    public static void playFly(AbstractClientPlayer player, ResourceLocation anim) {
        flyController(player).triggerAnimation(anim);
    }

    public static void stopFly(AbstractClientPlayer player) {
        flyController(player).stopTriggeredAnimation();
    }

    public static void playTransformStart(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_1);
    }

    public static void playTransformLoop(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_2);
    }
}