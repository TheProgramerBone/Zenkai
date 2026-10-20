package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.sense.LockOnPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lock-on estilo xenoverse (función base del jugador desde 0.5.1: ya no exige Ki Sense).
 *
 * CÁMARA. MouseHandlerMixin cede aquí el giro del ratón en {@link #applyAim}. Aviso importante
 * sobre lo que de verdad pasa, porque esta clase y el mixin decían otra cosa: turnPlayer se
 * llama CADA FRAME de render (también con el ratón quieto), no solo al moverlo, y
 * Entity.turn multiplica por 0.15 lo que recibe. Así que el lock NO es "solo cuando mueves el
 * ratón": la cámara sigue al objetivo a ritmo de frame. Antes cerraba un 15 % del error por
 * frame (dependía de los FPS); ahora cierra una fracción exponencial por segundo (independiente
 * de los FPS) y es ajustable con lock_on.smoothing. No marea porque se mueve por frame, nunca
 * por tick (a 20 Hz sí que se vería a saltos).
 *
 * El ratón NO queda anulado: sus movimientos se acumulan en un pequeño desvío (offYaw/offPitch,
 * con la sensibilidad del jugador) que desplaza el punto de mira unos grados y vuelve solo al
 * centro cuando sueltas el ratón. Así se puede "mirar alrededor" del objetivo sin soltarlo.
 *
 * El estado aquí es: a quién se ha fijado, cuánto lleva sin verse, y quién te fija a ti.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class LockOnClientState {
    private LockOnClientState() {}

    /** Semiángulo del cono frontal al FIJAR (grados). Solo afecta a elegir objetivo. */
    private static final double PICK_CONE_DEG = 35.0;

    /** Cono más ancho para reseleccionar cuando el objetivo muere (hay que encontrar al
     *  siguiente enemigo aunque no estés mirándolo justo). */
    private static final double RESELECT_CONE_DEG = 70.0;

    /** Margen sobre el rango del lock antes de soltar el objetivo (histéresis). */
    private static final double DROP_MARGIN = 8.0;

    /** Altura del punto de mira DENTRO del objetivo, como fracción de su altura.
     *  1.0 = coronilla · ~0.85 = ojos · 0.62 = pecho · 0.5 = ombligo. */
    private static final double AIM_HEIGHT_FRACTION = 0.62;

    /** Ticks seguidos sin línea de vista durante los que la cámara SIGUE apuntando (esquinas,
     *  postes, un salto): medio segundo. Pasado esto el ratón manda otra vez. */
    private static final int BLOCK_GRACE_TICKS = 10;

    /** Ticks seguidos sin línea de vista tras los que se suelta el lock (3 s). */
    private static final int BLOCK_DROP_TICKS = 60;

    /** Máxima desviación que el ratón puede añadir al punto de mira, en grados. */
    private static final double FREE_YAW_DEG = 14.0, FREE_PITCH_DEG = 9.0;

    /** Velocidad con la que ese desvío vuelve al centro (1/s) cuando el ratón está quieto. */
    private static final double FREE_DECAY_PER_SEC = 4.0;

    /** Velocidad de seguimiento de la cámara (1/s): smoothing 0 = ágil, 1 = flotante. */
    private static final double RATE_SNAPPY = 40.0, RATE_FLOATY = 5.0;

    /** Peso de cada criterio al puntuar candidatos (el término base es el coseno de la mirada). */
    private static final double HOSTILE_BONUS = 0.20, AGGRESSIVE_BONUS = 0.30, DISTANCE_PENALTY = 0.15;

    private static int targetId = -1;

    /** Tick de juego en que se fijó el objetivo actual (animación de "fijando" del marcador). */
    private static long lockedAtTick;

    /** Ticks seguidos sin línea de vista al objetivo (estado "perdiendo" del marcador). */
    private static int blockedTicks;

    private static double offYaw, offPitch;

    /** Ids de quienes te tienen fijado (LockOnAlertPacket). Solo se toca en el hilo del cliente. */
    private static final Set<Integer> ALERTS = new HashSet<>();

    public static int targetId() { return targetId; }

    public static boolean hasTarget() { return targetId >= 0; }

    public static long lockedAtTick() { return lockedAtTick; }

    public static int blockedTicks() { return blockedTicks; }

    /** true mientras la cámara ya no sigue al objetivo por no verlo (ver BLOCK_GRACE_TICKS). */
    public static boolean aimReleased() { return blockedTicks > BLOCK_GRACE_TICKS; }

    public static Set<Integer> alerts() { return ALERTS; }

    public static void setAlert(int attackerId, boolean on) {
        if (on) ALERTS.add(attackerId); else ALERTS.remove(attackerId);
    }

    /** Entidad fijada, o null si no hay o ya no existe en el cliente. */
    public static LivingEntity target(Minecraft mc) {
        if (targetId < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(targetId);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Suelta el lock y se lo dice al servidor. */
    public static void clear() {
        if (targetId < 0) return;
        reset();
        PacketDistributor.sendToServer(new LockOnPacket(-1));
    }

    /** Vacía el estado local SIN avisar al servidor (desconexión, respawn: él ya lo sabe). */
    private static void reset() {
        targetId = -1;
        blockedTicks = 0;
        offYaw = 0;
        offPitch = 0;
    }

    /** Rueda del ratón para cambiar de objetivo (opción cliente, apagada por defecto). */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (wheel(Minecraft.getInstance(), e.getScrollDeltaY())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        reset();
        ALERTS.clear();
    }

    /** El LocalPlayer se reemplaza al reaparecer o cambiar de dimensión: el servidor ya limpió su
     *  lock (SenseServerState) y los ids del mundo viejo no significan nada en el nuevo. */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone e) {
        reset();
        ALERTS.clear();
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    /** Pulsación de la tecla de fijar. Modo toggle: fija o suelta. Modo mantener: solo fija
     *  (soltar lo hace {@link #release} al levantar la tecla). */
    public static void press(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (notAllowed(mc)) return;

        if (ClientConfig.lockOnHold()) {
            if (targetId < 0) fix(mc);
            return;
        }
        if (targetId >= 0) {
            clear();
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.lock_on.off")
                            .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        fix(mc);
    }

    /** Tecla de fijar levantada en modo mantener: suelta en silencio. */
    public static void release(Minecraft mc) {
        if (targetId >= 0) clear();
    }

    /**
     * Pasa al siguiente (+1, hacia tu derecha) o al anterior (-1, izquierda) objetivo, ordenados
     * por el ángulo horizontal respecto al fijado actual, con vuelta al otro extremo al llegar al
     * final. Sin lock previo equivale a fijar. Solo cuenta lo que se ve y está en rango.
     */
    public static void cycle(Minecraft mc, int dir) {
        if (mc.player == null || mc.level == null) return;
        if (notAllowed(mc)) return;

        LivingEntity current = target(mc);
        if (current == null) {
            fix(mc);
            return;
        }

        LocalPlayer p = mc.player;
        double refYaw = yawTo(p, current);
        LivingEntity best = null;       // mejor candidato en el sentido pedido
        double bestDiff = Double.MAX_VALUE;
        LivingEntity wrap = null;       // el del extremo opuesto, si no hay ninguno en ese sentido
        double wrapDiff = -Double.MAX_VALUE;

        for (LivingEntity c : candidates(mc, p, range())) {
            if (c == current) continue;
            // Yaw crece hacia la derecha del jugador (0 = sur, 90 = oeste): diff > 0 = a su derecha.
            double diff = Mth.wrapDegrees(yawTo(p, c) - refYaw) * dir;
            if (diff > 0 && diff < bestDiff) { bestDiff = diff; best = c; }
            if (diff < 0 && -diff > wrapDiff) { wrapDiff = -diff; wrap = c; }
        }
        LivingEntity next = best != null ? best : wrap;
        if (next == null || next == current) return;
        setTarget(mc, next);
    }

    /** Ciclar con la rueda del ratón (opción cliente). Devuelve true si se ha consumido el giro. */
    public static boolean wheel(Minecraft mc, double scrollY) {
        if (targetId < 0 || scrollY == 0.0 || mc.screen != null || !ClientConfig.lockOnMouseWheel()) return false;
        cycle(mc, scrollY < 0 ? +1 : -1);
        return true;
    }

    /** Si el lock está cerrado para este jugador, avisa del motivo y devuelve true. */
    private static boolean notAllowed(Minecraft mc) {
        LocalPlayer p = mc.player;
        SkillEffects.LockOnBlock why = SkillEffects.lockOnBlockReason(p);
        if (why == SkillEffects.LockOnBlock.NONE) return false;
        String key = why == SkillEffects.LockOnBlock.FLYING
                ? "messages.zenkai.lock_on.no_flight"
                : "messages.zenkai.lock_on.disabled";
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** Elige el mejor candidato frente a la mirada y lo fija (o avisa de que no hay ninguno). */
    private static void fix(Minecraft mc) {
        LivingEntity best = pick(mc, PICK_CONE_DEG, false);
        if (best == null) {
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.lock_on.none")
                            .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        setTarget(mc, best);
        mc.player.displayClientMessage(
                Component.translatable("messages.zenkai.lock_on.on", best.getDisplayName())
                        .withStyle(ChatFormatting.AQUA), true);
    }

    private static void setTarget(Minecraft mc, LivingEntity t) {
        targetId = t.getId();
        lockedAtTick = mc.level.getGameTime();
        blockedTicks = 0;
        offYaw = 0;
        offPitch = 0;
        PacketDistributor.sendToServer(new LockOnPacket(targetId));
    }

    // ── Selección ────────────────────────────────────────────────────────────

    /**
     * Mejor candidato dentro del cono frontal. Puntúa el coseno de la mirada (más centrado =
     * mejor) más bonos por ser hostil y por estar agresivo (Mob.isAggressive es lo único que el
     * cliente sabe de "me está atacando": el último atacante vive solo en el servidor) y resta un
     * poco por distancia. Con {@code hostileOnly} descarta lo que no sea hostil (reselección).
     */
    private static LivingEntity pick(Minecraft mc, double coneDeg, boolean hostileOnly) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;

        double range = range();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        double cosLimit = Math.cos(Math.toRadians(coneDeg));

        LivingEntity best = null;
        double bestScore = -Double.MAX_VALUE;
        for (LivingEntity le : candidates(mc, p, range)) {
            if (hostileOnly && !(le instanceof Enemy)) continue;
            Vec3 to = le.getEyePosition().subtract(eye);
            double dist = to.length();
            double dot = look.dot(to.scale(1.0 / dist));
            if (dot <= cosLimit) continue;

            double score = dot - (dist / range) * DISTANCE_PENALTY;
            if (le instanceof Enemy) score += HOSTILE_BONUS;
            if (le instanceof Mob m && m.isAggressive()) score += AGGRESSIVE_BONUS;
            if (score > bestScore) { bestScore = score; best = le; }
        }
        return best;
    }

    /** Entidades fijables en rango y con línea de vista. Solo se llama al pulsar una tecla,
     *  nunca por frame ni por tick. */
    private static List<LivingEntity> candidates(Minecraft mc, LocalPlayer p, double range) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = p.getBoundingBox().inflate(range);
        for (Entity e : mc.level.getEntities(p, box, x -> x instanceof LivingEntity le
                && le.isAlive() && !le.isSpectator() && !(le instanceof ArmorStand)
                && !p.isPassengerOfSameVehicle(le))) {
            LivingEntity le = (LivingEntity) e;
            double dist = le.getEyePosition().distanceTo(p.getEyePosition());
            if (dist > range || dist < 1.0e-4) continue;
            if (isBlocked(mc, p, le)) continue; // solo se fija lo que se ve
            out.add(le);
        }
        return out;
    }

    /** Yaw (convenio de Minecraft) desde el jugador hacia la entidad. */
    private static double yawTo(LocalPlayer p, Entity e) {
        return Mth.wrapDegrees(Math.toDegrees(Mth.atan2(-(e.getX() - p.getX()), e.getZ() - p.getZ())));
    }

    /**
     * true si un bloque tapa la vista entre los ojos del jugador y los del objetivo.
     * Antes se llamaba hasLineOfSight y devolvía justo lo contrario (true = BLOQUEADA): los dos
     * usos lo compensaban al revés, una trampa de lectura.
     */
    private static boolean isBlocked(Minecraft mc, LocalPlayer p, LivingEntity t) {
        if (mc.level == null) return false;
        Vec3 from = p.getEyePosition();
        Vec3 target = t.getEyePosition();
        HitResult hit = mc.level.clip(new ClipContext(from, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(from) < from.distanceToSqr(target) - 0.5;
    }

    /** Rango propio del lock (lock_on.range, del servidor); ya no sale del Ki Sense. */
    private static double range() {
        return ServerConfig.lockOnRange();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    /** Llamar 1 vez por tick de cliente. Mantiene el objetivo vivo/en rango y cuenta los ticks
     *  sin línea de vista; NO rota la cámara. */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); ALERTS.clear(); return; }

        if (!ALERTS.isEmpty() && mc.level.getGameTime() % 20 == 0) {
            // Quien ya no existe (se fue, murió) no puede seguir avisándote.
            ALERTS.removeIf(id -> !(mc.level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        }

        if (targetId < 0) return;

        LivingEntity t = target(mc);
        if (t == null || !t.isAlive()) {
            // Reselección rápida: si el que caía era un enemigo, pasa al siguiente hostil visible
            // en vez de dejarte sin lock en medio de un combate de varios.
            boolean wasEnemy = t instanceof Enemy;
            reset();
            PacketDistributor.sendToServer(new LockOnPacket(-1));
            LivingEntity next = wasEnemy && !SkillEffects.lockOnBlocked(p)
                    ? pick(mc, RESELECT_CONE_DEG, true) : null;
            if (next != null) setTarget(mc, next); else lostMessage(p);
            return;
        }
        if (SkillEffects.lockOnBlocked(p) || p.distanceTo(t) > range() + DROP_MARGIN) {
            clear();
            lostMessage(p);
            return;
        }

        if (isBlocked(mc, p, t)) {
            if (++blockedTicks > BLOCK_DROP_TICKS) { clear(); lostMessage(p); }
        } else {
            blockedTicks = 0;
        }
    }

    private static void lostMessage(LocalPlayer p) {
        p.displayClientMessage(Component.translatable("messages.zenkai.lock_on.lost")
                .withStyle(ChatFormatting.GRAY), true);
    }

    // ── Cámara ───────────────────────────────────────────────────────────────

    /**
     * Gira la cámara hacia el objetivo ESTE frame. Lo invoca MouseHandlerMixin en lugar del giro
     * libre y devuelve true si lo ha sustituido (entonces el mixin cancela el turnPlayer vanilla).
     *
     * @param dt   segundos desde el frame anterior
     * @param accX movimiento horizontal acumulado del ratón (sin escalar, como en MouseHandler)
     * @param accY ídem vertical
     */
    public static boolean applyAim(Minecraft mc, LocalPlayer p, double dt, double accX, double accY) {
        if (targetId < 0 || mc.level == null) return false;
        LivingEntity t = target(mc);
        if (t == null || !t.isAlive()) return false;
        // Sin línea de vista durante demasiado rato el ratón manda (y el desvío se descarta).
        if (aimReleased()) { offYaw = 0; offPitch = 0; return false; }

        dt = Mth.clamp(dt, 0.0, 0.1);

        // El ratón desplaza el punto de mira. Misma sensibilidad que MouseHandler.turnPlayer
        // (curva cúbica) y el 0.15 que Entity.turn aplica, para que "se sienta" igual que sin lock.
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double degPerUnit = s * s * s * 8.0 * 0.15;
        double invert = mc.options.invertYMouse().get() ? -1.0 : 1.0;
        if (accX != 0.0 || accY != 0.0) {
            offYaw = Mth.clamp(offYaw + accX * degPerUnit, -FREE_YAW_DEG, FREE_YAW_DEG);
            offPitch = Mth.clamp(offPitch + accY * invert * degPerUnit, -FREE_PITCH_DEG, FREE_PITCH_DEG);
        } else {
            double k = Math.exp(-dt * FREE_DECAY_PER_SEC);
            offYaw *= k;
            offPitch *= k;
        }

        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 to = aimPoint(t, pt).subtract(p.getEyePosition(pt));
        double horizon = to.horizontalDistance();
        if (horizon < 1.0e-6 && Math.abs(to.y) < 1.0e-6) return false;

        // Convenio de TFAR: yaw destino = atan2(-x, z); la inclinación destino (xRot, + = mirar
        // abajo) es -atan2(y, horizontal).
        double targetYaw = Mth.atan2(-to.x, to.z) * 180.0 / Math.PI + offYaw;
        double targetPitch = -Mth.atan2(to.y, horizon) * 180.0 / Math.PI + offPitch;

        double errYaw = Mth.wrapDegrees(targetYaw - p.getYRot());
        double errPitch = targetPitch - p.getXRot();

        // Cierre exponencial por segundo: independiente de los FPS (el 15 % por frame de antes
        // iba el doble de rápido a 120 fps que a 60).
        double rate = Mth.lerp(ClientConfig.lockOnSmoothing(), RATE_SNAPPY, RATE_FLOATY);
        double f = 1.0 - Math.exp(-dt * rate);

        // turn() multiplica por 0.15: se compensa para girar exactamente errYaw*f grados.
        p.turn(errYaw * f / 0.15, errPitch * f / 0.15);
        return true;
    }

    /** Punto al que se apunta. Deliberadamente, NO son los ojos: fijar la cabeza deja la
     *  cámara mirando alto y el cuerpo se sale del centro en corta distancia. */
    public static Vec3 aimPoint(LivingEntity t, float partialTick) {
        Vec3 pos = t.getPosition(partialTick);
        return new Vec3(pos.x, pos.y + t.getBbHeight() * AIM_HEIGHT_FRACTION, pos.z);
    }
}
