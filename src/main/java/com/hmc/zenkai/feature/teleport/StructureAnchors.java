package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.worldgen.ZenkaiWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro GENÉRICO de puntos de ancla nombrados, capturados desde marcadores de datos
 * (Structure Block en modo Data) dentro de CUALQUIER estructura de segmentos del mod
 * ({@code SegmentPiece.handleDataMarker} llama aquí para las 4 — Kami/Otherworld/Kaiosama/HTC —
 * aunque hoy solo Kami lo necesita, ver {@link TeleportAnchors}). Reemplaza la fórmula anterior
 * "el punto es donde sea que estuviera parado el primer jugador que la descubrió" (podía caer en
 * el aire, dentro de una pared o enterrado) por un punto elegido A MANO por quien construye la
 * estructura.
 *
 * ── CÓMO AÑADIR O MOVER UN PUNTO DE ANCLA (sin tocar Java) ─────────────────────────────────
 * 1. En el mundo, coloca un Structure Block normal en el sitio EXACTO donde quieres el punto
 *    (suelo sólido, con hueco libre encima para que el jugador no quede atascado).
 * 2. Cambia su modo a "Data" (el botón del propio bloque — NO "Save"/"Load"/"Corner").
 * 3. En el campo de texto del bloque escribe el nombre del ancla — ese texto ES la clave, tal
 *    cual, sensible a mayúsculas/espacios. Usa uno de la lista de abajo, o inventa uno nuevo si
 *    es un punto que todavía no consume ningún código.
 * 4. Vuelve a guardar la PIEZA (el .nbt de ese segmento, p. ej. kami_1) con un Structure Block
 *    en modo "Save" normal — el marcador Data viaja incluido dentro del NBT exportado, no hace
 *    falta nada más.
 * 5. La próxima vez que esa pieza se genere en un chunk NUEVO, el punto se fija solo, sin tocar
 *    ningún archivo Java. Un mundo donde la estructura YA se generó antes de añadir el marcador
 *    NO lo recibe retroactivamente — hace falta terreno nuevo (o borrar/regenerar ese chunk).
 * 6. Léelo con {@link #get(MinecraftServer, String)}.
 *
 * Nombres en uso hoy (ver {@link TeleportAnchors} para quién consume cada uno):
 *   - {@code "kami_lookout"} — Kami's Lookout, la cima de la torre. Ancla de
 *     {@code TeleportDestination.KAMI_PALACE}.
 *   - {@code "korins_tower"} — Korin's Tower. Capturado y persistido igual que el de arriba,
 *     pero sin destino propio en {@link TeleportAnchors}/{@code TeleportDestination} todavía —
 *     añadir ese destino es un paso aparte (nuevo valor de enum + fila de menú), no algo que
 *     este registro necesite saber.
 *
 * ── POR QUÉ NO SE ESCRIBE DIRECTO A {@link ZenkaiWorldData} DESDE handleDataMarker ──────────
 * {@code postProcess} (y por tanto {@code handleDataMarker}) corre durante la fase FEATURES de
 * generación de chunk, en el pool de hilos de worldgen — NO en el hilo principal del servidor.
 * {@code ZenkaiWorldData} es un {@code SavedData} con {@code HashMap}/{@code HashSet} planos,
 * sin sincronizar — no es segura para escribirse desde ahí a la vez que el hilo principal pueda
 * leerla/escribirla (un comando, una teletransportación, el autoguardado). Por eso esta clase
 * separa CAPTURA ({@link #capture}, hilo de worldgen, solo un {@code ConcurrentHashMap} en
 * memoria, sin tocar disco) de PERSISTENCIA ({@link #flushPending}, hilo principal, llamado cada
 * tick desde {@code TeleportDiscoverySystem} — barato, casi siempre no hay nada pendiente que
 * volcar).
 */
public final class StructureAnchors {
    private StructureAnchors() {}

    private static final Map<String, BlockPos> PENDING = new ConcurrentHashMap<>();

    /** Prefijo de clave dentro de {@code ZenkaiWorldData}, para no chocar con otras claves
     *  sueltas que vivan en el mismo mapa de posiciones (p. ej. una clave vieja de una fase
     *  anterior de este mismo sistema). */
    private static final String KEY_PREFIX = "anchor:";

    /** Llamado desde {@code SegmentPiece.handleDataMarker} (hilo de worldgen). Solo memoriza —
     *  no toca disco ni SavedData todavía. Si el mismo nombre aparece más de una vez (dos
     *  marcadores iguales por error, o un reintento de worldgen sobre el mismo chunk) gana el
     *  primero que llega. */
    public static void capture(String name, BlockPos pos) {
        if (name == null || name.isBlank()) return;
        PENDING.putIfAbsent(name, pos.immutable());
    }

    /** Vuelca a {@code ZenkaiWorldData} (persistente) cualquier ancla capturada que aún no lo
     *  estuviera. Seguro y barato de llamar cada tick del hilo principal: normalmente PENDING
     *  está vacío y esto es solo un {@code isEmpty()}. */
    public static void flushPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        ZenkaiWorldData data = ZenkaiWorldData.get(server);
        PENDING.entrySet().removeIf(e -> {
            String key = KEY_PREFIX + e.getKey();
            if (data.getPos(key) == null) data.setPos(key, e.getValue());
            return true;
        });
    }

    /** Posición del ancla {@code name}, o null si esa estructura todavía no se ha generado (o
     *  se generó antes de que el marcador existiera — ver el paso 5 de la guía de arriba). */
    public static BlockPos get(MinecraftServer server, String name) {
        return ZenkaiWorldData.get(server).getPos(KEY_PREFIX + name);
    }
}
