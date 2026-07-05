package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.client.customization.CustomizationAssets;
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

import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.resourceExists;
import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.withSuffix;

/**
 * Capa de overlay de cara (ojos / boca / nariz) sobre el modelo del cuerpo.
 * Re-renderiza el MISMO modelo del cuerpo con la textura del rasgo (elegida por índice).
 *
 *  · Usa el RenderType compartido {@link RaceRenderTypes#viewOffset} (cutout + VIEW_OFFSET_Z_LAYERING):
 *    empuja el rasgo hacia la cámara en VIEW space, así queda siempre justo por delante de la cara en
 *    cualquier pose, sin z-fighting; y al ser cutout (opaco) se dibuja en la pasada opaca como el
 *    cuerpo, por lo que no lo oculta geometría translúcida delante (p. ej. el cristal del space pod).
 *  · Ojos: dos pasadas estilo armadura de cuero →
 *        eyes_N.png       (esclerótica/contorno, iris transparente) SIN tinte
 *        eyes_N_iris.png  (solo iris, en gris)                      CON tinte de color de ojos
 *    Si no existe el _iris, se tiñe el ojo (fallback).
 *
 * ⚠ API específica de GeckoLib 4.8.4 / 1.21.1 — verificar al compilar:
 *   firma de render(...) de GeoRenderLayer · getRenderer() · getCurrentEntity() · reRender(...).
 */
public class FaceOverlayGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public enum Kind { EYES, MOUTH, NOSE }

    /**
     * Escala del overlay. 1.0 (NO inflar): el "quedar por delante" lo da el view-offset.
     * Si vieras z-fighting puntual, súbelo un pelín (1.002), pero normalmente no hace falta.
     */
    private static final float OVERLAY_SCALE = 1.0f;

    private final Kind kind;

    public FaceOverlayGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer, Kind kind) {
        super(renderer);
        this.kind = kind;
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.hasFaceOverlays()) return;

        Entity wearer = ((GeoLayerArmorRenderer) this.getRenderer()).getCurrentEntity();
        if (!(wearer instanceof Player player)) return;
        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);

        poseStack.pushPose();
        poseStack.scale(OVERLAY_SCALE, OVERLAY_SCALE, OVERLAY_SCALE);

        switch (kind) {
            case EYES -> {
                ResourceLocation base = CustomizationAssets.getEye(vis.getEyeIndex());
                if (base != null) {
                    int eyeArgb = 0xFF000000 | (vis.getEyeColorRgb() & 0xFFFFFF);
                    ResourceLocation iris = withSuffix(base, "_iris");
                    if (resourceExists(iris)) {
                        drawPass(base, 0xFFFFFFFF, poseStack, bakedModel, animatable,
                                bufferSource, partialTick, packedLight, packedOverlay); // esclerótica/contorno fijo
                        drawPass(iris, eyeArgb, poseStack, bakedModel, animatable,
                                bufferSource, partialTick, packedLight, packedOverlay); // iris tintado
                    } else {
                        drawPass(base, eyeArgb, poseStack, bakedModel, animatable,
                                bufferSource, partialTick, packedLight, packedOverlay); // sin _iris: tiñe
                    }
                }
            }
            case MOUTH -> {
                ResourceLocation t = CustomizationAssets.getMouth(vis.getMouthIndex());
                int skinArgb = 0xFF000000 | (vis.getSkinColorRgb() & 0xFFFFFF); // tinte multiplicativo = color de piel
                if (t != null) drawPass(t, skinArgb, poseStack, bakedModel, animatable,
                        bufferSource, partialTick, packedLight, packedOverlay);
            }
            case NOSE -> {
                ResourceLocation t = CustomizationAssets.getNose(vis.getNoseIndex());
                int skinArgb = 0xFF000000 | (vis.getSkinColorRgb() & 0xFFFFFF); // tinte multiplicativo = color de piel
                if (t != null) drawPass(t, skinArgb, poseStack, bakedModel, animatable,
                        bufferSource, partialTick, packedLight, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    /** Re-renderiza el modelo del cuerpo con una textura y un color ARGB, con offset hacia la cámara. */
    private void drawPass(ResourceLocation tex, int argb, PoseStack poseStack, BakedGeoModel bakedModel,
                          GeoLayerArmorItem animatable, MultiBufferSource bufferSource,
                          float partialTick, int packedLight, int packedOverlay) {
        RenderType rt = RaceRenderTypes.viewOffset(tex);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, argb);
    }
}