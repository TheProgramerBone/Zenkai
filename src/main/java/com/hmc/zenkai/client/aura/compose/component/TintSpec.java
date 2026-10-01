package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonParseException;
import com.hmc.zenkai.client.aura.compose.AuraFrame;

import java.util.Locale;

/**
 * Color de un componente, en el JSON del estilo: {@code "inner"}, {@code "outer"}, {@code "hot"}
 * (capa caliente del tinte exterior), {@code "white"} o {@code "#RRGGBB"}. Los tres primeros salen
 * de AuraColors cada frame (Kaioken, dos tonos...): el componente sigue el color real del aura.
 */
public record TintSpec(Kind kind, float[] fixed) {

    public enum Kind { INNER, OUTER, HOT, FIXED }

    public static final TintSpec INNER = new TintSpec(Kind.INNER, new float[]{1f, 1f, 1f});
    public static final TintSpec OUTER = new TintSpec(Kind.OUTER, new float[]{1f, 1f, 1f});
    public static final TintSpec HOT = new TintSpec(Kind.HOT, new float[]{1f, 1f, 1f});
    public static final TintSpec WHITE = new TintSpec(Kind.FIXED, new float[]{1f, 1f, 1f});

    public static TintSpec parse(String s) {
        if (s.startsWith("#")) {
            int rgb = Integer.parseInt(s.substring(1), 16);
            return new TintSpec(Kind.FIXED, new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f});
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "inner" -> INNER;
            case "outer" -> OUTER;
            case "hot" -> HOT;
            case "white" -> WHITE;
            default -> throw new JsonParseException("color desconocido \"" + s + "\" (inner, outer, hot, white o #RRGGBB)");
        };
    }

    public float[] of(AuraFrame f) {
        return switch (kind) {
            case INNER -> f.inner;
            case OUTER -> f.outer;
            case HOT -> f.hot;
            case FIXED -> fixed;
        };
    }
}
