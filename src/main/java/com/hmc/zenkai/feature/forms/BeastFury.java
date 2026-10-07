package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.UUID;

/**
 * Desbloqueo de Beast por furia. Beast es la evolución de Mystic (potential_unlock) y se abre con
 * DOS condiciones: (1) maestría de Mystic al mínimo de {@code beast.unlock_mystic_mastery_percent}
 * y (2) presenciar de cerca la muerte de una mascota propia o de un miembro de la party.
 *
 * La maestría se comprueba EN EL MOMENTO de la muerte: el disparo es un evento, no un estado, y
 * si el jugador aún no llegaba al mínimo la furia no se consume (la próxima muerte cercana
 * cuenta). Una vez disparada queda guardada ({@link PlayerStatsAttachment#hasBeastFury()}) y
 * {@code EndgamePaths.unlocked} deja de pedir nada más; no se pierde si luego baja la maestría.
 *
 * Para la party se escucha la muerte REAL: DownedDeathGuard cancela la primera (el jugador queda
 * derribado) y esa cancelación no llega aquí; la muerte de verdad, al acabarse el derribado, sí.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class BeastFury {
    private BeastFury() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide()) return;
        var server = dead.getServer();
        if (server == null) return;

        double r2 = ServerConfig.beastFuryRadius() * ServerConfig.beastFuryRadius();

        // Mascota con dueño: furia solo para SU dueño, si estaba cerca.
        if (dead instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(pet.getOwnerUUID());
            if (owner != null && owner != dead && owner.level() == dead.level()
                    && owner.distanceToSqr(dead) <= r2) {
                trigger(owner);
            }
            return;
        }

        // Miembro de la party: furia para el resto de la party que estuviera cerca.
        if (dead instanceof ServerPlayer victim) {
            PartyManager.Party party = PartyManager.get(server).partyOf(victim.getUUID());
            if (party == null) return;
            for (UUID id : party.members) {
                if (id.equals(victim.getUUID())) continue;
                ServerPlayer mate = server.getPlayerList().getPlayer(id);
                if (mate != null && mate.level() == victim.level() && mate.distanceToSqr(victim) <= r2) {
                    trigger(mate);
                }
            }
        }
    }

    private static void trigger(ServerPlayer sp) {
        if (SkillEffects.level(sp, SkillEffects.POTENTIAL_UNLOCK) <= 0) return;
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        if (stats.hasBeastFury()) return;
        PlayerFormAttachment forms = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        if (forms.getFormMastery(FormIds.POTENTIAL_UNLOCK) < ServerConfig.beastUnlockMysticMastery()) return;

        stats.setBeastFury(true);
        sp.displayClientMessage(Component.translatable("message.zenkai.beast_fury")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        PlayerLifeCycle.sync(sp);
    }
}
