package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.client.aura.ModAuraRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;

/**
 * Envolvente + cáscara + núcleo explícito de una bola o haz de ki: la parte que comparten el
 * proyectil ya disparado ({@link com.hmc.zenkai.client.render_and_model_entities.entity.KiVfxProjectileRenderer})
 * y la bola que se carga en la mano ({@link com.hmc.zenkai.client.KiVfxChargeRenderer}). Con una
 * única fuente, cargar y disparar dibujan el MISMO cuerpo a distinto tamaño y con distinta malla
 * (esfera fija durante la carga, la forma real de la técnica una vez volando) — color, bandas y
 * alfas salen del mismo {@link KiVfxProfile} en los dos casos.
 *
 * DOS RUTAS, misma fuente de parámetros: con el shader disponible el cuerpo lo pinta el fragment
 * por píxel (tres bandas) MÁS un núcleo explícito aparte; sin él se cae al respaldo, cáscara
 * teñida con textura + núcleo encogido y lavado a blanco. Ver {@link KiVfxRenderTypes#available()}
 * — un fallo del shader (driver viejo, shaderpack) no puede hacer desaparecer técnicas que hacen
 * daño real.
 *
 * ORDEN DE DIBUJO (de fuera hacia dentro): envolvente → cáscara → núcleo. Los tres comparten
 * uniforms (mismo RenderType de energía) salvo el núcleo, que va en un SEGUNDO volcado aditivo —
 * un {@code MultiBufferSource.BufferSource} solo tiene un {@code sharedBuffer} para un RenderType
 * custom no-fijo, así que hay que cerrar (endBatch) el de energía antes de pedir el aditivo.
 */
public final class KiVfxCompositeRenderer {
    private KiVfxCompositeRenderer() {}

    public static void render(MultiBufferSource buffer, KiVfxProfile p, KiVfxMesh mesh,
                              PoseStack pose, float size, float r, float g, float b) {
        // proximity 1f: caller sin cámara de mundo real (vista previa de TechniqueEditScreen).
        // coreMesh null: esa vista previa no necesita nailar el núcleo, solo el aspecto general.
        render(buffer, p, mesh, null, pose, size, r, g, b, 1f, false, 1f);
    }

    public static void render(MultiBufferSource buffer, KiVfxProfile p, KiVfxMesh mesh, @Nullable KiVfxMesh coreMesh,
                              PoseStack pose, float size, float r, float g, float b,
                              float alphaMul, float proximity) {
        render(buffer, p, mesh, coreMesh, pose, size, r, g, b, alphaMul, false, proximity);
    }

    /**
     * @param coreMesh   null = sin núcleo explícito para esta técnica, o el caller no lo calculó.
     * @param frozenAnim true SOLO para entidades congeladas por "/zenkai debug kivfx" — fija la
     *                   fase del parpadeo del shader para que dos capturas de la MISMA copia
     *                   congelada se vean siempre igual.
     * @param proximity  ver {@link KiVfxRenderTypes#proximity(double, float)}.
     */
    public static void render(MultiBufferSource buffer, KiVfxProfile p, KiVfxMesh mesh, @Nullable KiVfxMesh coreMesh,
                              PoseStack pose, float size, float r, float g, float b,
                              float alphaMul, boolean frozenAnim, float proximity) {
        boolean shaded = KiVfxRenderTypes.available() && buffer instanceof MultiBufferSource.BufferSource;
        if (shaded) {
            renderShaded((MultiBufferSource.BufferSource) buffer, p, mesh, coreMesh, pose, size, r, g, b,
                    alphaMul, frozenAnim, proximity);
        } else {
            renderFallback(buffer, p, mesh, pose, size, r, g, b, alphaMul);
        }
    }

    private static void renderShaded(MultiBufferSource.BufferSource buffer, KiVfxProfile p, KiVfxMesh mesh,
                                     @Nullable KiVfxMesh coreMesh, PoseStack pose, float size,
                                     float r, float g, float b,
                                     float alphaMul, boolean frozenAnim, float proximity) {
        KiVfxDebugMode dbg = KiVfxDebugMode.current();

        if (dbg.wireframe()) {
            KiVfxRenderTypes.withWireframe(() -> drawShellAndEnvelope(buffer, p, mesh, pose, size, r, g, b,
                    alphaMul, frozenAnim, proximity, dbg));
            return;
        }

        if (dbg.showsShell()) {
            drawShellAndEnvelope(buffer, p, mesh, pose, size, r, g, b, alphaMul, frozenAnim, proximity, dbg);
        }

        if (dbg.showsCore() && coreMesh != null && p.hasExplicitCore()) {
            drawCore(buffer, p, coreMesh, pose, size, r, g, b, alphaMul);
        }
    }

    private static void drawShellAndEnvelope(MultiBufferSource.BufferSource buffer, KiVfxProfile p, KiVfxMesh mesh,
                                             PoseStack pose, float size, float r, float g, float b,
                                             float alphaMul, boolean frozenAnim, float proximity,
                                             KiVfxDebugMode dbg) {
        RenderType type = KiVfxRenderTypes.energy(p);
        KiVfxRenderTypes.setupEnergy(p, frozenAnim, proximity);
        VertexConsumer vc = buffer.getBuffer(type);

        if (dbg.showsEnvelope() && p.envelope().enabled()) {
            pose.pushPose();
            float es = size * p.envelope().scale();
            pose.scale(es, es, es);
            mesh.emit(vc, pose.last(), r, g, b, p.envelope().alpha() * alphaMul, 0f, false);
            pose.popPose();
        }

        pose.pushPose();
        pose.scale(size, size, size);
        // whiteMul 0: el tinte llega puro — la blancura horneada en la malla es la aproximación
        // de la ruta de respaldo; con shader el centro brillante lo pone el núcleo explícito.
        mesh.emit(vc, pose.last(), r, g, b, p.shell().alpha() * alphaMul, 0f, false);
        pose.popPose();

        buffer.endBatch(type);
    }

    // Fracción del alfa del núcleo explícito en la pasada del mundo: KiVfxTuning.CORE_WORLD_ALPHA
    // (/zkvfx set core.world_alpha), ver drawCore.

    private static void drawCore(MultiBufferSource.BufferSource buffer, KiVfxProfile p, KiVfxMesh coreMesh,
                                 PoseStack pose, float size, float r, float g, float b, float alphaMul) {
        // Blanco hacia el tinte por el mismo factor que la cáscara (coreWhite) — misma idea de
        // "cuánto se lava a blanco" en las dos capas, solo que aquí el tamaño lo decide geometría
        // real, no un ángulo de vista. HALO_TEXTURE: glow radial suave con UV plana, un aditivo
        // uniforme sin costuras sobre geometría 3D real.
        float white = Math.min(1f, p.shell().coreWhite()
                * KiVfxTuning.get(KiVfxTuning.Param.CORE_WHITE_MUL, p));
        float cr = r + (1f - r) * white;
        float cg = g + (1f - g) * white;
        float cb = b + (1f - b) * white;
        RenderType type = KiVfxRenderTypes.glow(KiVfxRenderTypes.HALO_TEXTURE);
        VertexConsumer cvc = buffer.getBuffer(type);
        pose.pushPose();
        pose.scale(size, size, size);
        // Desplazamiento del "sol descentrado" (Death Ball/Supernova, ver KiVfxProfile.Core) —
        // EN EL ESPACIO YA ESCALADO por size, así que offsetX/Y son fracciones del tamaño real de
        // la técnica y no bloques absolutos: una Genki Dama y un Death Beam con el mismo offset
        // relativo se desplazan proporcionalmente a su propio tamaño, no por el mismo número fijo.
        if (p.core().offsetX() != 0f || p.core().offsetY() != 0f) {
            pose.translate(p.core().offsetX(), p.core().offsetY(), 0f);
        }
        // Pasada del MUNDO: núcleo translúcido. Desde que glow() dejó de ser aditivo (2026-09-24),
        // un núcleo casi blanco a su alfa de tabla (~0.9) TAPABA lo de detrás — conos blancos
        // macizos y, en EXPLOSION, una esfera opaca que ocultaba al propio jugador (vídeo
        // 2026-09-24 09-10-44). El brillo del centro lo aporta la pasada de BLOOM, que lo usa
        // entero (ver KiVfxFrameQueue.bloomPass).
        float coreAlpha = p.core().alpha() * alphaMul
                * KiVfxTuning.get(KiVfxTuning.Param.CORE_ALPHA_MUL, p)
                * (KiVfxFrameQueue.bloomPass() ? 1f : KiVfxTuning.get(KiVfxTuning.Param.CORE_WORLD_ALPHA, p));
        coreMesh.emit(cvc, pose.last(), cr, cg, cb, coreAlpha, 0f, true);
        pose.popPose();
        buffer.endBatch(type);
    }

    /** Ruta de respaldo (shader no disponible): cáscara teñida + núcleo encogido y lavado a
     *  blanco, sin modos de depuración — es una ruta de resiliencia, no la que se inspecciona. */
    private static void renderFallback(MultiBufferSource buffer, KiVfxProfile p, KiVfxMesh mesh,
                                       PoseStack pose, float size, float r, float g, float b,
                                       float alphaMul) {
        VertexConsumer vc = buffer.getBuffer(ModAuraRenderType.energyCrisp(KiVfxRenderTypes.BALL_TEXTURE));

        pose.pushPose();
        pose.scale(size, size, size);
        mesh.emit(vc, pose.last(), r, g, b, p.shell().alpha() * alphaMul, 1.0f, true);
        pose.popPose();

        if (p.core().alpha() > 0f) {
            pose.pushPose();
            float cs = size * p.core().scale();
            pose.scale(cs, cs, cs);
            mesh.emit(vc, pose.last(), r, g, b, p.core().alpha() * alphaMul, 4.0f, true);
            pose.popPose();
        }
    }
}
