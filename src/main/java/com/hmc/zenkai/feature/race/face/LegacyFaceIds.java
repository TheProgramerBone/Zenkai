package com.hmc.zenkai.feature.race.face;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Migración de saves anteriores al catálogo (formato con eyeIndex/mouthIndex/noseIndex) y de ids
 * renombrados. Es el ÚNICO sitio donde sobrevive el concepto de "índice de rasgo": traduce el
 * número que tenía guardado un jugador al id que lo sustituye. Nada más del mod debe conocer índices.
 *
 * Historia:
 *  · S1 (2026-10-01): eyes_1..5 -> zenkai:eyes/legacy_1..5, mouth_1 -> zenkai:mouth/legacy_1.
 *  · S4: los ojos legacy se retiraron (todo índice viejo de ojos -> zenkai:eyes/minimal).
 *  · Mismo día, a petición del usuario: los 5 ojos antiguos VUELVEN, adaptados al sistema actual
 *    (iris izquierdo/derecho separados) y con ids NUMÉRICOS zenkai:eyes/1..5 ("no hace falta
 *    nombre, pueden ser números"). Un índice viejo de ojos vuelve a su ojo exacto, y los saves
 *    de entre medias que guardaron "zenkai:eyes/legacy_N" se renombran a zenkai:eyes/N al cargar.
 *  · Después: el 2 se fusionó con minimal (misma silueta con el trazo de cejas) y los demás
 *    pasaron a ids con nombre = carpeta: 1 narrow, 3 wide, 4 sleepy, 5 arched.
 *  · 0.3.1: la boca zenkai:mouth/legacy_1 pasó a zenkai:mouth/neutral y los peinados
 *    legacy_1 / legacy_1_ssj a classic / classic_ssj (la personalización no lleva números).
 * Los índices los resolvía cada cliente sobre su lista de PNG: solo son fiables para los
 * ficheros que había entonces; cualquier otro valor se pierde (null) y reconcile lo repone.
 */
public final class LegacyFaceIds {
    private LegacyFaceIds() {}

    private static final int LEGACY_EYES  = 5;
    private static final int LEGACY_MOUTH = 1;
    private static final String RENAMED_EYES_PREFIX = "eyes/legacy_";

    /**
     * Ojo actual de cada número antiguo. El 2 se FUSIONÓ con minimal (con su trazo tenía la misma
     * silueta capa por capa); el resto pasó a un id con nombre igual a su carpeta (petición del
     * usuario: "que los nombres concuerden con la carpeta").
     */
    private static final String[] EYE_BY_NUMBER = {null, "narrow", "minimal", "wide", "sleepy", "arched"};

    private static ResourceLocation eyeFor(int n) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "eyes/" + EYE_BY_NUMBER[n]);
    }

    @Nullable
    public static ResourceLocation fromIndex(FaceCategory category, int index) {
        return switch (category) {
            case EYES -> (index >= 1 && index <= LEGACY_EYES) ? eyeFor(index) : null;
            case MOUTH -> (index >= 1 && index <= LEGACY_MOUTH)
                    ? ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "mouth/neutral") : null;
            case NOSE, EYEBROWS -> null; // nunca hubo narices ni cejas con índice
        };
    }

    /**
     * Ids que cambiaron de nombre durante la sesión de 2026-10-01: zenkai:eyes/legacy_N y
     * zenkai:eyes/N (los dos formatos intermedios) -> el id con nombre actual (EYE_BY_NUMBER).
     * El resto, igual.
     */
    public static ResourceLocation renamed(ResourceLocation id) {
        if (!Zenkai.MOD_ID.equals(id.getNamespace())) return id;
        String path = id.getPath();
        // 0.3.1: la única boca con número pasó a llamarse "neutral".
        if (path.equals("mouth/legacy_1")) return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "mouth/neutral");
        String n = path.startsWith(RENAMED_EYES_PREFIX) ? path.substring(RENAMED_EYES_PREFIX.length())
                : path.startsWith("eyes/") ? path.substring("eyes/".length()) : null;
        if (n != null && n.matches("[1-" + LEGACY_EYES + "]")) return eyeFor(Integer.parseInt(n));
        return id;
    }
}
