package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.TechniqueAnimSet;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rutas de PAL de los sets de animación. Un set N son TRES clips emparejados:
 *   zenkai.ki_attack_N_charge       carga, hold en el último frame
 *   zenkai.ki_attack_N_overcharge   por encima del 100%, hold
 *   zenkai.ki_attack_N_release      disparo, one-shot
 * SOLO CLIENTE: PAL no existe en el servidor.
 * QUÉ SETS EXISTEN ya no se decide aquí: vive en TechniqueAnimSet (común), porque el servidor
 * necesita el origen de cada set para el spawn del proyectil. Esta clase solo construye rutas.
 */
public final class TechniqueAnimSets {
    private TechniqueAnimSets() {}

    private static List<Integer> cache;

    public static List<Integer> available() {
        if (cache == null) {
            List<Integer> out = new ArrayList<>();
            for (int n = 1; n <= TechniqueAnimSet.count(); n++) out.add(n);
            cache = List.copyOf(out);
        }
        return cache;
    }

    public static int clamp(int set) { return TechniqueAnimSet.clamp(set); }

    // El prefijo "zenkai." va DENTRO del path: así se llaman las animaciones en
    // player_animation.animation.json, igual que zenkai.block o zenkai.phys_barrage.
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