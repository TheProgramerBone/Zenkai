package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * RenderType + shader del sistema de aura NUEVO (estilo dbrebirth-0.3, FASE 1 — ver
 * .claude/docs/tecnicas-y-vfx.md, "Aura nueva estilo dbrebirth"). AISLADO por completo de
 * {@link com.hmc.zenkai.client.aura.ModAuraRenderType}: esta clase no importa ni modifica nada
 * del sistema legacy, y nada del legacy la importa a ella tampoco.
 * <p>
 * A diferencia de {@code ModAuraRenderType.ENERGY_RIM_SPIKED} (aro delgado que necesita cull
 * FRONT a mano porque {@code CompositeState.builder()} no lo permite — ver su javadoc), esta
 * malla ENVUELVE al jugador varias veces su tamaño: el mismo truco de cull FRONT taparía al
 * jugador entero visto desde dentro, igual que ya se descartó para BARRIER/EXPLOSION. En vez de
 * eso se usa el mecanismo real de dbrebirth: {@code NO_CULL} + un fresnel en el fragment shader
 * que atenúa la cara trasera por alfa ({@code facingRaw < 0.0 -> alpha muy bajo}, ver
 * aura_flame.fsh) — el mismo patrón de RenderType normal vía {@code CompositeState.builder()}
 * que ya usa {@code KiVfxRenderTypes.energy()} para las técnicas ki, no el constructor a mano de
 * {@code ENERGY_RIM_SPIKED}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para llegar a los shards protegidos de RenderStateShard.
    private AuraFlameRenderType() { super("", null, null, 0, false, false, null, null); }

    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_flame");

    private static final ResourceLocation SHELL_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_flame_shell");

    private static ShaderInstance auraFlameShader;
    private static ShaderInstance shellShader;

    /** Mismo patrón try/catch con fallback silencioso que ModAuraRenderType/KiVfxRenderTypes: un
     *  fallo de compilación (driver viejo, shaderpack) deja el shader en null en vez de
     *  crashear, y {@link #available()} es la única pregunta que hace el despacho. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHELL_SHADER_ID,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    instance -> shellShader = instance);
        } catch (Exception ex) {
            shellShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de aura_flame_shell NO cargó ({}). El sistema "
                    + "de aura nuevo no se dibuja hasta que compile.", ex.toString());
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                            DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        auraFlameShader = instance;
                        Zenkai.LOGGER.info("[Zenkai] Shader de aura_flame compilado: el sistema "
                                + "de aura nuevo (dbrebirth, Fase 1) puede dibujarse.");
                    });
        } catch (Exception ex) {
            auraFlameShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de aura_flame NO cargó ({}). El sistema de "
                    + "aura nuevo no se dibuja hasta que compile.", ex.toString());
        }
    }

    /** Única pregunta que hace el despacho ({@link AuraSystemDispatcher#useFlame()}). Hacen falta
     *  LOS DOS shaders (capa interior y llama exterior): si uno no compila, el despacho cae al
     *  legacy entero en vez de dibujar media aura nueva. */
    public static boolean available() { return auraFlameShader != null && shellShader != null; }

    // ── Llama exterior (aura_flame_shell) ───────────────────────────────────

    /** Mezcla PREMULTIPLICADA, la misma que KiVfxRenderTypes (su shard es privado, se replica):
     *  el shader sale como vec4(c*a, a*(1-emit)). Con emit=0 cubre como un translúcido normal; con
     *  emit=1 suma luz. Es lo que permite la regla de núcleo caliente sin aditivo puro (el aditivo
     *  sobre el aura se rechazó en juego: las capas superpuestas lavan a blanco). */
    private static final TransparencyStateShard PREMULTIPLIED =
            new TransparencyStateShard("zenkai_aura_flame_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    /** Ruido tileable del aura (NoiseTex de dbrebirth), generado por tools/gen_aura_noise.py —
     *  único origen del PNG, no editarlo a mano. */
    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_noise.png");

    /** Textura = solo el ruido (la forma sale entera del shader). blur=true: el ruido es un campo
     *  continuo, con NEAREST se verían los texels al estirarlo sobre la llama. El wrap queda en
     *  GL_REPEAT (defecto de una textura sin .mcmeta de clamp), lo que el scroll del shader
     *  necesita. Sin lightmap (energía, no se oscurece de noche) y sin sortOnUpload: las
     *  coordenadas del búfer son UNITARIAS (la transformación va en un uniform), así que el orden
     *  por distancia en CPU sería falso — y con la cara trasera a alfa 0.01 el orden interno
     *  apenas importa. */
    private static final RenderType SHELL = RenderType.create(
            "zenkai_aura_flame_shell",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS, 4096,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shellShader))
                    .setTextureState(new TextureStateShard(NOISE_TEX, true, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType shell() { return SHELL; }

    /**
     * Sube los uniforms de aura_flame_shell. Llamar dentro de la tarea de KiVfxFrameQueue, antes
     * de pedir el búfer: el ShaderInstance es único y sus uniforms valen para el siguiente draw, y
     * la cola vuelca el lote tras cada tarea (un draw por jugador).
     * @param model       pose de la tarea (traslación al jugador * giro de cuerpo * escala de cuerpo).
     * @param p           números de la llama para este jugador (AuraProfile ya mapeado, Fase 6).
     * @param growth      0..1, rampa de encendido/apagado.
     * @param core        color del centro, ya resuelto (capa caliente, ver AuraFlameShellRenderer).
     * @param edge        color del borde y de las puntas, ya resuelto (rgb 0..1).
     * @param bloomPass   true en la pasada de bloom de KiVfxBloomPipeline (KiVfxFrameQueue.bloomPass).
     */
    public static void setupShell(Matrix4f model, AuraFlameParams p, float growth, float[] core,
                                  float[] edge, boolean bloomPass) {
        ShaderInstance s = shellShader;
        if (s == null) return;
        s.safeGetUniform("ZenkaiShellModelMat").set(model);
        s.safeGetUniform("ZenkaiBloomMode").set(bloomPass ? 1f : 0f);
        s.safeGetUniform("ZenkaiShellEmit").set(p.emitBody(), p.emitEdge());
        s.safeGetUniform("ZenkaiShellBloom").set(p.bloom(), p.bloomBody());
        s.safeGetUniform("ZenkaiShellCenter").set(0f, p.centerY(), 0f);
        s.safeGetUniform("ZenkaiShellSize").set(p.radius(), p.halfHeight(), p.radius());
        s.safeGetUniform("ZenkaiShellAmp").set(p.amp());
        s.safeGetUniform("ZenkaiShellTopBoost").set(p.topBoost());
        s.safeGetUniform("ZenkaiShellPeaks").set(p.peaks());
        s.safeGetUniform("ZenkaiShellSharp").set(p.sharp());
        s.safeGetUniform("ZenkaiShellFreqY").set(p.freqY());
        s.safeGetUniform("ZenkaiShellSpeed").set(p.speed());
        s.safeGetUniform("ZenkaiShellDetail").set(p.detail());
        s.safeGetUniform("ZenkaiShellStreak").set(p.streak());
        s.safeGetUniform("ZenkaiShellUpDir").set(p.upDir());
        s.safeGetUniform("ZenkaiShellGrowth").set(growth);
        s.safeGetUniform("ZenkaiShellAlpha").set(p.alphaCenter(), p.alphaEdge());
        s.safeGetUniform("ZenkaiShellFresnel").set(p.fresnelPower(), p.fresnelDivis());
        s.safeGetUniform("ZenkaiShellCoreColor").set(core[0], core[1], core[2], 1f);
        s.safeGetUniform("ZenkaiShellOuterColor").set(edge[0], edge[1], edge[2], 1f);
    }

    private static final Function<ResourceLocation, RenderType> AURA_FLAME =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_aura_flame",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> auraFlameShader))
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL) // sin cull FRONT: la cara trasera se oculta en el fragment shader, ver javadoc de la clase
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE) // no escribe profundidad, igual que el resto de translúcidos del aura
                            .createCompositeState(false)));

    public static RenderType auraFlame(ResourceLocation tex) { return AURA_FLAME.apply(tex); }

    /**
     * Sube los uniforms propios de aura_flame.vsh/.fsh. Llamar ANTES de que el buffer haga
     * endBatch (mismo motivo que KiVfxRenderTypes.setupEnergy/ModAuraRenderType.setupAuraRim: el
     * ShaderInstance es único, sus uniforms valen para el próximo draw ejecutado).
     * @param amount      interruptor de intensidad general, 0..1 (1.0 fijo en la Fase 1).
     * @param spikeCount  nº de picos alrededor del eje Y.
     * @param scale       bloques de desplazamiento en el pico más alto (1-3 en esta fase).
     * @param falloffStart altura LOCAL (0 = pies) donde empieza el efecto.
     * @param falloffFull  altura LOCAL de fuerza plena, sin caída de vuelta por encima (a
     *                     diferencia de aura_rim).
     * @param invBodyRot  rotación de CUERPO del jugador, invertida (ver aura_flame.vsh).
     * @param origin      {@code player.getPosition(partialTick) - camera.getPosition()}, el
     *                    mismo vector que el motor ya usó para trasladar el PoseStack — se resta
     *                    en el shader para recuperar coordenadas locales de verdad (ver el
     *                    comentario de cabecera de aura_flame.vsh sobre la corrección de origen).
     * @param coreRgb     color empaquetado 0xRRGGBB del núcleo (AuraColors.Layers.inner).
     * @param outerRgb    color empaquetado 0xRRGGBB de la envolvente (AuraColors.Layers.outer,
     *                    o el mismo inner si la firma no tiene capa exterior).
     */
    public static void setupAuraFlame(float amount, float spikeCount, float scale,
                                       float falloffStart, float falloffFull,
                                       Matrix4f invBodyRot, Vector3f origin,
                                       int coreRgb, int outerRgb) {
        ShaderInstance s = auraFlameShader;
        if (s == null) return;
        s.safeGetUniform("ZenkaiFlameAmount").set(amount);
        s.safeGetUniform("ZenkaiFlameSpikeCount").set(spikeCount);
        s.safeGetUniform("ZenkaiFlameScale").set(scale);
        s.safeGetUniform("ZenkaiFlameFalloffY").set(falloffStart, falloffFull);
        s.safeGetUniform("InvBodyRotMat").set(invBodyRot);
        s.safeGetUniform("ZenkaiFlameOrigin").set(origin.x, origin.y, origin.z);
        s.safeGetUniform("ZenkaiFlameCoreColor").set(
                ((coreRgb >> 16) & 0xFF) / 255f, ((coreRgb >> 8) & 0xFF) / 255f, (coreRgb & 0xFF) / 255f, 1f);
        s.safeGetUniform("ZenkaiFlameOuterColor").set(
                ((outerRgb >> 16) & 0xFF) / 255f, ((outerRgb >> 8) & 0xFF) / 255f, (outerRgb & 0xFF) / 255f, 1f);
    }
}
