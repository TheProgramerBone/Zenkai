package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.event.ZenkaiPalAnimations;
import com.hmc.zenkai.feature.technique.TechniqueAnimOverride;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Ciclo de animación del preview del editor de técnicas: carga → sobrecarga → disparo → pausa,
 * en bucle, sobre el modelo del jugador que pinta la pantalla.
 * VA POR PREVIEW_LAYER, NO POR KI_LAYER
 * ------------------------------------
 * Esa capa tiene FirstPersonMode.NONE, así que la animación no entra en la pasada de 1ª
 * persona: el preview NO aparece en las manos del jugador y sí en el modelo del recuadro,
 * que no es una pasada FP. Antes se disparaba sobre KI_LAYER y se colaba en las manos.
 * No se conmuta el modo de KI_LAYER al abrir y cerrar la pantalla a propósito: si el cierre
 * no llega (cambio de dimensión con la GUI abierta, fallo de render), la capa real se queda
 * en NONE y las técnicas de ki dejan de verse en 1ª persona hasta reiniciar. Con capa propia
 * no hay nada que restaurar.
 * ESTRICTAMENTE LOCAL: no manda ningún paquete y no toca ActionState, así que no pelea con
 * ClientZenkaiPalTick — tickKiAnim reacciona a cambios del estado sincronizado, y con el
 * editor abierto ese estado es NONE y se queda quieto.
 * Los tiempos son de PRESENTACIÓN, no los de la técnica. Un big blast tarda 4 s en cargar y
 * un bucle de preview de 4 s no deja ver nada; aquí lo que importa es que las tres poses se
 * distingan.
 */
public final class TechniqueAnimPreview {

    private static final int CHARGE_TICKS     = 30;
    private static final int OVERCHARGE_TICKS = 20;
    private static final int RELEASE_TICKS    = 20;
    private static final int PAUSE_TICKS      = 15;

    private static final int T_OVERCHARGE = CHARGE_TICKS;
    private static final int T_RELEASE    = T_OVERCHARGE + OVERCHARGE_TICKS;
    private static final int T_PAUSE      = T_RELEASE + RELEASE_TICKS;
    private static final int CYCLE        = T_PAUSE + PAUSE_TICKS;

    private int t = -1;
    private int lastSet = Integer.MIN_VALUE;
    @Nullable private TechniqueAnimOverride lastOverride;

    /**
     * Un tick del bucle. Llamar desde Screen.tick().
     * Recibe la ANULACIÓN y no un booleano de "es defensiva": hay tres casos, no dos. La
     * barrera es clip único, la explosión tiene las tres fases igual que un set normal, y el
     * resto usa el set que eligió el jugador. Con el booleano, la explosión —que no es
     * defensiva— caía por la rama normal y el recuadro enseñaba el set 1.
     */
    public void tick(int animSet, @Nullable TechniqueAnimOverride override) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;

        // Cambiar de set o de tipo reinicia el ciclo: al pulsar la flecha de animación se ve
        // la nueva en el acto en vez de esperar a que termine la anterior.
        if (animSet != lastSet || override != lastOverride) {
            lastSet = animSet;
            lastOverride = override;
            t = -1;
        }
        t++;

        // La barrera tiene animación única, sin par carga/disparo: se lanza al comenzar la
        // instancia y se deja.
        if (override == TechniqueAnimOverride.BARRIER) {
            if (t == 0) ZenkaiPalAnimations.playPreviewBarrier(p);
            return;
        }

        int phase = t % CYCLE;
        if (override != null) {
            if (phase == 0)                 ZenkaiPalAnimations.playPreviewOverrideCharge(p, override);
            else if (phase == T_OVERCHARGE) ZenkaiPalAnimations.playPreviewOverrideOvercharge(p, override);
            else if (phase == T_RELEASE)    ZenkaiPalAnimations.playPreviewOverrideRelease(p, override);
            else if (phase == T_PAUSE)      ZenkaiPalAnimations.stopPreview(p);
            return;
        }

        if (phase == 0)                 ZenkaiPalAnimations.playPreviewCharge(p, animSet);
        else if (phase == T_OVERCHARGE) ZenkaiPalAnimations.playPreviewOvercharge(p, animSet);
        else if (phase == T_RELEASE)    ZenkaiPalAnimations.playPreviewRelease(p, animSet);
        else if (phase == T_PAUSE)      ZenkaiPalAnimations.stopPreview(p);
    }

    /** Al cerrar la pantalla. Sin esto el jugador se queda con la pose de carga puesta. */
    public void stop() {
        var p = Minecraft.getInstance().player;
        if (p != null) ZenkaiPalAnimations.stopPreview(p);
        t = -1;
        lastSet = Integer.MIN_VALUE;
        lastOverride = null;
    }
}