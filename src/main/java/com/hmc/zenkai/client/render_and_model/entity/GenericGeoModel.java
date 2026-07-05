package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib genérico por convención, con escapes para los casos no triviales.
 *
 * Extiende {@link DefaultedEntityGeoModel} para conservar el head-turn (la cabeza
 * sigue al jugador) cuando turnsHead = true, igual que Shenlong/Namekian.
 *
 * Resuelve las rutas como:
 *   geo/&lt;modelName&gt;.geo.json
 *   textures/entity/&lt;textureName&gt;.png
 *   animations/&lt;animName&gt;.animation.json
 *
 * Constructores:
 *   new GenericGeoModel&lt;&gt;("kintoun")                      // mismo nombre para los 3, sin head-turn, opaco
 *   new GenericGeoModel&lt;&gt;("shenlong", true)               // head-turn, opaco
 *   new GenericGeoModel&lt;&gt;("kintoun","kintoun_shadow","kintoun", false, false) // textura distinta
 *   new GenericGeoModel&lt;&gt;("namekian","namekian","namekian_default", true, true) // anim compartida + translúcido
 */
public class GenericGeoModel<T extends Entity & GeoAnimatable> extends DefaultedEntityGeoModel<T> {

    private final ResourceLocation modelRes;
    private final ResourceLocation textureRes;
    private final ResourceLocation animationRes;
    private final boolean translucent;

    public GenericGeoModel(String name) {
        this(name, name, name, false, false);
    }

    public GenericGeoModel(String name, boolean turnsHead) {
        this(name, name, name, turnsHead, false);
    }

    public GenericGeoModel(String name, boolean turnsHead, boolean translucent) {
        this(name, name, name, turnsHead, translucent);
    }

    public GenericGeoModel(String modelName, String textureName, String animName,
                           boolean turnsHead, boolean translucent) {
        super(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, modelName), turnsHead);
        this.modelRes     = id("geo/" + modelName + ".geo.json");
        this.textureRes   = id("textures/entity/" + textureName + ".png");
        this.animationRes = id("animations/" + animName + ".animation.json");
        this.translucent  = translucent;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return modelRes;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return textureRes;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animationRes;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        // Opaco => comportamiento por defecto de GeckoLib (entityCutoutNoCull: recorta el alfa,
        // los huecos se ven y escribe profundidad). Translúcido => entityTranslucentCull.
        return translucent
                ? RenderType.entityTranslucentCull(texture)
                : super.getRenderType(animatable, texture);
    }
}