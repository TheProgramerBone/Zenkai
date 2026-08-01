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
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class NpcMarkerBlockEntity extends BlockEntity {

    /** Cada cuántos ticks se comprueba si el NPC sigue vivo. */
    private static final int CHECK_INTERVAL = 100;
    /** Comprobaciones fallidas seguidas antes de re-spawnear (margen por chunks descargando). */
    private static final int GRACE_CHECKS = 3;

    private ResourceLocation npcType;
    private float yaw = 0.0F;
    private double offX = 0.0D, offY = 0.0D, offZ = 0.0D;
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

        spawnNpc(server);
    }

    /** Aplica los valores del editor. Limpia el UUID: lo guardado deja de ser válido. */
    public void applyFrom(ResourceLocation type, float newYaw, double x, double y, double z) {
        this.npcType = type;
        this.yaw = newYaw;
        this.offX = x;
        this.offY = y;
        this.offZ = z;
        this.npcUuid = null;
        this.missed = 0;
        setChanged();
    }

    /** Mata el NPC actual y lo vuelve a crear ya, para ver el cambio sin esperar al tick. */
    public void forceRespawn() {
        if (!(level instanceof ServerLevel server)) return;
        if (npcUuid != null) {
            Entity old = server.getEntity(npcUuid);
            if (old != null) old.discard();
            npcUuid = null;
        }
        spawnNpc(server);
    }

    private void spawnNpc(ServerLevel server) {
        if (npcType == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(npcType);
        if (!(type.create(server) instanceof Mob mob)) return;

        mob.moveTo(worldPosition.getX() + 0.5D + offX,
                worldPosition.getY() + offY,
                worldPosition.getZ() + 0.5D + offZ,
                yaw, 0.0F);
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

    public ResourceLocation getNpcType() { return npcType; }
    public float getYaw() { return yaw; }
    public double getOffX() { return offX; }
    public double getOffY() { return offY; }
    public double getOffZ() { return offZ; }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        npcType = tag.contains("NpcType") ? ResourceLocation.tryParse(tag.getString("NpcType")) : null;
        yaw = tag.getFloat("Yaw");
        offX = tag.getDouble("OffX");
        offY = tag.getDouble("OffY");
        offZ = tag.getDouble("OffZ");
        npcUuid = tag.hasUUID("NpcUuid") ? tag.getUUID("NpcUuid") : null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (npcType != null) tag.putString("NpcType", npcType.toString());
        tag.putFloat("Yaw", yaw);
        tag.putDouble("OffX", offX);
        tag.putDouble("OffY", offY);
        tag.putDouble("OffZ", offZ);
        if (npcUuid != null) tag.putUUID("NpcUuid", npcUuid);
    }
}