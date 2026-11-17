package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.content.item.KiChamberBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Renderer del ítem de la cámara. Como el del banco, su único trabajo extra es CENTRAR el modelo:
 * el geo va de y=0 a y=32 con el origen en la base, y vanilla dibuja los ítems alrededor del
 * centro de un cubo de 16. Se baja una vez aquí, en unidades de modelo, en vez de compensarlo en
 * cada transform del JSON.
 */
public class KiChamberItemRenderer extends GeoItemRenderer<KiChamberBlockItem> {

    /** Mitad de la altura del modelo (32 px). */
    private static final float MODEL_HALF_HEIGHT = 16f;

    public KiChamberItemRenderer() {
        super(new KiChamberItemModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0f, -MODEL_HALF_HEIGHT / 16f, 0f);
        super.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
