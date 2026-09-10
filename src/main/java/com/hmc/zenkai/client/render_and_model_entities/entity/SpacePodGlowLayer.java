package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Pantallas de cabina + toberas de la SpacePod. Emisivas SOLO mientras hay alguien pilotando
 * — pedido explícito del usuario ("solo mientras hay piloto"), mismo espíritu que
 * ScouterBenchGlowLayer (banco de scouter, mira el BlockState WORKING en vez de un pasajero):
 * un motor que arranca al subirse alguien, no unas luces que se quedan encendidas con la nave
 * vacía y aparcada.
 * Mismo mecanismo que esa clase, y por el mismo motivo — ver su javadoc para el porqué de un
 * GeoRenderLayer a mano en vez de {@code AutoGlowingGeoLayer} (esa borra de la textura base los
 * píxeles del glowmask dando por hecho que la capa se dibuja SIEMPRE; aquí se salta la mitad
 * del tiempo, así que dejaría huecos calados con la nave vacía): redibuja el modelo con el
 * GLOWMASK como textura (transparente salvo en los píxeles que emiten) y un {@code
 * RenderType.eyes()} aditivo que se salta la iluminación del mundo — se ve igual de noche y en
 * una cueva.
 * `textures/entity/space_pod_glowmask.png` es DERIVADO de `space_pod.png` con
 * `.claude/skills/zenkai-tech-textures/scripts/glowmask.py` (brillo = color base × 1.25 en los
 * píxeles cuyo color exacto coincide con la paleta emisiva canónica) — nunca pintado a mano;
 * regenerar con ese script si la textura base cambia.
 */
public class SpacePodGlowLayer extends GeoRenderLayer<SpacePodEntity> {

    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/entity/space_pod_glowmask.png");

    public SpacePodGlowLayer(GeoRenderer<SpacePodEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, SpacePodEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!animatable.isVehicle() || animatable.getControllingPassenger() == null) return;

        RenderType glow = RenderType.eyes(GLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, -1);
    }
}
