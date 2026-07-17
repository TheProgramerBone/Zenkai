package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.Color;

public class GeoLayerArmorRenderer extends GeoArmorRenderer<GeoLayerArmorItem> {

    public GeoLayerArmorRenderer(GeoLayerArmorItem item) {
        super(new GeoLayerArmorModel());
        // Overlays de cara solo para items de cuerpo (no pelo).
        if (item.hasFaceOverlays()) {
            addRenderLayer(new FaceOverlayGeoLayer(this, FaceOverlayGeoLayer.Kind.EYES));
            addRenderLayer(new FaceOverlayGeoLayer(this, FaceOverlayGeoLayer.Kind.MOUTH));
            addRenderLayer(new FaceOverlayGeoLayer(this, FaceOverlayGeoLayer.Kind.NOSE));

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
     */
    @Override
    public Color getRenderColor(GeoLayerArmorItem animatable, float partialTick, int packedLight) {
        GeoLayerArmorItem.ColorChannel ch = animatable.getColorChannel();
        if (ch == GeoLayerArmorItem.ColorChannel.NONE) return Color.WHITE;

        // ⚠ VERIFICAR en GeckoLib 4.8.4: accesor del portador de la armadura.
        // En 4.x suele ser getCurrentEntity(); si no, mira el campo/getter equivalente del GeoArmorRenderer.
        Entity wearer = getCurrentEntity();
        if (!(wearer instanceof Player player)) return Color.WHITE;

        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());
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
     * Permite presets de textura: elige la textura según el índice skinPreset del jugador.
     * Si el item no define presets, devuelve su textura única (comportamiento por defecto).
     */
    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(GeoLayerArmorItem animatable) {
        Entity wearer = getCurrentEntity();
        int preset = (wearer instanceof Player player)
                ? player.getData(DataAttachments.PLAYER_VISUAL.get()).getSkinPreset()
                : 0;
        return animatable.getTexture(preset);
    }
}