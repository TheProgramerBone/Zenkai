package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.AlignmentPalette;
import com.hmc.zenkai.core.combat.SenseKiMode;
import com.hmc.zenkai.core.network.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.core.skills.SkillEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Overlay flotante del SENTIR EL KI. Billboards sobre la cabeza, visibles A TRAVÉS de paredes.
 *
 * Lo que se ve depende del nivel de la habilidad Ki Sense (percepción CUALITATIVA; la cifra
 * exacta de PL es cosa del scouter):
 *   nv1  marcador de fuerza relativa (rojo más fuerte / amarillo similar / verde más débil)
 *   nv2  + barra de vida
 *   nv3  + marcador de alineamiento (gradiente de AlignmentPalette, el mismo de StatsScreen)
 *   nv4  + barra de ki
 *   nv5  + barra de estamina
 *
 * Render: RenderType.textBackgroundSeeThrough() + bufferSource del juego (el camino de los
 * fondos de nametag). Reglas aprendidas EN JUEGO de este RenderType:
 *  - Cullea: el winding de quad(...) es el frontal correcto para nuestra transformación.
 *  - Las capas NO deben superponerse: quads coplanares en el mismo plano hacían z-fighting
 *    (titileo). Por eso el layout es SIN solapamiento: relleno hasta pct, fondo desde pct,
 *    y el borde en 4 tiras alrededor.
 *
 * OJO con el eje Y: tras el scale de nametag (-0.025 en Y) el +Y va HACIA ABAJO. Por eso los
 * marcadores viven en Y negativa y las barras van sumando hacia abajo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SenseKiOverlayRenderer {
    private SenseKiOverlayRenderer() {}

    // Diales (unidades tras el scale de nametag 0.025: 36 px ≈ 0.9 bloques de ancho).
    private static final float BAR_W    = 36f;
    private static final float BAR_H    = 4f;
    private static final float BAR_GAP  = 2f;   // separación vertical entre barras
    private static final float MARK     = 6f;   // lado del marcador cuadrado
    private static final float Y_OFFSET = 0.75f;

    private static final int C_BG   = 0xB0101010;
    private static final int C_EDGE = 0xFF303030;
    private static final int FULL_BRIGHT = 0xF000F0; // LightTexture.FULL_BRIGHT

    // Fuerza relativa (nv1).
    private static final int C_STRONGER = 0xFFFF4040;
    private static final int C_SIMILAR  = 0xFFFFD040;
    private static final int C_WEAKER   = 0xFF50E050;

    // El alineamiento usa AlignmentPalette (la misma que la barra de StatsScreen).

    // Pools (nv4, nv5).
    private static final int C_KI      = 0xFF40A0FF;
    private static final int C_STAMINA = 0xFFB0F040;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (SenseKiClientState.mode() == SenseKiMode.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (SenseKiClientState.sensed().isEmpty()) return;

        int lvl = SkillEffects.senseLevel(mc.player);
        if (lvl <= 0) return; // sin la habilidad no se percibe nada

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());

        for (SenseKiDataPacket.Entry entry : SenseKiClientState.sensed().values()) {
            if (!SenseKiClientState.passesFilter(entry, mc)) continue;

            Entity ent = mc.level.getEntity(entry.entityId());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;
            if (le == mc.player) continue;

            Vec3 p = le.getPosition(pt).add(0, le.getBbHeight() + Y_OFFSET, 0);

            pose.pushPose();
            pose.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            pose.mulPose(cam.rotation());          // billboard: mira a la cámara
            pose.scale(-0.025f, -0.025f, 0.025f);  // convención nametag

            drawEntry(pose.last().pose(), vc, entry, mc, lvl);
            pose.popPose();
        }

        buffers.endBatch(RenderType.textBackgroundSeeThrough());
    }

    /** Pinta lo que el nivel permita ver de esta entidad. */
    private static void drawEntry(Matrix4f m, VertexConsumer vc, SenseKiDataPacket.Entry en,
                                  Minecraft mc, int lvl) {
        // ── Fila de marcadores (arriba, en Y negativa) ──
        boolean showAlign = lvl >= 3;
        float markY0 = -(MARK + 2f), markY1 = -2f;
        if (showAlign) {
            // Dos marcadores centrados: fuerza a la izquierda, alineamiento a la derecha.
            drawMark(m, vc, -(MARK + 1f), markY0, -1f, markY1, strengthColor(en, mc));
            drawMark(m, vc, 1f, markY0, MARK + 1f, markY1,
                    AlignmentPalette.forAlignment(en.alignment()));
        } else {
            drawMark(m, vc, -MARK / 2f, markY0, MARK / 2f, markY1, strengthColor(en, mc));
        }

        // ── Barras (hacia abajo) ──
        float y = 0f;
        if (lvl >= 2) {
            drawBar(m, vc, y, en.body(), en.bodyMax(), 0); // 0 = degradado verde->rojo
            y += BAR_H + BAR_GAP;
        }
        if (lvl >= 4) {
            drawBar(m, vc, y, en.energy(), en.energyMax(), C_KI);
            y += BAR_H + BAR_GAP;
        }
        if (lvl >= 5) {
            drawBar(m, vc, y, en.stamina(), en.staminaMax(), C_STAMINA);
        }
    }

    /** Rojo si es más fuerte que tú, amarillo si va parejo, verde si es más débil. */
    private static int strengthColor(SenseKiDataPacket.Entry en, Minecraft mc) {
        if (mc.player == null) return C_SIMILAR;
        var att = com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment.get(mc.player);
        long myPl = att.isRaceChosen()
                ? att.getPowerLevel()
                : Math.round(mc.player.getMaxHealth());
        if (myPl <= 0) return C_SIMILAR;

        double t = com.hmc.zenkai.core.config.StatsConfig.senseKiSimilarThreshold();
        if (t <= 0.0 || t >= 1.0) t = 0.8; // banda inválida en config: valor sensato

        // La banda de "similar" es simétrica: [myPl*t, myPl/t].
        if (en.powerLevel() < myPl * t) return C_WEAKER;
        if (en.powerLevel() > myPl / t) return C_STRONGER;
        return C_SIMILAR;
    }

    /** Marcador cuadrado con borde (mismo criterio anti z-fighting que las barras). */
    private static void drawMark(Matrix4f m, VertexConsumer vc,
                                 float x0, float y0, float x1, float y1, int color) {
        quad(vc, m, x0, y0, x1, y1, color);
        quad(vc, m, x0 - 1, y0 - 1, x1 + 1, y0,     C_EDGE); // arriba
        quad(vc, m, x0 - 1, y1,     x1 + 1, y1 + 1, C_EDGE); // abajo
        quad(vc, m, x0 - 1, y0,     x0,     y1,     C_EDGE); // izquierda
        quad(vc, m, x1,     y0,     x1 + 1, y1,     C_EDGE); // derecha
    }

    /**
     * Barra en la altura yTop. Layout SIN solapamiento (anti z-fighting):
     *   - relleno: desde la izquierda hasta pct (opaco)
     *   - fondo:   desde pct hasta la derecha (translúcido)
     *   - borde:   4 tiras alrededor, fuera del interior
     * @param color 0 = degradado verde->rojo según el llenado (vida); si no, color fijo.
     */
    private static void drawBar(Matrix4f m, VertexConsumer vc, float yTop,
                                int cur, int max, int color) {
        float half = BAR_W / 2f;
        float pct  = (max > 0) ? Math.max(0f, Math.min(1f, cur / (float) max)) : 0f;

        float left = -half, right = half;
        float fillEnd = left + BAR_W * pct;
        float yBot = yTop + BAR_H;

        int fill;
        if (color == 0) {
            // Verde (llena) -> rojo (vacía).
            int r = Math.min(255, (int) (255 * (1f - pct)));
            int g = Math.min(255, (int) (220 * pct) + 35);
            fill = 0xFF000000 | (r << 16) | (g << 8) | 0x20;
        } else {
            fill = color;
        }

        // Interior: dos tramos que NO se tocan entre sí.
        if (pct > 0f)  quad(vc, m, left,    yTop, fillEnd, yBot, fill);
        if (pct < 1f)  quad(vc, m, fillEnd, yTop, right,   yBot, C_BG);

        // Borde: 4 tiras alrededor del interior (sin pisarlo).
        quad(vc, m, left - 1, yTop - 1, right + 1, yTop,     C_EDGE); // arriba
        quad(vc, m, left - 1, yBot,     right + 1, yBot + 1, C_EDGE); // abajo
        quad(vc, m, left - 1, yTop,     left,      yBot,     C_EDGE); // izquierda
        quad(vc, m, right,    yTop,     right + 1, yBot,     C_EDGE); // derecha
    }

    /**
     * Quad de UNA cara, con el winding que el RenderType considera frontal con nuestra
     * transformación de billboard (verificado en juego: el orden inverso lo culleaba).
     */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
    }
}