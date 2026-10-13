package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.HairCode;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Catálogo de peinados vigente + reglas por raza (el equivalente de FaceCatalog + FaceBans para
 * el pelo). Lo rellena {@link HairCatalogManager} en el servidor y {@link HairCatalogSyncPacket}
 * en el cliente (mismo patrón que FormDef.replaceAll).
 *
 * "Calvo" (null) es SIEMPRE una opción válida. Los peinados importados ("custom:&lt;id&gt;", de la
 * biblioteca del jugador, CustomHairLibrary) no son del catálogo: los valida customAllowed.
 */
public final class HairCatalog {
    private HairCatalog() {}

    private static final Comparator<HairStyleDef> ORDER =
            Comparator.comparingInt(HairStyleDef::sort).thenComparing(d -> d.id().toString());

    private static volatile Map<ResourceLocation, HairStyleDef> styles = Map.of();

    public static void replaceAll(Map<ResourceLocation, HairStyleDef> newStyles) {
        styles = Map.copyOf(newStyles);
    }

    public static boolean isEmpty() { return styles.isEmpty(); }

    public static Collection<HairStyleDef> all() { return styles.values(); }

    @Nullable
    public static HairStyleDef get(@Nullable ResourceLocation id) {
        return id == null ? null : styles.get(id);
    }

    /** Peinados que la raza puede ELEGIR (los "selectable": false solo existen como override). */
    public static List<HairStyleDef> options(Race race) {
        List<HairStyleDef> out = new ArrayList<>();
        for (HairStyleDef d : styles.values()) if (d.selectable() && d.allows(race)) out.add(d);
        out.sort(ORDER);
        return out;
    }

    /** Lo que ve la GUI: calvo (null) primero y después los peinados de la raza. */
    public static List<ResourceLocation> choices(Race race) {
        List<ResourceLocation> out = new ArrayList<>();
        out.add(null);
        for (HairStyleDef d : options(race)) out.add(d.id());
        return out;
    }

    public static boolean isAllowed(Race race, @Nullable ResourceLocation id) {
        if (id == null) return true;
        HairStyleDef d = get(id);
        return d != null && d.selectable() && d.allows(race);
    }

    @Nullable
    public static ResourceLocation step(Race race, @Nullable ResourceLocation current, int dir) {
        List<ResourceLocation> choices = choices(race);
        int idx = choices.indexOf(current);
        int next = idx < 0 ? (dir >= 0 ? 0 : choices.size() - 1) : Math.floorMod(idx + dir, choices.size());
        return choices.get(next);
    }

    /**
     * El peinado que SE VE: el elegido por el jugador, si existe y su raza lo admite, sustituido
     * por el de la forma activa si el peinado declara un form_override para ella (SSJ, UI...).
     * Un override a un peinado inexistente cae al base (ya avisado al cargar). null = calvo.
     */
    @Nullable
    public static HairStyleDef effectiveStyle(@Nullable ResourceLocation selected,
                                              @Nullable ResourceLocation formId, Race race) {
        HairStyleDef base = get(selected);
        if (base == null || !base.allows(race)) return null;
        HairStyleDef shown = get(base.styleForForm(formId));
        return shown != null ? shown : base;
    }

    /**
     * RGB con el que se tiñe el peinado: "hair" = el hair_rgb de la forma si lo declara, si no
     * el color de pelo del jugador; "none" = blanco (textura tal cual); fijo = el del JSON.
     */
    public static int tintRgb(HairStyleDef style, int playerHairRgb,
                              @Nullable com.hmc.zenkai.feature.forms.FormDef form) {
        return switch (style.color()) {
            case FIXED -> style.fixedRgb() & 0xFFFFFF;
            case HAIR -> (form != null && form.tintsHair()) ? form.hairRgb() & 0xFFFFFF : playerHairRgb & 0xFFFFFF;
            default -> 0xFFFFFF;
        };
    }

    /**
     * Si el peinado guardado no vale para la raza (no existe, no seleccionable, otra raza), lo
     * cambia por el default_for de la raza o por calvo. Con el catálogo vacío no toca nada.
     * Un importado ("custom:&lt;id&gt;", CustomHairLibrary) se respeta si el mundo los permite, está en
     * la biblioteca del jugador y su raza tiene pelo; si no, cae igual que un id inválido.
     * Devuelve true si cambió algo.
     */
    public static boolean reconcile(Race race, PlayerVisualAttachment visual) {
        if (isEmpty()) return false;
        String raw = visual.getHairStyleId();
        ResourceLocation current = visual.getHairStyle();
        if (HairCode.isCustom(raw)) {
            if (customAllowed(race, visual)) return false;
        } else if (isAllowed(race, current)) {
            return false;
        }
        ResourceLocation fallback = null;
        for (HairStyleDef d : options(race)) {
            if (d.isDefaultFor(race)) { fallback = d.id(); break; }
        }
        if (!HairCode.isCustom(raw) && Objects.equals(fallback, current)) return false;
        visual.setHairStyle(fallback);
        return true;
    }

    /** ¿Puede llevar el peinado importado que tiene puesto? */
    public static boolean customAllowed(Race race, PlayerVisualAttachment visual) {
        return com.hmc.zenkai.config.ServerConfig.allowCustomHair()
                && visual.activeCustomHair() != null && !options(race).isEmpty();
    }
}
