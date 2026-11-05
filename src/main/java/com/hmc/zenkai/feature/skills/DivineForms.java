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
 * Rama divina de cada raza (las formas {@code divine_tier} de su FormDef) y la skill "god_ki".
 * Desde F5 del skill tree (2026-10-21) god_ki es de NIVEL 1 y no se compra con TP: es la puerta
 * (la dan la prueba del maestro, el ritual o ser una divinidad con cargo) y cada forma divina es
 * un nodo PROPIO del árbol que se compra con su tp_cost (antes: nivel N de god_ki = peldaño N).
 *
 * MAESTRO: hoy concede la audiencia Tori-Bot (GodKiServices), pero SOLO como placeholder — no hay ningún NPC "Whis"/"Bills"
 * (Beerus) en el mod todavía, y Tori-Bot es el catch-all de "cosas sin maestro adecuado" (junto a
 * instant_transmission y potential_unlock). Canónicamente debería ser uno de esos dos (los guías
 * angelicales/el Dios de la Destrucción son quienes enseñan el ki divino en la serie); en cuanto
 * exista esa entidad, cambiar el campo "masters" de zenkai_skills/god_ki.json — no hace falta
 * tocar nada de esta clase, el maestro es puro dato de datapack.
 *
 * SSJ BLUE vs SSJ ROSE: no es una elección del jugador. Son la MISMA forma narrativa vista
 * desde dos orígenes distintos de ki divino, y por eso son técnicamente dos FormDef separados
 * (mismo parent ssj_god, mismos números) en vez de una sola forma con un color condicional:
 * Blue es lo que un Saiyan MORTAL alcanza en ese peldaño; Rosé es lo que alcanza ESE MISMO
 * peldaño si el jugador ya es "divino" (PlayerStatsAttachment.isDivine — ver /zenkai debug
 * divine). Para un jugador divino, Blue directamente "no existe": resolveForPlayer() siempre
 * devuelve Rosé en su lugar, y viceversa. Por eso además comparten maestría por defecto
 * (ServerConfig.ssjBlueRoseShareMastery) — es la MISMA progresión vista con otro nombre, no dos
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
    public static int depthOf(Race race, ResourceLocation form) {
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
    private static ResourceLocation resolveTie(Race race, int depth, Player p, String ns) {
        List<ResourceLocation> atDepth = new ArrayList<>();
        for (ResourceLocation id : divineFormsFor(race)) {
            // Grupo de empate = mismo namespace: la pareja Blue/Rosé es de Zenkai; la forma divina de un addon colgada del
            // mismo padre es una hermana INDEPENDIENTE y no debe empatar con ellas (2026-11-02).
            if (depthOf(race, id) == depth && id.getNamespace().equals(ns)) atDepth.add(id);
        }
        if (atDepth.isEmpty()) return null;
        if (atDepth.size() == 1) return atDepth.get(0);

        boolean divine = PlayerStatsAttachment.get(p).isDivine();
        for (ResourceLocation id : atDepth) {
            if (FormIds.isRoseLine(id) == divine) return id;
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
        ResourceLocation resolved = resolveTie(race, depth, p, form.getNamespace());
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

    /** Profundidad máxima de la rama divina de esa raza (0 si no tiene ninguna forma divina).
     *  Desde F5 ya no es el techo de god_ki (que es de nivel 1): es solo información. */
    public static int maxLevel(Race race) {
        int max = 0;
        for (ResourceLocation id : divineFormsFor(race)) max = Math.max(max, depthOf(race, id));
        return max;
    }

    public static int maxLevel(Player p) { return maxLevel(SuperForms.raceOf(p)); }

    /**
     * Coste en TP de la forma divina de esa profundidad: el tp_cost de CUALQUIER forma divina en
     * ella (las hermanas deben compartir el mismo tp_cost por diseño, ver ssj_blue.json /
     * ssj_rose.json). MAX_VALUE si no existe para esta raza.
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

    /** ¿Tiene esta raza alguna forma divina? Desde F5 las cinco la tienen (Arcosian GOD). */
    public static boolean appliesTo(Race race) { return maxLevel(race) > 0; }

    /**
     * ¿Posee el jugador el PELDAÑO divino de esa forma? Las formas divinas son nodos propios del
     * skill tree desde F5 (antes eran niveles de god_ki); poseer una hermana empatada (Blue)
     * cuenta como poseer el peldaño entero, porque resolveTie decide cuál de las dos existe para
     * él según isDivine.
     */
    public static boolean ownsDepth(Player p, Race race, ResourceLocation form) {
        int depth = depthOf(race, form);
        if (depth <= 0) return false;
        var tree = PlayerStatsAttachment.get(p).skillTree();
        for (ResourceLocation id : divineFormsFor(race)) {
            if (depthOf(race, id) == depth && id.getNamespace().equals(form.getNamespace()) && tree.owns(id)) return true;
        }
        return false;
    }

    /**
     * ¿Desbloqueada esta forma divina para este jugador? Exige el peldaño mortal del que cuelga
     * (SuperForms), God Ki, y el nodo de ese peldaño divino comprado en el árbol — Y, si tiene hermanas,
     * ser la que resuelve para él (ver resolveTie): con isDivine puesto, Blue deja de estar
     * "unlocked" para él aunque tenga de sobra el nivel de god_ki, porque para él esa forma ya
     * no existe — lo que existe en su lugar es Rosé.
     */
    public static boolean unlocked(Player p, ResourceLocation form) {
        Race race = SuperForms.raceOf(p);
        int depth = depthOf(race, form);
        if (depth <= 0) return false;
        if (SuperForms.level(p) < SuperForms.mortalLevelNeededForDivine(race, form)) return false;
        if (SkillEffects.level(p, SKILL) <= 0) return false;
        if (!ownsDepth(p, race, form)) return false;
        // Forma divina de un addon: además exige que su padre esté desbloqueado (en las del mod lo garantiza la compra por peldaño).
        FormDef fd = FormDef.get(form);
        if (fd != null && fd.parent() != null && !com.hmc.zenkai.Zenkai.MOD_ID.equals(form.getNamespace())
                && !SuperForms.unlocked(p, fd.parent())) return false;
        return form.equals(resolveTie(race, depth, p, form.getNamespace()));
    }
}
