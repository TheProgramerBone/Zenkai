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

    /**
     * Pasada de IMAGEN RESIDUAL (AfterImageClient): distinto de 0 = ARGB de la silueta. Mientras
     * está puesto, TODO el arte GeckoLib del mod (pelo, cola, halo) se dibuja como una silueta
     * plana translúcida de ese color, sin capas de cara/tinte, y no se oculta por invisibilidad.
     * Solo hilo de render, y lo pone y quita siempre el mismo bloque try/finally.
     */
    public static int ghostArgb = 0;

    public static boolean isGhostPass() { return ghostArgb != 0; }

    public GeoLayerArmorRenderer(GeoLayerArmorItem item) {
        super(new GeoLayerArmorModel());
        // Tinte de cuerpo multicapa (detalle + líneas) — razas multicolor (Namek).
        // VA ANTES que la cara: las capas se ejecutan en el orden en que se añaden y, a igual
        // profundidad, gana el ÚLTIMO pase. Con la cara primero, cualquier _layer_N que cubriera
        // los píxeles de la cara la tapaba: pasó con la forma final arcosiana, cuya cara está
        // pintada con el color de armadura (capa 2) y se quedaba sin ojos ni boca (2026-10-10).
        if (item.hasBodyTint()) {
            addRenderLayer(new BodyTintGeoLayer(this));
        }
        // Cara (ojos/cejas/nariz/boca) solo para items de cuerpo (no pelo). Una sola capa que
        // recorre las categorías del catálogo, ver FaceOverlayGeoLayer. Siempre por encima del tinte.
        if (item.hasFaceOverlays()) {
            addRenderLayer(new FaceOverlayGeoLayer(this));
        }
        if (item.hasDyeTint()) {
            addRenderLayer(new DyedTintGeoLayer(this));
        }
    }

    /**
     * Invisibilidad: el arte GeckoLib del mod (pelo, cola, halo, scouter, pesas...) NO se dibuja
     * mientras su dueño está "esfumado" (Paso rápido) ni con una poción de invisibilidad. La
     * armadura vainilla sí se queda (como en el juego base), pero esto es nuestro: pelo o cola
     * flotando sin cuerpo era un fallo. firePreRenderEvent devuelve false = GeckoLib no pinta nada.
     */
    @Override
    public boolean firePreRenderEvent(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                      software.bernie.geckolib.cache.object.BakedGeoModel model,
                                      net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                      float partialTick, int packedLight) {
        if (!isGhostPass() && getCurrentEntity() != null
                && com.hmc.zenkai.client.AfterImageClient.isVanished(getCurrentEntity())) {
            return false;
        }
        return super.firePreRenderEvent(poseStack, model, bufferSource, partialTick, packedLight);
    }

    /** En la pasada de imagen residual, silueta plana translúcida (textura blanca, sin cutout). */
    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(GeoLayerArmorItem animatable,
            net.minecraft.resources.ResourceLocation texture,
            @org.jetbrains.annotations.Nullable net.minecraft.client.renderer.MultiBufferSource bufferSource,
            float partialTick) {
        if (isGhostPass()) {
            return net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png"));
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /** Sin capas (cara, tinte) en la imagen residual: serían pasadas a todo color encima de la silueta. */
    @Override
    public java.util.List<software.bernie.geckolib.renderer.layer.GeoRenderLayer<GeoLayerArmorItem>> getRenderLayers() {
        return isGhostPass() ? java.util.List.of() : super.getRenderLayers();
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
        if (isGhostPass()) {
            return Color.ofARGB((ghostArgb >>> 24) & 0xFF, (ghostArgb >> 16) & 0xFF,
                    (ghostArgb >> 8) & 0xFF, ghostArgb & 0xFF);
        }
        ItemStack current = getCurrentStack();
        if (current != null && com.hmc.zenkai.feature.race.hair.HairStacks.isStrandHair(current)) return Color.WHITE;
        if (current != null && current.has(DataComponents.DYED_COLOR)) {
            return Color.ofOpaque(DyedItemColor.getOrDefault(current, 0xFFFFFF));
        }

        GeoLayerArmorItem.ColorChannel ch = animatable.getColorChannel();
        if (ch == GeoLayerArmorItem.ColorChannel.NONE) return Color.WHITE;

        Entity wearer = getCurrentEntity();
        if (!(wearer instanceof Player player)) return Color.WHITE;

        var visual = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        // La capa 0 es la piel: si la forma activa le impone color (layer_colors), manda sobre el del jugador.
        var form = com.hmc.zenkai.feature.forms.FormDef.get(
                player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
        int formSkin = form == null ? -1 : form.layerColor(0);
        int rgb = switch (ch) {
            case SKIN   -> formSkin >= 0 ? formSkin : visual.getSkinColorRgb();
            case HAIR   -> visual.getHairColorRgb();
            default     -> 0xFFFFFF;
        };

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
        ItemStack stack = getCurrentStack();
        // Pelo por mechones: textura-rampa del color de pelo (tonos en Oklab, ver HairRampColors);
        // el modelo apunta cada cara a su franja. El color ya va en la textura (getRenderColor = blanco).
        if (stack != null && com.hmc.zenkai.feature.race.hair.HairStacks.isStrandHair(stack)) {
            int hair = strandHairRgb(stack);
            // Colores propios de los mechones (StrandColors): una columna por color, mezclada con
            // el de pelo según el peso que fija HairResolver (interruptor, forma que tiñe, transición).
            Integer weight = stack.get(com.hmc.zenkai.registry.ModDataComponents.HAIR_STRAND_COLORS.get());
            if (weight != null && weight > 0) {
                var palette = com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.palette(
                        com.hmc.zenkai.feature.race.hair.HairStacks.model(stack));
                if (palette != null && !palette.plain()) {
                    return com.hmc.zenkai.client.hair.HairRampTextures.forColors(palette.resolve(hair,
                            weight / (float) com.hmc.zenkai.feature.race.HairResolver.COLOR_STEPS));
                }
            }
            return com.hmc.zenkai.client.hair.HairRampTextures.forColor(hair);
        }
        return animatable.resolveTexture(stack);
    }

    /** Color de un pelo por mechones: el fijado en el stack (forma que tiñe) o el del jugador. */
    private int strandHairRgb(ItemStack stack) {
        if (stack.has(DataComponents.DYED_COLOR)) return DyedItemColor.getOrDefault(stack, 0xFFFFFF);
        if (getCurrentEntity() instanceof Player player) {
            return player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).getHairColorRgb();
        }
        return 0x1A1A1A;
    }
}