package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Variantes de estilo de aura por ESTADO del jugador (no por forma). Hoy una: el SSJ1 de un Saiyan con estado Legendario lleva un
 * guiño verde (motas verdes sueltas + un contorno de humo verde tenue) sobre su aura dorada de siempre, para que la sangre
 * Legendaria se note sin cambiar la forma ni inventar un apilado con Ikari (decisión del usuario 2026-11-25, por canon).
 *
 * El estilo de la variante es un JSON propio, {@code zenkai_aura_styles/<aura_type>_legendary.json}; si falta, el tipo cae en
 * su familia por defecto como cualquier aura desconocida. El estado Legendario de los DEMÁS jugadores llega por el attachment de
 * formas ({@link PlayerFormAttachment#isLegendaryState}), que sí se sincroniza a los que te ven.
 */
public final class AuraVariants {
    private AuraVariants() {}

    public static final String LEGENDARY_SUFFIX = "_legendary";

    /**
     * El aura_type a dibujar: el de la forma, o su variante Legendaria si el jugador está en SSJ1 con el estado Legendario.
     * SSJ2/SSJ3 no la llevan (un Legendario no puede usarlas) y SSJ4 conserva su identidad roja.
     */
    public static String of(AbstractClientPlayer p, String auraType) {
        if (!"ssj".equals(auraType)) return auraType;
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        if (!form.isLegendaryState()) return auraType;
        FormDef def = form.auraDef();
        return def != null && FormIds.SSJ1.equals(def.id()) ? auraType + LEGENDARY_SUFFIX : auraType;
    }
}
