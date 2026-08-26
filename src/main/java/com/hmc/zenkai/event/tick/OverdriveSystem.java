package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.OverdriveTuning;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Banda de powerPercent por encima de 100% ("forzar"): GENÉRICA, no exclusiva de ninguna raza.
 * El techo y el coste de forzar dependen de la forma que el jugador lleve puesta AHORA MISMO
 * (activeDef, ya resuelto por FormSystem para el drenaje normal — se reusa el mismo lookup en
 * vez de volver a consultar FormRegistry aquí), nunca de formas simplemente compradas.
 *
 * No toca formId ni transforma nada: solo drena ki mientras powerPercent > 100 y, si el ki se
 * agota, baja la banda a 100 (no fuerza base — eso es cosa de las formas de verdad).
 */
public final class OverdriveSystem {
    private OverdriveSystem() {}

    private static final ResourceLocation OVERDRIVE_SCALE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "overdrive_scale");

    /** Techo de forzar para el jugador AHORA MISMO: genérico + bonus de la forma puesta. */
    public static double ceilingFor(FormDef activeDef) {
        double bonus = activeDef == null ? 0.0 : activeDef.overdriveCeilingBonus();
        return OverdriveTuning.ceiling(bonus);
    }

    /** Multiplicador de coste de forzar de la forma puesta, ya interpolado por su maestría.
     *  1.0 si no hay forma activa o no declara estos campos. */
    private static double drainMultFor(FormDef activeDef, float mastery) {
        return activeDef == null ? 1.0 : activeDef.overdriveDrainMult(mastery);
    }

    /** Drena ki si powerPercent > 100. activeDef = la misma forma ya resuelta por
     *  FormSystem.tick() para el drenaje de forma normal (null en Base). */
    public static void tick(TickCtx c, FormDef activeDef) {
        PlayerStatsAttachment att = c.att();
        int pct = att.getPowerPercent();
        applyOverdriveScale(c.p(), pct);
        if (pct <= 100) return;

        double over = pct - 100;
        double mult = drainMultFor(activeDef, c.form().activeMastery());
        double drain = OverdriveTuning.costPerTick(over, mult);
        if (drain <= 0.0) return;

        int before = att.getKiCurrent();
        att.addKi(-drain);
        if (before > 0 && att.getKiCurrent() <= 0) {
            // Sin ki no se puede seguir forzando: cae a la banda sostenible (100), no a Base —
            // esto es del %, la forma en sí ni se toca.
            att.setPowerPercent(100, (int) Math.round(ceilingFor(activeDef)));
        }
    }

    /**
     * Crecimiento LIGERO de escala mientras se fuerza (aparte del de la forma —
     * FormSystem.applyFormScale usa su propio id, este es un SEGUNDO modificador independiente
     * sobre el mismo atributo, se suman). 0 en pct&lt;=100 a propósito: nada de crecimiento
     * durante el temblor previo a romper el límite, solo una vez el % sube de verdad.
     */
    private static void applyOverdriveScale(Player p, int pct) {
        AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
        if (scaleAttr == null) return;
        double amount = OverdriveTuning.overdriveScaleBonus(Math.max(0, pct - 100));
        AttributeModifier current = scaleAttr.getModifier(OVERDRIVE_SCALE_MOD_ID);
        if (amount <= 0.0) {
            if (current != null) scaleAttr.removeModifier(OVERDRIVE_SCALE_MOD_ID);
            return;
        }
        if (current == null || current.amount() != amount) {
            scaleAttr.removeModifier(OVERDRIVE_SCALE_MOD_ID);
            scaleAttr.addTransientModifier(new AttributeModifier(
                    OVERDRIVE_SCALE_MOD_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
