package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.combat.BlockingSyncPacket;
import com.hmc.zenkai.feature.combat.ZenkaiCombatStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado SERVIDOR del combate ki + fórmulas COMPARTIDAS (las usa KiFirePacket para
 * disparar y TechniqueEditScreen para las previews del editor — una sola fuente de verdad).
 *  - Fórmulas: daño = kiPower × dmgMult × sizeFactor; coste = kiPower × factor global ×
 *    multiplicador de raza/estilo × kiMult × costSizeFactor × (explosiva ? 1.5 : 1).
 *  - Carga: 0.25..2.00 (SOBRECARGA hasta el 200%). El daño escala lineal con el ratio; el
 *    coste 1:1 hasta el 100% y con recargo por encima. chargeRatio/chargeCostFactor son la
 *    ÚNICA conversión del mod: servidor, HUD, predicción de cliente y editor las comparten.
 *  - Cooldown: global anti-spam (5 ticks) + POR SLOT según type.cooldownTicks.
 *  - Barrera: pool = kiPower × 3 × sizeF, 10 s; absorb() desde CombatZenkaiHooks.
 *  - Bloqueo: manos vacías + click derecho; -60% velocidad (modificador transitorio) y
 *    difusión a trackers para la animación PAL.
 */
public final class KiCombatServer {
    private KiCombatServer() {}

    // ── Fórmulas compartidas ─────────────────────────────────────────────────

    public static final double BASE_COST_PCT = 0.04;
    public static final double EXPLOSIVE_COST_MULT = 1.5;

    /** Techo de carga: 2.0 = 200%. Como el daño escala lineal con el ratio, sobrecargar
     *  al máximo dobla el daño del disparo. */
    public static final double MAX_CHARGE = 2.0;

    /** Escalado del DAÑO por tamaño (1..7): 1.0 .. 2.5. */
    public static double sizeFactor(int size) {
        return 1.0 + 0.25 * (size - 1);
    }

    /** Escalado del COSTE por tamaño: 1.0 .. 2.2 (algo más barato que el daño). */
    public static double costSizeFactor(int size) {
        return 1.0 + 0.2 * (size - 1);
    }

    /** Daño por proyectil a carga completa. */
    public static double computeDamage(double kiPower, KiTechniqueType type, int size) {
        return kiPower * type.damageMult() * sizeFactor(size);
    }

    /** Coste de ki del disparo a carga completa. Escala con el PODER (WIL), no con el pool:
     *  así subir WIL sube daño Y coste, y SPI (el pool) decide cuántas veces lo sostienes.
     *  Recibe el objeto de stats y no el kiPower suelto para que el multiplicador de
     *  raza/estilo no se pueda olvidar en ningún call site. */
    public static int computeCost(ZenkaiCombatStats stats, KiTechniqueType type, int size,
                                  boolean explosive) {
        return (int) Math.ceil(stats.computeKiPowerFinal() * CommonConfig.kiCostPerPower()
                * stats.kiCostMult()
                * type.kiCostMult() * costSizeFactor(size)
                * (explosive ? EXPLOSIVE_COST_MULT : 1.0));
    }

    // ── Carga y sobrecarga ───────────────────────────────────────────────────

    /** Ticks totales para llegar al 200%: el primer 100% cuesta reqCharge y el tramo de
     *  sobrecarga overchargeTimeMult veces más. Con 2.5 son 3.5x el cast base. */
    public static int maxChargeTicks(int reqCharge) {
        return (int) Math.ceil(reqCharge
                * (1.0 + (MAX_CHARGE - 1.0) * CommonConfig.overchargeTimeMult()));
    }

    /** Ticks acumulados -> ratio 0..MAX_CHARGE. Curva PARTIDA a propósito: hasta el 100% es
     *  lineal 1:1 y a partir de ahí avanza overchargeTimeMult veces más lento. */
    public static double chargeRatio(int ticks, int reqCharge) {
        if (reqCharge <= 0 || ticks <= 0) return 0.0;
        if (ticks <= reqCharge) return ticks / (double) reqCharge;
        double over = (ticks - reqCharge) / (reqCharge * CommonConfig.overchargeTimeMult());
        return Math.min(MAX_CHARGE, 1.0 + over);
    }

    /** Multiplicador de coste para un ratio dado. El tramo normal cuesta 1:1; SOLO lo que
     *  pasa del 100% lleva recargo. Con 1.5, un disparo al 200% cuesta 2.5x el de 100%. */
    public static double chargeCostFactor(double ratio) {
        return Math.min(ratio, 1.0)
                + Math.max(0.0, ratio - 1.0) * CommonConfig.overchargeCostMult();
    }

    // ── Cooldowns (global anti-spam + por slot) ─────────────────────────────

    private static final int GLOBAL_COOLDOWN_TICKS = 5;

    private static final Map<UUID, Long> LAST_FIRE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Long>> SLOT_READY_AT = new ConcurrentHashMap<>();

    /** true si puede disparar ese slot ya (y registra el disparo + su cooldown). */
    public static boolean tryFire(ServerPlayer sp, int slot, int cooldownTicks) {
        long now = sp.level().getGameTime();
        Long last = LAST_FIRE.get(sp.getUUID());
        if (last != null && now - last < GLOBAL_COOLDOWN_TICKS) return false;

        Map<Integer, Long> slots = SLOT_READY_AT.computeIfAbsent(sp.getUUID(), k -> new ConcurrentHashMap<>());
        Long readyAt = slots.get(slot);
        if (readyAt != null && now < readyAt) return false;

        LAST_FIRE.put(sp.getUUID(), now);
        slots.put(slot, now + cooldownTicks);
        return true;
    }

    // ── Barrera ──────────────────────────────────────────────────────────────

    public static final double BARRIER_ABSORB_MULT = 3.0;  // pool = kiPower * mult * sizeF
    public static final int BARRIER_DURATION_TICKS = 200;  // 10 s

    private record Barrier(double pool, long expiryTick, int entityId) {}

    private static final Map<UUID, Barrier> BARRIERS = new ConcurrentHashMap<>();

    /** Activa (o reemplaza) la barrera del jugador y crea su visual. */
    public static void activateBarrier(ServerPlayer sp, KiTechnique tech, double kiPower) {
        removeBarrier(sp); // una sola barrera activa

        double pool = kiPower * BARRIER_ABSORB_MULT * sizeFactor(tech.size());
        long expiry = sp.level().getGameTime() + BARRIER_DURATION_TICKS;

        KiProjectileEntity visual = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());
        visual.configure(sp, KiTechniqueType.BARRIER, tech.rgb(), tech.size(),
                0, BARRIER_DURATION_TICKS, false);
        visual.setPos(sp.getX(), sp.getY(), sp.getZ());
        sp.level().addFreshEntity(visual);

        BARRIERS.put(sp.getUUID(), new Barrier(pool, expiry, visual.getId()));
    }

    /**
     * Reduce el daño entrante con la barrera activa (si hay). Devuelve el daño restante.
     * Llamar desde CombatZenkaiHooks antes de aplicar el daño al body del jugador.
     */
    public static double absorb(ServerPlayer sp, double damage) {
        Barrier b = BARRIERS.get(sp.getUUID());
        if (b == null) return damage;

        long now = sp.level().getGameTime();
        if (now >= b.expiryTick()) {
            removeBarrier(sp);
            return damage;
        }

        double absorbed = Math.min(b.pool(), damage);
        double remainingPool = b.pool() - absorbed;
        if (remainingPool <= 0) {
            removeBarrier(sp); // pool agotado: rompe la barrera (y su visual)
        } else {
            BARRIERS.put(sp.getUUID(), new Barrier(remainingPool, b.expiryTick(), b.entityId()));
        }
        return damage - absorbed;
    }

    private static void removeBarrier(ServerPlayer sp) {
        Barrier b = BARRIERS.remove(sp.getUUID());
        if (b != null && sp.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(b.entityId());
            if (e instanceof KiProjectileEntity) e.discard();
        }
    }

    // ── Bloqueo (defensa) ────────────────────────────────────────────────────

    /** -60% de velocidad mientras defiende (modificador transitorio, sin partículas). */
    private static final ResourceLocation BLOCK_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block_slow");
    private static final AttributeModifier BLOCK_SLOW = new AttributeModifier(
            BLOCK_SLOW_ID, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final Set<UUID> BLOCKING = ConcurrentHashMap.newKeySet();

    public static void setBlocking(ServerPlayer sp, boolean blocking) {
        // Autoritativo: defender exige las manos vacías.
        if (blocking && (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty())) {
            blocking = false;
        }
        boolean changed = blocking
                ? BLOCKING.add(sp.getUUID())
                : BLOCKING.remove(sp.getUUID());
        if (!changed) return;

        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(BLOCK_SLOW_ID);
            if (blocking) speed.addTransientModifier(BLOCK_SLOW);
        }

        // Los demás clientes reproducen la animación de defensa.
        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new BlockingSyncPacket(sp.getId(), blocking));
    }

    /** ¿Está este jugador defendiendo? (consulta del pipeline de daño). */
    public static boolean isBlocking(ServerPlayer sp) {
        return BLOCKING.contains(sp.getUUID());
    }
}