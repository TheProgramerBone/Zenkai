package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: aplicar un tinte al scouter que hay en el banco, o quitárselo.
 * POR QUÉ UN PAQUETE Y NO clickMenuButton: el id de botón de vanilla viaja como UN BYTE, y un
 * color son 24 bits. No hay forma de meterlo ahí sin inventar una tabla de índices, que es
 * peor que un paquete honesto.
 * SE REVALIDA AQUÍ. El cliente calcula el precio para el tooltip, pero esta manera vuelve
 * a calcularlo con los mismos datos sincronizados y con el inventario real: un cliente
 * modificado no puede teñir gratis.
 */
public record ScouterTintPacket(int rgb, boolean reset) implements CustomPacketPayload {

    public static final Type<ScouterTintPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_tint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScouterTintPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeInt(p.rgb()); buf.writeBoolean(p.reset()); },
                    buf -> new ScouterTintPacket(buf.readInt(), buf.readBoolean()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterTintPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // El menú abierto ES la autorización: si no tiene el banco delante, no hay nada
            // que teñir. Así no hace falta mandar ni revalidar un BlockPos.
            if (!(sp.containerMenu instanceof ScouterBenchMenu menu)) return;

            ItemStack scouter = menu.scouter();
            if (scouter.isEmpty()) return;
            if (ScouterStacks.isBroken(scouter)) return;      // roto: primero se repara
            if (menu.isWorking()) return;                      // no mientras hay trabajo

            if (pkt.reset()) {
                // Quitar el componente lo devuelve al color de fábrica sin escribir nada:
                // "sin teñir" y "teñido del color por defecto" no son el mismo estado.
                scouter.remove(DataComponents.DYED_COLOR);
                menu.setScouterChanged();
                return;
            }

            int rgb = pkt.rgb() & 0xFFFFFF;
            ScouterTintCost.Quote q = ScouterTintCost.get().quote(rgb);
            if (!q.canAfford(sp.getInventory())) return;
            if (!menu.spendEnergy(q.energy())) return;

            q.consume(sp.getInventory());
            scouter.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
            menu.setScouterChanged();
        });
    }
}