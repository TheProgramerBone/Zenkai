package com.hmc.zenkai.compat;

import com.hmc.zenkai.Zenkai;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * ¿Hay un shaderpack de Iris/Oculus EN USO ahora mismo? Un shaderpack reemplaza el pipeline de
 * render entero — un {@code RenderTarget}/pase propio compitiendo con eso es la receta de un
 * conflicto de estado de GL, así que {@code KiVfxBloomPipeline} se rinde en ese caso y dibuja el
 * ki por su ruta de shaderpack.
 * <p>
 * ═══ ANTES MIRABA SOLO SI EL MOD ESTABA CARGADO (bug, 2026-09-24) ═══ La versión anterior
 * devolvía true con Iris/Oculus simplemente CARGADO, sin pack activo. En el entorno de desarrollo
 * del usuario Iris+Sodium están siempre cargados, así que el bloom NO se ejecutó en ninguna
 * prueba en juego, y el ki se dibujaba por la ruta de shaderpack con el pack apagado — un vídeo
 * entero de "núcleos blancos planos sin resplandor" salió de aquí. Ahora se pregunta a la API
 * pública de Iris ({@code net.irisshaders.iris.api.v0.IrisApi#isShaderPackInUse}), por
 * reflexión para no depender de Iris al compilar — mismo criterio que dragonminez
 * ({@code IrisCompat.isShaderPackInUse}). Si la API no se encuentra (versión rara, fork), se
 * sigue siendo conservador: cargado = en uso.
 */
public final class IrisCompat {
    private IrisCompat() {}

    private static final boolean IRIS = ModList.get().isLoaded("iris");
    // Oculus es el fork legado de Iris para versiones donde Iris aún no publicaba oficialmente;
    // expone la misma API v0.
    private static final boolean OCULUS = ModList.get().isLoaded("oculus");

    private static Object api;
    private static Method inUse;
    private static Method shadowPass;
    private static boolean resolved = false;

    private static void resolve() {
        resolved = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            inUse = c.getMethod("isShaderPackInUse");
            try {
                shadowPass = c.getMethod("isRenderingShadowPass");
            } catch (Throwable t) {
                shadowPass = null; // API sin esa función: sin guarda de sombras, como antes
            }
        } catch (Throwable t) {
            api = null;
            inUse = null;
            Zenkai.LOGGER.warn("[Zenkai] Iris/Oculus cargado pero su API no respondió ({}); se "
                    + "asume shaderpack activo.", t.toString());
        }
    }

    /**
     * true mientras Iris dibuja el mapa de SOMBRAS del shaderpack.
     * <p>
     * ═══ DUPLICADO CON SHADERPACK ═══ Iris vuelve a llamar al render de cada entidad dentro de su
     * pase de sombras, desde la cámara de la luz. Los renderers de ki no dibujan en su
     * {@code render()}: ENCOLAN en {@code KiVfxFrameQueue}, que se vacía una vez por frame. Sin esta
     * guarda la cola recibía una segunda copia de la técnica con la matriz de la luz (otra pose y
     * otra escala), y además las partículas y el {@code consumeFxTick} corrían dos veces.
     */
    public static boolean renderingShadowPass() {
        if (!IRIS && !OCULUS) return false;
        if (!resolved) resolve();
        if (shadowPass == null) return false;
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    /** true si hay un shaderpack realmente EN USO (no solo el mod cargado). */
    public static boolean shaderPackActive() {
        if (!IRIS && !OCULUS) return false;
        if (!resolved) resolve();
        if (inUse == null) return true;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            return true;
        }
    }
}
