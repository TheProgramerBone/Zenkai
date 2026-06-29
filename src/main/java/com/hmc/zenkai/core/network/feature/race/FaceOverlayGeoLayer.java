package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.client.customization.CustomizationAssets;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Function;

/**
 * Capa de overlay de cara (ojos / boca / nariz) sobre el modelo del cuerpo.
 * Re-renderiza el MISMO modelo del cuerpo con la textura del rasgo (elegida por índice).
 *
 *  · En vez de "inflar" el modelo (que al hacer sneak + mirar arriba dejaba el overlay por
 *    dentro y desaparecía), usamos un RenderType con VIEW_OFFSET_Z_LAYERING: empuja el rasgo
 *    hacia la cámara en VIEW space, así queda siempre justo por delante de la cara en cualquier
 *    pose, sin z-fighting y sin tener que dejar hueca la textura de la cara.
 *  · Ojos: dos pasadas estilo armadura de cuero →
 *        eyes_N.png       (esclerótica/contorno, iris transparente) SIN tinte
 *        eyes_N_iris.png  (solo iris, en gris)                      CON tinte de color de ojos
 *    Si no existe el _iris, se tiñe el ojo (fallback).
 *
 * ⚠ API específica de GeckoLib 4.8.4 / 1.21.1 — verificar al compilar:
 *   firma de render(...) de GeoRenderLayer · getRenderer() · getCurrentEntity() · reRender(...) ·
 *   nombres de RenderStateShard (RENDERTYPE_ENTITY_TRANSLUCENT_SHADER, VIEW_OFFSET_Z_LAYERING).
 */
public class FaceOverlayGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public enum Kind { EYES, MOUTH, NOSE }

    /**
     * Escala del overlay. Ahora 1.0 (NO inflar): el "quedar por delante" lo da el view-offset.
     * Si vieras z-fighting puntual, súbelo un pelín (1.002), pero normalmente no hace falta.
     */
    private static final float OVERLAY_SCALE = 1.0f;

    /** RenderType translúcido + sin cull + offset hacia la cámara (view space). Memoizado por textura. */
    private static final Function<ResourceLocation, RenderType> FACE_OVERLAY = Util.memoize(tex ->
            RenderType.create(
                    "zenkai_face_overlay",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setLightmapState(RenderStateShard.LIGHTMAP)
                            .setOverlayState(RenderStateShard.OVERLAY)
                            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(true)));

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
        RenderType rt = FACE_OVERLAY.apply(tex);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, argb);
    }

    /** Inserta un sufijo antes de la extensión: eyes_1.png → eyes_1_iris.png */
    private static ResourceLocation withSuffix(ResourceLocation rl, String suffix) {
        String p = rl.getPath();
        int dot = p.lastIndexOf('.');
        String np = (dot >= 0) ? p.substring(0, dot) + suffix + p.substring(dot) : p + suffix;
        return ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), np);
    }

    private static boolean resourceExists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }
}