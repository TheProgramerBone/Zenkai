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

    /** El portador lleva un objeto de #zenkai:covers_ear (scouter) en la cabeza o en Curios. Lo fija RaceSkinGeoArmorLayer. */
    static boolean wearerHasEarGear = false;

    /**
     * Opciones de cuerpo EFECTIVAS (categoría -> nombre, ya resueltas con el catálogo) del jugador que se
     * renderiza AHORA. Lo fija RaceSkinGeoArmorLayer junto a wearerHasRealHeadwear y lo lee
     * GeoLayerArmorRenderer.preRender para ocultar los huesos de las opciones no elegidas.
     */
    static java.util.Map<String, String> wearerBodyOptions = java.util.Map.of();

    @Override
    public void setCustomAnimations(GeoLayerArmorItem animatable, long instanceId, AnimationState<GeoLayerArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        // Cola en la cintura: pose por render, no por controlador (ver TailWaistPose). Se llama
        // también con peso 0: así devuelve tail_base a su sitio tras agacharse en la cintura.
        if (animatable == com.hmc.zenkai.registry.ModItems.TAIL_LOOSE.get()) {
            TailWaistPose.apply(this, animationState.getAnimationTick());
        }
        // Pelo por mechones: balanceo, inercia y aleteo al cargar ki (HairMotion).
        if (animatable == com.hmc.zenkai.registry.ModItems.HAIR.get()) {
            com.hmc.zenkai.client.hair.HairMotion.apply(this);
        }
        // Un hueso puede estar en hideOnHelmet Y en hideOnEarGear (la oreja del Namekiano: se oculta con un casco real o con el
        // scouter). Cada bucle por separado se pisaba: el de casco (false con un scouter, que es keeps_hair) corría después del
        // de la oreja y la devolvía a visible (pendiente #14, medido 2026-11-02). La decisión es una sola, por hueso, y en la
        // misma forma que GeoLayerArmorRenderer.preRender: se oculta si CUALQUIERA de las dos causas aplica.
        boolean hideHelmet = wearerHasRealHeadwear;
        boolean hideEar = wearerHasEarGear || wearerHasRealHeadwear;
        for (String name : animatable.getHideOnHelmetBones()) {
            boolean hide = hideHelmet || (hideEar && isEarBone(animatable, name));
            getBone(name).ifPresent(b -> { b.setHidden(hide); b.setChildrenHidden(hide); });
        }
        for (String name : animatable.getHideOnEarGearBones()) {
            getBone(name).ifPresent(b -> { b.setHidden(hideEar); b.setChildrenHidden(hideEar); });
        }
    }

    private static boolean isEarBone(GeoLayerArmorItem item, String bone) {
        for (String n : item.getHideOnEarGearBones()) if (n.equals(bone)) return true;
        return false;
    }

    /**
     * Peinados importados (biblioteca del jugador): su modelo no existe como archivo, se construye
     * al vuelo la primera vez que se pide (CustomHairModels) y se sirve como cualquier otro.
     */
    @Override
    public software.bernie.geckolib.cache.object.BakedGeoModel getBakedModel(ResourceLocation location) {
        if (com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.MODEL_NAMESPACE.equals(location.getNamespace())) {
            com.hmc.zenkai.client.hair.CustomHairModels.ensure(location);
        }
        return super.getBakedModel(location);
    }

    // GeckoLib 4.8 deja esta firma deprecada pero abstracta: hay que implementarla; el render usa la sobrecarga con renderer.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getModelResource(GeoLayerArmorItem item) {
        return item.getModelPath();
    }

    /**
     * GeoRenderer.defaultRender pide el modelo por ESTA sobrecarga (con el renderer), así que aquí
     * se puede mirar el stack que se pinta: el item de pelo genérico elige la geo del peinado
     * desde su componente (GeoLayerArmorItem.stackModel). Sin stackModel, igual que antes.
     */
    @Override
    public ResourceLocation getModelResource(GeoLayerArmorItem item,
                                             @Nullable software.bernie.geckolib.renderer.GeoRenderer<GeoLayerArmorItem> renderer) {
        if (renderer instanceof GeoLayerArmorRenderer armor) return item.resolveModel(armor.getCurrentStack());
        return item.getModelPath();
    }

    // GeckoLib 4.8 deja esta firma deprecada pero abstracta: hay que implementarla; el render usa la sobrecarga con renderer.
    @SuppressWarnings("deprecation")
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