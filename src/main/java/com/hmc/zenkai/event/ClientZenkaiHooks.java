package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import com.hmc.zenkai.feature.combat.InCombatState;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ClientZenkaiHooks {

    private ClientZenkaiHooks() {}

    // =========================
    // Icons atlas
    // =========================
    public static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");

    // =========================
    // Barras Body/Stamina/Ki (bars_empty.png / bars_full.png)
    // =========================
    // Generadas por tools/gen_bars.py (medidor de poder estilo escáner: paralelogramo inclinado
    // + punta de flecha, bordes duros) — ver ZenkaiUiCredits. Lienzo 256x64 CON alfa: cada barra
    // es un paralelogramo con esquinas transparentes (verificado a nivel de píxel), no un
    // rectángulo — cualquier overlay que se pinte encima tiene que respetar esa silueta (ver
    // drawTintedOverlay) o se sale por las esquinas inclinadas. bars_empty es el contorno/interior
    // vacío; bars_full es el mismo dibujo con el interior relleno de color sólido — el relleno
    // proporcional se hace recortando el ANCHO de origen de bars_full (igual que una barra de
    // maná/XP vanilla), así la punta derecha (el chevron) se queda vacía hasta que la barra está
    // casi llena, que es el efecto correcto sin tratarla como caso aparte.
    public static final ResourceLocation BARS_EMPTY_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/bars_empty.png");
    public static final ResourceLocation BARS_FULL_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/bars_full.png");

    // == tools/gen_bars.py (TEX_W/TEX_H/ROW_V/ROW_H): si esa geometría cambia ahí, regenerar el
    // PNG con el script y actualizar estas constantes a mano — el script no puede escribir aquí.
    private static final int BARS_TEX_W = 256;
    private static final int BARS_TEX_H = 64;
    private static final int BARS_SRC_V = 0;    // top del bloque de las 3 barras
    private static final int BARS_SRC_H = 64;   // alto del bloque completo
    private static final int BAR_ROW_H = 20;    // alto de cada banda individual
    private static final int ROW_V_BODY = 0;
    private static final int ROW_V_STAMINA = 22;
    private static final int ROW_V_KI = 44;
    // Ancho real de contenido por fila (SHEAR + BODY_W + TIP en el generador) — Body > Stamina >
    // Ki, la misma "cascada" que dibuja el contorno. drawStatBar centra el texto cur/max sobre
    // ESTE ancho (escalado), no sobre el ancho de bloque completo (igual para las 3 filas) — así
    // el número seca la misma escalera que la barra en vez de quedar alineado al mismo punto en
    // las tres filas. == tools/gen_bars.py ROW_CONTENT_W.
    private static final int ROW_CONTENT_W_BODY = 242;
    private static final int ROW_CONTENT_W_STAMINA = 224;
    private static final int ROW_CONTENT_W_KI = 206;

    private static final int C_BODY_KAIOKEN = 0xFFFF6633;  // quemando vida
    private static final int C_BODY_STRAIN  = 0xFF9966CC;  // fatiga, stats penalizadas
    /** Body crítico sin ningún otro estado activo: mismo lenguaje (lavado de color sobre el
     *  relleno) que Kaioken/strain, pero pulsando en vez de fijo para que llame la atención. */
    private static final int C_BODY_CRITICAL = 0xFFFF2A2A;
    private static final float BODY_CRITICAL_FRAC = 0.2f;

    private static final int ICONS_TEX_W = 256;
    private static final int ICONS_TEX_H = 256;

    private static final int ICON_CELL = 20;     // tamaño real de celda en el atlas
    private static final int ICON_DRAW = 20;     // tamaño al dibujar el icono
    private static final int BADGE_SIZE = 20;    // cuadrito contenedor
    private static final int BADGE_PAD = 2;

    // =========================
    // Layout HUD
    // =========================
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 10;

    /** Hueco a la izquierda del bloque de barras para la etiqueta (HP/STM/KI). */
    private static final int LABEL_GUTTER = 25;

    /** El texto de las barras (label, cur/max, PL) usaba SIEMPRE la fuente nativa de 9px, sin
     *  importar cuánto creciera hudBarsScale — el bloque se agrandaba y el texto se quedaba fijo,
     *  leyéndose cada vez más pequeño en proporción. Un primer intento ligó la escala de fuente a
     *  `barsScale * multiplicador` con un suelo de 1f (nunca por debajo de la fuente nativa) — eso
     *  es exactamente lo que rompía la alineación: con el slider bajo, la fila renderizada
     *  (rowDestH) podía quedar MÁS BAJA que esos 9px nativos, así que el suelo forzaba un texto
     *  más alto que su propia fila y `drawStatBar` lo clavaba arriba (offset 0) porque la resta
     *  daba negativa, desbordándose sobre la fila de abajo en vez de quedar centrado.
     *  computeTextScale() elimina el suelo: la fuente se deriva SIEMPRE del alto real de la fila
     *  ya escalada (rowDestH), así el texto encaja y queda centrado para cualquier valor del
     *  slider — crece o encoge exactamente igual que la textura, nunca por su cuenta. */
    private static final float TEXT_FILL_FRAC = 0.7f;

    /** Escala de fuente que hace que el texto ocupe TEXT_FILL_FRAC del alto ya escalado de una
     *  fila de barra — ver el javadoc de TEXT_FILL_FRAC para por qué ya no hay un suelo fijo. */
    private static float computeTextScale(Minecraft mc, float barsScale) {
        int rowDestH = Math.round(BAR_ROW_H * barsScale);
        return (rowDestH * TEXT_FILL_FRAC) / mc.font.lineHeight;
    }

    // =========================
    // Íconos
    // =========================
    private static final IconUV ICON_DIVINE = IconUV.grid(5, 0);
    private static final IconUV ICON_FLY = IconUV.grid(3, 0);
    private static final IconUV ICON_POTENTIAL_UNLOCKED = IconUV.grid(6,3);
    private static final IconUV ICON_IMMORTAL = IconUV.grid(11, 0);
    private static final IconUV ICON_IN_COMBAT = IconUV.grid(0, 0);
    private static final IconUV ICON_KAIOKEN = IconUV.grid(2, 2);
    private static final IconUV ICON_KI_CHARGE = IconUV.grid(0, 2);
    private static final IconUV ICON_LEGENDARY = IconUV.grid(5, 2);
    // "Majin" en el HUD es SOLO PlayerVisualAttachment.isMajinControlled (la maldición/
    // posesión, ver MajinEffect/PersistentEffectsSystem). PlayerStateFlags llegó a tener un
    // campo isMajin aparte que no representaba nada real; se eliminó para no confundir los dos.
    private static final IconUV ICON_MAJIN = IconUV.grid(4, 0);
    private static final IconUV ICON_STRAIN = IconUV.grid(10, 1);
    private static final IconUV ICON_TRANSFORMING = IconUV.grid(4, 2);
    private static final IconUV ICON_TURBO = IconUV.grid(1, 2);
    private static final IconUV ICON_MOON = IconUV.grid(6, 0);
    /** Celda del cooldown de Transmisión Instantánea — mismo ícono que
     *  InstantTransmissionCrosshairOverlay pinta sobre la mira mientras se mantiene TAB (ver
     *  drawInstantTransmissionIcon, expuesto para que la otra clase no duplique esta celda por
     *  su cuenta). */
    private static final IconUV ICON_INSTANT_TRANSMISSION = IconUV.grid(11, 1);

    // =========================
    // Relleno "vivo": el valor mostrado se desliza hacia el real en vez de saltar de golpe cada
    // frame, para que perder/ganar Body/Stamina/Ki se sienta como un drenaje, no un corte.
    // Estado por-cliente, no por-jugador: solo existe el jugador local aquí.
    // =========================
    private static float dispBody = -1f, dispStamina = -1f, dispKi = -1f;
    private static float lastBodyMax = -1f, lastStaminaMax = -1f, lastKiMax = -1f;

    /** Fracción por frame que el valor mostrado recorre hacia el real. Dependiente de framerate
     *  a propósito (es un flourish visual, no un dato de gameplay) — a 60 FPS converge en pocos
     *  frames, que es justo el efecto buscado. */
    private static final float SMOOTH_RATE = 0.25f;

    /**
     * Desliza `prevDisplayed` hacia `target`, o SALTA directo si es la primera vez, si `max`
     * cambió (cambio de forma/raza, no es un drenaje real) o si el salto es demasiado grande
     * para leerse como un drenaje continuo (login, revivir, teletransporte) — sin esto un salto
     * de 0 a full tras reconectar tardaría segundos en "rellenarse" en vez de aparecer ya lleno.
     */
    private static float smoothTowards(float prevDisplayed, float target, float max, float prevMax) {
        if (prevDisplayed < 0f || max != prevMax || Math.abs(target - prevDisplayed) > max * 0.5f) {
            return target;
        }
        float next = prevDisplayed + (target - prevDisplayed) * SMOOTH_RATE;
        return Math.abs(next - target) < 0.05f ? target : next;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // F1: ocultar HUD vanilla → también ocultar el tuyo
        if (mc.options.hideGui) return;

        PlayerFormAttachment form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        PlayerStatsAttachment stats = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!stats.isRaceChosen()) return;

        GuiGraphics g = e.getGuiGraphics();

        // ========================
        // Barras (sin recuadro de fondo detrás: el propio arte de bars_empty.png ya hace de
        // fondo de cada barra individual — un recuadro extra alrededor de las 3 solo añadía un
        // segundo marco que no aportaba nada).
        // ========================
        float barsScale = ClientConfig.hudBarsScaleFrac();
        float textScale = computeTextScale(mc, barsScale);
        int barsDestW = Math.round(BARS_TEX_W * barsScale);
        int barsDestH = Math.round(BARS_SRC_H * barsScale);

        int panelH = barsDestH;

        // Layout interno. LABEL_GUTTER se escala con textScale (no con barsScale) porque lo que
        // tiene que caber ahí es el ANCHO DEL TEXTO ("STM"), que crece con textScale — a
        // textScale > 1 un hueco fijo se quedaría corto y el label invadiría el borde izquierdo
        // de la barra (a textScale < 1 sigue siendo válido, solo encoge el hueco de más).
        int barBlockX = PANEL_X + Math.round(LABEL_GUTTER * textScale);
        int barBlockY = PANEL_Y;

        // Fondo (contorno + puntas) de las 3 barras, una sola vez.
        g.blit(BARS_EMPTY_TEX, barBlockX, barBlockY, barsDestW, barsDestH,
                0f, BARS_SRC_V, BARS_TEX_W, BARS_SRC_H, BARS_TEX_W, BARS_TEX_H);

        long now = mc.player.level().getGameTime();
        KaiokenTier tier = form.getKaioken();
        boolean strained = form.isStrained(now);
        // El relleno ya es el color propio de cada barra (rojo/verde/cian, ver tools/gen_bars.py)
        // — no se puede reteñir limpio como la silueta gris de TechniqueIcons — así que
        // Kaioken/strain/crítico se avisan con un lavado translúcido encima del relleno
        // (drawTintedFill respeta la silueta real del paralelogramo, no un rectángulo).
        float bodyMax = stats.getBodyMax();
        float bodyPctRaw = bodyMax > 0 ? Mth.clamp(stats.getBody() / bodyMax, 0f, 1f) : 0f;
        int bodyOverlay = tier.isOn() ? (0x80000000 | (C_BODY_KAIOKEN & 0xFFFFFF))
                : strained ? (0x80000000 | (C_BODY_STRAIN & 0xFFFFFF))
                : (bodyPctRaw > 0f && bodyPctRaw <= BODY_CRITICAL_FRAC) ? criticalPulseColor() : 0;

        dispBody = smoothTowards(dispBody, stats.getBody(), bodyMax, lastBodyMax);
        lastBodyMax = bodyMax;
        dispStamina = smoothTowards(dispStamina, stats.getStamina(), stats.getStaminaMax(), lastStaminaMax);
        lastStaminaMax = stats.getStaminaMax();
        dispKi = smoothTowards(dispKi, stats.getEnergy(), stats.getEnergyMax(), lastKiMax);
        lastKiMax = stats.getEnergyMax();

        // ========================
        // 1) BODY
        // ========================
        drawStatBar(g, barBlockX, barBlockY, barsDestW, ROW_CONTENT_W_BODY, barsScale, textScale, ROW_V_BODY,
                pctOf(dispBody, bodyMax), stats.getBody(), (int) bodyMax, "HP", bodyOverlay);

        // ========================
        // 2) STAMINA
        // ========================
        drawStatBar(g, barBlockX, barBlockY, barsDestW, ROW_CONTENT_W_STAMINA, barsScale, textScale, ROW_V_STAMINA,
                pctOf(dispStamina, stats.getStaminaMax()), stats.getStamina(), stats.getStaminaMax(), "STM", 0);

        // ========================
        // 3) KI
        // ========================
        drawStatBar(g, barBlockX, barBlockY, barsDestW, ROW_CONTENT_W_KI, barsScale, textScale, ROW_V_KI,
                pctOf(dispKi, stats.getEnergyMax()), stats.getEnergy(), stats.getEnergyMax(), "KI", 0);

        // El % de poder en uso ya lo enseña KiChargeGaugeOverlay (el anillo sobre la hotbar) —
        // aquí solo queda el PL, en el mismo sitio de siempre (mismo textScale que las barras).
        int plY = PANEL_Y + panelH + 4;
        drawScaledString(g, mc, "PL " + ZenkaiNumbers.format(stats.getApparentPowerLevel()),
                PANEL_X, plY, textScale, 0xFFFFE066);
        int plTextH = Math.round(mc.font.lineHeight * textScale);

        // ========================
        // CADA ICONO EN UNA SOLA LÍNEA (debajo del panel; el PL ya puede ocupar más alto que los
        // 9px nativos si textScale > 1, así que el hueco se calcula a partir de su altura real
        // en vez de un +12 fijo, para no solaparse con la fila de badges).
        // ========================
        int iconX = PANEL_X;
        int iconY = plY + plTextH + 4;

        // --- Estados "especiales" (antes de acciones, misma altura) ---
        if (form.isTransforming()) {
            drawBadge(g, iconX, iconY, ICON_TRANSFORMING);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isDivine()) {
            drawBadge(g, iconX, iconY, ICON_DIVINE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).isMajinControlled()) {
            drawBadge(g, iconX, iconY, ICON_MAJIN);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (AuraClientState.localTurbo) {
            drawBadge(g, iconX, iconY, ICON_TURBO);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // Excluyentes por construcción: el strain nace en el instante en que el kaioken se
        // apaga por agotamiento, así que nunca coinciden y comparten hueco en la fila.
        if (tier.isOn()) {
            drawBadge(g, iconX, iconY, ICON_KAIOKEN);
            drawBadgeLabel(g, iconX, iconY, tier.label(), 0xFFFF8866);
            iconX += BADGE_SIZE + BADGE_PAD;
        } else if (strained) {
            drawBadge(g, iconX, iconY, ICON_STRAIN);
            drawBadgeLabel(g, iconX, iconY, Math.round(form.strainSecondsLeft(now)) + "s", C_BODY_STRAIN);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isImmortal()) {
            drawBadge(g, iconX, iconY, ICON_IMMORTAL);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isLegendary()) {
            drawBadge(g, iconX, iconY, ICON_LEGENDARY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // Oozaru/Super Oozaru: se dispara solo por la luna (OozaruSystem), no por la rueda, así
        // que el jugador necesita un aviso propio — también mientras el gui de transformación
        // automática está en curso (oozaruForced), antes incluso de que formId cambie.
        ResourceLocation formId = form.getFormId();
        if (form.isOozaruForced() || FormIds.OOZARU.equals(formId) || FormIds.SUPER_OOZARU.equals(formId)) {
            drawBadge(g, iconX, iconY, ICON_MOON);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (InCombatState.isInCombat(mc.player)) {
            drawBadge(g, iconX, iconY, ICON_IN_COMBAT);
            drawBadgeLabel(g, iconX, iconY,
                    InCombatState.secondsLeft(mc.player) + "s", 0xFFFF8866);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // --- Acciones (misma altura) ---
        if (stats.isFlyEnabled() && SkillEffects.canFly(mc.player)) {
            drawBadge(g, iconX, iconY, ICON_FLY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isChargingKi()) {
            drawBadge(g, iconX, iconY, ICON_KI_CHARGE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (InstantTransmissionClientState.onCooldown()) {
            drawBadge(g, iconX, iconY, ICON_INSTANT_TRANSMISSION);
            int secondsLeft = (InstantTransmissionClientState.cooldownTicks() + 19) / 20;
            drawBadgeLabel(g, iconX, iconY, secondsLeft + "s", 0xFFAAAAFF);
            iconX += BADGE_SIZE + BADGE_PAD;
        }
    }

    // =========================================================
    // Helpers (Barras / Panel / Badges)
    // =========================================================

    /** cur/max -> fracción 0..1, con guarda de max<=0 (evita NaN mientras stats.getXMax() está
     *  sin inicializar en el primer frame tras elegir raza). */
    private static float pctOf(float cur, float max) {
        return max > 0f ? Mth.clamp(cur / max, 0f, 1f) : 0f;
    }

    /** Color del pulso de Body crítico: mismo ARGB base (C_BODY_CRITICAL) con el alfa
     *  respirando con el reloj real (no el tick de juego, para que sea fluido a cualquier TPS). */
    private static int criticalPulseColor() {
        float t = (System.currentTimeMillis() % 900) / 900f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2);
        int alpha = 0x50 + Math.round(pulse * 0x50); // ~0x50..0xA0
        return (alpha << 24) | (C_BODY_CRITICAL & 0xFFFFFF);
    }

    /**
     * Dibuja una fila (Body/Stamina/Ki) del bloque de barras: etiqueta a la izquierda, relleno
     * proporcional recortado de bars_full.png sobre el fondo ya pintado por el llamador, texto
     * cur/max CENTRADO dentro de la propia barra (mismo lenguaje que las referencias pedidas:
     * el número vive en medio de la barra entera, no pegado a un borde), y un lavado de color
     * opcional encima del relleno (Kaioken/strain/crítico).
     *
     * @param blockX  X del bloque de las 3 barras (ya pintado el fondo con bars_empty.png).
     * @param blockY  Y del bloque.
     * @param destW   ancho de destino del BLOQUE COMPLETO (ya escalado por hudBarsScale) — usado
     *                solo para posicionar/pintar el fondo (bars_empty.png) en el llamador; el
     *                relleno NO se recorta contra esto (ver rowContentW).
     * @param rowContentW ancho NATIVO (sin escalar) del contenido real de ESTA fila
     *                (ROW_CONTENT_W_BODY/STAMINA/KI) — es el denominador real tanto del recorte
     *                del relleno (fillSrcW/fillDestW) como del centrado del texto cur/max, así el
     *                relleno cubre 0..100% exactamente el ancho visible de ESTA fila (sin tramo
     *                muerto al final) y el número sigue la misma cascada que el contorno de la
     *                barra, en vez de medirse contra el bloque completo (igual para las 3 filas).
     * @param scale   mismo factor de escala, para pasar de píxeles de origen a píxeles de fila.
     * @param textScale escala de fuente para label/cur-max de esta fila (ver computeTextScale) —
     *                  crece con `scale` para que el texto no se quede fijo en 9px nativos
     *                  mientras la barra que lo rodea sí se agranda.
     * @param rowV    V de origen de esta fila en el atlas (ROW_V_BODY/STAMINA/KI).
     * @param fillPct fracción 0..1 a rellenar — YA suavizada (ver smoothTowards), distinta del
     *                cur/max real que se enseña como texto para que el número nunca mienta
     *                mientras la barra todavía está deslizándose hacia él.
     * @param overlayColor ARGB traslúcido a superponer sobre el relleno, o 0 para ninguno.
     */
    private static void drawStatBar(GuiGraphics g, int blockX, int blockY, int destW, int rowContentW,
            float scale, float textScale, int rowV, float fillPct, int cur, int max, String label, int overlayColor) {
        Minecraft mc = Minecraft.getInstance();
        int rowOffsetDest = Math.round((rowV - BARS_SRC_V) * scale);
        int rowDestH = Math.round(BAR_ROW_H * scale);
        int rowY = blockY + rowOffsetDest;
        int textH = Math.round(mc.font.lineHeight * textScale);
        int textY = rowY + Math.max(0, (rowDestH - textH) / 2) + 1;

        drawScaledString(g, mc, label, PANEL_X, textY, textScale, 0xFFFFFFFF);

        // Recorte medido contra el ancho REAL de contenido de esta fila (rowContentW), no contra
        // el lienzo completo (BARS_TEX_W=256): más allá de rowContentW el atlas ya es transparente
        // (relleno para que las 3 filas cascadeen en distinto ancho), así que medir contra 256
        // hacía que fillSrcW alcanzara ese límite ANTES de fillPct=1 (87.5% en Stamina, 80.5% en
        // Ki, 94.5% en Body) — la barra se veía llena entre ese % y el 100% real, sin ningún cambio
        // visual en ese tramo. Con rowContentW como denominador, 0..1 de fillPct cubre exactamente
        // el ancho visible, sin tramo muerto al final; la punta (chevron) sigue siendo la última en
        // rellenarse por sí sola, solo por ser la parte más a la derecha del contenido.
        int contentDestW = Math.round(rowContentW * scale);
        int fillSrcW = Math.round(rowContentW * fillPct);
        int fillDestW = Math.round(contentDestW * fillPct);
        if (fillSrcW > 0 && fillDestW > 0) {
            g.blit(BARS_FULL_TEX, blockX, rowY, fillDestW, rowDestH,
                    0f, (float) rowV, fillSrcW, BAR_ROW_H, BARS_TEX_W, BARS_TEX_H);
            if (overlayColor != 0) {
                // Reteñido del MISMO recorte, no un g.fill() rectangular: la barra es un
                // paralelogramo con esquinas transparentes (ver el javadoc de clase), así que un
                // rectángulo se saldría por esas esquinas. RenderSystem.setShaderColor multiplica
                // el texel (color + su propio alfa) por este tinte, así el lavado queda recortado
                // exactamente a la silueta real sin necesitar una segunda textura de máscara.
                float a = ((overlayColor >>> 24) & 0xFF) / 255f;
                float r = ((overlayColor >> 16) & 0xFF) / 255f;
                float gr = ((overlayColor >> 8) & 0xFF) / 255f;
                float b = (overlayColor & 0xFF) / 255f;
                RenderSystem.setShaderColor(r, gr, b, a);
                g.blit(BARS_FULL_TEX, blockX, rowY, fillDestW, rowDestH,
                        0f, (float) rowV, fillSrcW, BAR_ROW_H, BARS_TEX_W, BARS_TEX_H);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }

        // texto cur/max, CENTRADO sobre el ancho real de ESTA barra (rowContentW ya escalado) —
        // no sobre el bloque completo, para que el número "escalone" igual que el contorno.
        String txt = ZenkaiNumbers.format(cur) + "/" + ZenkaiNumbers.format(max);
        int textW = Math.round(mc.font.width(txt) * textScale);
        int textX = blockX + Math.round((contentDestW - textW) / 2f);
        drawScaledString(g, mc, txt, textX, textY, textScale, 0xFFFFFFFF);
    }

    /**
     * Dibuja texto escalado (label/cur-max de las barras, PL), mismo patrón que
     * ScouterOverlay.drawScaled: traslada el origen y escala el PoseStack, así el string se pinta
     * en (0,0) del espacio ya escalado y (x,y) se mantiene como su esquina superior izquierda en
     * coordenadas de pantalla reales — permite alinear texto escalado igual que uno sin escalar.
     */
    private static void drawScaledString(GuiGraphics g, Minecraft mc, String text, int x, int y,
            float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, Component.literal(text), 0, 0, color);
        g.pose().popPose();
    }

    private static void drawBadge(GuiGraphics g, int x, int y, IconUV icon) {
        g.blit(ICONS_TEX, x, y, icon.u(), icon.v(), ICON_DRAW, ICON_DRAW, ICONS_TEX_W, ICONS_TEX_H);
    }

    /** Dibuja el MISMO ícono que el badge de cooldown de esta clase (ICON_INSTANT_TRANSMISSION)
     *  — expuesto para que InstantTransmissionCrosshairOverlay pinte exactamente el mismo ícono
     *  sobre la mira mientras se mantiene TAB, sin duplicar la celda del atlas por su cuenta.
     *  Esa duplicación (dos constantes con la misma pareja u/v repetida a mano en dos clases)
     *  fue justo lo que hizo que las dos pantallas dejaran de coincidir la última vez que esta
     *  celda se movió — con este único punto de dibujo, un futuro cambio de celda solo se toca
     *  aquí y las dos vistas siguen mostrando lo mismo automáticamente. */
    public static void drawInstantTransmissionIcon(GuiGraphics g, int x, int y) {
        drawBadge(g, x, y, ICON_INSTANT_TRANSMISSION);
    }

    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) {
            return new IconUV(col * ICON_CELL, row * ICON_CELL);
        }
    }

    /** Etiqueta corta pegada al borde inferior derecho de un badge ("x20", "12s"). */
    private static void drawBadgeLabel(GuiGraphics g, int x, int y, String text, int color) {
        if (text == null || text.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.font.width(text);
        g.drawString(mc.font, text, x + BADGE_SIZE - w, y + BADGE_SIZE - 8, color, true);
    }
}