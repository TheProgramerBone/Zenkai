package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Deseo de DESPERTAR EL POTENCIAL (2026-11-15, idea del usuario). Shenlong sube {@code ki_control} unos pocos niveles (el tope de
 * poder liberable, {@link SkillEffects#maxPowerPercent}; {@code ServerConfig.awakenPotentialKiControlLevels}, 3 por defecto: darlo todo de
 * golpe se vio excesivo) y, a los Namekianos, les regala además su primera transformación
 * (Naranja, nivel 2 de {@code super_forms}). Es una SEGUNDA vía: Naranja sigue comprándose con TP y no despierta por furia.
 * <p>
 * Los niveles se conceden con {@code grantWaived}: permanentes (suben el suelo, no se pueden olvidar ni reembolsar) y sin cobrar
 * MIND, igual que un despertar de Rage Forms. Nunca baja un nivel que el jugador ya tenga. Si no le daría nada, el servidor lo
 * rechaza SIN gastar el deseo (mismo criterio que {@code WishLegendaryPayload}). Toda la validación es del servidor.
 */
public record WishPotentialPayload() implements CustomPacketPayload {
    public static final Type<WishPotentialPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_potential"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishPotentialPayload> STREAM_CODEC =
            StreamCodec.unit(new WishPotentialPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Nivel de {@code super_forms} que equivale a tener la primera forma (Naranja): profundidad 1 + el nivel base. */
    public static final int NAMEK_FIRST_FORM_LEVEL = 2;

    /** Nivel máximo de Ki Control según el datapack (no se fija a mano: el JSON manda). */
    public static int kiControlMax() {
        SkillDef d = SkillDef.get(SkillEffects.KI_CONTROL);
        return d == null ? 0 : d.maxLevel();
    }

    /** ¿Ya no queda nada que concederle? (la pantalla esconde el deseo y el servidor lo rechaza). */
    public static boolean isComplete(PlayerStatsAttachment att) {
        if (att.skills().level(SkillEffects.KI_CONTROL) < kiControlMax()) return false;
        return att.getRace() != Race.NAMEKIAN || att.skills().level(SuperForms.SKILL) >= NAMEK_FIRST_FORM_LEVEL;
    }

    /** Concede el deseo. Devuelve false (sin tocar nada) si ya estaba completo. Cada deseo suma niveles; el tope es el máximo de la skill. */
    public static boolean apply(PlayerStatsAttachment att) {
        if (isComplete(att)) return false;
        int cur = att.skills().level(SkillEffects.KI_CONTROL);
        int target = Math.min(kiControlMax(), cur + ServerConfig.awakenPotentialKiControlLevels());
        att.skills().grantWaived(SkillEffects.KI_CONTROL, target);
        if (att.getRace() == Race.NAMEKIAN) att.skills().grantWaived(SuperForms.SKILL, NAMEK_FIRST_FORM_LEVEL);
        return true;
    }

    public static final class Handler {
        public static void handle(WishPotentialPayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) ctx.player();
                if (!WishSessions.validate(player)) return;
                if (!ServerConfig.isEnabled(ServerConfig.WishType.AWAKEN_POTENTIAL)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish_disabled"), false);
                    return;
                }
                PlayerStatsAttachment att = PlayerStatsAttachment.get(player);
                if (!att.isRaceChosen()) return;
                if (!apply(att)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish.potential_already"), false);
                    return;
                }
                PlayerLifeCycle.sync(player);
                player.displayClientMessage(Component.translatable(att.getRace() == Race.NAMEKIAN
                        ? "messages.zenkai.potential.namekian" : "messages.zenkai.potential"), false);

                WishFinalizer.finalizeWish(player, Component.translatable("messages.zenkai.wish_desc.potential"));
                ZenkaiTriggers.WISH_GRANTED.get().trigger(player, "awaken_potential");
            });
        }
    }
}
