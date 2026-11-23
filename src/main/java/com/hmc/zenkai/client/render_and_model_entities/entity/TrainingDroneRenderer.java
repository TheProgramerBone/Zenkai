package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.TrainingDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Dron de entrenamiento (Fase 4 de la 0.7.0). Modelo y texturas de {@code tools/gen_training_drone.py}:
 * <ul>
 *   <li>Carcasa: 4 texturas de desgaste ({@code training_drone_0..3.png}), según los usos que le quedan al ítem.</li>
 *   <li>Ojo y luces: capa emisiva aparte ({@code RenderType.eyes}) con el color del ESTADO: ámbar = blanco
 *       pendiente, verde = ya golpeado, rojo = agresivo. Así el progreso de la ronda se ve sobre cada dron
 *       sin tocar la carcasa (pedido del diseño: «iconos o colores sobre cada entidad»).</li>
 * </ul>
 */
public class TrainingDroneRenderer extends GenericGeoRenderer<TrainingDroneEntity> {

    public TrainingDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(), 0.25f);
        addRenderLayer(new EyeLayer(this));
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/" + path + ".png");
    }

    private static final ResourceLocation[] WEAR = {
            tex("training_drone_0"), tex("training_drone_1"), tex("training_drone_2"), tex("training_drone_3")};
    private static final ResourceLocation EYE_PENDING = tex("training_drone_eye_pending");
    private static final ResourceLocation EYE_HIT = tex("training_drone_eye_hit");
    private static final ResourceLocation EYE_AGGRESSIVE = tex("training_drone_eye_aggressive");

    private static final class Model extends GenericGeoModel<TrainingDroneEntity> {
        Model() { super("training_drone", "training_drone_0", "training_drone", false, false); }

        @Override
        public ResourceLocation getTextureResource(TrainingDroneEntity e) {
            return WEAR[Math.max(0, Math.min(3, e.wearStage()))];
        }
    }

    private static final class EyeLayer extends GeoRenderLayer<TrainingDroneEntity> {
        EyeLayer(GeoRenderer<TrainingDroneEntity> renderer) { super(renderer); }

        @Override
        public void render(PoseStack poseStack, TrainingDroneEntity e, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            ResourceLocation eye = switch (e.droneState()) {
                case TrainingDroneEntity.STATE_HIT -> EYE_HIT;
                case TrainingDroneEntity.STATE_AGGRESSIVE -> EYE_AGGRESSIVE;
                default -> EYE_PENDING;
            };
            RenderType glow = RenderType.eyes(eye);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, e, glow,
                    bufferSource.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, -1);
        }
    }
}
