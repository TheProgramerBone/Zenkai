package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S del sistema de técnicas (validación 100% servidor).
 *
 * op = UNLOCK: desbloquear un tipo (cuesta type.tpCost).
 * op = SAVE:   crear (slot = -1) o editar (slot >= 0) una instancia (nombre, tipo,
 *              color, tamaño, explosiva).
 * op = DELETE: borrar el slot indicado (las asignaciones se reparan solas).
 * op = BIND:   asignar el slot a una posición del overlay (size = posición 0..8; -1 = quitar).
 */
public record TechniquePacket(byte op, int slot, String typeName, String name,
                              int rgb, int size, boolean explosive)
        implements CustomPacketPayload {

    public static final byte OP_UNLOCK = 0;
    public static final byte OP_SAVE = 1;
    public static final byte OP_DELETE = 2;
    public static final byte OP_BIND = 3;

    public static final Type<TechniquePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "technique"));

    public static final StreamCodec<FriendlyByteBuf, TechniquePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeByte(pkt.op());
                        buf.writeVarInt(pkt.slot());
                        buf.writeUtf(pkt.typeName(), 32);
                        buf.writeUtf(pkt.name(), KiTechnique.MAX_NAME_LENGTH * 4);
                        buf.writeInt(pkt.rgb());
                        buf.writeVarInt(pkt.size());
                        buf.writeBoolean(pkt.explosive());
                    },
                    buf -> new TechniquePacket(buf.readByte(), buf.readVarInt(),
                            buf.readUtf(32), buf.readUtf(KiTechnique.MAX_NAME_LENGTH * 4),
                            buf.readInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ---- Constructores de conveniencia (cliente) ----
    public static TechniquePacket unlock(KiTechniqueType t) {
        return new TechniquePacket(OP_UNLOCK, -1, t.name(), "", 0, 0, false);
    }
    public static TechniquePacket save(int slot, KiTechniqueType t, String name,
                                       int rgb, int size, boolean explosive) {
        return new TechniquePacket(OP_SAVE, slot, t.name(), name, rgb, size, explosive);
    }
    public static TechniquePacket delete(int slot) {
        return new TechniquePacket(OP_DELETE, slot, "", "", 0, 0, false);
    }
    /** position 0..8 del overlay; -1 = desasignar. */
    public static TechniquePacket bind(int slot, int position) {
        return new TechniquePacket(OP_BIND, slot, "", "", 0, position, false);
    }

    public static void handle(TechniquePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            boolean changed = switch (pkt.op()) {
                case OP_UNLOCK -> handleUnlock(att, pkt);
                case OP_SAVE -> handleSave(att, pkt);
                case OP_DELETE -> {
                    boolean ok = att.techniques().slot(pkt.slot()) != null;
                    if (ok) att.techniques().removeSlot(pkt.slot());
                    yield ok;
                }
                case OP_BIND -> {
                    boolean ok = att.techniques().slot(pkt.slot()) != null;
                    if (ok) att.techniques().bind(pkt.size(), pkt.slot());
                    yield ok;
                }
                default -> false;
            };
            if (changed) PlayerLifeCycle.syncIfServer(sp);
        });
    }

    private static boolean handleUnlock(PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || att.techniques().isUnlocked(type)) return false;
        if (att.getTP() < type.tpCost) return false;
        att.addTP(-type.tpCost);
        att.techniques().unlock(type);
        return true;
    }

    private static boolean handleSave(PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || !att.techniques().isUnlocked(type)) return false;
        String name = KiTechnique.sanitizeName(pkt.name());
        if (name.isEmpty()) return false;
        int size = KiTechnique.clampSize(pkt.size());
        int rgb = pkt.rgb() & 0xFFFFFF;

        if (pkt.slot() < 0) { // crear
            if (att.techniques().slotCount() >= StatsConfig.techniqueMaxSlots()) return false;
            att.techniques().addSlot(new KiTechnique(name, type, rgb, size, pkt.explosive()));
            return true;
        }
        KiTechnique existing = att.techniques().slot(pkt.slot()); // editar
        if (existing == null) return false;
        existing.set(name, type, rgb, size, pkt.explosive());
        return true;
    }
}