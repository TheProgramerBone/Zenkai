package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.ki.KiBodyRenderer;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiMesh;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiMeshFactory;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiRenderTypes;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiShape;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiVisual;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Render de proyectil de ki, v2.
 *
 * EL CUERPO (malla + envolvente, shader o respaldo) VIVE EN {@link KiBodyRenderer}, compartido
 * con la bola que se carga en la mano antes de soltarse: es lo que hace que cargar y disparar
 * se lean como la MISMA energía en vez de cambiar de aspecto en el instante del disparo. Lo que
 * sí es exclusivo de aquí — porque solo tienen sentido con un proyectil que VUELA — es la
 * estela, las chispas y la orientación por velocidad.
 *
 * LA ESCALA ES UNIFORME. La longitud de los haces va HORNEADA en la malla (ver KiMeshFactory):
 * escalar Z aparte estiraba también el tubo de las hélices y convertía las cintas de la onda
 * en lóbulos alargados.
 *
 * Capas, de fuera hacia dentro: estela → halo → envolvente → cuerpo.
 */
public class KiProjectileRenderer extends EntityRenderer<KiProjectileEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Techo global de chispas por tick entre TODOS los proyectiles. Una ráfaga de veinte bolas
     *  grandes pedía cientos de partículas por tick y hundía los frames sin que se notara la
     *  diferencia visual. Se reinicia con el tick del cliente. */
    private static final int SPARK_BUDGET_PER_TICK = 24;
    private static int sparkBudget = SPARK_BUDGET_PER_TICK;
    private static long sparkBudgetTick = Long.MIN_VALUE;

    public KiProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(KiProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        KiVisual v = KiVisual.of(entity.techniqueType());
        int rgb = entity.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        if (v.hasTrail() && entity.trailHistory().size() >= 2) {
            renderTrail(entity, v, partialTick, pose, buffer, r, g, b);
        }

        renderHalo(entity, v, partialTick, pose, buffer, r, g, b);

        // Respiración: una energía contenida no está nunca perfectamente quieta. Es lo bastante
        // lenta y pequeña para no leerse como parpadeo, y a cambio quita el aspecto de objeto.
        float breathe = 1f + 0.035f * Mth.sin((entity.tickCount + partialTick) * 0.31f);
        float size = entity.getBbWidth() * 1.5f * breathe;   // el cuerpo sobresale del hitbox

        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);

        // Las formas alargadas se orientan con la VELOCIDAD, no con el yaw: un láser apunta a
        // donde va, y el yaw no lleva el cabeceo.
        if (v.shape() != KiShape.SPHERE) {
            Vec3 vel = entity.getDeltaMovement();
            if (vel.lengthSqr() > 1.0e-6) {
                vel = vel.normalize();
                pose.mulPose(Axis.YP.rotationDegrees(
                        (float) (Math.atan2(vel.x, vel.z) * 180.0 / Math.PI)));
                pose.mulPose(Axis.XP.rotationDegrees(
                        (float) (-Math.asin(vel.y) * 180.0 / Math.PI)));
            }
        }

        if (v.shape() == KiShape.DISK) {
            // CARA AL FRENTE. La normal de la malla va con la velocidad, así que quien dispara
            // ve la cara del disco. Se probó lo contrario (normal perpendicular, vuelo de
            // canto): desde detrás el disco se reducía a una raya, porque un plano sin espesor
            // no tiene nada que enseñar de perfil. El espesor lo arregla la lente de
            // KiMeshFactory; la orientación tiene que priorizar la silueta reconocible.
            pose.mulPose(Axis.XP.rotationDegrees(24f));   // ladeo: elipse escorzada, no círculo
            // El giro es sobre la normal y en un disco simétrico no cambia la silueta: lo que
            // mueve es el patrón de hervor, que se muestrea con la UV angular.
            pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 22f));
        }

        KiMesh mesh = KiMeshFactory.get(v);
        KiBodyRenderer.render(buffer, v, mesh, pose, size, r, g, b);

        pose.popPose();

        spawnSparks(entity, v, rgb);

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    // ── Halo ────────────────────────────────────────────────────────────────

    /**
     * Quad orientado a cámara con el degradado radial. Es el sustituto del bloom real: no
     * cuesta un post-proceso ni pelea con Iris, y a cambio no tiñe el mundo alrededor.
     *
     * VA FLOJO A PROPÓSITO. Es aditivo y se dibuja sobre el cuerpo, así que subirlo lava el
     * color de la técnica hasta dejarlo blanco y convierte el conjunto en un disco plano con
     * un anillo. El brillo lo pone el núcleo del shader; esto solo es el desbordamiento.
     */
    private void renderHalo(KiProjectileEntity e, KiVisual v, float partialTick, PoseStack pose,
                            MultiBufferSource buffer, float r, float g, float b) {
        if (v.haloAlpha() <= 0f) return;

        float pulse = 1f + 0.07f * Mth.sin((e.tickCount + partialTick) * 0.22f);
        float half = e.getBbWidth() * v.haloScale() * 0.5f * pulse;

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose mat = pose.last();

        VertexConsumer vc = buffer.getBuffer(KiRenderTypes.additive(KiRenderTypes.HALO_TEXTURE));
        float a = v.haloAlpha();
        vert(vc, mat, -half, -half, 0, r, g, b, a, 0f, 1f);
        vert(vc, mat,  half, -half, 0, r, g, b, a, 1f, 1f);
        vert(vc, mat,  half,  half, 0, r, g, b, a, 1f, 0f);
        vert(vc, mat, -half,  half, 0, r, g, b, a, 0f, 0f);

        pose.popPose();
    }

    // ── Estela ──────────────────────────────────────────────────────────────

    /**
     * Dos cintas sobre las mismas posiciones históricas: una ancha teñida y otra estrecha casi
     * blanca y aditiva. Ese par es lo que produce el núcleo caliente dentro del color; con una
     * sola capa la estela se lee como una tira de plástico.
     * El desplazamiento de las UV a lo largo de la cinta da la sensación de flujo hacia atrás,
     * que es lo que impide que la estela parezca una cinta rígida arrastrada.
     */
    private void renderTrail(KiProjectileEntity e, KiVisual v, float partialTick, PoseStack pose,
                             MultiBufferSource buffer, float r, float g, float b) {
        List<Vec3> all = new ArrayList<>(e.trailHistory());   // [0] = más reciente
        int n = Math.min(all.size(), v.trailPoints());
        if (n < 2) return;

        List<Vec3> pts = new ArrayList<>(n + 1);
        Vec3 feet = e.getPosition(partialTick);               // origen del PoseStack (pies)
        pts.add(feet.add(0, e.getBbHeight() * 0.5, 0));       // cabeza interpolada: pegada al cuerpo
        for (int i = 0; i < n; i++) pts.add(all.get(i));

        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        float scroll = -(e.tickCount + partialTick) * v.trailScroll() * 0.05f;
        float outer = e.getBbWidth() * v.trailWidth();

        ribbon(buffer.getBuffer(KiRenderTypes.soft(KiRenderTypes.TRAIL_TEXTURE)),
                pose.last(), pts, feet, cam, outer, scroll, r, g, b, v.trailAlpha());

        // Núcleo: el tinte empujado hacia blanco, no blanco puro, y con menos alfa que la capa
        // teñida. Al ser aditivo satura muy rápido: con los valores altos de antes la estela
        // entera salía blanca y el color de la técnica desaparecía. Opcional: en cuerpos grandes
        // y lentos (big blast) sobra, y se desactiva con trailInnerMul 0.
        if (v.hasTrailCore()) {
            float cr = r + (1f - r) * 0.55f, cg = g + (1f - g) * 0.55f, cb = b + (1f - b) * 0.55f;
            ribbon(buffer.getBuffer(KiRenderTypes.additive(KiRenderTypes.TRAIL_TEXTURE)),
                    pose.last(), pts, feet, cam, outer * v.trailInnerMul(), scroll,
                    cr, cg, cb, v.trailAlpha() * 0.70f);
        }
    }

    /**
     * Cinta en espacio mundo (el PoseStack llega centrado en la posición de render de la
     * entidad: cada vértice = puntoMundo − posiciónRender). Cada punto genera un par de
     * vértices desplazados por el vector lateral (dirección × haciaCámara) para encarar la
     * cámara. Doble cara: los RenderType de entidad hacen cull.
     */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose mat, List<Vec3> pts, Vec3 feet,
                               Vec3 cam, float fullWidth, float scroll,
                               float r, float g, float b, float headAlpha) {
        int n = pts.size();
        Vec3 prevL = null, prevR = null;
        float prevA = 0, prevV = 0;
        for (int i = 0; i < n; i++) {
            Vec3 pt = pts.get(i);
            Vec3 dir = (i < n - 1)
                    ? pts.get(i + 1).subtract(pt)
                    : pt.subtract(pts.get(i - 1));   // último punto: prolonga el segmento anterior
            Vec3 side = dir.cross(cam.subtract(pt));
            side = side.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : side.normalize();

            float t = 1f - (float) i / (n - 1);      // 1 cabeza -> 0 cola
            float half = fullWidth * 0.5f * (0.22f + 0.78f * t);
            float alpha = headAlpha * t * t;         // cuadrático: la cola se apaga antes y no
                                                     // deja un rabo largo y sucio detrás
            float v = (float) i / (n - 1) + scroll;

            Vec3 vL = pt.add(side.scale(half)).subtract(feet);
            Vec3 vR = pt.subtract(side.scale(half)).subtract(feet);

            if (i > 0) {
                quad(vc, mat, prevL, prevR, vR, vL, r, g, b, prevA, alpha, prevV, v);
                quad(vc, mat, vL, vR, prevR, prevL, r, g, b, alpha, prevA, v, prevV);
            }
            prevL = vL; prevR = vR; prevA = alpha; prevV = v;
        }
    }

    // ── Chispas ─────────────────────────────────────────────────────────────

    /**
     * Chispas sueltas mientras vuela. Se emiten desde el RENDER y no desde el tick de la
     * entidad para no meter una llamada a una clase de cliente dentro de código común; a cambio
     * hay que desduplicar por tick, porque render() corre por frame.
     * Presupuesto global compartido: ver SPARK_BUDGET_PER_TICK.
     */
    private static void spawnSparks(KiProjectileEntity e, KiVisual v, int rgb) {
        if (v.sparkRate() <= 0f || !e.consumeFxTick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        if (now != sparkBudgetTick) {
            sparkBudgetTick = now;
            sparkBudget = SPARK_BUDGET_PER_TICK;
        }
        if (sparkBudget <= 0) return;

        float rate = v.sparkRate() * (0.6f + 0.4f * e.size());
        int count = (int) rate;
        if (mc.level.random.nextFloat() < rate - count) count++;
        count = Math.min(count, sparkBudget);
        if (count <= 0) return;
        sparkBudget -= count;

        double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.5, cz = e.getZ();
        double spread = e.getBbWidth() * 0.55;
        Vec3 back = e.getDeltaMovement().scale(-0.25);
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(ModParticles.spark(rgb, 0.8f + 0.5f * e.size()),
                    cx + (mc.level.random.nextDouble() - 0.5) * spread,
                    cy + (mc.level.random.nextDouble() - 0.5) * spread,
                    cz + (mc.level.random.nextDouble() - 0.5) * spread,
                    back.x + (mc.level.random.nextDouble() - 0.5) * 0.06,
                    back.y + (mc.level.random.nextDouble() - 0.5) * 0.06,
                    back.z + (mc.level.random.nextDouble() - 0.5) * 0.06);
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private static void quad(VertexConsumer vc, PoseStack.Pose mat,
                             Vec3 aL, Vec3 aR, Vec3 bR, Vec3 bL,
                             float r, float g, float b,
                             float aAlpha, float bAlpha, float aV, float bV) {
        vert(vc, mat, aL, r, g, b, aAlpha, 0, aV);
        vert(vc, mat, aR, r, g, b, aAlpha, 1, aV);
        vert(vc, mat, bR, r, g, b, bAlpha, 1, bV);
        vert(vc, mat, bL, r, g, b, bAlpha, 0, bV);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose mat, Vec3 p,
                             float r, float g, float b, float a, float u, float v) {
        vert(vc, mat, (float) p.x, (float) p.y, (float) p.z, r, g, b, a, u, v);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose mat, float x, float y, float z,
                             float r, float g, float b, float a, float u, float v) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(mat, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(KiProjectileEntity entity) {
        return KiBodyRenderer.BALL_TEXTURE;
    }
}
