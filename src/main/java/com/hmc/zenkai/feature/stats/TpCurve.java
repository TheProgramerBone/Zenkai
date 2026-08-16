package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.config.CommonConfig;

/**
 * Curva de coste de atributos. ÚNICO sitio donde vive la fórmula.
 * La forma es lineal: el punto número n cuesta {@code b + m*n}, con b = attribute_base_cost
 * y m = tp_coefficient. Comprar un bloque de {@code add} puntos partiendo de {@code inv}
 * invertidos se resuelve en O(1) con la suma de la progresión aritmética, no con un bucle:
 * un jugador puede pedir 10.000 puntos de golpe y el bucle se notaría en el tick del servidor.
 * POR QUÉ NO SE LEE CommonConfig DIRECTAMENTE DESDE LA PANTALLA:
 * CommonConfig es de tipo COMMON, y NeoForge NO envía las configs COMMON al cliente. En un
 * servidor dedicado con la curva retocada, el cliente calculaba el precio con SU archivo y
 * la pantalla de stats mostraba un número que no era el que iba a cobrar el servidor: el
 * botón se veía asequible, el packet se rechazaba y el jugador no tenía forma de saber por
 * qué. Aquí el servidor sigue leyendo la config y el cliente usa lo que le manda el servidor
 * (TpCurveSyncPacket, mismo canal que las SkillDef).
 * El servidor NUNCA recibe el packet, así que {@code synced} se queda en false y lee la
 * config, que es la autoridad real. En un mundo local ambos coinciden por construcción.
 */
public final class TpCurve {
    private TpCurve() {}

    private static volatile boolean synced = false;
    private static volatile double syncedBase  = 1.0;
    private static volatile double syncedCoeff = 1.0;

    /** b de la recta: lo que cuesta el primer punto. */
    public static double base() {
        return synced ? syncedBase : CommonConfig.attributeBaseCost();
    }

    /** m de la recta: cuánto encarece cada punto ya invertido. */
    public static double coeff() {
        return synced ? syncedCoeff : CommonConfig.tpCoefficient();
    }

    /** Lo llama TpCurveSyncPacket en el cliente. No tiene efecto en el servidor. */
    public static void adopt(double b, double m) {
        syncedBase  = b;
        syncedCoeff = m;
        synced      = true;
    }

    /**
     * Coste total de comprar {@code add} puntos partiendo de {@code inv} invertidos.
     * UN SOLO redondeo al final, a propósito: redondear punto a punto hace que comprar de
     * uno en uno cueste más que comprar en bloque, y eso es una trampa para el jugador que
     * usa el botón x1.
     */
    public static int cost(int inv, int add) {
        if (add <= 0) return 0;
        double total = add * (base() + coeff() * (inv + (add - 1) / 2.0));
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(total));
    }

    /**
     * Coste acumulado EXACTO (sin redondear) de los primeros n puntos. Solo para el reparto
     * proporcional del reembolso: ahí el redondeo es justo lo que abría el exploit de TP
     * infinito devolviendo punto a punto lo comprado en bloque.
     */
    public static double theoretical(int n) {
        if (n <= 0) return 0.0;
        return n * (base() + coeff() * (n - 1) / 2.0);
    }
}