package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Viaje a/desde la Habitación del Tiempo. El bloque de portal solo llama a enterHtc/exitHtc.
 *  - enterHtc: recuerda de dónde vino, garantiza la estructura (una sola vez) y teletransporta a la HTC.
 *  - exitHtc:  vuelve al punto guardado; si no hay, cae a la base de Kami en el overworld.
 * El punto de retorno se guarda en los datos persistentes del jugador (sobrevive a relogs).
 */
public final class HtcTravel {
    private HtcTravel() {}

    private static final String RETURN_TAG = "zenkai_htc_return";

    public static void enterHtc(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel htc = server.getLevel(ModDimensions.HTC_LEVEL);
        if (htc == null) return;

        // Recordar de dónde vino (para volver por el portal).
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", player.level().dimension().location().toString());
        ret.putLong("pos", player.blockPosition().asLong());
        player.getPersistentData().put(RETURN_TAG, ret);

        // Asegurar que la estructura de la HTC exista (idempotente, una sola vez por mundo).
        ZenkaiStructurePlacement.ensureHtcStructure(htc);

        BlockPos e = ModStructureSegments.HTC_ENTRANCE;
        player.teleportTo(htc, e.getX() + 0.5, e.getY(), e.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    public static void exitHtc(ServerPlayer player) {
        MinecraftServer server = player.server;

        ServerLevel dst = null;
        BlockPos pos = null;
        CompoundTag pd = player.getPersistentData();
        if (pd.contains(RETURN_TAG)) {
            CompoundTag ret = pd.getCompound(RETURN_TAG);
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(ret.getString("dim")));
            dst = server.getLevel(dimKey);
            pos = BlockPos.of(ret.getLong("pos"));
        }
        if (dst == null) { // fallback: base de Kami en el overworld
            dst = server.overworld();
            BlockPos kami = ZenkaiWorldData.get(server).getPos(ZenkaiStructurePlacement.KEY_KAMI);
            pos = (kami != null) ? kami : dst.getSharedSpawnPos();
        }
        player.teleportTo(dst, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }
}