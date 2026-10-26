package com.hmc.zenkai.client.aura.silhouette;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.AuraStyleManager;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.feature.forms.FormDef;
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
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
 * Con un shaderpack (Iris/Oculus) se apaga entero, como el bloom: postura conservadora.
 * <p>
 * TRAMPAS YA PAGADAS (2026-10-26, medidas con AuraShots modo {@code humo}):
 * <ul>
 *   <li>NO capturar la geometría del jugador: redibujarlo daba CERO vértices (el cuerpo racial y el pelo
 *       GeckoLib no se dibujan dos veces por fotograma) y copiar sus vértices de la fuente del renderer
 *       tampoco vale: GeckoLib escribe en la fuente GLOBAL de Minecraft, no en la que recibe el renderer.
 *       La comparación de depth capta cualquier cosa que pinte.</li>
 *   <li>Volver al principal SIEMPRE con {@code bindWrite(true)}: el campo deja el viewport a media
 *       resolución y con {@code false} el resto del fotograma salía en un cuarto (el jugador desaparecía).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SilhouetteFlameRenderer {
    private SilhouetteFlameRenderer() {}

    private static final int MAX_SLOTS = 8;
    /** Grosor del contorno respecto al alto del jugador EN PANTALLA (referencia smoke.png). */
    private static final float THICKNESS_PER_HEIGHT = 0.045f;
    private static final float THICKNESS_MIN_PX = 2f, THICKNESS_MAX_PX = 36f;
    private static final double MAX_DIST = 64.0;
    private static final ResourceLocation NOISE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_noise.png");

    private record Slot(float id, float[] inner, float[] outer, float thickness, float reach, float speed,
                        float alpha, float upX, float upY) {}

    private static final List<Slot> SLOTS = new ArrayList<>();
    private static boolean inLevel;
    /** Jugador cuya pasada se está copiando a la máscara (entre su Pre y su Post); null = ninguno. */
    private static @Nullable Slot pending;

    // ── Depuración (AuraShots) ──────────────────────────────────────────────

    /** Apaga el contorno entero para comparar. */
    public static boolean debugDisabled;
    /** Sin capturar la máscara / sin componer la llama. */
    public static boolean debugNoCapture, debugNoCompose;
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

    private static TextureTarget mask;
    private static TextureTarget field;
    /** Depth de la escena ANTES de dibujar al jugador, copiado a color R32F. */
    private static TextureTarget before;
    private static int w = -1, h = -1;

    private static void ensureTargets(int fullW, int fullH) {
        if (fullW == w && fullH == h && mask != null) return;
        mask = recreate(mask, fullW, fullH, GL11.GL_NEAREST);
        field = recreate(field, Math.max(2, fullW / 2), Math.max(2, fullH / 2), GL11.GL_LINEAR);
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
        w = -1;
        h = -1;
    }

    // ── Qué jugadores ───────────────────────────────────────────────────────

    /** Contorno de la forma ACTIVA del jugador, o null. Mismo aura_type que usa el aura compuesta. */
    public static @Nullable SilhouetteFlameSpec specFor(AbstractClientPlayer p) {
        FormDef def = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).activeDef();
        return AuraStyleManager.silhouette(def == null ? "default" : def.auraType());
    }

    private static boolean enabled() {
        return !debugDisabled && depthShader != null && markShader != null && fieldShader != null && flameShader != null && !IrisCompat.shaderPackActive();
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
                ensureTargets(main.width, main.height);
                mask.clear(Minecraft.ON_OSX);
                main.bindWrite(true);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            inLevel = false;
            pending = null;
            if (!SLOTS.isEmpty() && enabled() && !debugNoCompose) compose(event);
            SLOTS.clear();
        }
    }

    /** Abre la captura si la forma del jugador lleva contorno: vacía los lotes y guarda el depth de
     *  antes. LOWEST: con el modelo ya preparado por las razas. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre e) {
        pending = null;
        if (!inLevel || debugNoCapture || e.isCanceled() || SLOTS.size() >= MAX_SLOTS) return;
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (p.isInvisible() || p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > MAX_DIST * MAX_DIST) return;
        SilhouetteFlameSpec spec = specFor(p);
        if (spec == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        // Alto en pantalla y "arriba" de pantalla del jugador: grosor y dirección de las lenguas.
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(e.getPoseStack().last().pose());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        float[] feet = toScreen(proj, mv, 0f, 0f, 0f, main);
        float[] head = toScreen(proj, mv, 0f, p.getBbHeight(), 0f, main);
        if (feet[2] <= 0f && head[2] <= 0f) return; // detrás de la cámara
        float dx = head[0] - feet[0], dy = head[1] - feet[1];
        float len = Mth.sqrt(dx * dx + dy * dy);
        float upX = len > 1f ? dx / len : 0f, upY = len > 1f ? dy / len : 1f;
        float thickness = Mth.clamp(len * THICKNESS_PER_HEIGHT * spec.thickness(), THICKNESS_MIN_PX, THICKNESS_MAX_PX);
        float id = (SLOTS.size() + 1) / (float) MAX_SLOTS;

        // Todo lo pendiente se dibuja YA: el depth de antes no debe incluir al jugador ni quedarse sin
        // lo que ya estaba encolado.
        mc.renderBuffers().bufferSource().endBatch();
        before.bindWrite(true);
        fullscreen(depthShader, main.getDepthTextureId(), 0);
        main.bindWrite(true);
        pending = new Slot(id, spec.inner(), spec.outer(), thickness, spec.reach(), spec.speed(), spec.alpha(), upX, upY);
    }

    /** Cierra la captura: dibuja ya al jugador y marca en la máscara los píxeles cuyo depth se acercó. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        Slot s = pending;
        pending = null;
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        mc.renderBuffers().bufferSource().endBatch();
        mask.bindWrite(true);
        markShader.safeGetUniform("Id").set(s.id());
        fullscreen(markShader, main.getDepthTextureId(), before.getColorTextureId());
        main.bindWrite(true);
        SLOTS.add(s);
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
                : (float) ((mc.level.getGameTime() % 24000L + event.getPartialTick().getGameTimeDeltaPartialTick(true)) / 20.0);
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
            flameShader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
            flameShader.safeGetUniform("Id").set(s.id());
            flameShader.safeGetUniform("Thickness").set(s.thickness());
            flameShader.safeGetUniform("Up").set(s.upX(), s.upY());
            flameShader.safeGetUniform("Time").set(time);
            flameShader.safeGetUniform("Look").set(s.reach(), s.speed(), s.alpha(), 0f);
            flameShader.safeGetUniform("Inner").set(s.inner()[0], s.inner()[1], s.inner()[2]);
            flameShader.safeGetUniform("Outer").set(s.outer()[0], s.outer()[1], s.outer()[2]);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            quad();
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
