package com.hmc.zenkai.feature.aura;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * ADAPTADOR entre el jugador y el aura.
 *   PlayerStatsAttachment -> AuraState -> (C_v2 + Δforma + Δraza) -> AuraProfile
 * Aquí NO hay aritmética: vive entera en AuraFormula, que no importa nada del juego y por
 * eso se puede verificar con AuraSelfTest sin arrancar Minecraft. Esta clase solo saca
 * números del Player y los pasa. Si hay que tocar una fórmula va en AuraTuning; si hay
 * que tocar cómo se combinan, en AuraFormula.
 */
public final class AuraManager {
    private AuraManager() {}

    /** Intensidad de Kaioken, 0..1. Normalizada sobre el statPercent del propio enum y
     *  NO sobre la etiqueta: "x20" es el nombre del escalón, no un multiplicador de
     *  stats (su statPercent real es 1.0, o sea +100%). Derivarla del enum hace que
     *  añadir o retocar un escalón reajuste la escala solo. */
    public static float kaiokenIntensity(KaiokenTier tier) {
        if (tier == null || !tier.isOn()) return 0f;
        double max = AuraCeiling.maxKaiokenPercent();
        return max <= 0d ? 0f : AuraTuning.clamp01((float) (tier.statPercent() / max));
    }

    public static AuraState stateOf(Player p, float turbo) {
        if (p == null) return AuraState.IDLE;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        int maxEnergy = Math.max(1, att.getEnergyMax());
        float kiFraction = att.getEnergy() / (float) maxEnergy;

        return AuraFormula.state(
                att.getApparentPowerLevel(),
                AuraCeiling.floor(),
                AuraCeiling.ceiling(),
                att.getPowerPercent(),
                SkillEffects.maxPowerPercent(att),
                att.skills().level(SkillEffects.KI_CONTROL),
                kiFraction,
                kaiokenIntensity(form.getKaioken()),
                turbo);
    }

    /**
     * Modificador combinado del jugador: el de su forma activa más el de su raza.
     * Se suman los offsets y se multiplican las ganancias; el clamp final lo hace
     * AuraProfile, así que dos empujones legítimos en el mismo eje no se recortan a
     * mitad de camino.
     */
    public static AuraModifier modifiersOf(Player p) {
        if (p == null) return AuraModifier.NONE;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        Race race = att.raceStats().getRace();
        AuraModifier out = RaceSignature.of(race);

        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        FormDef def = form.activeDef();
        if (def != null) out = out.plus(formModifier(def));
        return out;
    }

    /**
     * Modificador de una forma. Hoy devuelve NONE: FormDef todavía no lleva estos
     * campos. Se deja aislado en una forma para que añadirlos en L6 sea tocar aquí y
     * el codec del datapack, y nada más.
         * DEUDA EXPLÍCITA DE L6, no una implementación provisional escondida. Hasta
     * entonces la forma sigue influyendo por la vía del PL -> presence, así que un
     * SSJ4 ya se ve distinto de un base; lo que le falta es firma ESTRUCTURAL propia.
     * No se debe compensar metiendo dMass/dSpike en el renderer "para que se note":
     * eso saltaría por encima de la arquitectura y la neutralidad dejaría de estar
     * garantizada por construcción.
         * El campo `aura_type` de los diez JSON NO se elimina: en L6 pasa a identificar
     * el AuraModifier registrado (aura_type -> AuraModifier -> AuraProfile), así que
     * no hay campo muerto ni migración de datapacks que hacer.
     */
    public static AuraModifier formModifier(FormDef def) {
        return AuraModifier.NONE;
    }

    public static AuraProfile profileOf(Player p, float turbo) {
        AuraState st = stateOf(p, turbo);
        if (!st.isVisible()) return AuraProfile.OFF;
        return AuraFormula.profile(st, modifiersOf(p));
    }
}