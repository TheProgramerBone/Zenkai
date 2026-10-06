package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.otherworld.AngelEntity;
import com.hmc.zenkai.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Aro del ángel: pinta el halo del mod (el mismo ítem {@code ModItems.HALO} que se ve sobre los
 * jugadores en el Otro Mundo) cuando lo lleva puesto en la ranura de casco. GeckoLib no pinta
 * armadura en entidades, así que se redibuja el .geo.json del halo con su textura propia.
 *
 * El halo está modelado en el mismo marco que el rig de jugador (cubos a y=35, tres píxeles por
 * encima de la cabeza, que es lo que usa angel.geo.json), así que coincide con la cabeza del
 * ángel sin reposicionarlo. No sigue el giro de cabeza: es un aro simétrico y el ángel no gira
 * la cabeza (el modelo se registra con turnsHead=false).
 */
public class AngelHaloLayer extends GeoRenderLayer<AngelEntity> {

    private static final ResourceLocation HALO_GEO =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/halo.geo.json");
    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/models/races/halo.png");

    public AngelHaloLayer(GeoRenderer<AngelEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, AngelEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!animatable.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HALO.get())) return;
        BakedGeoModel halo = GeckoLibCache.getBakedModels().get(HALO_GEO);
        if (halo == null) return;

        RenderType rt = RenderType.entityCutoutNoCull(HALO_TEXTURE);
        getRenderer().reRender(halo, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, -1);
    }
}
