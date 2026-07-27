package com.hmc.zenkai.feature.race;

/**
 * Códigos de pelo procedimental. En 1.0 SOLO se valida el sobre (versión, longitud, checksum):
 * el creador y el renderer llegan después. Existe ya para que los saves y el packet de visual
 * nazcan con el formato definitivo y no haya migración cuando se implemente.
 *
 * Formato: zh1.<header>.<mechón>;<mechón>...[#<variante>][~<checksum>]
 * Regla de oro: campos desconocidos se IGNORAN, y el string crudo se guarda tal cual.
 */
public final class HairCode {
    private HairCode() {}

    public static final String PREFIX = "custom:";
    public static final String VERSION = "zh1";

    public static final int MAX_LENGTH = 1024;
    public static final int MAX_STRANDS = 48;
    public static final int MAX_SEGMENTS = 8;

    /** ¿Este hairStyleId es un código procedimental (y no un preset del registro)? */
    public static boolean isCustom(String hairStyleId) {
        return hairStyleId != null && hairStyleId.startsWith(PREFIX);
    }

    /** Validación de SOBRE, la que corre en el servidor al recibir el packet. No interpreta
     *  geometría a propósito: el servidor no necesita saber cómo es el pelo, solo que no es
     *  un ataque. */
    public static boolean isWellFormed(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH) return false;
        String body = raw.startsWith(PREFIX) ? raw.substring(PREFIX.length()) : raw;
        if (!body.startsWith(VERSION + ".")) return false;
        int strands = countStrands(body);
        return strands > 0 && strands <= MAX_STRANDS;
    }

    private static int countStrands(String body) {
        int cut = body.indexOf('#');
        if (cut < 0) cut = body.indexOf('~');
        String core = cut < 0 ? body : body.substring(0, cut);
        int dot = core.indexOf('.', VERSION.length() + 1);
        if (dot < 0) return 0;
        String list = core.substring(dot + 1);
        if (list.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < list.length(); i++) if (list.charAt(i) == ';') n++;
        return n;
    }
}