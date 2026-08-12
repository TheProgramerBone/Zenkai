package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.content.item.ScouterBenchBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Renderer del ítem del banco. Su único trabajo extra es CENTRAR el modelo.
 * El geo mide 16x25x16 con el origen en la base (y de 0 a 25), como cualquier modelo de
 * Blockbench hecho para un bloque. Vanilla, en cambio, renderiza los ítems alrededor del
 * CENTRO de un cubo de 16: un modelo de bloque normal va de -8 a 8 en los tres ejes.
 * Sin corregirlo, el banco aparecería hundido medio bloque y desbordando por arriba en todos
 * los contextos, y habría que compensarlo en cada transform del JSON por separado — seis
 * traslaciones distintas para decir lo mismo, que se desincronizan a la primera.
 * Aquí se baja UNA vez, en unidades del modelo, y los display transforms del JSON quedan
 * siendo los de un bloque vanilla con la escala corregida por la altura extra.
 * ⚠ VERIFICAR en GeckoLib 4.8.4: firma de GeoItemRenderer#renderByItem(ItemStack,
 * ItemDisplayContext, PoseStack, MultiBufferSource, int, int). Si cambió, el cuerpo del
 * procedimiento sirve igual: lo único que hace es un translate antes de delegar.
 */
public class ScouterBenchItemRenderer extends GeoItemRenderer<ScouterBenchBlockItem> {

    /**
     * Mitad de la altura del modelo, en píxeles de modelo. Sale de los bounds del geo:
     * y va de 0 a 25, así que su centro está en 12,5. Si cambia la altura del banco, este
     * número es lo único que hay que tocar.
     */
    private static final float MODEL_HALF_HEIGHT = 12.5f;

    public ScouterBenchItemRenderer() {
        super(new ScouterBenchItemModel());
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