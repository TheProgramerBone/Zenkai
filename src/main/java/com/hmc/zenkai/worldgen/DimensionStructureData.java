package com.hmc.zenkai.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Marca de «esta estructura fija ya se colocó» guardada DENTRO DE LA DIMENSIÓN donde vive
 * ({@code <mundo>/dimensions/zenkai/<dimensión>/data/zenkai_structures.dat}), no en el Overworld.
 *
 * POR QUÉ: la marca vivía en {@link ZenkaiWorldData} (datos del Overworld). Si el jugador o el servidor borraba la carpeta
 * de una dimensión (Otherworld, Habitación del Tiempo), sus estructuras desaparecían pero la marca seguía diciendo «ya
 * colocada», y no se volvían a poner nunca. Guardada aquí, borrar la dimensión borra también la marca, y el siguiente
 * arranque (o el siguiente viaje) vuelve a colocar sus estructuras fijas. Una estructura que los jugadores reemplazan por
 * una propia NO se vuelve a forzar: la marca solo desaparece con la dimensión. Ver
 * {@link ZenkaiStructurePlacement#isPlaced} (migración desde la marca antigua) y el comando
 * {@code /zenkai struct restore}.
 */
public class DimensionStructureData extends SavedData {

    private static final String ID = "zenkai_structures";
    private final Set<String> placed = new HashSet<>();

    public static final SavedData.Factory<DimensionStructureData> FACTORY =
            new SavedData.Factory<>(DimensionStructureData::new, DimensionStructureData::load, null);

    public static DimensionStructureData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public boolean isPlaced(String key) { return placed.contains(key); }
    public void markPlaced(String key) { if (placed.add(key)) setDirty(); }
    public void unmarkPlaced(String key) { if (placed.remove(key)) setDirty(); }

    public static DimensionStructureData load(CompoundTag tag, HolderLookup.Provider registries) {
        DimensionStructureData d = new DimensionStructureData();
        ListTag list = tag.getList("placed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.placed.add(list.getString(i));
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (String s : placed) list.add(StringTag.valueOf(s));
        tag.put("placed", list);
        return tag;
    }
}
