package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.race.RacePassives;
import net.minecraft.world.entity.player.Player;

/**
 * Cuánto rinde la regeneración de BODY mientras el jugador está en combate. Un solo embudo:
 * lo consultan RegenSystem (regen general, incluido el x3 namekiano) y RacePassiveSystem
 * (el canal propio del majin). Con la penalización escrita en cada sitio, bastaría tocar uno
 * para que un majin en combate se curara al ritmo de fuera y un humano no.
energy_generator * SOLO BODY. Estamina y ki no se penalizan: son los recursos con los que se pelea, y
 * secarlos en combate no hace la pelea más táctica, la hace más corta. Lo que se quiere
 * evitar es que un jugador aguante indefinidamente porque se cura tan rápido como le pegan.
energy_generator * EL SUELO RACIAL NO ES UN DETALLE DE BALANCE. Para namekiano y majin la regeneración ES la
 * raza, no un bonus: si el multiplicador general se pone a 0, un namekiano en combate deja
 * de ser un namekiano. El suelo garantiza que su identidad siga notándose aunque el servidor
 * decida que nadie se cura peleando. Un humano con el mult a 0 sí se queda a cero, que es lo
 * correcto: su pasiva es otra.
 */
public final class CombatRegen {
    private CombatRegen() {}

    /** Multiplicador a aplicar a la regeneración de body. 1.0 fuera de combate. */
    public static double bodyMult(Player p) {
        if (!InCombatState.isInCombat(p)) return 1.0;
        double m = ServerConfig.inCombatBodyRegenMult();
        if (RacePassives.hasRegenIdentity(p)) {
            m = Math.max(m, ServerConfig.inCombatRacialRegenFloor());
        }
        return m;
    }
}