package com.hmc.zenkai.client.render_and_model_entities.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.config.ClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloom real, OPT-IN e HÍBRIDO para el halo/estela de ki — inspirado en el pipeline de blur
 * multi-paso de dbrebirth-0.3, pero de alcance MUCHO más acotado a propósito (ver el porqué en
 * cada pieza más abajo). NUNCA reemplaza el aditivo de siempre
 * ({@link KiRenderTypes#additive}): ese se sigue dibujando SIEMPRE, sin condición; esto es una
 * capa EXTRA encima, así que el peor caso (toggle apagado, o shaderpack detectado) es
 * exactamente el aspecto de hoy.
 * <p>
 * ═══ POR QUÉ CAPTURA SELECTIVA, NO UN BRIGHT-PASS POR LUMINANCIA ═══
 * La forma "genérica" de bloom (extraer del framebuffer ya compuesto todo lo que supere un
 * umbral de brillo) haría bloom sobre glowstone, lava o el cielo al mediodía — cualquier cosa
 * emisiva vainilla, no solo el halo de ki. En su lugar, {@link #registerHaloQuad} recibe SOLO
 * los quads que {@code KiProjectileRenderer.renderHalo} ya sabe que dibujó ese frame, y esta
 * clase los vuelve a dibujar (más pequeños, aparte) en un render target propio para difuminar
 * SOLO eso.
 * <p>
 * ═══ 4 PASADAS, "BLOOM LIGERO" ═══
 * Captura (quads registrados -> {@code bloomA}, a mitad de resolución de ventana) -> blur
 * horizontal ({@code bloomA -> bloomB}) -> blur vertical ({@code bloomB -> bloomA}) -> composite
 * ({@code bloomA} sobre el render target principal, aditivo). No hay MRT ni tonemap Reinhard-
 * Jodie como en dbrebirth (3 radios de blur + control de bloom "fake"/"real"): el halo de ki es
 * una forma pequeña en pantalla, esa sofisticación sería gasto sin beneficio proporcional.
 * <p>
 * ═══ SIN MIXIN ═══ Todo pasa por {@code RenderLevelStageEvent.Stage.AFTER_PARTICLES}, el mismo
 * hook que ya usa {@code AuraRenderer} — no hace falta enganchar el pipeline de Minecraft con un
 * mixin nuevo.
 * <p>
 * ═══ RIESGO ═══ Es la única pieza del mod que gestiona {@link RenderTarget} propios fuera del
 * ciclo de vida que Minecraft ya administra solo. Sale con el toggle de
 * {@link ClientConfig#kiBloomEnabled()} en {@code false} por defecto a propósito.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiBloomPipeline {
    private KiBloomPipeline() {}

    private record HaloQuad(double x, double y, double z, float half,
                            float r, float g, float b, float a) {}

    private static final List<HaloQuad> FRAME_QUADS = new ArrayList<>();

    /** true solo si el jugador activó el toggle experimental Y no hay un shaderpack tipo
     *  Iris/Oculus cargado (ver IrisCompat) — postura conservadora pedida explícitamente. */
    public static boolean active() {
        return ClientConfig.kiBloomEnabled() && !IrisCompat.shaderPackActive();
    }

    /** Llamado desde KiProjectileRenderer.renderHalo por cada halo dibujado ese frame, ya
     *  gateado por el llamador — se re-comprueba {@link #active()} aquí también por si acaso, no
     *  cuesta nada y evita depender solo de la disciplina del llamador. */
    public static void registerHaloQuad(double x, double y, double z, float half,
                                        float r, float g, float b, float a) {
        if (!active()) return;
        FRAME_QUADS.add(new HaloQuad(x, y, z, half, r, g, b, a));
    }

    // ── Shaders ─────────────────────────────────────────────────────────────

    private static final ResourceLocation BLUR_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_bloom_blur");
    private static final ResourceLocation COMPOSITE_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_bloom_composite");

    private static ShaderInstance blurShader;
    private static ShaderInstance compositeShader;

    /** Mismo patrón que KiRenderTypes.onRegisterShaders — dos shaders en vez de uno, misma
     *  anotación @EventBusSubscriber sin bus explícito, mismo fallback silencioso si no
     *  compilan (ver onRenderLevel: sin los dos shaders, no se dibuja nada, sin crashear). */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), BLUR_ID,
                            DefaultVertexFormat.POSITION_TEX),
                    instance -> blurShader = instance);
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), COMPOSITE_ID,
                            DefaultVertexFormat.POSITION_TEX),
                    instance -> compositeShader = instance);
            Zenkai.LOGGER.info("[Zenkai] Shaders de ki_bloom compilados.");
        } catch (Exception ex) {
            blurShader = null;
            compositeShader = null;
            Zenkai.LOGGER.error("[Zenkai] Los shaders de ki_bloom NO cargaron ({}). El toggle "
                    + "experimental de bloom no tendrá efecto.", ex.toString());
        }
    }

    // ── Render targets ──────────────────────────────────────────────────────

    private static TextureTarget bloomA;
    private static TextureTarget bloomB;
    private static int targetW = -1, targetH = -1;

    /** A mitad de resolución de ventana a propósito: barato, y el upsample bilineal al
     *  componer ya aporta parte del difuminado — no hace falta full-res para un halo pequeño. */
    private static void ensureTargets(int fullW, int fullH) {
        int w = Math.max(2, fullW / 2);
        int h = Math.max(2, fullH / 2);
        if (w == targetW && h == targetH && bloomA != null && bloomB != null) return;
        if (bloomA == null) bloomA = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else bloomA.resize(w, h, Minecraft.ON_OSX);
        if (bloomB == null) bloomB = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else bloomB.resize(w, h, Minecraft.ON_OSX);
        targetW = w;
        targetH = h;
    }

    /** Libera los render targets al salir del mundo — sin esto se fuga memoria de GPU cada vez
     *  que se entra/sale de una partida con el toggle activo. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (bloomA != null) { bloomA.destroyBuffers(); bloomA = null; }
        if (bloomB != null) { bloomB.destroyBuffers(); bloomB = null; }
        targetW = -1;
        targetH = -1;
        FRAME_QUADS.clear();
    }

    // ── Orquestación por frame ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (FRAME_QUADS.isEmpty()) return; // nada que capturar este frame, activo o no
        if (!active() || blurShader == null || compositeShader == null) {
            FRAME_QUADS.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ensureTargets(mc.getWindow().getWidth(), mc.getWindow().getHeight());

        Camera camera = event.getCamera();
        capturePass(camera);
        blurPass(bloomA, bloomB, 1f, 0f);
        blurPass(bloomB, bloomA, 0f, 1f);
        compositePass(mc);

        FRAME_QUADS.clear();
    }

    /** Vuelca los quads registrados (mismos parámetros que ya emite
     *  KiProjectileRenderer.renderHalo) en bloomA, a mitad de resolución. Aditivo, igual que
     *  KiRenderTypes.ADDITIVE_TRANSPARENCY — es el mismo halo, solo que en su propio buffer. */
    private static void capturePass(Camera camera) {
        bloomA.bindWrite(true);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        bloomA.clear(Minecraft.ON_OSX);

        Vec3 camPos = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, KiRenderTypes.HALO_TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (HaloQuad q : FRAME_QUADS) {
            float wx = (float) (q.x - camPos.x), wy = (float) (q.y - camPos.y), wz = (float) (q.z - camPos.z);
            float rx = right.x() * q.half, ry = right.y() * q.half, rz = right.z() * q.half;
            float ux = up.x() * q.half, uy = up.y() * q.half, uz = up.z() * q.half;

            bb.addVertex(wx - rx - ux, wy - ry - uy, wz - rz - uz).setUv(0f, 1f).setColor(q.r, q.g, q.b, q.a);
            bb.addVertex(wx + rx - ux, wy + ry - uy, wz + rz - uz).setUv(1f, 1f).setColor(q.r, q.g, q.b, q.a);
            bb.addVertex(wx + rx + ux, wy + ry + uy, wz + rz + uz).setUv(1f, 0f).setColor(q.r, q.g, q.b, q.a);
            bb.addVertex(wx - rx + ux, wy - ry + uy, wz - rz + uz).setUv(0f, 0f).setColor(q.r, q.g, q.b, q.a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Un eje del blur gaussiano separable — llamado dos veces por frame (horizontal, luego
     *  vertical), leyendo de {@code src} y escribiendo en {@code dst}, sin mezclar con lo que
     *  {@code dst} tuviera antes (blend desactivado: es una sustitución, no una acumulación). */
    private static void blurPass(TextureTarget src, TextureTarget dst, float dirX, float dirY) {
        dst.bindWrite(true);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        dst.clear(Minecraft.ON_OSX);

        RenderSystem.setShader(() -> blurShader);
        RenderSystem.setShaderTexture(0, src.getColorTextureId());
        blurShader.safeGetUniform("TexelSize").set(1f / src.width, 1f / src.height);
        blurShader.safeGetUniform("BlurDir").set(dirX, dirY);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Última pasada: bloomA (ya difuminado en los dos ejes) sobre el render target principal,
     *  aditivo — restaura el target y el viewport de ventana completa antes de dibujar. */
    private static void compositePass(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        main.bindWrite(true);

        RenderSystem.setShader(() -> compositeShader);
        RenderSystem.setShaderTexture(0, bloomA.getColorTextureId());
        compositeShader.safeGetUniform("Intensity").set(1.0f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** Quad en NDC (-1..1) con UV 0..1 — ki_bloom_blur.vsh pasa Position tal cual a gl_Position,
     *  sin matrices, así que esto vale igual para el blur que para el composite (mismo vertex
     *  shader, ver ki_bloom_composite.json). */
    private static void drawFullscreenQuad() {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        bb.addVertex(1f, -1f, 0f).setUv(1f, 0f);
        bb.addVertex(1f, 1f, 0f).setUv(1f, 1f);
        bb.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }
}
