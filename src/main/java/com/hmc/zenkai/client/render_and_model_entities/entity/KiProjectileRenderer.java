package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.ki.KiBloomPipeline;
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
import net.minecraft.world.entity.Entity;
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

    /** Techo global de chispas por tick entre cada uno de los proyectiles. Una ráfaga de veinte bolas
     *  grandes pedía cientos de partículas por tick y hundía los frames sin que se notara la
     *  diferencia visual. Se reinicia con el tick del cliente. */
    private static final int SPARK_BUDGET_PER_TICK = 24;
    private static int sparkBudget = SPARK_BUDGET_PER_TICK;
    private static long sparkBudgetTick = Long.MIN_VALUE;

    /** Ticks tras el disparo en los que el cuerpo crece de 0 a tamaño completo (ver popScale en
     *  render()) — inspirado en el "pop" de aparición de dbrebirth-0.3 (su kicyl.vsh escala la
     *  malla entera con el mismo smoothstep sobre ticksexisted/5 en el vertex shader; aquí el
     *  escalado de malla ya vive 100% en CPU vía PoseStack, así que basta multiplicar `size`, sin
     *  tocar ki_fresnel.vsh). Solo afecta al CUERPO — el halo (renderHalo) mantiene su alfa/tamaño
     *  normal desde el primer frame, a propósito, para no interferir con su propio pulse. */
    private static final float POP_TICKS = 5f;

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

        // Lo que no viaja (BARRIER, la mecha de EXPLOSION — las dos ÚNICAS que devuelven false
        // en KiTechniqueType.travels()) va pegado al dueño, y en primera persona su cáscara
        // puede llegar a envolver la cámara. Pedido explícito: en primera persona propia esta
        // fase se OCULTA TOTALMENTE, no solo atenuada — ni con el slider "Ki Ball Opacity" al
        // mínimo se veía bien, la cáscara pegada a la cámara y el halo aditivo quedaban feos
        // igual. La carga (antes de soltar, KiChargeRenderer) no pasa por aquí y sigue las
        // reglas normales — salvo EXPLOSION, que allí también se oculta TOTALMENTE por el mismo
        // pedido (ver su comentario, el suelo de tamaño-jugador la pega a la cámara desde que
        // empieza a cargar).
        float alphaMul = 1f;
        float haloOpacity = 1f;
        if (!entity.techniqueType().travels()) {
            alignToOwner(entity, partialTick, pose);
            Minecraft mc = Minecraft.getInstance();
            if (entity.getOwner() == mc.player && mc.options.getCameraType().isFirstPerson()) {
                alphaMul = 0f;
                haloOpacity = 0f;
            }
        }

        if (v.hasTrail() && entity.trailHistory().size() >= 2) {
            renderTrail(entity, v, partialTick, pose, buffer, r, g, b);
        }

        renderHalo(entity, v, partialTick, pose, buffer, r, g, b, haloOpacity);

        // Respiración: una energía contenida no está nunca perfectamente quieta. Es lo bastante
        // lenta y pequeña para no leerse como parpadeo, y a cambio quita el aspecto de objeto.
        float breathe = 1f + 0.035f * Mth.sin((entity.tickCount + partialTick) * 0.31f);
        // Pop de aparición: smoothstep de 0 a 1 en los primeros POP_TICKS tras el disparo, igual
        // que la malla vuela a tamaño completo de golpe si se omite este factor. tickCount ya se
        // usa arriba para breathe, así que no hace falta ningún dato nuevo por proyectil.
        float popT = Mth.clamp((entity.tickCount + partialTick) / POP_TICKS, 0f, 1f);
        float popScale = popT * popT * (3f - 2f * popT);
        float size = entity.getBbWidth() * 1.5f * breathe * popScale;   // el cuerpo sobresale del hitbox

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
            //
            // El ladeo (antes 24f) es lo único que saca al disco de "cara exactamente al
            // frente": con un ángulo pequeño, visto de perfil (perpendicular al vuelo, que es
            // como se ve desde el lateral o mientras cruza por delante) apenas se distinguía del
            // canto — se leía como una hoja/espada vertical, no como un disco. Con un ladeo
            // mayor la cara se inclina hacia arriba/abajo, así que de perfil se ve como un plato
            // tumbado (elipse ancha y baja) en vez de una lámina de pie — la silueta de "disco
            // volador" que se buscaba. Sigue sin ser 90° (quedaría plano totalmente y perdería
            // profundidad al mirarlo de frente): ajustar a ojo en juego si 60° no cuadra.
            // KiChargeRenderer.DISK_CANT_DEGREES repite este mismo valor para cuando el disco
            // ya toma esta forma MIENTRAS CARGA (en vez de la esfera genérica) — tocar uno sin
            // el otro deja "cargar" y "disparar" con un ladeo distinto.
            pose.mulPose(Axis.XP.rotationDegrees(60f));
            // El giro es sobre la normal y en un disco simétrico no cambia la silueta: lo que
            // mueve es el patrón de hervor, que se muestrea con la UV angular.
            pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 22f));
        }

        KiMesh mesh = KiMeshFactory.get(v);
        KiBodyRenderer.render(buffer, v, mesh, pose, size, r, g, b, alphaMul);

        pose.popPose();

        spawnSparks(entity, v, rgb);

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /**
     * Lo que NO viaja (BARRIER, la mecha de EXPLOSION) fija su posición una vez por TICK de
     * juego, igualando la del dueño (ver KiProjectileEntity.tickAttached) — así que la
     * interpolación normal de esta entidad la deja un frame por detrás de la posición REAL del
     * dueño entre dos ticks. Para BARRIER, cuya cáscara envuelve la cámara, ese desfase de
     * pocos centímetros se ve como un ligero "nado" al moverse: la superficie está demasiado
     * cerca del ojo para que el desfase pase desapercibido.
     * Se corrige compensando el PoseStack (ya trasladado por el dispatcher a la posición
     * interpolada de ESTA entidad) hasta la posición interpolada DEL DUEÑO — la misma fuente
     * que ya usa el propio render del dueño (y, si es el jugador local, la cámara) — en vez de
     * fiarse de la interpolación tick-a-tick de esta entidad. Mismo desplazamiento vertical que
     * tickAttached: centro del dueño menos la mitad de la altura de esta entidad.
     */
    private static void alignToOwner(KiProjectileEntity entity, float partialTick, PoseStack pose) {
        Entity owner = entity.getOwner();
        if (owner == null) return;
        Vec3 wantFeet = owner.getPosition(partialTick)
                .add(0, owner.getBbHeight() * 0.5 - entity.getBbHeight() * 0.5, 0);
        Vec3 haveFeet = entity.getPosition(partialTick);
        pose.translate(wantFeet.x - haveFeet.x, wantFeet.y - haveFeet.y, wantFeet.z - haveFeet.z);
    }

    // ── Halo ────────────────────────────────────────────────────────────────

    /**
     * Quad orientado a cámara con el degradado radial. Es el sustituto del bloom real EN SU
     * MODO NORMAL: no cuesta un post-proceso ni pelea con Iris, y a cambio no tiñe el mundo
     * alrededor. Desde KiBloomPipeline, un bloom real SÍ existe como capa extra opt-in — ver
     * ClientConfig#kiBloomEnabled y el javadoc de esa clase para el porqué está apagado por
     * defecto y se rinde automáticamente si hay un shaderpack (IrisCompat).
     *
     * VA FLOJO A PROPÓSITO. Es aditivo y se dibuja sobre el cuerpo, así que subirlo lava el
     * color de la técnica hasta dejarlo blanco y convierte el conjunto en un disco plano con
     * un anillo. El brillo lo pone el núcleo del shader; esto solo es el desbordamiento.
     *
     * `haloOpacity` NO es el `alphaMul` del cuerpo: para primera persona propia de una técnica
     * que puede envolver la cámara (KiVisual.backfaceCull, BARRIER/EXPLOSION), el cuerpo aplica
     * un dampen extra (KiVisual.firstPersonOpacity) pensado para su alfa normal. El halo es
     * SIEMPRE aditivo (KiRenderTypes.ADDITIVE_TRANSPARENCY suma luz, no mezcla), así que se
     * rige directo por el valor crudo del slider "Ki Ball Opacity" — sin ese dampen extra — para
     * que sea ese único número, ajustable por el jugador, el que decida qué tan marcado se ve.
     */
    private void renderHalo(KiProjectileEntity e, KiVisual v, float partialTick, PoseStack pose,
                            MultiBufferSource buffer, float r, float g, float b,
                            float haloOpacity) {
        if (v.haloAlpha() <= 0f || haloOpacity <= 0f) return;

        float pulse = 1f + 0.07f * Mth.sin((e.tickCount + partialTick) * 0.22f);
        float half = e.getBbWidth() * v.haloScale() * 0.5f * pulse;

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose mat = pose.last();

        VertexConsumer vc = buffer.getBuffer(KiRenderTypes.additive(KiRenderTypes.HALO_TEXTURE));
        float a = v.haloAlpha() * haloOpacity;
        vert(vc, mat, -half, -half, 0, r, g, b, a, 0f, 1f);
        vert(vc, mat,  half, -half, 0, r, g, b, a, 1f, 1f);
        vert(vc, mat,  half,  half, 0, r, g, b, a, 1f, 0f);
        vert(vc, mat, -half,  half, 0, r, g, b, a, 0f, 0f);

        pose.popPose();

        // Capa EXTRA opt-in, encima del aditivo de arriba que se acaba de dibujar igual que
        // siempre — active() ya comprueba el toggle de cliente y IrisCompat, así que este
        // registro es gratis (una comprobación de booleano) cuando está apagado.
        if (KiBloomPipeline.active()) {
            KiBloomPipeline.registerHaloQuad(
                    e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), half, r, g, b, a);
        }
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

        if (v.helixTrail()) {
            renderHelixTrail(e, v, pts, feet, cam, outer, scroll, r, g, b, pose.last(), buffer);
            return;
        }

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
     * Estela en doble hélice CONTINUA ({@link KiVisual#helixTrail}, hoy solo SPIRAL): en vez de
     * una cinta plana, cada una de las dos hebras desplaza los puntos históricos con la MISMA
     * fórmula de torsión que ya hornea {@code KiMeshFactory.helix} en la malla del proyectil
     * ({@link KiMeshFactory#helixAngleFromTip}), evaluada más allá de {@code meshLength} — así
     * la estela SIGUE girando en el mismo sentido y al mismo ritmo en vez de cortar a una cinta
     * recta justo donde termina la malla horneada.
     * La base perpendicular (derecha/arriba) sale de {@link KiProjectileEntity#helixBasis()},
     * fijada una sola vez al disparar: recalcularla contra la dirección instantánea retorcería
     * la hélice de golpe en cuanto el proyectil gire (HOMING).
     * Reutiliza {@code ribbon()} tal cual (misma capa exterior teñida + núcleo blanco aditivo
     * opcional) — lo único que cambia es la LISTA de puntos que recibe cada llamada, no la
     * textura ni el RenderType.
     */
    private void renderHelixTrail(KiProjectileEntity e, KiVisual v, List<Vec3> pts, Vec3 feet,
                                  Vec3 cam, float outer, float scroll,
                                  float r, float g, float b,
                                  PoseStack.Pose mat, MultiBufferSource buffer) {
        Vec3[] basis = e.helixBasis();
        Vec3 right = basis[0], up = basis[1];
        float radius = v.meshRadius();
        float length = v.meshLength();

        VertexConsumer outerVc = buffer.getBuffer(KiRenderTypes.soft(KiRenderTypes.TRAIL_TEXTURE));
        VertexConsumer innerVc = v.hasTrailCore()
                ? buffer.getBuffer(KiRenderTypes.additive(KiRenderTypes.TRAIL_TEXTURE)) : null;
        float cr = r + (1f - r) * 0.55f, cg = g + (1f - g) * 0.55f, cb = b + (1f - b) * 0.55f;

        for (int s = 0; s < 2; s++) {
            double phase = Math.PI * s;
            List<Vec3> strand = new ArrayList<>(pts.size());
            double d = 0;
            strand.add(helixOffset(pts.get(0), right, up, radius,
                    KiMeshFactory.helixAngleFromTip(length, 0, phase)));
            for (int i = 1; i < pts.size(); i++) {
                d += pts.get(i - 1).distanceTo(pts.get(i));
                strand.add(helixOffset(pts.get(i), right, up, radius,
                        KiMeshFactory.helixAngleFromTip(length, d, phase)));
            }
            ribbon(outerVc, mat, strand, feet, cam, outer, scroll, r, g, b, v.trailAlpha());
            if (innerVc != null) {
                ribbon(innerVc, mat, strand, feet, cam, outer * v.trailInnerMul(), scroll,
                        cr, cg, cb, v.trailAlpha() * 0.70f);
            }
        }
    }

    private static Vec3 helixOffset(Vec3 center, Vec3 right, Vec3 up, float radius, double angle) {
        return center.add(right.scale(radius * Math.cos(angle)))
                .add(up.scale(radius * Math.sin(angle)));
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
