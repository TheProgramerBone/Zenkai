package com.hmc.zenkai.client.overlay.hud;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.event.ClientZenkaiHooks;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.OverlayStateSystem;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.opengl.GL11;

/**
 * HUD MODERNO de Body/Stamina/Ki, pixel art vanilla-like — el estilo por defecto. El minimalista
 * de siempre sigue en ClientZenkaiHooks.onRenderGui y se elige en la config de cliente
 * (hud.bars_style). Referencia: .claude/pendiente/Hoja de diseño HUD Zenkai en pixel art.png.
 * Sustituye a un primer intento "neón" (degradados y glow) que el usuario descartó el
 * 2026-10-09 porque "no se ve Minecraft".
 *
 * ESCALA ENTERA: se dibuja en un espacio de TEXELS (1 unidad = 1 píxel del atlas) con
 * pose().scale(k / guiScale), donde k = píxeles REALES por texel, siempre entero. Así cada píxel
 * del atlas y la fuente se ven exactos, sin el borroso/irregular de un escalado fraccionario. El
 * slider hud_bars_scale elige k = round(4 · escala): 65 % por defecto ⇒ 3, 20 % ⇒ 1, 150 % ⇒ 6.
 *
 * Arte: textures/gui/hud_modern.png de tools/gen_hud_modern.py; geometría DUPLICADA en el script.
 * Capas por fila, de abajo arriba: fondo vacío (MASK en gris oscuro, sin teñir) → estela de daño →
 * relleno → lavado de estado (Kaioken/strain/crítico) → muescas del ki → marco tintado. El
 * marco, el del retrato, la placa del PL y los marcos de icono son grises y se tiñen con
 * {@link #frameRgb}: hud.frame_color en base (negro por defecto) y, si hud.frame_follows_form,
 * el color del aura de la forma (Kaioken incluido); crítico, strain y stamina agotada ganan
 * mientras duran, sigan o no a la forma.
 */
public final class ModernStatsHud {
    private ModernStatsHud() {}

    public static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/hud_modern.png");
    private static final int TEX_W = 256, TEX_H = 208;

    // ── Atlas (== tools/gen_hud_modern.py) ───────────────────────────────────
    private static final int LAYER_V_FRAME = 56;   // solo el anillo del marco (gris, tintable)
    private static final int LAYER_V_FILL = 104;
    private static final int LAYER_V_MASK = 152;   // interior en blanco

    /** Una barra: x/y en texels dentro del bloque; w incluye la punta de flecha (tip). */
    private record Row(int x, int y, int w, int h, int tip, int v) {}

    /** Contorno + bisel: el interior de cada barra empieza a esta distancia del borde. */
    private static final int BAR_RIM = 2;
    /** Alto del texto vanilla: 7 px de glifo + 1 de sombra. */
    private static final int TEXT_H = 8;

    // Tres rondas en juego (2026-10-09). Anchos: 100/92/84 → 120/112/104 → 136/128/120 ("KI" y
    // "70.1K / 70.1K" se pisaban). Altos: 11/9/9 → 13/12/12 → 15/14/14. El interior (alto −
    // 2·BAR_RIM) pasó de 7/5/5 (el texto de 8 pisaba el bisel) a 9/8/8 (cabía, pero rozando: "se
    // chocan los textos") a 11/10/10: al menos 1 px de aire arriba y abajo del texto.
    // Posición (cuarta ronda, 2026-10-09): las barras arrancan DETRÁS del retrato (BEHIND px
    // ocultos bajo él, efecto de profundidad pedido por el usuario) y el bloque (y 4..48) va
    // centrado en vertical: 4 px libres arriba y abajo. Cabe justo en el tramo RECTO del lado
    // derecho del retrato (53 − 2·chaflán de 4 = 45 px). Fuera de ese tramo, por los chaflanes
    // asomaría la esquina de la barra: pasó con HP en y = 1 ("un pequeño espacio", TOC), y al
    // sacar las barras de debajo del retrato el bloque quedó descentrado hacia abajo.
    private static final Row HP = new Row(45, 4, 144, 15, 7, 0);
    private static final Row STM = new Row(45, 20, 136, 14, 6, 16);
    private static final Row KI = new Row(45, 35, 128, 14, 6, 31);
    /** Aire horizontal entre el texto y el borde interior de la barra (etiqueta y cifras). */
    private static final int TEXT_PAD_X = 5;

    /** 53 (32 → 40 → 48 → 53): ver el comentario de las filas — el tramo recto de su lado
     *  derecho tiene que medir lo que el bloque de barras. */
    private static final int PORTRAIT = 53;
    /** Interior del retrato (== 4..48 del script): ahí va el jugador. */
    private static final int PORTRAIT_INSET = 4;
    private static final int U_PORTRAIT_FRAME = 0, U_PORTRAIT_BACK = 53;
    /** Píxeles de cada barra ocultos detrás del retrato. El relleno recorre solo la parte
     *  VISIBLE: si contara la oculta, con poca vida la barra se vería vacía. */
    private static final int BEHIND = PORTRAIT - 45;

    private static final int U_PL = 106, PL_CAP = 4, PL_H = 14, PL_Y = 55, PL_MIN_W = 53;
    private static final int U_ICON = 115, ICON_FRAME = 22, ICON_STEP = ICON_FRAME + 1;

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int PANEL_X = 10;   // GUI px, igual que el minimalista
    private static final int PANEL_Y = 10;
    /** Píxeles reales por texel con hud_bars_scale = 100 % (se redondea a entero). Era 3 (65 % ⇒
     *  k = 2, lo de GUI Scale 2): en juego se veía "muy pequeño" (2026-10-09), así que 4 ⇒ con el
     *  65 % por defecto k = 3. */
    private static final float PIXELS_PER_TEXEL_AT_100 = 4f;

    // ── Colores ──────────────────────────────────────────────────────────────
    /** El tinte multiplica: con un color muy oscuro (el negro por defecto de hud.frame_color) el
     *  marco quedaba plano, sin bisel. Cada canal se levanta a FRAME_LIFT + (1 − FRAME_LIFT)·c,
     *  así el negro sale como un gris muy oscuro donde aún se lee la luz del bisel; en colores
     *  vivos el cambio apenas se nota. */
    private static final float FRAME_LIFT = 0.2f;
    private static final int EXHAUSTED_GREY = 0x9A9EA6;
    private static final float EMPTY_SHADE = 40 / 255f;   // fondo vacío de las barras
    private static final int PL_TEXT = 0xFFFFE066;
    /** Estela de daño: blanco rosado, como el parpadeo de los corazones vanilla al recibir daño. */
    private static final float[] TRAIL_RGB = {1f, 0.80f, 0.80f};

    // ── Animación ────────────────────────────────────────────────────────────
    private static final long TRAIL_HOLD_MS = 400;
    private static final float TRAIL_MIN_DRAIN = 0.35f;   // fracción del máximo por segundo
    private static final float FRAME_COLOR_RATE = 0.15f;

    // ── Retrato ──────────────────────────────────────────────────────────────
    /** Cámara al busto: el recuadro se centra en bbHeight/2 + yOffset·escala (ver
     *  HeadAppearanceScreen.FACE_Y_OFFSET; 0.65 = solo la cara). */
    private static final float PORTRAIT_Y_OFFSET = 0.55f;
    /** Píxeles por bloque del modelo, en texels (cabeza ≈ 20 de los 45 del interior). */
    private static final float PORTRAIT_MODEL_SIZE = 40f;
    /** Giro hacia las barras (lo mismo que daría el ratón algo a la derecha). */
    private static final float PORTRAIT_YAW = -0.35f;

    /** true mientras se dibuja el retrato: los hooks que guardan estado de la pasada "real" del
     *  jugador (AfterImageClient) deben ignorar esta. */
    public static boolean renderingPortrait = false;

    private static final class BarState {
        float disp = -1f, lastMax = -1f, trail = -1f, prevTarget = -1f;
        long holdUntil, lastMs;
    }

    private static final BarState HP_STATE = new BarState();
    private static final BarState STM_STATE = new BarState();
    private static final BarState KI_STATE = new BarState();
    private static float frameR = -1f, frameG, frameB;

    public static void render(GuiGraphics g, Minecraft mc, PlayerStatsAttachment stats, PlayerFormAttachment form) {
        long nowMs = System.currentTimeMillis();
        long gameTime = mc.player.level().getGameTime();
        KaiokenTier tier = form.getKaioken();
        boolean strained = form.isStrained(gameTime);

        float bodyMax = stats.getBodyMax();
        float bodyPct = ClientZenkaiHooks.pctOf(stats.getBody(), bodyMax);
        float staminaPct = ClientZenkaiHooks.pctOf(stats.getStamina(), stats.getStaminaMax());
        int frame = frameRgb(mc.player, form, tier, strained, bodyPct, staminaPct);

        // k píxeles reales por texel, ENTERO (ver javadoc de clase); f lo pasa a unidades de GUI.
        int k = Math.max(1, Math.round(PIXELS_PER_TEXEL_AT_100 * ClientConfig.hudBarsScaleFrac()));
        float f = (float) (k / mc.getWindow().getGuiScale());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bodyWash = tier.isOn() ? (0x80000000 | (ClientZenkaiHooks.C_BODY_KAIOKEN & 0xFFFFFF))
                : strained ? (0x80000000 | (ClientZenkaiHooks.C_BODY_STRAIN & 0xFFFFFF))
                : (bodyPct > 0f && bodyPct <= ClientZenkaiHooks.BODY_CRITICAL_FRAC)
                        ? ClientZenkaiHooks.criticalPulseColor() : 0;

        g.pose().pushPose();
        g.pose().translate(PANEL_X, PANEL_Y, 0);
        g.pose().scale(f, f, 1f);

        drawRow(g, mc, HP, HP_STATE, stats.getBody(), bodyMax, "HP", bodyWash, frame, nowMs);
        drawRow(g, mc, STM, STM_STATE, stats.getStamina(), stats.getStaminaMax(), "STM", 0, frame, nowMs);
        drawRow(g, mc, KI, KI_STATE, stats.getEnergy(), stats.getEnergyMax(), "KI", 0, frame, nowMs);

        // Fondo del retrato (el jugador va encima, fuera de este pose: ver drawPortraitModel).
        g.blit(TEX, 0, 0, U_PORTRAIT_BACK, 0, PORTRAIT, PORTRAIT, TEX_W, TEX_H);
        g.pose().popPose();

        drawPortraitModel(g, mc, f);

        g.pose().pushPose();
        g.pose().translate(PANEL_X, PANEL_Y, 0);
        g.pose().scale(f, f, 1f);

        // Marco del retrato DESPUÉS de las barras: tapa su arranque (salen del retrato).
        tint(g, frame);
        g.blit(TEX, 0, 0, U_PORTRAIT_FRAME, 0, PORTRAIT, PORTRAIT, TEX_W, TEX_H);

        // Placa del PL: tapa izquierda + columna central estirada + tapa derecha.
        String pl = "PL " + ZenkaiNumbers.format(stats.getApparentPowerLevel());
        int plW = Math.max(PL_MIN_W, mc.font.width(pl) + 2 * (PL_CAP + 2) + 1);
        g.blit(TEX, 0, PL_Y, U_PL, 0, PL_CAP, PL_H, TEX_W, TEX_H);
        g.blit(TEX, PL_CAP, PL_Y, plW - 2 * PL_CAP, PL_H, U_PL + PL_CAP, 0, 1, PL_H, TEX_W, TEX_H);
        g.blit(TEX, plW - PL_CAP, PL_Y, U_PL + PL_CAP + 1, 0, PL_CAP, PL_H, TEX_W, TEX_H);
        g.setColor(1f, 1f, 1f, 1f);
        g.drawString(mc.font, pl, (plW - mc.font.width(pl)) / 2 + 1,
                PL_Y + BAR_RIM + (PL_H - 2 * BAR_RIM - TEXT_H + 1) / 2, PL_TEXT);

        // Iconos de estado: la MISMA lista que el minimalista, cada uno en su marco tintado.
        int ix = plW + 2;
        for (ClientZenkaiHooks.Badge b : ClientZenkaiHooks.collectBadges(mc, stats, form, tier, strained, gameTime)) {
            tint(g, frame);
            g.blit(TEX, ix, PL_Y, U_ICON, 0, ICON_FRAME, ICON_FRAME, TEX_W, TEX_H);
            g.setColor(1f, 1f, 1f, 1f);
            ClientZenkaiHooks.drawBadge(g, ix + 1, PL_Y + 1, b.icon());
            // Etiqueta ("229s", "x20") DEBAJO del marco, centrada y a escala 1. En la esquina del
            // icono (como el minimalista) se salía del marco y tapaba el dibujo (visto en juego
            // con strain, 2026-10-09). Más pequeña no: media escala no cae en píxeles enteros y la
            // fuente se vería irregular. Si la etiqueta es más ancha que el marco, el paso crece
            // para que no pise la del icono siguiente.
            int step = ICON_STEP;
            if (b.label() != null && !b.label().isEmpty()) {
                int lw = mc.font.width(b.label());
                g.drawString(mc.font, b.label(), ix + (ICON_FRAME - lw) / 2, PL_Y + ICON_FRAME + 2,
                        b.labelColor(), true);
                step = Math.max(step, lw + 2);
            }
            ix += step;
        }
        g.pose().popPose();
    }

    // ── Barras ───────────────────────────────────────────────────────────────

    private static void drawRow(GuiGraphics g, Minecraft mc, Row row, BarState st, float cur, float max,
                                String label, int washArgb, int frame, long nowMs) {
        updateState(st, cur, max, nowMs);
        int fillW = visibleFill(row, ClientZenkaiHooks.pctOf(st.disp, max));
        int trailW = visibleFill(row, ClientZenkaiHooks.pctOf(st.trail, max));

        // 1) Fondo vacío: la máscara en gris oscuro, SIN el tinte del marco.
        g.setColor(EMPTY_SHADE, EMPTY_SHADE, EMPTY_SHADE * 1.15f, 1f);
        g.blit(TEX, row.x(), row.y(), 0, LAYER_V_MASK + row.v(), row.w(), row.h(), TEX_W, TEX_H);

        // 2) Estela de daño: entre el valor mostrado y donde estaba antes del golpe.
        if (trailW > fillW) {
            g.setColor(TRAIL_RGB[0], TRAIL_RGB[1], TRAIL_RGB[2], 1f);
            g.blit(TEX, row.x() + fillW, row.y(), fillW, LAYER_V_MASK + row.v(), trailW - fillW, row.h(),
                    TEX_W, TEX_H);
        }
        g.setColor(1f, 1f, 1f, 1f);

        // 3) Relleno recortado por ancho (la punta es lo último en llenarse) y 4) lavado.
        if (fillW > 0) {
            g.blit(TEX, row.x(), row.y(), 0, LAYER_V_FILL + row.v(), fillW, row.h(), TEX_W, TEX_H);
            if (washArgb != 0) {
                g.setColor(((washArgb >> 16) & 0xFF) / 255f, ((washArgb >> 8) & 0xFF) / 255f,
                        (washArgb & 0xFF) / 255f, ((washArgb >>> 24) & 0xFF) / 255f);
                g.blit(TEX, row.x(), row.y(), 0, LAYER_V_MASK + row.v(), fillW, row.h(), TEX_W, TEX_H);
                g.setColor(1f, 1f, 1f, 1f);
            }
        }

        // Sin segmentos en el ki: se probaron muescas (primero de lado a lado, luego en los bordes)
        // y el usuario prefirió quitarlas — "no tiene sentido que la de ki sí las tenga y las otras
        // no" (2026-10-09).

        // 5) Marco tintado.
        tint(g, frame);
        g.blit(TEX, row.x(), row.y(), 0, LAYER_V_FRAME + row.v(), row.w(), row.h(), TEX_W, TEX_H);
        g.setColor(1f, 1f, 1f, 1f);

        // 6) Texto a escala 1 texel (fuente vanilla exacta), centrado en el alto de la fila.
        int interiorH = row.h() - 2 * BAR_RIM;
        int textY = row.y() + BAR_RIM + (interiorH - TEXT_H + 1) / 2;
        g.drawString(mc.font, label, PORTRAIT + TEXT_PAD_X, textY, 0xFFFFFFFF);
        String txt = ClientZenkaiHooks.barValueText(cur, max, " / ");
        g.drawString(mc.font, txt, row.x() + row.w() - row.tip() - TEXT_PAD_X - mc.font.width(txt), textY, 0xFFFFFFFF);
    }

    /** Ancho de recorte (desde el borde izquierdo de la barra, oculto incluido) para un %. */
    private static int visibleFill(Row row, float pct) {
        if (pct <= 0f) return 0;
        return BEHIND + Math.round((row.w() - BEHIND) * pct);
    }

    /**
     * Valor mostrado (suavizado igual que el minimalista) + estela: al bajar el valor real, la
     * estela se queda donde estaba TRAIL_HOLD_MS y luego se consume hacia el mostrado. Al curarse,
     * o si cambia el máximo (forma/raza), se pega al mostrado sin animar.
     */
    private static void updateState(BarState st, float target, float max, long nowMs) {
        boolean maxChanged = max != st.lastMax;
        st.disp = ClientZenkaiHooks.smoothTowards(st.disp, target, max, st.lastMax);
        st.lastMax = max;
        if (st.trail < 0f || maxChanged) st.trail = st.disp;
        if (st.prevTarget >= 0f && target < st.prevTarget - 0.001f) st.holdUntil = nowMs + TRAIL_HOLD_MS;
        st.prevTarget = target;

        float dt = st.lastMs == 0 ? 0f : Math.min(0.1f, (nowMs - st.lastMs) / 1000f);
        st.lastMs = nowMs;
        if (st.trail <= st.disp) {
            st.trail = st.disp;
        } else if (nowMs >= st.holdUntil) {
            float drain = Math.max(max * TRAIL_MIN_DRAIN, (st.trail - st.disp) * 4f) * dt;
            st.trail = Math.max(st.disp, st.trail - drain);
        }
    }

    // ── Retrato ──────────────────────────────────────────────────────────────

    /**
     * Jugador en vivo dentro del retrato. Va FUERA del pose escalado porque
     * renderEntityInInventory hace su propio scissor en coordenadas de GUI, que no pasan por el
     * pose.
     *
     * Limpia antes el búfer de PROFUNDIDAD: en el intento neón el retrato salía VACÍO. Las capas
     * vanilla del HUD (la viñeta, entre otras) se pintan con un desplazamiento de Z creciente y
     * dejan profundidad escrita en toda la pantalla; el modelo, que sí usa test de profundidad,
     * quedaba DETRÁS de ellas y no se veía. Los blits de GUI no lo notan porque no la usan.
     */
    private static void drawPortraitModel(GuiGraphics g, Minecraft mc, float f) {
        int x1 = PANEL_X + Math.round(PORTRAIT_INSET * f);
        int y1 = PANEL_Y + Math.round(PORTRAIT_INSET * f);
        int x2 = PANEL_X + Math.round((PORTRAIT - PORTRAIT_INSET) * f);
        int y2 = PANEL_Y + Math.round((PORTRAIT - PORTRAIT_INSET) * f);
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        renderingPortrait = true;
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x1, y1, x2, y2,
                    Math.max(1, Math.round(PORTRAIT_MODEL_SIZE * f)), PORTRAIT_Y_OFFSET,
                    PORTRAIT_YAW, 0f, mc.player);
        } finally {
            renderingPortrait = false;
        }
    }

    // ── Color del marco ──────────────────────────────────────────────────────

    /**
     * Prioridad: crítico (pulsa hacia rojo) > strain > stamina agotada > forma/Kaioken/overlay
     * (AuraColors, el mismo color que el aura; solo con hud.frame_follows_form) > hud.frame_color. La parte de forma se suaviza entre
     * frames; el pulso crítico va encima sin suavizar para que no se apague.
     *
     * MIENTRAS TE TRANSFORMAS (pedido del usuario, 2026-10-09): el color de forma se mezcla hacia
     * el aura de la forma DESTINO según el progreso del hold — los mismos datos que pinta el
     * anillo de TransformGaugeOverlay (currentHoldTarget + getHoldTicks). Al completarse, la forma
     * nueva ya tiene ese color, así que no hay salto; si se suelta antes, vuelve suavizado.
     */
    private static int frameRgb(Player p, PlayerFormAttachment form, KaiokenTier tier, boolean strained,
                                float bodyPct, float staminaPct) {
        int baseColor = ClientConfig.hudFrameColor();
        int formColor;
        if (!ClientConfig.hudFrameFollowsForm()) {
            formColor = baseColor;
        } else {
            boolean base = form.activeDef() == null && !tier.isOn()
                    && OverlayStateSystem.highestPriorityAuraOverlay(p) == null;
            formColor = transformingTowards(p, form, base ? baseColor : (AuraColors.resolve(p) & 0xFFFFFF));
        }
        int target = strained ? (ClientZenkaiHooks.C_BODY_STRAIN & 0xFFFFFF)
                : staminaPct <= 0.01f ? EXHAUSTED_GREY
                : formColor;

        float tr = ((target >> 16) & 0xFF) / 255f, tg = ((target >> 8) & 0xFF) / 255f, tb = (target & 0xFF) / 255f;
        if (frameR < 0f) {
            frameR = tr; frameG = tg; frameB = tb;
        } else {
            frameR += (tr - frameR) * FRAME_COLOR_RATE;
            frameG += (tg - frameG) * FRAME_COLOR_RATE;
            frameB += (tb - frameB) * FRAME_COLOR_RATE;
        }
        float r = frameR, gr = frameG, b = frameB;
        if (bodyPct > 0f && bodyPct <= ClientZenkaiHooks.BODY_CRITICAL_FRAC) {
            float t = (System.currentTimeMillis() % 900) / 900f;
            float pulse = 0.5f + 0.5f * Mth.sin(t * Mth.TWO_PI);
            int crit = ClientZenkaiHooks.C_BODY_CRITICAL;
            r = Mth.lerp(pulse, r, ((crit >> 16) & 0xFF) / 255f);
            gr = Mth.lerp(pulse, gr, ((crit >> 8) & 0xFF) / 255f);
            b = Mth.lerp(pulse, b, (crit & 0xFF) / 255f);
        }
        return (Math.round(r * 255) << 16) | (Math.round(gr * 255) << 8) | Math.round(b * 255);
    }

    /** Color de forma actual mezclado hacia el aura del destino según el progreso del hold. */
    private static int transformingTowards(Player p, PlayerFormAttachment form, int current) {
        if (!form.isTransforming()) return current;
        PlayerFormAttachment.HoldTarget ht = form.currentHoldTarget(p, PlayerStatsAttachment.get(p).getRace());
        if (ht.isEmpty() || ht.auraRgb() < 0) return current;
        float t = Mth.clamp(form.getHoldTicks() / (float) ht.holdTicks(), 0f, 1f);
        int to = ht.auraRgb();
        int r = Math.round(Mth.lerp(t, (current >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (current >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, current & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    /** Tinte de MARCO (ver FRAME_LIFT): solo para piezas grises del atlas. */
    private static void tint(GuiGraphics g, int rgb) {
        g.setColor(lift((rgb >> 16) & 0xFF), lift((rgb >> 8) & 0xFF), lift(rgb & 0xFF), 1f);
    }

    private static float lift(int channel) {
        return FRAME_LIFT + (1f - FRAME_LIFT) * channel / 255f;
    }
}
