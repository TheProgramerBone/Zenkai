package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Carga sola cada subcarpeta de {@code <juego>/zenkai/hair_packs/} como pack de recursos Y de
 * datos (peinados exportados desde la pestaña Pelo por autores de modpacks, plan
 * .claude/docs/customizacion-pelo.md §10.1). Obligatorios: no hay que activarlos a mano ni en la lista de packs ni por mundo.
 *
 * Un peinado tiene dos mitades: el .geo.json es un asset (cliente) y la entrada del catálogo es un
 * dato (servidor, que la sincroniza). En un modpack la misma carpeta va a los dos lados. Si a un
 * cliente le falta el asset, ve calvo a quien lleva el peinado (HairResolver ya lo comprueba).
 *
 * La carpeta se vuelve a escanear en cada recarga del repositorio de packs: un peinado recién
 * importado entra con /reload (datos) y F3+T (assets), sin reiniciar.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class HairPackSource {
    private HairPackSource() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-HairPacks");

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("zenkai").resolve("hair_packs");
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type != PackType.CLIENT_RESOURCES && type != PackType.SERVER_DATA) return;
        event.addRepositorySource(consumer -> {
            for (Path dir : folders()) {
                String name = dir.getFileName().toString();
                PackLocationInfo info = new PackLocationInfo("zenkai_hair/" + name,
                        Component.literal("Zenkai hair: " + name), PackSource.BUILT_IN, Optional.empty());
                Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir), type,
                        new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) {
                    consumer.accept(pack);
                } else {
                    LOGGER.warn("[Zenkai] {} no es un pack válido (¿falta pack.mcmeta?)", dir);
                }
            }
        });
    }

    /** Subcarpetas con pack.mcmeta, en orden alfabético (orden de carga estable). */
    public static List<Path> folders() {
        Path root = root();
        try {
            Files.createDirectories(root);
            try (Stream<Path> s = Files.list(root)) {
                return s.filter(Files::isDirectory)
                        .filter(p -> Files.isRegularFile(p.resolve("pack.mcmeta")))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            LOGGER.error("[Zenkai] No se pudo leer {}: {}", root, e.toString());
            return List.of();
        }
    }
}
