package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;

/**
 * Presupuesto de MIND. ÚNICO sitio donde se decide cuánta concentración ocupa un jugador y
 * cuánta le queda libre.
 * QUÉ CAMBIA. Antes había dos lecturas distintas de MIND conviviendo:
 *   - Habilidades: MIND como CAPACIDAD. Cada nivel ocupa un hueco que no vuelve.
 *   - Técnicas: MIND como UMBRAL. Bastaba con tener el número; no ocupaba nada.
 * Con el umbral, MIND dejaba de significar algo en cuanto pasabas el listón más alto: a partir
 * de ahí todas las técnicas eran gratis en concentración y el atributo era un peaje de una vez.
 * Ahora los tres orígenes comparten la misma bolsa y MIND vuelve a ser una decisión sostenida:
 * lo que dedicas a técnicas no lo tienes para habilidades.
 * LOS TRES ORÍGENES SUMAN EN EL MISMO SITIO a propósito. Con el cálculo repetido en cada
 * pantalla y cada packet, bastaría olvidar uno para que el jugador viese "12 libres" y el
 * servidor le rechazara la compra sin decir por qué. Es el mismo motivo por el que
 * PlayerStatsAttachment#mindUsed delega aquí en vez de sumar por su cuenta.
 * OCUPA EL TIPO, NO LA INSTANCIA. Un jugador con seis kamehamehas guardados ocupa lo mismo que
 * con uno: lo que aprendió es la técnica, y las instancias del editor son variaciones de algo
 * que ya sabe hacer. El límite al número de instancias es techniqueMaxSlots, que es otra cosa.
 */
public final class MindBudget {
    private MindBudget() {}

    /** MIND total del jugador. */
    public static int total(PlayerStatsAttachment att) {
        return att.getAttribute(ZenkaiAttributes.MIND);
    }

    /** MIND ocupada por habilidades + tipos de ki + técnicas físicas. */
    public static int used(PlayerStatsAttachment att) {
        return att.skills().mindUsed() + techniquesUsed(att);
    }

    /**
     * MIND libre. PUEDE SALIR NEGATIVA y no se corrige aquí: pasa cuando un datapack sube los
     * mind_req por debajo de los pies del jugador, y también con las partidas anteriores a
     * este cambio, donde las técnicas no ocupaban nada. Taparlo con un max(0, …) escondería
     * el problema y haría que la pantalla mintiera; quien compra ya lo trata como "no llega",
     * y el jugador ve el déficit en rojo hasta que suba MIND o suelte algo.
     */
    public static int free(PlayerStatsAttachment att) {
        return total(att) - used(att);
    }

    /** ¿Caben {@code cost} puntos más? Con cost <= 0 siempre sí: liberar nunca se rechaza. */
    public static boolean canAfford(PlayerStatsAttachment att, int cost) {
        return cost <= 0 || free(att) >= cost;
    }

    /** MIND ocupada solo por las técnicas (ki + físicas). Lo usa la pantalla para desglosar. */
    public static int techniquesUsed(PlayerStatsAttachment att) {
        PlayerTechniques tech = att.techniques();
        int total = 0;
        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (tech.isUnlocked(t)) total += sane(t.mindReq());
        }
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (tech.isUnlocked(t)) total += sane(t.mindReq());
        }
        return total;
    }

    /**
     * Coste en MIND de un tipo de ki, ya saneado.
     * mindReq() devuelve Integer.MAX_VALUE cuando el TechniqueDef no está cargado (datapack
     * ausente o /reload a medias). Sumar eso desborda el int y el presupuesto se vuelve
     * NEGATIVO, con lo que pasaría a ser comprable — justo lo contrario de lo que quiere
     * ese centinela. Aquí se trata como "no disponible", que es lo que significa.
     */
    public static int costOf(KiTechniqueType t) { return sane(t.mindReq()); }

    public static int costOf(PhysicalTechnique t) { return sane(t.mindReq()); }

    /** ¿Se puede desbloquear esto ahora mismo? Un def ausente nunca. */
    public static boolean canUnlock(PlayerStatsAttachment att, KiTechniqueType t) {
        return t.mindReq() != Integer.MAX_VALUE && canAfford(att, costOf(t));
    }

    public static boolean canUnlock(PlayerStatsAttachment att, PhysicalTechnique t) {
        return t.mindReq() != Integer.MAX_VALUE && canAfford(att, costOf(t));
    }

    private static int sane(int mindReq) {
        return (mindReq <= 0 || mindReq == Integer.MAX_VALUE) ? 0 : mindReq;
    }
}