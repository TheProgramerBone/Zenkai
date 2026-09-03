package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.combat.SenseServerState;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.teleport.InstantTransmissionAttachment;
import com.hmc.zenkai.feature.teleport.InstantTransmissionSyncPacket;
import com.hmc.zenkai.feature.teleport.OpenInstantTransmissionMenuPayload;
import com.hmc.zenkai.feature.teleport.TeleportExecution;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Transmisión Instantánea (ver .claude/pendiente/instant-transmission-pendiente.md). Dos
 * gestos independientes en/alrededor de la MISMA tecla (TAB), resueltos aquí:
 *  - TAB pulsado + CLIC DERECHO (InstantTransmissionConfirmPacket, mientras
 *    InstantTransmissionAttachment.holding sigue true) -> blink instantáneo a lo que esté en la
 *    mira, ver confirmBlink. Soltar TAB SIN haber hecho clic derecho ya NO teletransporta —
 *    pedido explícito del usuario tras probar Dragon Block C, para que tocar TAB por error no
 *    dispare un blink accidental.
 *  - mantener TAB pulsado y QUIETO 2s seguidos (InstantTransmissionAttachment.MENU_ARM_TICKS) ->
 *    arma la apertura del menú de planetas (InstantTransmissionMenuScreen); soltar TAB una vez
 *    armado SÍ abre el menú con solo la tecla, sin clic derecho (pedido explícito: el menú se
 *    queda en "mantener y soltar").
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

                // El menú es Fase 2, nivel 3+ (SkillEffects.instantTransmissionMenuUnlocked).
                // Por debajo de ese nivel, quedarse quieto no hace nada especial: soltar sigue
                // disparando el blink normal pase lo que pase.
                if (!att.isMenuArmed()
                        && att.getStillTicks() >= InstantTransmissionAttachment.MENU_ARM_TICKS
                        && SkillEffects.instantTransmissionMenuUnlocked(sp)) {
                    att.setMenuArmed(true);
                    sp.displayClientMessage(Component.translatable(
                            "messages.zenkai.instant_transmission.menu_ready")
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
        } else if (wasHolding) {
            // Soltar TAB solo hace algo si el hold terminó ARMADO (abre el menú). El blink ya
            // NO se dispara al soltar — necesita el clic derecho de confirmBlink() mientras TAB
            // sigue pulsado (ver el javadoc de clase). Soltar sin haber confirmado nunca no hace
            // nada: es exactamente el "toqué la tecla por error" que este cambio evita.
            if (att.isMenuArmed()) {
                PacketDistributor.sendToPlayer(sp, new OpenInstantTransmissionMenuPayload());
            }
        }

        if (!holding) att.resetGesture();
        att.setWasHolding(holding);

        // Al final, con stillTicks/cooldown ya en su valor definitivo de este tick — así el
        // reset de resetGesture() (soltar TAB) se refleja en el mismo paquete, sin esperar al
        // tick siguiente para que InstantTransmissionCrosshairOverlay vea el estado "armado"
        // apagarse.
        syncStateIfChanged(sp, att);
    }

    /** Clic derecho mientras TAB sigue pulsado (InstantTransmissionConfirmPacket) — el gesto de
     *  confirmación del blink, ver el javadoc de clase. Revalida `holding` en SERVIDOR antes de
     *  hacer nada: un packet suelto/duplicado/tarde tras soltar TAB (o de un cliente modificado
     *  que lo mande sin tener la tecla pulsada de verdad) no dispara ningún blink. Tampoco actúa
     *  si el hold ya se armó para el menú — en ese punto el clic derecho no compite con abrir el
     *  menú, que sigue resolviéndose solo al soltar. Tras un blink real, cierra el gesto
     *  (resetGesture) para que el jugador pueda encadenar uno nuevo sin arrastrar stillTicks de
     *  antes de este blink. */
    public static void confirmBlink(ServerPlayer sp) {
        InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
        if (!att.isHolding() || att.isMenuArmed()) return;
        if (SkillEffects.instantTransmissionLevel(sp) <= 0) return;
        tryBlink(sp, att);
        att.resetGesture();
    }

    /** Resuelve objetivo y ejecuta el blink. No hace nada (sin sonido, sin coste) si el
     *  jugador no puede pagarlo o sigue en cooldown — un intento en esos casos es un no-op. */
    private static void tryBlink(ServerPlayer sp, InstantTransmissionAttachment att) {
        BlockPos rawTarget = resolveTarget(sp);
        if (rawTarget == null) return;

        if (TeleportExecution.execute(sp, att, sp.serverLevel(), rawTarget)) {
            // justTeleported=true: es el ÚNICO aviso que tiene el cliente de que el blink pasó
            // de verdad — ver InstantTransmissionSyncPacket y ClientZenkaiPalTick.
            // onInstantTransmissionTeleported (el brazo baja solo en vez de cortarse en seco).
            syncStateIfChanged(sp, att, true);
        }
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

    /** Público: lo reutiliza TeleportRequestPacket tras un teletransporte desde el menú de
     *  planetas, para que el badge del HUD (cooldown) y el anillo de progreso (quietud) se
     *  refresquen en el mismo tick sin esperar al próximo paso de este sistema. Delega en la
     *  variante con justTeleported=false: este llamador nunca acaba de ejecutar un blink él
     *  mismo (solo refleja cambios rutinarios de cooldown/stillTicks). */
    public static void syncStateIfChanged(ServerPlayer sp, InstantTransmissionAttachment att) {
        syncStateIfChanged(sp, att, false);
    }

    /** {@code justTeleported} es un PULSO, no un estado comparado contra el anterior — por eso
     *  esta variante manda el paquete SIEMPRE que sea true, incluso si por casualidad cooldown y
     *  stillTicks no cambiaron (no debería pasar nunca en la práctica: un blink real siempre
     *  sube el cooldown desde 0, pero mejor no depender de esa coincidencia para una señal que
     *  ClientZenkaiPalTick.onInstantTransmissionTeleported necesita recibir sí o sí). */
    public static void syncStateIfChanged(ServerPlayer sp, InstantTransmissionAttachment att, boolean justTeleported) {
        int stillNow = att.getStillTicks();
        boolean unchanged = att.getCooldownTicks() == att.getLastSyncedCooldown()
                && stillNow == att.getLastSyncedStillTicks();
        if (unchanged && !justTeleported) return;
        att.setLastSyncedCooldown(att.getCooldownTicks());
        att.setLastSyncedStillTicks(stillNow);
        PacketDistributor.sendToPlayer(sp,
                new InstantTransmissionSyncPacket(att.getCooldownTicks(), stillNow, justTeleported));
    }
}
