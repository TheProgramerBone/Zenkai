package com.hmc.zenkai.feature.race.layer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class GeoLayerArmorModel extends GeoModel<GeoLayerArmorItem> {

    /**
     * ¿Lleva el jugador que se está renderizando AHORA MISMO un objeto REAL (no la pieza racial
     * virtual) en la ranura HEAD? RaceSkinGeoArmorLayer lo fija justo antes de invocar el render,
     * capturando el HEAD real ANTES de hacer el swap temporal a la pieza racial — para cuando este
     * modelo se pinta, player.getItemBySlot(HEAD) ya devolvería la pieza racial, no el casco real.
     * Cliente, un solo hilo de render -> un campo estático simple basta (mismo patrón que
     * GeoLayerArmorItem.frozenTick/animFrozen).
     */
    static boolean wearerHasRealHeadwear = false;

    @Override
    public void setCustomAnimations(GeoLayerArmorItem animatable, long instanceId, AnimationState<GeoLayerArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        String[] bones = animatable.getHideOnHelmetBones();
        if (bones.length == 0) return;
        for (String name : bones) {
            getBone(name).ifPresent(b -> {
                b.setHidden(wearerHasRealHeadwear);
                b.setChildrenHidden(wearerHasRealHeadwear);
            });
        }
    }

    @Override
    public ResourceLocation getModelResource(GeoLayerArmorItem item) {
        return item.getModelPath();
    }

    @Override
    public ResourceLocation getTextureResource(GeoLayerArmorItem item) {
        return item.getTexturePath();
    }

    @Override
    public ResourceLocation getAnimationResource(GeoLayerArmorItem item) {
        return item.getAnimationPath();
    }

    @Override
    public @Nullable RenderType getRenderType(GeoLayerArmorItem animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}