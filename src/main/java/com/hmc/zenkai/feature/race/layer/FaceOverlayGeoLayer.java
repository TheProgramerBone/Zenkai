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

        // Ultra Instinct ACTIVADO COMO TÉCNICA (interruptor, con la skill): los ojos del UI dominado (el eye_rgb de la
        // forma ultra_instinct, no un blanco puro) sea cual sea la forma, para que se vea de un vistazo si el jugador
        // la está usando.
        FormDef uiForm = FormDef.get(com.hmc.zenkai.feature.forms.FormIds.ULTRA_INSTINCT);
        int uiEyes = uiForm != null && uiForm.tintsEyes() ? uiForm.eyeRgb() & 0xFFFFFF : 0xFFFFFF;
        int eyeOverride = (SkillEffects.level(player, SkillEffects.ULTRA_INSTINCT) > 0
                && SkillToggles.isOn(player, SkillEffects.ULTRA_INSTINCT)) ? uiEyes : -1;
        // Legendario: el iris depende de la maestría (aparece desde el 50 %) y, en el Pleno, del overdrive. No se
        // aplica durante el cambio de forma: ahí manda la mezcla de colores de abajo.
        if (eyeOverride < 0 && !com.hmc.zenkai.client.hair.HairTransition.current(player, partialTick).blending()) {
            eyeOverride = com.hmc.zenkai.feature.forms.LegendaryEyes.rgb(player);
        }

        // Cambio de forma en curso (hold o transición automática): ojos y cejas pasan del color de
        // la forma de origen al de la de destino al mismo ritmo que el pelo (HairTransition), en vez
        // de saltar de golpe al completarse (petición del usuario, 2026-10-12). current() y no
        // peek(): las razas sin pelo 3D (Namek, Arcosiano) son justo las que tiñen los ojos, y
        // peek() solo sabe del cambio si el pelo 3D se ha pintado hace nada.
        var blend = com.hmc.zenkai.client.hair.HairTransition.current(player, partialTick);
        FormDef blendFrom = blend.blending() ? FormDef.get(blend.from()) : null;
        FormDef blendTo = blend.blending() ? FormDef.get(blend.to()) : null;

        // Expresión (parpadeo, daño, cansancio, sueño, muerte, carga de ki): se calcula en el cliente, sin red, y solo
        // elige QUÉ CAPAS se pintan de ojos y boca. Quieta durante el cambio de forma (ahí manda la mezcla de
        // colores) y con los ojos forzados (Legendario, Ultra Instinto activo); apagable en la config del cliente.
        // La BOCA sí cambia durante el cambio de forma (es justo el grito de la transformación; la mezcla de colores solo
        // toca ojos y cejas): se calcula siempre y solo los ojos se quedan quietos.
        com.hmc.zenkai.client.face.FaceExpression.State expr = com.hmc.zenkai.client.face.FaceExpression.State.NEUTRAL;
        if (com.hmc.zenkai.config.ClientConfig.faceExpressions()) {
            expr = com.hmc.zenkai.client.face.FaceExpression.current(player, partialTick);
            if (blend.blending() || eyeOverride >= 0) {
                expr = new com.hmc.zenkai.client.face.FaceExpression.State(
                        com.hmc.zenkai.client.face.FaceExpression.Expression.NEUTRAL, expr.mouth());
            }
        }
        net.minecraft.resources.ResourceLocation formId = form.getFormId();

        // Marcas faciales (fase 4): cicatrices, pinturas... elegidas por el jugador (hasta 3), pintadas ya en color
        // DEBAJO de los rasgos (ojos, cejas, nariz y boca quedan encima; pedido del usuario, 2026-11-01) y recolocadas a la región de cara de cada modelo como cualquier rasgo.
        for (net.minecraft.resources.ResourceLocation mark : vis.getMarkings()) {
            ResourceLocation src = markingTexture(mark);
            if (!resourceExists(src)) continue;
            ResourceLocation tex = FaceRemapTextures.remap(src, region);
            if (tex == null) continue;
            drawPass(tex, 0xFFFFFFFF, poseStack, bakedModel,
                    animatable, bufferSource, partialTick, packedLight, packedOverlay);
        }

        for (FaceCategory category : FaceCategory.values()) {
            FacePartDef part = FaceCatalog.get(vis.getFacePart(category));
            if (part == null) continue;
            // Cara POR FORMA (fase 3): la forma activa puede imponer un rasgo (assets/zenkai/zenkai_form_faces/).
            part = com.hmc.zenkai.client.face.FormFaces.override(formId, category, part, race);
            // Capas de la RAZA si el rasgo trae una variante dibujada para ella (race_layers); con una expresión
            // activa, las de esa expresión (FaceExpression.layersFor).
            java.util.List<FaceLayer> partLayers =
                    com.hmc.zenkai.client.face.FaceExpression.layersFor(expr, category, part, race);
            // Emisivo (fase 5): a pleno brillo, ignorando la luz del mundo. Solo la categoría que la forma declara.
            int light = (com.hmc.zenkai.config.ClientConfig.faceEmissive()
                    && com.hmc.zenkai.client.face.FormFaces.emissive(formId, category))
                    ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight;
            for (FaceLayer layer : partLayers) {
                // Namek/Arcosiano/Majin no tienen cejas: ni la categoría ni el trazo-ceja del ojo.
                if (!RaceFaceTraits.shows(race, category, layer)) continue;
                // Formas sin cejas (SSJ3): fuera el trazo de ceja del ojo y la categoría de cejas.
                if (formDef != null && formDef.hidesBrows()
                        && (category == FaceCategory.EYEBROWS || layer.eyebrow())) continue;
                ResourceLocation tex = textureFor(layer, region);
                if (tex == null) continue;
                boolean eyes = category == FaceCategory.EYES;
                int rgb = blend.blending() && layer.color() != FaceLayer.Color.SKIN
                        ? com.hmc.zenkai.feature.race.hair.strand.HairRampColors.mix(
                                layer.resolveRgb(vis, blendFrom, eyeOverride, eyes),
                                layer.resolveRgb(vis, blendTo, eyeOverride, eyes), blend.t())
                        : layer.resolveRgb(vis, formDef, eyeOverride, eyes);
                // Boca/nariz ("skin") más oscuras donde la raza lo pide (verde Namek).
                if (layer.color() == FaceLayer.Color.SKIN) {
                    rgb = RaceFaceTraits.shadeSkin(race, FaceRemapTextures.skinFeatureRgb(animatable, player, rgb));
                }
                drawPass(tex, 0xFF000000 | rgb, poseStack, bakedModel,
                        animatable, bufferSource, partialTick, light, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    /** Textura de una marca facial: {@code <ns>:textures/customization/face/markings/<id>.png}. */
    public static ResourceLocation markingTexture(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/customization/face/markings/" + id.getPath() + ".png");
    }

    /** Emblema Majin de icons.png tal cual, 18x15 (tools/gen_majin_mark.py). Lleva su propio color: no se tiñe. */
    private static final ResourceLocation MAJIN_MARK = ResourceLocation.fromNamespaceAndPath(
            "zenkai", "textures/customization/face/majin_mark_icon.png");

    // ── «M» de Babidi como CALCOMANÍA (2026-11-01) ──
    // A 2 texels por píxel de MC el icono (18x15) mide más que la cara entera (16x16), así que no cabe en la textura de
    // la cara sin reducirlo. Se dibuja aparte: un cuadrito pegado al hueso de la cabeza, delante de la frente, con el
    // PNG intacto; lo pequeño es el cuadrito, no los píxeles. Medidas en TEXELS de la cara (16x16; la frente son las
    // filas 0-3 y los ojos empiezan en la 4), centrado en horizontal y pegado al borde superior.
    private static final float DECAL_W = 5.4f, DECAL_H = 4.5f;   // proporción 18:15 del icono
    private static final float DECAL_CENTER_X = 8.0f, DECAL_TOP_Y = 2.5f;
    /**
     * Separación hacia fuera de la cara, en bloques: mínima (0,002 = 0,03 px), PEGADA a la cabeza. Con 0,08 se sacaba
     * por delante del flequillo pero de perfil se veía despegada (2026-11-01). Quien evita el z-fighting es el
     * RenderType (RaceRenderTypes.viewOffset: empuja hacia la cámara en view space, como los ojos y la boca), no la distancia.
     */
    private static final float DECAL_LIFT = 0.002f;

    @Override
    public void renderForBone(PoseStack poseStack, GeoLayerArmorItem animatable, software.bernie.geckolib.cache.object.GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if (!"head".equals(bone.getName()) || bone.isHidden() || bone.getCubes().isEmpty()) return;
        if (!animatable.hasFaceOverlays()) return;
        GeoLayerArmorRenderer armorRenderer = (GeoLayerArmorRenderer) this.getRenderer();
        if (armorRenderer.getCurrentSlot() != EquipmentSlot.HEAD) return;
        if (!(armorRenderer.getCurrentEntity() instanceof Player player)) return;
        if (!PlayerVisualAttachment.get(player).isMajinControlled() || !resourceExists(MAJIN_MARK)) return;
        var formId = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        if (com.hmc.zenkai.feature.forms.FormIds.OOZARU.equals(formId)
                || com.hmc.zenkai.feature.forms.FormIds.SUPER_OOZARU.equals(formId)) return;

        // La cara del modelo: el quad NORTE de la primera caja de la cabeza (el mismo que FaceRemapTextures usa para saber
        // dónde está la cara en la textura). Sus esquinas se sitúan por su UV: arriba-izquierda = (u, v) mínimos.
        software.bernie.geckolib.cache.object.GeoQuad face = null;
        for (var q : bone.getCubes().get(0).quads()) {
            if (q != null && q.direction() == net.minecraft.core.Direction.NORTH) { face = q; break; }
        }
        if (face == null) return;
        org.joml.Vector3f tl = null, tr = null, bl = null;
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (var v : face.vertices()) {
            minU = Math.min(minU, v.texU()); maxU = Math.max(maxU, v.texU());
            minV = Math.min(minV, v.texV()); maxV = Math.max(maxV, v.texV());
        }
        for (var v : face.vertices()) {
            boolean left = Math.abs(v.texU() - minU) < 1.0e-5f, top = Math.abs(v.texV() - minV) < 1.0e-5f;
            if (left && top) tl = v.position();
            else if (!left && top) tr = v.position();
            else if (left) bl = v.position();
        }
        if (tl == null || tr == null || bl == null) return;

        // Punto de la cara en texels (tx a la derecha, ty hacia abajo) → posición en el espacio del hueso.
        org.joml.Vector3f right = new org.joml.Vector3f(tr).sub(tl), down = new org.joml.Vector3f(bl).sub(tl);
        org.joml.Vector3f out = new org.joml.Vector3f(face.normal()).normalize().mul(DECAL_LIFT);
        float x0 = DECAL_CENTER_X - DECAL_W / 2f, x1 = DECAL_CENTER_X + DECAL_W / 2f;
        float y0 = DECAL_TOP_Y, y1 = DECAL_TOP_Y + DECAL_H;
        org.joml.Vector3f[] c = {at(tl, right, down, out, x0, y0), at(tl, right, down, out, x0, y1),
                at(tl, right, down, out, x1, y1), at(tl, right, down, out, x1, y0)};
        float[] us = {0f, 0f, 1f, 1f}, vs = {0f, 1f, 1f, 0f};

        // El mismo RenderType de los rasgos de la cara (armorCutoutNoCull: sin culling y con view-offset hacia la cámara).
        RenderType rt = RaceRenderTypes.viewOffset(MAJIN_MARK);
        VertexConsumer vc = bufferSource.getBuffer(rt);
        PoseStack.Pose pose = poseStack.last();
        org.joml.Vector3f n = new org.joml.Vector3f(face.normal()).normalize();
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose.pose(), c[i].x, c[i].y, c[i].z).setColor(255, 255, 255, 255).setUv(us[i], vs[i])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(pose, n.x, n.y, n.z);
        }
        // Se devuelve el buffer del modelo: el resto de cubos del hueso y los hijos siguen en su RenderType.
        bufferSource.getBuffer(renderType);
    }

    private static org.joml.Vector3f at(org.joml.Vector3f tl, org.joml.Vector3f right, org.joml.Vector3f down,
                                        org.joml.Vector3f out, float tx, float ty) {
        return new org.joml.Vector3f(tl).fma(tx / 16f, right).fma(ty / 16f, down).add(out);
    }

    /**
     * Textura de una capa, o null si este cliente no la tiene. Punto ÚNICO de elección de
     * textura. Las expresiones eligen QUÉ CAPAS se pintan en el bucle de render (ver
     * {@code FaceExpression}); las variantes por rasgo (blink.png propio de cada ojo) entrarán allí. Devuelve la textura YA recolocada a la región de
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
