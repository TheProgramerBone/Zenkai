package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S del sistema de técnicas (validación 100% servidor).
 * op = UNLOCK: desbloquear un tipo (cuesta type.tpCost).
 * op = SAVE:   crear (slot = -1) o editar (slot >= 0) una instancia.
 * op = DELETE: borrar el slot indicado (las asignaciones se reparan solas).
 * op = BIND:   asignar el slot a una posición del overlay (usa 'size' como posición 0..8;
 *              -1 = quitar). Sí, reutiliza el campo: es anterior a 'position' y cambiarlo
 *              obligaría a tocar el overlay sin ganar nada.
 * 'position' es OTRA cosa: desde dónde SALE la técnica (mano, boca, frente...).
 * Los sonidos viajan como texto ("" = ninguno) y se validan contra TechniqueAssets: el
 * cliente puede mandar cualquier id, así que aquí se comprueba que esté registrado.
 */
public record TechniquePacket(byte op, int slot, String typeName, String name,
                              int rgb, int size, boolean explosive,
                              int position, String chargeSound, String releaseSound, int animSet)
        implements CustomPacketPayload {

    public static final byte OP_UNLOCK = 0;
    public static final byte OP_SAVE = 1;
    public static final byte OP_DELETE = 2;
    public static final byte OP_BIND = 3;

    private static final int SOUND_ID_MAX = 128;

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
                        buf.writeVarInt(pkt.position());
                        buf.writeUtf(pkt.chargeSound(), SOUND_ID_MAX);
                        buf.writeUtf(pkt.releaseSound(), SOUND_ID_MAX);
                        buf.writeVarInt(pkt.animSet());
                    },
                    buf -> new TechniquePacket(buf.readByte(), buf.readVarInt(),
                            buf.readUtf(32), buf.readUtf(KiTechnique.MAX_NAME_LENGTH * 4),
                            buf.readInt(), buf.readVarInt(), buf.readBoolean(),
                            buf.readVarInt(), buf.readUtf(SOUND_ID_MAX),
                            buf.readUtf(SOUND_ID_MAX), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ---- Constructores de conveniencia (cliente) ----
    public static TechniquePacket unlock(KiTechniqueType t) {
        return new TechniquePacket(OP_UNLOCK, -1, t.name(), "", 0, 0, false, 0, "", "", 1);
    }

    public static TechniquePacket save(int slot, KiTechniqueType t, String name,
                                       int rgb, int size, boolean explosive,
                                       TechniquePosition position,
                                       ResourceLocation chargeSound,
                                       ResourceLocation releaseSound,
                                       int animSet) {
        return new TechniquePacket(OP_SAVE, slot, t.name(), name, rgb, size, explosive,
                (position == null ? 0 : position.ordinal()),
                chargeSound == null ? "" : chargeSound.toString(),
                releaseSound == null ? "" : releaseSound.toString(),
                animSet);
    }

    public static TechniquePacket delete(int slot) {
        return new TechniquePacket(OP_DELETE, slot, "", "", 0, 0, false, 0, "", "", 1);
    }

    /** position 0..8 del overlay; -1 = desasignar. Ojo: viaja en 'size', no en 'position'. */
    public static TechniquePacket bind(int slot, int overlayPosition) {
        return new TechniquePacket(OP_BIND, slot, "", "", 0, overlayPosition, false, 0, "", "", 1);
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
        if (type == null || !type.enabled() || att.techniques().isUnlocked(type)) return false;
        if (att.getAttribute(ZenkaiAttributes.MIND) < type.mindReq()) return false;
        if (att.getTP() < type.tpCost()) return false;
        att.addTP(-type.tpCost());
        att.techniques().unlock(type);
        return true;
    }

    private static boolean handleSave(PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || !type.enabled() || !att.techniques().isUnlocked(type)) return false;

        // El nombre vacío ES VÁLIDO: la GUI muestra el nombre del tipo al vuelo, así cada
        // jugador lo lee en su idioma en vez de congelar la traducción del que la creó.
        String name = KiTechnique.sanitizeName(pkt.name());
        int size = KiTechnique.clampSize(pkt.size());
        int rgb = pkt.rgb() & 0xFFFFFF;
        TechniquePosition position = TechniquePosition.byOrdinal(pkt.position());
        ResourceLocation charge = validSound(pkt.chargeSound(), true);
        ResourceLocation release = validSound(pkt.releaseSound(), false);
        int animSet = Math.max(1, pkt.animSet());

        if (pkt.slot() < 0) { // crear
            if (att.techniques().slotCount() >= CommonConfig.techniqueMaxSlots()) return false;
            att.techniques().addSlot(new KiTechnique(name, type, rgb, size, pkt.explosive(),
                    position, charge, release, animSet));
            return true;
        }
        KiTechnique existing = att.techniques().slot(pkt.slot()); // editar
        if (existing == null) return false;
        existing.set(name, type, rgb, size, pkt.explosive(), position, charge, release, animSet);
        return true;
    }

    /**
     * "" o un id no registrado -> null (sin sonido). No se rechaza el guardado entero por un
     * sonido malo: el resto de la técnica es válido y quedarse mudo es un fallo recuperable.
     */
    private static ResourceLocation validSound(String raw, boolean charge) {
        if (raw == null || raw.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return null;
        boolean ok = charge ? TechniqueAssets.isValidCharge(id) : TechniqueAssets.isValidRelease(id);
        return ok ? id : null;
    }
}