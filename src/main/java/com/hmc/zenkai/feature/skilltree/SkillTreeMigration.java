package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.event.tick.ArcosianPowerSystem;
import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerSkills;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.EndgamePaths;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regla ÚNICA que traduce las skills heredadas a nodos DERIVADOS del skill tree (espejo en vivo,
 * ver PlayerSkillTree), más la migración de guardados de versiones anteriores ({@link #migrate}).
 *
 * Solo se derivan HECHOS DE COMPRA (nivel de skill alcanzado). Lo que depende de una condición
 * que puede cambiar —alineamiento (qué hermana de Majin), maestría (golden_fifth_form), el
 * ritual de SSJ4, la furia de Beast, isDivine (Blue/Rosé)— NO es un nodo poseído: sigue siendo
 * cosa de FormAccess.unlocked(), que es la fachada que ya llama el resto del código. Oozaru y Súper
 * Oozaru son automáticos y tampoco se compran. Por eso ownedByPurchase() devuelve null para
 * esas formas ("no se decide por compra") y el comando de verificación las salta.
 */
public final class SkillTreeMigration {
    private SkillTreeMigration() {}

    /** La skill que desapareció en la v5 (un nivel por peldaño de la cadena mortal). Solo existe aquí, para migrar partidas. */
    private static final String LEGACY_SUPER_FORMS = "super_forms";

    /** Nodos con niveles que reflejan una skill heredada (ids de nodo, no de forma). */
    public static final ResourceLocation NODE_GOD_KI = node("god_ki");
    public static final ResourceLocation NODE_ARCOSIAN_POWER = node("arcosian_power");
    public static final ResourceLocation NODE_ULTRA_INSTINCT = node("ultra_instinct");
    public static final ResourceLocation NODE_ULTRA_EGO = node("ultra_ego");
    public static final ResourceLocation NODE_POTENTIAL_UNLOCK = node("potential_unlock");
    /** Gigante Namekiano: interruptor comprable (skill namek_giant), colgado de la Base. */
    public static final ResourceLocation NODE_NAMEK_GIANT = node("namek_giant");

    static ResourceLocation node(String path) {
        return ResourceLocation.fromNamespaceAndPath("zenkai", "node/" + path);
    }

    /**
     * ¿Esta forma se posee por COMPRA? true/false si la decide un nivel de skill; null si no la
     * decide una compra (automática, o por condición): no entra en el espejo.
     */
    public static Boolean ownedByPurchase(PlayerSkills sk, Race race, FormDef d) {
        ResourceLocation id = d.id();
        if (FormIds.POTENTIAL_UNLOCK.equals(id)) return sk.level(SkillEffects.POTENTIAL_UNLOCK) > 0;
        if (d.endgamePath()) {
            if (FormIds.ULTRA_EGO.equals(id)) {
                return sk.level(SkillEffects.ULTRA_EGO) >= OverlayMetersSystem.UE_FORM_LEVEL;
            }
            int uiLevel = EndgamePaths.requiredUiLevel(id);
            if (uiLevel > 0) return sk.level(SkillEffects.ULTRA_INSTINCT) >= uiLevel;
            return null; // Beast: Místico + furia, es una condición
        }
        // Formas divinas: desde F5 son nodos PROPIOS (los compra el árbol con su tp_cost), no
        // se derivan de ninguna skill. Igual la cadena mortal desde la 0.7.0.
        if (d.divineTier()) return null;
        // Rama que cuelga por maestría: se desbloquea por condición, no se compra.
        if (!d.masteryReqs().isEmpty()) return null;
        // Peldaños de la cadena mortal: nodos PROPIOS desde la 0.7.0 (migración v5), ya no se derivan de ninguna skill.
        return null;
    }

    /** Conjunto de nodos que corresponde a este jugador según sus skills heredadas. */
    public static Map<ResourceLocation, Integer> derive(PlayerStatsAttachment att) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        if (!att.isRaceChosen()) return out;
        Race race = att.getRace();
        if (race == null) return out;
        PlayerSkills sk = att.skills();

        for (FormDef d : FormDef.all()) {
            if (!d.allows(race)) continue;
            if (Boolean.TRUE.equals(ownedByPurchase(sk, race, d))) out.put(d.id(), 1);
        }

        if (sk.level(DivineForms.SKILL) > 0) out.put(NODE_GOD_KI, 1);
        putLevel(out, NODE_ARCOSIAN_POWER, sk.level(ArcosianPowerSystem.SKILL));
        putLevel(out, NODE_ULTRA_INSTINCT, sk.level(SkillEffects.ULTRA_INSTINCT));
        putLevel(out, NODE_ULTRA_EGO, sk.level(SkillEffects.ULTRA_EGO));
        putLevel(out, NODE_POTENTIAL_UNLOCK, sk.level(SkillEffects.POTENTIAL_UNLOCK));
        putLevel(out, NODE_NAMEK_GIANT, sk.level(SkillEffects.NAMEK_GIANT));
        return out;
    }

    private static void putLevel(Map<ResourceLocation, Integer> out, ResourceLocation id, int lvl) {
        if (lvl > 0) out.put(id, lvl);
    }

    /** Recalcula los nodos DERIVADOS (los propios no se tocan). Único escritor de esa mitad. */
    public static void refresh(PlayerStatsAttachment att) {
        att.skillTree().replaceWith(derive(att));
    }

    /**
     * Migración de un guardado de versión anterior (la llama PlayerStatsAttachment.load).
     * v1 → v2 (F5, 2026-10-21): god_ki pasa a nivel máximo 1 y cada forma divina a nodo propio.
     *  - god_ki nivel N (N = profundidad divina comprada) → god_ki 1 + nodos propios de las formas
     *    divinas de profundidad ≤ N (las dos hermanas, Blue y Rosé, para no depender de isDivine).
     *    Un Arcosiano con el nivel placeholder recibe así Arcosian GOD: lo había pagado al precio
     *    del SSJ God.
     *  - Quien tenga UI o UE (o sea una divinidad con cargo) recibe god_ki (§2, «Migración»): desde
     *    F5 las doctrinas lo exigen y no se le quita a nadie lo que ya usaba.
     */
    public static void migrate(PlayerStatsAttachment att) {
        PlayerSkills sk = att.skills();
        int version = att.skillTree().version();
        if (version < 2 && att.isRaceChosen() && att.getRace() != null) {
            Race race = att.getRace();
            int godKi = sk.level(DivineForms.SKILL);
            if (godKi > 0) {
                for (FormDef d : FormDef.all()) {
                    if (!d.divineTier() || d.endgamePath() || !d.allows(race)) continue;
                    int depth = DivineForms.depthOf(race, d.id());
                    if (depth > 0 && depth <= godKi) att.skillTree().setOwned(d.id(), 1);
                }
            }
            boolean doctrine = sk.level(SkillEffects.ULTRA_INSTINCT) > 0 || sk.level(SkillEffects.ULTRA_EGO) > 0;
            if (godKi > 1 || ((doctrine || att.isDivine()) && godKi <= 0)) {
                sk.setLevel(DivineForms.SKILL, 1);
            }
        }
        // v2 → v3 (2026-10-21): el Gigante Namekiano deja de ser el primer peldaño de la cadena de
        // formas y pasa a ser un interruptor (skill namek_giant). Quien tenía super_forms >= 3 (Base,
        // Gigante, ...) conserva el Gigante como skill y baja un nivel: Naranja pasa a ser el nivel 2.
        if (version < 3 && att.isRaceChosen() && att.getRace() == Race.NAMEKIAN) {
            int lvl = sk.level(LEGACY_SUPER_FORMS);
            if (lvl >= 2) {
                sk.setLevel(SkillEffects.NAMEK_GIANT, 1);
                sk.setLevel(LEGACY_SUPER_FORMS, lvl - 1);
            }
        }
        // v3 → v4 (0.7.0): el Súper Namekiano pasa a ser el primer peldaño de la cadena y Naranja el segundo. Quien ya tenía
        // Naranja (super_forms >= 2) la CONSERVA y recibe el Súper Namekiano: sube un nivel. Si un nivel estaba exento de MIND
        // (deseo de potencial), la exención se corre con él para no dejar la MIND libre en negativo.
        if (version < 4 && att.isRaceChosen() && att.getRace() == Race.NAMEKIAN) {
            int lvl = sk.level(LEGACY_SUPER_FORMS);
            if (lvl >= 2) {
                int waived = sk.mindWaivedUpTo(LEGACY_SUPER_FORMS);
                if (waived >= 2) sk.grantWaived(LEGACY_SUPER_FORMS, waived + 1);
                sk.setLevel(LEGACY_SUPER_FORMS, lvl + 1);
            }
        }
        // v4 → v5 (0.7.0): la skill super_forms desaparece; cada peldaño de la cadena mortal pasa a ser un nodo PROPIO (id = id de la
        // forma). SIN reembolso ni pérdida: el nivel N concede los N−1 primeros nodos de la cadena de su raza (el nivel 1 era la
        // Base). La exención de MIND (despertares, deseos) se traslada nodo a nodo: exento hasta el nivel W = exentos los W−1 primeros.
        if (version < 5) {
            int lvl = sk.level(LEGACY_SUPER_FORMS);
            if (att.isRaceChosen() && att.getRace() != null && lvl >= 2) {
                var chain = FormAccess.purchasableChain(att.getRace());
                // Cadena vacía = el datapack de formas aún no estaba cargado: no se sella la versión y se reintenta en la
                // próxima carga, antes que borrar la skill y perder lo comprado.
                if (chain.isEmpty()) {
                    refresh(att);
                    return;
                }
                FormAccess.grantChain(att, lvl - 1, false);
                int waived = sk.mindWaivedUpTo(LEGACY_SUPER_FORMS);
                for (int i = 0; i < waived - 1 && i < chain.size(); i++) att.skillTree().setMindWaived(chain.get(i), true);
            }
            sk.revoke(LEGACY_SUPER_FORMS);
        }
        refresh(att);
        att.skillTree().setVersion(com.hmc.zenkai.feature.player.PlayerSkillTree.VERSION);
    }
}
