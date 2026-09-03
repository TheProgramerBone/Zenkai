package com.hmc.zenkai.client.aura;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
}