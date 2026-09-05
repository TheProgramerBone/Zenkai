package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.config.ServerConfig;

/**
 * Curva de coste de atributos. ÚNICO sitio donde vive la fórmula.
 * La forma es lineal: el punto número n cuesta {@code b + m*n}, con b = attribute_base_cost
 * y m = tp_coefficient. Comprar un bloque de {@code add} puntos partiendo de {@code inv}
 * invertidos se resuelve en O(1) con la suma de la progresión aritmética, no con un bucle:
 * un jugador puede pedir 10.000 puntos de golpe y el bucle se notaría en el tick del servidor.
 * Lee {@link ServerConfig#attributeBaseCost()}/{@link ServerConfig#tpCoefficient()} directamente
 * en los dos lados: al ser ModConfig.Type.SERVER, NeoForge sincroniza el valor real al cliente
 * al conectarse, así que la pantalla de stats y el cobro del servidor ven siempre el mismo
 * número. Hasta 2026-09-04 estos dos valores vivían en CommonConfig (Type.COMMON, sin
 * sincronizar) y hacía falta un packet a medida (TpCurveSyncPacket, ya borrado) para que el
 * cliente no calculara el coste con su propia copia local desincronizada del servidor.
 */
public final class TpCurve {
    private TpCurve() {}

    /** b de la recta: lo que cuesta el primer punto. */
    public static double base() {
        return ServerConfig.attributeBaseCost();
    }

    /** m de la recta: cuánto encarece cada punto ya invertido. */
    public static double coeff() {
        return ServerConfig.tpCoefficient();
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
