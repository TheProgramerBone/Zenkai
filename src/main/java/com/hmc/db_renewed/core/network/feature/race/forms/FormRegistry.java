package com.hmc.db_renewed.core.network.feature.race.forms;

import com.hmc.db_renewed.core.network.feature.Race;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class FormRegistry {
    private static final Map<ResourceLocation, FormDefinition> FORMS = new HashMap<>();

    public static void bootstrap() {
        // Base -> SSJ1 (solo Saiyan)
        register(new FormDefinition(
                FormIds.BASE,
                EnumSet.allOf(Race.class), // base permite todas
                0,
                0.0,
                FormIds.SSJ1 // ojo: para no-saiyan, lo gateamos por allowedRaces en ssj1
        ));

        // SSJ1
        register(new FormDefinition(
                FormIds.SSJ1,
                EnumSet.of(Race.SAIYAN),
                100,      // 5s hold para llegar aquí
                0.2,      // placeholder: luego configurable por usuario/forma
                FormIds.SSJ2
        ));

        // SSJ2
        register(new FormDefinition(
                FormIds.SSJ1,
                EnumSet.of(Race.SAIYAN),
                100,      // 5s hold para llegar aquí
                0.2,      // placeholder: luego configurable por usuario/forma
                FormIds.SSJ3
        ));

        // SSJ3
        register(new FormDefinition(
                FormIds.SSJ3,
                EnumSet.of(Race.SAIYAN),
                100,      // 5s hold para llegar aquí
                0.2,      // placeholder: luego configurable por usuario/forma
                FormIds.SSJ4
        ));

        // SSJ4
        register(new FormDefinition(
                FormIds.SSJ4,
                EnumSet.of(Race.SAIYAN),
                100,      // 5s hold para llegar aquí
                0.2,      // placeholder: luego configurable por usuario/forma
                null
        ));
    }

    public static void register(FormDefinition def) {
        FORMS.put(def.id(), def);
    }

    public static FormDefinition get(ResourceLocation id) {
        return FORMS.getOrDefault(id, FORMS.get(FormIds.BASE));
    }

    private FormRegistry() {}
}
