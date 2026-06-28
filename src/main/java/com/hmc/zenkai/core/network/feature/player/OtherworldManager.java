package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Lógica central del "otro mundo": enviar a un jugador muerto y revivirlo.
 * Usado por el death hook (OtherworldHandler), el comando /zenkai revive y el
 * deseo de revivir. El flag inOtherworld vive en PlayerStateFlags (sincronizado
 * y persistido vía PlayerStatsAttachment).
 */
public final class OtherworldManager {
    private OtherworldManager() {}

    /** Posición de aparición en el otro mundo (ajusta a la entrada de tu estructura). */
    public static final BlockPos OTHERWORLD_SPAWN = new BlockPos(0, 65, 0);

    public static boolean isInOtherworld(ServerPlayer player) {
        return player.getData(DataAttachments.PLAYER_STATS.get()).isInOtherworld();
    }

    /** Marca al jugador como muerto y lo teletransporta al otro mundo. */
    public static void sendToOtherworld(ServerPlayer player) {
        PlayerStatsAttachment stats = player.getData(DataAttachments.PLAYER_STATS.get());

        // Limpiar estado de "casi muerto" para que no muera de nuevo.
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects();
        player.setRemainingFireTicks(0);
        player.clearFire();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);

        stats.setInOtherworld(true);
        stats.setOtherworldSince(player.serverLevel().getGameTime());

        ServerLevel ow = player.server.getLevel(ModDimensions.OTHERWORLD_LEVEL);
        if (ow != null) {
            player.teleportTo(ow,
                    OTHERWORLD_SPAWN.getX() + 0.5,
                    OTHERWORLD_SPAWN.getY(),
                    OTHERWORLD_SPAWN.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }
        PlayerLifeCycle.sync(player);
    }

    /**
     * Revive al jugador: quita el flag y lo devuelve a su punto de respawn
     * (cama/ancla o spawn del mundo). Devuelve false si no estaba en el otro mundo.
     */
    public static boolean revive(ServerPlayer player) {
        PlayerStatsAttachment stats = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!stats.isInOtherworld()) return false;

        stats.setInOtherworld(false);
        stats.setOtherworldSince(0L);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);

        ServerLevel dest = player.server.getLevel(player.getRespawnDimension());
        if (dest == null) dest = player.server.overworld();
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) pos = dest.getSharedSpawnPos();

        player.teleportTo(dest,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        PlayerLifeCycle.sync(player);
        return true;
    }
}