package com.hmc.zenkai.feature.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Registro en vivo de dimensiones bloqueadas para Transmisión Instantánea, cargado por
 * InstantTransmissionBlocklistManager desde datapack
 * (data/&lt;ns&gt;/zenkai_instant_transmission_blocklist/*.json) y resincronizado al cliente vía
 * InstantTransmissionBlocklistSyncPacket. Espeja el patrón volatile Set + replaceAll de
 * AuraSignatureRegistry, no un EnumMap hardcodeado, porque ESTE sí recarga.
 *
 * Una dimensión bloqueada simplemente NO aparece en el menú de planetas (ni como realm curado
 * ni como fila genérica) — pedido explícito del usuario: "la dimensión no aparece en el menú y
 * ya está", sin mensaje de bloqueo ni fila atenuada, al contrario que "nivel insuficiente" o "no
 * descubierta todavía", que SÍ se enseñan bloqueadas con tooltip. El servidor revalida esto
 * también en TeleportRequestPacket/GenericDimensionTeleportPacket (defensa en profundidad: un
 * cliente modificado no puede saltarse el bloqueo solo porque el datapack no llegó a tiempo o
 * porque manipuló la lista sincronizada).
 */
public final class InstantTransmissionBlocklist {
    private InstantTransmissionBlocklist() {}

    private static volatile Set<ResourceLocation> BLOCKED = Set.of();

    public static void replaceAll(Set<ResourceLocation> blocked) {
        BLOCKED = Set.copyOf(blocked);
    }

    public static boolean isBlocked(ResourceKey<Level> dimension) {
        return BLOCKED.contains(dimension.location());
    }

    public static boolean isBlocked(ResourceLocation dimension) {
        return BLOCKED.contains(dimension);
    }

    public static Set<ResourceLocation> all() { return BLOCKED; }
}
