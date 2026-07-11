package com.hmc.zenkai.core.skills;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Definición de una habilidad (esqueleto v1.0).
 *
 * Se compran con TP y se desbloquean con requisito de MIND (el atributo mental gatea el
 * árbol). El EFECTO de cada habilidad se implementa donde corresponda consultando
 * PlayerStatsAttachment#skills().has(id) — este registro solo define QUÉ existe y cuánto
 * cuesta. Content drops post-release solo añaden register(...) aquí (o JSON en el futuro).
 *
 * Claves de lang derivadas: skill.zenkai.<id> (nombre) y skill.zenkai.<id>.desc (descripción).
 */
public record SkillDef(String id, int tpCost, int mindReq) {

    private static final Map<String, SkillDef> REGISTRY = new LinkedHashMap<>();

    static {
        // Habilidad de PRUEBA del esqueleto: valida compra/persistencia/UI end-to-end.
        // Su efecto llegará después (o bórrala cuando existan habilidades reales).
        register(new SkillDef("focus", 100, 5));
    }

    public static void register(SkillDef def) {
        REGISTRY.put(def.id(), def);
    }

    /** null si no existe. */
    public static SkillDef get(String id) {
        return REGISTRY.get(id);
    }

    /** En orden de registro (orden de la lista en la GUI). */
    public static Collection<SkillDef> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public String nameKey() { return "skill.zenkai." + id; }
    public String descKey() { return nameKey() + ".desc"; }
}