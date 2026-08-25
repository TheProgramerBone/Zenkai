package com.hmc.zenkai.feature.master;

/**
 * Definición de un maestro. Vive en datapack (data/&lt;ns&gt;/zenkai_masters/&lt;id&gt;.json —
 * ver MasterManager) y NO viaja al cliente: los requisitos solo se comprueban en servidor y
 * el rechazo llega por chat. Lo que la pantalla necesita (qué enseña y a qué precio) ya está
 * en las SkillDef, que sí se sincronizan.
 * JSON (cada campo opcional):
 * <pre>
 * {
 *   "pl_req": 5000,
 *   "alignment_min": 20,
 *   "alignment_max": 100
 * }
 * </pre>
 *
 * @param id            id del maestro; debe coincidir con el campo "master" de las SkillDef
 * @param plReq         PL LIBERABLE mínimo (ver PlayerStatsAttachment#getReleasablePowerLevel)
 * @param alignmentMin  alineamiento mínimo aceptado, en la escala -100..+100
 * @param alignmentMax  alineamiento máximo aceptado
 */
public record MasterDef(String id, long plReq, int alignmentMin, int alignmentMax) {

    /** Sin restricciones. Se usa cuando un maestro no tiene JSON: enseña a cualquiera. */
    public static MasterDef open(String id) {
        return new MasterDef(id, 0L, -100, 100);
    }

    public boolean alignmentOk(int alignment) {
        return alignment >= alignmentMin && alignment <= alignmentMax;
    }

    /** Clave de lang del nombre mostrado. Separada del entity.zenkai.* a propósito: el
     *  maestro es un rol, y podrías querer que la entidad y el maestro se llamen distinto. */
    public String nameKey() { return "master.zenkai." + id; }
}