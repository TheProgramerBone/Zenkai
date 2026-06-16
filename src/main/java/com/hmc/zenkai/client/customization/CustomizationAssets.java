package com.hmc.zenkai.client.customization;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scanner central de assets de customización.
 *
 * Convención de nombres:
 *   assets/zenkai/textures/customization/eyes/eyes_1.png
 *   assets/zenkai/textures/customization/mouth/mouth_1.png
 *   assets/zenkai/textures/customization/nose/nose_1.png
 *   assets/zenkai/textures/customization/hair/hair_1.png
 *
 * El índice 0 siempre es "ninguno" (sin ojos overlay, calvo, etc.)
 * Los archivos empiezan desde _1.
 *
 * Llamar a reload() una vez al iniciar el cliente o al detectar cambio de resource pack.
 */
public final class CustomizationAssets {

    private CustomizationAssets() {}

    private static final String MOD_ID = "zenkai";

    // Listas cacheadas — índice 0 = null (ninguno), índice 1+ = assets reales
    private static final List<ResourceLocation> EYES  = new ArrayList<>();
    private static final List<ResourceLocation> MOUTH = new ArrayList<>();
    private static final List<ResourceLocation> NOSE  = new ArrayList<>();
    private static final List<ResourceLocation> HAIR  = new ArrayList<>();

    static {
        reload();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Lista de texturas de ojos. Índice 0 = null (sin overlay). */
    public static List<ResourceLocation> eyes()  { return EYES; }

    /** Lista de texturas de boca. Índice 0 = null (sin overlay). */
    public static List<ResourceLocation> mouth() { return MOUTH; }

    /** Lista de texturas de nariz. Índice 0 = null (sin overlay). */
    public static List<ResourceLocation> nose()  { return NOSE; }

    /** Lista de texturas de pelo. Índice 0 = null (sin pelo / calvo). */
    public static List<ResourceLocation> hair()  { return HAIR; }

    /** Número de opciones de ojos (incluyendo "ninguno"). */
    public static int eyesCount()  { return EYES.size(); }
    public static int mouthCount() { return MOUTH.size(); }
    public static int noseCount()  { return NOSE.size(); }
    public static int hairCount()  { return HAIR.size(); }

    /**
     * Devuelve la textura para el índice dado, o null si es 0 (ninguno).
     * Seguro ante índices fuera de rango.
     */
    public static ResourceLocation getEye(int idx) {
        return safeGet(EYES, idx);
    }
    public static ResourceLocation getMouth(int idx) {
        return safeGet(MOUTH, idx);
    }
    public static ResourceLocation getNose(int idx) {
        return safeGet(NOSE, idx);
    }
    public static ResourceLocation getHair(int idx) {
        return safeGet(HAIR, idx);
    }

    /** Nombre legible para mostrar en la GUI (ej. "Eyes 2", "None"). */
    public static String eyeLabel(int idx)  { return label("Eyes",  idx); }
    public static String mouthLabel(int idx){ return label("Mouth", idx); }
    public static String noseLabel(int idx) { return label("Nose",  idx); }
    public static String hairLabel(int idx) { return label("Hair",  idx); }

    // ── Recarga ───────────────────────────────────────────────────────────────

    /**
     * Recarga todos los assets desde el ResourceManager.
     * Llamar desde ClientTickEvent la primera vez, o en ResourceReloadEvent.
     */
    public static void reload() {
        EYES.clear();
        MOUTH.clear();
        NOSE.clear();
        HAIR.clear();

        // Índice 0 siempre es "ninguno"
        EYES.add(null);
        MOUTH.add(null);
        NOSE.add(null);
        HAIR.add(null);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return;

        EYES.addAll( scan(mc, "textures/customization/eyes",  "eyes_"));
        MOUTH.addAll(scan(mc, "textures/customization/mouth", "mouth_"));
        NOSE.addAll( scan(mc, "textures/customization/nose",  "nose_"));
        HAIR.addAll( scan(mc, "textures/customization/hair",  "hair_"));
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private static List<ResourceLocation> scan(Minecraft mc, String folder, String prefix) {
        String fullPrefix = MOD_ID + ":" + folder + "/" + prefix;
        return mc.getResourceManager()
                .listResources(folder, rl ->
                        rl.getNamespace().equals(MOD_ID)
                                && rl.getPath().contains(prefix)
                                && rl.getPath().endsWith(".png")
                )
                .keySet()
                .stream()
                .filter(rl -> rl.toString().startsWith(fullPrefix))
                .sorted(Comparator.comparing(rl -> extractNumber(rl.getPath(), prefix)))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Extrae el número del nombre del archivo (ej. "eyes_3.png" → 3). */
    private static int extractNumber(String path, String prefix) {
        try {
            int start = path.lastIndexOf(prefix) + prefix.length();
            int end   = path.lastIndexOf('.');
            return Integer.parseInt(path.substring(start, end));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static ResourceLocation safeGet(List<ResourceLocation> list, int idx) {
        if (idx <= 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private static String label(String type, int idx) {
        if (idx == 0) return "None";
        return type + " " + idx;
    }
}