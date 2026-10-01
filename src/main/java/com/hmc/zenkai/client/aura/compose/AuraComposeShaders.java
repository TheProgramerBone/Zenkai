package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.flame.AuraFlameRenderType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Shaders y RenderTypes del sistema de aura COMPUESTO. El cuerpo FLAME reutiliza el programa de la
 * llama ({@link AuraFlameRenderType}); SOFT y DENSE tienen el suyo. Mismo patrón try/catch que
 * {@code AuraFlameRenderType}: un shader que no compila deja su programa en null y
 * {@link #available()} hace que el despacho caiga a la llama sola o al legacy, nunca a un crash.
 * <p>
 * Los RenderTypes de cuerpo NO se usan con el BufferSource: {@link AuraBodyMesh} dibuja un VBO
 * estático entre {@code setupRenderState()} y {@code clearRenderState()} del RenderType, que así
 * solo aporta el estado (shader, ruido, mezcla premultiplicada, depth test sin escritura).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraComposeShaders extends RenderType {
    // Nunca se instancia: solo heredamos para llegar a los shards protegidos de RenderStateShard.
    private AuraComposeShaders() { super("", null, null, 0, false, false, null, null); }

    private static final ResourceLocation SOFT_ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_body_soft");
    private static final ResourceLocation DENSE_ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_body_dense");
    private static final ResourceLocation FLOW_ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_body_flow");

    private static @Nullable ShaderInstance softShader;
    private static @Nullable ShaderInstance denseShader;
    private static @Nullable ShaderInstance flowShader;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SOFT_ID,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> softShader = s);
        } catch (Exception ex) {
            softShader = null;
            logFailure(SOFT_ID, ex);
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), DENSE_ID,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> denseShader = s);
        } catch (Exception ex) {
            denseShader = null;
            logFailure(DENSE_ID, ex);
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FLOW_ID,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> flowShader = s);
        } catch (Exception ex) {
            flowShader = null;
            logFailure(FLOW_ID, ex);
        }
    }

    private static void logFailure(ResourceLocation id, Exception ex) {
        Zenkai.LOGGER.error("[Zenkai] El shader {} NO cargó ({}). El aura compuesta no se dibuja "
                + "hasta que compile (se usa la llama sola o el legacy).", id, ex.toString());
    }

    /** ¿Compilaron los programas propios del compuesto? (El de la llama lo pregunta el despacho aparte.) */
    public static boolean available() { return softShader != null && denseShader != null && flowShader != null; }

    public static @Nullable ShaderInstance shader(AuraBodyKind kind) {
        return switch (kind) {
            case FLAME -> AuraFlameRenderType.shellShaderInstance();
            case SOFT -> softShader;
            case DENSE -> denseShader;
            case FLOW -> flowShader;
        };
    }

    /** Mezcla PREMULTIPLICADA, la misma que la llama (su shard es privado, se replica). */
    private static final TransparencyStateShard PREMULTIPLIED =
            new TransparencyStateShard("zenkai_aura_body_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_noise.png");

    private static RenderType body(String name, java.util.function.Supplier<ShaderInstance> shader) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS,
                256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(shader))
                        .setTextureState(new TextureStateShard(NOISE_TEX, true, false))
                        .setTransparencyState(PREMULTIPLIED)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static final RenderType SOFT = body("zenkai_aura_body_soft", () -> softShader);
    private static final RenderType DENSE = body("zenkai_aura_body_dense", () -> denseShader);
    private static final RenderType FLOW = body("zenkai_aura_body_flow", () -> flowShader);

    /** Estado de render del cuerpo de cada familia (la llama usa el RenderType de siempre). */
    public static RenderType bodyType(AuraBodyKind kind) {
        return switch (kind) {
            case FLAME -> AuraFlameRenderType.shell();
            case SOFT -> SOFT;
            case DENSE -> DENSE;
            case FLOW -> FLOW;
        };
    }

    // ── Motes ───────────────────────────────────────────────────────────────

    /** Atlas de motes, generado por tools/gen_aura_motes.py (única fuente del PNG). */
    public static final ResourceLocation MOTES_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_motes.png");

    /** Billboards de motes: NEAREST (pixel art, sin suavizar), translúcido normal — nunca aditivo:
     *  cien chispas aditivas encima del aura lavan a blanco, la regla del aura. Sin lightmap: son
     *  energía, no se oscurecen de noche. Sin escritura de depth, como el resto del aura. */
    private static final RenderType MOTES = RenderType.create("zenkai_aura_motes",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(MOTES_TEX, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType motes() { return MOTES; }

    // ── Rayos y wisps ───────────────────────────────────────────────────────

    /** Cintas sin textura (rayos, wisps): posición + color, translúcido normal, sin escritura de
     *  depth, sin culling (las cintas se ven por las dos caras). Bordes duros a propósito: el
     *  rayo del anime y el pixel art del mod no tienen degradado continuo. */
    private static final RenderType RIBBONS = RenderType.create("zenkai_aura_ribbons",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType ribbons() { return RIBBONS; }

}
