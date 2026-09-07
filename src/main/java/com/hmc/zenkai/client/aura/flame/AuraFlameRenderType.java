package com.hmc.zenkai.client.aura.flame;

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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * RenderType + shader del sistema de aura NUEVO (estilo dbrebirth-0.3, FASE 1 — ver
 * .claude/pendiente/aura-dbrebirth-sistema-propuesta.md). AISLADO por completo de
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
 * que ya usa {@code KiRenderTypes.FRESNEL} para las técnicas ki, no el constructor a mano de
 * {@code ENERGY_RIM_SPIKED}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para llegar a los shards protegidos de RenderStateShard.
    private AuraFlameRenderType() { super("", null, null, 0, false, false, null, null); }

    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_flame");

    private static ShaderInstance auraFlameShader;

    /** Mismo patrón try/catch con fallback silencioso que ModAuraRenderType/KiRenderTypes: un
     *  fallo de compilación (driver viejo, shaderpack) deja el shader en null en vez de
     *  crashear, y {@link #available()} es la única pregunta que hace AuraFlameRenderer. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
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

    /** Única pregunta que hace AuraFlameRenderer para decidir si intenta dibujar. Sin respaldo
     *  geométrico (a diferencia de aura_rim, que cae a una cáscara lisa): en esta fase el sistema
     *  es opt-in por comando de debug, así que un shader que no compila simplemente no dibuja
     *  nada en vez de caer a otra malla. */
    public static boolean available() { return auraFlameShader != null; }

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
     * endBatch (mismo motivo que KiRenderTypes.setupFresnel/ModAuraRenderType.setupAuraRim: el
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
