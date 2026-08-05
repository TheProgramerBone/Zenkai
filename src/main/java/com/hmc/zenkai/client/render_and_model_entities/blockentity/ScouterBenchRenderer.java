package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Banco de scouter + el scouter que tenga dentro, tumbado sobre la mesa y girando despacio.
 *
 * Se renderiza el ItemStack REAL, no una textura aparte: así el tinte del cristal (el color
 * handler del icono) y el modelo agrietado (el override de "broken") se aplican solos, sin
 * duplicar aquí la lógica que ya vive en ScouterItemColors y en scouter.json.
 */
public class ScouterBenchRenderer extends GeoBlockRenderer<ScouterBenchBlockEntity> {

    public ScouterBenchRenderer(Context context) {
        super(new ScouterBenchModel());
    }

    @Override
    public void render(ScouterBenchBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(be, partialTick, poseStack, buffers, packedLight, packedOverlay);

        ItemStack stack = be.scouter();
        if (stack.isEmpty()) return;

        float spin = be.getLevel() == null
                ? 0f
                : (be.getLevel().getGameTime() + partialTick) * 2f;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.95, 0.5);
        poseStack.scale(0.6f, 0.6f, 0.6f);
        poseStack.rotateAround(new Quaternionf().rotationY((float) Math.toRadians(spin)), 0, 0, 0);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.GROUND, packedLight, packedOverlay,
                poseStack, buffers, be.getLevel(), 0);

        poseStack.popPose();
    }
}