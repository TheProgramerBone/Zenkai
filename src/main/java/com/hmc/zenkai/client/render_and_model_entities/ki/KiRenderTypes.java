package com.hmc.zenkai.client.render_and_model_entities.ki;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.util.function.Function;

/**
 * Lo que necesita el pipeline de ki para dibujarse: el shader de fresnel, su
 * disponibilidad, sus uniforms y los RenderType de halo y estela.
// * POR QUÉ HAY DOS RUTAS. El shader es el render principal, pero un fallo suyo (driver viejo,
 * shaderpack, error de compilación tras un cambio) no puede hacer desaparecer los proyectiles:
 * quedarían técnicas que hacen daño y no se ven. {@link #available()} es la ÚNICA pregunta que
 * hace el renderer, y responde false salvo que el shader esté compilado y enlazado.
// * ⚠ EL BUS IMPORTA. {@code RegisterShadersEvent} es un evento del MOD bus, y
 * {@code @EventBusSubscriber} usa el GAME bus por defecto: sin {@code bus = Bus.MOD} el listener
 * no se llama nunca, el shader se queda a null y cae al respaldo en silencio. El respaldo
 * funciona tan bien que el fallo no se nota en juego; por eso hay un log explícito al registrar,
 * que es la forma rápida de confirmar qué ruta está viva.
// * ADITIVO. El halo y el núcleo de la estela usan SRC_ALPHA, ONE. Es una excepción CONSCIENTE y
 * acotada al pipeline de ki: la luz que se solapa tiene que sumar hacia blanco, y esa es la
 * diferencia entre "energía" y "calcomanía". NO cambia nada del aura, cuyo crossfade aditivo se
 * eliminó por otra razón (mezclaba dos texturas por luminancia) y sigue eliminado.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiRenderTypes extends RenderType {

    // Nunca se instancia: solo heredamos para llegar a los shards protegidos de RenderStateShard.
    private KiRenderTypes() { super("", null, null, 0, false, false, null, null); }

    public static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_halo.png");
    public static final ResourceLocation TRAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail_soft.png");

    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_fresnel");

    private static ShaderInstance fresnelShader;

    // ── Shader ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                            DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        fresnelShader = instance;
                        Zenkai.LOGGER.info("[Zenkai] Shader de ki compilado: los proyectiles "
                                + "usan el fresnel de tres bandas.");
                    });
        } catch (Exception ex) {
            fresnelShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de ki NO cargó ({}). Los proyectiles usarán "
                    + "la ruta de respaldo.", ex.toString());
        }
    }

    /** Única pregunta que hace el renderer para elegir ruta. */
    public static boolean available() { return fresnelShader != null; }

    private static final Function<ResourceLocation, RenderType> FRESNEL =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_fresnel",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> fresnelShader))   // ⚠
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)              // ⚠
                            .setCullState(NO_CULL)                                       // ⚠
                            .setLightmapState(LIGHTMAP)                                  // ⚠
                            .setOverlayState(OVERLAY)                                    // ⚠
                            .setDepthTestState(LEQUAL_DEPTH_TEST)                        // ⚠
                            .setWriteMaskState(COLOR_WRITE)                              // ⚠ no escribe profundidad
                            .createCompositeState(false)));

    /** El fresnel NO muestrea textura: las bandas son geometría, no píxeles. La ResourceLocation
     *  solo entra como clave del memoize para no crear un RenderType nuevo por llamada. */
    public static RenderType fresnel() { return FRESNEL.apply(SHADER_ID); }

    /**
     * Sube los uniforms de la técnica. Hay que llamarlo ANTES de volcar el lote (ver
     * KiProjectileRenderer: emitir y hacer endBatch por proyectil), porque un ShaderInstance es
     * único y sus uniforms valen para el draw que se ejecuta, no para el que se encoló.
     * Lo que sube es FORMA de la rampa, ni un dato de orientación: el modo de banda que
     * necesitaba el eje del haz se retiró justamente porque ese era el único estado por
     * proyectil que dependía de que dos espacios de coordenadas coincidieran.
     */
    public static void setupFresnel(KiVisual v) {
        ShaderInstance s = fresnelShader;
        if (s == null) return;
        s.safeGetUniform("ZenkaiShape").set(v.bandMode());                                  // ⚠
        s.safeGetUniform("ZenkaiBands").set(v.bandCore(), v.bandBorder(), v.bandOutline()); // ⚠
        s.safeGetUniform("ZenkaiTone").set(v.coreWhite(), v.outlineDark(), v.edgeFade());   // ⚠
        s.safeGetUniform("ZenkaiWobble").set(v.wobble());                                   // ⚠
    }

    // ── Halo y estela ───────────────────────────────────────────────────────

    private static final Function<ResourceLocation, RenderType> ADDITIVE =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_additive",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)  // ⚠
                            // blur=true: son degradados continuos, no pixel art. Sin filtrado
                            // el halo escalado a tres bloques enseña sus 128 téxeles.
                            .setTextureState(new TextureStateShard(tex, true, false))       // ⚠
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)                    // ⚠ SRC_ALPHA, ONE
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> SOFT =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_soft",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** Halo y núcleo de la estela: suman luz. */
    public static RenderType additive(ResourceLocation tex) { return ADDITIVE.apply(tex); }

    /** Capa exterior teñida de la estela: se mezcla, no suma, o el color se lava a blanco. */
    public static RenderType soft(ResourceLocation tex) { return SOFT.apply(tex); }
}
