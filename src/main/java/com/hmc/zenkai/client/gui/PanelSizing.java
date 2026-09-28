package com.hmc.zenkai.client.gui;

import net.minecraft.client.Minecraft;

/**
 * Matemática de tamaño de panel, sin textura ni caso particular: la comparte CommonPanel
 * (common_screen.png, panel beige) y cualquier otra pantalla con su propio fondo/tamaño ideal
 * (MasterScreen, InstantTransmissionMenuScreen, GalacticMenuScreen).
 *
 * Dos intentos previos y por qué no bastaban (2026-09-27):
 *   1) Un suelo mínimo que GANABA por encima del espacio disponible (`Math.max(MIN_SIZE, ...)`)
 *      — con GUI Scale alto, si de verdad solo había 90px, la función devolvía igualmente 160,
 *      MÁS GRANDE que el hueco real.
 *   2) Encoger/centrar usando directamente `this.width`/`this.height` sin ese suelo roto seguía
 *      sin arreglar el problema de fondo: esas unidades YA vienen deformadas por el GUI Scale del
 *      jugador. Con GUI Scale alto hay MENOS unidades GUI en total, así que un panel de 256
 *      unidades ocupa proporcionalmente MÁS pantalla real de la que ocupaba a GUI Scale 2 (el
 *      que se usó para diseñar/probar el conjunto de la GUI del mod) — el título (fuera del
 *      panel, encima) y la fila de pestañas (fuera, debajo) se quedaban sin sitio pasara lo que
 *      pasara con el encogido. Esto arreglaba el MARCO, pero no el contenido de dentro (texto,
 *      barras, filas): esas coordenadas siguen asumiendo un lienzo fijo a GUI Scale 2 y no
 *      derivan de este tamaño, así que se desalineaban del marco ya encogido.
 *
 * 2026-09-27 (mismo día, arreglo definitivo): la solución real no vive aquí, vive en
 * {@link ZenkaiGuiScaleLock}, que bloquea el GUI Scale REAL del juego a 2 mientras una pantalla
 * propia está abierta — así el contenido nunca se desalinea del marco porque nunca hace falta
 * encogerlo. `fitAxis` se queda como red de seguridad para una ventana realmente diminuta (donde
 * ni siquiera GUI Scale 2 cabría), no como el mecanismo principal: mientras el bloqueo esté
 * activo, `lockToReferenceScale` es un no-op (la escala real ya es 2) y solo actúa el clamp
 * final contra `total`.
 */
public final class PanelSizing {
    private PanelSizing() {}

    public record Axis(int size, int pos) {}

    /** GUI Scale con el que se diseñó y probó el conjunto de la GUI del mod. */
    private static final double REFERENCE_SCALE = 2.0;

    /** Nunca crece más allá de `ideal`; nunca pide más de lo que hay disponible. */
    public static int fit(int ideal, int available) {
        return Math.max(1, Math.min(ideal, available));
    }

    /** Cuántas unidades GUI hacen falta AHORA para que `idealAtReference` ocupe los mismos
     *  píxeles reales de pantalla que ocuparía a GUI Scale 2 (ver el javadoc de la clase). */
    private static int lockToReferenceScale(int idealAtReference) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (scale <= 0) return idealAtReference;
        return Math.max(1, (int) Math.round(idealAtReference * REFERENCE_SCALE / scale));
    }

    /**
     * Tamaño + posición de un eje. `idealAtReference` es el tamaño en unidades GUI que asumen
     * los layouts de contenido A GUI SCALE 2 — se convierte primero a cuántas unidades GUI hacen
     * falta AHORA para verse igual de grande en pantalla real (lockToReferenceScale), y solo
     * DESPUÉS se encoge si ni siquiera eso cabe en `total` (red de seguridad, ver el javadoc de
     * la clase). Además reserva `startReserve`/`endReserve` px a cada lado (el título dibujado
     * ENCIMA del panel, la fila de pestañas dibujada DEBAJO, etc.) antes de repartir lo que sobra
     * centrado entre ambos.
     */
    public static Axis fitAxis(int idealAtReference, int total, int startReserve, int endReserve) {
        int locked = lockToReferenceScale(idealAtReference);
        int available = Math.max(1, total - startReserve - endReserve);
        int size = fit(locked, available);
        int pos = startReserve + (available - size) / 2;
        return new Axis(size, pos);
    }
}
