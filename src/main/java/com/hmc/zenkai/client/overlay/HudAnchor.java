package com.hmc.zenkai.client.overlay;

/**
 * Las ocho anclas a las que puede pegarse un bloque del HUD.
 * Ocho y no cuatro porque los centros de lado son posiciones legítimas y frecuentes: la
 * columna de técnicas pegada al centro del borde derecho es la disposición clásica del género,
 * y arriba-centro es donde muchos jugadores esperan una barra de acción secundaria.
 * EL ANCLA NO ES LA POSICIÓN FINAL. Sobre ella se suma un desplazamiento libre que el jugador
 * fija arrastrando, y el ancla decide DE QUÉ BORDE cuelga ese desplazamiento. Es lo que hace
 * que la colocación sobreviva a un cambio de resolución: un bloque anclado abajo-derecha con
 * offset (-8, -8) sigue a 8 px de esa esquina tanto en 1280x720 como en 4K, mientras que
 * guardar coordenadas absolutas lo dejaría en mitad de la pantalla o fuera de ella.
 */
public enum HudAnchor {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f),
    MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    private final float fx, fy;

    HudAnchor(float fx, float fy) { this.fx = fx; this.fy = fy; }

    public float fx() { return fx; }
    public float fy() { return fy; }

    /** X del bloque pegado a este ancla, sin desplazamiento. */
    public int originX(int screenW, int blockW) {
        return Math.round((screenW - blockW) * fx);
    }

    /** Y del bloque pegado a este ancla, sin desplazamiento. */
    public int originY(int screenH, int blockH) {
        return Math.round((screenH - blockH) * fy);
    }

    public boolean isBottom() { return fy > 0.9f; }
    public boolean isTop()    { return fy < 0.1f; }
    public boolean isRight()  { return fx > 0.9f; }
    public boolean isLeft()   { return fx < 0.1f; }

    /** Clave de traducción del nombre, para la pantalla de configuración. */
    public String nameKey() { return "config.zenkai.anchor." + name().toLowerCase(); }

    /**
     * Ancla cuyo punto de referencia queda más cerca de (x, y).
     * Es lo que convierte un arrastre libre en una colocación estable: el jugador suelta el
     * bloque donde quiere, y esto decide de qué borde debe colgar para que siga ahí cuando
     * cambie de resolución. Se compara contra el punto del ancla en el ESPACIO DEL BLOQUE
     * (donde estaría su esquina superior izquierda), no contra el borde de la pantalla, o un
     * bloque ancho anclado a la derecha se leería siempre como centrado.
     */
    public static HudAnchor nearest(int x, int y, int screenW, int screenH, int blockW, int blockH) {
        HudAnchor best = TOP_LEFT;
        long bestDist = Long.MAX_VALUE;
        for (HudAnchor a : values()) {
            int ax = a.originX(screenW, blockW);
            int ay = a.originY(screenH, blockH);
            long dx = x - ax, dy = y - ay;
            long d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = a; }
        }
        return best;
    }
}