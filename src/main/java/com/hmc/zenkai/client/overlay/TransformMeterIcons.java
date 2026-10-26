package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.ArcosianPowerSystem;
import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Medidores de transformación de la HUD: el icono dentro de su marco y el CONTORNO del marco es el medidor.
 * <ul>
 *   <li>Ojo (Ultra Instinto): lo que QUEDA de concentración (1 - calor). Arranca lleno y baja con el tiempo y con cada
 *       esquive; el contorno pulsa cuando queda poco. Sale siempre que la skill esté activada (o la forma puesta), aunque
 *       el medidor esté lleno o vacío: es también un aviso visual de que se está usando.</li>
 *   <li>Llama (Ultra Ego): daño acumulado. Arranca vacío y sube con el daño recibido; pulsa al llenarse.</li>
 *   <li>Rayo (Power Points del arcosiano): el tanque, SIEMPRE visible para el arcosiano (la carga está activa desde el
 *       inicio). Se carga en las formas bajas y se gasta al aplicarlo; pulsa cuando queda poco mientras se está gastando.
 *       En la HUD solo se ve el porcentaje; la cantidad exacta está en el tooltip del nodo en Transformaciones.</li>
 * </ul>
 * Pueden salir a la vez (un arcosiano puede llevar PP y UI o UE): van en una sola fila, una celda a continuación de la otra
 * con los BORDES COINCIDENTES (comparten el contorno). Fila propia DEBAJO del PL, en los dos estilos de HUD.
 *
 * Arte: {@code textures/gui/hud/meter_frames.png}, generado por tools/gen_transform_meter_frames.py; las constantes de
 * geometría de aquí son == a las de ese script. Los iconos son los de la fila v=140 de icons.png (0 ojo, 1 llama, 2 rayo).
 * Se dibuja en las unidades del pose actual, así que sirve igual a escala 1 (minimalista) que escalado (moderno).
 */
public final class TransformMeterIcons {
    private TransformMeterIcons() {}

    private static final ResourceLocation FRAMES =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/hud/meter_frames.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");

    // == gen_transform_meter_frames.py: teselas de 30 px cada 32; cada medidor tiene la suya APAGADA y la ENCENDIDA
    private static final int TEX_W = 192, TEX_H = 32;
    public static final int TILE = 30;
    private static final int PAD = 4;
    private static final int OUTLINE = 1;
    // Perímetro recorrido: mitad derecha de arriba, lado derecho, abajo, lado izquierdo y mitad izquierda de arriba.
    private static final int HALF = TILE / 2;
    private static final int PERIMETER = 4 * TILE;

    /** Paso entre celdas contiguas: el contorno de 1 px se comparte (los bordes coinciden). */
    private static final int STEP = TILE - OUTLINE;

    /** Un medidor: celda de icono (fila v=140 de icons.png) y teselas apagada/encendida de meter_frames.png. */
    private enum Kind {
        UI(0, 0, 32), UE(1, 96, 64), PP(2, 160, 128);

        final int icon, uDim, uLit;

        Kind(int icon, int uDim, int uLit) {
            this.icon = icon;
            this.uDim = uDim;
            this.uLit = uLit;
        }
    }

    // == gen_transform_meter_icons.py: celdas de 20 px en la fila v=140
    private static final int ICON_V = 140, ICON_CELL = 20;

    /** UE: a partir de este llenado el contorno pulsa (poder al máximo). */
    private static final double SATURATED = 0.9;
    /** UI y PP: por debajo de esta fracción de lo que QUEDA el contorno pulsa. */
    private static final double LOW = 0.25;

    /** Un medidor visible ahora mismo: su tipo y su llenado (0..1; en UI y PP es lo que queda). */
    private record Meter(Kind kind, double frac, boolean alertWhenLow, boolean inactive) {}

    private static List<Meter> meters(Minecraft mc) {
        List<Meter> out = new ArrayList<>(3);
        if (mc.player == null) return out;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        ResourceLocation fid = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();

        boolean ui = OverlayMetersSystem.isUiForm(fid)
                || (SkillEffects.level(mc.player, SkillEffects.ULTRA_INSTINCT) > 0
                        && SkillToggles.isOn(mc.player, SkillEffects.ULTRA_INSTINCT))
                || att.getUiHeat() > 0.5;
        boolean ue = OverlayMetersSystem.isUeForm(fid)
                || (SkillEffects.level(mc.player, SkillEffects.ULTRA_EGO) > 0
                        && SkillToggles.isOn(mc.player, SkillEffects.ULTRA_EGO))
                || att.getUeCharge() > 0.5;
        if (ui) out.add(new Meter(Kind.UI, 1.0 - att.getUiHeat() / 100.0, true, false));
        if (ue) out.add(new Meter(Kind.UE, att.getUeCharge() / 100.0, false, false));

        if (att.getRace() == Race.ARCOSIAN) {
            double max = ArcosianPowerSystem.ppMax(mc.player);
            double pp = att.getArcosianPp();
            if (max > 0.0) {
                // Siempre visible para el arcosiano con tanque (la carga está activa desde el inicio), aunque esté vacío.
                // Con poco tanque solo avisa mientras SE ESTÁ GASTANDO; cargándose en las formas bajas lo bajo es lo normal.
                boolean draining = ArcosianPowerSystem.draining(att, fid);
                // Ni carga (poder entre 50 y 100 %, o en una forma alta) ni se gasta (poder ≥ 100 % en una forma que no los
                // aprovecha): el rayo se oscurece para avisar de que el tanque está parado.
                boolean idle = !draining && !ArcosianPowerSystem.charging(att, fid);
                out.add(new Meter(Kind.PP, pp / max, draining, idle));
            }
        }
        return out;
    }

    /** Cuántos medidores salen ahora (la HUD centra la fila solo cuando es uno). */
    public static int count(Minecraft mc) {
        return meters(mc).size();
    }

    /** Ancho de la fila (0 si no hay celdas): lo usa la HUD para colocarla bajo la placa del PL. */
    public static int rowWidth(Minecraft mc) {
        int n = count(mc);
        return n == 0 ? 0 : TILE + (n - 1) * STEP;
    }

    /**
     * Dibuja la fila con la esquina superior izquierda en (x, y); las celdas van contiguas, con el contorno compartido.
     * @return alto ocupado (0 si no hay nada que enseñar)
     */
    public static int render(GuiGraphics g, Minecraft mc, int x, int y) {
        List<Meter> list = meters(mc);
        int cx = x;
        for (Meter m : list) {
            cell(g, mc, cx, y, m);
            cx += STEP;
        }
        return list.isEmpty() ? 0 : TILE;
    }

    private static void cell(GuiGraphics g, Minecraft mc, int x, int y, Meter m) {
        double frac = Math.max(0.0, Math.min(1.0, m.frac()));
        Kind k = m.kind();
        RenderSystem.enableBlend();
        // 1) marco apagado completo (contorno, anillo tenue e interior)
        g.blit(FRAMES, x, y, k.uDim, 0, TILE, TILE, TEX_W, TEX_H);
        // 2) icono con 1 px de aire por lado dentro del interior de 22 px
        if (m.inactive()) g.setColor(0.32f, 0.32f, 0.38f, 1f);   // tanque parado: icono oscurecido
        g.blit(ICONS, x + PAD + 1, y + PAD + 1, 20, 20, k.icon * ICON_CELL, ICON_V, ICON_CELL, ICON_CELL, 256, 256);
        g.setColor(1f, 1f, 1f, 1f);
        // 3) anillo encendido, por tramos hasta el avance actual
        // Aviso: UI/PP con poco que queda, UE al máximo. El anillo entero respira detrás del avance real.
        boolean alert = k == Kind.UE ? frac >= SATURATED : (m.alertWhenLow() && frac <= LOW);
        float pulse = mc.player == null ? 1f : (float) Math.abs(Math.sin(mc.player.tickCount * 0.25));
        if (alert) {
            g.setColor(1f, 1f, 1f, 0.18f + 0.30f * pulse);
            band(g, x, y, k.uLit, HALF, 0, HALF, PAD);
            band(g, x, y, k.uLit, TILE - PAD, 0, PAD, TILE);
            band(g, x, y, k.uLit, 0, TILE - PAD, TILE, PAD);
            band(g, x, y, k.uLit, 0, 0, PAD, TILE);
            band(g, x, y, k.uLit, 0, 0, HALF, PAD);
            g.setColor(1f, 1f, 1f, 1f);
        }
        if (frac <= 0.0) return;
        // Pulso: el encendido respira sobre el apagado, sin llegar a apagarse del todo.
        if (alert) g.setColor(1f, 1f, 1f, 0.55f + 0.45f * pulse);
        int u = k.uLit;
        int p = (int) Math.round(PERIMETER * frac);
        int len = Math.min(p, HALF);                                    // arriba, de centro a derecha
        band(g, x, y, u, HALF, 0, len, PAD);
        p -= HALF;
        if (p > 0) { len = Math.min(p, TILE); band(g, x, y, u, TILE - PAD, 0, PAD, len); p -= TILE; }   // derecha, hacia abajo
        if (p > 0) { len = Math.min(p, TILE); band(g, x, y, u, TILE - len, TILE - PAD, len, PAD); p -= TILE; } // abajo, hacia la izquierda
        if (p > 0) { len = Math.min(p, TILE); band(g, x, y, u, 0, TILE - len, PAD, len); p -= TILE; }   // izquierda, hacia arriba
        if (p > 0) { len = Math.min(p, HALF); band(g, x, y, u, 0, 0, len, PAD); }                      // arriba, de izquierda a centro
        g.setColor(1f, 1f, 1f, 1f);

        // 4) punta brillante en el borde de avance, solo mientras no está lleno
        if (frac < 1.0) tip(g, x, y, (int) Math.round(PERIMETER * frac));
    }

    /** Porción {@code w x h} del anillo encendido que empieza en (ru, rv) dentro de la tesela. */
    private static void band(GuiGraphics g, int x, int y, int uTile, int ru, int rv, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.blit(FRAMES, x + ru, y + rv, uTile + ru, rv, w, h, TEX_W, TEX_H);
    }

    /** Destello de 2 px en el punto del perímetro {@code p}, sobre las dos filas exteriores del anillo. */
    private static void tip(GuiGraphics g, int x, int y, int p) {
        final int c = 0x70FFFFFF;
        int t0 = OUTLINE, t1 = OUTLINE + 2;
        if (p <= HALF) {
            int px = HALF + p;
            g.fill(x + px - 2, y + t0, x + px, y + t1, c);
        } else if (p <= HALF + TILE) {
            int py = p - HALF;
            g.fill(x + TILE - t1, y + py - 2, x + TILE - t0, y + py, c);
        } else if (p <= HALF + 2 * TILE) {
            int px = TILE - (p - HALF - TILE);
            g.fill(x + px, y + TILE - t1, x + px + 2, y + TILE - t0, c);
        } else if (p <= HALF + 3 * TILE) {
            int py = TILE - (p - HALF - 2 * TILE);
            g.fill(x + t0, y + py, x + t1, y + py + 2, c);
        } else {
            int px = p - (HALF + 3 * TILE);
            g.fill(x + px, y + t0, x + px + 2, y + t1, c);
        }
    }
}
