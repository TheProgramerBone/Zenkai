package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;

import java.util.function.Function;

/**
 * Lo que necesita el pipeline de VFX de ki para dibujarse: el shader de energía (STEP 4-8 de la
 * auditoría — coordinate spaces, Fresnel, depth/transparency), su disponibilidad, sus uniforms y
 * los RenderType de halo/estela/depuración.
 *
 * POR QUÉ HAY DOS RUTAS. El shader es el render principal, pero un fallo suyo (driver viejo,
 * shaderpack, error de compilación tras un cambio) no puede hacer desaparecer los proyectiles:
 * quedarían técnicas que hacen daño y no se ven. {@link #available()} es la ÚNICA pregunta que
 * hace el renderer, y responde false salvo que el shader esté compilado y enlazado.
 *
 * ⚠ EL BUS IMPORTA. {@code RegisterShadersEvent} es un evento del MOD bus, y
 * {@code @EventBusSubscriber} usa el GAME bus por defecto: sin {@code bus = Bus.MOD} el listener
 * no se llama nunca, el shader se queda a null y cae al respaldo en silencio.
 *
 * ALFA PREMULTIPLICADO CON EMISIÓN (2026-09-24, tercera iteración de la mezcla). Historia:
 * 1) Aditivo (SRC_ALPHA, ONE) en halo/núcleo/estela: cada capa solapada sumaba hacia blanco — con
 *    varias técnicas, la cámara cerca o el jugador dentro de su técnica, la escena se lavaba.
 * 2) Mezcla normal (el modelo de dragonminez/dbrebirth): arregló el lavado, pero una capa
 *    translúcida solo TIÑE lo de detrás, no emite — sobre el cielo claro de día todo se veía
 *    pastel (vídeo 09-38-28; de noche, en cambio, saturado y legible).
 * 3) Ahora: ONE, ONE_MINUS_SRC_ALPHA con el color ya multiplicado por su alfa en el shader, y un
 *    alfa de salida que es SOLO la parte que tapa. Cada capa decide cuánto emite (suma) y cuánto
 *    cubre (tapa) — ver ki_glow.fsh y la emisión por banda de ki_energy.fsh. Es la mezcla
 *    estándar de VFX de energía, y ajustable en vivo con /zkvfx (KiVfxTuning.EMIT_*).
 * No toca el aura, que tiene su propia mezcla.
 *
 * DEPTH/TRANSPARENCY (STEP 7 de la auditoría). El cuerpo (FRESNEL/FRESNEL_CULLED) usa
 * TRANSLUCENT_TRANSPARENCY con LEQUAL_DEPTH_TEST y escritura de color SOLO (sin depth-write): es
 * lo correcto para geometría translúcida que se solapa consigo misma (envolvente+cáscara+núcleo
 * del mismo proyectil) sin z-fighting contra ELLA MISMA, a costa de que dos técnicas translúcidas
 * distintas se ordenen por cuándo entran al buffer y no por profundidad exacta — el mismo
 * compromiso que ya usa cualquier geometría translúcida de Minecraft (agua, cristal de color),
 * no una decisión nueva de este pipeline.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiVfxRenderTypes extends RenderType {

    private KiVfxRenderTypes() { super("", null, null, 0, false, false, null, null); }

    public static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_halo.png");
    public static final ResourceLocation TRAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail_soft.png");
    /** ki_energy.fsh la sample como Sampler0 para la capa de detalle opcional
     *  (KiVfxProfile.Shell.detailStrength). */
    public static final ResourceLocation DETAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_detail.png");
    /** Variante de la capa de detalle para KiVfxProfile.DetailTexture.LAVA (Supernova): placas
     *  agrietadas en vez de ruido genérico — ver tools/gen_ki_fx.py::gen_detail_lava(). */
    public static final ResourceLocation DETAIL_TEXTURE_LAVA =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_detail_lava.png");
    /** Textura de respaldo cuando el shader no compiló. */
    public static final ResourceLocation BALL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");

    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_energy");

    private static ShaderInstance energyShader;

    private static final ResourceLocation GLOW_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_glow");
    /** Halo, núcleo explícito, estela y rayos — ver ki_glow.fsh. Si no compila, esas capas caen
     *  al shader de entidad vanilla (sin premultiplicar: más brillantes, pero visibles). */
    private static ShaderInstance glowShader;

    // ── Shader ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                            DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        energyShader = instance;
                        Zenkai.LOGGER.info("[Zenkai] Shader de VFX de ki compilado: los "
                                + "proyectiles usan el pipeline de energía de tres bandas.");
                    });
        } catch (Exception ex) {
            energyShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de VFX de ki NO cargó ({}). Los proyectiles "
                    + "usarán la ruta de respaldo.", ex.toString());
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), GLOW_SHADER_ID,
                            DefaultVertexFormat.NEW_ENTITY),
                    instance -> glowShader = instance);
        } catch (Exception ex) {
            glowShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader ki_glow NO cargó ({}); halo y estela usan el "
                    + "de entidad vanilla.", ex.toString());
        }
    }

    // ── Mezcla premultiplicada (ver cabecera) ───────────────────────────────

    private static void premultipliedBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void defaultBlend() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** Cuerpo (ki_energy): la emisión la sube setupEnergy por técnica. */
    private static final TransparencyStateShard PREMULTIPLIED =
            new TransparencyStateShard("zenkai_ki_premultiplied",
                    KiVfxRenderTypes::premultipliedBlend, KiVfxRenderTypes::defaultBlend);

    /** Capas ki_glow: la emisión se sube AQUÍ, en el setup del lote, porque las dos capas (glow y
     *  soft) comparten ShaderInstance y cada una tiene la suya. El setup corre antes de que el
     *  draw aplique los uniforms, así que el valor llega al draw correcto. */
    private static TransparencyStateShard glowBlend(String name, KiVfxTuning.Param emit) {
        return new TransparencyStateShard(name, () -> {
            premultipliedBlend();
            if (glowShader != null) {
                glowShader.safeGetUniform("ZenkaiEmit").set(KiVfxTuning.get(emit, (KiTechniqueType) null));
            }
        }, KiVfxRenderTypes::defaultBlend);
    }

    private static final TransparencyStateShard GLOW_BLEND = glowBlend("zenkai_ki_glow_blend", KiVfxTuning.Param.EMIT_GLOW);
    private static final TransparencyStateShard SOFT_BLEND = glowBlend("zenkai_ki_soft_blend", KiVfxTuning.Param.EMIT_SOFT);

    private static final ShaderStateShard GLOW_SHADER = new ShaderStateShard(() ->
            glowShader != null ? glowShader : GameRenderer.getRendertypeEntityTranslucentEmissiveShader());

    /** Única pregunta que hace el renderer para elegir ruta. */
    public static boolean available() { return energyShader != null; }

    private static final Function<ResourceLocation, RenderType> ENERGY =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_energy",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> energyShader))
                            // Sampler0: capa de detalle opcional. blur=true porque, igual que
                            // halo/estela, es un degradado continuo, no pixel art.
                            .setTextureState(new TextureStateShard(tex, true, false))
                            .setTransparencyState(PREMULTIPLIED)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** Igual que {@link #ENERGY} pero con culling normal — ver KiVfxProfile.backfaceCull. */
    private static final Function<ResourceLocation, RenderType> ENERGY_CULLED =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_energy_culled",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> energyShader))
                            .setTextureState(new TextureStateShard(tex, true, false))
                            .setTransparencyState(PREMULTIPLIED)
                            .setCullState(CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType energy() { return ENERGY.apply(DETAIL_TEXTURE); }

    public static RenderType energy(boolean backfaceCull) {
        return backfaceCull ? ENERGY_CULLED.apply(DETAIL_TEXTURE) : ENERGY.apply(DETAIL_TEXTURE);
    }

    /** @param p de dónde sale qué textura de detalle usar (KiVfxProfile.Shell.detailTexture) —
     *  ENERGY/ENERGY_CULLED ya están memoizados por textura, así que pedir una distinta no cuesta
     *  un RenderType nuevo cada vez, solo la primera vez que se pide esa combinación. */
    public static RenderType energy(KiVfxProfile p) {
        ResourceLocation tex = p.shell().detailTexture() == KiVfxProfile.DetailTexture.LAVA
                ? DETAIL_TEXTURE_LAVA : DETAIL_TEXTURE;
        return p.backfaceCull() ? ENERGY_CULLED.apply(tex) : ENERGY.apply(tex);
    }

    /**
     * Sube los uniforms de la técnica. Hay que llamarlo ANTES de volcar el lote, porque un
     * ShaderInstance es único y sus uniforms valen para el draw que se ejecuta, no para el que se
     * encoló.
     * Lo que sube es FORMA de la rampa, nunca un dato de orientación por proyectil: el eje de
     * vuelo que el fragment necesita (ZenkaiAxial) lo deriva el propio shader transformando el
     * vector local constante (0,0,1) — ver "EJE DE VUELO" en ki_energy.vsh — así que no hay
     * ningún uniform de orientación que se pueda desincronizar entre dos proyectiles distintos
     * dibujados en el mismo lote.
     */
    public static void setupEnergy(KiVfxProfile p) { setupEnergy(p, false, 1f); }

    public static void setupEnergy(KiVfxProfile p, boolean frozenAnim) { setupEnergy(p, frozenAnim, 1f); }

    /**
     * @param proximity ver {@link #proximity(double, float)} — 1 = cámara lejos, 0 = pegada a la
     *                  propia superficie.
     */
    public static void setupEnergy(KiVfxProfile p, boolean frozenAnim, float proximity) {
        ShaderInstance s = energyShader;
        if (s == null) return;
        KiVfxDebugMode dbg = KiVfxDebugMode.current();
        KiVfxProfile.Shell sh = p.shell();
        float wobble = dbg.forceFlatBands() ? 0f : sh.wobble();
        s.safeGetUniform("ZenkaiShape").set(sh.bandMode());
        s.safeGetUniform("ZenkaiBands").set(sh.bandCore(), sh.bandBorder(), sh.bandOutline());
        KiTechniqueType type = KiVfxProfile.typeOf(p);
        float coreWhite = Mth.clamp(sh.coreWhite() * KiVfxTuning.get(KiVfxTuning.Param.CORE_WHITE_MUL, type), 0f, 1f);
        s.safeGetUniform("ZenkaiTone").set(coreWhite, sh.outlineDark(), sh.edgeFade());
        s.safeGetUniform("ZenkaiEmit").set(KiVfxTuning.get(KiVfxTuning.Param.EMIT_BODY, type),
                KiVfxTuning.get(KiVfxTuning.Param.EMIT_CORE, type));
        s.safeGetUniform("ZenkaiBloomBody").set(KiVfxTuning.get(KiVfxTuning.Param.BLOOM_BODY, type));
        s.safeGetUniform("ZenkaiWobble").set(wobble);
        s.safeGetUniform("ZenkaiDetail").set(sh.detailStrength());
        s.safeGetUniform("ZenkaiFrozenTime").set(frozenAnim || dbg.forceFlatBands() ? 0f : -1f);
        s.safeGetUniform("ZenkaiProximity").set(proximity);
        s.safeGetUniform("ZenkaiAxial").set(p.axial() ? 1f : 0f);
        s.safeGetUniform("ZenkaiDebugMode").set(dbg.shaderMode());
        s.safeGetUniform("ZenkaiBloomMode").set(KiVfxFrameQueue.bloomPass() ? 1f : 0f);
    }

    /** Cuántos radios propios de la técnica hacen falta entre cámara y superficie para que el
     *  Fresnel de bandas se lea IGUAL que desde lejos (proximity 1.0, sin dampen). Ver "BUG DE
     *  CÁMARA" en ki_energy.fsh: un tubo largo visto de cerca abre en abanico, por perspectiva,
     *  los rayos de vista a lo largo de su longitud. */
    private static final float PROXIMITY_SAFE_RADII = 7f;

    /**
     * @param camDistance distancia REAL en bloques entre la cámara y el centro de la técnica.
     * @param worldRadius {@link KiVfxProfile#worldRadius(float)} — el radio visual YA escalado.
     * @return 0 (cámara pegada a la superficie) .. 1 (a PROXIMITY_SAFE_RADII radios o más).
     */
    public static float proximity(double camDistance, float worldRadius) {
        if (worldRadius <= 1.0e-4f) return 1f;
        return Mth.clamp((float) (camDistance / (worldRadius * PROXIMITY_SAFE_RADII)), 0f, 1f);
    }

    /** Por debajo de esta fracción de su propio medio-lado, un billboard se apaga por completo; a
     *  partir de {@link #BILLBOARD_FADE_FULL} se ve entero. */
    private static final float BILLBOARD_FADE_ZERO = 0.35f;
    private static final float BILLBOARD_FADE_FULL = 0.80f;

    /**
     * Atenuación de un billboard cara-a-cámara (halo, fuente de bloom) cuando la cámara se mete
     * DENTRO de su propio radio. Un billboard es solo un sustituto plano de una luz esférica, y
     * deja de tener sentido cuando lo envuelve la cámara: a menos de su medio-lado de distancia
     * ya cubre más que la pantalla entera, y la textura del halo (sus anillos de caída) se estira
     * hasta leerse como un disco blanco con anillos concéntricos — el artefacto de "cámara dentro
     * de BARRIER". La cáscara de la técnica NO pasa por aquí: su visibilidad desde dentro la
     * decide su propio culling (KiVfxProfile.backfaceCull); esto solo retira el SUSTITUTO plano.
     * Umbrales bajos a propósito: la carga en primera persona (KiVfxChargeRenderer.pushOutOfCamera
     * la deja a radio+0.35 de la cámara) queda casi siempre por encima y conserva su halo.
     *
     * @param camDistance distancia cámara–centro del billboard, en bloques.
     * @param half        medio-lado del billboard, en bloques.
     * @return 0 (cámara dentro, no dibujar) .. 1 (fuera, sin cambio).
     */
    public static float billboardCameraFade(double camDistance, float half) {
        if (half <= 1.0e-4f) return 1f;
        float t = Mth.clamp((float) ((camDistance / half - BILLBOARD_FADE_ZERO)
                / (BILLBOARD_FADE_FULL - BILLBOARD_FADE_ZERO)), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ── Halo, estela y depuración ───────────────────────────────────────────

    private static final Function<ResourceLocation, RenderType> GLOW =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_ki_glow",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(GLOW_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, false))
                            .setTransparencyState(GLOW_BLEND)
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
                            .setShaderState(GLOW_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, false))
                            .setTransparencyState(SOFT_BLEND)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** Halo, núcleo explícito y núcleo de la estela: capa clara, MUY emisiva (KiVfxTuning.EMIT_GLOW,
     *  ver "ALFA PREMULTIPLICADO" en la cabecera). */
    public static RenderType glow(ResourceLocation tex) { return GLOW.apply(tex); }

    /** Capa exterior teñida de la estela y rayos: POCO emisiva (KiVfxTuning.EMIT_SOFT) — tapa más
     *  de lo que suma, o el color se lava a blanco. */
    public static RenderType soft(ResourceLocation tex) { return SOFT.apply(tex); }

    /**
     * DEBUG-ONLY: ejecuta {@code draw} con el rasterizador en modo línea ({@code GL_LINE}) en vez
     * de relleno — la malla real dibujada como esqueleto, sin que ningún shader/textura la tape
     * (KiVfxDebugMode.WIREFRAME, ver STEP 11 de la auditoría). {@code draw} debe emitir Y VOLCAR
     * el lote (llamar {@code buffer.endBatch(...)}) dentro del propio Runnable — el modo línea
     * solo afecta a los draw calls de GL que ocurran mientras está activo, y
     * {@code MultiBufferSource.BufferSource} difiere el draw real hasta el `endBatch`.
     * {@code glPolygonMode} directo, no un {@code RenderStateShard}, porque
     * {@code RenderType.CompositeState} no expone un modo de relleno de polígono como shard —
     * única excepción del pipeline a "no pelear con el motor" (ver la nota de bloom en
     * .claude/pendiente/technique-visuals-referencia-mods.md), aceptable por ser EXCLUSIVAMENTE
     * una herramienta de depuración que nunca corre en la ruta normal, y siempre restaurada en un
     * {@code finally}.
     */
    public static void withWireframe(Runnable draw) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        try {
            draw.run();
        } finally {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
    }
}
