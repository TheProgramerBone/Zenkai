package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Quién puede tocar un cuerpo. Una sola puerta, SIEMPRE consultada en el servidor: el cliente nunca decide. */
public final class PlayerBodyAccess {
    private PlayerBodyAccess() {}

    /** Dueño, o miembro de su party si {@code permadeath.body_open_party} está activa. Los operadores
     *  en creativo también entran (para soporte del servidor). */
    public static boolean canOpen(ServerPlayer who, PlayerBodyEntity body) {
        UUID owner = body.owner().orElse(null);
        if (owner == null) return false;
        if (who.getUUID().equals(owner)) return true;
        if (who.isCreative() && who.hasPermissions(2)) return true;
        if (!ServerConfig.PERMADEATH_BODY_OPEN_PARTY.get()) return false;
        PartyManager.Party party = PartyManager.get(who.server).partyOf(owner);
        return party != null && party.members.contains(who.getUUID());
    }
}
