package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Potential Unlock: el potencial dormido, sacado a la fuerza. No es un escalón más de la
 * cadena — es un estado paralelo que bloquea las demás transformaciones y no consume ki.
 *
 * EL MULTIPLICADOR VA ACOPLADO, no fijado en el JSON. Se calcula como una fracción del techo
 * de la mejor forma que el jugador HAYA COMPRADO, evaluada a maestría 100 (su tope teórico,
 * no su maestría actual). Dos consecuencias buscadas:
 *   - Comprar SSJ4 sube tu Potential Unlock aunque nunca lo uses: es literalmente "tu
 *     potencial", así que crece con lo que tu cuerpo es capaz de alcanzar.
 *   - No hay una tabla por raza que recalibrar cada vez que se añada una forma nueva.
 *
 * Y POR ESO HAY UN SUELO. Un humano o un namekiano sin transformaciones registradas tendría
 * un techo de 0 y su Potential Unlock no haría absolutamente nada, cuando para ellos es LA
 * transformación definitiva. El suelo es el stat_percent_mastered del propio JSON de la
 * forma, que deja de ser el valor final para pasar a ser el mínimo garantizado. En cuanto
 * esas razas tengan cadena, el acoplado la adelanta solo y el suelo deja de aplicar.
 *
 * La maestría interpola entre el 30 % y el 85 % de ese techo. El 85 % es el "levemente
 * inferior a tu mejor forma" del diseño: dominar Potential Unlock te deja casi al nivel de tu
 * cima, pero sin drenaje de ki y sin poder subir más — a cambio de renunciar a lo demás.
 */
public final class PotentialUnlock {
    private PotentialUnlock() {}

    /** Fracción del techo con maestría 0. */
    public static final double MASTERY_FLOOR_FACTOR = 0.30;
    /** Fracción del techo con maestría 100. */
    public static final double MASTERY_CEIL_FACTOR  = 0.85;

    /**
     * Techo de referencia de este jugador: la mejor forma COMPRADA de su raza, a maestría
     * 100, o el suelo del JSON si no tiene ninguna.
     *
     * Se mira el nivel de super_forms y no la cadena entera a propósito: el nivel 1 es la
     * base, así que el nivel N tiene compradas las formas 0..N-2 de la cadena.
     */
    public static double referenceCeiling(Player p) {
        FormDef pu = FormDef.get(FormIds.POTENTIAL_UNLOCK);
        double floor = (pu == null) ? 0.0 : pu.statPercentMastered();
        if (p == null) return floor;

        Race race = SuperForms.raceOf(p);
        if (race == null) return floor;

        List<ResourceLocation> chain = SuperForms.chain(race);
        int owned = SuperForms.level(p) - 1;   // formas compradas por encima de la base

        double best = 0.0;
        for (int i = 0; i < chain.size() && i < owned; i++) {
            FormDef d = FormDef.get(chain.get(i));
            if (d != null) best = Math.max(best, d.statPercentMastered());
        }
        return Math.max(best, floor);
    }

    /**
     * Fracción que suma la forma, a partir del techo ya resuelto y de SU maestría.
     * Recibe el techo en vez de calcularlo para que el cliente pueda usar el valor
     * sincronizado y no tenga que rehacer la cuenta con datos que quizá no tenga.
     */
    public static double statPercent(double ceiling, float mastery0to100) {
        double t = Math.max(0f, Math.min(100f, mastery0to100)) / 100.0;
        return ceiling * (MASTERY_FLOOR_FACTOR
                + (MASTERY_CEIL_FACTOR - MASTERY_FLOOR_FACTOR) * t);
    }

    /**
     * Gate de COMPRA por alineamiento. Se comprueba al comprar y no al usar: si se mirase al
     * usar, matar a un aldeano sin querer te desactivaría la forma en mitad de una pelea, y
     * un requisito que se pierde solo no es un requisito, es una trampa.
     */
    public static boolean canPurchase(Player p) {
        if (p == null) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att.getAlignment() >= CommonConfig.potentialUnlockAlignmentReq();
    }

    /**
    * ¿Sigue siendo legítimo estar en esta forma? Solo opina sobre Potential Unlock; para
    * cualquier otra devuelve true y manda quien corresponda. Existe porque super_forms ya no
    * guarda las formas de fuera de la cadena: alguien tiene que hacerlo, y es esta clase.
    * Mira solo la HABILIDAD, no el interruptor. Apagar el interruptor estando transformado
    * cambia a qué apunta la tecla, no te expulsa de la forma; para salir está el toque corto.
    */
    public static boolean canRemain(Player p, ResourceLocation formId) {
        if (!FormIds.POTENTIAL_UNLOCK.equals(formId)) return true;
    return p != null && SkillEffects.level(p, SkillEffects.POTENTIAL_UNLOCK) > 0;
    }
}