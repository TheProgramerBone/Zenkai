package com.hmc.zenkai.core.network.feature.forms;

import com.hmc.zenkai.core.network.feature.Race;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas sobre las formas. Ya NO define nada: los datos vienen del datapack (FormDef /
 * FormManager) y aquí solo se cachean los índices derivados, que se recalculan en cada
 * /reload mediante rebuild().
 *
 * Índices que se derivan de los datos:
 *  - primera forma por raza: la de kind SUPER sin parent que admite esa raza.
 *  - hijo de cada forma: la cadena del datapack apunta hacia atrás (parent), así que el
 *    "siguiente" se reconstruye invirtiéndola.
 */
public final class FormRegistry {
    private FormRegistry() {}

    private static volatile Map<Race, ResourceLocation> FIRST_BY_RACE = Map.of();
    private static volatile Map<ResourceLocation, List<ResourceLocation>> CHILDREN = Map.of();

    /** Recalcula los índices derivados. Lo llama FormManager tras cada carga. */
    public static void rebuild() {
        Map<Race, ResourceLocation> first = new EnumMap<>(Race.class);
        Map<ResourceLocation, List<ResourceLocation>> children = new HashMap<>();

        for (FormDef d : FormDef.all()) {
            if (d.parent() == null && d.kind() == FormDef.Kind.SUPER) {
                for (Race r : d.races()) first.putIfAbsent(r, d.id());
            } else if (d.parent() != null) {
                children.computeIfAbsent(d.parent(), k -> new ArrayList<>()).add(d.id());
            }
        }
        FIRST_BY_RACE = Collections.unmodifiableMap(first);
        CHILDREN = Collections.unmodifiableMap(children);
    }

    /** Datos de una forma. null en BASE o si no está definida (BASE es el estado neutro). */
    public static FormDef get(ResourceLocation id) {
        if (id == null || FormIds.BASE.equals(id)) return null;
        return FormDef.get(id);
    }

    /** Primera forma de una raza, o null si esa raza no tiene transformaciones. */
    public static ResourceLocation firstFormFor(Race race) {
        return race == null ? null : FIRST_BY_RACE.get(race);
    }

    /**
     * Siguiente forma en la cadena. Con varias ramas devuelve la primera; cuando existan
     * bifurcaciones de verdad, es la rueda quien debe elegir y no esta función.
     */
    public static ResourceLocation nextFrom(ResourceLocation current, Race race) {
        if (current == null || FormIds.BASE.equals(current)) return firstFormFor(race);
        List<ResourceLocation> kids = CHILDREN.get(current);
        if (kids == null) return null;
        for (ResourceLocation k : kids) {
            FormDef d = FormDef.get(k);
            if (d != null && d.allows(race)) return k;
        }
        return null;
    }

    /** Todas las ramas que salen de una forma (para la rueda). Nunca null. */
    public static List<ResourceLocation> childrenOf(ResourceLocation id) {
        return CHILDREN.getOrDefault(id, List.of());
    }

    public static boolean isAllowed(Race race, ResourceLocation formId) {
        if (FormIds.BASE.equals(formId)) return true; // base siempre disponible
        FormDef d = get(formId);
        return d != null && d.allows(race);
    }

    /** Multiplicador (fracción a SUMAR) de una forma con cierta maestría. 0 en base. */
    public static double statPercent(ResourceLocation id, double mastery) {
        FormDef d = get(id);
        return d == null ? 0.0 : d.statPercent(mastery);
    }

    /** Ki drenado por tick por una forma con cierta maestría. 0 en base. */
    public static double kiDrain(ResourceLocation id, double mastery) {
        FormDef d = get(id);
        return d == null ? 0.0 : d.kiDrainPerTick(mastery);
    }

    /** Cadena completa de formas de una raza, en orden (índice 0 = la primera). Nunca null.
     *  Es la FUENTE DE VERDAD de "cuántas transformaciones tiene esta raza": la habilidad
     *  super_forms deriva de aquí su nivel máximo y el coste de cada nivel. */
    public static List<ResourceLocation> chainFor(Race race) {
        if (race == null) return List.of();
        List<ResourceLocation> out = new ArrayList<>();
        ResourceLocation cur = firstFormFor(race);
        while (cur != null && !out.contains(cur)) { // contains: corta ciclos de un datapack roto
            out.add(cur);
            cur = nextFrom(cur, race);
        }
        return Collections.unmodifiableList(out);
    }
}