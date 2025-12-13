package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class DataAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DragonBlockRenewed.MOD_ID);

    // Codec: NBT <-> PlayerStatsAttachment
    public static final Codec<PlayerStatsAttachment> PLAYER_STATS_CODEC = CompoundTag.CODEC.xmap(
            tag -> {
                PlayerStatsAttachment att = new PlayerStatsAttachment();
                att.load(tag);
                return att;
            },
            PlayerStatsAttachment::save // de objeto a NBT
    );

    public static final Codec<PlayerVisualAttachment> PLAYER_VISUAL_CODEC = CompoundTag.CODEC.xmap(
            tag -> {
                PlayerVisualAttachment att = new PlayerVisualAttachment();
                att.load(tag);
                return att;
            },
            PlayerVisualAttachment::save // de objeto a NBT
    );

    public static final Supplier<AttachmentType<PlayerStatsAttachment>> PLAYER_STATS =
            REGISTER.register("player_stats", () ->
                    AttachmentType.builder(PlayerStatsAttachment::new)
                            .serialize(PLAYER_STATS_CODEC)
                            .copyOnDeath()
                            .build());

    public static final Supplier<AttachmentType<PlayerVisualAttachment>> PLAYER_VISUAL =
            REGISTER.register("player_visual", () ->
                    AttachmentType.builder(PlayerVisualAttachment::new)
                            .serialize(PLAYER_VISUAL_CODEC)
                            .copyOnDeath()
                            .build());
}