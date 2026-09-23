package com.hmc.zenkai.compat.ponder;

import net.neoforged.fml.ModList;

/**
 * Puente opcional con Ponder — la librería de tutoriales 3D en pantalla que Create separó de su
 * jar principal desde 1.21.1 (net.createmod.ponder, licencia MIT). Sigue el mismo patrón que
 * CuriosCompat: cualquier tipo de la API de Ponder vive exclusivamente en clases que solo se
 * cargan desde el interior de Impl, nunca en la firma de un método público de esta clase. Así,
 * si alguien juega sin Ponder instalado, la JVM jamás intenta resolver esos tipos y el resto del
 * jar de Zenkai no se entera de que la dependencia falta.
 *
 * Uso: llamar a {@link #register()} una única vez desde el setup de cliente (FMLClientSetupEvent),
 * igual que hace Create en CreateClient.clientInit(...) con su propio plugin.
 */
public final class PonderCompat {
    private PonderCompat() {}

    private static final boolean LOADED = ModList.get().isLoaded("ponder");

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Registra el plugin de escenas de Zenkai en Ponder. No hace nada si el mod no está presente. */
    public static void register() {
        if (!LOADED) return;
        Impl.register();
    }

    private static final class Impl {
        static void register() {
            net.createmod.ponder.foundation.PonderIndex.addPlugin(new ZenkaiPonderPlugin());
        }
    }
}
