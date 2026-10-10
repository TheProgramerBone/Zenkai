package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Perfil de combate VIRTUAL de un jugador SIN raza: lo trata como un Humano recién creado
 * (atributos base, poder al 50 % por defecto, sin TP ni formas). No se guarda nada; el jugador
 * sigue sin raza y su vida vanilla sigue siendo la suya.
 *
 * Por qué existe: sin esto el jugador sin raza no tenía stats y el pipeline lo trataba como a un
 * mob vanilla. En los dos sentidos salía mal:
 *  - RECIBIENDO: el golpe de STR de un jugador con raza o de un mob con stats (~49 de un humano
 *    inicial) entraba ENTERO contra sus 20 de vida y moría de 1-2 golpes.
 *  - PEGANDO: su golpe vanilla (1-7) chocaba con la DEF y el pool del rival y no hacía nada.
 * Con el perfil base pelea exactamente como alguien que acaba de elegir Humano: su golpe sale de
 * STR base y lo que recibe se mitiga con DEF base y se traduce al MISMO % de su vida vanilla que
 * ese golpe le quitaría a un body base (ver CombatZenkaiHooks.applyToUnracedVictim).
 *
 * Solo lo usa el pipeline de daño (CombatZenkaiHooks.onDamage). ZenkaiStats.of() sigue
 * devolviendo los stats reales del jugador: scouter, sentir el ki, PL y recompensas no deben ver
 * a un jugador sin raza como si la tuviera.
 */
public final class UnracedBaseline implements ZenkaiCombatStats {

    private final Player player;
    /** Plantilla: un attachment nuevo con raza Humano. Se crea por golpe a propósito: leer
     *  ServerConfig en cada uso hace que un cambio de balance se note sin reiniciar, y un golpe
     *  es un evento raro comparado con un tick. */
    private final PlayerStatsAttachment template;

    private UnracedBaseline(Player player) {
        this.player = player;
        this.template = new PlayerStatsAttachment();
        this.template.setRace(Race.HUMAN);
        this.template.refillOnRespawn();
    }

    /**
     * Stats de combate de CUALQUIER entidad para el pipeline de daño: los reales si los tiene, el
     * perfil base si es un jugador sin raza con la capa Zenkai activa, o null (mob sin stats).
     */
    public static ZenkaiCombatStats combatStatsOf(Entity e) {
        ZenkaiCombatStats real = ZenkaiStats.of(e);
        if (real != null && real.isCombatActive()) return real;
        if (e instanceof Player p && !p.isSpectator() && raceBoostsOn(p.getServer())) {
            return new UnracedBaseline(p);
        }
        return real;
    }

    public static boolean is(ZenkaiCombatStats st) {
        return st instanceof UnracedBaseline;
    }

    private static boolean raceBoostsOn(MinecraftServer server) {
        return server != null && ModGameRules.enableRaceBoosts(server);
    }

    // ── Stats: los del Humano base ───────────────────────────────────────────
    @Override public double computeMeleeFinal()   { return template.computeMeleeFinal(); }
    @Override public double computeDefenseFinal() { return template.computeDefenseFinal(); }
    @Override public double computeKiPowerFinal() { return template.computeKiPowerFinal(); }
    @Override public double computeKiPoolFinal()  { return template.computeKiPoolFinal(); }
    @Override public double computeConFinal()     { return template.computeConFinal(); }

    @Override public boolean isCombatActive() { return true; }

    // ── Body: PROYECCIÓN de la vida vanilla sobre el body base ────────────────
    // La vida vanilla es la verdad; el body solo existe para que el pipeline pueda medir qué
    // % se lleva un golpe. Escribir aquí no hace nada: el daño lo aplica vanilla con el
    // equivalente que le pasa applyToUnracedVictim.
    @Override public int getBodyMax() { return Math.max(1, template.getBodyMax()); }
    @Override public int getBody() {
        float max = player.getMaxHealth();
        return max <= 0f ? 0 : Math.round(getBodyMax() * (player.getHealth() / max));
    }
    @Override public void addBody(int delta) {}

    // Sin estamina ni ki que gastar: el golpe base no cobra recursos (no hay barra que mostrar).
    @Override public int getStamina()    { return template.getStaminaMax(); }
    @Override public int getStaminaMax() { return template.getStaminaMax(); }
    @Override public void consumeStamina(int amount) {}
    @Override public int getEnergy()     { return 0; }
    @Override public int getEnergyMax()  { return 0; }

    /** La vida vanilla manda: nunca se reescribe desde el pool virtual. */
    @Override public void mirrorToVanilla(net.minecraft.world.entity.LivingEntity le) {}
}
