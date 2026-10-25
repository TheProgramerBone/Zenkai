package com.hmc.zenkai.client.gui.screens;

/**
 * Pestañas del menú Zenkai (StatsScreen como shell). Cada pestaña abre su pantalla desde
 * ZenkaiMenuScreen; solo STORY sigue sin contenido y muestra "Próximamente" (ComingSoonScreen).
 */
public enum ZenkaiTab {
    STATS(0,20),
    SKILLS(160,0),
    /** Fusiona lo que antes eran KI_TECHNIQUES(40,20)/PHYSICAL_TECHNIQUES(120,20) en un solo
     *  botón — abre {@link TechniquesScreen}, la pantalla única de las tres familias (ki,
     *  físicas y de maestro), que las reparte con su propio selector de categoría en vez de con
     *  un hub aparte. Reusa EXACTAMENTE el ícono/coordenada del botón "Técnicas" de
     *  MasterScreen en vez de uno de los dos viejos: es el mismo concepto. Las celdas 40,20 y
     *  120,20 siguen en uso como los íconos de categoría de esa pantalla. */
    TECHNIQUES(0,80),
    /** Hub de minijuegos que dan TP (sombra/meditación/puntería) — ver TrainingHubScreen. Misma
     *  fila v=20 que el resto de íconos de pestaña (0/20/40/80/100/120/160/200 ya ocupadas) —
     *  220 es la siguiente celda libre a la derecha. */
    TRAINING(220,20),
    /** Skill tree de transformaciones (0.6.0), ver SkillTreeScreen. Icono propio en la celda
     *  140,40 (tools/gen_transformations_tab_icon.py). Ocupa el hueco de MASTERY, que dejó de
     *  ser pestaña: la maestría de formas se ve en el panel de cada nodo y la de técnicas en
     *  la categoría Mastery de TechniquesScreen (con el icono del cerebro, 160,20). */
    TRANSFORMATIONS(140,40),
    STORY(20,20),
    PARTY(80,20),
    /** CREDITS (120,80) dejó de ser pestaña en la 0.6.0: su icono es ahora un botón en una
     *  esquina de ClientConfigScreen que abre CreditsScreen ("oculta pero accesible"). */
    CONFIG(100,20);

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