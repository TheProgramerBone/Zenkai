package com.hmc.zenkai.client.aura.silhouette;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.aura.compose.AuraStyleManager;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTORNO DE LLAMA SOBRE LA SILUETA (el "humo" del Legendario/Ikari, 2026-10-26; referencias del usuario
 * {@code aura vfx/smoke.png} y los dos vídeos de esa carpeta): lenguas finas que lamen hacia arriba pegadas
 * al borde REAL del jugador (cuerpo, pelo, ítems, armas a la espalda). Idea vista en el contorno de
 * villanos de DragonMine Z 2.2 (máscara de pantalla + campo + lenguas); código y shaders propios (DMZ es
 * GPLv3).
 * <p>
 * Por frame:
 * <ol>
 *   <li><b>Máscara</b>: entre el Pre y el Post de un jugador cuya forma lleva {@code "silhouette"} en su
 *       estilo se vacían los lotes de render y se compara el depth de la escena de antes y de después:
 *       los píxeles que se ACERCARON a la cámara son los que pintó, y se marcan en {@code mask} con un
 *       color plano = su ranura (canal R). La oclusión sale sola: lo que tapa un bloque no cambia.</li>
 *   <li><b>Campo</b>: por ranura, la cobertura de su máscara promediada en un radio = grosor del contorno
 *       (media resolución). Fuera del cuerpo vale 0 lejos y sube al acercarse al borde.</li>
 *   <li><b>Llama</b>: pasada a pantalla completa sobre el principal; el campo se lee desplazado hacia
 *       ABAJO una distancia que marca un ruido que sube, así el borde saca lenguas hacia arriba. Bandas
 *       duras (interior clara, exterior del color de fuera), como el resto del aura.</li>
 * </ol>
 * Se ve siempre que el jugador esté en la forma (con o sin aura), nunca en primera persona propia.
 * Con un shaderpack (Iris/Oculus) la captura funciona igual (medido con Complementary: Iris escribe el
 * depth de las entidades en el del principal), pero la llama se compone en AFTER_LEVEL: en
 * AFTER_ENTITIES la pisaba el compuesto del pack y no se veía nada (2026-10-26).
 * <p>
 * TRAMPAS YA PAGADAS (2026-10-26, medidas con AuraShots modo {@code humo}):
 * <ul>
 *   <li>NO capturar la geometría del jugador: redibujarlo daba CERO vértices (el cuerpo racial y el pelo
 *       GeckoLib no se dibujan dos veces por fotograma) y copiar sus vértices de la fuente del renderer
 *       tampoco vale: GeckoLib escribe en la fuente GLOBAL de Minecraft, no en la que recibe el renderer.
 *       La comparación de depth capta cualquier cosa que pinte.</li>
 *   <li>Volver al principal SIEMPRE con {@code bindWrite(true)}: el campo deja el viewport a media
 *       resolución y con {@code false} el resto del fotograma salía en un cuarto (el jugador desaparecía).</li>
 *   <li>OCLUSIÓN de las lenguas de fuera: la llama se compone sin test de depth, y fuera del cuerpo no
 *       hay depth del jugador con el que comparar. Antes salían ENCIMA de un bloque que tapara al
 *       jugador. Ahora la máscara guarda la distancia lineal de cada píxel del jugador (G), el campo la
 *       propaga al halo y la llama descarta donde la escena está claramente más cerca. La línea clara de
 *       dentro tampoco se pinta en esos cortes.</li>
 *   <li>DEPTH de la llama: se compone en AFTER_ENTITIES y lo translúcido (cristal, agua) y las partículas
 *       van después; sin depth propio, lo que estaba DETRÁS del jugador se pintaba encima de las lenguas.
 *       El shader escribe {@code gl_FragDepth}; en GL eso exige el test de depth ENCENDIDO (en ALWAYS).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SilhouetteFlameRenderer {
    private SilhouetteFlameRenderer() {}

    private static final int MAX_SLOTS = 8;
    /** Grosor del contorno respecto al alto del jugador EN PANTALLA (referencia smoke.png). */
    private static final float THICKNESS_PER_HEIGHT = 0.045f;
    private static final float THICKNESS_MIN_PX = 1f, THICKNESS_MAX_PX = 36f;
    private static final double MAX_DIST = 64.0;
    private static final ResourceLocation NOISE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_noise.png");

    private record Slot(float id, float[] inner, float[] outer, float thickness, float reach, float speed,
                        float alpha, float upX, float upY) {}

    private static final List<Slot> SLOTS = new ArrayList<>();

    /** Humo MOMENTÁNEO (whileCharging): segundos para aparecer por completo y para apagarse. */
    private static final float RAMP_IN_S = 0.5f, RAMP_OUT_S = 0.9f;
    /** Por jugador: {rampa 0..1, último nanoTime}. Tiempo real, no ticks: se avanza al dibujarlo. */
    private static final Map<Integer, float[]> RAMP = new HashMap<>();
    private static final Map<Integer, Long> RAMP_NANOS = new HashMap<>();
    private static boolean inLevel;
    /** Jugador cuya pasada se está copiando a la máscara (entre su Pre y su Post); null = ninguno. */
    private static @Nullable Slot pending;
    /** Captura SUSPENDIDA (halo del Otherworld, nombre): ya marcado lo anterior en la máscara; lo que se dibuje
     *  mientras tanto no cuenta como silueta. Se vuelve a abrir con {@link #resumeCapture} o se cierra en el Post. */
    private static @Nullable Slot suspended;

    // ── Depuración (AuraShots) ──────────────────────────────────────────────

    /** Apaga el contorno entero para comparar. */
    public static boolean debugDisabled;
    /** Sin capturar la máscara / sin componer la llama. */
    public static boolean debugNoCapture, debugNoCompose;
    /** Pinta las lenguas sin comparar con la escena (como antes del arreglo de oclusión), para comparar. */
    public static boolean debugNoOcclusion;
    /** La llama no escribe depth (como antes): lo translúcido de detrás vuelve a pintarse encima. */
    public static boolean debugNoDepthWrite;
    /** Si no es null, el próximo compose vuelca máscara y campo a esta carpeta. */
    public static @Nullable File debugDumpDir;

    // ── Shaders ─────────────────────────────────────────────────────────────

    private static ShaderInstance depthShader;
    private static ShaderInstance markShader;
    private static ShaderInstance fieldShader;
    private static ShaderInstance flameShader;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_silhouette_depth"),
                    DefaultVertexFormat.POSITION_TEX), s -> depthShader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_silhouette_mark"),
                    DefaultVertexFormat.POSITION_TEX), s -> markShader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_silhouette_field"),
                    DefaultVertexFormat.POSITION_TEX), s -> fieldShader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_silhouette_flame"),
                    DefaultVertexFormat.POSITION_TEX), s -> flameShader = s);
        } catch (Exception ex) {
            depthShader = null;
            markShader = null;
            fieldShader = null;
            flameShader = null;
            Zenkai.LOGGER.error("[Zenkai] Los shaders del contorno de silueta NO cargaron ({}).", ex.toString());
        }
    }

    // ── Targets ─────────────────────────────────────────────────────────────

    /** R = ranura, G = distancia lineal del píxel del jugador (RGBA16F: no cabe en 8 bits). */
    private static TextureTarget mask;
    /** R = cobertura, G = suma de distancias de lo cubierto (RGBA16F). */
    private static TextureTarget field;
    /** Depth de la escena ANTES de dibujar al jugador, copiado a color R32F. Al acabar las entidades se
     *  reutiliza para el depth de lo OPACO + entidades (comparación de oclusión del compose). */
    private static TextureTarget before;
    private static int w = -1, h = -1;
    /** m22 y m32 de la proyección del mundo, para pasar el depth a distancia lineal. */
    private static float projM22 = -1f, projM32 = -0.1f;

    private static void ensureTargets(int fullW, int fullH) {
        if (fullW == w && fullH == h && mask != null) return;
        mask = recreate(mask, fullW, fullH, GL11.GL_NEAREST);
        makeHalf(mask);
        field = recreate(field, Math.max(2, fullW / 2), Math.max(2, fullH / 2), GL11.GL_LINEAR);
        makeHalf(field);
        before = recreate(before, fullW, fullH, GL11.GL_NEAREST);
        makeFloat(before);
        w = fullW;
        h = fullH;
    }

    /** R32F: el depth no cabe en 8 bits (la comparación de antes/después saldría a escalones). Si el
     *  driver no lo acepta como color-renderable, RGBA16F. */
    private static void makeFloat(TextureTarget t) {
        GlStateManager._bindTexture(t.getColorTextureId());
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, t.width, t.height, 0,
                GL11.GL_RED, GL11.GL_FLOAT, null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.frameBufferId);
        if (GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, t.width, t.height, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, null);
            Zenkai.LOGGER.warn("[Zenkai] R32F no soportado para el contorno de silueta; se usa RGBA16F.");
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._bindTexture(0);
    }

    /** RGBA16F: la distancia lineal en bloques (hasta {@link #MAX_DIST}) con ~0,03 de precisión. */
    private static void makeHalf(TextureTarget t) {
        GlStateManager._bindTexture(t.getColorTextureId());
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, t.width, t.height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, null);
        GlStateManager._bindTexture(0);
    }

    private static TextureTarget recreate(TextureTarget t, int tw, int th, int filter) {
        // useDepth=false: su clear() borra SOLO el color, nunca el depth del principal enganchado.
        if (t == null) t = new TextureTarget(tw, th, false, Minecraft.ON_OSX);
        else t.resize(tw, th, Minecraft.ON_OSX);
        t.setClearColor(0f, 0f, 0f, 0f);
        t.setFilterMode(filter);
        return t;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        if (field != null) { field.destroyBuffers(); field = null; }
        if (before != null) { before.destroyBuffers(); before = null; }
        RAMP.clear();
        RAMP_NANOS.clear();
        w = -1;
        h = -1;
    }

    // ── Qué jugadores ───────────────────────────────────────────────────────

    /** Contorno de la forma ACTIVA del jugador, o null. Mismo aura_type que usa el aura compuesta.
     *  Beast forzando (overdrive) lleva el suyo, rojo ({@code beast_fury}), que antes era un contorno
     *  sobre las cajas de la pose. */
    public static @Nullable SilhouetteFlameSpec specFor(AbstractClientPlayer p) {
        if (MasteryEffects.isBeastOverdrive(p)) {
            SilhouetteFlameSpec fury = AuraStyleManager.silhouette("beast_fury");
            if (fury != null) return fury;
        }
        FormDef def = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).auraDef();
        return AuraStyleManager.silhouette(def == null ? "default"
                : com.hmc.zenkai.client.aura.compose.AuraVariants.of(p, def.auraType()));
    }

    private static boolean enabled() {
        return !debugDisabled && depthShader != null && markShader != null && fieldShader != null && flameShader != null;
    }

    /** Pase en el que se compone la llama: AFTER_ENTITIES sin shaderpack (lo translúcido de delante la
     *  tiñe); AFTER_LEVEL con shaderpack, porque lo dibujado antes del compuesto del pack se pierde. */
    private static RenderLevelStageEvent.Stage composeStage() {
        return IrisCompat.shaderPackActive() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    /** Framebuffer enganchado al empezar un pase propio: con shaderpack es uno de Iris, no el principal. */
    private static int boundFramebuffer() {
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    /** Vuelve al framebuffer de antes. El principal con bindWrite(true) (viewport entero, ver arriba); el
     *  de Iris a mano, con el viewport del principal (sus búferes son del mismo tamaño). */
    private static void restoreFramebuffer(int fbo, RenderTarget main) {
        if (fbo == main.frameBufferId || fbo == 0) {
            main.bindWrite(true);
        } else {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            RenderSystem.viewport(0, 0, main.width, main.height);
        }
    }

    // ── Frame ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            // Inicio del mundo en este frame: máscara limpia y sin ranuras. Fuera de esta ventana
            // (GUI, retrato del HUD, inventario) no se captura nada.
            SLOTS.clear();
            pending = null;
            inLevel = enabled();
            if (inLevel) {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                int fbo = boundFramebuffer();
                ensureTargets(main.width, main.height);
                mask.clear(Minecraft.ON_OSX);
                restoreFramebuffer(fbo, main);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            // Fin de la captura (los jugadores ya están dibujados), con o sin shaderpack.
            inLevel = false;
            pending = null;
            // Depth de la escena para la oclusión, AHORA (opaco + entidades, aún sin translúcidos) y no al
            // componer: con shaderpack se compone en AFTER_LEVEL y ahí el depth ya trae el cristal/agua de
            // delante (el contorno entero desaparecía detrás de un cristal) y no el de los bloques (volvía
            // la línea del corte). Se copia en vez de leer el del principal al componer, que es el que
            // está enganchado mientras se pinta la llama (leer y tener enganchada la misma textura es
            // indefinido en GL aunque no se escriba).
            if (!SLOTS.isEmpty() && enabled()) {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                int fbo = boundFramebuffer();
                before.bindWrite(true);
                fullscreen(depthShader, main.getDepthTextureId(), 0);
                restoreFramebuffer(fbo, main);
            }
        }
        if (event.getStage() == composeStage()) {
            if (!SLOTS.isEmpty() && enabled() && !debugNoCompose) compose(event);
            SLOTS.clear();
        }
    }

    /** Abre la captura si la forma del jugador lleva contorno: vacía los lotes y guarda el depth de
     *  antes. LOWEST: con el modelo ya preparado por las razas. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre e) {
        pending = null;
        suspended = null;
        if (!inLevel || debugNoCapture || e.isCanceled() || SLOTS.size() >= MAX_SLOTS) return;
        // Iris vuelve a dibujar a cada jugador en su pase de SOMBRAS, desde la luz: ahí no hay contorno.
        if (IrisCompat.renderingShadowPass()) return;
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (p.isInvisible() || p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > MAX_DIST * MAX_DIST) return;
        SilhouetteFlameSpec spec = specFor(p);
        if (spec == null) return;
        // Momentáneo (formas divinas): sube al cargar ki/turbo y baja al soltar; a 0 no hay humo.
        float ramp = spec.whileCharging() ? ramp(p.getId(), AuraClientState.isAuraActive(p)) : 1f;
        if (ramp <= 0.01f) return;

        RenderTarget main = mc.getMainRenderTarget();
        // Alto en pantalla y "arriba" de pantalla del jugador: grosor y dirección de las lenguas.
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(e.getPoseStack().last().pose());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        projM22 = proj.m22();
        projM32 = proj.m32();
        float[] feet = toScreen(proj, mv, 0f, 0f, 0f, main);
        float[] head = toScreen(proj, mv, 0f, p.getBbHeight(), 0f, main);
        if (feet[2] <= 0f && head[2] <= 0f) return; // detrás de la cámara
        float dx = head[0] - feet[0], dy = head[1] - feet[1];
        float len = Mth.sqrt(dx * dx + dy * dy);
        float upX = len > 1f ? dx / len : 0f, upY = len > 1f ? dy / len : 1f;
        // Al aparecer, además de la opacidad, crecen el grosor y el largo de las lenguas: "sale" del cuerpo.
        float thickness = Mth.clamp(len * THICKNESS_PER_HEIGHT * spec.thickness() * (0.55f + 0.45f * ramp),
                THICKNESS_MIN_PX, THICKNESS_MAX_PX);
        float id = (SLOTS.size() + 1) / (float) MAX_SLOTS;

        // Lo pendiente se dibuja YA: el depth de antes no debe incluir al jugador ni quedarse sin
        // lo que ya estaba encolado.
        mc.renderBuffers().bufferSource().endBatch();
        int fbo = boundFramebuffer();
        before.bindWrite(true);
        fullscreen(depthShader, main.getDepthTextureId(), 0);
        restoreFramebuffer(fbo, main);
        float[][] colors = spec.colors(p);
        pending = new Slot(id, colors[0], colors[1], thickness, spec.reach() * (0.3f + 0.7f * ramp), spec.speed(),
                spec.alpha() * ramp, upX, upY);
    }

    /** Avanza la rampa del humo momentáneo de un jugador y la devuelve (0..1). Suavizada (smoothstep). */
    private static float ramp(int id, boolean active) {
        long now = System.nanoTime();
        Long last = RAMP_NANOS.put(id, now);
        float dt = last == null ? 0f : Math.min((now - last) / 1.0e9f, 0.25f);
        float[] r = RAMP.computeIfAbsent(id, k -> new float[1]);
        r[0] = Mth.clamp(r[0] + (active ? dt / RAMP_IN_S : -dt / RAMP_OUT_S), 0f, 1f);
        if (r[0] <= 0f && !active) {
            RAMP.remove(id);
            RAMP_NANOS.remove(id);
            return 0f;
        }
        return r[0] * r[0] * (3f - 2f * r[0]);
    }

    /** Cierra la captura: dibuja ya al jugador y marca en la máscara los píxeles cuyo depth se acercó. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        Slot open = pending, closed = suspended;
        pending = null;
        suspended = null;
        if (open != null) markNow(open);
        Slot s = open != null ? open : closed;
        if (s != null) SLOTS.add(s);
    }

    /** Marca en la máscara lo que se ha dibujado desde el último depth de «antes». */
    private static void markNow(Slot s) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        mc.renderBuffers().bufferSource().endBatch();
        int fbo = boundFramebuffer();
        mask.bindWrite(true);
        markShader.safeGetUniform("Id").set(s.id());
        markShader.safeGetUniform("ProjParams").set(projM22, projM32);
        fullscreen(markShader, main.getDepthTextureId(), before.getColorTextureId());
        restoreFramebuffer(fbo, main);
    }

    /**
     * Deja de contar como silueta lo que se dibuje a partir de AHORA (halo del Otherworld). La máscara sale de
     * comparar el depth de antes y de después del jugador, así que cuanto pinte entre su Pre y su Post
     * (el halo, la placa del nombre) recibía contorno de humo. Se marca lo ya dibujado y se suspende; el que
     * llame decide si reabre ({@link #resumeCapture}) o no (la placa del nombre va la última).
     */
    public static void suspendCapture() {
        Slot s = pending;
        if (s == null) return;
        markNow(s);
        suspended = s;
        pending = null;
    }

    /** Reabre la captura tras {@link #suspendCapture}: vuelca lo suspendido al depth y lo toma como el nuevo «antes». */
    public static void resumeCapture() {
        Slot s = suspended;
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        mc.renderBuffers().bufferSource().endBatch();
        int fbo = boundFramebuffer();
        before.bindWrite(true);
        fullscreen(depthShader, main.getDepthTextureId(), 0);
        restoreFramebuffer(fbo, main);
        pending = s;
        suspended = null;
    }

    /** La placa del nombre (otros jugadores) se dibuja DENTRO del render del jugador: sin esto salía con humo. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNameTag(RenderNameTagEvent event) {
        suspendCapture();
    }

    /** Quad a pantalla completa con un shader de una o dos texturas, sin blend ni depth. */
    private static void fullscreen(ShaderInstance shader, int tex0, int tex1) {
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, tex0);
        if (tex1 != 0) RenderSystem.setShaderTexture(1, tex1);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        quad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** {x, y en píxeles del principal, w de recorte (> 0 = delante de la cámara)}. */
    private static float[] toScreen(Matrix4f proj, Matrix4f mv, float x, float y, float z, RenderTarget main) {
        Vector4f v = new Vector4f(x, y, z, 1f);
        mv.transform(v);
        proj.transform(v);
        float iw = Math.abs(v.w) > 1.0e-5f ? 1f / v.w : 0f;
        return new float[]{(v.x * iw * 0.5f + 0.5f) * main.width, (v.y * iw * 0.5f + 0.5f) * main.height, v.w};
    }

    // ── Composición ─────────────────────────────────────────────────────────

    private static void compose(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        float time = mc.level == null ? 0f
                : (float) ((com.hmc.zenkai.client.aura.AuraClock.ticks() % 24000L + event.getPartialTick().getGameTimeDeltaPartialTick(true)) / 20.0);
        for (Slot s : SLOTS) {
            // Campo de esta ranura, a media resolución.
            field.clear(Minecraft.ON_OSX);
            field.bindWrite(true);
            RenderSystem.setShader(() -> fieldShader);
            RenderSystem.setShaderTexture(0, mask.getColorTextureId());
            fieldShader.safeGetUniform("MaskTexel").set(1f / mask.width, 1f / mask.height);
            fieldShader.safeGetUniform("Footprint").set(s.thickness());
            fieldShader.safeGetUniform("Id").set(s.id());
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            quad();

            // Llama sobre el principal. bindWrite(TRUE): restaura el viewport a pantalla completa.
            main.bindWrite(true);
            RenderSystem.setShader(() -> flameShader);
            RenderSystem.setShaderTexture(0, mask.getColorTextureId());
            RenderSystem.setShaderTexture(1, field.getColorTextureId());
            RenderSystem.setShaderTexture(2, NOISE);
            RenderSystem.setShaderTexture(3, before.getColorTextureId());
            flameShader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
            flameShader.safeGetUniform("Id").set(s.id());
            flameShader.safeGetUniform("Thickness").set(s.thickness());
            flameShader.safeGetUniform("Up").set(s.upX(), s.upY());
            flameShader.safeGetUniform("Time").set(time);
            flameShader.safeGetUniform("Look").set(s.reach(), s.speed(), s.alpha(), 0f);
            flameShader.safeGetUniform("Inner").set(s.inner()[0], s.inner()[1], s.inner()[2]);
            flameShader.safeGetUniform("Outer").set(s.outer()[0], s.outer()[1], s.outer()[2]);
            flameShader.safeGetUniform("ProjParams").set(projM22, projM32);
            flameShader.safeGetUniform("Occlusion").set(debugNoOcclusion ? 0f : 1f);
            // Con shaderpack se compone al final (AFTER_LEVEL): ya no queda nada detrás que necesite el depth.
            flameShader.safeGetUniform("WriteDepth").set(debugNoDepthWrite || IrisCompat.shaderPackActive() ? 0f : 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // El shader escribe gl_FragDepth (el del cuerpo cercano): en GL solo se escribe depth con el
            // test encendido, así que va encendido en ALWAYS (la oclusión ya la resuelve el shader).
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
            quad();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            if (debugDumpDir != null) {
                Zenkai.LOGGER.info("[Zenkai] Contorno de silueta (depuración): {} ranuras", SLOTS.size());
                dump(mask, new File(debugDumpDir, "mascara.png"));
                dump(field, new File(debugDumpDir, "campo.png"));
                debugDumpDir = null;
                main.bindWrite(true);
            }
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        main.bindWrite(true);
    }

    private static void dump(TextureTarget t, File out) {
        try (NativeImage img = new NativeImage(t.width, t.height, false)) {
            RenderSystem.bindTexture(t.getColorTextureId());
            img.downloadTexture(0, false);
            img.flipY();
            out.getParentFile().mkdirs();
            img.writeToFile(out);
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("[Zenkai] Contorno de silueta: no se pudo volcar {}", out, ex);
        }
    }

    private static void quad() {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        bb.addVertex(1f, -1f, 0f).setUv(1f, 0f);
        bb.addVertex(1f, 1f, 0f).setUv(1f, 1f);
        bb.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }
}
