package com.hmc.zenkai.feature.teleport;

import net.minecraft.core.BlockPos;

/**
 * Un destino concreto DENTRO de una dimensión GENÉRICA (Nether, End, o cualquier dimensión de un
 * mod de terceros) — la pieza que faltaba para poder añadir varios puntos fijos a una misma
 * dimensión sin volver a los casos especiales por-enum que {@link TeleportDestination} ya dejó
 * atrás una vez (ver el javadoc de esa clase, "Ya NO incluye NETHER_PORTAL/END_SPAWN"). TRES
 * formas de resolver la posición real:
 *  - {@link Fixed} — una coordenada compartida por todo el mundo, igual que
 *    {@link TeleportAnchors} para los realms curados (p. ej. la plataforma de obsidiana del End,
 *    SIEMPRE en el mismo sitio en cualquier partida vainilla).
 *  - {@link LastEntry} — "el portal por el que entraste a esta dimensión": el mismo valor que ya
 *    usaría la dimensión con MENOS de 2 sub-destinos ({@code DimensionEntryTracker}, enganchado a
 *    {@code PlayerChangedDimensionEvent}) — congelado en el momento del cruce real, nunca
 *    reescrito por caminar/explorar después. Para cualquier dimensión SIN estructura fija propia
 *    (el Nether hoy), esto ES literalmente la posición real del portal: vainilla ya te deja justo
 *    ahí al cruzar, así que no hace falta recalcular ningún enlace de portal a mano.
 *  - {@link Waypoint} — un punto descubierto por un mecanismo QUE NO cruza de dimensión, así que
 *    {@code PlayerChangedDimensionEvent} nunca lo vería (el caso real: un End Gateway mueve al
 *    jugador dentro de la MISMA dimensión). Se resuelve leyendo
 *    {@code InstantTransmissionAttachment.getWaypoint(waypointKey)} en vez del "último cruce"
 *    genérico por dimensión — necesita su propio mecanismo de descubrimiento aparte (ver
 *    EndGatewayBlockMixin/EndOuterIslandTracker para el caso real), no algo que este record
 *    resuelva por sí solo.
 * Los tres comparten seguridad: {@code TeleportExecution.execute} pasa cualquier posición
 * resuelta por {@code TeleportUtil.findSafeSpot} antes de mover al jugador — ninguno de los tres
 * puede dejar caer al vacío o asfixiar, exactamente igual que cualquier otro teletransporte del
 * mod.
 * Ver {@link GenericDimensionDestinations} para el registro real de qué dimensión tiene cuáles.
 */
public sealed interface GenericSubDestination {

    /** Id corto, único DENTRO de la lista de su propia dimensión (no hace falta que sea único
     *  entre dimensiones distintas) — viaja en {@link GenericDimensionTeleportPacket#subId()}. */
    String id();

    /** Columna/fila dentro de icons_instant_transmision.png (ver InstantTransmissionMenuScreen.
     *  IconUV.grid) para pintar la fila de este sub-destino. La fila v=0 es la histórica de los
     *  realms/destinos curados — un sub-destino nuevo que necesite una celda propia (no una ya
     *  pintada que tenga sentido reusar) va en una fila NUEVA en vez de robarle una columna
     *  reservada a otra cosa (p. ej. la columna 10 de v=0, "NetherPortal", reservada para una
     *  futura estructura del propio Nether — no para Islas Exteriores del End). */
    int iconColumn();
    int iconRow();

    record Fixed(String id, BlockPos pos, int iconColumn, int iconRow) implements GenericSubDestination {}

    record LastEntry(String id, int iconColumn, int iconRow) implements GenericSubDestination {}

    /** {@code waypointKey} es la clave dentro de {@code InstantTransmissionAttachment.waypoints}
     *  (NO el id de este sub-destino: dos dimensiones distintas podrían compartir el mismo id de
     *  sub-destino sin colisionar, pero la clave del waypoint tiene que ser única de verdad en
     *  todo el attachment). */
    record Waypoint(String id, String waypointKey, int iconColumn, int iconRow) implements GenericSubDestination {}
}
