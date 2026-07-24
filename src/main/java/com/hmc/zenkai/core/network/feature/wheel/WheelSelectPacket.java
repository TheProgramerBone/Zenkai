package com.hmc.zenkai.core.network.feature.wheel;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillEffects;
import com.hmc.zenkai.core.skills.SuperForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: selección hecha en el menú radial. Un solo packet para lo que la rueda elige
 * (kind + carga útil en texto): añadir una categoría no obliga a registrar otro payload.
 * La rueda NO transforma. FORM fija la forma OBJETIVO y KAIOKEN alterna el interruptor;
 * subir de escalón es cosa de la tecla de transformar.
 * La rueda del cliente ya pinta en gris lo que no puedes usar, pero eso es cosmética:
 * se revalida aquí.
 */
public record WheelSelectPacket(String kind, String value) implements CustomPacketPayload {

    public static final Type<WheelSelectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "wheel_select"));

    public static final StreamCodec<FriendlyByteBuf, WheelSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WheelSelectPacket::kind,
                    ByteBufCodecs.STRING_UTF8, WheelSelectPacket::value,
                    WheelSelectPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WheelSelectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            PlayerStatsAttachment st = sp.getData(DataAttachments.PLAYER_STATS.get());
            PlayerFormAttachment fm = sp.getData(DataAttachments.PLAYER_FORM.get());
            if (st == null || fm == null || !st.isRaceChosen()) return;

            boolean changed = switch (pkt.kind()) {
                case "FORM"    -> selectForm(sp, st.getRace(), fm, pkt.value());
                case "KAIOKEN" -> toggleKaioken(sp, fm);
                default        -> false;
            };
            if (changed) PlayerLifeCycle.syncFormIfServer(sp);
        });
    }

    /** Fija la forma objetivo. No transforma: solo dice hacia dónde apunta la tecla. */
    private static boolean selectForm(ServerPlayer sp, Race race,
                                      PlayerFormAttachment fm, String value) {
        ResourceLocation target = ResourceLocation.tryParse(value);
        if (target == null || race == null) return false;

        if (!FormIds.BASE.equals(target)) {
            if (!FormRegistry.isAllowed(race, target)) return false;
            if (FormRegistry.get(target) == null) return false;
            if (!SuperForms.unlocked(sp, target)) return false;
        }
        if (target.equals(fm.getSelectedForm())) return false;

        fm.setSelectedForm(target);

        Component name = FormIds.BASE.equals(target)
                ? Component.translatable("wheel.zenkai.base")
                : Component.translatable("form.zenkai." + target.getPath());
        sp.displayClientMessage(
                Component.translatable("message.zenkai.form_selected", name), true);
        return true;
    }

    /**
     * Alterna el interruptor. Apagarlo NO baja el kaioken activo: solo devuelve la tecla de
     * transformar a la escalera de formas. El escalón se conserva.
     */
    private static boolean toggleKaioken(ServerPlayer sp, PlayerFormAttachment fm) {
        if (SkillEffects.level(sp, "kaioken") <= 0) return false;
        fm.setKaiokenSwitch(!fm.isKaiokenSwitch());
        return true;
    }
}