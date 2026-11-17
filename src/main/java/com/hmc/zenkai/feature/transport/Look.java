package com.hmc.zenkai.feature.transport;

/**
 * Aspecto de UNA cara de un transporte. Es lo único que el cliente necesita para pintar y para
 * calcular la forma, y viaja empaquetado (3 bits por cara) en el block entity, no en el blockstate:
 * así cada bloque tiene UN solo estado, como los transmisores de Mekanism.
 *
 * Cables y tuberías: OFF (sin brazo), NORMAL, PUSH (brazo con punta) o PULL (brazo con collar).
 * Tanques: NORMAL (cara lisa), PUSH (placa de salida), PULL (placa de entrada) o NONE (placa apagada).
 */
public enum Look {
    OFF, NORMAL, PUSH, PULL, NONE;

    public static Look of(SideMode mode) {
        return switch (mode) {
            case NORMAL -> NORMAL;
            case PUSH -> PUSH;
            case PULL -> PULL;
            case NONE -> NONE;
        };
    }

    /** Sufijo del nombre de las piezas del modelo compuesto (ver ModBlockStateProvider). */
    public String part() { return name().toLowerCase(java.util.Locale.ROOT); }

    // ── Empaquetado: 3 bits por cara, en el orden de Direction.get3DDataValue() ──

    public static int pack(Look[] looks) {
        int bits = 0;
        for (int i = 0; i < 6; i++) bits |= looks[i].ordinal() << (3 * i);
        return bits;
    }

    public static Look unpack(int bits, int index) {
        int o = (bits >> (3 * index)) & 7;
        Look[] all = values();
        return o < all.length ? all[o] : OFF;
    }
}
