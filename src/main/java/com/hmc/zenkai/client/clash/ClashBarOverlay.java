package com.hmc.zenkai.client.clash;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.technique.clash.ClashResultPacket;
import com.hmc.zenkai.feature.technique.clash.ClashRules;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD del choque de haces: fichas de los dos participantes, núcleo de energía, insignia de
 * velocidad y la barra del minijuego, que en el resultado se convierte en la de tira y afloja.
 *
 * Las piezas salen del atlas `clash_hud.png` (fuente única: tools/gen_clash_hud.py; sus
 * coordenadas son las constantes U_* / V_* de aquí). La barra se dibuja SIEMPRE en un espacio
 * local horizontal; la orientación vertical es la misma función con un giro de 90°, que sigue
 * siendo pixel-perfect. La posición del cursor sale de ClashRules.cursor, la MISMA función con la
 * que el servidor evalúa la pulsación.
 *
 * Nada de lo que se pinta antes del resultado depende del valor del rival: solo se ve SI pulsó
 * (su check), nunca dónde. El núcleo tampoco se inclina hacia nadie hasta que llega el resultado.
 *
 * Fases: contacto (fichas + núcleo creciendo + «BEAM CLASH!», sin barra), minijuego (la barra
 * entra en 4 ticks; aguja con estela según la velocidad), pulsado (destello de 3 ticks, aguja
 * dorada, check y «esperando…») y resultado (banner, cifras que suben, tira y afloja y el núcleo
 * yendo hacia el perdedor o estallando en el empate).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClashBarOverlay {
    private ClashBarOverlay() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/hud/clash_hud.png");
    private static final int TEX_W = 256, TEX_H = 64;

    // ── Atlas (== tools/gen_clash_hud.py) ──
    private static final int FRAME_W = 158, FRAME_H = 18;           // (0,0)
    private static final int IN_X = 4, IN_Y = 3, IN_W = 150, IN_H = 12;
    private static final int V_INTERIOR = 18;                        // (0,18)
    private static final int U_NEEDLE = 160, U_NEEDLE_GOLD = 166, NEEDLE_W = 5, NEEDLE_H = 22;
    private static final int U_TRAIL = 172, TRAIL_W = 9, TRAIL_H = 12;
    private static final int V_ROW2 = 32;
    private static final int U_CARD = 0, CARD = 30, FACE = 24;
    private static final int U_CHECK = 32, CHECK = 11;
    private static final int U_VS = 44, VS_W = 13, VS_H = 9;
    private static final int U_BADGE = 60, BADGE_W = 32, BADGE_H = 12;
    private static final int U_CHEVRON = 94, CHEVRON_W = 5, CHEVRON_H = 7;
    private static final int U_CORE = 112, CORE = 16;                 // 4 tamaños + 3 estallidos
    private static final int U_BANNER_L = 224, U_BANNER_C = 230, U_BANNER_R = 234;
    private static final int BANNER_CAP = 6, BANNER_TILE = 4, BANNER_H = 16;
    private static final int U_DIAMOND = 242, DIAMOND = 7;

    // ── Paleta (== tools/gen_clash_hud.py) ──
    private static final int C_OFFWHITE = 0xFFD9D9DE;
    private static final int C_MUTED = 0xFF74747F;
    private static final int C_GOLD = 0xFFFFD84A;
    private static final int C_COLD = 0xFFE6F0FF;
    private static final int C_LOSE_ACCENT = 0xFFC07070;
    private static final int C_TIME_WARN = 0xFFFF6A5A;
    /** Placa translúcida detrás de cada texto: el HUD se pinta encima de la esfera del choque, que
     *  es casi blanca, y sin ella el dorado y el gris claro no se leían (un 908 se leyó como 998). */
    private static final int C_PLATE = 0x900C0C10;

    // ── Disposición ──
    private static final int CARD_OFFSET = 70;
    private static final int CARD_TOP = 12;
    private static final int H_BAR_BELOW_CROSSHAIR = 56;
    private static final int V_BAR_RIGHT_OF_CROSSHAIR = 78;

    /** Escala de GUI efectiva con la que se pinta este HUD, sea cual sea la del jugador. */
    private static final double HUD_SCALE = 2.0;

    // ── Tiempos de animación (ticks) ──
    private static final float BAR_IN_TICKS = 4f;
    private static final float FLASH_TICKS = 3f;
    private static final float CHECK_IN_TICKS = 5f;
    private static final float NUMBERS_TICKS = 8f;
    private static final float TUG_TICKS = 14f;
    private static final int TUG_RANGE = 300;
    private static final float TUG_MIN_LEAD = 0.12f;
    private static final float CORE_RESULT_TICKS = 12f;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!ClashClientState.active() || ClashClientState.params() == null) return;

        GuiGraphics g = e.getGuiGraphics();
        long now = mc.level.getGameTime();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = now + partial;

        // Escala fija ×2, como las pantallas del mod (ZenkaiGuiScaleLock), pero SIN tocar el GUI
        // Scale real: esto es un overlay sin clics, así que basta con una transformación del
        // PoseStack (el motivo por el que las pantallas no pueden hacerlo —el ratón llega en las
        // coordenadas reales— aquí no existe). Cambiar la opción real a mitad de combate
        // redimensionaría también la hotbar y las barras de HP/KI durante el choque. Con GUI Scale
        // 3/4/Auto, la ficha «YOU» se metía debajo de las barras de HP/STM (capturas 2026-10-08).
        // Si la ventana es tan pequeña que la escala real es 1, se respeta (a ×2 no cabría).
        double realScale = mc.getWindow().getGuiScale();
        float k = (float) (Math.min(HUD_SCALE, realScale) / realScale);
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        int w = Math.round(g.guiWidth() / k), h = Math.round(g.guiHeight() / k);
        boolean vertical = ClientConfig.clashBarStyle() == 1;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int res = ClashClientState.result();
        boolean contact = res < 0 && ClashClientState.beforeStart(now);

        drawCards(g, mc, w, t);
        drawCore(g, w, t, contact);

        // Centro del bloque de la barra y anclas de texto (encima y debajo de ella).
        int barCx = vertical ? w / 2 + V_BAR_RIGHT_OF_CROSSHAIR : w / 2;
        int barCy = vertical ? h / 2 : h / 2 + H_BAR_BELOW_CROSSHAIR;
        int halfAlong = FRAME_W / 2, halfAcross = FRAME_H / 2;
        int top = barCy - (vertical ? halfAlong : halfAcross);
        int bottom = barCy + (vertical ? halfAlong : halfAcross);

        Font font = mc.font;
        if (contact) {
            drawContactTitle(g, font, w, t);
        } else if (res >= 0) {
            drawBar(g, barCx, barCy, vertical, 1f, 0f, true, t);
            drawBanner(g, font, w, res, t);
            drawTeamTotals(g, font, w, res, t);
        } else {
            float in = clamp01((float) ClashClientState.elapsed(now, partial) / BAR_IN_TICKS);
            drawBar(g, barCx, barCy, vertical, in, (1f - in) * 6f, false, t);
            int rowY = top - BADGE_H - 4;
            Component timer = timerText(now, partial);
            int timerCol = withAlpha(timerColor(now, partial), in);
            if (vertical) {
                // Insignia centrada encima de la barra y el tiempo encima de la insignia.
                int bw = speedBadgeWidth(font);
                drawSpeedBadge(g, font, barCx - bw / 2, rowY, in);
                plate(g, font, timer, barCx, rowY - 12, in);
                g.drawCenteredString(font, timer, barCx, rowY - 12, timerCol);
            } else {
                // Insignia a la izquierda y tiempo a la derecha, alineados con los extremos del marco.
                int left = barCx - FRAME_W / 2, right = barCx + FRAME_W / 2;
                drawSpeedBadge(g, font, left, rowY, in);
                int tw = font.width(timer);
                plate(g, font, timer, right - tw / 2, rowY + 2, in);
                g.drawString(font, timer, right - tw, rowY + 2, timerCol);
            }
            // Esperando también si MI barra acabó sin pulsar: con incorporaciones (modo
            // «alargar») el choque sigue hasta que termine la barra del último en llegar.
            boolean waiting = ClashClientState.pressed() || ClashClientState.myBarOver(now);
            Component hint = waiting
                    ? Component.translatable("zenkai.clash.waiting")
                    : Component.translatable("zenkai.clash.press");
            int col = waiting ? C_OFFWHITE : C_GOLD;
            plate(g, font, hint, barCx, bottom + 6, in);
            g.drawCenteredString(font, hint, barCx, bottom + 6, withAlpha(col, in));
        }

        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    // ── Tiempo restante ─────────────────────────────────────────────────────────────────────

    /** Segundos por debajo de los que el tiempo se pinta en rojo (sin parpadeo). */
    private static final float TIMER_WARN_SECONDS = 1f;

    /** Segundos que quedan del plazo. Sale de `Params.duration`, que viaja en ClashStartPacket con
     *  el valor de la config del SERVIDOR: el cliente no usa su propia copia. */
    private static float secondsLeft(long now, float partial) {
        double left = ClashClientState.params().duration() - ClashClientState.elapsed(now, partial);
        return (float) Math.max(0.0, left) / 20f;
    }

    /** Dos cifras significativas: «12 s», «4.9 s», «0.85 s». Locale.ROOT para que el separador
     *  decimal no dependa del idioma del sistema. */
    private static Component timerText(long now, float partial) {
        float s = secondsLeft(now, partial);
        String num = s >= 10f ? String.format(java.util.Locale.ROOT, "%.0f", s)
                : s >= 1f ? String.format(java.util.Locale.ROOT, "%.1f", s)
                : String.format(java.util.Locale.ROOT, "%.2f", s);
        return Component.translatable("zenkai.clash.time", num);
    }

    private static int timerColor(long now, float partial) {
        return secondsLeft(now, partial) < TIMER_WARN_SECONDS ? C_TIME_WARN : C_OFFWHITE;
    }

    // ── Barra (espacio local horizontal; la vertical es un giro de -90°) ─────────────────────

    /**
     * @param alpha     opacidad de entrada
     * @param slide     desplazamiento de entrada, en px locales «hacia abajo»
     * @param resultBar si es la tira y afloja del resultado en vez del minijuego
     */
    private static void drawBar(GuiGraphics g, int cx, int cy, boolean vertical, float alpha,
                                float slide, boolean resultBar, double t) {
        var pose = g.pose();
        pose.pushPose();
        if (vertical) {
            // Local +x = arriba en pantalla, local +y = derecha.
            pose.translate(cx - FRAME_H / 2f + slide, cy + FRAME_W / 2f, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(-90f));
        } else {
            pose.translate(cx - FRAME_W / 2f, cy - FRAME_H / 2f + slide, 0f);
        }
        g.setColor(1f, 1f, 1f, alpha);

        g.blit(TEX, 0, 0, 0, 0, FRAME_W, FRAME_H, TEX_W, TEX_H);
        if (resultBar) drawTug(g, t);
        else {
            g.blit(TEX, IN_X, IN_Y, 0, V_INTERIOR, IN_W, IN_H, TEX_W, TEX_H);
            drawNeedle(g, t, alpha);
        }

        g.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    private static void drawNeedle(GuiGraphics g, double t, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level.getGameTime();
        float partial = (float) (t - now);
        double v = ClashClientState.cursorNow(now, partial);
        int x = IN_X + (int) Math.round(clamp01((float) (v / ClashRules.MAX)) * (IN_W - 1));

        boolean pressed = ClashClientState.pressed();
        float sincePress = pressed ? (float) (t - ClashClientState.pressedAt()) : -1f;

        // Estela: ninguna en lenta, media en media y entera en rápida. Siempre detrás del sentido
        // de la marcha; la velocidad se lee en el movimiento, no en el tamaño de la aguja.
        if (!pressed) {
            int speed = ClashClientState.params().speed();
            int len = speed == ClashRules.SPEED_FAST ? TRAIL_W
                    : speed == ClashRules.SPEED_MEDIUM ? 5 : 0;
            int dir = ClashClientState.cursorDirection(now, partial);
            if (len > 0 && dir != 0) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(x, IN_Y, 0f);
                if (dir < 0) pose.scale(-1f, 1f, 1f);
                // La pieza tiene la parte densa a la derecha (pegada a la aguja): se recorta por
                // la izquierda para acortarla.
                g.setColor(1f, 1f, 1f, alpha * 0.85f);
                g.blit(TEX, -len - 2, 0, U_TRAIL + (TRAIL_W - len), 0, len, TRAIL_H,
                        TEX_W, TEX_H);
                g.setColor(1f, 1f, 1f, alpha);
                pose.popPose();
            }
        }

        int nx = x - NEEDLE_W / 2, ny = (FRAME_H - NEEDLE_H) / 2;
        boolean gold = pressed && sincePress >= FLASH_TICKS;
        g.blit(TEX, nx, ny, gold ? U_NEEDLE_GOLD : U_NEEDLE, 0, NEEDLE_W, NEEDLE_H, TEX_W, TEX_H);
        if (pressed && sincePress < FLASH_TICKS) {
            // Destello: dos chispas en cruz encima y debajo de la aguja recién fijada.
            int a = withAlpha(0xFFFFFFFF, alpha);
            for (int sy : new int[]{ny - 3, ny + NEEDLE_H + 2}) {
                g.fill(x - 2, sy, x + 3, sy + 1, a);
                g.fill(x, sy - 2, x + 1, sy + 3, a);
            }
        }
    }

    /** Tira y afloja del resultado: mi lado (izquierda / abajo) contra el del rival. */
    private static void drawTug(GuiGraphics g, double t) {
        int res = ClashClientState.result();
        int mine = Math.max(0, ClashClientState.mineValue());
        int rival = Math.max(0, ClashClientState.rivalValue());
        // Se dibuja la DIFERENCIA y no la proporción: con mine / (mine + rival), un 960 contra 908
        // daba un 51 % y la barra parecía empatada. TUG_RANGE puntos de ventaja llenan el 95 %, y
        // quien gana se queda al menos con TUG_MIN_LEAD de ventaja visual.
        float lead = Math.max(-0.45f, Math.min(0.45f, (mine - rival) / (float) TUG_RANGE));
        if (res == ClashResultPacket.WIN) lead = Math.max(lead, TUG_MIN_LEAD);
        else if (res == ClashResultPacket.LOSE) lead = Math.min(lead, -TUG_MIN_LEAD);
        float target = 0.5f + lead;
        float k = easeOut(clamp01((float) (t - ClashClientState.resultAt()) / TUG_TICKS));
        float r = 0.5f + (target - 0.5f) * k;

        int mineCol, rivalCol;
        if (res == ClashResultPacket.WIN) { mineCol = C_GOLD; rivalCol = C_MUTED; }
        else if (res == ClashResultPacket.LOSE) { mineCol = C_MUTED; rivalCol = C_GOLD; }
        else { mineCol = C_COLD; rivalCol = C_COLD; }

        int split = IN_X + Math.round(r * IN_W);
        g.fill(IN_X, IN_Y, IN_X + IN_W, IN_Y + IN_H, 0xFF18181E);
        g.fill(IN_X, IN_Y + 3, split, IN_Y + IN_H - 3, mineCol);
        g.fill(split, IN_Y + 3, IN_X + IN_W, IN_Y + IN_H - 3, rivalCol);
        g.blit(TEX, split - DIAMOND / 2, (FRAME_H - DIAMOND) / 2, U_DIAMOND, V_ROW2,
                DIAMOND, DIAMOND, TEX_W, TEX_H);
    }

    // ── Insignia de velocidad ───────────────────────────────────────────────────────────────

    private static Component speedLabel() {
        return Component.translatable("zenkai.clash.stage." + ClashClientState.params().speed());
    }

    private static int speedBadgeWidth(Font font) {
        return BADGE_W + 4 + font.width(speedLabel());
    }

    private static void drawSpeedBadge(GuiGraphics g, Font font, int x, int y, float alpha) {
        int speed = ClashClientState.params().speed();
        int tint = switch (speed) {
            case ClashRules.SPEED_FAST -> 0xFFFFF0A0;   // amarillo claro
            case ClashRules.SPEED_MEDIUM -> 0xFFFFFFFF; // blanco
            default -> 0xFFA0A0AA;                       // gris
        };
        Component label = speedLabel();

        g.setColor(1f, 1f, 1f, alpha);
        g.blit(TEX, x, y, U_BADGE, V_ROW2, BADGE_W, BADGE_H, TEX_W, TEX_H);
        int n = speed + 1;
        int chevW = n * 6 - 1;
        int chx = x + (BADGE_W - chevW) / 2;
        setColor(g, tint, alpha);
        for (int i = 0; i < n; i++) {
            g.blit(TEX, chx + i * 6, y + (BADGE_H - CHEVRON_H) / 2, U_CHEVRON, V_ROW2,
                    CHEVRON_W, CHEVRON_H, TEX_W, TEX_H);
        }
        g.setColor(1f, 1f, 1f, 1f);
        plate(g, font, label, x + BADGE_W + 4 + font.width(label) / 2, y + 2, alpha);
        g.drawString(font, label, x + BADGE_W + 4, y + 2, withAlpha(tint, alpha));
    }

    // ── Fichas ──────────────────────────────────────────────────────────────────────────────

    /** Separación entre fichas del mismo equipo (hacia fuera desde el líder) y escala de los
     *  ayudantes: el líder —quien disparó— sigue siendo la ficha grande. */
    private static final int HELPER_GAP = 44;
    private static final float HELPER_SCALE = 0.75f;
    private static final int HELPER_NAME_W = 40;

    private static void drawCards(GuiGraphics g, Minecraft mc, int w, double t) {
        int res = ClashClientState.result();
        int mineState = res == ClashResultPacket.WIN ? 1 : res == ClashResultPacket.LOSE ? -1
                : res == ClashResultPacket.TIE ? 2 : 0;
        int rivalState = mineState == 2 ? 2 : -mineState;

        var mine = ClashClientState.rosterMine();
        var rival = ClashClientState.rosterRival();
        if (mine.isEmpty() || rival.isEmpty()) {
            drawLegacyCards(g, mc, w, t, mineState, rivalState);
            return;
        }
        drawSide(g, mc, w, t, mine, -1, mineState, ClashClientState.mineValue(), ClashClientState.multMine());
        drawSide(g, mc, w, t, rival, 1, rivalState, ClashClientState.rivalValue(), ClashClientState.multRival());
    }

    /**
     * Un equipo: el líder en ±CARD_OFFSET y los ayudantes hacia fuera, más pequeños. Con un
     * solo miembro la cifra es el TOTAL del equipo (lo de siempre); con varios, cada ficha enseña
     * su aportación y los totales van bajo el banner ({@link #drawTeamTotals}).
     * @param dir -1 = mi lado (izquierda), +1 = el rival (derecha)
     */
    private static void drawSide(GuiGraphics g, Minecraft mc, int w, double t,
                                 java.util.List<com.hmc.zenkai.feature.technique.clash.ClashRosterPacket.Member> side,
                                 int dir, int state, int teamTotal, float mult) {
        for (int i = side.size() - 1; i >= 0; i--) {
            var m = side.get(i);
            int cx = w / 2 + dir * (CARD_OFFSET + i * HELPER_GAP);
            boolean me = mc.player != null && m.entityId() == mc.player.getId();
            Component label = me ? Component.translatable("zenkai.clash.you") : Component.literal(m.name());
            boolean pressed = me ? ClashClientState.pressed() : m.pressed();
            long pressedAt = me ? ClashClientState.pressedAt() : ClashClientState.pressedAtOf(m.entityId());
            int value = side.size() == 1 ? teamTotal : Math.max(0, m.value());
            float scale = i == 0 ? 1f : HELPER_SCALE;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, CARD_TOP, 0f);
            pose.scale(scale, scale, 1f);
            pose.translate(-cx, -CARD_TOP, 0f);
            drawCard(g, mc, cx, faceOf(mc, m), label, pressed, pressedAt, state, value, t,
                    i == 0 ? MAX_NAME_W : HELPER_NAME_W);
            pose.popPose();
        }
        // Insignia del equipo pequeño («×1.6») bajo el líder mientras no hay resultado.
        if (mult > 1.001f && state == 0) {
            String txt = "\u00D7" + String.format(java.util.Locale.ROOT, "%.1f", mult);
            int cx = w / 2 + dir * CARD_OFFSET;
            plate(g, mc.font, Component.literal(txt), cx, CARD_TOP + CARD + 14, 1f);
            g.drawCenteredString(mc.font, txt, cx, CARD_TOP + CARD + 14, C_GOLD);
        }
    }

    private static Face faceOf(Minecraft mc,
                               com.hmc.zenkai.feature.technique.clash.ClashRosterPacket.Member m) {
        if (m.isPlayer()) {
            var conn = mc.getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(m.uuid());
            return Face.of(info != null ? info.getSkin() : DefaultPlayerSkin.get(m.uuid()));
        }
        return mobFace(mc, m.entityId());
    }

    /** Totales de cada equipo bajo el banner, solo si algún lado tiene más de un miembro (en un
     *  1 contra 1 las cifras de las fichas ya son los totales). */
    private static void drawTeamTotals(GuiGraphics g, Font font, int w, int res, double t) {
        if (ClashClientState.rosterMine().size() <= 1 && ClashClientState.rosterRival().size() <= 1) return;
        float k = clamp01((float) (t - ClashClientState.resultAt()) / NUMBERS_TICKS);
        int mineCol = res == ClashResultPacket.WIN ? C_GOLD : res == ClashResultPacket.TIE ? C_COLD : C_OFFWHITE;
        int rivalCol = res == ClashResultPacket.LOSE ? C_GOLD : res == ClashResultPacket.TIE ? C_COLD : C_OFFWHITE;
        String a = Integer.toString(Math.round(ClashClientState.mineValue() * k));
        String b = Integer.toString(Math.round(ClashClientState.rivalValue() * k));
        String vs = "  \u2014  ";
        int total = font.width(a) + font.width(vs) + font.width(b);
        int x = w / 2 - total / 2, y = TITLE_Y + 22;
        g.fill(x - 3, y - 2, x + total + 3, y + 9, C_PLATE);
        g.drawString(font, a, x, y, mineCol);
        g.drawString(font, vs, x + font.width(a), y, C_MUTED);
        g.drawString(font, b, x + font.width(a) + font.width(vs), y, rivalCol);
    }

    /** Fichas de antes del primer roster (no debería verse más de un tick: el roster llega
     *  justo detrás de ClashStartPacket). */
    private static void drawLegacyCards(GuiGraphics g, Minecraft mc, int w, double t,
                                        int mineState, int rivalState) {
        drawCard(g, mc, w / 2 - CARD_OFFSET, Face.of(mc.player.getSkin()),
                Component.translatable("zenkai.clash.you"),
                ClashClientState.pressed(), ClashClientState.pressedAt(), mineState,
                ClashClientState.mineValue(), t, MAX_NAME_W);

        // Rival: cara de su skin si es un jugador (de la lista de jugadores, aunque la entidad no
        // esté cargada); si es un mob humanoide, la cara de su textura; si no, un «?».
        Face rivalFace;
        if (ClashClientState.rivalIsPlayer()) {
            var conn = mc.getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(ClashClientState.rivalUuid());
            rivalFace = Face.of(info != null ? info.getSkin()
                    : DefaultPlayerSkin.get(ClashClientState.rivalUuid()));
        } else {
            rivalFace = mobFace(mc, ClashClientState.rivalEntityId());
        }
        drawCard(g, mc, w / 2 + CARD_OFFSET, rivalFace,
                Component.literal(ClashClientState.rivalName()),
                ClashClientState.rivalPressed(), ClashClientState.rivalPressedAt(), rivalState,
                ClashClientState.rivalValue(), t, MAX_NAME_W);
    }

    /** Ancho máximo del nombre bajo la ficha: con las fichas a ±CARD_OFFSET del centro, más ancho
     *  invade el VS (un «Dev's Shadow» entero ya se le acercaba). */
    private static final int MAX_NAME_W = 64;

    private static String fitName(Font font, String name, int maxW) {
        if (font.width(name) <= maxW) return name;
        String dots = "…";
        return font.plainSubstrByWidth(name, maxW - font.width(dots)).stripTrailing() + dots;
    }

    /**
     * Cara de la ficha: región 8x8 en (8,8) de una textura con el formato de la skin del jugador,
     * más la capa de sombrero en (40,8). Las coordenadas se escalan con el ancho real de la
     * textura (una de 128 px tiene la cara en (16,16)) y el alto se respeta (64x32 de las skins
     * antiguas y del esqueleto vanilla).
     * {@code hat} solo cuando el modelo PINTA esa capa: los rigs GeckoLib del mod no tienen hueso
     * de sombrero y en shadow_clone.png esa región es un relleno oscuro opaco que tapaba la cara.
     */
    private record Face(ResourceLocation tex, int texW, int texH, boolean hat) {
        static Face of(PlayerSkin skin) { return new Face(skin.texture(), 64, 64, true); }

        void draw(GuiGraphics g, int x, int y, int size) {
            int k = Math.max(1, texW / 64);
            g.blit(tex, x, y, size, size, 8 * k, 8 * k, 8 * k, 8 * k, texW, texH);
            if (hat) g.blit(tex, x, y, size, size, 40 * k, 8 * k, 8 * k, 8 * k, texW, texH);
        }
    }

    /** Medidas reales de cada textura de mob ya leída: se leen UNA vez del ResourceManager, no
     *  por frame. -1 en las que no se pudieron leer (no se reintenta). */
    private static final java.util.Map<ResourceLocation, int[]> TEX_SIZES = new java.util.HashMap<>();

    /**
     * Cara del rival si es un mob con el formato de skin humanoide: los NPC GeckoLib del mod con
     * PosedHumanoidGeoModel (Sombra, y cualquier futuro NPC de combate humanoide) y los mobs
     * vanilla que se renderizan con HumanoidModel (zombi, esqueleto, piglin…). Cualquier otro
     * modelo tiene la textura en otro formato y daría un recorte sin sentido: «?». También «?»
     * si la entidad no está cargada en el cliente.
     */
    private static Face mobFace(Minecraft mc, int entityId) {
        var entity = mc.level.getEntity(entityId);
        if (entity == null) return null;
        var renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        boolean humanoid, geckolib = false;
        if (renderer instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> geo) {
            geckolib = true;
            humanoid = geo.getGeoModel() instanceof
                    com.hmc.zenkai.client.render_and_model_entities.entity.PosedHumanoidGeoModel<?>;
        } else {
            humanoid = renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> lr
                    && lr.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>;
        }
        if (!humanoid) return null;
        ResourceLocation tex = renderer.getTextureLocation(entity);
        int[] size = TEX_SIZES.computeIfAbsent(tex, ClashBarOverlay::readSize);
        if (size[0] <= 0) return null;
        return new Face(tex, size[0], size[1], !geckolib);
    }

    private static int[] readSize(ResourceLocation tex) {
        try (var in = Minecraft.getInstance().getResourceManager().open(tex);
             var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception ex) {
            return new int[]{-1, -1};
        }
    }

    /** @param state 0 en curso, 1 ganó, -1 perdió, 2 empate */
    private static void drawCard(GuiGraphics g, Minecraft mc, int cx, Face face,
                                 Component label, boolean pressed,
                                 long pressedAt, int state, int value, double t, int maxNameW) {
        int x = cx - CARD / 2, y = CARD_TOP;
        int fx = x + (CARD - FACE) / 2, fy = y + (CARD - FACE) / 2;

        if (face != null) {
            face.draw(g, fx, fy, FACE);
        } else {
            g.fill(fx, fy, fx + FACE, fy + FACE, 0xFF2A2A33);
            g.drawCenteredString(mc.font, "?", cx, fy + FACE / 2 - 4, 0xFFAAAAAA);
        }
        if (state == -1) g.fill(fx, fy, fx + FACE, fy + FACE, 0x800C0C10); // perdedor apagado

        // Marco gris neutro tintado por estado: dorado gana, blanco frío empate, apagado pierde.
        int ring = switch (state) {
            case 1 -> C_GOLD;
            case -1 -> C_MUTED;
            case 2 -> C_COLD;
            default -> 0xFF8A8A96;
        };
        setColor(g, ring, 1f);
        g.blit(TEX, x, y, U_CARD, V_ROW2, CARD, CARD, TEX_W, TEX_H);
        g.setColor(1f, 1f, 1f, 1f);

        String text = fitName(mc.font, label.getString(), maxNameW);
        int nameCol = state == 1 ? C_GOLD : state == -1 ? C_MUTED : 0xFFFFFFFF;
        plate(g, mc.font, Component.literal(text), cx, y + CARD + 3, 1f);
        g.drawCenteredString(mc.font, text, cx, y + CARD + 3, nameCol);

        // Cifra final, subiendo de 0 al valor en NUMBERS_TICKS.
        if (state != 0) {
            float k = clamp01((float) (t - ClashClientState.resultAt()) / NUMBERS_TICKS);
            int shown = Math.round(value * k);
            int col = state == 1 ? C_GOLD : state == 2 ? C_COLD : C_OFFWHITE;
            plate(g, mc.font, Component.literal(Integer.toString(shown)), cx, y + CARD + 14, 1f);
            g.drawCenteredString(mc.font, Integer.toString(shown), cx, y + CARD + 14, col);
        }

        // Check: entra con un pequeño rebote de escala. Solo dice QUE pulsó, no dónde.
        if (pressed && state == 0) {
            float k = clamp01((float) (t - pressedAt) / CHECK_IN_TICKS);
            float s = k < 0.6f ? 0.4f + k / 0.6f * 0.8f : 1.2f - (k - 0.6f) / 0.4f * 0.2f;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(x + CARD - 4, y + 4, 0f);
            pose.scale(s, s, 1f);
            g.blit(TEX, -CHECK / 2, -CHECK / 2, U_CHECK, V_ROW2, CHECK, CHECK, TEX_W, TEX_H);
            pose.popPose();
        }
    }

    // ── Núcleo de energía y VS ──────────────────────────────────────────────────────────────

    /** Entre las fichas: VS durante el minijuego; núcleo en el contacto (creciendo) y en el
     *  resultado (hacia el perdedor, o comprimiéndose y estallando en el empate). */
    private static void drawCore(GuiGraphics g, int w, double t, boolean contact) {
        int cy = CARD_TOP + CARD / 2;
        int res = ClashClientState.result();
        if (!contact && res < 0) {
            // A x2: a x1 (13x9) se perdía entre las dos fichas.
            var pose = g.pose();
            pose.pushPose();
            pose.translate(w / 2f - VS_W, cy - VS_H, 0f);
            pose.scale(2f, 2f, 1f);
            g.blit(TEX, 0, 0, U_VS, V_ROW2, VS_W, VS_H, TEX_W, TEX_H);
            pose.popPose();
            return;
        }
        int frame;
        float dx = 0f;
        float alpha = 1f;
        if (contact) {
            float since = (float) (t - ClashClientState.receivedAt());
            frame = Math.min(3, (int) (since / 2.5f));       // 2 → 4 → 6 → 8 px en ~8 ticks
        } else {
            float k = clamp01((float) (t - ClashClientState.resultAt()) / CORE_RESULT_TICKS);
            if (res == ClashResultPacket.TIE) {
                // Se comprime (8 → 4 → 2 px) y luego tres fotogramas de estallido.
                int step = (int) (k * 6f);
                frame = step < 3 ? 3 - step : 3 + Math.min(3, step - 2);
                if (k >= 1f) return;
            } else {
                // El haz ganador empuja el núcleo hasta la ficha del perdedor y lo absorbe.
                int toward = res == ClashResultPacket.WIN ? 1 : -1;
                dx = easeOut(k) * CARD_OFFSET * toward;
                frame = 3 - Math.min(3, (int) (k * 4f));
                alpha = 1f - Math.max(0f, (k - 0.75f) / 0.25f);
            }
        }
        // A x2, igual que el VS: a x1 era un punto apenas visible.
        var pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2f - CORE + dx, cy - CORE, 0f);
        pose.scale(2f, 2f, 1f);
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(TEX, 0, 0, U_CORE + frame * CORE, V_ROW2, CORE, CORE, TEX_W, TEX_H);
        g.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    // ── Textos grandes ──────────────────────────────────────────────────────────────────────

    private static final int TITLE_Y = CARD_TOP + CARD + 42;

    /** «BEAM CLASH!» con un «pop» pequeño hacia el tick 6, sin parpadeo. */
    private static void drawContactTitle(GuiGraphics g, Font font, int w, double t) {
        float since = (float) (t - ClashClientState.receivedAt());
        float pop = since >= 5f && since < 7f ? 2.25f : 2f;
        Component txt = Component.translatable("zenkai.clash.contact");
        var pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2f, TITLE_Y, 0f);
        pose.scale(pop, pop, 1f);
        g.drawCenteredString(font, txt, 0, -4, C_GOLD);
        pose.popPose();
    }

    /** Banner de resultado en tres piezas (tapa, centro repetido, tapa), a ×2 con el texto. */
    private static void drawBanner(GuiGraphics g, Font font, int w, int res, double t) {
        String key;
        int tint, textCol;
        switch (res) {
            case ClashResultPacket.WIN -> { key = "zenkai.clash.win"; tint = C_GOLD; textCol = C_GOLD; }
            case ClashResultPacket.LOSE -> { key = "zenkai.clash.lose"; tint = C_LOSE_ACCENT; textCol = C_OFFWHITE; }
            default -> { key = "zenkai.clash.tie"; tint = C_COLD; textCol = 0xFFFFFFFF; }
        }
        Component txt = Component.translatable(key);
        int inner = font.width(txt) + 6;
        int tiles = (inner + BANNER_TILE - 1) / BANNER_TILE;
        int bw = BANNER_CAP * 2 + tiles * BANNER_TILE;

        // Entrada: crece desde ×1.5 hasta ×2 en 4 ticks.
        float k = clamp01((float) (t - ClashClientState.resultAt()) / 4f);
        float scale = 1.5f + 0.5f * easeOut(k);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2f, TITLE_Y, 0f);
        pose.scale(scale, scale, 1f);
        int x = -bw / 2, y = -BANNER_H / 2;
        setColor(g, tint, 1f);
        g.blit(TEX, x, y, U_BANNER_L, V_ROW2, BANNER_CAP, BANNER_H, TEX_W, TEX_H);
        for (int i = 0; i < tiles; i++) {
            g.blit(TEX, x + BANNER_CAP + i * BANNER_TILE, y, U_BANNER_C, V_ROW2,
                    BANNER_TILE, BANNER_H, TEX_W, TEX_H);
        }
        g.blit(TEX, x + bw - BANNER_CAP, y, U_BANNER_R, V_ROW2, BANNER_CAP, BANNER_H,
                TEX_W, TEX_H);
        g.setColor(1f, 1f, 1f, 1f);
        g.drawCenteredString(font, txt, 0, -4, textCol);
        pose.popPose();
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────────────────

    private static void setColor(GuiGraphics g, int argb, float alpha) {
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, alpha);
    }

    /** Aplica un alfa 0..1 a un color. Mínimo 4: la fuente vanilla trata un alfa por debajo de 4
     *  como «sin alfa» y pintaría el texto opaco justo en el primer tick del fundido. */
    private static int withAlpha(int argb, float alpha) {
        int a = Math.max(4, Math.round(((argb >>> 24) & 0xFF) * clamp01(alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Placa oscura translúcida detrás de una línea de texto centrada en {@code cx}. */
    private static void plate(GuiGraphics g, Font font, Component text, int cx, int y, float alpha) {
        int hw = font.width(text) / 2 + 3;
        int a = Math.round(((C_PLATE >>> 24) & 0xFF) * clamp01(alpha));
        g.fill(cx - hw, y - 2, cx + hw, y + 9, (a << 24) | (C_PLATE & 0xFFFFFF));
    }

    private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

    private static float easeOut(float k) { return 1f - (1f - k) * (1f - k); }
}
