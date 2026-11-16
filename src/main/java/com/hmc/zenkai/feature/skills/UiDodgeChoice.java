package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué esquive de Ultra Instinto corresponde al golpe que llega (2026-11-15). Solo servidor y solo PRESENTACIÓN: que el
 * golpe se anule lo decide OverlayMetersSystem.tryDodge; aquí se elige cuál de los clips ui_dodge_* ven los clientes.
 *
 * El esquive se coordina con el ataque cuando el atacante usa los sistemas de Zenkai, porque el servidor ya sabe con qué
 * golpea: el golpe básico llega con su variante (BasicStrikePacket) y las técnicas físicas pasan por
 * PhysicalCombatServer.execute. Con esos datos y la posición REAL del atacante respecto a la mirada de la víctima se
 * decide el lado: no basta «puño derecho = esquivo a la derecha», porque el mismo puño llega por el otro lado si el
 * atacante está a la espalda. Sin esos datos (mob vanilla, otro mod, daño sin autor) se usa el lado por el que viene el
 * atacante si es claro y, si no, una secuencia fija.
 */
public final class UiDodgeChoice {
    private UiDodgeChoice() {}

    // El orden ES el protocolo: viaja como entero en UiDodgeAnimPacket y ZenkaiPalAnimations.UI_DODGE_ANIMS lo sigue.
    public static final int LEAN_LEFT = 0, LEAN_RIGHT = 1, BACK = 2, DUCK = 3, TURN_LEFT = 4, TURN_RIGHT = 5;
    public static final int COUNT = 6;

    /** Sin datos del golpe: ladeos, medios giros y atrás, por turnos. */
    private static final int[] SEQUENCE = {LEAN_LEFT, LEAN_RIGHT, TURN_LEFT, TURN_RIGHT, BACK};

    private static final int RIGHT_HAND = 0, LEFT_HAND = 1, LEG = 2;
    /** El golpe básico conecta en el mismo tick en que llega su paquete, o en el siguiente. */
    private static final int BASIC_WINDOW_TICKS = 8;
    /** Una técnica reparte sus golpes durante su duración (la ráfaga, el corte cruzado, el picado). */
    private static final int TECHNIQUE_WINDOW_TICKS = 40;
    /** Separación lateral de la mano respecto al centro del atacante, en bloques. */
    private static final double HAND_OFFSET = 0.35;
    /** Por debajo de esto el golpe viene «de frente» y el lado no es fiable. */
    private static final double CENTERED = 0.25;

    private record Strike(int limb, PhysicalTechnique tech, long tick) {}

    private static final Map<UUID, Strike> LAST = new ConcurrentHashMap<>();

    /** variant: el de BasicStrikePacket (0 y 2 puño de la mano principal, 1 puño de la otra, 3 patada). */
    public static void noteBasicStrike(ServerPlayer sp, int variant) {
        boolean rightHanded = sp.getMainArm() == HumanoidArm.RIGHT;
        int limb = variant == 3 ? LEG : ((variant == 1) != rightHanded ? RIGHT_HAND : LEFT_HAND);
        LAST.put(sp.getUUID(), new Strike(limb, null, sp.level().getGameTime()));
    }

    public static void notePhysical(ServerPlayer sp, PhysicalTechnique t) {
        int limb = sp.getMainArm() == HumanoidArm.RIGHT ? RIGHT_HAND : LEFT_HAND;
        LAST.put(sp.getUUID(), new Strike(limb, t, sp.level().getGameTime()));
    }

    public static void forget(UUID id) { LAST.remove(id); }

    /** @param seq contador de esquives de la víctima: reparte la secuencia y alterna los lados en las ráfagas. */
    public static int pick(ServerPlayer victim, DamageSource src, int seq) {
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        long now = victim.level().getGameTime();
        Strike s = attacker == null ? null : LAST.get(attacker.getUUID());
        if (s != null && now - s.tick() > (s.tech() == null ? BASIC_WINDOW_TICKS : TECHNIQUE_WINDOW_TICKS)) s = null;

        // Proyectil (ki, flecha, la media luna de la Hoja de luz): se aparta del lado por el que entra.
        if (direct != null && direct != attacker) {
            if (s != null && s.tech() == PhysicalTechnique.LIGHT_BLADE) return DUCK; // barrido horizontal
            double side = lateral(victim, direct.position());
            if (Math.abs(side) >= CENTERED) return away(side, false);
            return sequence(seq);
        }
        if (attacker == null) return sequence(seq);

        if (s != null) {
            double side = lateral(victim, handPos(attacker, s.limb()));
            if (s.tech() == null) return s.limb() == LEG ? BACK : away(side, false);
            return switch (s.tech()) {
                // Golpes rectos o verticales: basta ponerse de perfil para que pasen por delante del pecho.
                case DASH_PUNCH, HEAVY_BLOW, PIERCING_THRUST, SKY_CLEAVE -> away(side, true);
                // Varios golpes seguidos: un lado y el otro.
                case BARRAGE, CROSS_SLASH -> away((seq & 1) == 0 ? side : -side, false);
                // Tajos horizontales: por debajo.
                case SPIN_SLASH, LIGHT_BLADE -> DUCK;
                // De abajo arriba, de frente o cayendo encima: atrás.
                case RISING_SLASH, KIAI, DIVE -> BACK;
                default -> away(side, false);
            };
        }
        // Mob vanilla u otro mod: no se sabe con qué golpea, pero sí por dónde viene.
        double side = lateral(victim, attacker.position());
        return Math.abs(side) >= CENTERED ? away(side, false) : sequence(seq);
    }

    private static int sequence(int seq) {
        return SEQUENCE[Math.floorMod(seq, SEQUENCE.length)];
    }

    /**
     * side &gt; 0 = el golpe llega por la DERECHA de la víctima. Ladeo: el tronco se va al lado contrario. Medio giro: el hombro
     * de ese lado se echa atrás (el pecho gira hacia el lado del golpe).
     */
    private static int away(double side, boolean turn) {
        if (turn) return side > 0 ? TURN_RIGHT : TURN_LEFT;
        return side > 0 ? LEAN_LEFT : LEAN_RIGHT;
    }

    /** Coordenada lateral de un punto en el marco de la víctima (bloques; positivo = a su derecha). */
    private static double lateral(Entity victim, Vec3 point) {
        Vec3 right = rightOf(victim);
        Vec3 d = point.subtract(victim.position());
        return d.x * right.x + d.z * right.z;
    }

    private static Vec3 handPos(Entity attacker, int limb) {
        if (limb == LEG) return attacker.position();
        Vec3 right = rightOf(attacker);
        double k = limb == RIGHT_HAND ? HAND_OFFSET : -HAND_OFFSET;
        return attacker.position().add(right.x * k, 0, right.z * k);
    }

    /** Vector unitario hacia la derecha de la entidad (guiñada 0 = mira a +Z, y su derecha es -X). */
    private static Vec3 rightOf(Entity e) {
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
    }
}
