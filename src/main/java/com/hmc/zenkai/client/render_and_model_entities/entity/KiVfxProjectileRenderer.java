package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiAxis;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiRibbon;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxDebugMode;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxMesh;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxShape;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxTuning;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Render de proyectil de ki — reconstrucción completa del pipeline de VFX (2026-09-23, ver
 * .claude/pendiente si queda algún resumen de sesión; el diseño en sí vive aquí y en el resto del
 * paquete {@code kivfx}).
 *
 * EL CUERPO (malla + envolvente + núcleo, shader o respaldo) VIVE EN {@link KiVfxCompositeRenderer},
 * compartido con la bola que se carga en la mano ({@link com.hmc.zenkai.client.KiVfxChargeRenderer})
 * — cargar y disparar son la MISMA energía, nunca cambian de aspecto en el instante del disparo.
 * Lo exclusivo de aquí, porque solo tiene sentido con un proyectil que VUELA, es la estela, los
 * rayos radiales, las chispas y la orientación por velocidad.
 *
 * LA ESCALA ES UNIFORME. La longitud de los haces va HORNEADA en la malla (ver KiVfxGeometry):
 * escalar Z aparte estiraría también el tubo de las hélices.
 *
 * Capas, de fuera hacia dentro: estela → halo → envolvente → cáscara → núcleo. Cada capa consulta
 * {@link KiVfxDebugMode#current()} para aislarse (ver STEP 11 de la auditoría).
 */
public class KiVfxProjectileRenderer extends EntityRenderer<KiProjectileEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Techo global de chispas por tick entre todos los proyectiles — una ráfaga de veinte bolas
     *  grandes pedía cientos de partículas por tick y hundía los frames sin diferencia visible. */
    private static final int SPARK_BUDGET_PER_TICK = 24;
    private static int sparkBudget = SPARK_BUDGET_PER_TICK;
    private static long sparkBudgetTick = Long.MIN_VALUE;

    /** Ticks tras el disparo en los que el cuerpo crece de 0 a tamaño completo. Solo afecta al
     *  CUERPO — el halo mantiene su alfa/tamaño normal desde el primer frame, a propósito, para
     *  no interferir con su propio pulso. */
    private static final float POP_TICKS = 5f;

    public KiVfxProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(KiProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        KiVfxProfile v = KiVfxProfile.of(entity.techniqueType());
        int rgb = entity.rgb();
        KiVfxDebugMode dbg = KiVfxDebugMode.current();

        // Lo que no viaja (BARRIER, la mecha de EXPLOSION) va pegado al dueño, y en primera
        // persona su cáscara puede llegar a envolver la cámara: se OCULTA TOTALMENTE ahí, no solo
        // atenuada.
        boolean hiddenFirstPerson = false;
        if (!entity.techniqueType().travels()) {
            Minecraft mc = Minecraft.getInstance();
            hiddenFirstPerson = entity.getOwner() == mc.player && mc.options.getCameraType().isFirstPerson();
        }

        // Congelada por "/zenkai debug kivfx": la posición nunca cambia, así que tick() nunca
        // construiría un historial de estela real.
        if (entity.isFrozen() && v.hasTrail() && entity.trailHistory().isEmpty()) {
            synthesizeFrozenTrail(entity, v);
        }

        // DIBUJADO DIFERIDO (ver KiVfxFrameQueue): aquí solo se encola; KiVfxBloomPipeline lo
        // reproduce en un stage fijo, una vez para el mundo y otra para el bloom. Lo que debe
        // pasar UNA sola vez por frame (partículas, consumeFxTick) se queda fuera de la tarea.
        if (!hiddenFirstPerson) {
            Vec3 fromCamera = visualFeet(entity, partialTick).add(0, entity.getBbHeight() * 0.5, 0)
                    .subtract(this.entityRenderDispatcher.camera.getPosition());
            KiVfxFrameQueue.submit(pose, fromCamera, (p, buf) -> drawTechnique(entity, v, partialTick, p, buf));
        }

        if (dbg.showsParticles()) {
            boolean fxTick = entity.consumeFxTick();
            spawnSparks(entity, v, rgb, fxTick);
            spawnArcs(entity, v, rgb, fxTick);
        } else {
            entity.consumeFxTick(); // sigue avanzando el flag aunque no se gaste, ver su javadoc
        }

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /** Cuerpo completo de la técnica: estela → halo → envolvente/cáscara/núcleo → rayos. Se
     *  ejecuta DOS veces por frame (mundo y bloom), así que no puede tener efectos laterales. */
    private void drawTechnique(KiProjectileEntity entity, KiVfxProfile v, float partialTick,
                               PoseStack pose, MultiBufferSource.BufferSource buffer) {
        int rgb = entity.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        KiVfxDebugMode dbg = KiVfxDebugMode.current();

        if (!entity.techniqueType().travels()) {
            alignToOwner(entity, partialTick, pose);
        }

        if (dbg.showsRibbons() && v.hasTrail() && entity.trailHistory().size() >= 2) {
            renderTrail(entity, v, partialTick, pose, buffer, r, g, b);
        }

        if (dbg.showsHalo()) {
            renderHalo(entity, v, partialTick, pose, buffer, r, g, b);
        }

        float animT = animTime(entity, partialTick);

        // Respiración: una energía contenida no está nunca perfectamente quieta.
        float breathe = 1f + 0.035f * Mth.sin(animT * 0.31f);
        float popT = Mth.clamp((entity.tickCount + partialTick) / POP_TICKS, 0f, 1f);
        float popScale = popT * popT * (3f - 2f * popT);
        float size = (float) entity.techniqueType().visualDiameter(entity.size()) * breathe * popScale;

        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);

        // Las formas alargadas se orientan con la VELOCIDAD, no con el yaw.
        if (v.shape() != KiVfxShape.SPHERE) {
            Vec3 vel = entity.getDeltaMovement();
            if (vel.lengthSqr() > 1.0e-6) {
                vel = vel.normalize();
                pose.mulPose(Axis.YP.rotationDegrees(
                        (float) (Math.atan2(vel.x, vel.z) * 180.0 / Math.PI)));
                pose.mulPose(Axis.XP.rotationDegrees(
                        (float) (-Math.asin(vel.y) * 180.0 / Math.PI)));
            }
        }

        if (v.shape() == KiVfxShape.DISK) {
            pose.mulPose(Axis.XP.rotationDegrees(DISK_CANT_DEGREES));
            pose.mulPose(Axis.ZP.rotationDegrees(animT * 22f));
        }

        if (dbg.showsShell() || dbg.showsCore()) {
            KiVfxMesh mesh = KiVfxGeometry.shell(v);
            // proximity: ver "BUG DE CÁMARA" en ki_energy.fsh. El radio ya escalado decide qué
            // cuenta como "cerca" para ESTA técnica, no una distancia fija en bloques.
            float proximity = KiVfxRenderTypes.proximity(
                    this.entityRenderDispatcher.camera.getPosition().distanceTo(
                            visualFeet(entity, partialTick).add(0, entity.getBbHeight() * 0.5, 0)),
                    v.worldRadius(size));
            KiVfxMesh coreMesh = KiVfxGeometry.core(v, true);
            KiVfxCompositeRenderer.render(buffer, v, mesh, coreMesh, pose, size, r, g, b,
                    1f, entity.isFrozen(), proximity);
        }

        pose.popPose();

        if (dbg.showsRibbons()) {
            renderRays(entity, v, partialTick, pose, buffer, r, g, b);
        }
    }

    /** Mismo ladeo que la bola de carga (ver KiVfxChargeRenderer.DISK_CANT_DEGREES): un disco
     *  visto exactamente de frente se lee como una lámina de canto, no como un plato. */
    public static final float DISK_CANT_DEGREES = 60f;

    private static float animTime(KiProjectileEntity e, float partialTick) {
        return e.isFrozen() ? 0f : (e.tickCount + partialTick);
    }

    private static void alignToOwner(KiProjectileEntity entity, float partialTick, PoseStack pose) {
        Vec3 wantFeet = visualFeet(entity, partialTick);
        Vec3 haveFeet = entity.getPosition(partialTick);
        pose.translate(wantFeet.x - haveFeet.x, wantFeet.y - haveFeet.y, wantFeet.z - haveFeet.z);
    }

    /**
     * Dónde se DIBUJA de verdad la técnica este frame, en mundo: la posición interpolada y, para
     * lo que no viaja, re-anclada al dueño igual que {@link #alignToOwner}. Cualquier cálculo en
     * coordenadas de MUNDO (distancia a cámara del halo, proximity) tiene que salir de aquí y no
     * de {@code getX()/getY()/getZ()}, que es la posición del TICK sin interpolar ni re-anclar:
     * con ella las fuentes de bloom antiguas iban un tick por delante de un haz rápido o por
     * detrás del dueño en vuelo.
     */
    private static Vec3 visualFeet(KiProjectileEntity entity, float partialTick) {
        Entity owner = entity.getOwner();
        if (entity.techniqueType().travels() || owner == null) return entity.getPosition(partialTick);
        return owner.getPosition(partialTick)
                .add(0, owner.getBbHeight() * 0.5 - entity.getBbHeight() * 0.5, 0);
    }

    // ── Halo ────────────────────────────────────────────────────────────────

    private void renderHalo(KiProjectileEntity e, KiVfxProfile v, float partialTick, PoseStack pose,
                            MultiBufferSource buffer, float r, float g, float b) {
        if (!v.halo().enabled()) return;

        float pulse = 1f + 0.07f * Mth.sin(animTime(e, partialTick) * 0.22f);
        float half = e.getBbWidth() * v.halo().scale() * 0.5f * pulse;
        Vec3 center = visualFeet(e, partialTick).add(0, e.getBbHeight() * 0.5, 0);
        float camFade = KiVfxRenderTypes.billboardCameraFade(
                this.entityRenderDispatcher.camera.getPosition().distanceTo(center), half);
        if (camFade <= 0f) return;

        VertexConsumer vc = buffer.getBuffer(KiVfxRenderTypes.glow(KiVfxRenderTypes.HALO_TEXTURE));
        float a = v.halo().alpha() * camFade * KiVfxTuning.get(KiVfxTuning.Param.HALO_ALPHA_MUL, v);
        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose mat = pose.last();
        vert(vc, mat, -half, -half, 0, r, g, b, a, 0f, 1f);
        vert(vc, mat,  half, -half, 0, r, g, b, a, 1f, 1f);
        vert(vc, mat,  half,  half, 0, r, g, b, a, 1f, 0f);
        vert(vc, mat, -half,  half, 0, r, g, b, a, 0f, 0f);
        pose.popPose();
    }

    // ── Estela ──────────────────────────────────────────────────────────────

    private void renderTrail(KiProjectileEntity e, KiVfxProfile v, float partialTick, PoseStack pose,
                             MultiBufferSource buffer, float r, float g, float b) {
        List<Vec3> all = new ArrayList<>(e.trailHistory());
        int n = Math.min(all.size(), v.trail().points());
        if (n < 2) return;

        List<Vec3> pts = new ArrayList<>(n + 1);
        Vec3 feet = e.getPosition(partialTick);
        pts.add(feet.add(0, e.getBbHeight() * 0.5, 0));
        for (int i = 0; i < n; i++) pts.add(all.get(i));

        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        float scroll = -animTime(e, partialTick) * v.trail().scroll() * 0.05f;
        float outer = e.getBbWidth() * v.trail().width();

        if (v.helixTrail()) {
            renderHelixTrail(e, v, pts, feet, cam, outer, scroll, r, g, b, pose.last(), buffer);
            return;
        }

        Vec3[] basis = e.flightBasis();
        VertexConsumer outerVc = buffer.getBuffer(KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE));
        float headClear = headClear(e);
        float trailAlpha = v.trail().alpha() * KiVfxTuning.get(KiVfxTuning.Param.TRAIL_ALPHA_MUL, v);
        KiRibbon.draw(outerVc, pose.last(), pts, feet, basis[0], basis[1], cam, outer, scroll,
                r, g, b, trailAlpha, headClear);

        // Núcleo: el tinte empujado hacia blanco, no blanco puro, y con menos alfa que la capa
        // teñida. Opcional: en cuerpos grandes y lentos sobra
        // (trailInnerMul 0).
        if (v.trail().hasCore()) {
            float cr = r + (1f - r) * 0.55f, cg = g + (1f - g) * 0.55f, cb = b + (1f - b) * 0.55f;
            VertexConsumer innerVc = buffer.getBuffer(KiVfxRenderTypes.glow(KiVfxRenderTypes.TRAIL_TEXTURE));
            KiRibbon.draw(innerVc, pose.last(), pts, feet, basis[0], basis[1], cam,
                    outer * v.trail().innerMul(), scroll, cr, cg, cb, trailAlpha * 0.70f, headClear);
        }
    }

    /** Rampa de entrada de la estela (ver KiRibbon.strand, headClear): algo más que el radio de
     *  la cabeza, para que el arranque de la cinta quede escondido dentro de ella. */
    private static float headClear(KiProjectileEntity e) {
        return e.getBbWidth() * 0.75f;
    }

    private static final float FROZEN_TRAIL_STEP_MIN = 0.15f;

    private static void synthesizeFrozenTrail(KiProjectileEntity e, KiVfxProfile v) {
        Vec3 dir = e.getDeltaMovement();
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();

        Vec3 head = e.position().add(0, e.getBbHeight() * 0.5, 0);
        float step = Math.max(FROZEN_TRAIL_STEP_MIN, e.techniqueType().speed());
        int n = Math.min(v.trail().points(), KiProjectileEntity.TRAIL_MAX);
        for (int i = 0; i < n; i++) {
            e.trailHistory().addLast(head.add(dir.scale(-step * (i + 1))));
        }
    }

    /**
     * Estela en doble hélice CONTINUA (hoy solo SPIRAL): cada una de las dos hebras desplaza los
     * puntos históricos con la MISMA fórmula de torsión que hornea la malla
     * ({@link KiVfxGeometry#helixAngleFromTip}), evaluada más allá de {@code meshLength} — la
     * estela sigue girando en el mismo sentido y ritmo en vez de cortar a una cinta recta donde
     * termina la malla horneada. La base perpendicular sale de {@code flightBasis()}, fijada una
     * sola vez al disparar.
     */
    private void renderHelixTrail(KiProjectileEntity e, KiVfxProfile v, List<Vec3> pts, Vec3 feet,
                                  Vec3 cam, float outer, float scroll,
                                  float r, float g, float b,
                                  PoseStack.Pose mat, MultiBufferSource buffer) {
        Vec3[] basis = e.flightBasis();
        Vec3 right = basis[0], up = basis[1];
        float headClear = headClear(e);
        float trailAlpha = v.trail().alpha() * KiVfxTuning.get(KiVfxTuning.Param.TRAIL_ALPHA_MUL, v);
        float radius = v.shell().meshRadius();
        float length = v.shell().meshLength();
        float cr = r + (1f - r) * 0.55f, cg = g + (1f - g) * 0.55f, cb = b + (1f - b) * 0.55f;

        List<List<Vec3>> strands = new ArrayList<>(2);
        strands.add(KiRibbon.helixStrand(pts, right, up, radius, length, 0));
        strands.add(KiRibbon.helixStrand(pts, right, up, radius, length, Math.PI));

        // Orden por facing de las dos hebras contra sus propios laterales instantáneos (right/up,
        // ya fijos): calculado una vez, reutilizado en las dos pasadas.
        Vec3[] order = KiAxis.orderByFacing(pts.get(0), cam, right, up);

        // DOS PASADAS, no un solo draw con outerVc/innerVc simultáneos: un
        // MultiBufferSource.BufferSource solo tiene un sharedBuffer para un RenderType custom
        // no-fijo, así que hay que cerrar (endBatch) el anterior antes de pedir otro.
        VertexConsumer outerVc = buffer.getBuffer(KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE));
        for (List<Vec3> strand : strands) {
            KiRibbon.strand(outerVc, mat, strand, feet, order[0], outer, scroll, r, g, b, trailAlpha, headClear);
            KiRibbon.strand(outerVc, mat, strand, feet, order[1], outer, scroll, r, g, b, trailAlpha, headClear);
        }

        if (v.trail().hasCore()) {
            VertexConsumer innerVc = buffer.getBuffer(KiVfxRenderTypes.glow(KiVfxRenderTypes.TRAIL_TEXTURE));
            float innerW = outer * v.trail().innerMul();
            for (List<Vec3> strand : strands) {
                KiRibbon.strand(innerVc, mat, strand, feet, order[0], innerW, scroll, cr, cg, cb, trailAlpha * 0.70f, headClear);
                KiRibbon.strand(innerVc, mat, strand, feet, order[1], innerW, scroll, cr, cg, cb, trailAlpha * 0.70f, headClear);
            }
        }
    }

    // ── Rayos radiales ──────────────────────────────────────────────────────

    private static final float RAY_WIDTH_FRAC = 0.35f;
    private static final float RAY_WIDTH_MAX = 2.5f;
    private static final float RAY_LENGTH_MAX = 20f;

    /** Haces de luz rectos desde el centro hacia fuera — Death Ball, Supernova, Spirit Bomb. Usa
     *  {@link KiRibbon} con un camino de 2 puntos (centro, punta) en vez del historial de
     *  posiciones de la estela. */
    private void renderRays(KiProjectileEntity e, KiVfxProfile v, float partialTick, PoseStack pose,
                            MultiBufferSource buffer, float r, float g, float b) {
        if (!v.hasRays()) return;

        Vec3 feet = e.getPosition(partialTick);
        Vec3 center = feet.add(0, e.getBbHeight() * 0.5, 0);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        float radius = e.getBbWidth() * 0.75f;
        float length = Math.min(RAY_LENGTH_MAX, radius * v.rays().length());
        float width = Math.min(RAY_WIDTH_MAX, radius * RAY_WIDTH_FRAC);

        VertexConsumer vc = buffer.getBuffer(KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE));
        for (int i = 0; i < v.rays().count(); i++) {
            Vec3 dir = rayDirection(e.getId(), i);
            Vec3[] basis = KiAxis.basis(dir);
            List<Vec3> pts = List.of(center, center.add(dir.scale(length)));
            KiRibbon.draw(vc, pose.last(), pts, center, basis[1], basis[2], cam, width, 0f,
                    r, g, b, v.rays().alpha());
        }
    }

    private static Vec3 rayDirection(int entityId, int index) {
        Random rnd = new Random(entityId * 97L + index * 131L);
        double theta = rnd.nextDouble() * Math.PI * 2.0;
        double z = rnd.nextDouble() * 2.0 - 1.0;
        double planar = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(planar * Math.cos(theta), z, planar * Math.sin(theta));
    }

    // ── Chispas ─────────────────────────────────────────────────────────────

    private static float particleScale(float base, float perBlockWidth, float bbWidth, float max) {
        return Math.min(max, base + bbWidth * perBlockWidth);
    }

    private static void spawnSparks(KiProjectileEntity e, KiVfxProfile v, int rgb, boolean fxTick) {
        if (v.particles().sparkRate() <= 0f || !fxTick) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        if (now != sparkBudgetTick) {
            sparkBudgetTick = now;
            sparkBudget = SPARK_BUDGET_PER_TICK;
        }
        if (sparkBudget <= 0) return;

        float rate = v.particles().sparkRate() * (0.6f + 0.4f * e.size());
        int count = (int) rate;
        if (mc.level.random.nextFloat() < rate - count) count++;
        count = Math.min(count, sparkBudget);
        if (count <= 0) return;
        sparkBudget -= count;

        double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.5, cz = e.getZ();
        double spread = e.getBbWidth() * 0.55;
        Vec3 back = e.getDeltaMovement().scale(-0.25);
        float scale = particleScale(0.6f, 0.15f, e.getBbWidth(), 3.0f);
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(ModParticles.spark(rgb, scale),
                    cx + (mc.level.random.nextDouble() - 0.5) * spread,
                    cy + (mc.level.random.nextDouble() - 0.5) * spread,
                    cz + (mc.level.random.nextDouble() - 0.5) * spread,
                    back.x + (mc.level.random.nextDouble() - 0.5) * 0.06,
                    back.y + (mc.level.random.nextDouble() - 0.5) * 0.06,
                    back.z + (mc.level.random.nextDouble() - 0.5) * 0.06);
        }
    }

    private static final int ARC_BUDGET_PER_TICK = 12;
    private static int arcBudget = ARC_BUDGET_PER_TICK;
    private static long arcBudgetTick = Long.MIN_VALUE;

    private static void spawnArcs(KiProjectileEntity e, KiVfxProfile v, int rgb, boolean fxTick) {
        if (v.particles().chargeSparkRate() <= 0f || !fxTick) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        if (now != arcBudgetTick) {
            arcBudgetTick = now;
            arcBudget = ARC_BUDGET_PER_TICK;
        }
        if (arcBudget <= 0) return;

        float rate = v.particles().chargeSparkRate() * (0.6f + 0.4f * e.size());
        int count = (int) rate;
        if (mc.level.random.nextFloat() < rate - count) count++;
        count = Math.min(count, arcBudget);
        if (count <= 0) return;
        arcBudget -= count;

        double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.5, cz = e.getZ();
        double surfaceRadius = e.getBbWidth() * 0.75;
        float scale = particleScale(0.20f, 0.05f, e.getBbWidth(), 0.65f);
        for (int i = 0; i < count; i++) {
            Vec3 dir = rayDirection(e.getId(), mc.level.random.nextInt(1_000_000));
            mc.level.addParticle(ModParticles.arc(rgb, scale),
                    cx + dir.x * surfaceRadius, cy + dir.y * surfaceRadius, cz + dir.z * surfaceRadius,
                    0, 0, 0);
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private static void vert(VertexConsumer vc, PoseStack.Pose mat, float x, float y, float z,
                             float r, float g, float b, float a, float u, float v) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(mat, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(KiProjectileEntity entity) {
        return KiVfxRenderTypes.BALL_TEXTURE;
    }
}
