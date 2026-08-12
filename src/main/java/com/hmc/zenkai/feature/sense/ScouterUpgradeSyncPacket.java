package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * S2C: los costes del scouter (login y /reload). Tres cosas en un solo paquete —mejoras,
 * reparación y tinte— porque las tres salen del mismo /reload y el cliente las necesita para
 * lo mismo: pintar precios y apagar botones SIN preguntar al servidor por cada fila y cada
 * frame. Separarlas en tres paquetes abriría la puerta a que llegue uno y no los otros.
 *
 * El servidor revalida el coste antes de cobrar: esto es presentación, no autoridad.
 */
public record ScouterUpgradeSyncPacket(Map<ScouterUpgrade, List<ScouterUpgradeCost>> costs,
                                       ScouterRepairCost repair,
                                       ScouterTintCost tint)
        implements CustomPacketPayload {

    public static final Type<ScouterUpgradeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_upgrade_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScouterUpgradeSyncPacket> STREAM_CODEC =
            StreamCodec.of(ScouterUpgradeSyncPacket::encode, ScouterUpgradeSyncPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ScouterUpgradeSyncPacket pkt) {
        buf.writeVarInt(pkt.costs().size());
        for (var e : pkt.costs().entrySet()) {
            buf.writeUtf(e.getKey().id());
            buf.writeVarInt(e.getValue().size());
            for (ScouterUpgradeCost c : e.getValue()) ScouterUpgradeCost.encode(buf, c);
        }
        // El ORDEN de estos dos tiene que ser el mismo aquí y en decode(): son campos de
        // ancho variable sin marca de tipo, así que invertirlos no da error de compilación,
        // da basura en el cliente.
        ScouterRepairCost.encode(buf, pkt.repair());
        ScouterTintCost.encode(buf, pkt.tint());
    }

    private static ScouterUpgradeSyncPacket decode(RegistryFriendlyByteBuf buf) {
        Map<ScouterUpgrade, List<ScouterUpgradeCost>> map = new EnumMap<>(ScouterUpgrade.class);
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            ScouterUpgrade u = ScouterUpgrade.byId(buf.readUtf());
            int ln = buf.readVarInt();
            List<ScouterUpgradeCost> list = new ArrayList<>(ln);
            for (int j = 0; j < ln; j++) list.add(ScouterUpgradeCost.decode(buf));
            // u == null: el servidor conoce una mejora que este cliente no. Se leen los bytes
            // igual (arriba) y se descarta la entrada, en vez de desincronizar el buffer.
            if (u != null) map.put(u, List.copyOf(list));
        }
        return new ScouterUpgradeSyncPacket(map,
                ScouterRepairCost.decode(buf),
                ScouterTintCost.decode(buf));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterUpgradeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ScouterUpgradeCost.replaceAll(pkt.costs());
            ScouterRepairCost.replace(pkt.repair());
            ScouterTintCost.replace(pkt.tint());
        });
    }
}