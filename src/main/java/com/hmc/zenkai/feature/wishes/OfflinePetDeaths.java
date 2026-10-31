package com.hmc.zenkai.feature.wishes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mascotas que murieron MIENTRAS su dueño estaba desconectado, a la espera de que vuelva.
 *
 * POR QUÉ NO VAN DIRECTAS AL ATTACHMENT. Un jugador desconectado no está cargado, así que no hay
 * {@code PlayerStatsAttachment} donde escribir. Se aparcan aquí, por UUID del DUEÑO, y
 * {@link PetDeathTracker#onLogin} las pasa a su lista de mascotas muertas al volver a conectarse. El deseo de
 * revivir mascota sigue leyendo SOLO la lista del jugador que invoca: nadie puede resucitar las de otro.
 * Se guarda en el store del overworld, igual que KorinSenzuData; el tope por dueño es el mismo que el del
 * attachment (6), y al pasarse se descartan las más antiguas.
 */
public class OfflinePetDeaths extends SavedData {

    private static final String ID = "zenkai_offline_pet_deaths";
    private static final int MAX_PER_OWNER = 6;

    private final Map<UUID, List<CompoundTag>> pending = new HashMap<>();

    public static final SavedData.Factory<OfflinePetDeaths> FACTORY =
            new SavedData.Factory<>(OfflinePetDeaths::new, OfflinePetDeaths::load, null);

    public static OfflinePetDeaths get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public void add(UUID owner, CompoundTag pet) {
        List<CompoundTag> list = pending.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(pet);
        while (list.size() > MAX_PER_OWNER) list.remove(0);
        setDirty();
    }

    /** Saca y devuelve las mascotas pendientes de este dueño (lista vacía si no hay). */
    public List<CompoundTag> drain(UUID owner) {
        List<CompoundTag> list = pending.remove(owner);
        if (list == null) return List.of();
        setDirty();
        return list;
    }

    public static OfflinePetDeaths load(CompoundTag tag, HolderLookup.Provider registries) {
        OfflinePetDeaths d = new OfflinePetDeaths();
        CompoundTag map = tag.getCompound("pending");
        for (String key : map.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                ListTag pets = map.getList(key, Tag.TAG_COMPOUND);
                List<CompoundTag> list = new ArrayList<>();
                for (int i = 0; i < pets.size(); i++) list.add(pets.getCompound(i));
                d.pending.put(owner, list);
            } catch (IllegalArgumentException ignored) {
                // UUID corrupto: se descarta esa entrada en vez de tirar el archivo entero.
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        CompoundTag map = new CompoundTag();
        pending.forEach((owner, list) -> {
            ListTag pets = new ListTag();
            pets.addAll(list);
            map.put(owner.toString(), pets);
        });
        tag.put("pending", map);
        return tag;
    }
}
