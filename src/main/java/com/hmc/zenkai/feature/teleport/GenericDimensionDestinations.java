package com.hmc.zenkai.feature.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * Registro de {@link GenericSubDestination} por dimensión GENÉRICA (Nether/End/mod de terceros).
 * Una dimensión SIN entrada aquí (la inmensa mayoría — cualquier dimensión de un mod de terceros
 * hoy, y el propio Nether por ahora) sigue con el comportamiento de siempre: un único destino
 * implícito ("tu última llegada ahí"), fila de un solo tap, sin submenú — ver
 * {@code InstantTransmissionMenuScreen.paintGenericRow}. Una dimensión que SÍ aparece aquí con
 * 2+ entradas abre el submenú de nivel 2, mismo patrón visual que ya usan los realms curados
 * (Overworld/Otherworld) para sus propios destinos — pedido explícito del usuario: "que se abra
 * el submenú... para poder añadir nuevos tps" (antes de esta ronda, una dimensión genérica SOLO
 * podía tener un destino, sin forma de añadir más sin reinventar el mecanismo).
 *
 * El End es el primer caso real: la plataforma de obsidiana de salida ({@link #END_MAIN_ISLAND})
 * es una coordenada FIJA — vainilla la genera SIEMPRE en el mismo sitio en cualquier partida, la
 * primera vez que alguien entra por un portal del End — así que no hace falta descubrimiento por
 * marcador ni por jugador, solo la protección de siempre de TeleportUtil.findSafeSpot contra el
 * caso raro de que algo la haya alterado. "Islas exteriores" es un {@link GenericSubDestination.
 * Waypoint} (ver EndGatewayBlockMixin/EndOuterIslandTracker) — NO un {@link
 * GenericSubDestination.LastEntry}: un End Gateway no cruza de dimensión (the_end -&gt; the_end),
 * así que el {@code PlayerChangedDimensionEvent} del que depende LastEntry nunca lo vería —
 * necesita su propio mecanismo de descubrimiento, congelado en el momento real de usar un
 * Gateway (nunca "donde estás ahora", que se pisaría solo con caminar de vuelta al spawn).
 *
 * ── ORDEN de la lista: el destino "por donde entró el jugador" SIEMPRE va primero ───────────
 * Pedido explícito del usuario: "hipotéticamente hayan 2 destinos en el nether, el primero debe
 * ser el portal porque es por donde el jugador entró". No es "el {@link GenericSubDestination.
 * LastEntry} siempre primero" a ciegas — es un criterio SEMÁNTICO (¿cuál de las entradas
 * representa la llegada real del jugador a esta dimensión?), y el tipo de dato es solo un
 * detalle de implementación de esa entrada concreta:
 *  - El End YA cumple esto: {@link #END_MAIN_ISLAND} está primero porque es literalmente donde
 *    vainilla deja al jugador al cruzar un portal del End — pasa a ser {@link
 *    GenericSubDestination.Fixed} en vez de {@link GenericSubDestination.LastEntry} solo porque
 *    esa llegada NUNCA varía de jugador a jugador (a diferencia del Nether), no porque el orden
 *    sea distinto.
 *  - Una futura estructura del Nether (columna 10 de v=0, reservada — ver
 *    tools/gen_end_outer_islands_icon.py) SÍ necesitará un {@link GenericSubDestination.LastEntry}
 *    para "el portal" (varía por jugador/mundo, se resuelve con el mismo
 *    {@code DimensionEntryTracker} de siempre) — ESE debe declararse primero en la lista de esa
 *    dimensión, con la estructura fija después, para no invertir el criterio de arriba.
 * Esta es también la razón por la que el comportamiento implícito de "menos de 2 entradas" (un
 * solo tap, sin submenú) ya usa {@code lastEntry(...)} — el mismo destino que ocuparía la
 * posición 0 de una lista futura, así que añadir una segunda entrada nunca reordena lo que el
 * jugador ya conocía como comportamiento por defecto.
 */
public final class GenericDimensionDestinations {
    private GenericDimensionDestinations() {}

    /** Plataforma de obsidiana de salida del End — vainilla la coloca siempre en esta misma
     *  posición (centro X=100, Z=0) en cualquier mundo, la primera vez que se entra por un
     *  portal del End. Y=49 es donde vainilla deja de pie al jugador sobre ella; findSafeSpot
     *  (llamado por TeleportExecution.execute para CUALQUIER destino, no solo este) corrige el
     *  aterrizaje si alguna generación concreta difiere en uno o dos bloques. */
    public static final BlockPos END_MAIN_ISLAND = new BlockPos(100, 49, 0);

    /** Clave dentro de InstantTransmissionAttachment.waypoints para "isla exterior" — compartida
     *  entre este registro (para construir el Waypoint de abajo) y EndOuterIslandTracker (quien
     *  de verdad la escribe). Vive aquí, no en el tracker, porque este archivo ya es "la fuente
     *  de la verdad" de qué claves usa cada dimensión. */
    public static final String END_OUTER_ISLAND_WAYPOINT = "end_outer_island";

    private static final Map<ResourceLocation, List<GenericSubDestination>> BY_DIMENSION = Map.of(
            Level.END.location(), List.of(
                    // Columna 11, fila v=0 ("EndPortal" en el atlas real) — plataforma/roca
                    // sólida, encaja mejor con "isla principal" que con un portal.
                    new GenericSubDestination.Fixed("main_island", END_MAIN_ISLAND, 11, 0),
                    // Columna 0, fila v=1 (v=20px) — celda NUEVA, pintada aparte por
                    // tools/gen_end_outer_islands_icon.py (remolino cian, un End Gateway). NO
                    // columna 10 de v=0 ("NetherPortal"): esa está reservada a propósito para
                    // una futura estructura del propio Nether, pedido explícito del usuario —
                    // "el del nether portal... déjalo para el portal del nether de la dimensión
                    // del nether... si te dije en otro hueco es que sea uno nuevo en la
                    // siguiente fila".
                    new GenericSubDestination.Waypoint("outer_islands", END_OUTER_ISLAND_WAYPOINT, 0, 1)
            )
    );

    /** Sub-destinos definidos para `dim`, o lista vacía si no tiene ninguno propio (cae al
     *  comportamiento implícito de un solo tap, ver el javadoc de clase). */
    public static List<GenericSubDestination> of(ResourceLocation dim) {
        return BY_DIMENSION.getOrDefault(dim, List.of());
    }

    /** El sub-destino `subId` dentro de `dim`, o null si no existe (dimensión sin entradas, o id
     *  desconocido — un cliente modificado no puede inventarse uno). */
    public static GenericSubDestination byId(ResourceLocation dim, String subId) {
        for (GenericSubDestination sub : of(dim)) {
            if (sub.id().equals(subId)) return sub;
        }
        return null;
    }
}
