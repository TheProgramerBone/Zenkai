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

    private static final ResourceLocation BLOCK_ANIM =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.block");

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

    public static PlayerAnimationController blockController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.BLOCK_LAYER);
    }

    public static void playBlock(AbstractClientPlayer player) {
        blockController(player).triggerAnimation(BLOCK_ANIM);
    }

    public static void stopBlock(AbstractClientPlayer player) {
        blockController(player).stopTriggeredAnimation();
    }

    // ── Pose ofensiva del modo combate ───────────────────────────────────────
    /** Por estilo (orden = Style.ordinal()):
     *   start = "zenkai.combat_idle_<estilo>_start" (transición, se ve una vez)
     *   loop  = "zenkai.combat_idle_<estilo>"        (pose sostenida, loop) */
    private static final String[] COMBAT_STYLES = {"warrior", "martial_artist", "spiritualist"};
    private static final ResourceLocation[] COMBAT_IDLE_START = new ResourceLocation[COMBAT_STYLES.length];
    private static final ResourceLocation[] COMBAT_IDLE_LOOP  = new ResourceLocation[COMBAT_STYLES.length];
    static {
        for (int i = 0; i < COMBAT_STYLES.length; i++) {
            COMBAT_IDLE_START[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "zenkai.combat_idle_" + COMBAT_STYLES[i] + "_start");
            COMBAT_IDLE_LOOP[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "zenkai.combat_idle_" + COMBAT_STYLES[i]);
        }
    }

    public static PlayerAnimationController combatController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.COMBAT_LAYER);
    }

    public static void playCombatIdleStart(AbstractClientPlayer player, int styleOrdinal) {
        if (styleOrdinal < 0 || styleOrdinal >= COMBAT_IDLE_START.length) return;
        combatController(player).triggerAnimation(COMBAT_IDLE_START[styleOrdinal]);
    }

    public static void playCombatIdleLoop(AbstractClientPlayer player, int styleOrdinal) {
        if (styleOrdinal < 0 || styleOrdinal >= COMBAT_IDLE_LOOP.length) return;
        combatController(player).triggerAnimation(COMBAT_IDLE_LOOP[styleOrdinal]);
    }

    public static void stopCombatIdle(AbstractClientPlayer player) {
        combatController(player).stopTriggeredAnimation();
    }
}