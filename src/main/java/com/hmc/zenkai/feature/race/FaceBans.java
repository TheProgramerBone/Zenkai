package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué rasgos faciales puede elegir cada raza, sobre ids del catálogo ({@link FaceCatalog}).
 * La regla vive en el JSON de cada rasgo ("races" / "exclude_races" / "default_for"): un rasgo
 * exclusivo de Majin es un JSON con "races": ["majin"], sin tabla aparte.
 *
 * Historia: la primera versión (2026-10-01) era una tabla en código de ÍNDICES baneados por
 * raza. Funcionaba, pero el servidor no conocía el catálogo y reconciliaba "a ciegas" hacia el
 * índice permitido más bajo, sin saber si existía. Ahora el servidor tiene el catálogo
 * (datapack) y la pregunta es "¿este id existe y admite esta raza?".
 *
 * Se usa en la cuadrícula de HeadAppearanceScreen (choices) y, con reconcile, en la pantalla,
 * en UpdatePlayerVisualPacket (anti cliente modificado), al elegir raza (ChooseRacePacket,
 * /zenkai race) y al entrar al mundo (ids de un addon desinstalado). reconcile también corrige
 * el pelo (HairCatalog.reconcile).
 */
public final class FaceBans {

    private FaceBans() {}

    /** null = "ninguno", válido solo si la categoría lo admite. */
    public static boolean isAllowed(Race race, FaceCategory category, @Nullable ResourceLocation id) {
        if (id == null) return category.allowsNone();
        FacePartDef def = FaceCatalog.get(id);
        return def != null && def.category() == category && def.allows(race);
    }

    /** Opciones que ve la GUI, en orden: "ninguno" primero si la categoría lo admite. */
    public static List<ResourceLocation> choices(Race race, FaceCategory category) {
        List<ResourceLocation> out = new ArrayList<>();
        if (category.allowsNone()) out.add(null);
        for (FacePartDef d : FaceCatalog.options(category, race)) out.add(d.id());
        return out;
    }

    /**
     * Si un rasgo guardado no es válido para la raza (no existe, otra categoría, no admite la
     * raza), lo cambia por el "default_for" de la raza, o por la primera opción. Devuelve true
     * si cambió algo (para decidir si re-sincronizar).
     *
     * Con el catálogo VACÍO no toca nada: un datapack roto, o un cliente que aún no recibió el
     * sync, no deben borrar las caras guardadas de nadie.
     */
    public static boolean reconcile(Race race, PlayerVisualAttachment visual) {
        // El pelo va por el mismo embudo (S5): estos son ya los puntos donde se valida el visual
        // (pantalla, packet, elegir raza, /zenkai race, entrar al mundo).
        boolean changed = com.hmc.zenkai.feature.race.hair.HairCatalog.reconcile(race, visual);
        if (FaceCatalog.isEmpty()) return changed;
        for (FaceCategory c : FaceCategory.values()) {
            ResourceLocation current = visual.getFacePart(c);
            if (isAllowed(race, c, current)) continue;
            ResourceLocation fallback = fallbackFor(race, c);
            if (java.util.Objects.equals(fallback, current)) continue;
            visual.setFacePart(c, fallback);
            changed = true;
        }
        return changed;
    }

    @Nullable
    private static ResourceLocation fallbackFor(Race race, FaceCategory category) {
        List<FacePartDef> options = FaceCatalog.options(category, race);
        for (FacePartDef d : options) if (d.isDefaultFor(race)) return d.id();
        if (category.allowsNone()) return null;
        return options.isEmpty() ? null : options.get(0).id();
    }
}
