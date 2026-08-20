package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Indicador del % de poder en uso (Ki Control): un cascarón circular que se rellena según
 * powerPercent/techo, pegado sobre la hotbar.
 * Se ve en DOS situaciones, no solo una:
 *  - mientras se mantiene la tecla de cargar (isChargingKi()): visible el rato.
 *  - justo tras bajarlo de un tirón con Z (PowerPercentPacket): un flash corto, porque ese
 *    gesto es instantáneo y sin esto no daría NINGÚN feedback visual — antes lo daba el
 *    mensaje de action bar que este anillo sustituye.
 * El cambio se detecta comparando el powerPercent de este frame contra el último visto: no
 * hace falta que el servidor avise de nada aparte, el sync de fin de tick ya trae el valor.
 * SUSTITUYE al aviso de action bar que mandaban KiChargeSystem y PowerPercentPacket (un texto
 * que aparecía y desaparecía una vez por segundo): un anillo que crece EN EL MISMO SITIO
 * el rato se lee de reojo sin perseguir un texto que parpadea, y no compite con otros mensajes
 * de action bar (kaioken, daño, etc.).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiChargeGaugeOverlay {
    private KiChargeGaugeOverlay() {}

    private static final float R_OUT = 14f;
    private static final float R_IN  = 10f;

    /** Aire entre el anillo y lo que tenga debajo (hotbar/armadura, o la barra de técnicas de
     *  modo combate si está en medio). */
    private static final int GAP = 3;

    /** Cuánto se queda visible tras un cambio puntual (tecla Z) sin estar cargando. */
    private static final int FLASH_TICKS = 30; // 1.5 s

    private static final int C_BG   = 0x66000000;
    private static final int C_FILL = 0xFF33A0FF;   // mismo azul que la barra de KI del panel

    private static int lastSeenPercent = Integer.MIN_VALUE;
    private static long lastChangeTick = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        if (!att.isRaceChosen()) return;

        int pct = att.getPowerPercent();
        long now = mc.player.level().getGameTime();
        if (pct != lastSeenPercent) {
            lastSeenPercent = pct;
            lastChangeTick = now;
        }

        boolean charging = att.isChargingKi();
        boolean flashing = (now - lastChangeTick) < FLASH_TICKS;
        if (!charging && !flashing) return;

        GuiGraphics g = e.getGuiGraphics();
        float cx = g.guiWidth() / 2f;
        float cy = g.guiHeight() - TechniqueHudLayout.vanillaBottomReserve() - GAP - R_OUT;

        // La barra de técnicas del modo combate puede estar anclada justo ahí debajo: si
        // solapa con el hueco del anillo, subirlo más para no comerle el sitio (y viceversa,
        // que no la tape a ella).
        if (CombatModeClientState.isActive()) {
            TechniqueHudLayout bar = TechniqueHudLayout.current(g.guiWidth(), g.guiHeight());
            boolean overlapsX = cx + R_OUT > bar.x() && cx - R_OUT < bar.x() + bar.width();
            boolean overlapsY = cy + R_OUT > bar.y() && cy - R_OUT < bar.y() + bar.height();
            if (overlapsX && overlapsY) cy = bar.y() - GAP - R_OUT;
        }

        int max = Math.max(1, SkillEffects.maxPowerPercent(mc.player));
        float progress = pct / (float) max;

        RadialGauge.ring(g, cx, cy, R_IN, R_OUT, progress, C_BG, C_FILL);

        // Encima del anillo y no dentro: el número compite con el propio relleno del gauge
        // cuando va por la mitad (el texto blanco se pierde sobre el arco claro).
        g.drawCenteredString(mc.font, Component.literal(pct + "%"),
                Math.round(cx), Math.round(cy - R_OUT) - mc.font.lineHeight - 2, 0xFFFFFFFF);
    }
}
