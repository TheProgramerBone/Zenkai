package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

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
 *   namekian_player_colorable.png  ->  namekian_player_detail.png / namekian_player_lines.png
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
        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);

        ResourceLocation base   = animatable.getTexturePath();
        ResourceLocation detail = deriveMask(base, "_detail");
        ResourceLocation lines  = deriveMask(base, "_lines");

        // Capa 2: detalles
        if (resourceExists(detail)) {
            int argb = 0xFF000000 | (vis.getDetailColorRgb() & 0xFFFFFF);
            drawPass(detail, argb, poseStack, bakedModel, animatable,
                    bufferSource, partialTick, packedLight, packedOverlay);
        }
        // Capa 3: líneas
        if (resourceExists(lines)) {
            int argb = 0xFF000000 | (vis.getLineColorRgb() & 0xFFFFFF);
            drawPass(lines, argb, poseStack, bakedModel, animatable,
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

    /** namekian_player_colorable.png -> namekian_player_detail.png / _lines.png */
    private static ResourceLocation deriveMask(ResourceLocation base, String suffix) {
        String p = base.getPath();
        int dot = p.lastIndexOf('.');
        String ext  = (dot >= 0) ? p.substring(dot) : ".png";
        String stem = (dot >= 0) ? p.substring(0, dot) : p;
        if (stem.endsWith("_colorable")) stem = stem.substring(0, stem.length() - "_colorable".length());
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + suffix + ext);
    }

    private static boolean resourceExists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }
}