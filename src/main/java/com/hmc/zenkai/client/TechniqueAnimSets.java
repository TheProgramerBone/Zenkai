package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Sets de animación de técnica disponibles. Un set N son DOS animaciones emparejadas:
 * zenkai.ki_attack_N_charge y zenkai.ki_attack_N_release. El editor elige el número; la
 * carga y el disparo van siempre juntos.
 * Se descubren SONDEANDO el registro de PAL, igual que los sonidos se descubren del registro
 * de sonidos: modelar un set nuevo no debe obligar a tocar Java. El sondeo no se corta en el
 * primer hueco a propósito (si borras el 3 y tienes el 4, el 4 sigue apareciendo).
 * SOLO CLIENTE: PAL no existe en el servidor. El servidor guarda el número y no lo interpreta;
 * si el set no existe en un cliente, ese cliente no reproduce animación y ya.
 */
public final class TechniqueAnimSets {
    private TechniqueAnimSets() {}

    /**
     * Sets modelados, A MANO. PAL (fork de zigythebird) no expone consultar el registro por
     * id, así que el sondeo no es viable: al modelar zenkai.ki_attack_3_charge/release, sube
     * este número a 3. Es el ÚNICO sitio que tocar.
     */
    private static final int MAX_SETS = 2;

    private static List<Integer> cache;

    public static List<Integer> available() {
        if (cache == null) {
            List<Integer> out = new ArrayList<>();
            for (int n = 1; n <= Math.max(1, MAX_SETS); n++) out.add(n);
            cache = List.copyOf(out);
        }
        return cache;
    }

    /** Encaja cualquier número guardado dentro de lo que existe hoy. */
    public static int clamp(int set) {
        return (set < 1 || set > MAX_SETS) ? 1 : set;
    }

    public static ResourceLocation charge(int set) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_attack_" + set + "_charge");
    }

    public static ResourceLocation release(int set) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_attack_" + set + "_release");
    }

}