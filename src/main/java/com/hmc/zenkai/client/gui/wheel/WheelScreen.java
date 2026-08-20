package com.hmc.zenkai.client.gui.wheel;

import com.hmc.zenkai.client.overlay.RadialGauge;
import com.hmc.zenkai.feature.wheel.WheelSelectPacket;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Menú radial (mantener X). Screen y no overlay: se queda abierto al soltar la tecla y se
 * elige con clic, así que hacen falta cursor real, clics y Esc, que un Screen da gratis.
 * isPauseScreen() -> false: el mundo sigue corriendo, esto se usa en combate.
 * La rueda NO ejecuta transformaciones: SELECCIONA. Ver WheelMenu.
 * Los hijos de la raíz pueden ser categorías (se despliegan en el anillo exterior dentro de
 * su propio sector) u hojas sueltas (interruptores como el Kaioken, que actúan al clic).
 * Cada sector se pinta en tres pasadas: contorno negro, relleno con degradado radial
 * (oscuro dentro, claro fuera) y brillo superior al pasar el ratón. Sin eso se ve plano.
 */
public class WheelScreen extends Screen {

    // Rueda compacta y de bandas iguales: cuanto más parecidos son el hueco y el grosor de
    // cada anillo, más "redonda" se lee y menos ocupa la pantalla.
    private static final float R_HOLE = 34f;   // zona muerta central = cancelar
    private static final float R0_IN  = 36f;
    private static final float R0_OUT = 72f;
    private static final float R1_IN  = 74f;
    private static final float R1_OUT = 110f;

    private static final float HOVER_POP = 3f;   // el sector apuntado sobresale
    private static final float OUTLINE   = 1.5f; // grosor del contorno negro
    private static final float GAP_DEG   = 2.2f; // aire entre sectores: el look segmentado

    /** Contorno de sector: casi negro para que los sectores no se fundan entre sí sobre el
     *  mundo. Es estructura de la rueda, no un color de estado, pero se nombra desde la paleta
     *  para que no quede como un literal huérfano. */
    private static final int COL_OUTLINE  = ZenkaiPalette.withAlpha(0xFF000000, 0xE6);
    private static final int COL_DISABLED = 0x606068;
    /** Base oscura sobre la que se tiñe cada sector. */
    private static final int COL_BACKDROP = 0x101014;

    // En reposo casi no hay color: el sector es gris oscuro y el nombre es lo que se lee.
    // Al apuntarlo salta al color propio a tope, que es lo que da el golpe de brillo.
    private static final float TINT_IDLE  = 0.18f;
    private static final float TINT_HOVER = 0.85f;

    private static final float LABEL_SCALE = 0.72f;  // letra más pequeña
    private static final int   LABEL_WRAP  = 84;     // ancho de corte, en px ya escalados

    private final WheelNode root;

    private int hoverRoot = -1;
    private int hoverChild = -1;

    public WheelScreen(WheelNode root) {
        super(Component.translatable("wheel.zenkai.title"));
        this.root = root;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private float cx() { return this.width / 2f; }
    private float cy() { return this.height / 2f; }

    private List<WheelNode> roots() { return root.children(); }

    private float[] rootSpan(int i) {
        int n = roots().size();
        float size = 360f / n;
        return new float[]{ -90f + i * size, size };
    }

    /** false = centro o fuera del anillo: el gesto de cancelar. */
    private boolean pick(double mouseX, double mouseY) {
        float dx = (float) mouseX - cx();
        float dy = (float) mouseY - cy();
        float r = Mth.sqrt(dx * dx + dy * dy);

        if (r < R_HOLE || r > R1_OUT || roots().isEmpty()) {
            hoverChild = -1;
            return false;
        }

        float ang = (float) Math.toDegrees(Math.atan2(dy, dx)); // ⚠
        int n = roots().size();
        float size = 360f / n;
        int idx = Mth.clamp((int) (norm(ang + 90f) / size), 0, n - 1);

        if (r < R1_IN) {
            hoverRoot = idx;
            hoverChild = -1;
            return true;
        }

        if (hoverRoot != idx) { hoverChild = -1; return true; }

        List<WheelNode> kids = roots().get(idx).children();
        if (kids.isEmpty()) { hoverChild = -1; return true; }

        float[] span = rootSpan(idx);
        float kidSize = span[1] / kids.size();
        hoverChild = Mth.clamp((int) (norm(ang - span[0]) / kidSize), 0, kids.size() - 1);
        return true;
    }

    private static float norm(float deg) {
        float d = deg % 360f;
        return d < 0 ? d + 360f : d;
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) { pick(mouseX, mouseY); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pick(mouseX, mouseY)) { onClose(); return true; }

        WheelNode target = null;
        if (hoverChild >= 0 && hoverRoot >= 0) {
            target = roots().get(hoverRoot).children().get(hoverChild);
        } else if (hoverRoot >= 0) {
            WheelNode cat = roots().get(hoverRoot);
            if (cat.isLeaf()) target = cat; // interruptor suelto en la raíz
        }

        if (target != null && target.enabled() && target.isLeaf()) {
            PacketDistributor.sendToServer(
                    new WheelSelectPacket(target.kind().name(), target.value()));
            onClose();
        }
        return true;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        pick(mouseX, mouseY);

        List<WheelNode> rs = roots();
        if (rs.isEmpty()) {
            PanelText.centeredOnDark(g, this.font, Component.translatable("wheel.zenkai.empty"),
                    (int) cx(), (int) cy() - 4, ZenkaiPalette.TEXT_OFF);
            return;
        }

        for (int i = 0; i < rs.size(); i++) {
            WheelNode cat = rs.get(i);
            float[] span = rootSpan(i);
            boolean hot = (i == hoverRoot) && hoverChild < 0;

            sector(g, span[0] + GAP_DEG, span[1] - GAP_DEG * 2f,
                    R0_IN, R0_OUT, cat.color(), cat.enabled(), hot, cat.active());
            labelAt(g, cat.label(), span[0] + span[1] / 2f,
                    (R0_IN + R0_OUT) / 2f + (hot ? HOVER_POP : 0f),
                    labelColor(cat));

            if (i != hoverRoot) continue;

            List<WheelNode> kids = cat.children();
            if (kids.isEmpty()) continue;

            float kidSize = span[1] / kids.size();
            for (int k = 0; k < kids.size(); k++) {
                WheelNode kid = kids.get(k);
                float a0 = span[0] + k * kidSize;
                boolean kidHot = (k == hoverChild);

                sector(g, a0 + GAP_DEG * 0.5f, kidSize - GAP_DEG,
                        R1_IN, R1_OUT, kid.color(), kid.enabled(), kidHot, kid.active());
                labelAt(g, kid.label(), a0 + kidSize / 2f,
                        (R1_IN + R1_OUT) / 2f + (kidHot ? HOVER_POP : 0f),
                        labelColor(kid));
            }
        }
    }

    /**
     * Un sector completo: contorno negro por detrás, relleno con degradado radial y un filo
     * en el borde exterior — TENUE siempre que esté habilitado, a tope si está seleccionado.
     * El filo permanente es la diferencia con la versión vieja: un sector sin ÉL en reposo se
     * leía como un gajo de color plano pegado sobre el mundo; con él, hasta el que no tiene el
     * ratón encima insinúa el mismo bisel que ahora comparten los anillos de carga del HUD
     * (ver RadialGauge, de donde sale el primitivo de arco con degradado).
     */
    private void sector(GuiGraphics g, float start, float size, float rIn, float rOut,
                        int color, boolean enabled, boolean hovered, boolean active) {
        float pop = hovered ? HOVER_POP : 0f;
        float in = rIn, out = rOut + pop;

        // 1) Contorno: el mismo arco un poco más grande, en negro, por debajo.
        arc(g, start - 0.6f, size + 1.2f, in - OUTLINE, out + OUTLINE, COL_OUTLINE, COL_OUTLINE);

        // 2) Relleno: el color NO se pinta puro. Se mezcla sobre un fondo oscuro, poco por
        //    dentro y más por fuera. Así se lee como un menú de Minecraft (translúcido y
        //    apagado) en vez de como un tarta de colores planos, y el degradado le da bulto.
        int tone = enabled ? color : COL_DISABLED;
        int alpha = enabled ? (hovered ? 0xF2 : 0xB8) : 0x70;
        float t = hovered ? TINT_HOVER : TINT_IDLE;
        arc(g, start, size, in, out,
                RadialGauge.withAlpha(RadialGauge.mix(COL_BACKDROP, tone, t * 0.55f), alpha),
                RadialGauge.withAlpha(RadialGauge.mix(COL_BACKDROP, tone, t), alpha));

        // 3) Filo exterior: siempre presente si el sector está habilitado (apagado y estrecho
        //    en reposo, más ancho y claro al pasar el ratón, a tope de brillo si es el activo).
        if (!enabled) return;
        int rimAlpha = active ? 0xFF : (hovered ? 0xC0 : 0x70);
        float rimWidth = active ? 2f : 1.2f;
        int rimTone = active ? color : RadialGauge.mix(COL_BACKDROP, color, hovered ? 0.9f : 0.65f);
        arc(g, start, size, out - rimWidth, out,
                RadialGauge.withAlpha(rimTone, rimAlpha),
                RadialGauge.withAlpha(active ? ZenkaiPalette.TEXT : 0xFFFFFF, rimAlpha));
    }

    /** El seleccionado escribe en su propio color; el resto en blanco. Deshabilitado, apagado. */
    private static int labelColor(WheelNode n) {
        if (!n.enabled()) return ZenkaiPalette.TEXT_OFF;
        return n.active() ? RadialGauge.withAlpha(n.color(), 0xFF) : ZenkaiPalette.TEXT;
    }

    private void labelAt(GuiGraphics g, Component text, float angDeg, float radius, int color) {
        float rad = (float) Math.toRadians(angDeg);
        float x = cx() + Mth.cos(rad) * radius;
        float y = cy() + Mth.sin(rad) * radius;

        List<FormattedCharSequence> lines = this.font.split(text, LABEL_WRAP);
        int lineH = this.font.lineHeight;
        float totalH = lines.size() * lineH;

        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(LABEL_SCALE, LABEL_SCALE, 1f);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int w = this.font.width(line);
            g.drawString(this.font, line, -w / 2, (int) (-totalH / 2f + i * lineH), color, true);
        }
        g.pose().popPose();
    }

    /**
     * Sector anular relleno, con color propio para el borde interior y el exterior (de ahí
     * el degradado). Delega en RadialGauge.arc: mismo primitivo de Tesselator que usan los
     * anillos de carga del HUD, para que el Wheel y esos anillos compartan exactamente el
     * mismo trazo en vez de dos copias que puedan divergir.
     */
    private void arc(GuiGraphics g, float startDeg, float sizeDeg,
                     float rIn, float rOut, int argbIn, int argbOut) {
        RadialGauge.arc(g, cx(), cy(), rIn, rOut, startDeg, sizeDeg, argbIn, argbOut);
    }
}