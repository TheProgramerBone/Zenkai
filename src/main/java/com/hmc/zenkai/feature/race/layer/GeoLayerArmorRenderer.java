package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.Color;

public class GeoLayerArmorRenderer extends GeoArmorRenderer<GeoLayerArmorItem> {

    public GeoLayerArmorRenderer(GeoLayerArmorItem item) {
        super(new GeoLayerArmorModel());
        // Cara (ojos/cejas/nariz/boca) solo para items de cuerpo (no pelo). Una sola capa que
        // recorre las categorías del catálogo, ver FaceOverlayGeoLayer.
        if (item.hasFaceOverlays()) {
            addRenderLayer(new FaceOverlayGeoLayer(this));
        }
        // Tinte de cuerpo multicapa (detalle + líneas) — razas multicolor (Namek).
        if (item.hasBodyTint()) {
            addRenderLayer(new BodyTintGeoLayer(this));
        }
        if (item.hasDyeTint()) {
            addRenderLayer(new DyedTintGeoLayer(this));
        }
    }

    /**
     * Tiñe el modelo con el color del canal indicado por el item, leído del jugador que lo "lleva".
     * REQUISITO: la textura debe estar en ESCALA DE GRISES para que el tinte multiplique bien.
     *
     * Color FIJO por forma (p. ej. cola/pelo con un tono propio por transformación, independiente
     * de lo que el jugador haya elegido en piel/pelo): si el stack trae DYED_COLOR, ese color GANA
     * sobre el canal. Quien lo pone es el resolver correspondiente (TailResolver/HairResolver),
     * leyendo FormDef.tailRgb()/hairRgb() de la forma activa — el jugador jamás "lleva" estos
     * ítems de verdad (son virtuales, inyectados solo para el render), así que no hace falta pasar
     * por .dyeTint()/DyedTintGeoLayer (eso es para tintes vanilla reales, craftear con tinte).
     */
    @Override
    public Color getRenderColor(GeoLayerArmorItem animatable, float partialTick, int packedLight) {
        ItemStack current = getCurrentStack();
        if (current != null && current.has(DataComponents.DYED_COLOR)) {
            return Color.ofOpaque(DyedItemColor.getOrDefault(current, 0xFFFFFF));
        }

        GeoLayerArmorItem.ColorChannel ch = animatable.getColorChannel();
        if (ch == GeoLayerArmorItem.ColorChannel.NONE) return Color.WHITE;

        // ⚠ VERIFICAR en GeckoLib 4.8.4: accesor del portador de la armadura.
        // En 4.x suele ser getCurrentEntity(); si no, mira el campo/getter equivalente del GeoArmorRenderer.
        Entity wearer = getCurrentEntity();
        if (!(wearer instanceof Player player)) return Color.WHITE;

        var visual = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        int rgb = switch (ch) {
            case SKIN   -> visual.getSkinColorRgb();
            case HAIR   -> visual.getHairColorRgb();
            default     -> 0xFFFFFF;
        };

        // ⚠ VERIFICAR en GeckoLib 4.8.4: factory de Color a partir de un RGB opaco.
        // Alternativas según versión: Color.ofOpaque(rgb), Color.ofRGB(rgb), new Color(0xFF000000 | rgb).
        return Color.ofOpaque(rgb);
    }



    /**
     * Reaplica en CADA pintado qué huesos se ocultan por casco real (casco/cuernos arcosianos,
     * antenas namek). Antes solo lo hacía GeoLayerArmorModel.setCustomAnimations: el BakedGeoModel
     * es compartido entre pintados y esa función no corre en cualquier pasada (p. ej. el muñeco
     * del inventario), así que el estado "oculto" de un jugador con casco se quedaba pegado y el
     * muñeco sin casco salía con la cabeza arcosiana sin el casco (captura 2026-10-06).
     */
    @Override
    public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, GeoLayerArmorItem animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        boolean hide = GeoLayerArmorModel.wearerHasRealHeadwear;
        for (String name : animatable.getHideOnHelmetBones()) {
            model.getBone(name).ifPresent(b -> {
                b.setHidden(hide);
                b.setChildrenHidden(hide);
            });
        }
    }

    /** Textura efectiva según el stack que se pinta (embudo único, ver resolveTexture). */
    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(GeoLayerArmorItem animatable) {
        return animatable.resolveTexture(getCurrentStack());
    }
}