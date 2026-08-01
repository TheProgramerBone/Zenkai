package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public class NpcMarkerBlockEntity extends BlockEntity {

    /** Cada cuántos ticks se comprueba si el NPC sigue vivo. */
    private static final int CHECK_INTERVAL = 100;
    /** Comprobaciones fallidas seguidas antes de re-spawnear (margen por chunks descargando). */
    private static final int GRACE_CHECKS = 3;

    private ResourceLocation npcType;
    private float yaw = 0.0F;
    private UUID npcUuid;
    private int missed = 0;

    public NpcMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NPC_MARKER.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        if (npcType == null) return;
        if (server.getGameTime() % CHECK_INTERVAL != 0) return;
        if (ModGameRules.keepStructureNpcs(server.getServer())) return;

        if (npcUuid != null) {
            Entity existing = server.getEntity(npcUuid);
            if (existing != null && existing.isAlive()) { missed = 0; return; }
        }
        if (++missed < GRACE_CHECKS) return;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(npcType);
        if (type == null) return;
        if (!(type.create(server) instanceof Mob mob)) return;

        mob.moveTo(worldPosition.getX() + 0.5D, worldPosition.getY(),
                worldPosition.getZ() + 0.5D, yaw, 0.0F);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setPersistenceRequired();
        mob.restrictTo(worldPosition, 12);
        mob.finalizeSpawn(server, server.getCurrentDifficultyAt(worldPosition),
                MobSpawnType.STRUCTURE, null);
        server.addFreshEntity(mob);

        npcUuid = mob.getUUID();
        missed = 0;
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        npcType = tag.contains("NpcType") ? ResourceLocation.tryParse(tag.getString("NpcType")) : null;
        yaw = tag.getFloat("Yaw");
        npcUuid = tag.hasUUID("NpcUuid") ? tag.getUUID("NpcUuid") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (npcType != null) tag.putString("NpcType", npcType.toString());
        tag.putFloat("Yaw", yaw);
        if (npcUuid != null) tag.putUUID("NpcUuid", npcUuid);
    }
}