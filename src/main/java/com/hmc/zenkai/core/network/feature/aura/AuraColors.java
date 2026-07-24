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

    /** Capas del aura: interior (siempre) + exterior envolvente (kaioken sobre forma), o -1. */
    public record Layers(int inner, int outer) {
        public boolean hasOuter() { return outer >= 0; }
    }

    public static Layers resolveLayers(Player p) {
        var visual = p.getData(DataAttachments.PLAYER_VISUAL.get());
        var form = p.getData(DataAttachments.PLAYER_FORM.get());
        FormDef def = form.activeDef();
        boolean kaio = form.getKaioken().isOn();
        // Kaioken SOBRE una forma: rojo por FUERA envolviendo el color de la forma.
        if (kaio && def != null) return new Layers(def.auraRgb(), KAIOKEN_RGB);
        if (kaio) return new Layers(KAIOKEN_RGB, -1);
        if (def != null) return new Layers(def.auraRgb(), -1);
        // Majin solo tiñe el aura en BASE (con forma activa manda la forma).
        if (visual.isMajinControlled()) return new Layers(MAJIN_RGB, -1);
        return new Layers(visual.getAuraColorRgb(), -1);
    }

    public static int resolve(Player p) {
        Layers l = resolveLayers(p);
        return l.hasOuter() ? l.outer() : l.inner(); // partículas: bajo kaioken, rojo
    }
}