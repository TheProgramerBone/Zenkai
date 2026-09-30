package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Rutas de endgame universales (0.3.0): Destroyer Path -&gt; Ultra Ego, Path of the Angels -&gt;
 * Ultra Instinct (Sign -&gt; pleno), Mystic Path -&gt; Ultimate Mystic -&gt; Beast — un solo
 * {@link FormDef} compartido por las 5 razas para cada peldaño, ver
 * {@code .claude/pendiente/rediseno-transformaciones-balance.md}.
 *
 * NO reutiliza {@link DivineForms}/{@link com.hmc.zenkai.feature.forms.PotentialUnlock}: las 3
 * raíces son {@code parent: null} igual que Potential Unlock, pero a diferencia de él SÍ viven
 * en la rueda como una transformación normal (se cargan con hold, no son un interruptor) — ver
 * {@code WheelMenu.forms()}, que las añade en una pasada aparte tras el BFS de la cadena de raza.
 *
 * ENTRADA: haber desbloqueado el "Evolution Tier" propio de la raza (campo {@code tier} del
 * FormDef, ver {@link #entryUnlocked}). Las 5 formas de endgame llevan {@code endgame_path:true}
 * y NO {@code divine_tier}, para no contarse como rama divina de cada raza.
 *
 * NO son mutuamente excluyentes (decisión explícita del usuario, revertido tras un primer
 * intento con {@code FormDef.exclusiveGroup}/{@code PlayerFormAttachment.committedPaths} — ver
 * historial de commits si hace falta el detalle): un jugador puede desbloquear y usar las 3
 * rutas libremente, igual que cualquier otra forma de la cadena de su raza.
 */
public final class EndgamePaths {
    private EndgamePaths() {}

    /**
     * ¿Tiene el jugador desbloqueada alguna forma "Evolution Tier" de SU raza
     * ({@link FormDef.Tier#EVOLUTION}: SSB/Rosé, Orange, golden_fifth_form...)? Ya NO exige agotar
     * super_forms + god_ki: el Arcosiano no tiene rama divina y aun así llega por su propia
     * Evolution. Las formas endgame se excluyen a propósito (comparten tier con las de raza pero
     * llamarlas aquí sería recursivo, {@code SuperForms.unlocked} las enruta a este método).
     */
    public static boolean entryUnlocked(Player p) {
        Race race = SuperForms.raceOf(p);
        if (race == null) return false;
        for (FormDef d : FormDef.all()) {
            if (d.tier() != FormDef.Tier.EVOLUTION || d.endgamePath() || !d.allows(race)) continue;
            if (SuperForms.unlocked(p, d.id())) return true;
        }
        return false;
    }

    /** ¿Desbloqueada esta forma de endgame para este jugador? Hoy es literalmente
     *  {@link #entryUnlocked} — sin exclusividad entre rutas, no hace falta mirar nada más
     *  sobre la forma en sí. Se queda como método aparte (no se inlinea en las llamadas) por si
     *  algún día una ruta concreta necesita un requisito adicional propio. */
    public static boolean unlocked(Player p, ResourceLocation form) {
        if (FormRegistry.get(form) == null || !entryUnlocked(p)) return false;
        return SkillEffects.level(p, SkillEffects.ULTRA_INSTINCT) >= requiredUiLevel(form);
    }

    /** Nivel de la skill Ultra Instinct que pide cada peldaño de esa ruta: Sign nivel 5,
     *  Ultra Instinct pleno nivel 9. 0 = la forma no pertenece a esa ruta (sin requisito). */
    public static int requiredUiLevel(ResourceLocation form) {
        if (FormIds.ULTRA_INSTINCT_SIGN.equals(form)) return 5;
        if (FormIds.ULTRA_INSTINCT.equals(form)) return 9;
        return 0;
    }
}
