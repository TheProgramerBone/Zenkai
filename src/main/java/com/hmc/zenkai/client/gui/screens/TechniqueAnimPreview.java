package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.event.ZenkaiPalAnimations;
import net.minecraft.client.Minecraft;

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
    private boolean lastDefensive;

    /** Un tick del bucle. Llamar desde Screen.tick(). */
    public void tick(int animSet, boolean defensive) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;

        // Cambiar de set o de tipo reinicia el ciclo: al pulsar la flecha de animación se ve
        // la nueva en el acto en vez de esperar a que termine la anterior.
        if (animSet != lastSet || defensive != lastDefensive) {
            lastSet = animSet;
            lastDefensive = defensive;
            t = -1;
        }
        t++;

        // Las defensivas tienen una animación única, sin par carga/disparo: se lanza al
        // comenzar la instancia y se deja. Mismo contrato que visual == 0 en ClientZenkaiPalTick.
        if (defensive) {
            if (t == 0) ZenkaiPalAnimations.playPreviewBarrier(p);
            return;
        }

        int phase = t % CYCLE;
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
    }
}