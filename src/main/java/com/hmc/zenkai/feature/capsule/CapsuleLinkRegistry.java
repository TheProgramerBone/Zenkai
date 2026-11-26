package com.hmc.zenkai.feature.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dónde está COLOCADA cada cápsula vinculada (id de vínculo → dimensión y posición), para que el control remoto la encuentre
 * aunque la hayas recogido y puesto en otro sitio (petición del usuario 2026-11-26: «que una cápsula siempre se quede vinculada
 * y el jugador no tenga que volver a vincularla»). Una cápsula vinculada guarda su id para siempre en el stack, también como
 * ítem; aquí solo figura mientras está en el suelo.
 *
 * Lo mantiene {@code CapsuleBlockEntity}: alta al recibir su stack (colocarla, o el control al vincularla) y baja al soltarlo
 * (romperla, recogerla, capturar o abrir). La baja solo borra si la entrada apunta a ESA posición, por si la misma cápsula ya
 * se colocó en otra (copias por creativo: gana la última). Una entrada vieja (mundo editado a mano) no hace daño: el control
 * comprueba que en esa posición haya de verdad una cápsula con ese id.
 */
public final class CapsuleLinkRegistry extends SavedData {

    private static final String ID = "zenkai_capsule_links";
    public static final SavedData.Factory<CapsuleLinkRegistry> FACTORY =
            new SavedData.Factory<>(CapsuleLinkRegistry::new, CapsuleLinkRegistry::load, null);

    private final Map<UUID, GlobalPos> placed = new HashMap<>();

    public static CapsuleLinkRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public @Nullable GlobalPos where(UUID id) {
        return placed.get(id);
    }

    public void put(UUID id, GlobalPos pos) {
        if (!pos.equals(placed.put(id, pos))) setDirty();
    }

    /** Baja solo si la entrada sigue apuntando a {@code pos}. */
    public void remove(UUID id, GlobalPos pos) {
        if (pos.equals(placed.get(id))) {
            placed.remove(id);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        placed.forEach((id, gp) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putString("Dim", gp.dimension().location().toString());
            e.putLong("Pos", gp.pos().asLong());
            list.add(e);
        });
        tag.put("Placed", list);
        return tag;
    }

    public static CapsuleLinkRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        CapsuleLinkRegistry r = new CapsuleLinkRegistry();
        for (Tag t : tag.getList("Placed", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            if (dim == null || !e.hasUUID("Id")) continue;
            r.placed.put(e.getUUID("Id"), GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(e.getLong("Pos"))));
        }
        return r;
    }
}
