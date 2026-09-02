package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Habilidad "god_ki": UNA sola skill (no una por raza) que desbloquea la rama de energía
 * divina de la raza del jugador, igual de "genérica por raza" que ya es super_forms — mismo
 * espíritu, apuntando a un subconjunto distinto de la cadena de formas (las marcadas
 * {@code divine_tier} en su FormDef, ver el javadoc de esa clase).
 *
 * MAESTRO: hoy la enseña Kami, pero SOLO como placeholder — no hay ningún NPC "Whis"/"Bills"
 * (Beerus) en el mod todavía. Canónicamente debería ser uno de esos dos (los guías angelicales/
 * el Dios de la Destrucción son quienes enseñan el ki divino en la serie); en cuanto exista esa
 * entidad, cambiar el campo "master" de zenkai_skills/god_ki.json — no hace falta tocar nada de
 * esta clase, el maestro es puro dato de datapack.
 *
 * SSJ BLUE vs SSJ ROSE: no es una elección del jugador. Son la MISMA forma narrativa vista
 * desde dos orígenes distintos de ki divino, y por eso son técnicamente dos FormDef separados
 * (mismo parent ssj_god, mismos números) en vez de una sola forma con un color condicional:
 * Blue es lo que un Saiyan MORTAL alcanza en ese peldaño; Rosé es lo que alcanza ESE MISMO
 * peldaño si el jugador ya es "divino" (PlayerStatsAttachment.isDivine — ver /zenkai debug
 * divine). Para un jugador divino, Blue directamente "no existe": resolveForPlayer() siempre
 * devuelve Rosé en su lugar, y viceversa. Por eso además comparten maestría por defecto
 * (CommonConfig.ssjBlueRoseShareMastery) — es la MISMA progresión vista con otro nombre, no dos
 * logros independientes.
 *
 * Por qué NO se apoya en FormRegistry.chainFor/SuperForms.chain para nada de esto: esos dos son
 * un CAMINO ÚNICO (FormRegistry.nextFrom devuelve solo el primer hijo cuando una forma tiene
 * varios), y aquí hace falta enumerar y comparar TODAS las hermanas de una profundidad. Esta
 * clase calcula la profundidad de cada forma subiendo por su propio parent mientras siga siendo
 * divineTier, así que dos hermanas quedan EMPATADAS a la misma profundidad en vez de que el
 * camino único se coma una de las dos en silencio.
 */
public final class DivineForms {
    private DivineForms() {}

    public static final String SKILL = "god_ki";

    /** Todas las formas divine_tier que admite esa raza, sin importar su posición en el árbol. */
    private static List<ResourceLocation> divineFormsFor(Race race) {
        List<ResourceLocation> out = new ArrayList<>();
        if (race == null) return out;
        for (FormDef d : FormDef.all()) {
            if (d.divineTier() && d.allows(race)) out.add(d.id());
        }
        return out;
    }

    /**
     * Profundidad dentro de la RAMA divina: 1 para el primer peldaño (su parent ya no es
     * divineTier), y sigue subiendo mientras el parent también lo sea. Dos hermanas (mismo
     * parent, ambas divineTier) quedan a la MISMA profundidad a propósito — ver javadoc de
     * clase. 0 si la forma no es divina o no pertenece a esa raza.
     */
    private static int depthOf(Race race, ResourceLocation form) {
        FormDef d = FormDef.get(form);
        if (d == null || !d.divineTier() || !d.allows(race)) return 0;
        int depth = 1;
        ResourceLocation parent = d.parent();
        while (parent != null) {
            FormDef pd = FormDef.get(parent);
            if (pd == null || !pd.divineTier()) break;
            depth++;
            parent = pd.parent();
        }
        return depth;
    }

    /**
     * Entre las formas empatadas a esa profundidad (hermanas), cuál es "la" real para este
     * jugador. Sin empate, la única candidata tal cual. Con empate, la única regla que existe
     * hoy: Rosé (FormIds.SSJ_ROSE) si el jugador es divino, cualquier otra hermana (Blue) si no
     * — ver el javadoc de clase. null si esa profundidad no tiene ninguna forma para la raza.
     */
    private static ResourceLocation resolveTie(Race race, int depth, Player p) {
        List<ResourceLocation> atDepth = new ArrayList<>();
        for (ResourceLocation id : divineFormsFor(race)) {
            if (depthOf(race, id) == depth) atDepth.add(id);
        }
        if (atDepth.isEmpty()) return null;
        if (atDepth.size() == 1) return atDepth.get(0);

        boolean divine = PlayerStatsAttachment.get(p).isDivine();
        for (ResourceLocation id : atDepth) {
            if (FormIds.SSJ_ROSE.equals(id) == divine) return id;
        }
        return atDepth.get(0); // ninguna hermana coincide con la regla: no dejar sin resolver
    }

    /**
     * Si `form` es una forma de nivel divino con hermanas (ver resolveTie), la que de verdad
     * le corresponde a ESTE jugador en esa profundidad — que puede ser una hermana distinta de
     * `form`. Si `form` no es divine_tier, o no tiene hermanas, se devuelve tal cual.
     */
    public static ResourceLocation resolveForPlayer(Player p, ResourceLocation form) {
        Race race = SuperForms.raceOf(p);
        int depth = depthOf(race, form);
        if (depth <= 0) return form;
        ResourceLocation resolved = resolveTie(race, depth, p);
        return resolved == null ? form : resolved;
    }

    /**
     * Si `current` (la forma que el jugador lleva puesta ahora mismo) tiene una hermana y ya
     * NO es la que le corresponde (isDivine cambió mientras la llevaba puesta), la hermana a
     * la que debería pasar — para que FormSystem.tick() pueda deslizar de una a otra en vez de
     * forzar Base de golpe. null si no aplica, o si `current` sigue siendo la correcta.
     * Comprueba unlocked() sobre la resuelta para no proponer un cambio hacia algo que TAMBIÉN
     * esté fuera de alcance por otra razón (p. ej. si además se perdió el nivel de god_ki).
     */
    public static ResourceLocation pendingSiblingSwap(Player p, ResourceLocation current) {
        ResourceLocation resolved = resolveForPlayer(p, current);
        if (resolved.equals(current)) return null;
        return unlocked(p, resolved) ? resolved : null;
    }

    /** Niveles que "god_ki" puede llegar a tener para esa raza. 0 si no tiene ninguna forma
     *  divina (hoy: Arcosiano). */
    public static int maxLevel(Race race) {
        int max = 0;
        for (ResourceLocation id : divineFormsFor(race)) max = Math.max(max, depthOf(race, id));
        return max;
    }

    public static int maxLevel(Player p) { return maxLevel(SuperForms.raceOf(p)); }

    /**
     * Coste en TP de ese nivel: el tp_cost de CUALQUIER forma divina en esa profundidad (las
     * hermanas deben compartir el mismo tp_cost por diseño — ver zenkai_forms/ssj_blue.json /
     * ssj_rose.json). MAX_VALUE si ese nivel no existe para esta raza.
     */
    public static int tpCostForLevel(Race race, int level) {
        for (ResourceLocation id : divineFormsFor(race)) {
            if (depthOf(race, id) == level) {
                FormDef d = FormDef.get(id);
                return d == null ? Integer.MAX_VALUE : d.tpCost();
            }
        }
        return Integer.MAX_VALUE;
    }

    public static int tpCostForLevel(Player p, int level) {
        return tpCostForLevel(SuperForms.raceOf(p), level);
    }

    /** ¿Esta raza tiene alguna forma de nivel divino? (Arcosiano hoy: no.) Usado para ocultar
     *  la fila de "god_ki" en la tienda de su maestro para quien no le sirve de nada. */
    public static boolean appliesTo(Race race) { return maxLevel(race) > 0; }

    /**
     * ¿Desbloqueada esta forma divina para este jugador? Exige la cadena "mortal" de su raza
     * (SuperForms) ya agotada Y el nivel correspondiente de "god_ki" — Y, si tiene hermanas,
     * ser la que resuelve para él (ver resolveTie): con isDivine puesto, Blue deja de estar
     * "unlocked" para él aunque tenga de sobra el nivel de god_ki, porque para él esa forma ya
     * no existe — lo que existe en su lugar es Rosé.
     */
    public static boolean unlocked(Player p, ResourceLocation form) {
        Race race = SuperForms.raceOf(p);
        int depth = depthOf(race, form);
        if (depth <= 0) return false;
        if (SuperForms.level(p) < SuperForms.maxLevel(race)) return false;
        if (SkillEffects.level(p, SKILL) < depth) return false;
        return form.equals(resolveTie(race, depth, p));
    }
}
