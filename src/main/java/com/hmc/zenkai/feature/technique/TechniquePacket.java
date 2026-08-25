package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.master.MasterManager;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S del sistema de técnicas (validación 100% servidor).
 * op = UNLOCK: desbloquear un tipo (cuesta type.tpCost). Si el tipo tiene master() (técnica
 *              firma, ver TechniqueDef), 'name' se REUTILIZA para llevar el id del maestro
 *              delante de quien se está comprando ("" = ninguno) — mismo truco que ya usa BIND
 *              con 'size'; UNLOCK no usa 'name' para nada más.
 * op = SAVE:   crear (slot = -1) o editar (slot >= 0) una instancia.
 * op = DELETE: borrar el slot indicado (las asignaciones se reparan solas).
 * op = BIND:   asignar el slot a una posición del overlay (usa 'size' como posición 0..8;
 *              -1 = quitar). Sí, reutiliza el campo.
 * Los sonidos viajan como texto ("" = ninguno) y se validan contra TechniqueAssets: el
 * cliente puede mandar cualquier id, así que aquí se comprueba que esté registrado.
 */
public record TechniquePacket(byte op, int slot, String typeName, String name,
                              int rgb, int size, int effect, String chargeSound, String releaseSound, int animSet)
        implements CustomPacketPayload {

    public static final byte OP_UNLOCK = 0;
    public static final byte OP_SAVE = 1;
    public static final byte OP_DELETE = 2;
    public static final byte OP_BIND = 3;
    public static final byte OP_FORGET = 4;

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
                        buf.writeVarInt(pkt.effect());
                        buf.writeUtf(pkt.chargeSound(), SOUND_ID_MAX);
                        buf.writeUtf(pkt.releaseSound(), SOUND_ID_MAX);
                        buf.writeVarInt(pkt.animSet());
                    },
                    buf -> new TechniquePacket(buf.readByte(), buf.readVarInt(),
                            buf.readUtf(32), buf.readUtf(KiTechnique.MAX_NAME_LENGTH * 4),
                            buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readUtf(SOUND_ID_MAX), buf.readUtf(SOUND_ID_MAX),
                            buf.readVarInt()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ---- Constructores de conveniencia (cliente) ----
    /** Desbloqueo normal (sin maestro delante). Para una técnica firma, usar la otra
     *  sobrecarga con el id del maestro — esta manda "" y el servidor la rechazará. */
    public static TechniquePacket unlock(KiTechniqueType t) {
        return unlock(t, "");
    }

    /** Desbloqueo ANTE UN MAESTRO: 'masterId' viaja en 'name' (ver comentario de la clase). */
    public static TechniquePacket unlock(KiTechniqueType t, String masterId) {
        return new TechniquePacket(OP_UNLOCK, -1, t.name(), masterId, 0, 0, 0, "", "", 1);
    }

    /** Olvidar un tipo de ki: libera su MIND y devuelve el TP. */
    public static TechniquePacket forget(KiTechniqueType t) {
        return new TechniquePacket(OP_FORGET, -1, t.name(), "", 0, 0, 0, "", "", 1);
    }

    public static TechniquePacket save(int slot, KiTechniqueType t, String name,
                                       int rgb, int size, TechniqueEffect effect,
                                       ResourceLocation chargeSound,
                                       ResourceLocation releaseSound,
                                       int animSet) {
        return new TechniquePacket(OP_SAVE, slot, t.name(), name, rgb, size,
                effect == null ? TechniqueEffect.NONE.ordinal() : effect.ordinal(),
                chargeSound == null ? "" : chargeSound.toString(),
                releaseSound == null ? "" : releaseSound.toString(),
                animSet);
    }

    public static TechniquePacket delete(int slot) {
        return new TechniquePacket(OP_DELETE, slot, "", "", 0, 0, 0, "", "", 1);
    }

    /** position 0..8 del overlay; -1 = desasignar. Ojo: viaja en 'size', no en 'position'. */
    public static TechniquePacket bind(int slot, int overlayPosition) {
        return new TechniquePacket(OP_BIND, slot, "", "", 0, overlayPosition, 0, "", "", 1);
    }

    public static void handle(TechniquePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            boolean changed = switch (pkt.op()) {
                case OP_UNLOCK -> handleUnlock(sp, att, pkt);
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
                case OP_FORGET -> handleForget(att, pkt);
                default -> false;
            };
            if (changed) PlayerLifeCycle.syncIfServer(sp);
        });
    }

    private static boolean handleUnlock(ServerPlayer sp, PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || !type.enabled() || att.techniques().isUnlocked(type)) return false;
        if (!MindBudget.canUnlock(att, type)) return false;

        // Técnica firma (ver TechniqueDef, "TÉCNICA FIRMA"): solo SU maestro la enseña, en
        // persona. Mismo embudo que SkillBuyPacket usa para el nivel 1 de una habilidad con
        // maestro — un cliente que no manda el masterId correcto (o lo manda sin estar
        // delante) se queda exactamente igual que si no hubiera pedido nada.
        String master = type.master();
        if (!master.isEmpty()) {
            if (!master.equals(pkt.name())) return false; // 'name' reutilizado como masterId
            Entity masterEntity = MasterManager.findNearby(sp, master);
            if (masterEntity == null) return false;
            MasterManager.Result r = MasterManager.check(sp, master, masterEntity);
            if (!r.ok()) { MasterManager.tell(sp, master, r); return false; }
        }

        if (att.getTP() < type.tpCost()) return false;
        att.addTP(-type.tpCost());
        att.techniques().unlock(type);
        return true;
    }

    private static boolean handleForget(PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || !att.techniques().isUnlocked(type)) return false;

        att.techniques().forget(type);
        att.addTP(type.tpCost());
        return true;
    }

    private static boolean handleSave(PlayerStatsAttachment att, TechniquePacket pkt) {
        KiTechniqueType type = KiTechniqueType.byName(pkt.typeName());
        if (type == null || !type.enabled() || !att.techniques().isUnlocked(type)) return false;

        String name = KiTechnique.sanitizeName(pkt.name());
        int size = KiTechnique.clampSize(pkt.size());
        // Técnica firma: el color es su identidad (p.ej. "morada"), no una elección del
        // jugador — se ignora lo que mande el cliente y se fuerza el de fábrica, igual que el
        // TIPO se ignora más abajo en modo edición. Nombre y tamaño SÍ siguen editables: lo
        // que se bloquea es la identidad visual, no el ajuste de coste/potencia.
        int rgb = type.master().isEmpty() ? (pkt.rgb() & 0xFFFFFF) : type.defaultRgb();
        ResourceLocation charge = validSound(pkt.chargeSound(), true);
        ResourceLocation release = validSound(pkt.releaseSound(), false);
        int animSet = TechniqueAnimSet.clamp(pkt.animSet());

        if (pkt.slot() < 0) { // crear
            if (att.techniques().slotCount() >= CommonConfig.techniqueMaxSlots()) return false;
            att.techniques().addSlot(new KiTechnique(name, type, rgb, size,
                    TechniqueEffect.byOrdinal(pkt.effect()), charge, release, animSet));
            return true;
        }
        KiTechnique existing = att.techniques().slot(pkt.slot()); // editar
        if (existing == null) return false;
        // El TIPO no se puede cambiar una vez creada la técnica: es lo que decide daño, coste,
        // animación y qué efectos admite, así que reasignarlo a mitad de vida es más "borrar y
        // crear otra" que "editar" — y el TextOnlyButton del cliente ya no deja tocarlo en este
        // caso (TechniqueEditScreen), pero el servidor es quien manda: un pkt.typeName() de otro
        // tipo (cliente modificado, o un draft viejo aún abierto cuando el tipo cambió en otro
        // sitio) se IGNORA en vez de aplicarse.
        existing.set(name, existing.type(), rgb, size, TechniqueEffect.byOrdinal(pkt.effect()),
                charge, release, animSet);
        return true;
    }

    private static ResourceLocation validSound(String raw, boolean charge) {
        if (raw == null || raw.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return null;
        boolean ok = charge ? TechniqueAssets.isValidCharge(id) : TechniqueAssets.isValidRelease(id);
        return ok ? id : null;
    }
}