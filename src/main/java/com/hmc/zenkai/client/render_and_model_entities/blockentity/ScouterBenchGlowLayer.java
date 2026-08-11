package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.content.block.ScouterBenchBlock;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Luz del banco. Emisiva SOLO mientras WORKING está puesto.
 *
 * El estado se lee del BLOCKSTATE y no del block entity a propósito: WORKING ya viaja al
 * cliente por la sincronización normal de bloques (por eso vive ahí y no solo en el BE), así
 * que la capa no necesita ningún paquete propio ni saber nada del trabajo en curso.
 *
 * ⚠ VERIFICAR en GeckoLib 4.8.4:
 *   - AutoGlowingGeoLayer busca la máscara en <textura>_glowmask.png, es decir
 *     textures/block/scouter_bench_glowmask.png. Si la versión usa otro sufijo, es lo único
 *     que cambia: el asset se renombra, este archivo no.
 *   - Firma de GeoRenderLayer#render(PoseStack, T, BakedGeoModel, RenderType,
 *     MultiBufferSource, VertexConsumer, float, int, int).
 */
public class ScouterBenchGlowLayer extends AutoGlowingGeoLayer<ScouterBenchBlockEntity> {

    public ScouterBenchGlowLayer(GeoRenderer<ScouterBenchBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, ScouterBenchBlockEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        BlockState state = animatable.getBlockState();
        if (!state.hasProperty(ScouterBenchBlock.WORKING)
                || !state.getValue(ScouterBenchBlock.WORKING)) {
            return;
        }
        super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer,
                partialTick, packedLight, packedOverlay);
    }
}