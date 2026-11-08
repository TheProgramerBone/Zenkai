package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Rutas de endgame universales (0.3.0): Destroyer Path -&gt; Ultra Ego, Path of the Angels -&gt;
 * Ultra Instinct (Sign -&gt; pleno), Mystic Path -&gt; Beast (Mystic es la forma potential_unlock) — un solo
 * {@link FormDef} compartido por las 5 razas para cada peldaño, ver
 * {@code .claude/docs/jugador-razas-formas.md}.
 *
 * NO reutiliza {@link DivineForms}/{@link com.hmc.zenkai.feature.forms.PotentialUnlock}: las 3
 * raíces son {@code parent: null} igual que Potential Unlock, pero a diferencia de él SÍ viven
 * en la rueda como una transformación normal (se cargan con hold, no son un interruptor) — ver
 * {@code WheelMenu.forms()}, que las añade en una pasada aparte tras el BFS de la cadena de raza.
 *
 * ENTRADA (distinta por ruta, decisión del usuario 2026-09-30):
 *  - Ultra Instinct y Ultra Ego: el nivel de su skill (Señal 5, pleno 9 / Ultra Ego 5) y, desde
 *    F5 del skill tree (2026-10-21), God Ki — sin ninguna evolución de raza: las 5 razas llegan
 *    igual. Las 5th forms del Arcosiano son un camino secreto aparte.
 *  - Beast (evolución de Mystic): tener la habilidad de Mystic y haber sentido la furia
 *    ({@link com.hmc.zenkai.feature.rage.RageSystem}: muerte de una mascota propia o de un
 *    miembro de la party cerca, con la maestría de Mystic al mínimo configurado).
 * Las 5 formas de endgame llevan {@code endgame_path:true} y NO {@code divine_tier}, para no
 * contarse como rama divina de cada raza.
 *
 * NO son mutuamente excluyentes (decisión explícita del usuario, revertido tras un primer
 * intento con {@code FormDef.exclusiveGroup}/{@code PlayerFormAttachment.committedPaths} — ver
 * historial de commits si hace falta el detalle): un jugador puede desbloquear y usar las 3
 * rutas libremente, igual que cualquier otra forma de la cadena de su raza.
 */
public final class EndgamePaths {
    private EndgamePaths() {}

    /** ¿Desbloqueada esta forma de endgame para este jugador? Cada ruta tiene su regla: UI/UE por
     *  el nivel de su doctrina más God Ki, Beast por Mystic más la furia. */
    public static boolean unlocked(Player p, ResourceLocation form) {
        if (FormRegistry.get(form) == null) return false;
        // Ultra Instinct y Ultra Ego (decisión del usuario, 2026-09-30): SOLO piden el nivel de
        // su skill, sin ninguna evolución previa de raza — las 5 razas pueden llegar. Las
        // "5th forms" del Arcosiano son un camino secreto aparte que nada tiene que ver con esto.
        // Skill tree F5 (§5): las doctrinas de los dioses exigen God Ki en las cinco razas.
        if (FormIds.ULTRA_EGO.equals(form)) {
            return hasGodKi(p)
                    && SkillEffects.level(p, SkillEffects.ULTRA_EGO) >= OverlayMetersSystem.UE_FORM_LEVEL;
        }
        int uiLevel = requiredUiLevel(form);
        if (uiLevel > 0) return hasGodKi(p) && SkillEffects.level(p, SkillEffects.ULTRA_INSTINCT) >= uiLevel;
        // Beast: evolución de Mystic. Hace falta tener la habilidad de Mystic y que la furia ya
        // se haya disparado (Rage Forms, que a su vez exigió la maestría de Mystic en ese momento).
        if (FormIds.BEAST.equals(form)) {
            return SkillEffects.level(p, SkillEffects.POTENTIAL_UNLOCK) > 0
                    && PlayerStatsAttachment.get(p).hasBeastFury();
        }
        // Forma de endgame de un datapack sin regla propia: cerrada. (Antes caía en
        // entryUnlocked, «alguna forma EVOLUTION de tu raza»; F8 la quitó al dar a los tiers un
        // significado fijo de balance sin lógica de juego.)
        return false;
    }

    /** ¿Tiene God Ki? Requisito de las doctrinas (UI/UE) y de sus formas. */
    public static boolean hasGodKi(Player p) {
        return SkillEffects.level(p, DivineForms.SKILL) > 0;
    }

    /** Nivel de la skill Ultra Instinct que pide cada peldaño de esa ruta: Sign nivel 5,
     *  Ultra Instinct pleno nivel 9. 0 = la forma no pertenece a esa ruta (sin requisito). */
    public static int requiredUiLevel(ResourceLocation form) {
        if (FormIds.ULTRA_INSTINCT_SIGN.equals(form)) return 5;
        if (FormIds.ULTRA_INSTINCT.equals(form)) return 9;
        return 0;
    }
}
