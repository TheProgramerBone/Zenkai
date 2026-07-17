package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.deriveMask;
import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.resourceExists;

/**
 * Capa de tinte multicapa del CUERPO (Namek y futuras razas multicolor).
 *
 * La base de piel ya la tiñe el renderer con el canal SKIN (getRenderColor).
 * Esta capa pinta DOS pasadas más sobre el mismo modelo:
 *   · <base>_detail.png  teñida con detailColor  (capa 2)
 *   · <base>_lines.png   teñida con lineColor     (capa 3)
 *
 * Usa el RenderType view-offset (RaceRenderTypes.viewOffset): empuja cada pasada hacia la cámara
 * en VIEW space, así quedan SIEMPRE por delante de la piel sin escalar el modelo y sin tener que
 * dejar hueca la textura base. Esto evita además que las capas desaparezcan al hacer sneak.
 *
 * Las máscaras se derivan por convención desde la textura base del item:
 *   namekian_player_layer_0.png  ->  namekian_player_layer_1.png / namekian_player_layer_2.png
 * Cada máscara en gris claro en sus zonas teñibles y transparente en el resto.
 * Si una máscara no existe, esa pasada se omite.
 *
 * ⚠ API GeckoLib 4.8.4 — verificar: render(...), getCurrentEntity(), reRender(...), color int ARGB.
 */
public class BodyTintGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public BodyTintGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.hasBodyTint()) return;

        Entity wearer = ((GeoLayerArmorRenderer) this.getRenderer()).getCurrentEntity();
        if (!(wearer instanceof Player player)) return;

        // Capas numeradas descubiertas por convención (<base>_layer_0, _1, ...), en orden.
        // Cada una se pinta sobre la base con su canal de color (o color fijo del JSON).
        for (RaceLayerDiscovery.Layer layer : RaceLayerDiscovery.layersFor(animatable)) {
            // La capa 0 = piel/base: ya la tiñe el pase base del renderer (canal SKIN).
            // Repintarla aquí multiplicaría el color dos veces (piel más oscura). La omitimos.
            if (layer.index() == 0) continue;
            drawPass(layer.texture(), layer.argb(player), poseStack, bakedModel, animatable,
                    bufferSource, partialTick, packedLight, packedOverlay);
        }
    }

    /** Re-renderiza el modelo con una textura-máscara y un color ARGB, empujado hacia la cámara. */
    private void drawPass(ResourceLocation tex, int argb, PoseStack poseStack, BakedGeoModel bakedModel,
                          GeoLayerArmorItem animatable, MultiBufferSource bufferSource,
                          float partialTick, int packedLight, int packedOverlay) {
        RenderType rt = RaceRenderTypes.viewOffset(tex);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, argb);
    }
}