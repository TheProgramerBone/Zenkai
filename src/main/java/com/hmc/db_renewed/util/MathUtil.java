package com.hmc.db_renewed.util;

public class MathUtil {
    public static int clamp(int v, int min, int max){ return Math.max(min, Math.min(max, v)); }
    public static double safeDiv(double a, double b){ return b == 0 ? 0 : a / b; }
}