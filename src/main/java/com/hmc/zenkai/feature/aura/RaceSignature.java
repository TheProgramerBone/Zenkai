package com.hmc.zenkai.feature.aura;

import com.hmc.zenkai.feature.Race;

import java.util.EnumMap;
import java.util.Map;

/**
 * Firma energética por raza. Dos jugadores con el mismo Power Level, el mismo tamaño de
 * aura y el mismo color se ven DISTINTOS — pero ninguno se ve más poderoso.
 *
 * Los valores están calibrados: se midió la energía luminosa total del render de cada
 * raza con estado, tinte, size, alpha y core idénticos, promediando seis semillas de
 * textura. Resultado final: Saiyan −2.1%, Human +3.2%, Namekian +2.9%, Arcosian −4.0%,
 * Majin −0.0%. Rango total 7.4% con un suelo de medición de 3.9%.
 *
 * SOBRE LA CALIBRACIÓN, porque volverá a hacer falta: el primer intento de igualar las
 * cinco NO convergió — oscilaba entre ±3% y ±11%. La causa es que la semilla de la
 * silueta depende de los parámetros, así que tocar dMass no ajusta la forma: la
 * REGENERA entera. Cualquier ajuste fino futuro tiene que promediar varias semillas o
 * estará midiendo ruido en lugar de señal.
 *
 * Dónde vive cada cosa: los d* dan la identidad estructural (siempre visible) y los
 * *Gain solo se notan al perder el control. Por eso cada raza se descompone de una
 * manera reconocible cuando se le va el ki, que es donde la firma se lee mejor.
 */
public final class RaceSignature {
    private RaceSignature() {}

    private static final Map<Race, AuraModifier> SIGNATURES = new EnumMap<>(Race.class);

    static {
        // Saiyan — agresiva y pulsante. Puntas marcadas y, pulseGain: es la
        // raza cuya aura late. Encaja con que sea la dueña del Kaioken.
        SIGNATURES.put(Race.SAIYAN, new AuraModifier(
                0.01f, 0.10f, 0.04f, 0.00f, 0.00f, 0.00f,
                1.10f, 1.00f, 1.30f));

        // Human — compacta y estable. Poca dispersión y ganancias por debajo de 1: es la
        // que mejor aguanta cuando pierde el control. Su virtud es no romperse.
        SIGNATURES.put(Race.HUMAN, new AuraModifier(
                0.05f, 0.00f, -0.05f, -0.06f, 0.00f, -0.05f,
                0.80f, 0.75f, 1.00f));

        // Namekian — con cuerpo, movimiento suave. Más masa, menos punta, más baja.
        // Todas las ganancias por debajo de 1: es la aura que menos se altera.
        SIGNATURES.put(Race.NAMEKIAN, new AuraModifier(
                0.07f, -0.10f, -0.06f, 0.00f, -0.06f, 0.00f,
                0.75f, 0.80f, 0.80f));

        // Arcosian — afilada y concentrada. Menos masa pero más densidad y más alta:
        // pegada al cuerpo y elegante en vez de explosiva. spreadGain 0.70 la mantiene
        // recogida incluso descontrolada.
        SIGNATURES.put(Race.ARCOSIAN, new AuraModifier(
                -0.04f, 0.09f, 0.00f, -0.07f, 0.04f, 0.13f,
                1.00f, 0.70f, 1.00f));

        // Majin — orgánica y turbulenta. Poca punta, mucha turbulencia y dispersión, y
        // las ganancias más altas del juego: en reposo es solo blanda, descontrolada se
        // desintegra. Es la raza que peor esconde que está al límite.
        SIGNATURES.put(Race.MAJIN, new AuraModifier(
                0.00f, -0.08f, 0.09f, 0.07f, 0.00f, 0.00f,
                1.30f, 1.25f, 1.15f));
    }

    /** NONE si la raza es null (jugador sin raza elegida todavía). */
    public static AuraModifier of(Race race) {
        return race == null ? AuraModifier.NONE
                : SIGNATURES.getOrDefault(race, AuraModifier.NONE);
    }

    /**
     * Sustituye una firma. Existe para que un datapack pueda redefinirlas más adelante
     * (L6) sin que nada más tenga que enterarse. Un valor null vuelve a NONE, nunca
     * deja el mapa en un estado a medias.
     */
    public static void replace(Race race, AuraModifier mod) {
        if (race == null) return;
        SIGNATURES.put(race, mod == null ? AuraModifier.NONE : mod);
    }
}