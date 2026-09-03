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

        BlockPos safe = TeleportUtil.findSafeSpot(destLevel, rawTarget);
        Vec3 dest = TeleportUtil.footCenter(safe);

        originLevel.playSound(null, ox, oy, oz,
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.7f, 1.0f);

        sp.teleportTo(destLevel, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());

        destLevel.playSound(null, dest.x, dest.y, dest.z,
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.7f, 1.0f);

        stats.addKi(-kiCost);
        att.setCooldownTicks(SkillEffects.instantTransmissionCooldownTicks(sp));
        return true;
    }
}
