package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
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
import org.lwjgl.opengl.GL11;

import java.util.function.Function;

/**
 * RenderType único del aura: TRANSLÚCIDO EMISIVO (SRC_ALPHA, 1-SRC_ALPHA, fullbright).
 * El sombreado del aura vive EN LA TEXTURA (bandas centro-blanco → borde-gris → puntas
 * oscuras) y se tinta por vértice: funciona igual para blanco, negro y cualquier tono,
 * sin pases aditivos ni crossfades por luminancia.
 * Depth-test LEQUAL (el mundo/jugador ocluyen) sin escribir profundidad (COLOR_WRITE)
 * para que los planos del cono se apilen entre sí en orden de dibujo.
 * Blur=true (filtrado bilineal en magnificación): sin él, escalar la llama a ~4 bloques
 * hace que cada téxel se lea como un escalón. La hoja lleva margen transparente por
 * cuadrante y el renderer inserta las UV media téxel para que el filtro no sangre.
 * Memoizado por textura.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ModAuraRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para acceder a los shards protegidos de RenderStateShard.
    private ModAuraRenderType() { super("", null, null, 0, false, false, null, null); }

    private static final Function<ResourceLocation, RenderType> ENERGY =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_energy",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER) // ⚠
                            .setTextureState(new TextureStateShard(tex, true, true))      // ⚠ blur=true: magnificación bilineal
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)                // ⚠
                            .setCullState(NO_CULL)                                         // ⚠
                            .setLightmapState(LIGHTMAP)                                    // ⚠
                            .setOverlayState(OVERLAY)                                      // ⚠
                            .setDepthTestState(LEQUAL_DEPTH_TEST)                          // ⚠
                            .setWriteMaskState(COLOR_WRITE)                                // ⚠ no escribe profundidad
                            .createCompositeState(false)));

    /** Igual que ENERGY pero SIN filtrado bilineal: para texturas pixel art (bola de ki,
     *  proyectiles) donde el escalonado ES el estilo. El aura sigue usando blur=true. */
    private static final Function<ResourceLocation, RenderType> ENERGY_CRISP =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_energy_crisp",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType energyCrisp(ResourceLocation tex) { return ENERGY_CRISP.apply(tex); }

    /** Pasada única del aura (tintada por vértice; sombreado horneado en la textura). */
    public static RenderType energy(ResourceLocation tex) { return ENERGY.apply(tex); }

    /**
     * Igual que {@link #energy} pero ADITIVO (SRC_ALPHA, ONE) en vez de TRANSLUCENT — mismo
     * patrón que {@code KiRenderTypes.ADDITIVE} (halo/núcleo de estela de las técnicas ki):
     * la luz que se solapa suma hacia blanco en vez de mezclarse con lo de detrás.
     * PRUEBA DE VIABILIDAD (2026-09-02, ver AuraModifier.additiveGlow): pasada extra opcional
     * para las firmas de aura que la pidan, encima del cono translúcido normal — el cuerpo del
     * aura sigue siendo translúcido siempre, esto es solo un brillo añadido, nunca un
     * reemplazo. Misma hoja de textura que energy() (memoizado por textura, RenderType
     * distinto), así que no hace falta un atlas aparte.
     */
    private static final Function<ResourceLocation, RenderType> ENERGY_ADDITIVE =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_aura_energy_additive",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, true))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType energyAdditive(ResourceLocation tex) { return ENERGY_ADDITIVE.apply(tex); }

    /**
     * Cáscara de borde (rim glow, PROTOTIPO 2026-09-02, ver AuraRimRenderer): una copia
     * INFLADA del propio modelo del jugador, dibujada con la cara CERCANA a cámara
     * recortada (cull FRONT en vez del BACK de siempre).
     * <p>
     * Sin ese cambio de cara esto no funciona: la cara cercana de una cáscara más
     * grande, al mismo centro, queda MÁS CERCA de cámara que la del cuerpo real —
     * dibujarla tal cual taparía al jugador entero, el mismo barrido a blanco que ya se
     * descartó para el halo de EXPLOSION/BARRIER, solo que ahora sobre el cuerpo
     * completo en vez de un plano. Cortando la cara cercana, solo queda la cara LEJANA
     * de la cáscara — que el cuerpo real (opaco, dibujado antes, SÍ escribe profundidad)
     * ya tapa en todo su volumen salvo el borde de su propia silueta, donde la cáscara
     * (más grande) asoma por fuera. Ese aro delgado es el efecto entero; no depende de
     * ningún ajuste de alfa.
     * <p>
     * No se puede montar con {@code CompositeStateBuilder}: su {@code setCullState()}
     * solo acepta {@code RenderStateShard.CullStateShard}, y esa clase siempre cullea
     * BACK — no hay forma de pedirle FRONT a través del builder. Por eso este RenderType
     * llama al constructor base de {@code RenderType} directamente (mismo constructor
     * público que usa {@code RenderType.create} por debajo) y compone su propio
     * setup/clear a mano, incluyendo la llamada GL cruda que decide qué cara se
     * descarta — Minecraft no expone esa dirección en ningún shard existente, así que
     * no hay nada que desincronizar: ningún otro sitio del juego lee o cachea el modo de
     * cull actual, solo si está activado o no.
     */
    private static final Function<ResourceLocation, RenderType> ENERGY_RIM =
            Util.memoize(tex -> {
                TextureStateShard texState = new TextureStateShard(tex, false, false);
                return new RenderType(
                        "zenkai_aura_rim",
                        DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                        false, true,
                        () -> {
                            RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER.setupRenderState();
                            texState.setupRenderState();
                            ADDITIVE_TRANSPARENCY.setupRenderState();
                            LEQUAL_DEPTH_TEST.setupRenderState();
                            COLOR_WRITE.setupRenderState();
                            LIGHTMAP.setupRenderState();
                            OVERLAY.setupRenderState();
                            RenderSystem.enableCull();
                            GL11.glCullFace(GL11.GL_FRONT); // ⚠ la pieza que hace el aro
                        },
                        () -> {
                            GL11.glCullFace(GL11.GL_BACK); // ⚠ restaurar el default de Minecraft
                            RenderSystem.disableCull();
                            OVERLAY.clearRenderState();
                            LIGHTMAP.clearRenderState();
                            COLOR_WRITE.clearRenderState();
                            LEQUAL_DEPTH_TEST.clearRenderState();
                            ADDITIVE_TRANSPARENCY.clearRenderState();
                            texState.clearRenderState();
                            RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER.clearRenderState();
                        }) {};
            });

    public static RenderType energyRim(ResourceLocation tex) { return ENERGY_RIM.apply(tex); }

    // ── Rim con picos angulares (aura_rim.vsh/fsh) ─────────────────────────────

    private static final ResourceLocation AURA_RIM_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_rim");

    private static ShaderInstance auraRimShader;

    /** Mismo patrón que KiRenderTypes.onRegisterShaders — copiado tal cual, incluida la misma
     *  anotación @EventBusSubscriber sin bus explícito (RegisterShadersEvent es del mod bus; ese
     *  patrón ya funciona en este mismo repo tanto ahí como en AuraRimRenderer.onAddLayers). Un
     *  fallo de compilación (driver viejo, shaderpack) no puede hacer desaparecer el rim: cae a
     *  energyRimSpiked()/energyRim() en silencio, ver el fallback más abajo. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), AURA_RIM_SHADER_ID,
                            DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        auraRimShader = instance;
                        Zenkai.LOGGER.info("[Zenkai] Shader de aura_rim compilado: el rim de "
                                + "aura puede mostrar picos angulares.");
                    });
        } catch (Exception ex) {
            auraRimShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de aura_rim NO cargó ({}). El rim usará la "
                    + "cáscara lisa de siempre.", ex.toString());
        }
    }

    /** Única pregunta que hace AuraRimRenderer para decidir si intenta picos. */
    public static boolean auraRimSpikeShaderAvailable() { return auraRimShader != null; }

    /**
     * Igual que {@link #ENERGY_RIM} (mismo truco de cull FRONT, mismo motivo — ver su javadoc)
     * pero con {@code aura_rim.vsh}/{@code .fsh} en vez del shader emisivo vainilla, para poder
     * desplazar la malla con picos angulares en el vertex shader. Memoizado por textura, igual
     * que el resto de RenderType de esta clase.
     */
    private static final Function<ResourceLocation, RenderType> ENERGY_RIM_SPIKED =
            Util.memoize(tex -> {
                TextureStateShard texState = new TextureStateShard(tex, false, false);
                ShaderStateShard shaderState = new ShaderStateShard(() -> auraRimShader);
                return new RenderType(
                        "zenkai_aura_rim_spiked",
                        DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                        false, true,
                        () -> {
                            shaderState.setupRenderState();
                            texState.setupRenderState();
                            ADDITIVE_TRANSPARENCY.setupRenderState();
                            LEQUAL_DEPTH_TEST.setupRenderState();
                            COLOR_WRITE.setupRenderState();
                            LIGHTMAP.setupRenderState();
                            OVERLAY.setupRenderState();
                            RenderSystem.enableCull();
                            GL11.glCullFace(GL11.GL_FRONT); // ⚠ misma pieza que ENERGY_RIM
                        },
                        () -> {
                            GL11.glCullFace(GL11.GL_BACK);
                            RenderSystem.disableCull();
                            OVERLAY.clearRenderState();
                            LIGHTMAP.clearRenderState();
                            COLOR_WRITE.clearRenderState();
                            LEQUAL_DEPTH_TEST.clearRenderState();
                            ADDITIVE_TRANSPARENCY.clearRenderState();
                            texState.clearRenderState();
                            shaderState.clearRenderState();
                        }) {};
            });

    /** Rim con picos si el shader está disponible; si no, cae a {@link #energyRim} sin que el
     *  llamador tenga que comprobar nada — mismo criterio defensivo que KiRenderTypes.available()
     *  aplicado aquí a nivel de RenderType en vez de dejarlo solo al llamador. */
    public static RenderType energyRimSpiked(ResourceLocation tex) {
        return auraRimSpikeShaderAvailable() ? ENERGY_RIM_SPIKED.apply(tex) : energyRim(tex);
    }

    /**
     * Sube los uniforms propios de aura_rim.fsh/vsh. Llamar ANTES de que el buffer haga
     * endBatch, mismo motivo que KiRenderTypes.setupFresnel (el ShaderInstance es único, sus
     * uniforms valen para el draw que se ejecuta).
     * @param spikeAmount AuraProfile.spike() del jugador, 0..1.
     * @param spikeCount  nº de picos alrededor del eje Y.
     * @param invBodyRot  rotación de CUERPO del jugador, invertida (ver el javadoc de
     *                    aura_rim.vsh para el porqué hace falta deshacerla).
     */
    public static void setupAuraRim(float spikeAmount, float spikeCount, Matrix4f invBodyRot) {
        ShaderInstance s = auraRimShader;
        if (s == null) return;
        s.safeGetUniform("ZenkaiSpikeAmount").set(spikeAmount);
        s.safeGetUniform("ZenkaiSpikeCount").set(spikeCount);
        s.safeGetUniform("InvBodyRotMat").set(invBodyRot);
    }
}