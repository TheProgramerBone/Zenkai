package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.teleport.InstantTransmissionAttachment;
import com.hmc.zenkai.feature.teleport.InstantTransmissionMenuSync;
import com.hmc.zenkai.feature.teleport.StructureAnchors;
import com.hmc.zenkai.feature.teleport.TeleportDestination;
import com.hmc.zenkai.worldgen.ProtectedZones;
import net.minecraft.server.level.ServerPlayer;

/**
 * "El jugador encontró esta estructura" + "el jugador ha estado en esta dimensión". Reusa
 * `ProtectedZones.protectorAt(...)` — la misma fachada que ya identifica en qué zona protegida
 * está un jugador para el aviso de hotbar/no-spawn hostil — en vez de inventar un segundo
 * sistema de detección. Kami's Palace llega por el backend de estructura de worldgen (tag
 * `zenkai:protected`); Yemma/Kaiosama por el backend de zona estática (`NoHostileSpawnZones`) —
 * ambos ya devuelven la MISMA cadena de protector que `TeleportDestination.byProtectorKey`
 * sabe mapear.
 * No depende del game rule de protección de estructuras (a diferencia de
 * `ProtectedZoneMessageHandler`, que si se apaga deja de correr): descubrir un destino o marcar
 * una dimensión como visitada debe funcionar aunque el servidor desactive esa protección.
 * Revisión tras la Fase 2: las posiciones de destino son FIJAS (TeleportAnchors) — este sistema
 * ya no graba coordenadas por jugador, solo booleanos — y el "planeta" en sí no aparece en el
 * selector del menú hasta que el jugador haya pisado esa dimensión al menos una vez.
 * Este tick es también el único sitio que llama a StructureAnchors.flushPending: no hace falta
 * un tick global aparte, porque cualquier jugador online ya tica aquí una vez por tick — barato
 * incluso cuando no hay nada pendiente que volcar.
 */
public final class TeleportDiscoverySystem {
    private TeleportDiscoverySystem() {}

    public static void tick(TickCtx c) {
        if (!(c.p() instanceof ServerPlayer sp)) return;
        StructureAnchors.flushPending(sp.server);
        InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);

        boolean changed = att.markDimensionVisited(sp.level().dimension());

        // Nether/End/cualquier dimensión de un mod de terceros ya NO necesitan un
        // TeleportDestination propio que "descubrir" aparte: markDimensionVisited (arriba) ya
        // es su único requisito, y su posición de llegada la graba DimensionEntryTracker (por
        // jugador, no un punto fijo compartido) — ver GenericDimensionRow en
        // InstantTransmissionMenuScreen. Solo Overworld/Otherworld conservan destinos con
        // ancla fija que de verdad hace falta "descubrir" (Kami/Yemma/Kaiosama, abajo).
        // Kami's Palace ya no necesita ningún caso especial aquí: su posición ahora sale de un
        // marcador de datos capturado en generación (StructureAnchors), no de dónde estuviera
        // parado el jugador que la descubre — descubrir solo marca el booleano, igual que
        // Yemma/Kaiosama.
        String protector = ProtectedZones.protectorAt(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ());
        if (protector != null) {
            TeleportDestination dest = TeleportDestination.byProtectorKey(protector);
            if (dest != null && att.markDiscovered(dest)) changed = true;
        }

        // Solo resincroniza cuando algo cambió de verdad — nunca en bucle cada tick.
        if (changed) InstantTransmissionMenuSync.send(sp);
    }
}
