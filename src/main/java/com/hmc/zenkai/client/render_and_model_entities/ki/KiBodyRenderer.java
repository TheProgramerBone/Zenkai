package com.hmc.zenkai.client.render_and_model_entities.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.ModAuraRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Envolvente + cuerpo de una bola o haz de ki: la parte que comparten el proyectil YA
 * disparado ({KiProjectileRenderer}) y la bola que se carga en la mano
 * ({@code KiChargeRenderer}).
 * ANTES cada uno tenía su propio dibujado: el proyectil la malla con shader de fresnel (o su
 * respaldo), la carga un quad plano orientado a cámara con ki_ball.png sin banda ni volumen.
 * El resultado era que la energía cambiaba de aspecto justo en el instante de soltarse —
 * pasaba de disco plano pixelado a esfera con núcleo y contorno — que es exactamente lo
 * contrario de lo que se espera de la MISMA bola. Con una única fuente, cargar y disparar
 * dibujan el mismo cuerpo a distinto tamaño y con distinta malla (esfera fija durante la
 * carga, la forma real de la técnica una vez volando); lo demás — color, bandas, alfas — sale
 * del mismo {@link KiVisual} en los dos casos, así que un ajuste de arte no puede quedar
 * aplicado solo en uno de los dos.
 * DOS RUTAS, misma fuente de parámetros: con el shader disponible el cuerpo lo pinta el
 * fragment por píxel (tres bandas); sin él se cae al respaldo, cáscara teñida con
 * {@code ki_ball.png} + núcleo encogido y lavado a blanco. Ver {@link KiRenderTypes#available()}.
 */
public final class KiBodyRenderer {
    private KiBodyRenderer() {}

    /** Textura de respaldo. Es la misma cáscara para el proyectil y la bola de carga: en la
     *  ruta sin shader las dos vuelven a ser literalmente el mismo dibujo. */
    public static final ResourceLocation BALL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");

    /**
     * Punto de entrada único: elige ruta y dibuja envolvente + cuerpo, a fuerza completa.
     * @param size escala uniforme aplicada a la malla (radio 0.5 en espacio de malla, salvo
     *             la esfera "desnuda" de la carga, que también mide 0.5).
     */
    public static void render(MultiBufferSource buffer, KiVisual v, KiMesh mesh,
                              PoseStack pose, float size, float r, float g, float b) {
        render(buffer, v, mesh, pose, size, r, g, b, 1f);
    }

    /**
     * Igual que {@link #render(MultiBufferSource, KiVisual, KiMesh, PoseStack, float, float,
     * float, float)} pero con un multiplicador de alfa sobre TODAS las capas (envolvente,
     * cáscara y núcleo). Lo usa KiChargeRenderer para el ajuste de opacidad en primera persona
     * y para que el desvanecido al soltar la carga atenúe también el cuerpo, no solo el halo.
     */
    public static void render(MultiBufferSource buffer, KiVisual v, KiMesh mesh,
                              PoseStack pose, float size, float r, float g, float b,
                              float alphaMul) {
        boolean shaded = KiRenderTypes.available() && buffer instanceof MultiBufferSource.BufferSource;
        if (shaded) {
            renderShaded((MultiBufferSource.BufferSource) buffer, v, mesh, pose, size, r, g, b, alphaMul);
        } else {
            renderFallback(buffer, v, mesh, pose, size, r, g, b, alphaMul);
        }
    }

    /**
     * Envolvente + cuerpo en UNA sola emisión de lote. Las dos comparten uniforms, así que no
     * hace falta un segundo volcado: la única diferencia es la escala y el alfa del vértice.
     * La envolvente es lo que quita el aspecto de canica sobrepuesta — el shader ya disuelve el
     * borde del cuerpo, pero una segunda capa más grande y casi invisible es lo que da el
     * volumen difuso alrededor, y a diferencia de un halo es geometría real: rodea la bola en
     * tres dimensiones en vez de encarar siempre a la cámara.
     */
    private static void renderShaded(MultiBufferSource.BufferSource buffer, KiVisual v, KiMesh mesh,
                                     PoseStack pose, float size, float r, float g, float b,
                                     float alphaMul) {
        RenderType type = KiRenderTypes.fresnel();
        KiRenderTypes.setupFresnel(v);
        VertexConsumer vc = buffer.getBuffer(type);

        if (v.hasEnvelope()) {
            pose.pushPose();
            float es = size * v.envelopeScale();
            pose.scale(es, es, es);
            mesh.emit(vc, pose.last(), r, g, b, v.envelopeAlpha() * alphaMul, 0f, false);
            pose.popPose();
        }

        pose.pushPose();
        pose.scale(size, size, size);
        // whiteMul 0: el tinte llega puro. La blancura horneada en la malla es la aproximación
        // de la ruta vieja y aquí sobra — el shader ya sabe dónde está el núcleo.
        mesh.emit(vc, pose.last(), r, g, b, v.shellAlpha() * alphaMul, 0f, false);
        pose.popPose();

        buffer.endBatch(type);
    }

    /** Ruta de respaldo: cáscara teñida + núcleo encogido y lavado a blanco. */
    private static void renderFallback(MultiBufferSource buffer, KiVisual v, KiMesh mesh,
                                       PoseStack pose, float size, float r, float g, float b,
                                       float alphaMul) {
        VertexConsumer vc = buffer.getBuffer(ModAuraRenderType.energyCrisp(BALL_TEXTURE));

        pose.pushPose();
        pose.scale(size, size, size);
        mesh.emit(vc, pose.last(), r, g, b, v.shellAlpha() * alphaMul, 1.0f, true);
        pose.popPose();

        if (v.coreAlpha() > 0f) {
            pose.pushPose();
            float cs = size * v.coreScale();
            pose.scale(cs, cs, cs);
            mesh.emit(vc, pose.last(), r, g, b, v.coreAlpha() * alphaMul, 4.0f, true);
            pose.popPose();
        }
    }
}
