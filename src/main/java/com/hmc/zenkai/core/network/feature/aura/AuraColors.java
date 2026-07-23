package com.hmc.zenkai.core.network.feature.aura;

import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * ÚNICO sitio donde se decide el color de energía de un jugador. Vive en core (no en client)
 * porque el SERVIDOR también lo necesita: las partículas de impacto llevan el tinte dentro
 * del packet, así que se resuelve una vez en el servidor y llega igual a todos los clientes.
 * AuraClientState.resolveColor delega aquí: si cambian las prioridades, cambian en los dos
 * sitios a la vez.
 */
public final class AuraColors {
    private AuraColors() {}

    public static final int MAJIN_RGB   = 0xD41A25;
    public static final int KAIOKEN_RGB = 0xE02020;

    public static int resolve(Player p) {
        var visual = p.getData(DataAttachments.PLAYER_VISUAL.get());
        var form = p.getData(DataAttachments.PLAYER_FORM.get());
        // El kaioken MANDA sobre la transformación: si está activo, su rojo gana.
        if (form.getKaioken().isOn()) return KAIOKEN_RGB;
        if (visual.isMajinControlled()) return MAJIN_RGB;
        // La forma activa define su aura en el datapack; en base, el color del jugador.
        FormDef def = form.activeDef();
        if (def != null) return def.auraRgb();
        return visual.getAuraColorRgb();
    }
}