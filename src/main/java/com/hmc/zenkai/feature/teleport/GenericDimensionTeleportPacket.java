package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.InstantTransmissionSystem;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador eligió una fila de dimensión GENÉRICA en el menú de planetas (Nether, End, o
 * cualquier dimensión de un mod de terceros — ver GenericDimensionRow en
 * InstantTransmissionMenuScreen). Hermano de TeleportRequestPacket, pero para destinos que NO
 * son un TeleportDestination del enum curado: identificado por el ResourceLocation crudo de la
 * dimensión en vez de un id fijo — así funciona igual sin importar qué mod registre la dimensión.
 * {@code subId} elige QUÉ punto dentro de esa dimensión (ver {@link GenericDimensionDestinations}):
 * cadena vacía es el comportamiento de siempre y el único que existía antes de esta ronda —
 * "tu última llegada ahí" (InstantTransmissionAttachment.lastEntryPos, ver DimensionEntryTracker)
 * — para cualquier dimensión sin sub-destinos propios definidos, o como atajo cuando el cliente
 * ya sabe que solo hay una fila (menos de dos sub-destinos, sin submenú, ver
 * InstantTransmissionMenuScreen.clickRealms). Un `subId` no vacío busca esa entrada concreta en
 * {@link GenericDimensionDestinations#byId}.
 * El servidor revalida TODO de nuevo (visitada, punto grabado, nivel de cross-dimension) — un
 * cliente modificado no puede pedir una dimensión que nunca visitó, un subId inexistente, ni
 * saltarse el nivel mínimo.
 */
public record GenericDimensionTeleportPacket(String dimensionId, String subId) implements CustomPacketPayload {

    /** Atajo para el comportamiento de siempre (sin submenú): "tu última llegada ahí". */
    public static GenericDimensionTeleportPacket lastEntry(String dimensionId) {
        return new GenericDimensionTeleportPacket(dimensionId, "");
    }

    public static final Type<GenericDimensionTeleportPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "generic_dimension_teleport"));

    // dimensionId: 128, no 32/64 — un ResourceLocation "namespace:path" de un mod de terceros no
    // tiene un límite corto garantizado (a diferencia de un id fijo del propio mod) — sizear el
    // cap para el peor caso razonable, no el caso común, mismo criterio que ya documenta
    // CLAUDE.md sobre este mismo tipo de límite (writeUtf/readUtf revienta la conexión si el
    // valor real lo supera, no solo rechaza el packet). subId: 32 basta de sobra — son ids
    // cortos definidos por este mismo mod dentro de GenericDimensionDestinations ("main_island",
    // "outer_islands"), nunca texto libre ni un fallback de UUID como el que rompió
    // PartySyncPacket (ver CLAUDE.md).
    public static final StreamCodec<FriendlyByteBuf, GenericDimensionTeleportPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeUtf(pkt.dimensionId(), 128); buf.writeUtf(pkt.subId(), 32); },
                    buf -> new GenericDimensionTeleportPacket(buf.readUtf(128), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GenericDimensionTeleportPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!SkillEffects.instantTransmissionMenuUnlocked(sp)) return;

            ResourceLocation loc = ResourceLocation.tryParse(pkt.dimensionId());
            if (loc == null) return;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);

            // Overworld/Otherworld no son "genéricas" — tienen su propio flujo curado
            // (TeleportRequestPacket, con destinos y anclas fijas). Un cliente que mande esto
            // para ellas de todos modos no hace nada.
            if (Level.OVERWORLD.equals(dimKey) || ModDimensions.OTHERWORLD_LEVEL.equals(dimKey)) return;

            // Dimensión bloqueada por datapack (ver InstantTransmissionBlocklist): la fila ya no
            // debería ni aparecer en el menú de un cliente sin modificar, pero se revalida aquí
            // igual — defensa en profundidad, mismo criterio que TeleportRequestPacket.
            if (InstantTransmissionBlocklist.isBlocked(dimKey)) return;

            InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
            if (!att.hasVisitedDimension(dimKey)) return; // nunca ha estado ahí

            BlockPos target = resolveTarget(att, dimKey, loc, pkt.subId());
            if (target == null) return; // visitada, pero sin punto que resolver todavía

            boolean crossOk = dimKey.equals(sp.serverLevel().dimension())
                    || SkillEffects.instantTransmissionCrossDimensionUnlocked(sp);
            if (!crossOk) return;

            ServerLevel destLevel = sp.server.getLevel(dimKey);
            if (destLevel == null) return; // dimensión no cargada ahora mismo (mod quitado, etc.)

            if (TeleportExecution.execute(sp, att, destLevel, target)) {
                InstantTransmissionSystem.syncStateIfChanged(sp, att, true);
            }
        });
    }

    /** subId vacío -> siempre "última llegada" (comportamiento de siempre, y el único que existe
     *  para una dimensión sin entradas en GenericDimensionDestinations). subId no vacío -> busca
     *  esa entrada concreta; null si no existe (id inventado por un cliente modificado) o si es
     *  un LastEntry sin punto grabado todavía. */
    private static BlockPos resolveTarget(InstantTransmissionAttachment att, ResourceKey<Level> dimKey,
                                           ResourceLocation loc, String subId) {
        if (subId.isEmpty()) return att.getLastEntryPos(dimKey);

        GenericSubDestination sub = GenericDimensionDestinations.byId(loc, subId);
        return switch (sub) {
            case null -> null;
            case GenericSubDestination.Fixed(String ignored, BlockPos pos, int ignoredCol, int ignoredRow) -> pos;
            case GenericSubDestination.LastEntry ignored -> att.getLastEntryPos(dimKey);
            case GenericSubDestination.Waypoint(String ignored, String key, int ignoredCol, int ignoredRow) ->
                    att.getWaypoint(key);
        };
    }
}
