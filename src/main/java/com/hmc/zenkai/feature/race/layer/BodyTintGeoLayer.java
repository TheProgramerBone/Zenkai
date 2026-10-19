package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RaceRenderTypes;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
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
 *   namekian_player_layer_0.png  ->  namekian_player_layer_1.png / namekian_player_layer_2.png
 * Cada máscara en gris claro en sus zonas teñibles y transparente en el resto.
 * Si una máscara no existe, esa pasada se omite.
 *
 */
public class BodyTintGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public BodyTintGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        Entity wearer = ((GeoLayerArmorRenderer) this.getRenderer()).getCurrentEntity();
        if (!(wearer instanceof Player player)) return;

        // Pelaje del Saiyan (SSJ4...): va aunque el cuerpo no traiga capas de tinte propias.
        drawFur(poseStack, bakedModel, animatable, bufferSource, partialTick, packedLight, packedOverlay, player);

        if (!animatable.hasBodyTint()) return;

        // Capas numeradas descubiertas por convención (<base>_layer_0, _1, ...), en orden.
        // Cada una se pinta sobre la base con su canal de color (o color fijo del JSON).
        for (RaceLayerDiscovery.Layer layer : RaceLayerDiscovery.layersFor(animatable)) {
            // La capa 0 = piel/base: ya la tiñe el pase base del renderer (canal SKIN).
            // Repintarla aquí multiplicaría el color dos veces (piel más oscura). La omitimos.
            if (layer.index() == 0) continue;
            if (!layer.visibleFor(player, animatable)) continue;
            drawPass(layer.texture(), layer.argb(player), poseStack, bakedModel, animatable,
                    bufferSource, partialTick, packedLight, packedOverlay);
        }
    }

    /** Textura gris del pelaje (mismo UV que el cuerpo humano); generada por tools/gen_saiyan_fur.py. */
    private static final ResourceLocation FUR =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/models/races/saiyan/saiyan_fur.png");
    private static final ResourceLocation FUR_FEMALE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/models/races/saiyan/saiyan_fur_female.png");

    /**
     * Pelaje de torso y brazos del Saiyan cuando la forma activa declara fur_rgb (FormDef.hasFur).
     * Solo en el item del CUERPO (torso): en cabeza, piernas y botas el modelo no trae esa geometría
     * y el pase gastaría un recorrido entero. Solo con el cuerpo humano (su UV es el de la textura);
     * el Oozaru y demás cuerpos propios no entran.
     */
    private void drawFur(PoseStack poseStack, BakedGeoModel bakedModel, GeoLayerArmorItem animatable,
                         MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay,
                         Player player) {
        if (((GeoLayerArmorRenderer) this.getRenderer()).getCurrentSlot() != EquipmentSlot.CHEST) return;
        String path = animatable.getTexturePath().getPath();
        if (!path.contains("/human/")) return;
        FormDef form = FormDef.get(player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
        if (form == null || !form.hasFur()) return;
        if (PlayerStatsAttachment.get(player).getRace() != Race.SAIYAN) return;
        ResourceLocation tex = path.contains("female") ? FUR_FEMALE : FUR;
        drawPass(tex, 0xFF000000 | (form.furRgb() & 0xFFFFFF), poseStack, bakedModel, animatable,
                bufferSource, partialTick, packedLight, packedOverlay);
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