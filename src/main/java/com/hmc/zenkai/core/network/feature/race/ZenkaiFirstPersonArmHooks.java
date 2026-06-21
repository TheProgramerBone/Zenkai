package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Brazo en 1ª persona usando el MISMO modelo del cuerpo (reusado): oculta todos los huesos
 * menos el del brazo y lo renderiza en la pose que da RenderArmEvent. Así hereda automáticamente
 * cualquier cambio de modelo (transformaciones, género, etc.) sin assets extra.
 *
 * ⚠ API de GeckoLib 4.8.4 a verificar al compilar:
 *   · GeoObjectRenderer (constructor + firma de render(...))
 *   · GeoModel.getBakedModel(...) / BakedGeoModel.topLevelBones()
 *   · GeoBone.setHidden(boolean) / setChildrenHidden(boolean)
 *   · Color.ofOpaque(rgb)  (mismo factory que GeoLayerArmorRenderer)
 *
 * ⚠ ALINEACIÓN: el brazo se dibuja en la posición que ocupa DENTRO del modelo (offset respecto
 *   al origen, a la altura del torso). Ajusta OFF_* / SCALE hasta que calce con la mano vanilla.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiFirstPersonArmHooks {

    private ZenkaiFirstPersonArmHooks() {}

    // Ajustes de alineación. MUTABLES en runtime vía el comando /zenkaiarm (calibración en vivo).
    // Cuando te gusten, copia estos valores y vuelve a ponerlos final si quieres.
    public static float OFF_X = -1.55f;   // bloques: +/- mueve lateral
    public static float OFF_Y = 0.65f;   // bloques: +/- mueve vertical
    public static float OFF_Z = -0.5f;  // bloques: +/- acerca/aleja de cámara
    public static float SCALE = 1.0f;   // tamaño
    public static float ROT_X = 0.0f;   // grados: inclinar (pitch)
    public static float ROT_Y = 0.0f;   // grados: girar (yaw) — útil si el brazo apunta a cámara
    public static float ROT_Z = 0.0f;   // grados: rodar (roll)

    // Nombres de los huesos raíz del brazo en tu geo (convención GeckoLib).
    private static final String RIGHT_ARM_BONE = "bipedRightArm";
    private static final String LEFT_ARM_BONE  = "bipedLeftArm";

    private static ArmRenderer RENDERER; // cache perezoso (en hilo de render)

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent e) {
        Player player = e.getPlayer();
        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());
        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!visual.shouldRenderRaceSkin() || !stats.isRaceChosen()) return;

        // El cuerpo lleva el brazo; cualquier slot vale porque el modelo es el mismo.
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

        ArmRenderer() { super(new GeoLayerArmorModel()); }

        void renderArm(GeoLayerArmorItem item, Player player, HumanoidArm arm,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
            this.current = player;

            GeoModel<GeoLayerArmorItem> model = getGeoModel();
            BakedGeoModel baked = model.getBakedModel(model.getModelResource(item));
            String armRoot = (arm == HumanoidArm.RIGHT) ? RIGHT_ARM_BONE : LEFT_ARM_BONE;

            // El baked es COMPARTIDO (1ª/3ª persona, inventario). Forzamos el estado de visibilidad
            // de toda la rama de forma determinista cada frame: solo la cadena del brazo visible.
            // No nos fiamos de lo que dejó la pasada anterior (de ahí que desapareciera tras F5).
            try {
                for (GeoBone b : baked.topLevelBones()) {
                    setBranchHidden(b, !b.getName().equals(armRoot));
                }

                RenderType rt = RenderType.entityTranslucent(model.getTextureResource(item));

                // Pivote del hombro en el geo (px). bipedRightArm=[-5,22,0], bipedLeftArm=[5,22,0].
                float pivotX = (arm == HumanoidArm.RIGHT) ? -5f : 5f;
                float pivotY = 22f;

                poseStack.pushPose();
                // Perillas de ajuste fino (en bloques), se aplican en el espacio ya corregido.
                poseStack.translate(OFF_X, OFF_Y, OFF_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(ROT_X));
                poseStack.mulPose(Axis.YP.rotationDegrees(ROT_Y));
                poseStack.mulPose(Axis.ZP.rotationDegrees(ROT_Z));
                poseStack.scale(SCALE, SCALE, SCALE);
                // GeckoLib usa Y hacia arriba; el espacio de RenderArmEvent (ModelPart) usa Y hacia
                // abajo. Volteamos SOLO en Y (el flip en X que hace GeoEntityRenderer aquí no aplica).
                poseStack.scale(1f, -1f, 1f);
                // ...y cancelamos el pivote del hombro para llevarlo al origen de la mano.
                poseStack.translate(-pivotX / 16f, -pivotY / 16f, 0f);
                render(poseStack, item, buffers, rt, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            } finally {
                // Restaurar TODA la rama a visible, pase lo que pase (incluido si render lanzó).
                for (GeoBone b : baked.topLevelBones()) {
                    setBranchHidden(b, false);
                }
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

        @Override
        public Color getRenderColor(GeoLayerArmorItem animatable, float partialTick, int packedLight) {
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