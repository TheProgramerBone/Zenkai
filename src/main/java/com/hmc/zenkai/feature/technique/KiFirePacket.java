package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: disparar la técnica del slot con la carga acumulada (R + click derecho; soltar
 * click dispara). Validación 100% servidor: raza, manos vacías, slot, cooldown por slot
 * (KiCombatServer.tryFire), carga mínima (MIN_CHARGE) y energía suficiente.
 * chargeTicks se clampa a KiCombatServer.maxChargeTicks; ratio = 0..2.0 vía
 * KiCombatServer.chargeRatio (SOBRECARGA hasta el 200%). El daño escala LINEAL con la carga;
 * el coste 1:1 hasta el 100% y con recargo por encima (chargeCostFactor), así sobrecargar
 * dobla el daño pero cuesta 2.5x y tarda 3.5x. BARRIER ignora la carga (siempre completa).
 * Fórmulas en KiCombatServer (compartidas con las previews del editor).
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
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            KiChargeServer.stop(sp); // disparó: la bola de carga se apaga para todos
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            // Técnicas ki: ambas manos libres (canaliza el ki con las palmas).
            if (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty()) return;

            KiTechnique tech = att.techniques().slot(pkt.slot());
            if (tech == null) return;

            KiTechniqueType type = tech.type();
            if (!type.enabled()) return; // sin JSON: técnica desactivada

            // Maestría: carga requerida reducida (cast), costo reducido, daño aumentado.
            double castF = com.hmc.zenkai.feature.mastery.MasteryEffects.techCastFactor(att, type.name());
            int reqCharge = Math.max(1, (int) Math.round(type.chargeTicks() * castF));
            // Clamp a maxChargeTicks ANTES de convertir: un cliente modificado podría mandar
            // 99999 ticks y disparar al 5000%.
            int maxTicks = KiCombatServer.maxChargeTicks(reqCharge);
            // Carga REAL vs carga EFECTIVA. Para el daño, BARRIER ignora la carga y va siempre
            // al 100%. Pero los logros tienen que ver lo que el jugador de verdad cargó: con la
            // efectiva, un Barrier a carga mínima concedería "carga llena" sin cargar nada.
            double rawRatio = KiCombatServer.chargeRatio(
                    Mth.clamp(pkt.chargeTicks(), 0, maxTicks), reqCharge);
            double ratio = type.defensive() ? 1.0 : rawRatio;
            if (ratio < KiTechniqueType.MIN_CHARGE) return;

            boolean explosive = tech.explosive() && !type.defensive();
            int cost = (int) Math.max(1, Math.ceil(
                    KiCombatServer.computeCost(att, type, tech.size(), explosive)
                            * KiCombatServer.chargeCostFactor(ratio) * att.powerFraction()));
            // La energía se comprueba ANTES de tryFire: si no, sin ki el slot se queda
            // enfriándose sin haber disparado nada.
            if (att.getEnergy() < cost) return;

            if (!KiCombatServer.tryFire(sp, pkt.slot(), type.cooldownTicks())) return;

            att.addEnergy(-cost);
            att.addTechniqueMastery(type.name(), (float) CommonConfig.techMasteryPerUse());

            double kiPower = att.computeKiPowerFinal();

            if (type.defensive()) {
                KiCombatServer.activateBarrier(sp, tech, kiPower);
            } else {
                double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio;
                for (int i = 0; i < Math.max(1, type.count()); i++) {
                    spawnProjectile(sp, tech, damage, explosive, i);
                }
            }
            // Aquí y no antes: ya pasó cooldown, coste y spawn. Disparar al empezar a cargar
            // daría el logro por apretar el botón y soltarlo.
            ZenkaiTriggers.TECHNIQUE_USED.get().trigger(sp,
                    type.name().toLowerCase(java.util.Locale.ROOT), rawRatio);
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

        // El punto de salida lo decide la técnica (mano, boca, frente...). El enum ya lo
        // orienta con la mirada y lo escala con el tamaño del jugador; aquí solo se empuja
        // un poco hacia delante para que no nazca dentro del propio modelo.
        Vec3 spawn = tech.position().origin(sp).add(dir.scale(0.45));

        proj.configure(sp, type, tech.rgb(), tech.size(), damage, 100, explosive);
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed()));
        sp.level().addFreshEntity(proj);

        // Sonido de disparo: solo con el primer proyectil, o una ráfaga lo solaparía cinco
        // veces. Desde el servidor y con player=null, así lo oyen todos los de alrededor.
        if (index == 0) {
            SoundEvent snd = TechniqueAssets.soundOf(tech.releaseSound());
            if (snd != null) {
                sp.level().playSound(null, spawn.x, spawn.y, spawn.z,
                        snd, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }
}