package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * El cristal de la cámara, en una pasada translúcida APARTE y la última.
 * POR QUÉ NO VA EN LA TEXTURA BASE con el modelo entero translúcido, que es lo obvio: con Iris y
 * Sodium el dibujado se agrupa por tipo y lo translúcido se pinta al final, así que el cuerpo
 * de la máquina caía DESPUÉS del brillo aditivo de KiChamberGlowLayer y lo tapaba entero (medido
 * con capturas el 2026-11-16: cámara encendida y apagada salían idénticas). Repartido en tres
 * pasadas, cuerpo recortado, brillo aditivo y cristal translúcido, el orden natural de cada
 * tipo ya es el correcto con y sin esos mods.
 * La textura del cristal comparte el reparto del atlas de la base y es transparente en lo que no
 * es cristal, así que volver a dibujar el modelo entero con ella solo pinta los paneles.
 */
public class KiChamberGlassLayer extends GeoRenderLayer<KiContainmentChamberBlockEntity> {

    private static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
            "textures/block/ki_containment_chamber_glass.png");

    public KiChamberGlassLayer(GeoRenderer<KiContainmentChamberBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, KiContainmentChamberBlockEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glass = KiChamberRenderTypes.glass(GLASS);   // sin escribir profundidad: ver esa clase
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glass,
                bufferSource.getBuffer(glass), partialTick, packedLight, packedOverlay, -1);
    }
}
