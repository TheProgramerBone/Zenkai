package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.feature.ZenkaiAttributes;
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
 *   "moveset": {
 *     "melee": true,
 *     "ki_attacks": [
 *       { "type": "wave", "size": 3, "rgb": "0x33CCFF", "cooldown": 80,  "range": 20 },
 *       { "type": "big_blast", "size": 5, "rgb": "0xFFAA00", "cooldown": 140, "range": 24, "damage_mult": 1.2 }
 *     ]
 *   },
 *   "rewards": { "tp": "auto" }
 * }
 */
public record EntityStatDef(
        ResourceLocation entity,
        long powerLevel,
        boolean displayOnly,       // true = solo PL de display (sin stats de combate); "display_only" en JSON
        String archetype,
        EnumMap<ZenkaiAttributes, AttrOverride> attributeOverrides,
        double bodyMultOverride,   // 1.0 = usar el del arquetipo
        double kiMultOverride,     // 1.0 = usar el del arquetipo
        List<EntityKiAttack> kiAttacks, // ataques de ki que puede lanzar (vacío = ninguno)
        boolean movesetMelee,           // false = no persigue cuerpo a cuerpo
        String rewardTp                 // "auto" (escala por PL) o número en string
) {
    /** Un override de atributo: absoluto (percent=false) o relativo en % (percent=true). */
    public record AttrOverride(boolean percent, double value) {}

    /** ¿La entidad tiene al menos un ataque de ki definido? */
    public boolean hasKiAttacks() { return kiAttacks != null && !kiAttacks.isEmpty(); }
}