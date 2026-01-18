package com.hmc.db_renewed.core.network.feature.forms;

import com.hmc.db_renewed.core.network.feature.Race;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class FormRegistry {

    private static final Map<ResourceLocation, FormDefinition> FORMS = new HashMap<>();

    private static final Map<Race, ResourceLocation> FIRST_FORM_BY_RACE = new HashMap<>();

    private static boolean BOOTSTRAPPED = false;

    private FormRegistry() {}

    public static void bootstrap() {
        if (BOOTSTRAPPED) return;
        BOOTSTRAPPED = true;

        // =========================
        // BASE (no apunta a nada)
        // =========================
        registerRaw(new FormDefinition(
                FormIds.BASE,
                EnumSet.allOf(Race.class),
                0,
                0.0,
                null
        ));

        // =========================
        // Saiyan route
        // =========================
        FIRST_FORM_BY_RACE.put(Race.SAIYAN, FormIds.SSJ1);

        registerRaw(new FormDefinition(
                FormIds.SSJ1,
                EnumSet.of(Race.SAIYAN),
                100,
                0.2,
                FormIds.SSJ2
        ));

        registerRaw(new FormDefinition(
                FormIds.SSJ2,
                EnumSet.of(Race.SAIYAN),
                100,
                0.2,
                FormIds.SSJ3
        ));

        registerRaw(new FormDefinition(
                FormIds.SSJ3,
                EnumSet.of(Race.SAIYAN),
                100,
                0.2,
                FormIds.SSJ4
        ));

        registerRaw(new FormDefinition(
                FormIds.SSJ4,
                EnumSet.of(Race.SAIYAN),
                100,
                0.2,
                null
        ));

        // =========================
        // Arcosian / Freezer route
        // =========================
        FIRST_FORM_BY_RACE.put(Race.ARCOSIAN, FormIds.SECOND_FORM);

        registerRaw(new FormDefinition(
                FormIds.SECOND_FORM,
                EnumSet.of(Race.ARCOSIAN),
                80,     // 4s
                0.10,
                FormIds.THIRD_FORM
        ));

        registerRaw(new FormDefinition(
                FormIds.THIRD_FORM,
                EnumSet.of(Race.ARCOSIAN),
                80,
                0.14,
                FormIds.FINAL_FORM
        ));

        registerRaw(new FormDefinition(
                FormIds.FINAL_FORM,
                EnumSet.of(Race.ARCOSIAN),
                100,    // 5s
                0.20,
                FormIds.GOLDEN_FORM
        ));

        registerRaw(new FormDefinition(
                FormIds.GOLDEN_FORM,
                EnumSet.of(Race.ARCOSIAN),
                120,    // 6s
                0.30,
                FormIds.BLACK_FORM
        ));

        registerRaw(new FormDefinition(
                FormIds.BLACK_FORM,
                EnumSet.of(Race.ARCOSIAN),
                140,    // 7s (ajusta)
                0.40,
                null
        ));
    }

    private static void ensureBootstrapped() {
        if (!BOOTSTRAPPED) bootstrap();
    }

    /**
     * Registro público (seguro). Si llamas esto antes del bootstrap, lo dispara.
     * Útil si luego quieres registrar formas vía datapacks/config.
     */
    public static void register(FormDefinition def) {
        ensureBootstrapped();
        if (def == null || def.id() == null) return;
        FORMS.put(def.id(), def);
    }

    /**
     * Registro interno usado solo durante bootstrap() para no recursar.
     */
    private static void registerRaw(FormDefinition def) {
        if (def == null || def.id() == null) return;
        FORMS.put(def.id(), def);
    }

    public static FormDefinition get(ResourceLocation id) {
        ensureBootstrapped();
        FormDefinition base = FORMS.get(FormIds.BASE);
        if (id == null) return base;
        return FORMS.getOrDefault(id, base);
    }

    /**
     * Cuando el jugador está en BASE, de aquí sale la “primera forma” según raza.
     * Si retorna null => esa raza no tiene transformaciones (y NO deberías animar ni FOV, etc).
     */
    public static ResourceLocation firstFormFor(Race race) {
        ensureBootstrapped();
        if (race == null) return null;
        return FIRST_FORM_BY_RACE.get(race);
    }

    /**
     * Helper opcional: valida si una forma es usable por una raza.
     */
    public static boolean isAllowed(Race race, ResourceLocation formId) {
        ensureBootstrapped();
        if (race == null || formId == null) return false;
        return get(formId).allowedRaces().contains(race);
    }
}
