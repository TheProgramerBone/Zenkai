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

    public static PlayerAnimationController controller(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.TRANSFORM_LAYER);
    }

    public static void playTransformStart(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_1);
    }

    public static void playTransformLoop(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_2);
    }
}