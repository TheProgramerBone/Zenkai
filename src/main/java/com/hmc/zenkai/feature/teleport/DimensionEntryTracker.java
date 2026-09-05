package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Graba la posición del ÚLTIMO cruce de dimensión REAL de cada jugador a cualquier dimensión
 * GENÉRICA (cualquiera que no sea Overworld/Otherworld, las dos únicas con anclas fijas
 * compartidas propias — ver TeleportAnchors) — antes era {@code NetherPortalTracker}, un caso
 * especial solo para el Nether; generalizado a CUALQUIER dimensión de CUALQUIER mod, pedido
 * explícito del usuario ("que el NetherPortalTracker se pueda hacer de manera universal para
 * cada dimensión modeada").
 *
 * Pedido explícito del usuario, tras probarlo: para el Nether (y cualquier dimensión sin
 * estructura propia) esto debe ser "la posición REAL del portal", no "donde caminaste después" —
 * así que se engancha a {@code PlayerChangedDimensionEvent} (un cruce GENUINO: portal vainilla,
 * comando, otro mod) en vez de mirar la posición cada tick. Vainilla ya deja al jugador justo
 * junto a su portal real al cruzar (el mismo enlace 1:8 Overworld/Nether que usa el juego), así
 * que capturar ESE instante ya es "la posición real del portal" sin tener que recalcular ningún
 * enlace por nuestra cuenta — y se queda CONGELADA ahí hasta el siguiente cruce real, sin
 * pisarse sola por explorar lejos después.
 *
 * SOLO cuenta como "llegada" un cruce GENUINO — nunca uno disparado por nuestro propio
 * TeleportExecution. {@code ServerPlayer.teleportTo(ServerLevel, ...)} SÍ pasa por
 * {@code changeDimension} (y por tanto dispara este mismo evento) en cuanto cruza de dimensión,
 * así que sin este filtro cualquier salto de Instant Transmission a otra dimensión sobrescribiría
 * esta posición con su propio punto de llegada. Ver {@link #suppressNextEntry} y
 * TeleportExecution.execute, su única llamante.
 *
 * Para el End en concreto, este mecanismo NO cubre "islas exteriores" — un End Gateway mueve al
 * jugador DENTRO de la misma dimensión (the_end -&gt; the_end) y por tanto nunca dispara este
 * evento. Ver EndOuterIslandTracker/EndGatewayBlockMixin para ese caso, que usa su propio
 * almacén (InstantTransmissionAttachment.waypoints) en vez de este.
 *
 * Sin destino previo (nunca ha entrado a esa dimensión en esta partida), `getLastEntryPos`
 * devuelve null — ver InstantTransmissionMenuScreen.GenericDimensionRow, que ya trata eso como
 * "todavía sin punto de entrada registrado" en vez de crashear.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DimensionEntryTracker {
    private DimensionEntryTracker() {}

    /** UUIDs cuyo PRÓXIMO PlayerChangedDimensionEvent no debe grabarse — consumido (removido) en
     *  cuanto se lee, nunca queda una marca "colgada": TeleportExecution.execute la pone
     *  JUSTO antes de sp.teleportTo(...), que dispara este evento de forma SINCRÓNICA dentro de
     *  esa misma llamada cuando cruza de dimensión, así que siempre hay como mucho una entrada
     *  pendiente por jugador y se consume en la misma orden en que se puso. */
    private static final Set<UUID> SUPPRESSED = new HashSet<>();

    /** Ver el javadoc de clase. Llamar SOLO cuando se sabe que el teletransporte que sigue va a
     *  cruzar de dimensión de verdad (mismo criterio que TeleportExecution ya calcula para otras
     *  cosas) — marcarlo para un salto que se queda en el mismo nivel no hace daño por sí solo
     *  (el evento nunca llegaría a consumirlo), pero dejaría la marca viva hasta el PRÓXIMO
     *  cruce real de ese jugador, suprimiendo por error una llegada genuina. */
    public static void suppressNextEntry(ServerPlayer sp) {
        SUPPRESSED.add(sp.getUUID());
    }

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (SUPPRESSED.remove(sp.getUUID())) return; // salto de Instant Transmission, no una llegada real

        // Overworld/Otherworld tienen su propia ancla fija compartida (TeleportAnchors) — no
        // hace falta ni tiene sentido trackear "última llegada" por jugador para ellas.
        if (Level.OVERWORLD.equals(e.getTo()) || ModDimensions.OTHERWORLD_LEVEL.equals(e.getTo())) return;

        InstantTransmissionAttachment.get(sp).setLastEntryPos(e.getTo(), sp.blockPosition());
        // La posición nunca se sincroniza al cliente (se resuelve enteramente en servidor, ver
        // GenericDimensionTeleportPacket.handle) — solo el booleano "¿ha visitado esta
        // dimensión?" importa ahí, y eso ya lo marca TeleportDiscoverySystem por su cuenta cada
        // tick, así que este tracker no necesita mandar ningún packet de resync.
    }
}
