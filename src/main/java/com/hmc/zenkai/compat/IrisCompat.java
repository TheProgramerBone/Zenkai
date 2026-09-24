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
    private static boolean resolved = false;

    private static void resolve() {
        resolved = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            inUse = c.getMethod("isShaderPackInUse");
        } catch (Throwable t) {
            api = null;
            inUse = null;
            Zenkai.LOGGER.warn("[Zenkai] Iris/Oculus cargado pero su API no respondió ({}); se "
                    + "asume shaderpack activo.", t.toString());
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
