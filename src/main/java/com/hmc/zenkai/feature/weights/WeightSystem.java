package com.hmc.zenkai.feature.weights;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.gravity.GravityFields;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.registry.ModStructureSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * ÚNICO dueño de las matemáticas de las pesas. Nadie más calcula capacidad, carga,
 * penalización ni bono: cada uno de los sitios (tick, movimiento, vuelo, TP, GUI, pantalla de
 * ajuste) leen de aquí. Si algún día cambia la curva, cambia en un solo archivo.
 *
 * Modelo:
 *   carga r  = (toneladas equipadas + toneladas ambientales) / capacidad
 *   capacidad = (PL_LIMPIO / divisor) ^ exponente          [toneladas]
 *
 * PL_LIMPIO = con forma y kaioken, SIN el factor de pesas. Es obligatorio: la capacidad
 * depende del PL y el PL depende de la penalización, así que usar el PL penalizado crearía
 * un bucle que converge a cualquier cosa.
 *
 * "Toneladas ambientales" ({@link #ambientTons}) es la gravedad de un SITIO (el planeta de
 * Kaiosama, la dimensión de la HTC) o de una cámara de gravedad, convertida con
 * {@link #tonsForGravity} — ver .claude/docs/pesas-y-gravedad.md: un
 * jugador ahí SUMA esa carga a la de su equipo, en vez de tener una curva de penalización propia
 * — un jugador con pesas físicas puestas y de pie en uno de esos sitios sufre las dos a la vez,
 * como en el canon.
 *
 * Penalizaciones (lineales en r, con r CLAMPADO al umbral de sobrecarga para que llevar
 * 500x tu capacidad no te deje en stats negativos):
 *   stats     x (1 - stat_penalty * r)   -> entra en PlayerStatsAttachment.weightFactor,
 *                                           afecta melee/defensa/ki power y por tanto el PL
 *   movimiento x (1 - move_penalty * r)  -> suelo, vuelo y turbo
 *   salto      x (1 - jump_penalty * r)
 *   TP         x (1 + tp_bonus * r)
 *
 * Sobrecarga (r > umbral): movimiento clavado al factor de arrastre, salto y vuelo
 * anulados, y el bono de TP cae a 1.0 — la carga que no puedes mover no entrena.
 */
public final class WeightSystem {
    private WeightSystem() {}

    /** Id del slot de Curios declarado en data/zenkai/curios/slots/weight.json. */
    public static final String CURIOS_SLOT = "weight";

    // ── Carga equipada ───────────────────────────────────────────────────────

    /** Toneladas totales: pecho + slot de Curios. Se SUMAN (las dos pesas son acumulables). */
    public static double equippedTons(Player p) {
        if (p == null) return 0.0;
        double t = 0.0;
        t += tonsOf(p.getItemBySlot(EquipmentSlot.CHEST));
        t += tonsOf(CuriosCompat.findEquipped(p, CURIOS_SLOT));
        return t;
    }

    private static double tonsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        if (!(stack.getItem() instanceof WeightArmorItem w)) return 0.0;
        return w.getTons(stack);
    }

    // ── Gravedad ambiental (planeta de Kaiosama, HTC) ─────────────────────────

    /** Caja del planeta de Kaiosama, MISMA que usa {@code protector.zenkai.kaiosama}
     *  (ver {@link ModStructureSegments#KAIO_NO_SPAWN_MIN}) — reusar esos números en vez de
     *  una caja propia evita que "estás protegido por Kaiosama" y "sufres su gravedad" puedan
     *  desincronizarse algún día. Geometría pura por posición+dimensión y NO
     *  {@code ProtectedZones.protectorAt} a propósito: esto se llama también desde CLIENTE
     *  (KeyBindings.handleClientTick espeja weightLoad ahí, ver su comentario), donde el nivel
     *  es un ClientLevel y no hay ServerLevel del que preguntarle a ProtectedZones. */
    public static boolean isOnKaiosamaPlanet(Player p) {
        if (p == null) return false;
        if (!p.level().dimension().equals(ModDimensions.OTHERWORLD_LEVEL)) return false;
        BlockPos min = ModStructureSegments.KAIO_NO_SPAWN_MIN;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        return x >= min.getX() && x < min.getX() + ModStructureSegments.KAIO_NO_SPAWN_SX
                && y >= min.getY() && y < min.getY() + ModStructureSegments.KAIO_NO_SPAWN_SY
                && z >= min.getZ() && z < min.getZ() + ModStructureSegments.KAIO_NO_SPAWN_SZ;
    }

    /** La HTC (Habitación del Tiempo) tiene gravedad propia como DIMENSIÓN, distinto de
     *  Kaiosama (que es un punto concreto dentro del Otherworld): toda la dimensión cuenta, sin
     *  caja — igual que ya hace {@code inHtc} en TrainingHubScreen para el multiplicador de TP.
     *  Corrección del usuario (2026-09-10): esto es gravedad INHERENTE a la dimensión, no algo
     *  que dependa de la cámara de gravedad (feature/gravity) — son cosas separadas aunque una
     *  cámara se pueda construir DENTRO de la HTC (entonces manda la g mayor). */
    public static boolean isInHtc(Player p) {
        return p != null && p.level().dimension().equals(ModDimensions.HTC_LEVEL);
    }

    /** Toneladas que pesa una gravedad g: {@code k × (g − 1)} (k = 4,44; diseño de la Fase 3 de
     *  la 0.7.0). ×10 = 40 t, ×500 = 2 216 t. La ÚNICA conversión g → toneladas del mod. */
    public static double tonsForGravity(double g) {
        return ServerConfig.gravityTonsPerG() * Math.max(0.0, g - 1.0);
    }

    /** g del SITIO (planeta de Kaiosama ×10, HTC ×2), sin contar cámaras. Kaiosama y la HTC
     *  son dimensiones distintas: nunca coinciden. */
    private static double siteGravity(Player p) {
        if (isOnKaiosamaPlanet(p)) return ServerConfig.weightKaiosamaGravityMultiplier();
        if (isInHtc(p)) return ServerConfig.weightHtcGravityMultiplier();
        return 1.0;
    }

    /** g efectiva: la MAYOR entre la del sitio y la de una cámara de gravedad. Las gravedades
     *  no se suman: una cámara montada dentro de la HTC a ×50 da ×50, no ×51. Es a la vez la
     *  etiqueta «Gravedad: xN» del hub y la fuente de las toneladas ({@link #ambientTons}), así
     *  que nunca se contradicen (antes eran dos números sueltos en config). */
    public static double gravityMultiplier(Player p) {
        return Math.max(siteGravity(p), GravityFields.chamberG(p));
    }

    /** Toneladas "ambientales" (gravedad, no equipo) que le tocan a este jugador ahora mismo.
     *  0 a gravedad normal. */
    public static double ambientTons(Player p) {
        return tonsForGravity(gravityMultiplier(p));
    }

    /** Clave de traducción de la fuente de gravedad activa ahora mismo, o null si ninguna
     *  (gravedad normal). Solo la usa el tooltip de la fila genérica del hub. */
    public static String gravitySourceNameKey(Player p) {
        double site = siteGravity(p);
        double chamber = GravityFields.chamberG(p);
        if (chamber > 1.0 && chamber >= site) return "screen.zenkai.training_hub.panel.gravity_source.chamber";
        if (isOnKaiosamaPlanet(p)) return "screen.zenkai.training_hub.panel.gravity_source.kaiosama";
        if (isInHtc(p)) return "screen.zenkai.training_hub.panel.gravity_source.htc";
        return null;
    }

    /** r para cualquier entidad (mobs en la cámara): toneladas / capacidad de su PL. */
    public static double loadFor(long pl, double tons) {
        if (tons <= 0.0) return 0.0;
        return tons / capacityTons(pl);
    }

    // ── Capacidad y carga ────────────────────────────────────────────────────

    /** Capacidad en toneladas para un PL limpio dado. Nunca 0 (evita división por cero). */
    public static double capacityTons(long cleanPl) {
        double div = Math.max(0.0001, ServerConfig.weightCapacityDivisor());
        double exp = ServerConfig.weightCapacityExponent();
        double base = Math.max(1.0, cleanPl) / div;
        return Math.max(0.01, Math.pow(base, exp));
    }

    /** r = (toneladas equipadas + toneladas ambientales) / capacidad. 0 si no lleva pesas y no
     *  está en ningún planeta con gravedad propia. */
    public static double computeLoad(Player p) {
        double tons = equippedTons(p) + ambientTons(p);
        if (tons <= 0.0) return 0.0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) return 0.0;
        return tons / playerCapacity(att);
    }

    /**
     * Capacidad de un JUGADOR: {@code (PL base / divisor)^exponente × multiplicador de forma}.
     * Pedido del usuario (2026-11-20): «la gravedad no puede afectarme igual en base que en SSJ o en
     * God». Antes la forma solo entraba por el PL limpio, y salía doblemente diluida: el PL multiplica
     * por la forma tres de sus cinco términos (CON y la reserva de ki no cambian) y luego el exponente
     * 0,6 lo aplastaba, así que una forma ×2,5 aguantaba ~1,4 veces más. Ahora la forma (y el Kaioken,
     * que también va en statMultiplier) multiplica la capacidad ENTERA: en ×2,5 aguantas 2,5 veces el
     * peso. El PL base (sin forma) sigue llevando la progresión por entrenamiento, con su exponente.
     * En base (multiplicador 1) no cambia nada: Kaiosama y la HTC siguen calibradas igual.
     */
    public static double playerCapacity(PlayerStatsAttachment att) {
        double mult = Math.max(0.1, att.getStatMultiplier());
        return capacityTons(att.getPowerLevelWithStatMultiplier(1.0)) * mult;
    }

    /** r usado SOLO para el salto — igual que {@link #computeLoad} pero la parte AMBIENTAL
     *  (gravedad, no equipo) cuenta con el factor {@link ServerConfig#weightGravityJumpFactor}
     *  en vez de al 100%. Pedido explícito del usuario (2026-09-10): la gravedad debe seguir
     *  frenando el movimiento y dando bono de TP al completo, pero no debe aplastar el salto
     *  tanto como cargar equipo físico de verdad — a PL 20k con 100t de Kaiosama (r combinado
     *  ~0.55) el salto se sentía "aún no por completo mitigado". Con el default 0.25 ese mismo caso
     *  pasa a r≈0.14 solo para el salto, mientras que movimiento/stats/TP siguen usando la r
     *  completa de {@link #computeLoad}. El equipo físico NO se toca: sigue contando al 100%
     *  para el salto, como antes. */
    public static double jumpLoad(Player p) {
        double tons = equippedTons(p) + ambientTons(p) * ServerConfig.weightGravityJumpFactor();
        if (tons <= 0.0) return 0.0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) return 0.0;
        return tons / playerCapacity(att);
    }

    /** PL BASE (sin forma) mínimo para que un peso dado quede en r = 1 (lo muestra la pantalla de ajuste). */
    public static long plForTons(double tons) {
        if (tons <= 0.0) return 0L;
        double div = Math.max(0.0001, ServerConfig.weightCapacityDivisor());
        double exp = ServerConfig.weightCapacityExponent();
        if (exp <= 0.0) return 0L;
        return Math.max(1L, Math.round(Math.pow(tons, 1.0 / exp) * div));
    }

    // ── Factores derivados ───────────────────────────────────────────────────

    public static boolean isOverloaded(double load) {
        return load > ServerConfig.weightOverloadThreshold();
    }

    /** r acotado al umbral: pasado el umbral la penalización ya no crece, la sobrecarga manda. */
    private static double effective(double load) {
        return Math.min(Math.max(0.0, load), ServerConfig.weightOverloadThreshold());
    }

    /** Multiplicador de melee / defensa / ki power (y por tanto del PL mostrado). */
    public static double statFactor(double load) {
        return Math.max(0.05, 1.0 - ServerConfig.weightStatPenalty() * effective(load));
    }

    /** Multiplicador de velocidad de suelo, vuelo y turbo. */
    public static double moveFactor(double load) {
        if (isOverloaded(load)) return ServerConfig.weightOverloadMoveFactor();
        return Math.max(0.0, 1.0 - ServerConfig.weightMovePenalty() * effective(load));
    }

    /** Multiplicador de altura de salto. 0 en sobrecarga: no despegas del suelo. */
    public static double jumpFactor(double load) {
        if (isOverloaded(load)) return 0.0;
        return Math.max(0.0, 1.0 - ServerConfig.weightJumpPenalty() * effective(load));
    }

    /** Multiplicador de cualquier ganancia de TP. 1.0 en sobrecarga: la carga muerta no entrena. */
    public static double tpFactor(double load) {
        if (isOverloaded(load)) return 1.0;
        return 1.0 + ServerConfig.weightTpBonus() * effective(load);
    }
}