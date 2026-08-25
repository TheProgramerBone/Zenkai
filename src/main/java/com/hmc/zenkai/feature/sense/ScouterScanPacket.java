package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.config.CommonConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: "escanea lo que tengo en la mira" (scouter). El cliente lo manda periódicamente mientras
 * el overlay del scouter está activo. Raycast AUTORITATIVO en servidor (mirada del jugador hasta
 * el alcance del aparato). Un scouter detecta ki, no luz -> el rayo NO se corta con bloques
 * (atraviesa paredes, como el sentir ki). Responde ScouterDataPacket con el PL del objetivo
 * (o "sin objetivo").
 * El alcance y el tope de PL salen de ScouterStacks (mejoras del stack), no de un nivel global.
 * Cada una de las salidas de este forma pasa por ScouterOverload.tick: quedarse sin objetivo,
 * mirar a algo legible o quitarse el scouter CANCELAN la cuenta atrás. Si alguna rama se
 * saltara la llamada, la sobrecarga se quedaría congelada esperando al mismo objetivo y
 * reventaría el aparato la próxima vez que lo mirases, aunque fuese media hora después.
 */
public record ScouterScanPacket() implements CustomPacketPayload {
    public static final Type<ScouterScanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_scan"));

    public static final StreamCodec<FriendlyByteBuf, ScouterScanPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new ScouterScanPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterScanPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            ItemStack scouter = ScouterStacks.equipped(sp);
            // Sin scouter funcional no hay lectura: el cliente puede pedirla, el server no la da.
            if (scouter.isEmpty() || ScouterStacks.isBroken(scouter)) {
                ScouterOverload.tick(sp, -1);
                PacketDistributor.sendToPlayer(sp, ScouterDataPacket.empty());
                return;
            }

            // El alcance de la mira es el MENOR entre la config y el de la mejora: un scouter
            // sin mejorar no ve a 128 bloques por mucho que la config lo permita.
            double range = Math.min(CommonConfig.scouterRange(), ScouterStacks.range(scouter));

            Vec3 start = sp.getEyePosition();
            Vec3 end   = start.add(sp.getLookAngle().scale(range));
            AABB sweep = sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(range)).inflate(1.0);

            EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp.level(), sp, start, end, sweep,
                    e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && e != sp);

            if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
                ScouterOverload.tick(sp, -1);   // apartar la mirada CANCELA la sobrecarga
                PacketDistributor.sendToPlayer(sp, ScouterDataPacket.empty());
                return;
            }

            long pl = ZenkaiStats.resolveDisplayPowerLevel(target);
            long[] b = ZenkaiStats.resolveBreakdown(target);   // null = sin stats del mod

            var st = ZenkaiStats.of(target);
            boolean zenkai = st != null && st.isCombatActive();
            long hp    = zenkai ? st.getBody()    : Math.round(target.getHealth());
            long hpMax = zenkai ? st.getBodyMax() : Math.round(target.getMaxHealth());

            // Por encima del tope: arranca (o continúa) la cuenta atrás. Si llega al final,
            // breakScouter ya ha corrido dentro de tick() y este paquete es el último.
            long cap = ScouterStacks.plCap(scouter);
            boolean overload = pl > cap && ScouterOverload.tick(sp, target.getId());
            if (pl <= cap) ScouterOverload.tick(sp, -1);

            PacketDistributor.sendToPlayer(sp, new ScouterDataPacket(true, target.getId(), pl,
                    b == null ? 0L : b[0], b == null ? 0L : b[1], b == null ? 0L : b[2],
                    hp, hpMax, overload));
        });
    }
}