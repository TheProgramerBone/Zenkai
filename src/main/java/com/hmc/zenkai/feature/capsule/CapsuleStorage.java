package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.Zenkai;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Un archivo por cápsula llena en {@code <mundo>/data/zenkai_capsules/<uuid>.dat} (NBT comprimido).
 *
 * POR QUÉ NO un SavedData: un único archivo con cada cápsula del servidor se reescribiría entero en cada autoguardado,
 * aunque solo cambie una. Y POR QUÉ NO en el ítem: ver {@link CapsuleContent}. El archivo se escribe ANTES de quitar los
 * bloques del mundo (si falla el disco, la captura se cancela sin perder nada) y se borra al TERMINAR el pegado.
 *
 * Límite conocido: una cápsula llena que se destruye (lava, cactus, desaparece en el suelo) deja su archivo huérfano.
 * No se recolecta: cuesta unos KB y borrarlo exigiría saber que ningún cofre del mundo guarda ese ítem.
 */
public final class CapsuleStorage {
    private CapsuleStorage() {}

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("zenkai_capsules");
    }

    private static Path file(MinecraftServer server, UUID id) {
        return dir(server).resolve(id + ".dat");
    }

    public static boolean write(MinecraftServer server, UUID id, CompoundTag tag) {
        try {
            Files.createDirectories(dir(server));
            Path target = file(server, id);
            // A un temporal y luego se mueve: un cierre a medias no deja un archivo truncado con el nombre bueno.
            Path tmp = target.resolveSibling(id + ".tmp");
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Zenkai.LOGGER.error("Capsule: could not write {}", id, e);
            return false;
        }
    }

    public static @Nullable CompoundTag read(MinecraftServer server, UUID id) {
        Path f = file(server, id);
        if (!Files.isRegularFile(f)) return null;
        try {
            return NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            Zenkai.LOGGER.error("Capsule: could not read {}", id, e);
            return null;
        }
    }

    public static boolean exists(MinecraftServer server, UUID id) {
        return Files.isRegularFile(file(server, id));
    }

    public static void delete(MinecraftServer server, UUID id) {
        try {
            Files.deleteIfExists(file(server, id));
        } catch (IOException e) {
            Zenkai.LOGGER.error("Capsule: could not delete {}", id, e);
        }
    }
}
