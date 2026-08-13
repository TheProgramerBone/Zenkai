package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.ScouterBenchBlock;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Luz del banco. Emisiva SOLO mientras WORKING está puesto.
 * POR QUÉ NO ES UN AutoGlowingGeoLayer, que sería lo obvio: esa clase, al construir la
 * textura emisiva, BORRA de la textura base los píxeles marcados en el glowmask. Lo hace para
 * que no se pinten dos veces y el emisivo no salga quemado, y da por supuesto que la capa se
 * dibuja SIEMPRE, así que el hueco siempre queda tapado.
 * Aquí la capa se salta cuando el banco está parado, y entonces esos huecos quedaban al aire:
 * el panel se veía calado con la máquina apagada y correcto solo mientras trabajaba.
 * Esta versión no toca la textura base. Dibuja el modelo otra vez usando el GLOWMASK como
 * textura —transparente salvo en los píxeles que emiten— con un RenderType emisivo. Apagado
 * se ve la base entera; encendido se le suma el brillo encima.
 * RenderType.eyes() es aditivo y se salta la iluminación del mundo, que es justo el
 * comportamiento de un piloto encendido: se ve igual de noche y en una cueva.
 * ⚠ VERIFICAR en GeckoLib 4.8.4: firma de GeoRenderer#reRender(BakedGeoModel, PoseStack,
 * MultiBufferSource, T, RenderType, VertexConsumer, float, int, int, int). Si el último
 * parámetro de color no existe en tu versión, se quita.
 */
public class ScouterBenchGlowLayer extends GeoRenderLayer<ScouterBenchBlockEntity> {

    /** Máscara emisiva. Es una TEXTURA aquí, no una máscara que GeckoLib interprete. */
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/block/scouter_bench_glowmask.png");

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

        RenderType glow = RenderType.eyes(GLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, -1);
    }
}