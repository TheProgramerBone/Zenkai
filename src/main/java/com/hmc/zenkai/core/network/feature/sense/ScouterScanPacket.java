package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.combat.ZenkaiStats;
import com.hmc.zenkai.core.config.StatsConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "escanea lo que tengo en la mira" (scouter). El cliente lo manda periódicamente mientras
 * el overlay del scouter está activo. Raycast AUTORITATIVO en servidor (mirada del jugador hasta
 * scouter.range). Un scouter detecta ki, no luz -> el rayo NO se corta con bloques (atraviesa
 * paredes, como el sentir ki). Responde ScouterDataPacket con el PL del objetivo (o "sin objetivo").
 */
public record ScouterScanPacket() implements CustomPacketPayload {
    public static final Type<ScouterScanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_scan"));

    public static final StreamCodec<FriendlyByteBuf, ScouterScanPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new ScouterScanPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterScanPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            double range = StatsConfig.scouterRange();
            Vec3 start = sp.getEyePosition();
            Vec3 end   = start.add(sp.getLookAngle().scale(range));
            AABB sweep = sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(range)).inflate(1.0);

            EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp.level(), sp, start, end, sweep,
                    e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && e != sp);

            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                long pl = ZenkaiStats.resolveDisplayPowerLevel(target);
                PacketDistributor.sendToPlayer(sp, new ScouterDataPacket(true, pl));
            } else {
                PacketDistributor.sendToPlayer(sp, new ScouterDataPacket(false, 0L));
            }
        });
    }
}