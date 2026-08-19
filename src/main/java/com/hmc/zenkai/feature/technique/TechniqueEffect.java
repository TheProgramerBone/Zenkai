package com.hmc.zenkai.feature.technique;

import java.util.Locale;

/**
 * Efecto añadido de una técnica de ki. UNO SOLO por técnica, nunca combinables.
 * POR QUÉ UN ENUM Y NO CASILLAS SUELTAS
 * -------------------------------------
 * Antes era un boolean `explosive`. Con casillas independientes el editor se convierte en una
 * hoja de cálculo, las combinaciones hay que balancearlas una a una, y una técnica "that has all"
 * no tiene identidad. Con un selector único cada técnica dice una cosa.
 * QUÉ HACE HOY EXPLOSIVE
 * ----------------------
 * SOLO romper bloques en el radio de la explosión. El daño en ÁREA ya no depende de la marca:
 * toda técnica que impacta lo reparte según el aoeFactor de su tipo. Por eso el recargo baja de
 * ×1.5 a ×1.15 — destruir terreno no vale lo que valía "área + terreno".
 * Consecuencia: con la gamerule de griefing apagada este efecto NO HACE NADA, y por eso el
 * editor debe deshabilitarlo en ese caso en vez de cobrarlo (ver entrega 2).
 * AÑADIR UN EFECTO = una constante aquí, su clave de lang, su color de marco y su rama de
 * comportamiento. No se declaran efectos sin implementar: una opción que no hace nada en el
 * editor es peor que no tenerla.
 * El ORDINAL viaja en NBT y en el packet: las nuevas van AL FINAL.
 */
public enum TechniqueEffect {

    NONE(1.00, 0),

    /** Rompe bloques en el radio de la explosión. Prohibido en disco, barrera y ráfaga. */
    EXPLOSIVE(1.15, 0xFFE08A2B);

    private final double costMult;
    private final int borderRgb;

    TechniqueEffect(double costMult, int borderRgb) {
        this.costMult = costMult;
        this.borderRgb = borderRgb;
    }

    /** Multiplicador sobre el coste de ki del disparo. */
    public double costMult() { return costMult; }

    /** Color del marco de la casilla en el HUD y en la barra de asignación. 0 = sin marco.
     *  Vive aquí y no en la GUI porque es identidad del efecto, no decoración de una pantalla. */
    public int borderRgb() { return borderRgb; }

    public String langKey() {
        return "technique.zenkai.effect." + name().toLowerCase(Locale.ROOT);
    }

    /** Nunca falla: un ordinal fuera de rango (NBT viejo, packet manipulado) cae a NONE. */
    public static TechniqueEffect byOrdinal(int i) {
        TechniqueEffect[] all = values();
        return (i < 0 || i >= all.length) ? NONE : all[i];
    }
}