package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.ScouterBenchBlock;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Luz del banco. Emisiva SOLO mientras WORKING está puesto.
 * El estado se lee del BLOCKSTATE y no del block entity a propósito: WORKING ya viaja al
 * cliente por la sincronización normal de bloques (por eso vive ahí y no solo en el BE), así
 * que la capa no necesita ningún paquete propio ni saber nada del trabajo en curso.
 * DOS SALVAGUARDAS, cada una por un fallo que ya ocurrió:
 *   - hasMask(): sin scouter_bench_glowmask.png, GeckoLib construye una textura emisiva vacía
 *     y la pinta sobre el modelo entero, que se volvía NEGRO justo al empezar a trabajar. Si
 *     la máscara falta, la capa no dibuja nada y el banco se ve normal, sin brillo.
 *   - warmUp(): la textura emisiva se genera la primera vez que se pide, y como esta capa
 *     solo se dibuja con WORKING en true, esa primera vez caía en el frame de arranque y el
 *     modelo parpadeaba. Precalentándola desde el render normal, el coste se paga cuando el
 *     banco entra en pantalla.
 * ⚠ VERIFICAR en GeckoLib 4.8.4:
 *   - AutoGlowingGeoLayer busca la máscara en <textura>_glowmask.png. Si la versión usa otro
 *     sufijo, se renombra el asset y la constante MASK; el resto no cambia.
 *   - AutoGlowingTexture.getEmissiveResource(ResourceLocation).
 *   - Firma de GeoRenderLayer#render(PoseStack, T, BakedGeoModel, RenderType,
 *     MultiBufferSource, VertexConsumer, float, int, int).
 */
public class ScouterBenchGlowLayer extends AutoGlowingGeoLayer<ScouterBenchBlockEntity> {

    /** Textura base del bloque. La emisiva se deriva de ella. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/block/scouter_bench.png");

    /** Máscara emisiva. Sin ella la capa se apaga entera. */
    private static final ResourceLocation MASK =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/block/scouter_bench_glowmask.png");

    private static Boolean maskPresent;
    private static boolean warmed;

    public ScouterBenchGlowLayer(GeoRenderer<ScouterBenchBlockEntity> renderer) {
        super(renderer);
    }

    /**
     * ¿Existe la máscara? Se consulta UNA vez y se recuerda: un getResource por frame y por
     * banco es caro para una respuesta que no cambia en toda la sesión.
         * ⚠ El caché no se invalida con F3+T. Si añades la máscara con el juego abierto, hay que
     * reiniciar. Aceptable para algo que solo cambia mientras se desarrolla.
     */
    private static boolean hasMask() {
        if (maskPresent == null) {
            maskPresent = Minecraft.getInstance().getResourceManager()
                    .getResource(MASK).isPresent();
            if (!maskPresent) {
                Zenkai.LOGGER.warn("[Zenkai] Falta {}: la capa emisiva del banco queda apagada.",
                        MASK);
            }
        }
        return maskPresent;
    }

    /**
     * Fuerza la creación de la textura emisiva ANTES de que haga falta. Lo llama
     * ScouterBenchRenderer en cada render, no solo al trabajar: ese es justo el punto.
     */
    public static void warmUp() {
        if (warmed || !hasMask()) return;
        warmed = true;
        AutoGlowingTexture.getEmissiveResource(TEXTURE);
    }

    @Override
    public void render(PoseStack poseStack, ScouterBenchBlockEntity animatable,
                       BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!hasMask()) return;

        BlockState state = animatable.getBlockState();
        if (!state.hasProperty(ScouterBenchBlock.WORKING)
                || !state.getValue(ScouterBenchBlock.WORKING)) {
            return;
        }
        super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer,
                partialTick, packedLight, packedOverlay);
    }
}