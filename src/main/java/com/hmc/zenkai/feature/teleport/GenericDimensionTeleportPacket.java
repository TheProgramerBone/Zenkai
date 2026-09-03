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
 * son un TeleportDestination del enum curado: aquí el "destino" es siempre el último punto de
 * entrada del propio jugador a esa dimensión (InstantTransmissionAttachment.lastEntryPos, ver
 * DimensionEntryTracker), identificado por el ResourceLocation crudo de la dimensión en vez de
 * un id fijo — así funciona igual sin importar qué mod registre la dimensión.
 * El servidor revalida TODO de nuevo (visitada, punto grabado, nivel de cross-dimension) — un
 * cliente modificado no puede pedir una dimensión que nunca visitó ni saltarse el nivel mínimo.
 */
public record GenericDimensionTeleportPacket(String dimensionId) implements CustomPacketPayload {

    public static final Type<GenericDimensionTeleportPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "generic_dimension_teleport"));

    // 128, no 32/64: un ResourceLocation "namespace:path" de un mod de terceros no tiene un
    // límite corto garantizado (a diferencia de un id fijo del propio mod) — sizear el cap para
    // el peor caso razonable, no el caso común, mismo criterio que ya documenta CLAUDE.md sobre
    // este mismo tipo de límite (writeUtf/readUtf revienta la conexión si el valor real lo
    // supera, no solo rechaza el packet).
    public static final StreamCodec<FriendlyByteBuf, GenericDimensionTeleportPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.dimensionId(), 128),
                    buf -> new GenericDimensionTeleportPacket(buf.readUtf(128)));

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

            BlockPos target = att.getLastEntryPos(dimKey);
            if (target == null) return; // visitada, pero el tracker aún no grabó ningún punto

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
}
