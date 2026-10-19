package com.hmc.zenkai.client.fly;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Líneas de velocidad: trazos finos y cortos que pasan a tu lado mientras vuelas rápido. Sustituyen
 * a las imágenes residuales, que no gustaron (siluetas grises gigantes tapando la cámara).
 *
 * SOLO cliente y solo para el jugador local: es una sensación de piloto, no algo que deba ver un
 * tercero. Cada trazo nace en una corona alrededor del jugador, ALINEADO con el rumbo, y se queda
 * FIJO en el mundo mientras se desvanece: así, al avanzar, pasa junto a ti hacia atrás, que es lo que
 * da la sensación de velocidad (si siguiera pegado al jugador no se movería respecto a la cámara).
 * Se dibuja como un triángulo muy estrecho que mira a la cámara, afilado hacia la cola y
 * desvaneciéndose, con el RenderType sin textura de los rayos de vanilla.
 *
 * Nace fuera de un radio mínimo del eje de vuelo para no tapar el centro de la pantalla.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SpeedLines {
    private SpeedLines() {}

    /** Velocidad mínima (bloques/tick) para que haya líneas; nivel medio ≈ 2.0, turbo ≈ 2.7. */
    private static final double MIN_SPEED = 1.5;
    /** Tope de trazos vivos, por si se acumulan (cámara lenta, tick largo). */
    private static final int CAP = 140;
    /** Grosor de la cabeza del trazo, en bloques. Fino a propósito. */
    private static final float WIDTH = 0.02f;
    /** Corona de nacimiento alrededor del ojo: radio mínimo y máximo, en bloques. */
    private static final double MIN_RADIUS = 1.6;
    private static final double MAX_RADIUS = 5.0;
    /** Rango de aparición a lo largo del rumbo respecto al ojo (negativo = detrás). */
    private static final double AHEAD_MIN = -1.0;
    private static final double AHEAD_MAX = 6.0;
    private static final float MAX_ALPHA = 0.55f;

    private static final class Streak {
        final Vec3 head;
        final Vec3 dir;
        final float length;
        final int rgb;
        final long start;
        final int life;

        Streak(Vec3 head, Vec3 dir, float length, int rgb, long start, int life) {
            this.head = head;
            this.dir = dir;
            this.length = length;
            this.rgb = rgb;
            this.start = start;
            this.life = life;
        }
    }

    private static final List<Streak> STREAKS = new ArrayList<>();
    private static Vec3 lastPos;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STREAKS.clear();
            lastPos = null;
            return;
        }
        if (mc.isPaused()) return;

        var p = mc.player;
        Vec3 now = p.position();
        Vec3 delta = lastPos == null ? Vec3.ZERO : now.subtract(lastPos);
        lastPos = now;

        long tick = mc.level.getGameTime();
        STREAKS.removeIf(s -> tick - s.start >= s.life);
        if (!ClientConfig.flightFxSpeedLines()) {
            STREAKS.clear();
            return;
        }

        PlayerStatsAttachment att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        double speed = delta.length();
        if (!att.isFlyEnabled() || p.onGround() || speed < MIN_SPEED) return;

        Vec3 dir = delta.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        Vec3 eye = p.getEyePosition();
        // Casi blanco con un toque del aura: se lee como viento, no como otra silueta de color.
        int rgb = lighten(AuraColors.resolve(p), 0.75f);

        RandomSource r = mc.level.random;
        int n = Mth.clamp(1 + (int) Math.round((speed - MIN_SPEED) * 2.0), 1, 5);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double radius = MIN_RADIUS + r.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            double ahead = AHEAD_MIN + r.nextDouble() * (AHEAD_MAX - AHEAD_MIN);
            Vec3 head = eye.add(dir.scale(ahead))
                    .add(a.scale(Math.cos(ang) * radius)).add(b.scale(Math.sin(ang) * radius));
            float length = (float) (1.2 + r.nextDouble() * 1.8 + speed * 0.7);
            STREAKS.add(new Streak(head, dir, length, rgb, tick, 6 + r.nextInt(4)));
        }
        while (STREAKS.size() > CAP) STREAKS.remove(0);
    }

    private static int lighten(int rgb, float f) {
        int r = (int) Mth.lerp(f, (rgb >> 16) & 0xFF, 255);
        int g = (int) Mth.lerp(f, (rgb >> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(f, rgb & 0xFF, 255);
        return (r << 16) | (g << 8) | b;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STREAKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        Matrix4f m = pose.last().pose();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.lightning();
        VertexConsumer vc = buffers.getBuffer(rt);

        for (Streak s : STREAKS) {
            float age = (now - s.start) + pt;
            float fade = 1f - Math.min(1f, age / s.life);
            int alpha = (int) (MAX_ALPHA * fade * 255f);
            if (alpha <= 2) continue;

            Vec3 h = s.head.subtract(cam);
            Vec3 t = h.subtract(s.dir.scale(s.length));
            // Ancho perpendicular al rumbo y a la visual: el trazo siempre se ve de frente.
            Vec3 side = h.subtract(t).cross(h);
            double len = side.length();
            if (len < 1.0e-6) continue;
            side = side.scale(WIDTH * 0.5 / len);

            int r = (s.rgb >> 16) & 0xFF, g = (s.rgb >> 8) & 0xFF, b = s.rgb & 0xFF;
            // Cabeza con alfa y cola a 0: un trazo afilado que se desvanece hacia atrás.
            vc.addVertex(m, (float) (h.x + side.x), (float) (h.y + side.y), (float) (h.z + side.z)).setColor(r, g, b, alpha);
            vc.addVertex(m, (float) (h.x - side.x), (float) (h.y - side.y), (float) (h.z - side.z)).setColor(r, g, b, alpha);
            vc.addVertex(m, (float) t.x, (float) t.y, (float) t.z).setColor(r, g, b, 0);
            vc.addVertex(m, (float) t.x, (float) t.y, (float) t.z).setColor(r, g, b, 0);
        }
        buffers.endBatch(rt);
    }
}
