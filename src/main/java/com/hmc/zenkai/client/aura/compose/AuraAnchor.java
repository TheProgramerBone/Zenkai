package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * POSE REAL del cuerpo de un jugador en este frame, como seis CÁPSULAS (cabeza, torso, dos brazos,
 * dos piernas) en coordenadas de mundo. Es lo que permite un aura CEÑIDA al cuerpo (Ultra Instinct,
 * aura_6/aura_12), el contorno de energía y rayos que salen de los brazos (Beast, aura_9) SIN
 * deformar el PlayerModel — deformar sus cubos a esta escala los separa en triángulos sueltos
 * ("cubo explotado", confirmado en juego el 2026-09-29).
 * <p>
 * Captura: un {@link RenderLayer} que NO dibuja nada; solo aplica la transformación de cada
 * {@code ModelPart} ya posado (setupAnim + animaciones de PAL) a los extremos de su cápsula. Las
 * pieles raciales GeckoLib ocultan el modelo vanilla, pero sus partes se siguen posando: la
 * captura no depende de que sean visibles.
 * <p>
 * Sin captura en este frame (primera persona propia: el propio cuerpo no se renderiza; o fuera de
 * pantalla) se usa una pose de pie sintética orientada con el giro del cuerpo: el aura propia en
 * primera persona se sigue viendo alrededor, solo sin seguir los brazos.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraAnchor {

    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    public static final int SEGMENTS = 6;

    /** Extremos A/B y radio de cada cápsula en PÍXELES del espacio de su ModelPart (Y hacia abajo). */
    private static final float[][] LOCAL = {
            // ax, ay, az, bx, by, bz, radio
            {0f, -1.5f, 0f, 0f, -6.5f, 0f, 4.6f},   // cabeza: de la nuca a la coronilla
            {0f, 1.5f, 0f, 0f, 10.5f, 0f, 4.4f},    // torso
            {-1f, 0f, 0f, -1f, 9f, 0f, 2.4f},       // brazo derecho: hombro -> muñeca
            {1f, 0f, 0f, 1f, 9f, 0f, 2.4f},         // brazo izquierdo
            {0f, 1f, 0f, 0f, 11f, 0f, 2.5f},        // pierna derecha: cadera -> tobillo
            {0f, 1f, 0f, 0f, 11f, 0f, 2.5f}};       // pierna izquierda

    /** Pose sintética de pie (bloques sobre los pies; x = lateral hacia la derecha). */
    private static final float[][] STANDING = {
            {0f, 1.53f, 0f, 1.84f, 0.29f},
            {0f, 0.78f, 0f, 1.34f, 0.28f},
            {0.31f, 1.37f, 0.31f, 0.83f, 0.15f},
            {-0.31f, 1.37f, -0.31f, 0.83f, 0.15f},
            {0.12f, 0.68f, 0.12f, 0.06f, 0.16f},
            {-0.12f, 0.68f, -0.12f, 0.06f, 0.16f}};

    /** Cápsulas de un jugador. Coordenadas de MUNDO. */
    public static final class Pose {
        public final double[] ax = new double[SEGMENTS], ay = new double[SEGMENTS], az = new double[SEGMENTS];
        public final double[] bx = new double[SEGMENTS], by = new double[SEGMENTS], bz = new double[SEGMENTS];
        public final float[] r = new float[SEGMENTS];
        long frame = -1;
    }

    private static final Map<Integer, Pose> CAPTURED = new HashMap<>();
    private static long frame;
    /**
     * true solo mientras el MUNDO dibuja entidades (de AFTER_CUTOUT_BLOCKS a AFTER_ENTITIES). El
     * modelo del jugador también se dibuja fuera del mundo — retratos del HUD, pantallas con el
     * personaje — con un PoseStack en coordenadas de PANTALLA; una captura ahí colocaba las
     * cápsulas a kilómetros (visto en las capturas automáticas del 2026-09-30: el aura ceñida
     * aparecía como una sala gigante alrededor de la cámara).
     */
    private static boolean inWorldEntityPass;

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) inWorldEntityPass = true;
        else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) inWorldEntityPass = false;
    }

    private AuraAnchor() {}

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre e) { frame++; }

    /**
     * Rellena {@code out} con la pose del jugador en este frame: la capturada si existe, si no la
     * sintética de pie. @return true si es la pose real capturada.
     */
    public static boolean resolve(AbstractClientPlayer p, double x, double y, double z, float bodyYawDeg,
                                  Pose out) {
        Pose cap = CAPTURED.get(p.getId());
        if (cap != null && cap.frame == frame) {
            System.arraycopy(cap.ax, 0, out.ax, 0, SEGMENTS);
            System.arraycopy(cap.ay, 0, out.ay, 0, SEGMENTS);
            System.arraycopy(cap.az, 0, out.az, 0, SEGMENTS);
            System.arraycopy(cap.bx, 0, out.bx, 0, SEGMENTS);
            System.arraycopy(cap.by, 0, out.by, 0, SEGMENTS);
            System.arraycopy(cap.bz, 0, out.bz, 0, SEGMENTS);
            System.arraycopy(cap.r, 0, out.r, 0, SEGMENTS);
            return true;
        }
        AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
        float s = scaleAttr == null ? 1f : (float) scaleAttr.getValue();
        float yaw = bodyYawDeg * Mth.DEG_TO_RAD;
        // Derecha del cuerpo en mundo (con yaw 0 el jugador mira a +Z y su derecha es -X).
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        for (int i = 0; i < SEGMENTS; i++) {
            float[] st = STANDING[i];
            out.ax[i] = x + rx * st[0] * s;
            out.ay[i] = y + st[1] * s;
            out.az[i] = z + rz * st[0] * s;
            out.bx[i] = x + rx * st[2] * s;
            out.by[i] = y + st[3] * s;
            out.bz[i] = z + rz * st[2] * s;
            out.r[i] = st[4] * s;
        }
        return false;
    }

    /**
     * Pose de pie en coordenadas LOCALES (pies en el origen, sin giro), con el formato de segmentos
     * de {@code AuraBodyUniforms} (por segmento: A xyz, radio, B xyz, 0). Para la vista previa de
     * la GUI, donde no hay mundo ni cámara.
     */
    public static float[] standingLocal() {
        float[] out = new float[SEGMENTS * 8];
        for (int i = 0; i < SEGMENTS; i++) {
            float[] st = STANDING[i];
            int o = i * 8;
            out[o] = -st[0];
            out[o + 1] = st[1];
            out[o + 3] = st[4];
            out[o + 4] = -st[2];
            out[o + 5] = st[3];
        }
        return out;
    }

    /** La capa que captura. No dibuja. */
    private static final class CaptureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final Vector4f tmp = new Vector4f();

        CaptureLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light,
                           @NotNull AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!inWorldEntityPass) return;
            // Solo quien pueda tener aura: no cuesta nada para el resto.
            if (!com.hmc.zenkai.client.aura.AuraClientState.isAuraActive(player)
                    && !AuraComposer.hasRuntime(player.getId())) return;
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Pose out = CAPTURED.computeIfAbsent(player.getId(), k -> new Pose());
            capture(pose, m.head, HEAD, out, cam);
            capture(pose, m.body, BODY, out, cam);
            capture(pose, m.rightArm, RIGHT_ARM, out, cam);
            capture(pose, m.leftArm, LEFT_ARM, out, cam);
            capture(pose, m.rightLeg, RIGHT_LEG, out, cam);
            capture(pose, m.leftLeg, LEFT_LEG, out, cam);
            // Cordura: una pose a más de 8 bloques de los pies no es de este jugador en el mundo
            // (otra pasada de render con otro espacio). Se descarta y se usa la sintética.
            Vec3 at = player.getPosition(partialTick);
            boolean sane = true;
            for (int i = 0; i < SEGMENTS; i++) {
                double dx = out.ax[i] - at.x, dy = out.ay[i] - at.y, dz = out.az[i] - at.z;
                if (dx * dx + dy * dy + dz * dz > 64.0) sane = false;
            }
            out.frame = sane ? frame : -1;
        }

        private void capture(PoseStack pose, ModelPart part, int i, Pose out, Vec3 cam) {
            float[] l = LOCAL[i];
            pose.pushPose();
            part.translateAndRotate(pose);
            Matrix4f mat = pose.last().pose();
            // El PoseStack de entidades está en espacio RELATIVO A LA CÁMARA (sin su giro, que va
            // en la ModelView): sumando la cámara sale el mundo.
            mat.transform(tmp.set(l[0] / 16f, l[1] / 16f, l[2] / 16f, 1f));
            out.ax[i] = tmp.x + cam.x;
            out.ay[i] = tmp.y + cam.y;
            out.az[i] = tmp.z + cam.z;
            mat.transform(tmp.set(l[3] / 16f, l[4] / 16f, l[5] / 16f, 1f));
            out.bx[i] = tmp.x + cam.x;
            out.by[i] = tmp.y + cam.y;
            out.bz[i] = tmp.z + cam.z;
            // Escala real de la parte (escala de cuerpo, Oozaru...): largo de una columna.
            float sc = (float) Math.sqrt(mat.m00() * mat.m00() + mat.m01() * mat.m01() + mat.m02() * mat.m02());
            out.r[i] = l[6] / 16f * sc;
            pose.popPose();
        }
    }

    static void forget(int id) { CAPTURED.remove(id); }

    static void retain(java.util.Set<Integer> ids) { CAPTURED.keySet().retainAll(ids); }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new CaptureLayer(renderer));
        }
    }
}
