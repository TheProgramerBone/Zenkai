package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.action.ActionRules;
import com.hmc.zenkai.feature.action.ActionType;
import com.hmc.zenkai.feature.action.ServerActionContext;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
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

    /**
     * CURVAS DE TAMAÑO (1..5). Índice = tamaño - 1.
     * El problema que arreglan: antes el daño subía más deprisa que el coste y el tamaño no
     * costaba tiempo, así que el tamaño máximo daba 2.5x de DPS Y era un 13 % más eficiente por
     * ki. No había ninguna razón para elegir pequeño; el selector era un "sube esto al tope".
     * Con estas cuatro curvas el DPS sostenido queda PLANO (±3 %), la eficiencia por ki CAE un
     * 24 % del tamaño 1 al 5, y el daño por disparo SUBE un 132 %. La decisión pasa a ser real:
     * pequeño para pelear sostenido sin quedarte sin ki, grande para el golpe que no puedes
     * fallar. El peso del coste temporal lo lleva la carga; el enfriamiento sube solo un 52 %
     * en el rango.
     * Verificado por simulación antes de entregar. No tocar una sola de las cuatro sin
     * recalcular las otras tres: la planitud del DPS depende de las cuatro a la vez.
     */
    private static final double[] SIZE_DMG  = {1.00, 1.30, 1.62, 1.96, 2.32};
    private static final double[] SIZE_COST = {1.00, 1.42, 1.90, 2.44, 3.05};
    private static final double[] SIZE_CAST = {1.00, 1.35, 1.75, 2.20, 2.70};
    private static final double[] SIZE_CD   = {1.00, 1.10, 1.22, 1.36, 1.52};

    private static double curve(double[] c, int size) {
        return c[Math.min(c.length - 1, Math.max(0, size - 1))];
    }

    public static double sizeFactor(int size)     { return curve(SIZE_DMG, size); }
    public static double costSizeFactor(int size) { return curve(SIZE_COST, size); }

    /** AUTORIDAD ÚNICA del tiempo de carga. El tipo pone la base y el tamaño la estira: es lo
     *  que impide que una bola enorme salga tan rápido como una pequeña. Doce sitios entre
     *  servidor, HUD, predicción y editor tienen que pasar por aquí o el jugador verá una
     *  barra que no coincide con lo que el servidor cobra. */
    public static int chargeTicksFor(KiTechniqueType type, int size) {
        return Math.max(1, (int) Math.round(type.chargeTicks() * curve(SIZE_CAST, size)));
    }

    /** Ídem para el enfriamiento. */
    public static int cooldownTicksFor(KiTechniqueType type, int size) {
        return Math.max(0, (int) Math.round(type.cooldownTicks() * curve(SIZE_CD, size)));
    }

    /** Techo de carga: 2.0 = 200%. Como el daño escala lineal con el ratio, sobrecargar
     *  al máximo dobla el daño del disparo. */
    public static final double MAX_CHARGE = 2.0;

    /** Daño por proyectil a carga completa. */
    public static double computeDamage(double kiPower, KiTechniqueType type, int size) {
        return kiPower * type.damageMult() * sizeFactor(size);
    }

    /** Coste de ki del disparo a carga completa. Escala con el PODER (WIL), no con el pool:
     *  así subir WIL sube daño Y coste, y SPI (el pool) decide cuántas veces lo sostienes.
     *  Recibe el objeto de stats y no el kiPower suelto para que el multiplicador de
     *  raza/estilo no se pueda olvidar en ningún call site. */
    public static int computeCost(ZenkaiCombatStats stats, KiTechniqueType type, int size,
                                  TechniqueEffect effect) {
        return (int) Math.ceil(stats.computeKiPowerFinal() * CommonConfig.kiCostPerPower()
                * stats.kiCostMult()
                * type.kiCostMult() * costSizeFactor(size)
                * (effect == null ? 1.0 : effect.costMult()));
    }

    /**
     * Fracción del CUERPO MÁXIMO que se cobra a sí mismo quien detona una EXPLOSION.
     *   f = 0.10 + 0.90 · ((ratio − MIN_CHARGE) / (MAX_CHARGE − MIN_CHARGE))²
     * Cuadrática a propósito: cargar poco casi no duele y el precio se dispara en el tramo de
     * sobrecarga. Vale EXACTAMENTE 1.0 en el tope, así que a 200 % el golpe es letal sin
     * ningún escalón artificial — no es "a partir de aquí mueres", es que a esa carga te has
     * gastado entero. Que morir signifique quedar ABATIDO y no muerto no se decide aquí: lo
     * hace DownedDeathGuard interceptando LivingDeathEvent, como con cualquier otra muerte.
     * IGNORA LA DEFENSA en el sitio donde se aplica: es daño verdadero sobre el cuerpo, no un
     * golpe recibido; si pasara por computeDefenseFinal un tanque se autodetonaría gratis.
     */
    public static double selfDamageFraction(double ratio) {
        double t = (Math.min(MAX_CHARGE, Math.max(MIN_CHARGE_D, ratio)) - MIN_CHARGE_D)
                / (MAX_CHARGE - MIN_CHARGE_D);
        return 0.10 + 0.90 * t * t;
    }

    /**
     * Daño extra que aporta la vida sacrificada en una autodetonación.
     * POR QUÉ EXISTE
     * --------------
     * El daño de una técnica de ki sale de WIL y el autodaño sale de CON. Sin este término, un
     * build de CON alto paga un número absoluto enorme y devuelve una miseria: el precio lo
     * pone una estadística y el premio otra. Convirtiendo el cuerpo gastado en daño, quien
     * tiene vida que quemar se vuelve el mejor detonador, que es lo que el concepto pedía.
     * OJO A LA MAGNITUD: EXPLOSION reparte aoeFactor 1.00 con 65 % en el borde y radio de hasta
     * 18 bloques, así que este término lo cobra ENTERO cada objetivo del área. Subir con
     * cuidado y probando en grupo, no en un muñeco.
     */
    public static double explosionSacrificeDamage(int bodySpent) {
        return Math.max(0, bodySpent) * CommonConfig.explosionSacrificeConversion();
    }

    private static final double MIN_CHARGE_D = KiTechniqueType.MIN_CHARGE;

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
    /**
     * ¿Puede disparar ese slot AHORA? Consulta pura: no registra nada ni arranca cooldowns.
     * Espeja exactamente las dos comprobaciones de tryFire (global anti-spam + por slot);
     * si una cambia, la otra tiene que cambiar con ella — por eso están pegadas.
     * La usan ActionRules (para dar el motivo correcto del rechazo) y KiChargeServer.start
     * (para no dejar cargar una técnica que al soltar iba a rebotar por cooldown).
     */
    public static boolean isReady(ServerPlayer sp, int slot) {
        long now = sp.level().getGameTime();

        Long last = LAST_FIRE.get(sp.getUUID());
        if (last != null && now - last < GLOBAL_COOLDOWN_TICKS) return false;

        Map<Integer, Long> slots = SLOT_READY_AT.get(sp.getUUID());
        if (slots == null) return true;
        Long readyAt = slots.get(slot);
        return readyAt == null || now >= readyAt;
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
                0, BARRIER_DURATION_TICKS, TechniqueEffect.NONE);
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

    /** Aplica la mecánica de defensa. NO VALIDA ni registra estado: ambas cosas las hizo
     *  ActionResolver. Aquí solo el modificador de velocidad y el aviso a los trackers. */
    public static void applyBlocking(ServerPlayer sp, boolean blocking) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);

        // Idempotencia a partir del propio modificador, que es el efecto real, en vez de un
        // booleano guardado aparte. Si ya está como se pide, no hay nada que hacer ni que
        // difundir.
        boolean had = speed != null && speed.getModifier(BLOCK_SLOW_ID) != null;
        if (had == blocking) return;

        if (speed != null) {
            speed.removeModifier(BLOCK_SLOW_ID);
            if (blocking) speed.addTransientModifier(BLOCK_SLOW);
        }

        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new BlockingSyncPacket(sp.getId(), blocking));
    }

    /** ¿Está defendiendo? Fuente única: ActionState. */
    public static boolean isBlocking(ServerPlayer sp) {
        return com.hmc.zenkai.feature.action.ActionStateServer.get(sp).type()
                == com.hmc.zenkai.feature.action.ActionType.BLOCK;
    }
}