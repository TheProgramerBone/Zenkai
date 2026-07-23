package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.combat.*;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.skills.SkillEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: "escanea mi entorno" (sentir el ki). El cliente lo manda cada 10 ticks mientras el modo
 * no sea OFF. El servidor responde con un SenseKiDataPacket con lo que hay en rango (el
 * filtrado por modo es del cliente, que conoce su propio PL).
 *
 * PL de cada entidad:
 *  - Jugador con raza / entidad con stats -> su PL real (y su body real).
 *  - Entidad con JSON display_only -> PL fijo del JSON; vida = la vanilla.
 *  - Mob vanilla / jugador sin raza -> PL = vida_max × factor (config); vida = la vanilla.
 */
public record SenseKiScanPacket() implements CustomPacketPayload {
    public static final Type<SenseKiScanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sense_ki_scan"));

    public static final StreamCodec<FriendlyByteBuf, SenseKiScanPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new SenseKiScanPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SenseKiScanPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Deja constancia de que este jugador tiene el sense encendido: es lo que
            // permite avisarle si alguien lo fija con el lock-on.
            // Sin la habilidad no se escanea, pase lo que pase en el cliente.
            if (SkillEffects.senseLevel(sp) <= 0) return;
            SenseServerState.markScan(sp);

            double r = StatsConfig.senseKiRange() * SkillEffects.senseRangeFactor(sp);
            AABB box = AABB.ofSize(sp.position(), r * 2, r * 2, r * 2);

            List<SenseKiDataPacket.Entry> out = new ArrayList<>();
            for (LivingEntity le : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != sp && e.isAlive() && !e.isSpectator())) {
                out.add(buildEntry(le));
                if (out.size() >= 128) break; // techo de seguridad del paquete
            }
            PacketDistributor.sendToPlayer(sp, new SenseKiDataPacket(out));
        });
    }

    private static SenseKiDataPacket.Entry buildEntry(LivingEntity le) {
        boolean isPlayer = le instanceof Player;

        // Gamerule de la capa Zenkai apagado: el combate va por vida vanilla (los pools quedan
        // congelados), asi que se reporta vanilla, igual que el resto del sistema.
        if (le.getServer() == null || !ModGameRules.enableRaceBoosts(le.getServer())) {
            return vanillaEntry(le, isPlayer);
        }

        // Jugador con raza: stats reales.
        if (le instanceof Player p) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
            if (att.isRaceChosen()) {
                return new SenseKiDataPacket.Entry(le.getId(),
                        att.getBody(), att.getBodyMax(),
                        att.getStamina(), att.getStaminaMax(),
                        att.getEnergy(), att.getEnergyMax(),
                        att.getPowerLevel(), true);
            }
            return vanillaEntry(le, true);
        }

        // Entidad con stats de combate resueltos.
        EntityStats stats = ZenkaiStats.entityStats(le);
        if (stats != null) {
            return new SenseKiDataPacket.Entry(le.getId(),
                    stats.getBody(), stats.getBodyMax(),
                    0, 0, 0, 0,
                    stats.getPowerLevel(), false);
        }

        // JSON display_only: PL fijo, vida vanilla.
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
        EntityStatDef def = EntityStatsManager.get(id);
        if (def != null && def.displayOnly()) {
            return new SenseKiDataPacket.Entry(le.getId(),
                    Math.round(le.getHealth()), Math.round(le.getMaxHealth()),
                    0,0,0,0,
                    def.powerLevel(), false);
        }

        return vanillaEntry(le, false);
    }

    private static SenseKiDataPacket.Entry vanillaEntry(LivingEntity le, boolean isPlayer) {
        long pl = Math.round(le.getMaxHealth() * StatsConfig.vanillaPowerLevelFactor());
        return new SenseKiDataPacket.Entry(le.getId(),
                Math.round(le.getHealth()), Math.round(le.getMaxHealth()),
                0,0,0,0, pl, isPlayer);
    }
}