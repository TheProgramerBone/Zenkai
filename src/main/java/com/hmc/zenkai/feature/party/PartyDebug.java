package com.hmc.zenkai.feature.party;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOLO PARA PRUEBAS. Nombres de "miembros" falsos añadidos por {@code /zenkai debug party add}
 * — un UUID aleatorio no corresponde a ningún jugador real, así que
 * {@link PartyService#resolveName} no tiene de dónde sacar un nombre legible sin esto. Existe
 * ÚNICAMENTE porque en singleplayer no hay con quién formar una party de verdad para probar
 * PartyScreen (barra de Body, botón de expulsar, etc.).
 * En memoria, nunca se persiste: si el servidor se reinicia con una party de prueba viva, sus
 * miembros falsos vuelven a mostrarse como su UUID en texto — degradación aceptable para algo
 * explícitamente temporal.
 * BORRAR es seguro en cuanto termine de probarse el menú: quitar esta clase, el bloque
 * "/zenkai debug party" de ModCommands, {@link PartyService#debugAddFakeMember} y las dos
 * líneas que lo leen en resolveName() — nada más del mod depende de esto.
 */
final class PartyDebug {
    private PartyDebug() {}

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    static void register(UUID id, String name) { NAMES.put(id, name); }

    static String nameOf(UUID id) { return NAMES.get(id); }
}
