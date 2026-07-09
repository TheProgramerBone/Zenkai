package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.network.feature.Dbrattributes;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;

/**
 * Definición de stats de una entidad, tal cual viene del JSON de datapack
 * (data/&lt;ns&gt;/zenkai_entities/*.json). Es solo el "plano"; el runtime resuelto es EntityStats.
 *
 * Ejemplo:
 * {
 *   "entity": "zenkai:saibaman",
 *   "power_level": 1200,
 *   "archetype": "brawler",
 *   "overrides": { "attributes": { "spirit": "+20%", "strength": 250 },
 *                  "body_mult": 1.1, "ki_mult": 1.0 },
 *   "moveset": { "ki_attacks": ["zenkai:ki_blast"], "melee": true },
 *   "rewards": { "tp": "auto" }
 * }
 */
public record EntityStatDef(
        ResourceLocation entity,
        long powerLevel,
        boolean displayOnly,       // true = solo PL de display (sin stats de combate); "display_only" en JSON
        String archetype,
        EnumMap<Dbrattributes, AttrOverride> attributeOverrides,
        double bodyMultOverride,   // 1.0 = usar el del arquetipo
        double kiMultOverride,     // 1.0 = usar el del arquetipo
        List<ResourceLocation> movesetKiAttacks, // placeholder (Fase futura)
        boolean movesetMelee,                      // placeholder
        String rewardTp                            // "auto" (escala por PL) o número en string
) {
    /** Un override de atributo: absoluto (percent=false) o relativo en % (percent=true). */
    public record AttrOverride(boolean percent, double value) {}
}