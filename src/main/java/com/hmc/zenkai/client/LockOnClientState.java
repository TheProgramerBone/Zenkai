package com.hmc.zenkai.client;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.sense.LockOnPacket;
import com.hmc.zenkai.core.skills.SkillEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lock-on estilo xenoverse / TFAR (habilidad Ki Sense, nivel 1+).
 * CLAVE (por qué no marea): la cámara NO se mueve cada tick. La rotación se aplica solo
 * cuando el jugador mueve el ratón, desde MouseHandlerMixin (gancho en turnPlayer), y ahí
 * REEMPLAZA el giro libre por "mira al objetivo" en vez de sumarse. Con el ratón quieto la
 * cámara está quieta; al moverlo, la vista se mantiene anclada al objetivo. Este es el patrón
 * del mod TFAR Lock-On, adaptado a la habilidad Ki Sense.
 * El estado aquí es solo: a quién se ha fijado. La geometría de la rotación vive en aimDelta,
 * que el mixin invoca.
 */
public final class LockOnClientState {
    private LockOnClientState() {}

    /** Semiángulo del cono frontal al FIJAR (grados). Solo afecta a elegir objetivo. */
    private static final double PICK_CONE_DEG = 30.0;

    /** Margen sobre el rango de sentido antes de soltar el objetivo (histéresis). */
    private static final double DROP_MARGIN = 8.0;

    /** Altura del punto de mira DENTRO del objetivo, como fracción de su altura.
     *  1.0 = coronilla · ~0.85 = ojos (comportamiento anterior) · 0.62 = pecho · 0.5 = ombligo.
     *  Bájalo para que la cámara apunte más al centro del cuerpo. */
    private static final double AIM_HEIGHT_FRACTION = 0.62;

    private static int targetId = -1;

    public static int targetId() { return targetId; }

    public static boolean hasTarget() { return targetId >= 0; }

    /** Entidad fijada, o null si no hay o ya no existe en el cliente. */
    public static LivingEntity target(Minecraft mc) {
        if (targetId < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(targetId);
        return e instanceof LivingEntity le ? le : null;
    }

    public static void clear() {
        if (targetId < 0) return;
        targetId = -1;
        PacketDistributor.sendToServer(new LockOnPacket(-1));
    }

    /** Pulsación de la tecla de fijar (Left Alt). Toggle simple, sin ciclar. */
    public static void toggle(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        if (SkillEffects.canLockOn(mc.player)) {
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.lock_on.locked")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (targetId >= 0) {
            clear();
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.lock_on.off")
                            .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        LivingEntity best = pick(mc);
        if (best == null) {
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.lock_on.none")
                            .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        targetId = best.getId();
        PacketDistributor.sendToServer(new LockOnPacket(targetId));
        mc.player.displayClientMessage(
                Component.translatable("messages.zenkai.lock_on.on", best.getDisplayName())
                        .withStyle(ChatFormatting.AQUA), true);
    }

    /** El más centrado en la mirada dentro del cono, del rango y CON línea de visión. */
    private static LivingEntity pick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;

        double range = range(p);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        double cosLimit = Math.cos(Math.toRadians(PICK_CONE_DEG));

        AABB box = p.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestDot = cosLimit;

        for (Entity e : mc.level.getEntities(p, box,
                x -> x instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            LivingEntity le = (LivingEntity) e;

            Vec3 to = le.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0e-4) continue;

            double dot = look.dot(to.scale(1.0 / dist));
            if (dot <= bestDot) continue;
            if (hasLineOfSight(mc, p, le)) continue; // solo se fija lo que se ve

            bestDot = dot;
            best = le;
        }
        return best;
    }

    /** Raycast de bloques entre los ojos del jugador y los del objetivo. */
    private static boolean hasLineOfSight(Minecraft mc, LocalPlayer p, LivingEntity t) {
        if (mc.level == null) return true;
        Vec3 from = p.getEyePosition();
        Vec3 target = t.getEyePosition();
        HitResult hit = mc.level.clip(new ClipContext(from, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() != HitResult.Type.MISS
                && !(hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(target) - 0.5);
    }

    private static double range(LocalPlayer p) {
        return StatsConfig.senseKiRange() * SkillEffects.senseRangeFactor(p);
    }

    /** Llamar 1 vez por tick de cliente. Solo mantiene el objetivo vivo/en rango; NO rota. */
    public static void tick(Minecraft mc) {
        if (targetId < 0) return;

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { targetId = -1; return; }

        LivingEntity t = target(mc);
        if (t == null || !t.isAlive() || SkillEffects.canLockOn(p)
                || p.distanceTo(t) > range(p) + DROP_MARGIN) {
            clear();
            p.displayClientMessage(Component.translatable("messages.zenkai.lock_on.lost")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Devuelve el giro [yaw, pitch] (grados) que debe aplicarse ESTE frame para mirar al
     * objetivo, o null si no hay lock o no se debe rotar. Lo invoca MouseHandlerMixin cuando
     * el jugador mueve el ratón: sustituye el giro libre por "mira al objetivo".
     * Al llamarse solo con el ratón en movimiento, la cámara nunca se mueve sola: es el patrón
     * de TFAR que evita el mareo.
     */
    public static float[] aimDelta(Minecraft mc) {
        if (targetId < 0) return null;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;

        LivingEntity t = target(mc);
        if (t == null || !t.isAlive()) return null;
        // Tras una pared no reorientamos: dejamos que el ratón mande ese instante.
        if (hasLineOfSight(mc, p, t)) return null;

        Vec3 to = aimPoint(t).subtract(p.getEyePosition());
        double horizon = to.horizontalDistance();
        if (horizon < 1.0e-6 && Math.abs(to.y) < 1.0e-6) return null;

        // Convenio EXACTO de TFAR (handleKeyPress), para no arriesgar signos con turn():
        //   targetYaw   = atan2(-x, z),  turn recibe -(yRot - targetYaw)
        //   targetPitch = atan2(y, horizon), turn recibe -(xRot + targetPitch)
        Vec3 dir = to.normalize();
        double targetYaw = Mth.wrapDegrees(Mth.atan2(-dir.x, dir.z) * 180.0 / Math.PI);
        double targetPitch = Mth.atan2(dir.y, dir.horizontalDistance()) * 180.0 / Math.PI;

        double toTurnYaw = Mth.wrapDegrees(Mth.wrapDegrees(p.getYRot()) - targetYaw);
        double toTurnPitch = Mth.wrapDegrees(Mth.wrapDegrees(p.getXRot()) + targetPitch);
        return new float[]{ (float) -toTurnYaw, (float) -toTurnPitch };
    }

    /** Punto al que se apunta. Deliberadamente, NO son los ojos: fijar la cabeza deja la
     *  cámara mirando alto y el cuerpo se sale del centro en corta distancia. */
    private static Vec3 aimPoint(LivingEntity t) {
        return new Vec3(t.getX(), t.getY() + t.getBbHeight() * AIM_HEIGHT_FRACTION, t.getZ());
    }
}