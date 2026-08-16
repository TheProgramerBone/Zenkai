package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Lógica central del "otro mundo": enviar a un jugador muerto, mantenerlo allí
 * y revivirlo. Usado por el death hook (OtherworldHandler), el comando
 * /zenkai revive y el deseo de revivir. El flag inOtherworld vive en
 * PlayerStateFlags (sincronizado y persistido vía PlayerStatsAttachment).
 */
public final class OtherworldManager {
    private OtherworldManager() {}

    public static final long REVIVE_DELAY_TICKS = 6000L; // 5 min (20 tps * 60 * 5)

    /** Posición de aparición en el otro mundo (ajusta a la entrada de tu estructura). */
    public static final BlockPos OTHERWORLD_SPAWN = new BlockPos(66, 197, 13);

    public static boolean isInOtherworld(ServerPlayer player) {
        return player.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isInOtherworld();
    }

    /** Cura vida vanilla + pools (incluida la barra HP/body) y limpia estado de daño. */
    private static void fullHeal(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects();
        player.setRemainingFireTicks(0);
        player.clearFire();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        // Restaura los pools (body=HP, stamina, energy) → arregla la barra "HP 0/20".
        player.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).refillOnRespawn();
        // Vida vanilla explícita ANTES del sync (mirrorHealth ya no revive a vida <= 0).
        player.setHealth(player.getMaxHealth());
        PlayerLifeCycle.sync(player);
    }

    private static void teleportToOtherworld(ServerPlayer player) {
        ServerLevel ow = player.server.getLevel(ModDimensions.OTHERWORLD_LEVEL);
        if (ow != null) {
            player.teleportTo(ow,
                    OTHERWORLD_SPAWN.getX() + 0.5,
                    OTHERWORLD_SPAWN.getY(),
                    OTHERWORLD_SPAWN.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }
    }

    /** Marca al jugador como muerto y lo teletransporta al otro mundo (reinicia el temporizador). */
    public static void sendToOtherworld(ServerPlayer player) {
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        fullHeal(player);
        // NO invulnerable: en el más allá el jugador SÍ puede recibir daño (PvP/entrenamiento).
        // Al agotar body allí, CombatZenkaiHooks lo re-ancla vía keepInOtherworld (no re-derriba).
        player.setInvulnerable(false);

        stats.setInOtherworld(true);
        stats.setOtherworldSince(player.serverLevel().getGameTime());

        teleportToOtherworld(player);
        PlayerLifeCycle.sync(player);
    }

    /**
     * Marca a un jugador que acaba de MORIR DE VERDAD (no se cancela la muerte) para que
     * reaparezca en el otro mundo. Se llama desde el death hook; el teletransporte ocurre en
     * el respawn (respawnIntoOtherworld). Fija aquí el flag + el inicio del temporizador de Yemma.
     * Como PLAYER_STATS usa copyOnDeath, el flag sobrevive a la muerte hasta el nuevo cuerpo.
     */
    public static void markPendingOtherworld(ServerPlayer player) {
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        stats.setInOtherworld(true);
        stats.setOtherworldSince(player.serverLevel().getGameTime());
        // Se apunta AQUÍ y no en el mixin: aquí sabemos que es una muerte de verdad, y el
        // mixin corre en el respawn, cuando ya no hay forma de distinguirla de un traslado.
        if (player.server.isHardcore()) stats.setHardcoreDeath(true);
    }

    /**
     * Coloca en el otro mundo a un jugador que ACABA DE REAPARECER con el flag activo.
     * NO reinicia el flag ni el temporizador de Yemma (ya se fijaron al morir): solo cura,
     * limpia el derribado residual, corrige el modo de juego y teletransporta a la entrada.
     */
    public static void respawnIntoOtherworld(ServerPlayer player) {
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        fullHeal(player);
        player.setInvulnerable(false);
        stats.flags().setDowned(false);
        stats.flags().setDownedUntil(0L);

        // El espectador forzado del hardcore se deshace AQUÍ y no en el mixin. Interceptar el
        // setGameMode de PlayerList#respawn exige acertar con un punto del bytecode de vanilla
        // que cambia entre versiones; esto corre un tick después, con el respawn ya terminado,
        // y solo tiene que leer el estado final. Lo que no se puede fallar es esto.
        //
        // Se condiciona a isSpectator y no a isHardcore a propósito: así un admin que muera en
        // creativo o que esté mirando de espectador a voluntad no se ve arrastrado a survival.
        if (player.isSpectator() && shouldSurviveHardcore(player)) {
            player.setGameMode(GameType.SURVIVAL);
        }

        teleportToOtherworld(player);
        PlayerLifeCycle.sync(player);
    }

    /**
     * Re-ancla a un jugador que YA está en el otro mundo y "murió" allí (p. ej. /kill):
     * lo cura y lo reposiciona, SIN reiniciar el temporizador de Yemma ni el flag.
     */
    public static void keepInOtherworld(ServerPlayer player) {
        fullHeal(player);
        player.setInvulnerable(false);
        // Limpia cualquier derribado residual: en el más allá no aplica el estado.
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        stats.flags().setDowned(false);
        stats.flags().setDownedUntil(0L);
        teleportToOtherworld(player);
        PlayerLifeCycle.sync(player);
    }

    /**
     * Revive al jugador y lo devuelve a su punto de respawn (cama/ancla o spawn del mundo).
     * Lo usan Yemma y /zenkai revive: los dos representan "vuelve a tu vida", no "ven aquí".
     */
    public static boolean revive(ServerPlayer player) {
        ServerLevel dest = player.server.getLevel(player.getRespawnDimension());
        if (dest == null) dest = player.server.overworld();
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) pos = dest.getSharedSpawnPos();
        return reviveTo(player, dest, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /**
     * Revive al jugador y lo deja en el sitio indicado. Lo usa el deseo: las esferas no te
     * mandan a casa, te traen de vuelta al mundo donde se pidió el deseo.
     */
    public static boolean reviveAt(ServerPlayer player, ServerLevel level,
                                   double x, double y, double z) {
        return reviveTo(player, level, x, y, z);
    }

    /**
     * El acto de resucitar. Los dos caminos públicos pasan por aquí y solo se diferencian en
     * el destino: si cada uno hiciera su propia limpieza, arreglar un fallo en el reseteo del
     * flag hardcoreDeath en un sitio dejaría el otro roto sin que nadie se enterara.
     */
    private static boolean reviveTo(ServerPlayer player, ServerLevel level,
                                    double x, double y, double z) {
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!stats.isInOtherworld()) return false;

        stats.setInOtherworld(false);
        stats.setOtherworldSince(0L);
        // Las esferas SÍ deshacen la muerte hardcore. Es el único camino de vuelta y por eso
        // vale la pena buscarlas.
        stats.setHardcoreDeath(false);
        player.setInvulnerable(false);
        fullHeal(player);

        // Espectador residual: si el mixin de hardcore no llegó a tiempo por lo que sea,
        // resucitar es el momento de arreglarlo. Un revivido fantasma no está revivido.
        if (player.isSpectator()) player.setGameMode(GameType.SURVIVAL);

        ServerLevel dest = (level != null) ? level : player.server.overworld();
        player.teleportTo(dest, x, y, z, player.getYRot(), player.getXRot());

        PlayerLifeCycle.sync(player);
        return true;
    }

    /**
     * ¿Este jugador debe reaparecer en SURVIVAL pese al hardcore? Lo pregunta
     * PlayerListHardcoreMixin justo antes del setGameMode(SPECTATOR) forzado.
     * La gamerule manda: con el Otro Mundo apagado el hardcore se comporta como siempre y el
     * jugador se queda de espectador. La regla vive AQUÍ y no en el mixin porque el mixin no
     * debe saber de gamerules — solo preguntar si a este jugador le toca.
     */
    public static boolean shouldSurviveHardcore(ServerPlayer player) {
        if (!ModGameRules.enableOtherworld(player.server)) return false;
        return isInOtherworld(player);
    }

    /** Columna de partículas en el punto de llegada. Sin esto, un jugador apareciendo de
     *  la nada a dos bloques es indistinguible de alguien que acaba de entrar al servidor. */
    private static void spawnParticles(ServerLevel level, Vec3 at) {
        for (int i = 0; i <= 6; i++) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    at.x, at.y + i * 0.5, at.z, 12, 0.3, 0.2, 0.3, 0.05);
        }
    }
}