package com.hmc.zenkai.feature.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Un destino concreto dentro del menú de la Fase 2. {@code protectorKey} es la MISMA cadena
 * que ya devuelve {@code ProtectedZones.protectorAt(...)} para esa estructura
 * ("protector.zenkai.kami_palace"/".yemma"/".kaiosama") — reusar ese identificador evita
 * inventar un segundo sistema de "dónde está cada estructura": el descubrimiento
 * (TeleportDiscoverySystem) solo tiene que preguntarle a ProtectedZones en qué zona está el
 * jugador y mapear la respuesta aquí.
 * HOME es la excepción: no es una estructura del mundo, es el punto de reaparición del propio
 * jugador — no necesita descubrimiento (protectorKey null, requiresDiscovery false, siempre
 * disponible desde el nivel 3). KORIN_TOWER es la OTRA excepción a protectorKey no-null: SÍ
 * necesita descubrimiento (requiresDiscovery true) pero su protectorKey es null porque comparte
 * estructura física con KAMI_PALACE — ver el comentario junto a su declaración más abajo.
 * Ya NO incluye NETHER_PORTAL/END_SPAWN: al generalizar el sistema a cualquier dimensión (ver
 * GenericDimensionRow/DimensionEntryTracker), esos dos casos vainilla-específicos se fusionaron
 * en el mismo mecanismo genérico que cubre Nether/End y cualquier dimensión de un mod de
 * terceros — ya no necesitan una entrada propia aquí. Ver
 * .claude/pendiente/instant-transmission-pendiente.md para el porqué del cambio.
 */
public enum TeleportDestination {
    HOME(TeleportRealm.OVERWORLD, null, false),
    KAMI_PALACE(TeleportRealm.OVERWORLD, "protector.zenkai.kami_palace", true),
    // KORIN_TOWER va A PROPÓSITO justo después de KAMI_PALACE (no al final): su columna en
    // icons_instant_transmision.png (5 + ordinal, ver InstantTransmissionMenuScreen.destIcon) ya
    // está pintada a mano en ese hueco exacto — insertarlo en cualquier otra posición del enum
    // correría el resto de columnas y desalinearía TODOS los iconos posteriores (Yemma/Kaiosama)
    // contra el atlas real. protectorKey=null porque comparte la MISMA caja de estructura/
    // protector que Kami's Palace (es la misma pieza de worldgen, torre + mirador) — byProtectorKey
    // nunca podría distinguir "estás en la base" de "estás arriba" aunque le diéramos la misma
    // cadena, así que no lo intenta: el descubrimiento se concede en TeleportDiscoverySystem al
    // mismo tiempo que KAMI_PALACE, ver el comentario de ese archivo.
    KORIN_TOWER(TeleportRealm.OVERWORLD, null, true),
    YEMMA_PALACE(TeleportRealm.OTHERWORLD, "protector.zenkai.yemma", true),
    KAIOSAMA_PLANET(TeleportRealm.OTHERWORLD, "protector.zenkai.kaiosama", true);

    private final TeleportRealm realm;
    private final String protectorKey;
    private final boolean requiresDiscovery;

    TeleportDestination(TeleportRealm realm, String protectorKey, boolean requiresDiscovery) {
        this.realm = realm;
        this.protectorKey = protectorKey;
        this.requiresDiscovery = requiresDiscovery;
    }

    public TeleportRealm realm() { return realm; }
    public boolean requiresDiscovery() { return requiresDiscovery; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public String nameKey() { return "screen.zenkai.instant_transmission.dest." + id(); }

    /** ¿Sabemos ejecutar ESTE destino, estando el jugador en {@code currentDim} y con
     *  {@code crossDimensionUnlocked} = {@code SkillEffects.instantTransmissionCrossDimensionUnlocked}?
     *  Tres casos, de más a menos permisivo:
     *  1. El realm es OVERWORLD — Home/Kami's Palace SIEMPRE cruzan de vuelta al Overworld desde
     *     cualquier dimensión (igual que OtherworldManager.revive()), sin gate de nivel extra por
     *     ser un "regreso". Esto NUNCA ha estado bloqueado, ni antes de esta función existir.
     *  2. El jugador YA está en la dimensión de ese realm — moverse entre destinos DENTRO de la
     *     misma dimensión (p. ej. Yemma -> Kaiosama estando en el Otherworld) no es un salto real
     *     entre dimensiones, así que tampoco necesita el nivel de cross-dimension.
     *  3. Cualquier otro caso es un salto de IDA de verdad (p. ej. Overworld -> Yemma, o
     *     Overworld -> Nether) — requiere {@code crossDimensionUnlocked} (nivel 6+). Como los
     *     destinos de este caso siguen exigiendo requiresDiscovery()=true, el jugador YA tuvo que
     *     llegar allí a pie/por otro medio al menos una vez antes de que el menú pueda ofrecerlo
     *     como destino — el nivel 6 solo evita tener que repetir ese viaje, nunca sustituye el
     *     primero.
     *  El PORQUÉ de recibir currentDim/crossDimensionUnlocked como parámetros en vez de leerlos
     *  aquí: este enum no conoce al jugador (SkillEffects sí, pero solo sirve del lado que tiene
     *  un Player real) — servidor (TeleportRequestPacket) y cliente
     *  (InstantTransmissionMenuScreen.lockStateOf) resuelven los dos valores por su cuenta y se
     *  los pasan, así la MISMA regla vive en un solo sitio para pintar la fila y para decidir de
     *  verdad — nunca dos copias que puedan desincronizarse. */
    public boolean executableThisPhase(ResourceKey<Level> currentDim, boolean crossDimensionUnlocked) {
        if (realm == TeleportRealm.OVERWORLD) return true;
        if (realm.dimension().equals(currentDim)) return true;
        return crossDimensionUnlocked;
    }

    /** El destino cuyo protector coincide, o null si ninguno (posición fuera de cualquier
     *  estructura rastreada). */
    public static TeleportDestination byProtectorKey(String protector) {
        if (protector == null) return null;
        for (TeleportDestination d : values()) {
            if (protector.equals(d.protectorKey)) return d;
        }
        return null;
    }

    public static TeleportDestination byId(String id) {
        for (TeleportDestination d : values()) {
            if (d.id().equals(id)) return d;
        }
        return null;
    }
}
