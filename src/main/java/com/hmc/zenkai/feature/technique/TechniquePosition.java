package com.hmc.zenkai.feature.technique;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Desde DÓNDE sale la técnica. Es lo que separa un Kamehameha (dos manos a la altura del
 * costado) de un Masenko (frente) o un Makankosappo (dedos, casi a la altura de los ojos).
 *
 * Los offsets son RELATIVOS al jugador, no absolutos: lateral (derecha +), vertical respecto
 * a los ojos y adelante. Así el punto de salida acompaña a la mirada y a la escala de la
 * forma sin cuentas extra en el sitio del disparo.
 *
 * Añadir una posición es añadir una constante aquí y su clave de lang. El ordinal viaja en
 * el packet, así que las nuevas van AL FINAL: insertarlas en medio recolocaría las ya
 * guardadas por los jugadores.
 */
public enum TechniquePosition {
    RIGHT_HAND ( 0.32, -0.22, 0.15),
    LEFT_HAND  (-0.32, -0.22, 0.15),
    BOTH_HANDS ( 0.00, -0.18, 0.28),
    MOUTH      ( 0.00, -0.06, 0.12),
    FOREHEAD   ( 0.00,  0.14, 0.10),
    EYES       ( 0.00,  0.02, 0.10);

    private final double side;
    private final double up;
    private final double forward;

    TechniquePosition(double side, double up, double forward) {
        this.side = side;
        this.up = up;
        this.forward = forward;
    }

    /** Clave de lang: technique.zenkai.position.right_hand, etc. */
    public String langKey() {
        return "technique.zenkai.position." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Nunca falla: un ordinal fuera de rango (datos viejos, packet manipulado) cae a la mano
     *  derecha, que es el comportamiento que había antes de que esto existiera. */
    public static TechniquePosition byOrdinal(int i) {
        TechniquePosition[] all = values();
        return (i < 0 || i >= all.length) ? RIGHT_HAND : all[i];
    }

    /** Punto de salida en el mundo, ya orientado según hacia dónde mira el jugador. */
    public Vec3 origin(Player p) {
        return origin(p, p.position());
    }

    /**
     * Igual, pero partiendo de una posición de pies dada. El render la necesita interpolada
     * (p.getPosition(partialTick)): con la cruda del tick, la bola de carga vibra al andar.
     */
    public Vec3 origin(Player p, Vec3 feet) {
        Vec3 eye = feet.add(0.0, p.getEyeHeight(), 0.0);
        Vec3 look = p.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        double scale = p.getScale(); // ⚠ acompaña a la escala de la forma

        return eye.add(right.scale(side * scale))
                .add(0.0, up * scale, 0.0)
                .add(look.scale(forward * scale));
    }
}