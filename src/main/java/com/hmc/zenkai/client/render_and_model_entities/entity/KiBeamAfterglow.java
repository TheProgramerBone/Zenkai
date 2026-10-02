package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DESVANECIDO DE SALIDA del haz anclado ({@link KiVfxProfile#column()}). Solo cliente.
 * <p>
 * Un proyectil de ki desaparece en el mismo tick en que impacta (lo borra el servidor), y con él
 * se iba de golpe un tubo de veinte o treinta bloques: el haz se "apagaba como una bombilla". En
 * dragonminez el haz es una entidad propia con {@code fadeTicks} al final de su vida; aquí la vida
 * la decide el impacto, así que en vez de tocar la entidad (código común con el servidor) se
 * guarda aparte la última forma dibujada de cada haz vivo y, cuando la entidad desaparece, se
 * sigue dibujando unos ticks con el alfa y el grosor cayendo — el mismo
 * {@link KiVfxProjectileRenderer#renderColumn}, por la misma cola diferida (así también entra en
 * el bloom y en el orden de atrás hacia delante).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiBeamAfterglow {
    private KiBeamAfterglow() {}

    private static final float FADE_TICKS = 8f;
    /** Un haz que deja de dibujarse (fuera de pantalla) sin haber desaparecido se olvida pasado
     *  este tiempo — si desapareciera después, no hay forma reciente que desvanecer. */
    private static final long STALE_TICKS = 40;

    private static final class Live {
        KiProjectileEntity entity;
        KiVfxProfile profile;
        List<Vec3> points; // camino raíz → cabeza, curvado si hubo HOMING
        float size;
        long lastSeen;
    }

    private record Ghost(KiVfxProfile profile, List<Vec3> points, float size, int rgb, int rgb2, long start) {}

    private static final Map<Integer, Live> LIVE = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();

    /** Lo llama el renderer del proyectil cada frame que dibuja un haz anclado. */
    static void track(KiProjectileEntity e, KiVfxProfile v, List<Vec3> points) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isFrozen() || mc.level == null) return;
        Live l = LIVE.computeIfAbsent(e.getId(), k -> new Live());
        l.entity = e;
        l.profile = v;
        l.points = points;
        l.size = (float) e.techniqueType().visualDiameter(e.size());
        l.lastSeen = mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<Live> it = LIVE.values().iterator();
        while (it.hasNext()) {
            Live l = it.next();
            if (l.entity.isRemoved()) {
                GHOSTS.add(new Ghost(l.profile, l.points, l.size, l.entity.rgb(), l.entity.rgb2(), now));
                it.remove();
            } else if (now - l.lastSeen > STALE_TICKS) {
                it.remove();
            }
        }
        GHOSTS.removeIf(g -> now - g.start() > FADE_TICKS + 1);
    }

    /** AFTER_ENTITIES: antes del stage en el que KiVfxBloomPipeline vacía la cola (AFTER_PARTICLES,
     *  o AFTER_LEVEL con shaderpack). La matriz encolada es solo la traslación relativa a cámara,
     *  igual que la de los renderers de entidad. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);

        for (Ghost gh : GHOSTS) {
            float f = 1f - (float) ((t - gh.start()) / FADE_TICKS);
            if (f <= 0f) continue;
            float fade = f * f;
            // Se adelgaza a la vez que se apaga: un haz que solo pierde alfa se lee como un tubo
            // de cristal que se vuelve transparente, no como energía que se disipa.
            float size = gh.size() * (0.55f + 0.45f * f);
            int rgb = gh.rgb();
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            float animT = (float) t;

            PoseStack pose = new PoseStack();
            Vec3 head = gh.points().get(gh.points().size() - 1);
            Vec3 rel = head.subtract(cam);
            pose.translate(rel.x, rel.y, rel.z);
            Vec3 mid = head.add(gh.points().get(0)).scale(0.5).subtract(cam);
            KiVfxFrameQueue.submit(pose, mid, (p, buf) -> {
                com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors.begin(gh.rgb2());
                KiVfxProjectileRenderer.renderColumn(gh.profile(), p, buf, gh.points(), 0f,
                        size, animT, r, g, b, fade, false, cam);
            });
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        LIVE.clear();
        GHOSTS.clear();
    }
}
