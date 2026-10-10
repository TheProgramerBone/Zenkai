package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.client.FaceRemapTextures;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.RaceRenderTypes;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.feature.race.face.FaceRegion;
import com.hmc.zenkai.feature.race.face.RaceFaceTraits;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import static com.hmc.zenkai.feature.race.RaceTextureUtil.resourceExists;

/**
 * Cara del jugador (ojos, cejas, nariz, boca) sobre el modelo del cuerpo. UNA sola capa que
 * recorre las categorías en orden (FaceCategory) y, por cada rasgo elegido, pinta sus capas
 * (FacePartDef.layers) re-renderizando el MISMO modelo con la textura de cada una.
 *
 *  · RenderType compartido {@link RaceRenderTypes#viewOffset} (cutout + VIEW_OFFSET_Z_LAYERING):
 *    empuja el rasgo hacia la cámara en VIEW space, así queda justo por delante de la cara en
 *    cualquier pose, sin z-fighting; y al ser cutout (opaco) se dibuja en la pasada opaca como el
 *    cuerpo, por lo que no lo oculta geometría translúcida delante (p. ej. el cristal del space pod).
 *    Es vanilla A PROPÓSITO: un RenderType propio rompió Iris/Oculus una vez — no volver a crearlo.
 *  · Cada capa trae su fuente de color (FaceLayer.Color) y el color MULTIPLICA la textura. Lo que
 *    antes era fijo en código ("eyes_N.png sin tinte + eyes_N_iris.png con color de ojos") ahora
 *    es un JSON de dos capas; pupila, brillo, iris izquierdo/derecho o cejas son más capas.
 *  · Color de ojos: el eye_rgb de la forma activa manda por encima del del jugador (pensado
 *    para razas sin pelo, donde una transformación no puede "vender" pelo dorado). Color de pelo
 *    (cejas): igual con hair_rgb — en SSJ las cejas se vuelven doradas con el pelo.
 *  · Un id que el catálogo no conoce (no llegó el sync, addon desinstalado) o un PNG ausente en
 *    este cliente: esa capa no se pinta. Nunca crash ni textura morada.
 *
 * Coste: una pasada por capa, solo en el pase de HEAD. Cara típica de hoy: 3 (ojos 2 + boca 1).
 */
public class FaceOverlayGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    /**
     * Escala del overlay. 1.0 (NO inflar): el "quedar por delante" lo da el view-offset.
     * Si vieras z-fighting puntual, súbelo un pelín (1.002), pero normalmente no hace falta.
     */
    private static final float OVERLAY_SCALE = 1.0f;

    public FaceOverlayGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.hasFaceOverlays()) return;

        GeoLayerArmorRenderer armorRenderer = (GeoLayerArmorRenderer) this.getRenderer();
        // Las 4 piezas de cuerpo llevan .faceOverlays(), pero la cara solo existe en el pase de
        // HEAD: en CHEST/LEGS/FEET applyBoneVisibilityBySlot oculta la cabeza y el reRender no
        // emitía ni un vértice, aunque sí recorría el árbol entero de huesos y pedía buffer
        // (9-12 recorridos inútiles por jugador y frame, verificado en el source de GeckoLib 4.8.4).
        if (armorRenderer.getCurrentSlot() != EquipmentSlot.HEAD) return;

        Entity wearer = armorRenderer.getCurrentEntity();
        if (!(wearer instanceof Player player)) return;
        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        // Oozaru / Super Oozaru: modelo y cara propios; los rasgos del catálogo no se aplican.
        if (com.hmc.zenkai.feature.forms.FormIds.OOZARU.equals(form.getFormId())
                || com.hmc.zenkai.feature.forms.FormIds.SUPER_OOZARU.equals(form.getFormId())) return;
        FormDef formDef = FormDef.get(form.getFormId());
        Race race = PlayerStatsAttachment.get(player).getRace();

        // Dónde está la cara en la textura de ESTE modelo (FaceRegions): los rasgos se pintan en
        // la cara canónica y se recolocan aquí (la arcosiana está en (16,48), no en (16,16)).
        FaceRegion region = FaceRemapTextures.regionFor(animatable.getModelPath());

        poseStack.pushPose();
        poseStack.scale(OVERLAY_SCALE, OVERLAY_SCALE, OVERLAY_SCALE);

        // Pelo PLANO (peinado con "representation": "texture"): se pinta sobre la propia cabeza,
        // como el pelo de una skin vanilla, ANTES de los rasgos para que las cejas lo pisen. El
        // pelo 3D ("geo") lo pinta HairGeoLayer aparte. Bajo un casco real queda tapado por el
        // propio casco, que se dibuja encima. NO pasa por FaceRemapTextures: el pelo plano cubre
        // la cabeza entera (techo, nuca, laterales) en la UV de una cabeza uv [0,0], no solo la
        // cara; recolocar solo la cara le cortaría el resto.
        if (!com.hmc.zenkai.feature.race.HairCode.isCustom(vis.getHairStyleId())) {
            HairStyleDef hair = HairCatalog.effectiveStyle(vis.getHairStyle(),
                    formDef == null ? null : formDef.id(), race);
            if (hair != null && hair.representation() == HairStyleDef.Representation.TEXTURE
                    && resourceExists(hair.texture())) {
                drawPass(hair.texture(), 0xFF000000 | HairCatalog.tintRgb(hair, vis.getHairColorRgb(), formDef),
                        poseStack, bakedModel, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            }
        }

        // Ultra Instinct ACTIVADO COMO TÉCNICA (interruptor, con la skill): ojos blancos sea cual sea
        // la forma, para que se vea de un vistazo si el jugador la está usando.
        int eyeOverride = (SkillEffects.level(player, SkillEffects.ULTRA_INSTINCT) > 0
                && SkillToggles.isOn(player, SkillEffects.ULTRA_INSTINCT)) ? 0xFFFFFF : -1;

        for (FaceCategory category : FaceCategory.values()) {
            FacePartDef part = FaceCatalog.get(vis.getFacePart(category));
            if (part == null) continue;
            // Capas de la RAZA si el rasgo trae una variante dibujada para ella (race_layers).
            for (FaceLayer layer : part.layersFor(race)) {
                // Namek/Arcosiano/Majin no tienen cejas: ni la categoría ni el trazo-ceja del ojo.
                if (!RaceFaceTraits.shows(race, category, layer)) continue;
                ResourceLocation tex = textureFor(layer, region);
                if (tex == null) continue;
                int rgb = layer.resolveRgb(vis, formDef, eyeOverride, category == FaceCategory.EYES);
                // Boca/nariz ("skin") más oscuras donde la raza lo pide (verde Namek).
                if (layer.color() == FaceLayer.Color.SKIN) {
                    rgb = RaceFaceTraits.shadeSkin(race, FaceRemapTextures.skinFeatureRgb(animatable, player, rgb));
                }
                drawPass(tex, 0xFF000000 | rgb, poseStack, bakedModel,
                        animatable, bufferSource, partialTick, packedLight, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    /**
     * Textura de una capa, o null si este cliente no la tiene. Punto ÚNICO de elección de
     * textura: las expresiones (parpadeo, daño...) entrarán aquí eligiendo una variante de la
     * capa, sin tocar el resto de la clase. Devuelve la textura YA recolocada a la región de
     * cara del modelo (la misma si la región es la canónica).
     */
    @Nullable
    private static ResourceLocation textureFor(FaceLayer layer, FaceRegion region) {
        if (!resourceExists(layer.texture())) return null;
        return FaceRemapTextures.remap(layer.texture(), region);
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
