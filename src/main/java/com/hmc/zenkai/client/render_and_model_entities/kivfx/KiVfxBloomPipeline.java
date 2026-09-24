package com.hmc.zenkai.client.render_and_model_entities.kivfx;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Dibujado del VFX de ki + bloom real — rework 2026-09-24 sobre el modelo de dragonminez
 * ({@code KiBloomRenderer}) y dbrebirth, los dos mods de referencia en los que el ki se mantiene
 * correcto con la cámara dentro/cerca de una técnica.
 * <p>
 * ═══ QUÉ HACE ═══ Consume {@link KiVfxFrameQueue} en un stage fijo y reproduce cada técnica
 * DOS veces:
 * <ol>
 *   <li><b>Mundo</b>: en el target que el motor tiene enlazado en ese stage (el principal en
 *       Fast/Fancy, {@code particlesTarget} en Fabulous — la cadena de transparencia lo compone
 *       después ordenando por profundidad).</li>
 *   <li><b>Bloom</b>: en {@code kiScene}, un target propio cuyo DEPTH ATTACHMENT es la propia
 *       textura de depth del render target principal (truco de dbrebirth: compartir, no copiar —
 *       sin blit ni formatos que casar con el stencil opcional de NeoForge). La técnica pasa el
 *       MISMO depth test real que en la pasada del mundo, con el mismo shader y culling: lo que
 *       un bloque o el jugador tapan no brilla, y una cáscara que la cámara tiene dentro no deja
 *       ningún sustituto plano en pantalla.</li>
 * </ol>
 * Después: blur separable a media resolución (dos iteraciones) y composición aditiva con un
 * limitador que conserva el tono (ver ki_bloom_composite.fsh).
 * <p>
 * ═══ LO QUE REEMPLAZA ═══ La versión anterior capturaba billboards SUSTITUTOS registrados por
 * los renderers (discos con la textura del halo). Ni con oclusión por depth ni con atenuación
 * por cámara representaban la forma real de la técnica — de ahí el disco con anillos. Además,
 * hasta el 2026-09-24 ni siquiera llegaba a pantalla: {@code RenderTarget.clear()} desenlaza el
 * framebuffer (ver "TRAMPAS DE GL" abajo).
 * <p>
 * ═══ TRAMPAS DE GL (cada una ya costó un bug) ═══
 * <ul>
 *   <li>{@code RenderTarget.clear()} termina con {@code unbindWrite()} (framebuffer 0): SIEMPRE
 *       {@code clear()} y DESPUÉS {@code bindWrite()}, nunca al revés.</li>
 *   <li>{@code clear()} usa el {@code clearChannels} PROPIO del target, por defecto (1,1,1,0) =
 *       blanco — no {@code RenderSystem.clearColor}.</li>
 *   <li>{@code kiScene} se crea con {@code useDepth=false} a propósito: así su {@code clear()}
 *       borra SOLO el color, jamás el depth del principal que lleva enganchado.</li>
 *   <li>{@code TextureTarget} solo crea RGBA8 + NEAREST: ver {@link #makeHdr}.</li>
 * </ul>
 * <p>
 * ═══ SHADERPACKS ═══ Con Iris/Oculus cargado ({@link IrisCompat}) la cola se reproduce en
 * AFTER_LEVEL sobre el principal (lo mismo que hace dragonminez con shaderpack) y SIN bloom.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiVfxBloomPipeline {
    private KiVfxBloomPipeline() {}

    /** true solo si el jugador no apagó el toggle Y no hay un shaderpack tipo Iris/Oculus
     *  cargado (ver IrisCompat) — postura conservadora pedida explícitamente. */
    public static boolean active() {
        return ClientConfig.kiBloomEnabled() && !IrisCompat.shaderPackActive();
    }

    // ── Shaders ─────────────────────────────────────────────────────────────

    private static final ResourceLocation BLUR_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_bloom_blur");
    private static final ResourceLocation COMPOSITE_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_bloom_composite");

    private static ShaderInstance blurShader;
    private static ShaderInstance compositeShader;

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
            Zenkai.LOGGER.error("[Zenkai] Los shaders de ki_bloom NO cargaron ({}). El bloom no "
                    + "tendrá efecto.", ex.toString());
        }
    }

    // ── Render targets ──────────────────────────────────────────────────────

    /** Resolución completa del principal: su depth attachment ES el depth del principal, y un
     *  attachment de framebuffer tiene que medir lo mismo que el resto. */
    private static TextureTarget kiScene;
    /** Media resolución: barato, y el upsample bilineal al componer ya aporta difuminado. */
    private static TextureTarget bloomA;
    private static TextureTarget bloomB;
    private static int sceneW = -1, sceneH = -1;
    private static boolean warnedIncomplete = false;

    private static void ensureTargets(int fullW, int fullH) {
        if (fullW == sceneW && fullH == sceneH && kiScene != null) return;
        int halfW = Math.max(2, fullW / 2);
        int halfH = Math.max(2, fullH / 2);
        kiScene = recreate(kiScene, fullW, fullH);
        bloomA = recreate(bloomA, halfW, halfH);
        bloomB = recreate(bloomB, halfW, halfH);
        sceneW = fullW;
        sceneH = fullH;
    }

    private static TextureTarget recreate(TextureTarget t, int w, int h) {
        if (t == null) t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else t.resize(w, h, Minecraft.ON_OSX);
        // resize() vuelve a crear la textura de color en RGBA8, así que se re-mejora cada vez.
        makeHdr(t);
        t.setClearColor(0f, 0f, 0f, 0f);
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    /**
     * Cambia el almacenamiento de la textura de color del target a RGBA16F. TextureTarget solo
     * sabe crear RGBA8, que recorta cada canal a 1.0 ANTES del blur y del composite: varias
     * técnicas solapadas perderían el tono y saldrían blancas. Re-especificar el nivel 0 de la
     * MISMA textura mantiene válido el attachment; si el driver no lo acepta como
     * color-renderable se vuelve a RGBA8 (el bloom sigue funcionando, solo con recorte), nunca se
     * deja un framebuffer incompleto.
     */
    private static void makeHdr(TextureTarget t) {
        GlStateManager._bindTexture(t.getColorTextureId());
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, t.width, t.height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.frameBufferId);
        if (GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, t.width, t.height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
            Zenkai.LOGGER.warn("[Zenkai] RGBA16F no soportado para el bloom de ki; se usa RGBA8.");
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._bindTexture(0);
    }

    /** Engancha el depth del principal como depth attachment de {@code kiScene}. Cada frame: si
     *  el principal se redimensiona, su textura de depth cambia de id. @return false si el
     *  framebuffer resultante no es completo (el frame se queda sin bloom, nunca se rompe). */
    private static boolean attachMainDepth(RenderTarget main) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, kiScene.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, main.getDepthTextureId(), 0);
        boolean ok = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        if (!ok && !warnedIncomplete) {
            warnedIncomplete = true;
            Zenkai.LOGGER.warn("[Zenkai] El target de bloom de ki no acepta el depth del principal; "
                    + "el bloom queda desactivado en este equipo.");
        }
        return ok;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (kiScene != null) { kiScene.destroyBuffers(); kiScene = null; }
        if (bloomA != null) { bloomA.destroyBuffers(); bloomA = null; }
        if (bloomB != null) { bloomB.destroyBuffers(); bloomB = null; }
        sceneW = -1;
        sceneH = -1;
    }

    // ── Orquestación por frame ──────────────────────────────────────────────

    // Fuerza del composite y codo del limitador suave de ki_bloom_composite.fsh (por debajo de
    // KNEE pasa intacto, por encima tiende a LIMIT escalando los tres canales por igual): viven
    // en KiVfxTuning (BLOOM_INTENSITY/KNEE/LIMIT) para poder calibrarlos en vivo con /zkvfx.

    /**
     * LOWEST: los listeners que ENCOLAN en este mismo stage (KiVfxChargeRenderer) tienen que
     * correr antes; con la misma prioridad el orden lo decidiría el escaneo de anotaciones.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        boolean iris = IrisCompat.shaderPackActive();
        RenderLevelStageEvent.Stage want = iris
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (event.getStage() != want) return;
        if (KiVfxFrameQueue.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // Target del mundo en este stage: particlesTarget en Fabulous, el principal si no.
        RenderTarget particles = mc.levelRenderer.getParticlesTarget();
        RenderTarget world = (!iris && particles != null) ? particles : main;

        KiVfxFrameQueue.sortBackToFront();
        world.bindWrite(true);
        if (iris) {
            // AFTER_LEVEL corre DESPUÉS de que LevelRenderer desapile su ModelView (la rotación
            // de cámara): las matrices encoladas solo llevan la traslación relativa a cámara,
            // así que sin volver a aplicarla aquí las técnicas saldrían sin rotar.
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            mv.pushMatrix();
            mv.mul(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            try {
                KiVfxFrameQueue.replay(false);
            } finally {
                mv.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        } else {
            KiVfxFrameQueue.replay(false);
        }

        if (active() && blurShader != null && compositeShader != null) {
            bloom(main);
            world.bindWrite(true);
        }
        KiVfxFrameQueue.clear();
    }

    private static void bloom(RenderTarget main) {
        ensureTargets(main.width, main.height);

        kiScene.clear(Minecraft.ON_OSX);            // solo color: useDepth=false
        if (!attachMainDepth(main)) return;
        kiScene.bindWrite(true);
        KiVfxFrameQueue.replay(true);

        // Dos iteraciones H+V: la primera también reduce de resolución completa a media.
        blurPass(kiScene, bloomA, 1f, 0f);
        blurPass(bloomA, bloomB, 0f, 1f);
        blurPass(bloomB, bloomA, 1f, 0f);
        blurPass(bloomA, bloomB, 0f, 1f);

        compositePass(main, bloomB);
    }

    private static void blurPass(TextureTarget src, TextureTarget dst, float dirX, float dirY) {
        dst.clear(Minecraft.ON_OSX);
        dst.bindWrite(true);

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

    private static void compositePass(RenderTarget main, TextureTarget bloom) {
        main.bindWrite(true);

        RenderSystem.setShader(() -> compositeShader);
        RenderSystem.setShaderTexture(0, bloom.getColorTextureId());
        compositeShader.safeGetUniform("Intensity").set(tuned(KiVfxTuning.Param.BLOOM_INTENSITY));
        compositeShader.safeGetUniform("Knee").set(tuned(KiVfxTuning.Param.BLOOM_KNEE));
        // Limit > Knee siempre: el shader divide por (Limit - Knee).
        compositeShader.safeGetUniform("Limit").set(Math.max(tuned(KiVfxTuning.Param.BLOOM_LIMIT),
                tuned(KiVfxTuning.Param.BLOOM_KNEE) + 0.01f));

        RenderSystem.enableBlend();
        // ONE, ONE: el shader ya entrega la energía final (alfa 0, el alfa del main no se toca).
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        // Sin depth test a propósito: el bloom es de pantalla y su derrame alrededor de una
        // fuente VISIBLE es correcto. La oclusión ya se resolvió al dibujar en kiScene.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float tuned(KiVfxTuning.Param p) {
        return KiVfxTuning.get(p, (com.hmc.zenkai.feature.technique.KiTechniqueType) null);
    }

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
