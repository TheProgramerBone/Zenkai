package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Graba la posición de la ÚLTIMA llegada de cada jugador a cualquier dimensión GENÉRICA
 * (cualquiera que no sea Overworld/Otherworld, las dos únicas con anclas fijas compartidas
 * propias — ver TeleportAnchors) — antes era {@code NetherPortalTracker}, un caso especial
 * solo para el Nether; generalizado a CUALQUIER dimensión de CUALQUIER mod, pedido explícito
 * del usuario ("que el NetherPortalTracker se pueda hacer de manera universal para cada
 * dimensión modeada"). Para una dimensión sin ancla fija razonable (el Nether, el End, o
 * cualquier dimensión de un mod de terceros), "donde te dejó tu última llegada" es la única
 * posición que tiene sentido — inherentemente distinta para cada jugador, así que vive POR
 * JUGADOR en InstantTransmissionAttachment.lastEntryPos.
 *
 * Se engancha a {@code PlayerChangedDimensionEvent} en vez de mirar la posición del jugador cada
 * tick (que registraría "donde estabas la última vez que estuviste ahí", no necesariamente cerca
 * de ningún portal — p. ej. tras caminar lejos hacia una fortaleza): el evento se dispara UNA vez
 * por cruce de dimensión, justo cuando `player.position()` ya refleja el punto de llegada real,
 * sea por portal vainilla o por cualquier otro medio (comando, otro mod) que use
 * `ServerPlayer.changeDimension` — todos disparan el mismo evento.
 *
 * Sin destino previo (nunca ha entrado a esa dimensión en esta partida), `getLastEntryPos`
 * devuelve null — ver InstantTransmissionMenuScreen.GenericDimensionRow, que ya trata eso como
 * "todavía sin punto de entrada registrado" en vez de crashear.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DimensionEntryTracker {
    private DimensionEntryTracker() {}

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
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
