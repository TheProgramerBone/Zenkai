package com.hmc.zenkai.client.gui.screens;

/**
 * Pestañas del menú Zenkai (StatsScreen como shell). En v1.0 solo PRINCIPAL y HABILIDADES
 * tienen contenido; el resto muestra "Próximamente" (los pasos 4-5 del release las llenan).
 */
public enum ZenkaiTab {
    STATS(0,20),
    SKILLS(160,0),
    KI_TECHNIQUES(40,20),
    PHYSICAL_TECHNIQUES(120,20),
    MASTERY(160,20),
    STORY(20,20),
    PARTY(80,20),
    CONFIG(100,20),
    /** Celda candidata: fila v=80 tiene 0/20/40/60/80/100 ya ocupadas o reservadas
     *  (ver gen_master_icons.py / gen_appearance_icons.py) — 120 es la siguiente libre.
     *  Reconfirmar por muestreo de píxeles antes de generar el PNG real (tools/gen_credits_icon.py). */
    CREDITS(120,80);

    /** Esquina del ícono de la pestaña dentro de textures/gui/icons.png (256x256). */
    public final int u, v;

    ZenkaiTab(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public String titleKey() {
        return "screen.zenkai.tab." + name().toLowerCase();
    }
}