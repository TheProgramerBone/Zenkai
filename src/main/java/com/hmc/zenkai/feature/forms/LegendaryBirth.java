package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.util.RandomSource;

/**
 * Nacer Legendario (0.7.0, decidido con el usuario el 2026-11-16): al completar la creación del personaje, y de nuevo en cada
 * reencarnación, cualquier raza tiene {@code legendary.birth_chance_percent} (5 por defecto; 0 = apagado) de recibir el ESTADO
 * Legendario, el mismo flag que concede el deseo a Shenlong (abre Ikari o la forma Legendaria de la raza; no transforma).
 * <p>
 * Una tirada por personaje y sin semilla: {@code PlayerStateFlags.legendaryRolled} impide repetirla al volver a elegir raza o
 * estilo con el mismo personaje, y la reencarnación lo rearma porque estrena attachment. No hay más límite contra repetir vidas:
 * reencarnar ya cuesta el personaje entero.
 */
public final class LegendaryBirth {
    private LegendaryBirth() {}

    /**
     * Hace la tirada si este personaje aún no la tuvo. Solo datos (sin red ni mensajes), para poder probarla en un gametest.
     *
     * @param majinMarked lleva la marca Majin ahora mismo: con {@code majin.legendary_mutually_exclusive} no se concede
     * @return true si acaba de nacer Legendario
     */
    public static boolean roll(PlayerStatsAttachment att, RandomSource random, boolean majinMarked) {
        return roll(att, random, majinMarked, ServerConfig.legendaryBirthChancePercent());
    }

    /** La misma tirada con la probabilidad dada (0-100): la config está cacheada y los gametests necesitan fijarla. */
    public static boolean roll(PlayerStatsAttachment att, RandomSource random, boolean majinMarked, double chance) {
        if (att.flags().isLegendaryRolled()) return false;
        att.flags().setLegendaryRolled(true);
        if (att.isLegendary()) return false;
        if (chance <= 0.0) return false;
        if (majinMarked && ServerConfig.majinLegendaryExclusive()) return false;
        if (random.nextDouble() * 100.0 >= chance) return false;
        att.setLegendary(true);
        return true;
    }
}
