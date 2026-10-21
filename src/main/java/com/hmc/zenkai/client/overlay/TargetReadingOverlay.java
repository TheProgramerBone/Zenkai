package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Panel de LECTURA del objetivo fijado (Ki Sense nivel 5, «Dominio»). Es la única parte del sentido
 * que enseña cifras, y a propósito: solo del objetivo fijado y solo al nivel máximo, que es el premio
 * de dominar la habilidad. El mundo (ecos, ondas) sigue siendo cualitativo y el PL exacto sigue
 * siendo del scouter — este panel NO lo muestra: enseña la CATEGORÍA de presencia.
 *
 * Contenido: categoría de presencia (y sello de jefe), vida exacta con porcentaje, ki y stamina por
 * CUARTOS (sin cifra), estados activos y «ATENCIÓN» si el objetivo te tiene fijado. Sin nombre de la
 * entidad: el sentido percibe presencias, no identidades. Compacto, alineado a la derecha, panel
 * #151D27 con el filo dorado del dominio.
 *
 * Los datos los manda el servidor solo con nivel 5 y solo del objetivo que él mismo valida
 * (SenseKiScanPacket / SenseServerState.lockedTarget); aquí no hay gate que valga. Si el objetivo
 * deja de venir en los escaneos, su presencia caduca y el panel desaparece con ella.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TargetReadingOverlay {
    private TargetReadingOverlay() {}

    /** Ancho del panel; público porque ReadingHudLayout y la pantalla de colocación lo necesitan. */
    public static final int WIDTH = 128;
    private static final int W = WIDTH;
    private static final int PAD = 6;
    private static final int STRIPE = 2;
    private static final int BAR_H = 4;
    private static final int PIPS = 4;
    private static final int PIP_W = 12;
    private static final int PIP_GAP = 2;
    private static final int LINE = 10;
    private static final int LABEL_W = 22;

    private static final int C_BG = 0xD9000000 | SenseKiOverlayRenderer.C_INK;
    private static final int C_GOLD = 0xFF000000 | SenseKiOverlayRenderer.C_GOLD;
    private static final int C_TEXT = 0xFF000000 | SenseKiOverlayRenderer.C_CORE;
    private static final int C_CYAN = 0xFF000000 | SenseKiOverlayRenderer.C_CYAN;
    private static final int C_CORAL = 0xFF000000 | SenseKiOverlayRenderer.C_CORAL;
    private static final int C_VIOLET = 0xFF000000 | SenseKiOverlayRenderer.C_VIOLET;
    private static final int C_DIM = 0xFF9BAFB9;
    private static final int C_TRACK = 0xFF2F434E;

    /** Estados que se listan como líneas (jefe y «te fija» van aparte). */
    private static final int STATE_MASK = SenseKiDataPacket.FLAG_CHARGING | SenseKiDataPacket.FLAG_TRANSFORMING | SenseKiDataPacket.FLAG_TRANSFORMED
            | SenseKiDataPacket.FLAG_SUPPRESSING | SenseKiDataPacket.FLAG_IN_COMBAT;

    /** Alto de muestra para colocar el panel: cabecera, vida, ki, stamina y un estado. */
    public static final int SAMPLE_HEIGHT = 112;

    private static final String[] STRENGTH_KEYS = {
            "hud.zenkai.ki_sense.strength.inferior",
            "hud.zenkai.ki_sense.strength.comparable",
            "hud.zenkai.ki_sense.strength.superior",
            "hud.zenkai.ki_sense.strength.overwhelming",
    };

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (SenseKiClientState.mode() == SenseKiMode.OFF) return;
        if (SenseKiClientState.level() < 5) return;

        SenseKiDataPacket.TargetReading r = SenseKiClientState.reading();
        if (r == null || r.entityId() != LockOnClientState.targetId()) return;
        SenseKiDataPacket.Entry en = SenseKiClientState.entry(r.entityId());
        if (en == null) return; // caducó: no se enseña una lectura vieja

        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int flags = en.flags();
        boolean threat = en.has(SenseKiDataPacket.FLAG_TARGETING_YOU);

        int lines = 0;
        lines += 1;                                   // cabecera
        lines += 1;                                   // categoría
        lines += 1;                                   // vida en cifras
        int resourceLines = (r.kiTier() >= 0 ? 1 : 0) + (r.staminaTier() >= 0 ? 1 : 0);
        int stateLines = Integer.bitCount(flags & STATE_MASK);
        if (stateLines == 0) stateLines = 1;          // «ESTABLE»
        int h = PAD + lines * LINE + 2 + BAR_H + 4 + resourceLines * LINE + stateLines * LINE + (threat ? LINE : 0) + PAD - 2;

        ReadingHudLayout pos = ReadingHudLayout.current(g.guiWidth(), g.guiHeight(), h);
        int x = pos.x();
        int y = pos.y();
        g.fill(x, y, x + W, y + h, C_BG);
        g.fill(x, y, x + STRIPE, y + h, C_GOLD);

        int tx = x + STRIPE + PAD;
        int tw = W - STRIPE - PAD * 2;
        int ty = y + PAD;
        g.drawString(font, Component.translatable("hud.zenkai.ki_sense.reading"), tx, ty, C_GOLD, false);
        if (en.has(SenseKiDataPacket.FLAG_BOSS)) {
            Component boss = Component.translatable("hud.zenkai.ki_sense.boss");
            g.drawString(font, boss, tx + tw - font.width(boss), ty, C_GOLD, false);
        }
        ty += LINE;

        g.drawString(font, Component.translatable(STRENGTH_KEYS[SenseKiOverlayRenderer.strengthLabel(en.strength())]),
                tx, ty, C_TEXT, false);
        ty += LINE + 2;

        // Vida exacta: barra + porcentaje, y debajo la cifra. Se actualiza con cada escaneo (10 ticks).
        float frac = r.bodyMax() <= 0 ? 0f : Mth.clamp(r.body() / (float) r.bodyMax(), 0f, 1f);
        String pct = Math.round(frac * 100f) + "%";
        int barW = tw - font.width("100%") - 4;
        g.fill(tx, ty, tx + barW, ty + BAR_H, C_TRACK);
        int fw = Math.round(barW * frac);
        if (fw > 0) g.fill(tx, ty, tx + fw, ty + BAR_H, frac <= 0.25f ? C_CORAL : C_TEXT);
        g.drawString(font, pct, tx + tw - font.width(pct), ty - 2, C_TEXT, false);
        ty += BAR_H + 4;
        g.drawString(font, ZenkaiNumbers.format((long) r.body()) + " / " + ZenkaiNumbers.format((long) r.bodyMax()),
                tx, ty, C_DIM, false);
        ty += LINE;

        if (r.kiTier() >= 0) {
            g.drawString(font, Component.translatable("hud.zenkai.ki_sense.ki"), tx, ty, C_DIM, false);
            pips(g, tx + LABEL_W, ty + 2, r.kiTier(), C_CYAN);
            ty += LINE;
        }
        if (r.staminaTier() >= 0) {
            g.drawString(font, Component.translatable("hud.zenkai.ki_sense.stamina"), tx, ty, C_DIM, false);
            pips(g, tx + LABEL_W, ty + 2, r.staminaTier(), C_GOLD);
            ty += LINE;
        }

        ty = state(g, font, tx, ty, flags, SenseKiDataPacket.FLAG_CHARGING, "hud.zenkai.ki_sense.state.charging", C_CYAN);
        ty = state(g, font, tx, ty, flags, SenseKiDataPacket.FLAG_TRANSFORMING, "hud.zenkai.ki_sense.state.transforming", C_GOLD);
        ty = state(g, font, tx, ty, flags, SenseKiDataPacket.FLAG_TRANSFORMED, "hud.zenkai.ki_sense.state.transformed", C_GOLD);
        ty = state(g, font, tx, ty, flags, SenseKiDataPacket.FLAG_SUPPRESSING, "hud.zenkai.ki_sense.state.suppressing", C_VIOLET);
        ty = state(g, font, tx, ty, flags, SenseKiDataPacket.FLAG_IN_COMBAT, "hud.zenkai.ki_sense.state.in_combat", C_TEXT);
        if ((flags & STATE_MASK) == 0) {
            g.drawString(font, Component.translatable("hud.zenkai.ki_sense.state.stable"), tx, ty, C_DIM, false);
            ty += LINE;
        }

        // «Atención»: te tiene fijado. Presión detectada, no certeza de ataque.
        if (threat) {
            float pulse = 0.65f + 0.35f * Mth.sin((mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(true)) * 0.3f);
            int a = Mth.clamp((int) (pulse * 255f), 0, 255);
            g.drawString(font, Component.translatable("hud.zenkai.ki_sense.attention"), tx, ty,
                    (a << 24) | (C_CORAL & 0xFFFFFF), false);
        }
    }

    /** Maqueta del panel para la pantalla de colocación: misma caja y filo, contenido de muestra. */
    public static void drawSample(GuiGraphics g, Font font, int x, int y) {
        g.fill(x, y, x + W, y + SAMPLE_HEIGHT, C_BG);
        g.fill(x, y, x + STRIPE, y + SAMPLE_HEIGHT, C_GOLD);
        int tx = x + STRIPE + PAD;
        int tw = W - STRIPE - PAD * 2;
        int ty = y + PAD;
        g.drawString(font, Component.translatable("hud.zenkai.ki_sense.reading"), tx, ty, C_GOLD, false);
        ty += LINE;
        g.drawString(font, Component.translatable(STRENGTH_KEYS[2]), tx, ty, C_TEXT, false);
        ty += LINE + 2;
        g.fill(tx, ty, tx + tw, ty + BAR_H, C_TRACK);
        g.fill(tx, ty, tx + tw * 3 / 4, ty + BAR_H, C_TEXT);
        ty += BAR_H + 4;
        g.drawString(font, "750 / 1K", tx, ty, C_DIM, false);
        ty += LINE;
        g.drawString(font, Component.translatable("hud.zenkai.ki_sense.ki"), tx, ty, C_DIM, false);
        pips(g, tx + LABEL_W, ty + 2, 3, C_CYAN);
        ty += LINE;
        g.drawString(font, Component.translatable("hud.zenkai.ki_sense.stamina"), tx, ty, C_DIM, false);
        pips(g, tx + LABEL_W, ty + 2, 2, C_GOLD);
        ty += LINE;
        g.drawString(font, Component.translatable("hud.zenkai.ki_sense.state.stable"), tx, ty, C_DIM, false);
    }

    private static int state(GuiGraphics g, Font font, int x, int y, int flags, int bit, String key, int color) {
        if ((flags & bit) == 0) return y;
        g.drawString(font, Component.translatable(key), x, y, color, false);
        return y + LINE;
    }

    /** Cuatro casillas de reserva: {@code tier} llenas. Un cuarto no es una cifra, es un estado. */
    private static void pips(GuiGraphics g, int x, int y, int tier, int color) {
        for (int i = 0; i < PIPS; i++) {
            int px = x + i * (PIP_W + PIP_GAP);
            g.fill(px, y, px + PIP_W, y + 4, i < tier ? color : C_TRACK);
        }
    }
}
