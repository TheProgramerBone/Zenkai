package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.util.TeleportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Cola de ejecución COMPARTIDA por las dos formas de teletransportarse de Instant
 * Transmission: el blink de nivel 1 (InstantTransmissionSystem, a lo que esté en la mira) y el
 * menú de planetas de la Fase 2 (TeleportRequestPacket, a un destino elegido). Mismo coste de
 * ki y mismo cooldown para los dos — un salto largo a un sitio conocido no es "más barato" que
 * un blink corto, y viceversa, en esta fase.
 */
public final class TeleportExecution {
    private TeleportExecution() {}

    /**
     * Intenta teletransportar. No hace nada (y devuelve false) si sigue en cooldown, no tiene
     * la skill, o no puede pagar el ki — en ninguno de esos casos se cobra nada ni se reproduce
     * sonido. `destLevel` puede ser el mismo nivel del jugador (blink, destinos del Overworld)
     * o, en el futuro, uno distinto (salto entre dimensiones — todavía no expuesto por ningún
     * llamador de esta fase).
     */
    public static boolean execute(ServerPlayer sp, InstantTransmissionAttachment att,
                                   ServerLevel destLevel, BlockPos rawTarget) {
        if (att.getCooldownTicks() > 0) return false;

        int level = SkillEffects.instantTransmissionLevel(sp);
        if (level <= 0) return false;

        double kiCost = SkillEffects.instantTransmissionKiCost(sp);
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        if (stats.getKiCurrent() < kiCost) return false;

        ServerLevel originLevel = sp.serverLevel();
        double ox = sp.getX(), oy = sp.getY(), oz = sp.getZ();
        boolean crossingDimension = !originLevel.dimension().equals(destLevel.dimension());

        BlockPos safe = TeleportUtil.findSafeSpot(destLevel, rawTarget);
        Vec3 dest = TeleportUtil.footCenter(safe);

        originLevel.playSound(null, ox, oy, oz,
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.2f, 1.0f);

        // sp.teleportTo(ServerLevel, ...) SÍ pasa por ServerPlayer.changeDimension(...) en cuanto
        // destLevel es distinto del nivel actual (verificado leyendo el fuente real de NeoForm/
        // NeoForge: la rama newLevel != this.level() de ese método construye un
        // DimensionTransition y llama a changeDimension, que a su vez dispara
        // PlayerChangedDimensionEvent) — DimensionEntryTracker está enganchado a ese evento para
        // capturar la posición REAL del portal en un cruce genuino, así que hay que avisarle de
        // que ESTE cruce concreto es nuestro (Instant Transmission), no una llegada real, o
        // sobrescribiría su propia posición con el punto de llegada de este mismo teletransporte.
        if (crossingDimension) DimensionEntryTracker.suppressNextEntry(sp);
        sp.teleportTo(destLevel, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());

        // Red de seguridad ADEMÁS de TeleportUtil.isSafe evitando portales de verdad: el
        // DimensionTransition que arma sp.teleportTo usa DO_NOTHING como post-transición, que no
        // reproduce el cooldown de portal que sí aplica un cruce real (Entity.handleNetherPortal)
        // — sin esto, aterrizar cerca de CUALQUIER portal (uno que TeleportUtil no conociera, o
        // simplemente muy pegado) puede volver a cruzarlo al instante. Mismo valor que usa el
        // propio vainilla tras un cruce real (300 ticks para un jugador que no va montado en
        // nada).
        sp.setPortalCooldown();

        destLevel.playSound(null, dest.x, dest.y, dest.z,
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.2f, 1.0f);

        stats.addKi(-kiCost);
        att.setCooldownTicks(SkillEffects.instantTransmissionCooldownTicks(sp));
        return true;
    }
}
