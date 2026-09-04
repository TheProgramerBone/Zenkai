package com.hmc.zenkai.compat;

import net.neoforged.fml.ModList;

/**
 * Detección de presencia (NO API bridge, a diferencia de CuriosCompat) de un shaderpack tipo
 * Iris/Oculus. Un shaderpack ya reemplaza/engancha el pipeline de post-proceso de Minecraft
 * entero — un {@code RenderTarget}/{@code PostChain} propio compitiendo con eso es la receta de
 * un conflicto de estado de GL o un simple glitch visual, así que KiBloomPipeline se rinde
 * automáticamente aquí en vez de arriesgarlo, sin importar el toggle de ClientConfig.
 * <p>
 * Postura CONSERVADORA a propósito: esto apaga el bloom con Iris/Oculus simplemente CARGADO,
 * sea cual sea el shaderpack activo (incluido "None"), no solo cuando hay uno realmente
 * reemplazando el pipeline. Más cauto de lo estrictamente necesario, pedido explícitamente así
 * ("mejor rendirse automáticamente ahí") en vez de intentar leer el estado dinámico real de
 * Iris, que exigiría un puente de verdad a su API (mismo patrón que CuriosCompat.Impl) por un
 * beneficio marginal.
 */
public final class IrisCompat {
    private IrisCompat() {}

    private static final boolean IRIS = ModList.get().isLoaded("iris");
    // Oculus es el fork legado de Iris para versiones donde Iris aún no publicaba oficialmente;
    // se comprueba igual por si el usuario tiene ese en vez de Iris.
    private static final boolean OCULUS = ModList.get().isLoaded("oculus");

    /** true si CUALQUIERA de los dos está cargado, sin mirar si hay un pack activo. */
    public static boolean shaderPackActive() { return IRIS || OCULUS; }
}
