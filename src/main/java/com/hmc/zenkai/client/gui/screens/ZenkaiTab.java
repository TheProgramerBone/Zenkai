package com.hmc.zenkai.client.gui.screens;

/**
 * Pestañas del menú Zenkai (StatsScreen como shell). En v1.0 solo PRINCIPAL y HABILIDADES
 * tienen contenido; el resto muestra "Próximamente" (los pasos 4-5 del release las llenan).
 */
public enum ZenkaiTab {
    MAIN(0,20),
    SKILLS(160,0),
    KI_TECHNIQUES(40,20),
    PHYSICAL_TECHNIQUES(0,20),
    STORY(20,20),
    PARTY(80,20),
    CONFIG(100,20);

    /** Esquina del ícono de la pestaña dentro de textures/gui/icons.png (270x270). */
    public final int u, v;

    ZenkaiTab(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public String titleKey() {
        return "screen.zenkai.tab." + name().toLowerCase();
    }
}