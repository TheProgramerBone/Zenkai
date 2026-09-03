package com.hmc.zenkai.feature.aura;

/**
 * Estado ENERGÉTICO del jugador. Deliberadamente no sabe nada de texturas, colores,
 * razas, formas ni render: si algo de eso apareciera aquí estaríamos mezclando el
 * gameplay con su representación, y la firma racial podría empezar a mentir sobre el
 * poder por la puerta de atrás.
 *
 * Los cinco ejes salen de datos que YA existen y YA viajan a los trackers cada tick
 * (PlayerLifeCycle.syncIfServer manda el CompoundTag entero de stats), así que este
 * sistema no necesita un solo packet nuevo:
 *
 *   release   powerPercent / 100                cuánta energía estás liberando
 *   tension   powerPercent / maxPowerPercent    lo cerca que estás de tu límite
 *   refine    nivel(ki_control) / 10            lo bien que la contienes
 *   energy    ki actual / ki máximo             cuánta te queda
 *   presence  log10 normalizado del PL APARENTE cuánta hay en total
 *
 * presence usa el PL APARENTE, no el real: un jugador suprimiendo su ki debe verse
 * débil aunque tenga millones de PL. Eso es intencional y encaja con el scouter.
 *
 * NOTA sobre pulseHz/pulseAmp: NO están aquí. El pulso final depende de pulseHzGain/
 * pulseAmpGain, que son de un AuraModifier; si vivieran en AuraState, AuraState tendría
 * que conocer la raza. Viven en AuraProfile, que es donde se resuelve la representación.
 */
public record AuraState(
        float release,
        float tension,
        float refine,
        float energy,
        float presence,
        float chaos,
        float chaosVisual,
        float instability,
        float kaiokenIntensity,
        float turbo
) {
    public static final AuraState IDLE =
            new AuraState(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

    /** Constructor compacto: clampa. Ninguna consumidora debe defenderse de basura. */
    public AuraState {
        release = AuraTuning.clamp01(release);
        tension = AuraTuning.clamp01(tension);
        refine = AuraTuning.clamp01(refine);
        energy = AuraTuning.clamp01(energy);
        presence = AuraTuning.clamp01(presence);
        chaos = AuraTuning.clamp01(chaos);
        chaosVisual = AuraTuning.clamp01(chaosVisual);
        instability = AuraTuning.clamp01(instability);
        kaiokenIntensity = AuraTuning.clamp01(kaiokenIntensity);
        turbo = AuraTuning.clamp01(turbo);
    }

    /**
     * ¿Hay algo que dibujar? presence NO entra en la cuenta.
     * Exigirla apagaba el aura de personaje recién creado: PL_FLOOR se derivó del
     * PL sin suprimir (231) pero presence compara contra el PL APARENTE, y un novato
     * arranca con powerPercent 50, o sea aparentando 122. Caía por debajo del suelo,
     * presence salía 0 y el aura no se dibujaba. Lo mismo con el poder al 0% cargando
     * ki, que debe expulsar aura precisamente porque está cargando.
     */
    public boolean isVisible() {
        return energy > 0.001f;
    }
}