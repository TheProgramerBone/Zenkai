package com.hmc.db_renewed.capability;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.EntityCapability;

import javax.annotation.Nullable;

public class PlayerStatsProvider {

    // Declaramos la Capability para entidades sin contexto (Void)
    public static final EntityCapability<PlayerStats, Void> CAPABILITY =
            EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "player_stats"),
                    PlayerStats.class
            );

    // Instancia real (guardada por jugador)
    private final PlayerStats stats = new PlayerStats();

    public PlayerStats getStats() {
        return stats;
    }

    // ======= Métodos para guardar/cargar NBT =======

    public CompoundTag serializeNBT() {
        return stats.save();
    }

    public void deserializeNBT(CompoundTag nbt) {
        stats.load(nbt);
    }

    // ======= Metodo de fábrica que usaremos en el registro =======

    public static @Nullable PlayerStats get(Entity entity) {
        return entity.getCapability(CAPABILITY, null);
    }
}