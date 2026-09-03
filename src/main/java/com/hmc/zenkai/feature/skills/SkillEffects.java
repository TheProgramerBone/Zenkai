package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Consulta centralizada de los efectos de habilidad (el equivalente a MasteryEffects).
 * Cada número sale de las curvas del datapack vía SkillDef#value, así que
 * balancear es editar el JSON y hacer /reload, nunca tocar esta clase.
 */
public final class SkillEffects {
    private SkillEffects() {}

    public static final String FLY = "fly";
    public static final String RUN = "run";
    public static final String KI_CONTROL = "ki_control";
    public static final String MEDITATION = "meditation";
    public static final String KI_BLOCK = "ki_block";
    public static final String KI_SENSE = "ki_sense";
    public static final String KAIOKEN = "kaioken";
    public static final String KI_INFUSE = "ki_infuse";
    public static final String KI_FIST   = "ki_fist";
    public static final String INSTANT_TRANSMISSION = "instant_transmission";
    /** No es una habilidad comprable: se desbloquea por tener Ki Fist + Ki Infuse. */
    public static final String POTENTIAL_UNLOCK = "potential_unlock";
    /** No son habilidades comprables: se desbloquean por tener Ki Fist + Ki Infuse. */
    public static final String KI_BLADE = "ki_blade";
    public static final String KI_SCYTHE = "ki_scythe";

    public static int level(Player p, String skillId) {
        if (p == null) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att.skills().level(skillId);
    }

    /** Valor de una curva en el nivel actual; devuelve fallback si no tiene la habilidad. */
    private static double curve(Player p, String skillId, String key, double fallback) {
        int lvl = level(p, skillId);
        if (lvl <= 0) return fallback;
        SkillDef def = SkillDef.get(skillId);
        return def == null ? fallback : def.value(key, lvl, fallback);
    }

    // ── Fly ──────────────────────────────────────────────────────────────────
    /** Sin la habilidad no se vuela: el nivel 1 lo enseña Kami. */
    public static boolean canFly(Player p) { return level(p, FLY) > 0; }

    /** Multiplicador del drenaje de ki al volar (1.0 = coste completo). */
    public static double flyKiDrainFactor(Player p) { return curve(p, FLY, "ki_cost_mult", 1.0); }

    /** Multiplicador de velocidad de vuelo por nivel. */
    public static double flySpeedFactor(Player p) { return curve(p, FLY, "speed_mult", 1.0); }

    // ── Run ──────────────────────────────────────────────────────────────────
    public static double runStaminaDrainFactor(Player p) { return curve(p, RUN, "stamina_cost_mult", 1.0); }

    public static double runSpeedFactor(Player p) { return curve(p, RUN, "speed_mult", 1.0); }

    /** Misma fórmula leyendo el attachment directamente. La necesita el PL liberable, que
     *  se calcula dentro del propio attachment y no tiene un Player a mano. */
    public static int maxPowerPercent(PlayerStatsAttachment att) {
        return 50 + 5 * att.skills().level(KI_CONTROL);
    }

    public static int maxPowerPercent(Player p) {
        return maxPowerPercent(PlayerStatsAttachment.get(p));
    }

    // ── Meditación ───────────────────────────────────────────────────────────
    /** EL GATE: sin Meditación NO se recupera ki cargando con C. El % de poder sí sube
     *  igual: concentrarse siempre funciona, canalizar el ki es lo que hay que aprender. */
    public static boolean canChargeKi(Player p) { return level(p, MEDITATION) > 0; }

    /** Multiplicador del regen PASIVO de ki. 1.0 sin la habilidad (mínimo vital). */
    public static double kiRegenFactor(Player p) { return curve(p, MEDITATION, "regen_mult", 1.0); }

    /** Multiplicador de la carga con C sobre el regen base por segundo. 0 = no puede cargar. */
    public static double kiChargeFactor(Player p) { return curve(p, MEDITATION, "charge_mult", 0.0); }

    // ── Ki Block ─────────────────────────────────────────────────────────────
    /** Fracción del daño que se CORTA al bloquear: 0.20 sin la habilidad, 0.50 a nivel 5.
     *  Bloquear siempre sirve de algo; la habilidad es lo que lo hace fiable. */
    public static double blockReduction(Player p) {
        return curve(p, KI_BLOCK, "block_reduction", 0.20);
    }

    /** Multiplicador de daño RECIBIDO al bloquear. Menos es mejor. */
    public static double blockDamageMultiplier(Player p) {
        return Math.max(0.0, 1.0 - blockReduction(p));
    }

    // ── Ki Sense ─────────────────────────────────────────────────────────────
    public static int senseLevel(Player p) { return level(p, KI_SENSE); }

    /** Multiplicador del rango de sentido. 1.0 sin la habilidad. */
    public static double senseRangeFactor(Player p) { return curve(p, KI_SENSE, "range_mult", 1.0); }

    /** La vida se lee en el llenado de la llama desde el nivel 1; a partir del 2 es continua
     *  en vez de por tramos. Nunca hay barra: el ki sense no dibuja rectángulos. */
    public static boolean sensePreciseHealth(Player p) { return level(p, KI_SENSE) >= 2; }

    /** Siluetas a través de paredes. */
    public static boolean senseShowsSilhouettes(Player p) { return level(p, KI_SENSE) >= 3; }

    /** Cuántas siluetas como mucho a la vez. 0 = ninguna. */
    public static int senseSilhouetteCap(Player p) {
        return (int) Math.round(curve(p, KI_SENSE, "silhouette_cap", 0.0));
    }

    /** Radio de las siluetas como FRACCIÓN del rango del sentido. */
    public static double senseSilhouetteRangeFrac(Player p) {
        return curve(p, KI_SENSE, "silhouette_range_frac", 0.0);
    }

    /** Avisos no visuales. */
    public static boolean senseShowsWarnings(Player p) { return level(p, KI_SENSE) >= 5; }

    // ── Kaioken ──────────────────────────────────────────────────────────────
    public static int kaiokenLevel(Player p) { return level(p, KAIOKEN); }

    /** Multiplicador del drenaje de vida. 1.0 sin maestría, 0.35 a nivel 10. */
    public static double kaiokenDrainFactor(Player p) {
        return curve(p, KAIOKEN, "drain_factor", 1.0);
    }

    /** Escalón más alto que este jugador puede usar. */
    public static KaiokenTier maxKaioken(Player p) {
        return KaiokenTier.highestFor(level(p, KAIOKEN));
    }

    /** true si el jugador NO puede fijar objetivo (sin la habilidad). Se llamaba canLockOn
     *  y devolvía justo lo contrario de lo que su nombre prometía. */
    public static boolean lockOnBlocked(Player p) { return level(p, KI_SENSE) <= 0; }

    // ── Ki Infuse ────────────────────────────────────────────────────────────
    /** Fracción de la potencia de ki (WIL) que suma un golpe infusionado. 0 sin la habilidad. */
    public static double kiInfuseFactor(Player p) {
        return curve(p, KI_INFUSE, "damage_factor", 0.0);
    }

    // ── Ki Fist ──────────────────────────────────────────────────────────────
    /** Fracción del SPI (en escala de melee) que suma un golpe con los puños de ki. */
    public static double kiFistFactor(Player p) {
        return curve(p, KI_FIST, "damage_factor", 0.0);
    }

    // ── Instant Transmission ─────────────────────────────────────────────────
    /** Nivel real, o 0 si el admin apagó la técnica entera por config
     *  (ServerConfig.instantTransmissionEnabled) — ÚNICO gate de config que vive en esta clase,
     *  a propósito: cada otra consulta de la técnica (blink, menú, HUD) ya deriva de este nivel,
     *  así que apagarlo aquí basta para inutilizarla entera sin tener que repetir el chequeo en
     *  cada sitio que la usa, aunque el jugador ya la hubiera comprado antes de que se apagara. */
    public static int instantTransmissionLevel(Player p) {
        return ServerConfig.instantTransmissionEnabled() ? level(p, INSTANT_TRANSMISSION) : 0;
    }

    /** Ki que cuesta un blink en el nivel actual. Fallback alto (nivel 1) por si faltara la
     *  curva: mejor caro que gratis. */
    public static double instantTransmissionKiCost(Player p) {
        return curve(p, INSTANT_TRANSMISSION, "ki_cost", 40.0);
    }

    /** Cooldown tras un blink, en ticks. */
    public static int instantTransmissionCooldownTicks(Player p) {
        return (int) Math.round(curve(p, INSTANT_TRANSMISSION, "cooldown_ticks", 200.0));
    }

    /** Rango del blink (lock-on o raytrace), en bloques. Sube con el nivel hasta un máximo de
     *  64 en el nivel más alto — pedido explícito del usuario. */
    public static double instantTransmissionRange(Player p) {
        return curve(p, INSTANT_TRANSMISSION, "range", 8.0);
    }

    // ── Fase 2: umbrales de progresión del menú (fijos, no curvas del datapack — son
    // desbloqueos puntuales, no números que escalen gradualmente) ──────────────────────────
    private static final int MENU_MIN_LEVEL = 3;
    private static final int CROSS_DIMENSION_MIN_LEVEL = 6;
    private static final int PARTY_MIN_LEVEL = 8;

    /** Nivel 3+: mantener TAB quieto 2s arma el menú de planetas en vez de solo cancelar el
     *  blink (por debajo de este nivel, quedarse quieto no hace nada especial). Además del
     *  nivel, exige ServerConfig.instantTransmissionMenuEnabled — un segundo toggle
     *  INDEPENDIENTE de instantTransmissionEnabled (ver instantTransmissionLevel): un admin
     *  puede apagar SOLO el menú (saltos entre dimensiones, TP a party) sin tocar el blink
     *  básico de nivel 1, que sigue funcionando igual. */
    public static boolean instantTransmissionMenuUnlocked(Player p) {
        return ServerConfig.instantTransmissionMenuEnabled() && instantTransmissionLevel(p) >= MENU_MIN_LEVEL;
    }

    /** Nivel 6+: el menú permite elegir destinos en OTRA dimensión de la actual (un salto de IDA
     *  de verdad, no un TP dentro de la misma dimensión ni un regreso al Overworld — esos dos NO
     *  necesitan este nivel, ver TeleportDestination.executableThisPhase). Por debajo de este
     *  nivel esas filas se ven pero bloqueadas con el tooltip "requiere nivel %s". */
    public static boolean instantTransmissionCrossDimensionUnlocked(Player p) {
        return instantTransmissionLevel(p) >= CROSS_DIMENSION_MIN_LEVEL;
    }

    /** El número crudo detrás de {@link #instantTransmissionCrossDimensionUnlocked}, para el
     *  tooltip "screen.zenkai.instant_transmission.locked.level" (necesita el número real, no
     *  solo el booleano) — InstantTransmissionMenuScreen es el único consumidor hoy. */
    public static int crossDimensionMinLevel() { return CROSS_DIMENSION_MIN_LEVEL; }

    /** Nivel 8+: TP a miembros de party — su posición EN VIVO, sin descubrimiento previo (ver
     *  PartyTeleportRequestPacket/TeleportAnchors, que no lo cubre por ser el único destino sin
     *  ancla fija). */
    public static boolean instantTransmissionPartyUnlocked(Player p) {
        return instantTransmissionLevel(p) >= PARTY_MIN_LEVEL;
    }

    /** El número crudo detrás de {@link #instantTransmissionPartyUnlocked}, para el tooltip
     *  "screen.zenkai.instant_transmission.locked.level" — InstantTransmissionMenuScreen es el
     *  único consumidor hoy (mismo patrón que crossDimensionMinLevel). */
    public static int partyMinLevel() { return PARTY_MIN_LEVEL; }
}