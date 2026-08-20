package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AllDragonBallsModel extends GeoModel<AllDragonBallsEntity> {
    // Cacheados: GeckoLib llama a los tres getters de abajo cada frame, y reconstruir un
    // ResourceLocation por llamada era trabajo repetido sin motivo (mismo patrón que
    // GenericGeoModel ya usa para el resto de entidades).
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/all_dragon_balls.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/all_dragon_balls_texture.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/all_dragon_balls.animation.json");

    @Override
    public ResourceLocation getModelResource(AllDragonBallsEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AllDragonBallsEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AllDragonBallsEntity animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(AllDragonBallsEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
