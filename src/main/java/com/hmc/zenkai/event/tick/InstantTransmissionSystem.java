package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.combat.SenseServerState;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.teleport.InstantTransmissionAttachment;
import com.hmc.zenkai.feature.teleport.InstantTransmissionSyncPacket;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.util.TeleportUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Transmisión Instantánea — Fase 1 ("la base", ver
 * .claude/pendiente/instant-transmission-pendiente.md). Dos gestos en la MISMA tecla (TAB),
 * resueltos aquí a partir de InstantTransmissionAttachment.holding (el único dato que manda el
 * cliente — ver InstantTransmissionHoldPacket):
 *  - soltar ANTES de armar el menú -> blink instantáneo a lo que esté en la mira;
 *  - mantener pulsado y QUIETO 5s seguidos -> arma el menú de planetas (Fase 2, sin pantalla
 *    todavía) en vez del blink.
 * Mantener pulsado NO congela el movimiento a propósito — el usuario quiere poder seguir
 * esquivando mientras decide qué hacer con el hold — así que "quieto" (stillTicks) es un
 * contador aparte de "pulsado" (holding), calculado aquí en SERVIDOR por desplazamiento real
 * tick a tick, nunca por un flag que mande el cliente.
 * Simplificación deliberada de esta fase: no pasa por ActionRules (la matriz de exclusión de
 * BLOCK/PHYSICAL/KI_TECHNIQUE/TRANSFORM) — el único gate es tener la skill y no estar en
 * cooldown. Ver la nota de pendientes de la Fase 1 si en el futuro hace falta integrarlo.
 */
public final class InstantTransmissionSystem {
    private InstantTransmissionSystem() {}

    public static void tick(TickCtx c) {
        if (!(c.p() instanceof ServerPlayer sp)) return;
        InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);

        if (att.getCooldownTicks() > 0) att.setCooldownTicks(att.getCooldownTicks() - 1);
        syncCooldownIfChanged(sp, att);

        boolean holding = att.isHolding();
        boolean wasHolding = att.wasHolding();
        boolean moved = att.advanceStillnessAndCheckMoved(sp.getX(), sp.getY(), sp.getZ());

        if (holding) {
            if (SkillEffects.instantTransmissionLevel(sp) <= 0) {
                // Sin la skill, TAB no hace absolutamente nada — ni cuenta hacia el menú.
                att.resetGesture();
            } else {
                if (moved) att.setStillTicks(0);
                else att.setStillTicks(att.getStillTicks() + 1);

                if (!att.isMenuArmed()
                        && att.getStillTicks() >= InstantTransmissionAttachment.MENU_ARM_TICKS) {
                    att.setMenuArmed(true);
                    sp.displayClientMessage(Component.translatable(
                            "messages.zenkai.instant_transmission.menu_ready")
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
        } else if (wasHolding && !att.isMenuArmed()) {
            // Flanco de bajada (soltó) y el menú no llegó a armarse: gesto de blink normal.
            tryBlink(sp, att);
        }

        if (!holding) att.resetGesture();
        att.setWasHolding(holding);
    }

    /** Resuelve objetivo y ejecuta el blink. No hace nada (sin sonido, sin coste) si el
     *  jugador no puede pagarlo o sigue en cooldown — soltar TAB en esos casos es un no-op. */
    private static void tryBlink(ServerPlayer sp, InstantTransmissionAttachment att) {
        if (att.getCooldownTicks() > 0) return;

        int level = SkillEffects.instantTransmissionLevel(sp);
        if (level <= 0) return;

        double kiCost = SkillEffects.instantTransmissionKiCost(sp);
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        if (stats.getKiCurrent() < kiCost) return;

        BlockPos rawTarget = resolveTarget(sp);
        if (rawTarget == null) return;

        ServerLevel level0 = sp.serverLevel();
        BlockPos safe = TeleportUtil.findSafeSpot(level0, rawTarget);
        Vec3 dest = TeleportUtil.footCenter(safe);

        level0.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.7f, 1.0f);

        sp.teleportTo(level0, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());

        level0.playSound(null, dest.x, dest.y, dest.z,
                ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.7f, 1.0f);

        stats.addKi(-kiCost);
        att.setCooldownTicks(SkillEffects.instantTransmissionCooldownTicks(sp));
        syncCooldownIfChanged(sp, att);
    }

    /**
     * Objetivo del blink: prioridad al lock-on de Ki Sense (si está activo y en rango) —
     * pedido explícito del usuario —; si no, un raytrace a lo largo de la mirada hasta el
     * rango del nivel actual: la entidad viva más cercana que el rayo toque, o si no golpea
     * ninguna, el bloque/punto donde el rayo termina (un "blink" corto en la dirección en la
     * que mira, sin requerir ningún objetivo vivo). El rango sube con el nivel (8 -> 64
     * bloques, ver SkillEffects.instantTransmissionRange).
     */
    private static BlockPos resolveTarget(ServerPlayer sp) {
        double range = SkillEffects.instantTransmissionRange(sp);

        if (!SkillEffects.lockOnBlocked(sp)) {
            int lockId = SenseServerState.lockOf(sp);
            if (lockId >= 0) {
                Entity e = sp.level().getEntity(lockId);
                if (e instanceof LivingEntity le && le.isAlive() && le.distanceTo(sp) <= range) {
                    return le.blockPosition();
                }
            }
        }

        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        Vec3 far = eye.add(look.scale(range));

        LivingEntity hitEntity = rayPickEntity(sp, eye, far);
        if (hitEntity != null) return hitEntity.blockPosition();

        HitResult blockHit = sp.level().clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 point = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getLocation();
        return BlockPos.containing(point.x, point.y, point.z);
    }

    /** La entidad viva más cercana al ojo cuya caja cruza el segmento eye->far. */
    private static LivingEntity rayPickEntity(ServerPlayer sp, Vec3 eye, Vec3 far) {
        AABB box = sp.getBoundingBox().expandTowards(far.subtract(eye)).inflate(1.0);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : sp.level().getEntities(sp, box,
                x -> x instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            var hit = e.getBoundingBox().inflate(0.3).clip(eye, far);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private static void syncCooldownIfChanged(ServerPlayer sp, InstantTransmissionAttachment att) {
        if (att.getCooldownTicks() == att.getLastSyncedCooldown()) return;
        att.setLastSyncedCooldown(att.getCooldownTicks());
        PacketDistributor.sendToPlayer(sp, new InstantTransmissionSyncPacket(att.getCooldownTicks()));
    }
}
