package com.hmc.zenkai.feature.aura;

import com.hmc.zenkai.feature.alignment.AlignmentTier;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * ÚNICO sitio donde se decide el color de energía de un jugador. Vive en core (no en client)
 * porque el SERVIDOR también lo necesita: las partículas de impacto llevan el tinte dentro
 * del packet, así que se resuelve una vez en el servidor y llega igual al conjunto de clientes.
 * AuraClientState.resolveColor delega aquí: si cambian las prioridades, cambian en los dos
 * sitios a la vez.
 */
public final class AuraColors {
    private AuraColors() {}

    public static final int MAJIN_RGB   = 0xD41A25;
    public static final int KAIOKEN_RGB = 0xE02020;
    // Tinte por AlignmentTier del aura POR DEFECTO (solo si !visual.isCustomAuraColor()).
    // Hex duplicados a propósito respecto a ZenkaiPalette.ALIGN_EVIL/ALIGN_NEUTRAL (client/gui):
    // AuraColors vive en core (el servidor también lo necesita para las partículas de impacto),
    // ZenkaiPalette es de cliente — no cruzar esa frontera con un import. Si cambia uno, cambiar
    // el otro a mano.
    public static final int EVIL_RGB    = 0xD62828; // rojo "malvado": distinto de Kaioken (E02020) y
                                                      // de Majin (D41A25), pero de la misma familia
    public static final int NEUTRAL_RGB = 0x9A9A9A; // gris

    /** Capas del aura: interior (siempre) + exterior envolvente (kaioken sobre forma), o -1. */
    public record Layers(int inner, int outer) {
        public boolean hasOuter() { return outer >= 0; }
    }

    public static Layers resolveLayers(Player p) {
        var visual = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        var form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        FormDef def = form.activeDef();
        boolean kaio = form.getKaioken().isOn();
        // Kaioken SOBRE una forma: rojo por FUERA envolviendo el color de la forma.
        if (kaio && def != null) return new Layers(def.auraRgb(), KAIOKEN_RGB);
        if (kaio) return new Layers(KAIOKEN_RGB, -1);
        if (def != null) return new Layers(def.auraRgb(), -1);
        // Majin solo tiñe el aura en BASE (con forma activa manda la forma).
        if (visual.isMajinControlled()) return new Layers(MAJIN_RGB, -1);
        // Tinte por alineamiento: solo si el jugador no ha fijado un color propio en
        // StyleSelectionScreen. GOOD cae al fallback de abajo sin más — el azul por defecto de
        // visual.getAuraColorRgb() YA lee como "bueno" (mismo azul que ZenkaiPalette.ALIGN_GOOD).
        if (!visual.isCustomAuraColor()) {
            var stats = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            AlignmentTier tier = AlignmentTier.of(stats.getAlignment());
            if (tier == AlignmentTier.EVIL) return new Layers(EVIL_RGB, -1);
            if (tier == AlignmentTier.NEUTRAL) return new Layers(NEUTRAL_RGB, -1);
        }
        return new Layers(visual.getAuraColorRgb(), -1);
    }

    public static int resolve(Player p) {
        Layers l = resolveLayers(p);
        return l.hasOuter() ? l.outer() : l.inner(); // partículas: bajo kaioken, rojo
    }
}