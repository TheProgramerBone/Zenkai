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

    private static final ResourceLocation NAMEK_MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/namek_all_dragon_balls.geo.json");
    private static final ResourceLocation NAMEK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/namek_all_dragon_balls_texture.png");
    private static final ResourceLocation NAMEK_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/namek_all_dragon_balls.animation.json");

    /** ¿Es el bloque de Namek? Tiene su propio modelo (PLACEHOLDER, lo sustituirá el usuario). */
    private static boolean isNamek(AllDragonBallsEntity be) {
        return be.getBlockState().getBlock() instanceof com.hmc.zenkai.content.blockentity.AllDragonBalls.NamekAllDragonBallsBlock;
    }

    // GeckoLib 4.8 deja esta firma deprecada pero abstracta: hay que implementarla; el render usa la sobrecarga con renderer.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getModelResource(AllDragonBallsEntity animatable) {
        return isNamek(animatable) ? NAMEK_MODEL : MODEL;
    }

    // GeckoLib 4.8 deja esta firma deprecada pero abstracta: hay que implementarla; el render usa la sobrecarga con renderer.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureResource(AllDragonBallsEntity animatable) {
        return isNamek(animatable) ? NAMEK_TEXTURE : TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AllDragonBallsEntity animatable) {
        return isNamek(animatable) ? NAMEK_ANIMATION : ANIMATION;
    }

    @Override
    public RenderType getRenderType(AllDragonBallsEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
