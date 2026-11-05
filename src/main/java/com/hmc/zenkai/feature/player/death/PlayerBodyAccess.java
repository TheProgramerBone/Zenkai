package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Quién puede tocar un cuerpo. Una sola puerta, SIEMPRE consultada en el servidor: el cliente nunca decide. */
public final class PlayerBodyAccess {
    private PlayerBodyAccess() {}

    /**
     * Clave de lang del motivo por el que se NIEGA el acceso, o null si se permite.
     * Orden: dueño y operador en creativo siempre entran; después la ventana de protección del dueño
     * ({@code permadeath.owner_grace_seconds}, ni siquiera la party); luego el grifeo; luego la party.
     */
    @Nullable
    public static String denyReason(ServerPlayer who, PlayerBodyEntity body) {
        UUID owner = body.owner().orElse(null);
        if (owner == null) return "zenkai.body.not_moral";
        if (who.getUUID().equals(owner)) return null;
        if (who.isCreative() && who.hasPermissions(2)) return null;
        int grace = ServerConfig.PERMADEATH_OWNER_GRACE_SECONDS.get();
        if (grace > 0 && body.level().getGameTime() - body.createdAt() < grace * 20L) return "zenkai.body.grace";
        if (ServerConfig.PERMADEATH_BODY_GRIEFING.get()) return null;
        if (!ServerConfig.PERMADEATH_BODY_OPEN_PARTY.get()) return "zenkai.body.not_moral";
        PartyManager.Party party = PartyManager.get(who.server).partyOf(owner);
        return party != null && party.members.contains(who.getUUID()) ? null : "zenkai.body.not_moral";
    }

    public static boolean canOpen(ServerPlayer who, PlayerBodyEntity body) {
        return denyReason(who, body) == null;
    }

    /** Como {@link #canOpen}, pero si se niega avisa en el CHAT con el motivo. */
    public static boolean checkOrWarn(ServerPlayer who, PlayerBodyEntity body) {
        String reason = denyReason(who, body);
        if (reason == null) return true;
        who.sendSystemMessage(Component.translatable(reason));
        return false;
    }

    /** ¿Puede reclamar la XP guardada? El dueño siempre; la party solo si {@code permadeath.xp_party_can_take}. */
    public static boolean canTakeXp(ServerPlayer who, PlayerBodyEntity body) {
        UUID owner = body.owner().orElse(null);
        if (owner != null && who.getUUID().equals(owner)) return true;
        if (owner == null || !ServerConfig.PERMADEATH_XP_PARTY_CAN_TAKE.get() || !canOpen(who, body)) return false;
        PartyManager.Party party = PartyManager.get(who.server).partyOf(owner);   // grifeo no basta: la XP es de la party, no de cualquiera
        return party != null && party.members.contains(who.getUUID());
    }
}
