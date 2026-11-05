package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Datapacks INTEGRADOS en el jar pero DESACTIVADOS por defecto (mismo patrón que los packs opcionales de Create o Bumblezone):
 * viven en {@code zenkai_builtin_packs/<nombre>/} dentro de los recursos del mod, no están implementados en el código y solo
 * cuentan cuando el jugador los activa (pantalla de datapacks al crear el mundo, o {@code /datapack enable} en uno existente).
 * Son EJEMPLOS para autores de addons de transformaciones (ver ADDONS.md §1c) y a la vez la prueba de la lógica de ramas:
 * «Extra Transformations» (SSJ Blue 2, SSJ Blue 3 y SSJ4 Full Power) y «AF Saiyan Transformations» (SSJ5 y SSJ6) cuelgan
 * formas del MISMO padre (SSJ4) desde dos namespaces distintos, y deben coexistir como hermanas independientes.
 *
 * Cada carpeta lleva datos (formas) y assets (lang), así que se registra dos veces: el pack de DATOS es opcional; el de
 * RECURSOS va siempre activo (solo trae traducciones, inofensivas aunque el datapack esté apagado).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class BuiltinExamplePacks {
    private BuiltinExamplePacks() {}

    private static final String BASE = "zenkai_builtin_packs/";

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        add(event, "extra_transformations", "Zenkai Example: Extra Transformations");
        add(event, "af_saiyan_transformations", "Zenkai Example: AF Saiyan Transformations");
    }

    private static void add(AddPackFindersEvent event, String folder, String title) {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, BASE + folder);
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addPackFinders(path, PackType.SERVER_DATA, Component.literal(title), PackSource.BUILT_IN, false, Pack.Position.TOP);
        } else if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addPackFinders(path, PackType.CLIENT_RESOURCES, Component.literal(title + " (lang)"), PackSource.BUILT_IN, true, Pack.Position.TOP);
        }
    }
}
