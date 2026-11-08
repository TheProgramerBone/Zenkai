package com.hmc.zenkai.feature.rage;

import java.util.List;

/**
 * Lo último que el servidor mandó de la crónica del jugador local. Solo un contenedor: SIN imports de cliente, para poder
 * llamarlo desde el handler del packet (código común) sin cargar clases de cliente en un servidor dedicado.
 * {@code loaded} distingue «aún no ha llegado la respuesta» de «llegó y está vacía» (la pantalla muestra «cargando»
 * en el primer caso y el mensaje de vacío en el segundo).
 */
public final class RageChronicleClientState {
    private RageChronicleClientState() {}

    private static volatile List<RageChronicle.Entry> entries = List.of();
    private static volatile boolean loaded;

    public static void onSync(List<RageChronicle.Entry> list) {
        entries = List.copyOf(list);
        loaded = true;
    }

    public static List<RageChronicle.Entry> entries() { return entries; }

    public static boolean loaded() { return loaded; }

    /** Al abrir la pestaña y al salir del mundo: que no se vea la crónica del mundo anterior mientras llega la nueva. */
    public static void reset() {
        entries = List.of();
        loaded = false;
    }
}
