package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.technique.KiCombatServer;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: disparar la técnica del slot indicado. Validación 100% servidor: raza, slot,
 * cooldown (KiCombatServer), y energía suficiente (se descuenta con addEnergy negativo).
 *
 * Fórmulas (simuladas, eficiencia 0.22-0.35 pareja entre tipos):
 *  daño  = kiPower × type.damageMult × sizeFactor(size)          [por proyectil]
 *  coste = ceil(energyMax × 0.04 × type.kiCostMult × costSizeFactor(size))  [por disparo]
 * BURST dispara type.count proyectiles con dispersión; BARRIER activa la burbuja.
 */
public record KiFirePacket(int slot) implements CustomPacketPayload {

    private static final double BASE_COST_PCT = 0.04;
    private static final float BURST_SPREAD_DEG = 6.0f;

    public static final Type<KiFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_fire"));

    public static final StreamCodec<FriendlyByteBuf, KiFirePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, KiFirePacket::slot, KiFirePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            KiTechnique tech = att.techniques().slot(pkt.slot());
            if (tech == null) return;
            if (!KiCombatServer.tryFire(sp)) return;

            KiTechniqueType type = tech.type();
            double kiPower = att.computeKiPowerFinal();

            int cost = (int) Math.ceil(att.getEnergyMax() * BASE_COST_PCT
                    * type.kiCostMult * KiCombatServer.costSizeFactor(tech.size()));
            if (att.getEnergy() < cost) return;
            att.addEnergy(-cost);

            if (type.defensive) {
                KiCombatServer.activateBarrier(sp, tech, kiPower);
            } else {
                double damage = kiPower * type.damageMult * KiCombatServer.sizeFactor(tech.size());
                for (int i = 0; i < Math.max(1, type.count); i++) {
                    spawnProjectile(sp, tech, damage, i);
                }
            }
            PlayerLifeCycle.syncIfServer(sp);
        });
    }

    private static void spawnProjectile(ServerPlayer sp, KiTechnique tech, double damage, int index) {
        KiTechniqueType type = tech.type();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());

        // Dispersión solo en ráfagas (el primer proyectil va recto).
        float yawJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;
        float pitchJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;

        Vec3 dir = Vec3.directionFromRotation(sp.getXRot() + pitchJitter, sp.getYRot() + yawJitter);
        Vec3 spawn = sp.getEyePosition().add(dir.scale(0.9)).subtract(0, 0.15, 0);

        proj.configure(sp, type, tech.rgb(), tech.size(), damage, 100);
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed));
        sp.level().addFreshEntity(proj);
    }
}