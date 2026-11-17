package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
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
 * Luz de la cámara, emisiva SOLO mientras carga. Dos pasadas aditivas sobre el modelo, con el
 * mismo truco que ScouterBenchGlowLayer (y por su mismo motivo: un AutoGlowingGeoLayer borra de
 * la textura base los píxeles del glowmask y, como esta capa se salta con la máquina apagada,
 * dejaría huecos):
 *   1. el GLOWMASK: LED, ranura de lectura y aro escáner, con su color propio;
 *   2. la capa de KI, que está en GRISES y se tiñe con el color del tipo de energía que se
 *      está cargando. Así hay una textura y no cuatro casi iguales.
 */
public class KiChamberGlowLayer extends GeoRenderLayer<KiContainmentChamberBlockEntity> {

    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
            "textures/block/ki_containment_chamber_glowmask.png");
    private static final ResourceLocation KI = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
            "textures/block/ki_containment_chamber_ki.png");

    public KiChamberGlowLayer(GeoRenderer<KiContainmentChamberBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, KiContainmentChamberBlockEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        KiEnergyType type = animatable.glowType();
        if (type == null) return;

        RenderType glow = RenderType.eyes(GLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                bufferSource.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, -1);

        RenderType ki = RenderType.eyes(KI);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, ki,
                bufferSource.getBuffer(ki), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, type.argb());
    }
}
