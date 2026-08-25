package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.AlignmentPalette;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Overlay del SENTIR EL KI: una llama de ki sobre cada entidad percibida, visible a través de
 * paredes. Percepción CUALITATIVA — aquí no se dibuja ni un número; la cifra exacta de PL es
 * lenguaje del scouter.
 *
 * Tres canales, y ni uno más:
 *  - POSICIÓN: dónde arde la llama.
 *  - COLOR:    fuerza relativa a la tuya (verde más débil / amarillo parejo / rojo más fuerte).
 *  - LLENADO:  vida restante. La llama se apaga de arriba abajo; lo perdido queda como brasa.
 *
 * La vida NO es una barra. Una barra es un instrumento de medida y este sentido no mide: ver a
 * alguien con la llama medio consumida dice "está malherido" sin decir 43/120.
 *
 * TAMAÑO FIJO EN PANTALLA: la escala se compensa con la distancia. Una llama que encoge con la
 * distancia se vuelve ilegible justo cuando más falta hace, que es cuando la cosa está lejos.
 *
 * OJO con el eje Y: tras el scale de nametag (-0.025 en Y) el +Y va HACIA ABAJO. Por eso el
 * quad va de -FLAME_H (punta) a 0 (base).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SenseKiOverlayRenderer {
    private SenseKiOverlayRenderer() {}

    private static final ResourceLocation FLAME_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/sense/ki_flame.png");

    /** Fotogramas de la tira vertical y velocidad del bucle. */
    private static final int FRAMES = 8;
    private static final float FRAME_FPS = 8f;

    // Unidades tras el scale de nametag (0.025): 40 ≈ 1 bloque.
    private static final float FLAME_W = 34f;
    private static final float FLAME_H = 44f;
    private static final float Y_OFFSET = 0.55f;

    /** Distancia a la que la llama tiene su tamaño nominal, y topes de compensación. */
    private static final float REF_DIST = 10f;
    private static final float SCALE_MIN = 0.55f;
    private static final float SCALE_MAX = 5.0f;

    /** Brasa: lo que ya no arde. Se ve, pero no compite con la parte viva. */
    private static final int C_EMBER = 0x55282828;

    private static final int FULL_BRIGHT = 0xF000F0; // LightTexture.FULL_BRIGHT

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (SenseKiClientState.mode() == SenseKiMode.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (SenseKiClientState.sensed().isEmpty()) return;

        int lvl = SkillEffects.senseLevel(mc.player);
        if (lvl <= 0) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + pt);

        long myPl = ownPowerLevel(mc);
        boolean precise = SkillEffects.sensePreciseHealth(mc.player);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // ⚠ VERIFICAR 1.21.1: textSeeThrough(tex) es el RenderType texturizado SIN test de
        // profundidad (es el que usan los nametags). Nos da "a través de paredes" y color por
        // vértice sin inventar un RenderType propio, que dejaría de dibujarse con Iris/Oculus.
        RenderType rt = RenderType.textSeeThrough(FLAME_TEX);
        VertexConsumer vc = buffers.getBuffer(rt);

        for (SenseKiDataPacket.Entry entry : SenseKiClientState.sensed().values()) {
            LivingEntity le = resolve(entry, mc);
            if (le == null) continue;
            // A partir de 2B, las entidades con silueta se saltan la llama aquí.

            pose.pushPose();
            float dist = billboard(pose, le, cam, camPos, pt);
            drawFlame(pose.last().pose(), vc, entry, myPl, precise, time, dist);
            pose.popPose();
        }
        buffers.endBatch(rt);
    }

    /** Entidad viva y visible detrás de una entrada, o null si no toca pintarla. */
    private static LivingEntity resolve(SenseKiDataPacket.Entry entry, Minecraft mc) {
        if (!SenseKiClientState.passesFilter(entry, mc)) return null;
        assert mc.level != null;
        Entity ent = mc.level.getEntity(entry.entityId());
        if (!(ent instanceof LivingEntity le) || !le.isAlive() || le == mc.player) return null;
        return le;
    }

    /**
     * Coloca el PoseStack sobre la cabeza, mirando a la cámara, con la escala compensada por
     * distancia para que la llama ocupe siempre lo mismo en pantalla.
     * @return distancia a la cámara, en bloques.
     */
    private static float billboard(PoseStack pose, LivingEntity le, Camera cam, Vec3 camPos, float pt) {
        Vec3 p = le.getPosition(pt).add(0, le.getBbHeight() + Y_OFFSET, 0);
        double dx = p.x - camPos.x, dy = p.y - camPos.y, dz = p.z - camPos.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        pose.translate(dx, dy, dz);
        pose.mulPose(cam.rotation());

        float k = Mth.clamp(dist / REF_DIST, SCALE_MIN, SCALE_MAX);
        pose.scale(-0.025f * k, -0.025f * k, 0.025f * k);
        return dist;
    }

    /**
     * La llama, en dos pasadas sobre el MISMO quad partido por la vida:
     *  - de la punta hasta el corte: brasa (lo perdido)
     *  - del corte hasta la base: color vivo (lo que queda)
     * Los dos tramos no se solapan, así que no hay z-fighting entre ellos.
     */
    private static void drawFlame(Matrix4f m, VertexConsumer vc, SenseKiDataPacket.Entry en,
                                  long myPl, boolean precise, float time, float dist) {
        float health = healthFraction(en, precise);
        int color = flameColor(en, myPl);

        // Latido: cuanto más fuerte es la entidad, más rápido arde. El PL que llega ya es el
        // APARENTE (suprimido incluido), así que alguien ocultando su ki late como un civil.
        double ratio = myPl > 0 ? en.powerLevel() / (double) myPl : 1.0;
        float hz = (float) Mth.clamp(0.6 + 0.55 * Math.log(1.0 + Math.max(0.0, ratio)) / Math.log(2), 0.6, 3.0);
        float pulse = 1f + 0.09f * Mth.sin(time / 20f * hz * Mth.TWO_PI);

        float w = FLAME_W * pulse * 0.5f;
        float h = FLAME_H * pulse;

        // Fotograma actual, desfasado por entidad para que no ardan en conjunto al unísono.
        int frame = (int) ((time / 20f * FRAME_FPS) + (en.entityId() * 3)) % FRAMES;
        if (frame < 0) frame += FRAMES;
        float v0 = frame / (float) FRAMES;
        float v1 = (frame + 1) / (float) FRAMES;

        // +Y hacia abajo: la punta está en -h y la base en 0.
        float yTip = -h, yBase = 0f;
        // health=1 -> el corte está en la PUNTA (arde entera); health=0 -> en la base (apagada).
        float yCut = Mth.lerp(health, yBase, yTip);
        float vCut = Mth.lerp(health, v1, v0);

        if (health < 1f) {
            quad(vc, m, -w, yTip, w, yCut, v0, vCut, C_EMBER);
        }
        if (health > 0f) {
            quad(vc, m, -w, yCut, w, yBase, vCut, v1, color);
        }
    }

    /**
     * Vida restante, 0..1. Al nivel 1 se redondea a tercios: el sentido en bruto distingue
     * "entero / tocado / agonizando" y poco más. Del 2 en adelante es continua.
     */
    private static float healthFraction(SenseKiDataPacket.Entry en, boolean precise) {
        if (en.bodyMax() <= 0) return 1f;
        float f = Mth.clamp(en.body() / (float) en.bodyMax(), 0f, 1f);
        if (precise) return f;
        if (f > 0.66f) return 1f;
        if (f > 0.33f) return 0.66f;
        return 0.33f;
    }

    /** Ancho de la rampa de fuerza, en octavas: ×4 tu PL satura, ÷4 lo apaga. */
    private static final float STRENGTH_SPAN = 2.0f;

    /**
     * Color de la llama: TONO por alineamiento (paleta viva) e INTENSIDAD por fuerza relativa.
     * El tono NO se lava nunca. Antes se desaturaba hacia gris con las entidades débiles, y
     * como generalmente está por debajo del jugador, el resultado era un pegote gris para el
     * bestiario — el dato principal desaparecía justo en el caso común. La fuerza se lee en el
     * latido y en el brillo, que basta.
     */
    public static int flameColor(SenseKiDataPacket.Entry en, long myPl) {
        int rgb = AlignmentPalette.vividForAlignment(en.alignment()) & 0xFFFFFF;
        float t = strengthT(en, myPl);

        // Solo hacia ARRIBA: por encima de ti se sobreexpone hacia blanco (ki que ciega).
        // Por debajo, el color se mantiene entero y solo baja algo de brillo.
        int col = (t > 0.5f)
                ? AlignmentPalette.lerpRgb(rgb, 0xFFFFFF, (t - 0.5f) * 0.55f)
                : rgb;

        float k = 0.82f + 0.18f * t;   // suelo alto a propósito: nunca un pegote oscuro
        int r = Mth.clamp((int) (((col >> 16) & 0xFF) * k), 0, 255);
        int g = Mth.clamp((int) (((col >> 8) & 0xFF) * k), 0, 255);
        int b = Mth.clamp((int) ((col & 0xFF) * k), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 0 = muy por debajo de ti, 0.5 = parejo, 1 = muy por encima. Escala logarítmica porque
     *  el PL crece por órdenes de magnitud: en lineal, lo tuyo sería 0 o 1. */
    public static float strengthT(SenseKiDataPacket.Entry en, long myPl) {
        if (myPl <= 0 || en.powerLevel() <= 0) return 0.5f;
        double octaves = Math.log((double) en.powerLevel() / myPl) / Math.log(2);
        return 0.5f + 0.5f * Mth.clamp((float) (octaves / STRENGTH_SPAN), -1f, 1f);
    }

    private static long ownPowerLevel(Minecraft mc) {
        assert mc.player != null;
        var att = PlayerStatsAttachment.get(mc.player);
        return att.isRaceChosen() ? att.getPowerLevel() : Math.round(mc.player.getMaxHealth());
    }

    /** Quad texturizado de una cara, con el winding que este RenderType considera frontal. */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float x1, float y1,
                             float v0, float v1, int argb) {
        vc.addVertex(m, x1, y0, 0).setColor(argb).setUv(1f, v0).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setUv(1f, v1).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setUv(0f, v1).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y0, 0).setColor(argb).setUv(0f, v0).setLight(FULL_BRIGHT);
    }
}