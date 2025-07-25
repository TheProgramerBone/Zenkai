package com.hmc.db_renewed.util;

public class TPUtils {

    /**
     * Calcula el costo en TP para subir al siguiente punto en una estadística.
     * Ejemplo: si tienes 3 puntos, subir al cuarto cuesta 4 TP.
     */
    public static int getTPCost(int currentLevel) {
        return currentLevel + 1;
    }

    /**
     * Costo total para alcanzar un nivel específico en una stat desde 0.
     * Ejemplo: nivel 3 = 1 + 2 + 3 = 6 TP totales.
     */
    public static int getTotalTPCostForLevel(int level) {
        return level * (level + 1) / 2;
    }
}