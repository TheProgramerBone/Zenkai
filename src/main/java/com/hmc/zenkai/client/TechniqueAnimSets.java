package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Sets de animación de técnica. Un set N son TRES animaciones emparejadas:
 *   zenkai.ki_attack_N_charge       carga, hold en el último frame
 *   zenkai.ki_attack_N_overcharge   por encima del 100%, hold
 *   zenkai.ki_attack_N_release      disparo, one-shot
 * SOLO CLIENTE: PAL no existe en el servidor. El servidor guarda el número y no lo
 * interpreta; si el set no existe en un cliente, ese cliente cae al 1.
 */
public final class TechniqueAnimSets {
    private TechniqueAnimSets() {}

    /**
     * Sets modelados, A MANO. PAL (fork de zigythebird) no expone consultar el registro por
     * id, así que sondear no es viable: al modelar el set 3 completo, sube este número a 3.
     * Es el ÚNICO sitio que tocar.
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

    /** Cualquier número guardado cae dentro de lo que existe hoy. Un set inexistente anima
     *  como el 1: que una técnica no se anime es peor que animarse distinto. */
    public static int clamp(int set) {
        return (set < 1 || set > MAX_SETS) ? 1 : set;
    }

    // El prefijo "zenkai." va DENTRO del path: así se llaman las animaciones en
    // player_animation.animation.json, igual que zenkai.block o zenkai.phys_barrage.
    // Sin él los ids nunca casaban y los sets no habrían disparado nunca.
    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    public static ResourceLocation charge(int set)     { return rl("zenkai.ki_attack_" + clamp(set) + "_charge"); }
    public static ResourceLocation overcharge(int set) { return rl("zenkai.ki_attack_" + clamp(set) + "_overcharge"); }
    public static ResourceLocation release(int set)    { return rl("zenkai.ki_attack_" + clamp(set) + "_release"); }

    /** BARRIER ignora la carga y tiene una animación única, sin par charge/release. */
    public static final ResourceLocation BARRIER = rl("zenkai.ki_barrier");

    /** Subir ki (tecla C). Nada que ver con cargar una técnica. */
    public static final ResourceLocation KI_CHARGE_START = rl("zenkai.ki_charge_start");
    public static final ResourceLocation KI_CHARGE_LOOP  = rl("zenkai.ki_charge");
}