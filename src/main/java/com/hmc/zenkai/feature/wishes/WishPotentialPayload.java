package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skilltree.FormAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Deseo de DESPERTAR EL POTENCIAL (2026-11-15, idea del usuario). Shenlong sube {@code ki_control} unos pocos niveles (el tope de
 * poder liberable, {@link SkillEffects#maxPowerPercent}; {@code ServerConfig.awakenPotentialKiControlLevels}, 3 por defecto: darlo completo de
 * golpe se vio excesivo) y, a los Namekianos, les regala además su primera transformación
 * (Súper Namekiano, nodo propio del árbol; es una SEGUNDA vía: también se compra con TP) junto con el indicio de que
 * existe un poder superior. Al Namekiano que YA la tenía (0.7.0), el deseo le da además algo de MAESTRÍA de esa forma
 * ({@code awaken_potential_wish.namek_super_mastery_percent}) y REVELA en el árbol su nodo oculto, Naranja
 * ({@code PlayerStateFlags.namekOrangeHinted}); Naranja nunca se regala: despierta por maestría (ver MasteryAwakening).
 * <p>
 * El nodo y los niveles se conceden sin cobrar TP (el nivel de Ki Control con {@code grantWaived}: permanente, no se puede olvidar ni reembolsar) y sin cobrar
 * MIND, igual que un despertar de Rage Forms. Nunca baja un nivel que el jugador ya tenga. Si no le daría nada, el servidor lo
 * rechaza SIN gastar el deseo (mismo criterio que {@code WishLegendaryPayload}). Toda la validación es del servidor.
 */
public record WishPotentialPayload() implements CustomPacketPayload {
    public static final Type<WishPotentialPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_potential"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishPotentialPayload> STREAM_CODEC =
            StreamCodec.unit(new WishPotentialPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final ResourceLocation NAMEK_SUPER = ResourceLocation.fromNamespaceAndPath("zenkai", "namek_super");
    public static final ResourceLocation NAMEK_ORANGE = ResourceLocation.fromNamespaceAndPath("zenkai", "namek_orange");

    /** Qué le dio el deseo, para elegir el mensaje. */
    public enum Outcome { NOTHING, GRANTED, NAMEK_FIRST_FORM, NAMEK_REVEAL }

    /** Nivel máximo de Ki Control según el datapack (no se fija a mano: el JSON manda). */
    public static int kiControlMax() {
        SkillDef d = SkillDef.get(SkillEffects.KI_CONTROL);
        return d == null ? 0 : d.maxLevel();
    }

    /** ¿Ya no queda nada que concederle? (la pantalla esconde el deseo y el servidor lo rechaza). */
    public static boolean isComplete(PlayerStatsAttachment att) {
        if (att.skills().level(SkillEffects.KI_CONTROL) < kiControlMax()) return false;
        if (att.getRace() != Race.NAMEKIAN) return true;
        // Al Namekiano le queda algo mientras no tenga su primera forma o, teniéndola, no haya visto aún el poder de más allá
        // (si ya posee Naranja, no hay nada que revelar).
        if (att.skillTree().owns(NAMEK_ORANGE)) return true;
        return att.skillTree().owns(NAMEK_SUPER) && att.flags().isNamekOrangeHinted();
    }

    /** Concede el deseo. Devuelve false (sin tocar nada) si ya estaba completo. Cada deseo suma niveles; el tope es el máximo de la skill. */
    public static boolean apply(PlayerStatsAttachment att) {
        return apply(att, null) != Outcome.NOTHING;
    }

    /**
     * Igual, diciendo qué dio. {@code forms} es el attachment de formas del jugador (para la maestría del Súper Namekiano);
     * con null no se suma maestría (pruebas que solo miran las skills).
     */
    public static Outcome apply(PlayerStatsAttachment att, @org.jetbrains.annotations.Nullable com.hmc.zenkai.feature.player.PlayerFormAttachment forms) {
        if (isComplete(att)) return Outcome.NOTHING;
        int cur = att.skills().level(SkillEffects.KI_CONTROL);
        int target = Math.min(kiControlMax(), cur + ServerConfig.awakenPotentialKiControlLevels());
        if (target > cur) att.skills().grantWaived(SkillEffects.KI_CONTROL, target);
        if (att.getRace() != Race.NAMEKIAN) return Outcome.GRANTED;
        if (!att.skillTree().owns(NAMEK_SUPER)) {
            com.hmc.zenkai.feature.skilltree.SkillTree.grantNode(att, NAMEK_SUPER, true);
            return Outcome.NAMEK_FIRST_FORM;
        }
        if (!att.skillTree().owns(NAMEK_ORANGE) && !att.flags().isNamekOrangeHinted()) {
            att.flags().setNamekOrangeHinted(true);
            float pct = (float) ServerConfig.awakenPotentialNamekMastery();
            // Puntos de maestría tal cual (sin pasar por mastery_gain): el porcentaje del deseo es el de la config.
            if (forms != null && pct > 0f) forms.addFormMasteryPoints(NAMEK_SUPER, pct);
            return Outcome.NAMEK_REVEAL;
        }
        return Outcome.GRANTED;
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
                Outcome outcome = apply(att, player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()));
                if (outcome == Outcome.NOTHING) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish.potential_already"), false);
                    return;
                }
                PlayerLifeCycle.sync(player);
                PlayerLifeCycle.syncFormToTrackersAndSelf(player);   // la maestría regalada viaja con el attachment de formas
                player.displayClientMessage(Component.translatable(switch (outcome) {
                    case NAMEK_FIRST_FORM -> "messages.zenkai.potential.namekian";
                    case NAMEK_REVEAL -> "messages.zenkai.potential.namekian_again";
                    default -> "messages.zenkai.potential";
                }), false);
                // La maestría regalada puede bastar para que Naranja despierte en el acto.
                com.hmc.zenkai.feature.skilltree.MasteryAwakening.check(player);

                WishFinalizer.finalizeWish(player, Component.translatable("messages.zenkai.wish_desc.potential"));
                ZenkaiTriggers.WISH_GRANTED.get().trigger(player, "awaken_potential");
            });
        }
    }
}
