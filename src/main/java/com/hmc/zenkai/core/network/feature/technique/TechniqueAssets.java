package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Catálogo de sonidos y sets de animación para el editor de técnicas.
 *
 * NADA está escrito a mano: las listas se descubren del registro por PREFIJO, así que
 * registrar zenkai:ki_attack_charge_7 lo hace aparecer en el editor sin tocar Java. Es la
 * misma idea que el datapack de formas: añadir contenido no debe obligar a recompilar.
 *
 * Se recalcula bajo demanda y se cachea, porque el registro de sonidos está congelado desde
 * que arranca el juego: no puede cambiar a mitad de partida.
 */
public final class TechniqueAssets {
    private TechniqueAssets() {}

    /** Cámbialos aquí si renombras los assets. Lo que empiece así entra en la lista. */
    public static final String CHARGE_PREFIX  = "ki_attack_charge";
    public static final String RELEASE_PREFIX = "ki_attack_release";

    private static List<ResourceLocation> chargeCache;
    private static List<ResourceLocation> releaseCache;

    public static List<ResourceLocation> chargeSounds() {
        if (chargeCache == null) chargeCache = discover(CHARGE_PREFIX);
        return chargeCache;
    }

    public static List<ResourceLocation> releaseSounds() {
        if (releaseCache == null) releaseCache = discover(RELEASE_PREFIX);
        return releaseCache;
    }

    /**
     * Todos los sonidos del mod cuyo path empieza por el prefijo, en orden natural
     * (…_2 antes que …_10, que es lo que un orden alfabético haría mal).
     */
    private static List<ResourceLocation> discover(String prefix) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!Zenkai.MOD_ID.equals(id.getNamespace())) continue;
            if (!id.getPath().startsWith(prefix)) continue;
            out.add(id);
        }
        out.sort(Comparator.comparingInt(TechniqueAssets::trailingNumber)
                .thenComparing(ResourceLocation::getPath));
        return List.copyOf(out);
    }

    /** Número final del path (…_12 -> 12). Integer.MAX_VALUE si no lo lleva: va al final. */
    private static int trailingNumber(ResourceLocation id) {
        String path = id.getPath();
        int i = path.length();
        while (i > 0 && Character.isDigit(path.charAt(i - 1))) i--;
        if (i == path.length()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(path.substring(i));
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    /** ¿Es un id de sonido válido para este hueco? El editor puede mentir; esto no. */
    public static boolean isValidCharge(ResourceLocation id) {
        return id != null && chargeSounds().contains(id);
    }

    public static boolean isValidRelease(ResourceLocation id) {
        return id != null && releaseSounds().contains(id);
    }

    public static SoundEvent soundOf(ResourceLocation id) {
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
    }
}