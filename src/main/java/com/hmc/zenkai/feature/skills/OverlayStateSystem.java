package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Consumidor de los overlays GENÉRICOS (Ultra Instinct/Ultra Ego "técnica", y cualquier estado
 * futuro con el mismo patrón) — Kaioken NO pasa por aquí, se queda exactamente como estaba
 * (ver {@code KaiokenSystem}/{@code KaiokenTier}, sin tocar).
 *
 * Un overlay genérico es: un {@link SkillToggles.Toggle} encendido (activación/persistencia/
 * sync/rueda, ya existentes) + un {@link OverlayStateDef} cargado (qué hace, según la
 * Transformación primaria activa). Los dos se buscan por el MISMO id — ninguno de los dos
 * necesita al otro para existir de forma segura (ver el javadoc de {@link OverlayStateDef}).
 */
public final class OverlayStateSystem {
    private OverlayStateSystem() {}

    /**
     * Suma del canal {@code "stat_percent"} de cada overlay genérico activo, evaluado contra la
     * forma primaria actual del jugador. Sumando/hermano de {@code formStatPercent()} y
     * {@code kaioken.statPercent()} en {@code MasteryEffects.formStatFactor} — término
     * independiente, no toca los otros dos.
     * Hoy siempre da 0.0 en cualquier partida real: ningún {@link OverlayStateDef} del mod
     * declara todavía ese canal (sin balance de UI/UE definido aún).
     */
    public static double statPercent(Player p) {
        double sum = 0.0;
        ResourceLocation formId = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        for (OverlayStateDef def : OverlayStateDef.all()) {
            if (!SkillToggles.isOn(p, def.id())) continue;
            sum += def.modifier(formId, "stat_percent", 0.0);
        }
        return sum;
    }

    /**
     * El overlay genérico ACTIVO con `aura_rgb` declarado y mayor `priority`, o null si ninguno
     * lo está. {@link com.hmc.zenkai.feature.aura.AuraColors} lo consulta SOLO cuando Kaioken
     * está apagado (Kaioken sigue ganando siempre la capa exterior, sin cambios de
     * comportamiento — ver su rama, que no se toca).
     */
    public static OverlayStateDef highestPriorityAuraOverlay(Player p) {
        OverlayStateDef best = null;
        for (OverlayStateDef def : OverlayStateDef.all()) {
            if (!def.hasAura() || !SkillToggles.isOn(p, def.id())) continue;
            if (best == null || def.priority() > best.priority()) best = def;
        }
        return best;
    }
}
