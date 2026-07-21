package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.technique.KiCombatServer;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: disparar la técnica del slot con la carga acumulada (R + click derecho; soltar
 * click dispara). Validación 100% servidor: raza, manos vacías, slot, cooldown por slot
 * (KiCombatServer.tryFire), carga mínima (MIN_CHARGE) y energía suficiente.
 *
 * chargeTicks se clampa a type.chargeTicks; ratio = ticks/max. Daño y coste escalan
 * LINEAL con la carga (misma eficiencia a cualquier %, disparar antes = más débil pero
 * más rápido). BARRIER ignora la carga (siempre completa). Fórmulas en KiCombatServer
 * (compartidas con las previews del editor).
 */
public record KiFirePacket(int slot, int chargeTicks) implements CustomPacketPayload {

    private static final float BURST_SPREAD_DEG = 6.0f;

    public static final Type<KiFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_fire"));

    public static final StreamCodec<FriendlyByteBuf, KiFirePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.slot());
                        buf.writeVarInt(pkt.chargeTicks());
                    },
                    buf -> new KiFirePacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            // Técnicas ki: ambas manos libres (canaliza el ki con las palmas).
            if (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty()) return;

            KiTechnique tech = att.techniques().slot(pkt.slot());
            if (tech == null) return;

            KiTechniqueType type = tech.type();
            // Maestría: carga requerida reducida (cast), costo reducido, daño aumentado.
            double castF = com.hmc.zenkai.core.mastery.MasteryEffects.techCastFactor(att, type.name());
            int reqCharge = Math.max(1, (int) Math.round(type.chargeTicks * castF));
            double ratio = type.defensive ? 1.0
                    : Mth.clamp(pkt.chargeTicks() / (double) reqCharge, 0.0, 1.0);
            if (ratio < KiTechniqueType.MIN_CHARGE) return;

            if (!KiCombatServer.tryFire(sp, pkt.slot(), type.cooldownTicks)) return;

            double kiPower = att.computeKiPowerFinal()* MasteryEffects.formStatFactor(sp);
            boolean explosive = tech.explosive() && !type.defensive;

            int cost = (int) Math.max(1, Math.ceil(
                    KiCombatServer.computeCost(att.getEnergyMax(), type, tech.size(), explosive) * ratio * att.powerFraction()));
            if (att.getEnergy() < cost) return;
            att.addEnergy(-cost);
            att.addTechniqueMastery(type.name(), (float) StatsConfig.techMasteryPerUse());

            if (type.defensive) {
                KiCombatServer.activateBarrier(sp, tech, kiPower);
            } else {
                double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio;
                for (int i = 0; i < Math.max(1, type.count); i++) {
                    spawnProjectile(sp, tech, damage, explosive, i);
                }
            }
            PlayerLifeCycle.syncIfServer(sp);
        });
    }

    private static void spawnProjectile(ServerPlayer sp, KiTechnique tech, double damage,
                                        boolean explosive, int index) {
        KiTechniqueType type = tech.type();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());

        // Dispersión solo en ráfagas (el primer proyectil va recto).
        float yawJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;
        float pitchJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;

        Vec3 dir = Vec3.directionFromRotation(sp.getXRot() + pitchJitter, sp.getYRot() + yawJitter);
        Vec3 spawn = sp.getEyePosition().add(dir.scale(0.9)).subtract(0, 0.15, 0);

        proj.configure(sp, type, tech.rgb(), tech.size(), damage, 100, explosive);
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed));
        sp.level().addFreshEntity(proj);
    }
}