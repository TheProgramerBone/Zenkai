package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Brazo en 1ª persona usando el MISMO modelo del cuerpo (reusado): oculta todos los huesos
 * menos el del brazo y lo renderiza en la pose que da RenderArmEvent. Así hereda automáticamente
 * cualquier cambio de modelo (transformaciones, género, etc.) sin assets extra.
 *
 * Para razas con tinte multicapa (bodyTint: Namek, y futuras Majin/Arcosian) pinta además las
 * pasadas _detail y _lines sobre el brazo, igual que BodyTintGeoLayer hace en 3ª persona — si no,
 * el brazo se vería "hueco" donde la textura base es transparente.
 *
 * ⚠ API de GeckoLib 4.8.4 a verificar al compilar:
 *   · GeoObjectRenderer (constructor + firma de render(...))
 *   · GeoModel.getBakedModel(...) / BakedGeoModel.topLevelBones()
 *   · GeoBone.setHidden(boolean) / setChildrenHidden(boolean)
 *   · Color.ofOpaque(rgb)  (mismo factory que GeoLayerArmorRenderer)
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiFirstPersonArmHooks {

    private ZenkaiFirstPersonArmHooks() {}

    // Ajustes de alineación. MUTABLES en runtime vía el comando /zenkaiarm (calibración en vivo).
    public static float OFF_X = -1.55f;
    public static float OFF_Y = 0.65f;
    public static float OFF_Z = -0.5f;
    public static float SCALE = 1.0f;
    public static float ROT_X = 0.0f;
    public static float ROT_Y = 0.0f;
    public static float ROT_Z = 0.0f;

    private static final String RIGHT_ARM_BONE = "bipedRightArm";
    private static final String LEFT_ARM_BONE  = "bipedLeftArm";

    private static ArmRenderer RENDERER; // cache perezoso (en hilo de render)

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent e) {
        Player player = e.getPlayer();
        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());
        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!visual.shouldRenderRaceSkin() || !stats.isRaceChosen()) return;

        ItemStack body = RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.CHEST);
        if (!(body.getItem() instanceof GeoLayerArmorItem item)) return;

        e.setCanceled(true); // ocultar el brazo vanilla

        if (RENDERER == null) RENDERER = new ArmRenderer();
        RENDERER.renderArm(item, player, e.getArm(), e.getPoseStack(),
                e.getMultiBufferSource(), e.getPackedLight());
    }

    /** GeoObjectRenderer que pinta SOLO el brazo del cuerpo, tintado según el canal del item. */
    private static final class ArmRenderer extends GeoObjectRenderer<GeoLayerArmorItem> {
        private Player current;
        /** Si != null, getRenderColor devuelve este color (para las pasadas detail/lines). */
        private Integer passColor = null;

        ArmRenderer() { super(new GeoLayerArmorModel()); }

        void renderArm(GeoLayerArmorItem item, Player player, HumanoidArm arm,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
            this.current = player;

            GeoModel<GeoLayerArmorItem> model = getGeoModel();
            BakedGeoModel baked = model.getBakedModel(model.getModelResource(item));
            String armRoot = (arm == HumanoidArm.RIGHT) ? RIGHT_ARM_BONE : LEFT_ARM_BONE;

            try {
                for (GeoBone b : baked.topLevelBones()) {
                    boolean isArm = b.getName().equals(armRoot);
                    setBranchHidden(b, !isArm);
                    // Los GeoBone del modelo son compartidos y mutables: un render previo (3ª persona)
                    // pudo dejar el brazo animado por el idle. Lo devolvemos a su pose de bind ANTES de
                    // pintar, y como el render fuerza isReRender=true (no corre handleAnimations), se queda
                    // quieto igual que el brazo vanilla. Fin del vaivén.
                    if (isArm) resetBranchToBind(b);
                }

                ResourceLocation baseTex = model.getTextureResource(item);
                RenderType rt = RenderType.entityTranslucent(baseTex);

                float pivotX = (arm == HumanoidArm.RIGHT) ? -5f : 5f;
                float pivotY = 22f;

                poseStack.pushPose();
                poseStack.translate(OFF_X, OFF_Y, OFF_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(ROT_X));
                poseStack.mulPose(Axis.YP.rotationDegrees(ROT_Y));
                poseStack.mulPose(Axis.ZP.rotationDegrees(ROT_Z));
                poseStack.scale(SCALE, SCALE, SCALE);
                poseStack.scale(1f, -1f, 1f);
                poseStack.translate(-pivotX / 16f, -pivotY / 16f, 0f);

                // Pasada base (piel): tinte por el canal del item (SKIN).
                passColor = null;
                render(poseStack, item, buffers, rt, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY);

                // Pasadas de tinte de cuerpo (detalle + líneas) para razas multicolor.
                if (item.hasBodyTint()) {
                    var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());
                    ResourceLocation detail = deriveMask(baseTex, "_detail");
                    ResourceLocation lines  = deriveMask(baseTex, "_lines");

                    if (resourceExists(detail)) {
                        passColor = visual.getDetailColorRgb();
                        RenderType rtD = RaceRenderTypes.viewOffset(detail);
                        render(poseStack, item, buffers, rtD, buffers.getBuffer(rtD), light, OverlayTexture.NO_OVERLAY);
                    }
                    if (resourceExists(lines)) {
                        passColor = visual.getLineColorRgb();
                        RenderType rtL = RaceRenderTypes.viewOffset(lines);
                        render(poseStack, item, buffers, rtL, buffers.getBuffer(rtL), light, OverlayTexture.NO_OVERLAY);
                    }
                    passColor = null;
                }

                poseStack.popPose();
            } finally {
                for (GeoBone b : baked.topLevelBones()) {
                    setBranchHidden(b, false);
                }
                this.passColor = null;
                this.current = null;
            }
        }

        /** Oculta/muestra un hueso y TODA su descendencia (no solo un nivel). */
        private static void setBranchHidden(GeoBone bone, boolean hidden) {
            bone.setHidden(hidden);
            bone.setChildrenHidden(hidden);
            for (GeoBone child : bone.getChildBones()) {
                setBranchHidden(child, hidden);
            }
        }

        /** Devuelve el hueso y su descendencia a la pose inicial (bind), anulando cualquier animación previa. */
        private static void resetBranchToBind(GeoBone bone) {
            BoneSnapshot s = bone.getInitialSnapshot();
            if (s != null) {
                bone.setRotX(s.getRotX());     bone.setRotY(s.getRotY());     bone.setRotZ(s.getRotZ());
                bone.setPosX(s.getOffsetX());  bone.setPosY(s.getOffsetY());  bone.setPosZ(s.getOffsetZ());
                bone.setScaleX(s.getScaleX()); bone.setScaleY(s.getScaleY()); bone.setScaleZ(s.getScaleZ());
            }
            for (GeoBone child : bone.getChildBones()) {
                resetBranchToBind(child);
            }
        }

        /**
         * Fuerza isReRender=true SIEMPRE: así {@code GeoObjectRenderer.actuallyRender} NO llama a
         * handleAnimations y el modelo se pinta tal cual quedó tras {@link #resetBranchToBind} (bind pose).
         * Es lo que mantiene el brazo estático (como el vanilla) en vez de reproducir el idle.
         */
        @Override
        public void actuallyRender(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel model,
                                   RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                   boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                    true, partialTick, packedLight, packedOverlay, colour);
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

        @Override
        public Color getRenderColor(GeoLayerArmorItem animatable, float partialTick, int packedLight) {
            if (passColor != null) return Color.ofOpaque(passColor & 0xFFFFFF);
            if (current == null) return Color.WHITE;
            GeoLayerArmorItem.ColorChannel ch = animatable.getColorChannel();
            if (ch == GeoLayerArmorItem.ColorChannel.NONE) return Color.WHITE;
            var visual = current.getData(DataAttachments.PLAYER_VISUAL.get());
            int rgb = switch (ch) {
                case SKIN   -> visual.getSkinColorRgb();
                case HAIR   -> visual.getHairColorRgb();
                case DETAIL -> visual.getDetailColorRgb();
                default     -> 0xFFFFFF;
            };
            return Color.ofOpaque(rgb);
        }
    }
}