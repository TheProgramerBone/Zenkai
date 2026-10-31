package com.hmc.zenkai.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

/**
 * Config de servidor: ModConfig.Type.SERVER, un archivo POR MUNDO (vive en el save, bajo
 * {@code serverconfig/}) que NeoForge SINCRONIZA automáticamente al cliente al conectarse. Aquí
 * va cualquier número/ajuste de gameplay que un admin de server o modpack quiera tunear por
 * mundo y que el cliente necesite ver correctamente sincronizado — que es, en la práctica, casi
 * todo el balance del mod (combate, economía de TP, entrenamiento, maestría, vuelo, pools...).
 * Antes de 2026-09-04 ese balance vivía en {@link CommonConfig} (Type.COMMON: un archivo POR
 * INSTALACIÓN, cliente y servidor leen cada uno su PROPIA copia, nunca se sincroniza) — eso ya
 * causó un bug real parcheado a mano (ver {@code feature/stats/TpCurve.java} antes de esa fecha
 * en el histórico de git): con la config retocada en el servidor, el cliente calculaba costes de
 * TP con SU PROPIO archivo, así que la pantalla de stats mostraba un precio que no era el que el
 * servidor iba a cobrar de verdad. La migración a SERVER hace que ese tipo de bug sea imposible
 * por construcción: solo hace falta tunear el archivo del mundo, y todo el que se conecta ve el
 * valor real, sin instalar nada.
 * DOS CONVENCIONES DE ACCESO CONVIVEN EN ESTE ARCHIVO — mirar cuál sigue una opción antes de
 * copiarle el patrón a una nueva:
 * - **Hot-path** (todo lo migrado desde CommonConfig, y cualquier opción nueva leída muchas veces
 *   por tick o por golpe): sigue el patrón RAW + copia volátil que refresca {@code onConfigLoad},
 *   igual que documentaba CommonConfig — el resto del código lee SOLO el getter de la copia
 *   volátil, nunca `_RAW.get()` directamente. El valor inicial de cada copia volátil DEBE
 *   coincidir con el default de su `_RAW` (es lo que está vivo durante el arranque, antes de que
 *   la config cargue).
 * - **Baja frecuencia** (los toggles/precios/cooldowns/listas que ya vivían aquí: deseos,
 *   invocación, Transmisión Instantánea): siguen leyendo `.get()` directo en cada llamada — no
 *   son hot-path, así que el coste de `ConfigValue.get()` es irrelevante y añadir una copia
 *   volátil sería ceremonia sin beneficio. Las tres listas (`STACK_OVERRIDES`, `BANNED_ITEMS`,
 *   `WHITELIST_ITEMS`) sí cachean, pero porque necesitan parseo real (String -> ResourceLocation),
 *   no por frecuencia.
 */
public class ServerConfig {
    private ServerConfig() {}

    /** Tipos de deseo, para habilitar/deshabilitar y cualquier lógica genérica. */
    public enum WishType { STACK, REVIVE_PLAYER, ENCHANT_VILLAGER, IMMORTAL, TRAINING_POINTS, LEGENDARY }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Toggles por deseo ──────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLE_STACK;
    public static final ModConfigSpec.BooleanValue ENABLE_REVIVE_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANT_VILLAGER;
    public static final ModConfigSpec.BooleanValue ENABLE_IMMORTAL;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAINING_POINTS;
    public static final ModConfigSpec.BooleanValue ENABLE_LEGENDARY;

    // ── Stack wish ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_RACES_RAW;
    public static final ModConfigSpec.BooleanValue ALLOW_ABOVE_MAX;
    public static final ModConfigSpec.IntValue GLOBAL_HARD_CAP;

    // ── Enchant villager ───────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue VILLAGER_BOOK_BASE_PRICE;
    public static final ModConfigSpec.IntValue VILLAGER_BOOK_PRICE_PER_LEVEL;

    // ── Stack whitelist (7) ─────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST_ITEMS_RAW;

    // ── Training points (3) ─────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue TRAINING_POINTS_AMOUNT;

    // ── Invocación: cooldown por jugador (1) + deseos por entidad (2) ───────────
    public static final ModConfigSpec.IntValue SUMMON_COOLDOWN_DAYS;
    public static final ModConfigSpec.IntValue SHENLONG_WISHES;
    public static final ModConfigSpec.IntValue NAMEK_DRAGON_WISHES;
    public static final ModConfigSpec.IntValue BROADCAST_RADIUS;

    // ── Transmisión Instantánea: dos toggles independientes (2) ─────────────────
    public static final ModConfigSpec.BooleanValue INSTANT_TRANSMISSION_ENABLED;
    public static final ModConfigSpec.BooleanValue INSTANT_TRANSMISSION_MENU_ENABLED;

    // ── Minijuegos de Training (Meditation/Ki Target Practice): opción de baja
    // frecuencia (.get() directo, sin caché volátil) — se leen UNA VEZ al final de cada
    // sesión, no en hot-path, a diferencia del resto de training.* (ver Config pattern en
    // CLAUDE.md sobre por qué esta sección concreta NO copia el patrón RAW+volátil de sus
    // vecinos aunque comparta el mismo "training" temático). El cliente reporta desempeño
    // CRUDO (notas/combo/orbes + duración), nunca un TP ya calculado — estos valores son el
    // tope/tarifa que el SERVIDOR aplica sobre ese desempeño (ver TrainingHooks.grantFromMeditation/
    // grantFromTargetPractice), mismo principio anti-trampa que training.air_tp_factor.
    public static final ModConfigSpec.DoubleValue MEDITATION_TP_PER_COMBO;
    public static final ModConfigSpec.IntValue MEDITATION_SESSION_TP_CAP;
    public static final ModConfigSpec.IntValue MEDITATION_MIN_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue TARGET_PRACTICE_TP_PER_ORB;
    public static final ModConfigSpec.IntValue TARGET_PRACTICE_SESSION_TP_CAP;
    public static final ModConfigSpec.IntValue TARGET_PRACTICE_MIN_COOLDOWN_TICKS;

    static {
        BUILDER.comment("Enables or disables each Shenlong wish individually.")
                .push("wishes_enabled");
        ENABLE_STACK            = BUILDER.comment("Wish: item stack.").define("stack", true);
        ENABLE_REVIVE_PLAYER    = BUILDER.comment("Wish: revive a player.").define("revive_player", true);
        ENABLE_ENCHANT_VILLAGER = BUILDER.comment("Wish: enchanted villager (sells an enchanted book).").define("enchant_villager", true);
        ENABLE_IMMORTAL         = BUILDER.comment("Wish: immortality.").define("immortal", true);
        ENABLE_TRAINING_POINTS  = BUILDER.comment("Wish: training points (TP).").define("training_points", true);
        ENABLE_LEGENDARY        = BUILDER.comment("Wish: become Legendary (any race: unlocks the Saiyan Legendary line or the race's own Legendary form).").define("legendary", true);
        BUILDER.pop();

        BUILDER.push("stack_wish");
        STACK_OVERRIDES_RAW = BUILDER
                .comment("Overrides the amount of the item given with this format: 'namespace:item=count'. Ex: minecraft:diamond=32")
                .defineListAllowEmpty("stack_overrides",
                        List.of("minecraft:ender_pearl=32"),
                        () -> "",
                        obj -> true);
        BANNED_ITEMS_RAW = BUILDER
                .comment("List of items banned from wishes. Ex: minecraft:shulker_box",
                         "Items with the tag zenkai:wish_banned (Dragon Balls, the fused seven, the ball stones) are ALWAYS banned too.")
                .defineListAllowEmpty("banned_items",
                        List.of("minecraft:shulker_box"),
                        () -> "",
                        obj -> true);
        BANNED_RACES_RAW = BUILDER
                .comment("Races players CANNOT pick in the race menu (names: human, saiyan, namekian, arcosian, majin).",
                         "Only blocks choosing: characters that already have the race keep it. Majin is banned by default",
                         "until its art direction is defined (see .claude/pendiente/pendiente-majin.md).")
                .defineListAllowEmpty("banned_races",
                        List.of("majin"),
                        () -> "",
                        obj -> true);
        WHITELIST_ITEMS_RAW = BUILDER
                .comment("Whitelist of items allowed in the stack wish.",
                            "If EMPTY, any item is allowed except the blacklist.",
                            "If it has items, ONLY those are allowed (the blacklist still takes priority).")
                .defineListAllowEmpty("whitelist_items",
                        List.of(),
                        () -> "",
                        obj -> true);
        ALLOW_ABOVE_MAX = BUILDER
                .comment("Allow Overrides above max (ex: get 64 ender pearls instead of the max stack size of 16).")
                .define("allow_overrides_above_max", true);
        GLOBAL_HARD_CAP = BUILDER
                .comment("Global hard cap override (security).")
                .defineInRange("global_hard_cap", 4096, 1, 65535);
        BUILDER.pop();

        BUILDER.push("enchant_villager");
        VILLAGER_BOOK_BASE_PRICE = BUILDER
                .comment("Base price (in emeralds) of the villager's enchanted book.")
                .defineInRange("base_price", 10, 0, 9999);
        VILLAGER_BOOK_PRICE_PER_LEVEL = BUILDER
                .comment("Extra price per enchantment level.")
                .defineInRange("price_per_level", 5, 0, 9999);
        BUILDER.pop();

        BUILDER.push("training_points");
        TRAINING_POINTS_AMOUNT = BUILDER
                .comment("Amount of Training Points granted by the wish.")
                .defineInRange("amount", 1000, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("summoning");
        SUMMON_COOLDOWN_DAYS = BUILDER
                .comment("PER-PLAYER cooldown before summoning again, in game days.",
                            "0 = no cooldown (useful for testing).")
                .defineInRange("cooldown_days", 7, 0, 100000);
        SHENLONG_WISHES = BUILDER
                .comment("How many wishes Shenlong grants per summoning (shared pool; it runs out).")
                .defineInRange("shenlong_wishes", 1, 1, 100);
        NAMEK_DRAGON_WISHES = BUILDER
                .comment("How many wishes the Namekian Dragon Balls grant per summoning (shared pool).",
                            "Porunga has no model yet, so Shenlong answers with THIS amount instead.")
                .defineInRange("namek_dragon_wishes", 3, 1, 100);
        BROADCAST_RADIUS = BUILDER
                .comment("Radius (blocks) within which other players see Shenlong's public messages:",
                            "the summoning and the granted wish (and what it was asked for).",
                            "0 = only the interacting player.")
                .defineInRange("broadcast_radius", 32, 0, 256);
        BUILDER.pop();

        BUILDER.push("instant_transmission");
        INSTANT_TRANSMISSION_ENABLED = BUILDER
                .comment("Enables or disables the Instant Transmission technique entirely",
                            "(buying it, the blink, everything). Meant for a server/modpack that finds it",
                            "too broken/OP and would rather turn it off completely than touch numbers.")
                .define("technique_enabled", true);
        INSTANT_TRANSMISSION_MENU_ENABLED = BUILDER
                .comment("Enables or disables ONLY the planet menu (standing still to arm it,",
                            "dimension jumps, party TP). The level 1 blink",
                            "(look at a point and confirm) keeps working even when this is off -",
                            "it is a toggle independent of technique_enabled, not a sub-case.")
                .define("menu_enabled", true);
        BUILDER.pop();

        BUILDER.push("training_minigames");
        MEDITATION_TP_PER_COMBO = BUILDER
                .comment("Meditation: TP per streak (combo) point when a session's performance is reported.")
                .defineInRange("meditation.tp_per_combo", 0.5D, 0.0D, 1000.0D);
        MEDITATION_SESSION_TP_CAP = BUILDER
                .comment("Meditation: cap on the TP that ONE single session can grant, regardless of the reported combo.")
                .defineInRange("meditation.session_tp_cap", 500, 0, Integer.MAX_VALUE);
        MEDITATION_MIN_COOLDOWN_TICKS = BUILDER
                .comment("Meditation: minimum ticks between two session reports from the same player.")
                .defineInRange("meditation.min_cooldown_ticks", 200, 0, 72000);
        TARGET_PRACTICE_TP_PER_ORB = BUILDER
                .comment("Ki Target Practice: TP per popped orb when a session's performance is reported.")
                .defineInRange("target_practice.tp_per_orb", 2.0D, 0.0D, 1000.0D);
        TARGET_PRACTICE_SESSION_TP_CAP = BUILDER
                .comment("Ki Target Practice: cap on the TP that ONE single session can grant.")
                .defineInRange("target_practice.session_tp_cap", 500, 0, Integer.MAX_VALUE);
        TARGET_PRACTICE_MIN_COOLDOWN_TICKS = BUILDER
                .comment("Ki Target Practice: minimum ticks between two session reports from the same player.")
                .defineInRange("target_practice.min_cooldown_ticks", 200, 0, 72000);
        BUILDER.pop();
    }

    // =====================================================================
    // SPEC — TP y topes
    // =====================================================================

    private static final ModConfigSpec.DoubleValue TP_COEFFICIENT_RAW =
            BUILDER.comment("Slope of the cost line: cost(n) = attribute_base_cost + coef * n. At 1.0 point n costs 1+n TP, i.e. the cost rises one at a time. Lowering it flattens the curve; raising it hardens it abruptly, because it is linear and not exponential.")
                    .defineInRange("stats.tp_coefficient", 1.0D, 0.0D, 100D);

    private static final ModConfigSpec.IntValue GLOBAL_ATTRIBUTE_CAP_RAW =
            BUILDER.comment("Max per attribute. 5 counted attrs x 200000 = PL cap 1,000,000")
                    .defineInRange("caps.global_attribute", 200000, 1, 1000000);

    private static final ModConfigSpec.DoubleValue AURA_REFERENCE_TP_RAW =
            BUILDER.comment("Aura presence scale: total TP an endgame player is expected to have invested. The ONLY reference number AuraCeiling needs to normalize the log10 presence scale - it does NOT change when a form/Kaioken step is added, the ceiling recomputes from the whole registry on its own. Raise it if TP income outpaces this and auras start looking maxed out too early; lower it if the top of the scale never gets reached.")
                    .defineInRange("aura.reference_tp", 5_000_000D, 1.0D, 1.0E12D);

    private static final ModConfigSpec.DoubleValue ATTRIBUTE_BASE_COST_RAW =
            BUILDER.comment("Intercept of the cost line: what the first point costs, with zero invested.")
                    .defineInRange("stats.attribute_base_cost", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue TRAINING_PL_RATIO_FLOOR_RAW =
            BUILDER.comment("Minimum TP multiplier when killing something far below your power level. Full formula: clamp(victimPL / yourPL, floor, 1.0). Keeps farming trash from ever being worth more per minute than fighting something your size, without making it literally zero.")
                    .defineInRange("training.pl_ratio_floor", 0.05D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue TRAINING_PL_RATIO_FULL_RAW =
            BUILDER.comment("Power ratio (victimPL / yourPL) at which a kill is worth FULL TP. Not 1.0: a player's PL is 31-65% ki pool because ki_reserves coefficients are 40-78 per point, while a mob splits its PL over a flat 100-point archetype shape. Measured against the actual curve, an even fight sits near 0.25 and stays there as both sides grow. Below this, TP decays linearly to training.pl_ratio_floor.")
                    .defineInRange("training.pl_ratio_full", 0.25D, 0.01D, 1.0D);

    // =====================================================================
    // SPEC — Movimiento y vuelo
    // =====================================================================

    private static final ModConfigSpec.DoubleValue SPEED_MULT_CAP_RAW =
            BUILDER.comment("Max movement multiplier (cap)")
                    .defineInRange("speed.multiplier_cap", 3.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MOVE_SCALING_RAW =
            BUILDER.comment("How DEX-derived Speed translates to move % per 100 points (1.0 => +100%)")
                    .defineInRange("scaling.movement", 1.0D, 0.01D, 10.0D);

    private static final ModConfigSpec.DoubleValue RUN_STAMINA_DRAIN_RAW =
            BUILDER.comment("Stamina drained per SECOND while sprinting (before the Run skill reduction)")
                    .defineInRange("run.stamina_drain_per_second", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.IntValue DASH_COOLDOWN_RAW =
            BUILDER.comment("Combat-mode dash (double-tap W/A/S/D): cooldown in ticks between dashes. It costs no stamina")
                    .defineInRange("dash.cooldown_ticks", 15, 0, 200);

    private static final ModConfigSpec.DoubleValue DASH_SPEED_RAW =
            BUILDER.comment("Combat-mode dash: horizontal speed in blocks per tick (1.1 is roughly 5 blocks of travel)")
                    .defineInRange("dash.speed", 1.1D, 0.2D, 4.0D);

    private static final ModConfigSpec.IntValue STEP_COOLDOWN_RAW =
            BUILDER.comment("Quick Step (a skill bought from Korin that overrides the combat dash): base cooldown in ticks between uses (each skill level shortens it)")
                    .defineInRange("step.cooldown_ticks", 60, 0, 1200);

    private static final ModConfigSpec.DoubleValue STEP_SPEED_RAW =
            BUILDER.comment("Quick Step: horizontal speed in blocks per tick (1.8 is roughly 8 blocks of travel)")
                    .defineInRange("step.speed", 1.8D, 0.2D, 6.0D);

    private static final ModConfigSpec.DoubleValue STEP_ENERGY_PCT_RAW =
            BUILDER.comment("Quick Step: base ki cost as a fraction of the maximum ki pool (0.03 = 3%); each skill level lowers it")
                    .defineInRange("step.energy_cost_pct", 0.03D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue STEP_INVISIBLE_RAW =
            BUILDER.comment("Quick Step: ticks the player model stays invisible while moving")
                    .defineInRange("step.invisible_ticks", 8, 1, 60);

    private static final ModConfigSpec.IntValue STEP_IFRAME_RAW =
            BUILDER.comment("Quick Step: ticks of invulnerability from the moment it starts")
                    .defineInRange("step.iframe_ticks", 10, 0, 60);

    private static final ModConfigSpec.DoubleValue BLOCK_WEAPON_FACTOR_RAW =
            BUILDER.comment("Blocking with a melee weapon in the main hand: fraction of the normal damage reduction you keep (1.0 = same as bare hands)")
                    .defineInRange("block.weapon_factor", 0.6D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue BLOCK_SHIELD_BONUS_RAW =
            BUILDER.comment("Blocking with a shield in the off hand: extra damage reduction as a fraction of the normal one (0.35 = 35% more)")
                    .defineInRange("block.shield_bonus", 0.35D, 0.0D, 3.0D);

    private static final ModConfigSpec.DoubleValue BLOCK_SHIELD_CAP_RAW =
            BUILDER.comment("Blocking with a shield: the damage reduction can never exceed this (0.85 = at most 85% of the damage is cut)")
                    .defineInRange("block.shield_cap", 0.85D, 0.0D, 0.95D);

    private static final ModConfigSpec.DoubleValue BLOCK_SHIELD_SLOW_RAW =
            BUILDER.comment("Blocking with a shield: fraction of movement speed lost while guarding (bare-handed blocking always loses 60%). 0 = no slowdown")
                    .defineInRange("block.shield_slow", 0.0D, 0.0D, 0.9D);

    private static final ModConfigSpec.IntValue BLOCK_SHIELD_DURABILITY_RAW =
            BUILDER.comment("Blocking with a shield: durability points the shield loses per blocked hit. 0 = never wears out")
                    .defineInRange("block.shield_durability_per_hit", 1, 0, 100);

    private static final ModConfigSpec.DoubleValue BLOCK_SHIELD_KI_RAW =
            BUILDER.comment("Ki-infused shield (Ki Infuse skill): ki spent per blocked hit as a fraction of your maximum ki. Without enough ki the shield is not infused")
                    .defineInRange("block.shield_ki_cost_pct", 0.005D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue GUARD_MAX_RAW =
            BUILDER.comment("Guard stamina: base maximum. Every blocked hit drains it; at 0 the guard breaks and the defender is stunned. 0 disables guard stamina")
                    .defineInRange("block.guard_max_base", 100.0D, 0.0D, 10000.0D);

    private static final ModConfigSpec.DoubleValue GUARD_CON_BONUS_RAW =
            BUILDER.comment("Guard stamina: maximum extra stamina Constitution can add (1.0 = up to +100%). The bonus follows an ease-out curve and reaches exactly this maximum at guard_con_full")
                    .defineInRange("block.guard_con_bonus", 1.0D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue GUARD_CON_FULL_RAW =
            BUILDER.comment("Guard stamina: effective Constitution (attribute x race/style multiplier) at which the full guard_con_bonus is reached (no further growth beyond it)")
                    .defineInRange("block.guard_con_full", 500.0D, 1.0D, 1000000.0D);

    private static final ModConfigSpec.DoubleValue GUARD_DEX_BONUS_RAW =
            BUILDER.comment("Guard stamina: maximum extra regeneration Dexterity can add (1.5 = up to +150%), same ease-out curve, reaching it at guard_dex_full")
                    .defineInRange("block.guard_dex_bonus", 1.5D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue GUARD_DEX_FULL_RAW =
            BUILDER.comment("Guard stamina: effective defense (Dexterity, with form and power multipliers) at which the full guard_dex_bonus is reached")
                    .defineInRange("block.guard_dex_full", 500.0D, 1.0D, 1000000.0D);

    private static final ModConfigSpec.DoubleValue GUARD_DRAIN_RAW =
            BUILDER.comment("Guard stamina: drained per blocked hit = damage that got through the guard, as a % of the defender's max body health, x this multiplier.",
                            "With the defaults (100 stamina, x3) the guard breaks after blocking ~33% of max health worth of damage")
                    .defineInRange("block.guard_drain_mult", 3.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue GUARD_REGEN_RAW =
            BUILDER.comment("Guard stamina: regenerated per tick while NOT blocking (after the regen delay)")
                    .defineInRange("block.guard_regen_per_tick", 0.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.IntValue GUARD_REGEN_DELAY_RAW =
            BUILDER.comment("Guard stamina: ticks after lowering the guard (or taking a blocked hit) before it starts regenerating")
                    .defineInRange("block.guard_regen_delay_ticks", 40, 0, 600);

    private static final ModConfigSpec.IntValue GUARD_BREAK_STUN_RAW =
            BUILDER.comment("Guard stamina: ticks of guard break AND stun when the stamina runs out. 0 = no stun, only the guard break (uses 40 ticks)")
                    .defineInRange("block.guard_break_stun_ticks", 40, 0, 400);

    private static final ModConfigSpec.BooleanValue STUN_PLAYERS_RAW =
            BUILDER.comment("Stun: whether players can be stunned (false = only mobs; players ignore any stun)")
                    .define("stun.affect_players", true);

    private static final ModConfigSpec.IntValue STUN_MAX_RAW =
            BUILDER.comment("Stun: hard cap, in ticks, for any single stun")
                    .defineInRange("stun.max_ticks", 100, 1, 600);

    private static final ModConfigSpec.DoubleValue STUN_DR_RAW =
            BUILDER.comment("Stun: diminishing returns. A stun that lands within stun.dr_window_ticks after the previous one ended lasts",
                            "this fraction of its duration per consecutive stun (0.5 = half, then a quarter...). 1 = no diminishing returns")
                    .defineInRange("stun.diminishing_returns", 0.5D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue STUN_DR_WINDOW_RAW =
            BUILDER.comment("Stun: ticks after a stun ends during which the next one counts as consecutive for the diminishing returns")
                    .defineInRange("stun.dr_window_ticks", 100, 0, 1200);

    private static final ModConfigSpec.IntValue STUN_IMMUNITY_RAW =
            BUILDER.comment("Stun: ticks of immunity right after a stun ends (nothing can stun the target again meanwhile)")
                    .defineInRange("stun.immunity_ticks", 40, 0, 600);

    private static final ModConfigSpec.IntValue COMBO_WINDOW_RAW =
            BUILDER.comment("Combo: ticks allowed between two consecutive hits for the chain to continue")
                    .defineInRange("combo.window_ticks", 30, 5, 200);

    private static final ModConfigSpec.DoubleValue COMBO_DMG_RAW =
            BUILDER.comment("Combo: extra damage per consecutive hit (0.05 = +5% each, starting from the 2nd hit). 0 = combos give no bonus")
                    .defineInRange("combo.damage_mult_per_hit", 0.05D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue COMBO_MAX_RAW =
            BUILDER.comment("Combo: number of hits after which the bonus stops growing")
                    .defineInRange("combo.max_hits", 10, 1, 100);

    private static final ModConfigSpec.IntValue HEAVY_TICKS_RAW =
            BUILDER.comment("Heavy blow charge: ticks holding the key per charge level (a quick tap = no charge)")
                    .defineInRange("heavy.charge_ticks_per_level", 15, 1, 200);

    private static final ModConfigSpec.IntValue HEAVY_LEVELS_RAW =
            BUILDER.comment("Heavy blow charge: maximum charge level. 0 = charging disabled")
                    .defineInRange("heavy.max_level", 3, 0, 10);

    private static final ModConfigSpec.DoubleValue HEAVY_DMG_RAW =
            BUILDER.comment("Heavy blow charge: extra damage per charge level (0.35 = +35% each)")
                    .defineInRange("heavy.damage_mult_per_level", 0.35D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue HEAVY_KNOCK_RAW =
            BUILDER.comment("Heavy blow charge: extra knockback per charge level")
                    .defineInRange("heavy.knockback_per_level", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue HEAVY_STAMINA_RAW =
            BUILDER.comment("Heavy blow charge: extra stamina per charge level, as a fraction of the technique's base stamina cost")
                    .defineInRange("heavy.stamina_fraction_per_level", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue DIVE_MIN_HEIGHT_RAW =
            BUILDER.comment("Dive: minimum height above the ground (blocks) required to start it. Only usable while flying")
                    .defineInRange("dive.min_height", 8.0D, 1.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue DIVE_SPEED_RAW =
            BUILDER.comment("Dive: speed, in blocks per tick, held during the dive")
                    .defineInRange("dive.speed", 2.0D, 0.5D, 6.0D);

    private static final ModConfigSpec.IntValue DIVE_MAX_TICKS_RAW =
            BUILDER.comment("Dive: hard cap of ticks a dive can last (safety so it always ends)")
                    .defineInRange("dive.max_ticks", 80, 5, 400);

    private static final ModConfigSpec.DoubleValue DIVE_RADIUS_RAW =
            BUILDER.comment("Dive: impact radius per block/tick of speed reached")
                    .defineInRange("dive.radius_per_speed", 1.5D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue DIVE_MAX_RADIUS_RAW =
            BUILDER.comment("Dive: maximum impact radius, in blocks")
                    .defineInRange("dive.max_radius", 6.0D, 1.0D, 32.0D);

    private static final ModConfigSpec.DoubleValue DIVE_KNOCK_RAW =
            BUILDER.comment("Dive: radial knockback of the impact")
                    .defineInRange("dive.knockback", 1.4D, 0.0D, 10.0D);

    private static final ModConfigSpec.IntValue EVADE_TICKS_RAW =
            BUILDER.comment("Evade (Super Explosive Wave): total ticks the user stays planted, from the start of the charge to the end of the cooldown pose. The user cannot be hurt during them")
                    .defineInRange("evade.duration_ticks", 34, 6, 200);

    private static final ModConfigSpec.IntValue EVADE_CHARGE_TICKS_RAW =
            BUILDER.comment("Evade: ticks of the ki-compression charge before the wave goes off (the grab is released at that moment)")
                    .defineInRange("evade.charge_ticks", 12, 1, 100);

    private static final ModConfigSpec.DoubleValue GRAB_RANGE_RAW =
            BUILDER.comment("Grab: reach in blocks. It works on any non-boss target in front of the user")
                    .defineInRange("grab.range", 3.5D, 1.0D, 16.0D);

    private static final ModConfigSpec.IntValue GRAB_HOLD_RAW =
            BUILDER.comment("Grab: optional cap, in ticks, on how long a target is held before being thrown automatically. 0 = no cap: the hold lasts until the target breaks free (skill check, Evade, Instant Transmission) or the grabber throws them")
                    .defineInRange("grab.max_hold_ticks", 0, 0, 72000);

    private static final ModConfigSpec.DoubleValue GRAB_THROW_RAW =
            BUILDER.comment("Grab: speed (blocks per tick) the target is thrown at")
                    .defineInRange("grab.throw_speed", 1.8D, 0.2D, 6.0D);

    private static final ModConfigSpec.IntValue GRAB_STUN_RAW =
            BUILDER.comment("Grab: ticks the target stays stunned after the throw lands")
                    .defineInRange("grab.landing_stun_ticks", 30, 0, 200);

    private static final ModConfigSpec.IntValue GRAB_MOB_HOLD_RAW =
            BUILDER.comment("Grab: ticks a MOB stays held before it breaks free on its own (mobs do not play the skill check). 300 = 15 s")
                    .defineInRange("grab.mob_max_hold_ticks", 300, 20, 72000);

    private static final ModConfigSpec.IntValue GRAB_CHECKS_RAW =
            BUILDER.comment("Grab: skill checks the held PLAYER must hit to break free (mobs just wait out the timer)")
                    .defineInRange("grab.checks_to_escape", 2, 1, 10);

    private static final ModConfigSpec.DoubleValue GRAB_ZONE_RAW =
            BUILDER.comment("Grab: size of the success zone of a skill check, as a fraction of the bar (0.2 = 20%)")
                    .defineInRange("grab.zone_size", 0.2D, 0.05D, 0.6D);

    private static final ModConfigSpec.IntValue GRAB_NEEDLE_RAW =
            BUILDER.comment("Grab: ticks the needle takes to cross the bar once (lower = harder)")
                    .defineInRange("grab.needle_ticks", 18, 4, 100);

    private static final ModConfigSpec.IntValue GRAB_FAIL_RAW =
            BUILDER.comment("Grab: extra ticks of pause before the next skill check every time the held player misses one")
                    .defineInRange("grab.fail_penalty_ticks", 20, 0, 200);

    private static final ModConfigSpec.IntValue GRAB_GAP_RAW =
            BUILDER.comment("Grab: ticks of pause between one skill check and the next")
                    .defineInRange("grab.check_gap_ticks", 12, 0, 100);

    private static final ModConfigSpec.DoubleValue EVADE_WAVE_RADIUS_RAW =
            BUILDER.comment("Evade: radius (blocks) of the shockwave that pushes everything away from the user. 0 = no wave")
                    .defineInRange("evade.wave_radius", 9.0D, 0.0D, 24.0D);

    private static final ModConfigSpec.DoubleValue EVADE_WAVE_KNOCK_RAW =
            BUILDER.comment("Evade: horizontal knockback of the shockwave (it deals no damage)")
                    .defineInRange("evade.wave_knockback", 3.2D, 0.0D, 10.0D);

    private static final ModConfigSpec.IntValue EVADE_WAVE_STUN_RAW =
            BUILDER.comment("Evade: ticks the shockwave stuns whoever it hits (stun rules apply: bosses, immunity and diminishing returns). 0 = no stun")
                    .defineInRange("evade.wave_stun_ticks", 0, 0, 100);

    private static final ModConfigSpec.IntValue COMBO_FINISHER_EVERY_RAW =
            BUILDER.comment("Combo: every Nth consecutive bare-hand hit is a finisher that knocks the target away. 0 = no finisher")
                    .defineInRange("combo.finisher_every", 4, 0, 20);

    private static final ModConfigSpec.DoubleValue COMBO_FINISHER_KNOCK_RAW =
            BUILDER.comment("Combo: knockback of the finisher hit")
                    .defineInRange("combo.finisher_knockback", 1.4D, 0.0D, 10.0D);

    private static final ModConfigSpec.BooleanValue KIAI_OMNI_RAW =
            BUILDER.comment("Kiai: true = it blasts in a full circle around the user (crowd control, pushes everyone away from you);",
                            "false = only the frontal cone, pushing along the look direction")
                    .define("kiai.omnidirectional", true);

    private static final ModConfigSpec.DoubleValue KIAI_EXTRA_STAMINA_RAW =
            BUILDER.comment("Kiai: extra stamina charged for every target beyond the first (enemies pushed + ki projectiles deflected),",
                            "as a fraction of the technique's base stamina cost. 0 = a kiai always costs the same")
                    .defineInRange("kiai.extra_target_stamina_fraction", 0.12D, 0.0D, 2.0D);

    private static final ModConfigSpec.IntValue KIAI_EXTRA_CAP_RAW =
            BUILDER.comment("Kiai: maximum number of extra targets that add to the stamina cost")
                    .defineInRange("kiai.extra_target_cap", 6, 0, 50);

    private static final ModConfigSpec.IntValue KIAI_GUARD_BREAK_RAW =
            BUILDER.comment("Kiai: ticks a blocking target loses its guard for (it cannot raise it again meanwhile). 0 disables guard break")
                    .defineInRange("kiai.guard_break_ticks", 40, 0, 400);

    private static final ModConfigSpec.BooleanValue PARRY_ENABLED_RAW =
            BUILDER.comment("Parry: right after starting to block, a short window turns the guard into a parry. A hit or ki",
                            "projectile landing inside it is negated (a projectile is sent back), the attacker is knocked back and staggered,",
                            "and the defender gets a counter-attack window. Everyone has it; the Parry Mastery skill improves it")
                    .define("parry.enabled", true);

    private static final ModConfigSpec.IntValue PARRY_WINDOW_RAW =
            BUILDER.comment("Parry: ticks after starting to block during which the guard parries (6 = 0.3 s). Parry Mastery adds to this")
                    .defineInRange("parry.window_ticks", 6, 1, 40);

    private static final ModConfigSpec.IntValue PARRY_COOLDOWN_RAW =
            BUILDER.comment("Parry: ticks before a NEW block can open another parry window. Counted from the moment the previous window opened,",
                            "whether or not it caught anything, so tapping the guard repeatedly cannot keep a parry up")
                    .defineInRange("parry.cooldown_ticks", 40, 0, 400);

    private static final ModConfigSpec.DoubleValue PARRY_KNOCKBACK_RAW =
            BUILDER.comment("Parry: how far, in blocks, a melee attacker who got parried is thrown back (about; the push is",
                            "computed from the vanilla drag and friction, so it lands near this distance on flat ground)")
                    .defineInRange("parry.knockback_blocks", 2.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue PARRY_NPC_CHANCE_RAW =
            BUILDER.comment("Parry: chance that an NPC whose kit includes kiai parries an incoming melee hit (0 disables NPC parries).",
                            "Each NPC then waits parry.cooldown_ticks before it can parry again")
                    .defineInRange("parry.npc_chance", 0.3D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue PARRY_REFLECT_RAW =
            BUILDER.comment("Parry: damage returned to a melee attacker who got parried, as a fraction of the defender's own",
                            "melee strength (0 disables it). Goes through the normal defense of the target")
                    .defineInRange("parry.reflect_fraction", 0.25D, 0.0D, 3.0D);

    private static final ModConfigSpec.IntValue PARRY_STUN_RAW =
            BUILDER.comment("Parry: ticks a parried NPC cannot start a new attack (players are only knocked back)")
                    .defineInRange("parry.stun_ticks", 30, 0, 400);

    private static final ModConfigSpec.IntValue PARRY_COUNTER_TICKS_RAW =
            BUILDER.comment("Parry: ticks after a successful parry during which the defender's next hit is boosted")
                    .defineInRange("parry.counter_ticks", 40, 0, 400);

    private static final ModConfigSpec.DoubleValue PARRY_COUNTER_MULT_RAW =
            BUILDER.comment("Parry: damage multiplier of the counter-attack hit (Parry Mastery adds to it)")
                    .defineInRange("parry.counter_mult", 1.5D, 1.0D, 10.0D);

    private static final ModConfigSpec.IntValue PARRY_PERFECT_TICKS_RAW =
            BUILDER.comment("Parry: a hit caught within this many ticks of OPENING the window is a perfect parry (0 disables it)")
                    .defineInRange("parry.perfect_ticks", 2, 0, 10);

    private static final ModConfigSpec.DoubleValue PARRY_PERFECT_BONUS_RAW =
            BUILDER.comment("Parry: extra damage multiplier added to the counter-attack hit after a perfect parry")
                    .defineInRange("parry.perfect_counter_bonus", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.BooleanValue CLASH_ENABLED_RAW =
            BUILDER.comment("Beam clash: two ki projectiles from different owners that cross freeze in place and start a",
                            "timing minigame. The winner's beam absorbs the loser's and is fired back at them.")
                    .define("clash.enabled", true);

    private static final ModConfigSpec.IntValue CLASH_DURATION_RAW =
            BUILDER.comment("Beam clash: length of the timing minigame in ticks (100 = 5 seconds)")
                    .defineInRange("clash.duration_ticks", 100, 20, 1200);

    private static final ModConfigSpec.IntValue CLASH_CONTACT_RAW =
            BUILDER.comment("Beam clash: ticks between the beams meeting and the bar starting (the sphere grows meanwhile)")
                    .defineInRange("clash.contact_ticks", 10, 0, 100);

    private static final ModConfigSpec.IntValue CLASH_RESOLVE_RAW =
            BUILDER.comment("Beam clash: ticks the final numbers stay on screen before the winner's beam is fired back")
                    .defineInRange("clash.resolve_ticks", 14, 0, 100);

    private static final ModConfigSpec.IntValue CLASH_PERIOD_SLOW_RAW =
            BUILDER.comment("Beam clash: ticks per full up-and-down sweep of the bar when the slow speed is drawn")
                    .defineInRange("clash.period_slow_ticks", 30, 4, 200);

    private static final ModConfigSpec.IntValue CLASH_PERIOD_MEDIUM_RAW =
            BUILDER.comment("Beam clash: ticks per full sweep when the medium speed is drawn")
                    .defineInRange("clash.period_medium_ticks", 18, 4, 200);

    private static final ModConfigSpec.IntValue CLASH_PERIOD_FAST_RAW =
            BUILDER.comment("Beam clash: ticks per full sweep when the fast speed is drawn")
                    .defineInRange("clash.period_fast_ticks", 10, 4, 200);

    private static final ModConfigSpec.IntValue CLASH_FIXED_SPEED_RAW =
            BUILDER.comment("Beam clash: the bar speed is drawn ONCE per clash and is the same for both fighters until it ends.",
                            "-1 = random between the three speeds, 0 = always slow, 1 = always medium, 2 = always fast")
                    .defineInRange("clash.fixed_speed", -1, -1, 2);

    private static final ModConfigSpec.IntValue CLASH_PING_GRACE_RAW =
            BUILDER.comment("Beam clash: maximum ticks the server will rewind a player's button press to compensate for latency.",
                            "The press is evaluated at the tick the client reports, clamped to this window and to the player's real ping,",
                            "so a modified client cannot pick its own score. 0 disables compensation")
                    .defineInRange("clash.ping_grace_ticks", 6, 0, 20);

    private static final ModConfigSpec.DoubleValue CLASH_NO_PRESS_RAW =
            BUILDER.comment("Beam clash: multiplier applied to the bar value of a player who never pressed (0 = they score nothing)")
                    .defineInRange("clash.no_press_multiplier", 0.5D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue CLASH_STAT_BONUS_CAP_RAW =
            BUILDER.comment("Beam clash: the stronger beam gets up to this many bar points of bonus (out of 1000) so power level matters without deciding the minigame. 0 disables it")
                    .defineInRange("clash.stat_bonus_cap", 100.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue CLASH_TIE_MARGIN_RAW =
            BUILDER.comment("Beam clash: bar-point difference below which the clash is a tie and both beams detonate")
                    .defineInRange("clash.tie_margin", 10.0D, 0.0D, 200.0D);

    private static final ModConfigSpec.IntValue CLASH_TEAM_MAX_RAW =
            BUILDER.comment("Beam clash in teams: maximum fighters per side (the two who fired count). Other players join by",
                            "right-clicking a fighter while aiming at them. 1 disables joining")
                    .defineInRange("clash.team_max_size", 3, 1, 3);

    private static final ModConfigSpec.DoubleValue CLASH_JOIN_RANGE_RAW =
            BUILDER.comment("Beam clash in teams: how close (blocks) a player must be to a fighter to join their side")
                    .defineInRange("clash.join_range", 6.0D, 1.0D, 32.0D);

    private static final ModConfigSpec.BooleanValue CLASH_JOIN_RESETS_RAW =
            BUILDER.comment("Beam clash in teams: what happens to the minigame when someone joins.",
                            "false (default) = presses already made are KEPT, the newcomer gets a full bar of their own and the deadline",
                            "is extended until it ends. true = the minigame restarts for everyone (all presses are wiped)")
                    .define("clash.join_resets_minigame", false);

    private static final ModConfigSpec.DoubleValue CLASH_OUTNUMBERED_BONUS_RAW =
            BUILDER.comment("Beam clash in teams: base multiplier B for each member of the SMALLER team. Each one's bar value is multiplied by 1 + (bigger - smaller) * (B - 1) / smaller (2 vs 1 with B = 1.6: the lone player scores x1.6). 1 disables the handicap")
                    .defineInRange("clash.outnumbered_bonus", 1.6D, 1.0D, 3.0D);

    private static final ModConfigSpec.DoubleValue CLASH_TIE_DAMAGE_RAW =
            BUILDER.comment("Beam clash: fraction of its damage each beam still deals when the clash is a tie")
                    .defineInRange("clash.tie_damage_fraction", 0.5D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue CLASH_SUM_RAW =
            BUILDER.comment("Beam clash: fraction of the LOSER's beam damage added to the winner's returning beam (1.0 = full sum)")
                    .defineInRange("clash.damage_sum_fraction", 1.0D, 0.0D, 3.0D);

    private static final ModConfigSpec.DoubleValue CLASH_RETURN_SPEED_RAW =
            BUILDER.comment("Beam clash: speed of the returning beam as a fraction of the technique's normal speed. Lower gives the loser more time to dodge (for example with Instant Transmission)")
                    .defineInRange("clash.return_speed_multiplier", 0.9D, 0.1D, 2.0D);

    private static final ModConfigSpec.DoubleValue CLASH_HOMING_RAW =
            BUILDER.comment("Beam clash: how strongly the returning beam curves toward the loser each tick (0 = flies straight, 0.08 = gentle, 0.15 = strong).",
                            "It only steers while the loser is within clash.return_homing_range, so getting far enough away still dodges it")
                    .defineInRange("clash.return_homing_strength", 0.08D, 0.0D, 0.5D);

    private static final ModConfigSpec.DoubleValue CLASH_HOMING_RANGE_RAW =
            BUILDER.comment("Beam clash: distance in blocks within which the returning beam homes on the loser")
                    .defineInRange("clash.return_homing_range", 24.0D, 1.0D, 128.0D);

    private static final ModConfigSpec.DoubleValue CLASH_MIN_DAMAGE_RAW =
            BUILDER.comment("Beam clash: projectiles dealing less damage than this never start a clash (they pass through each other)")
                    .defineInRange("clash.min_damage", 1.0D, 0.0D, 1.0E6D);

    private static final ModConfigSpec.DoubleValue CLASH_MOB_SKILL_RAW =
            BUILDER.comment("Beam clash: how well mobs time their press (0 = random, 1 = almost always near the peak)")
                    .defineInRange("clash.mob_skill", 0.35D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue KI_LIFE_RAW =
            BUILDER.comment("Lifetime in ticks of the ki techniques that travel (20 ticks = 1 s). A projectile flies in a",
                            "straight line with no gravity or drag, so its RANGE = speed (blocks/tick) x lifetime (ticks).",
                            "Range at 100 ticks (the old fixed value) / 200 ticks (default) / 400 ticks:",
                            "  supernova 0.55 b/t:        55 / 110 / 220 blocks",
                            "  big_blast 0.60:            60 / 120 / 240",
                            "  death_ball 0.65:           65 / 130 / 260",
                            "  spirit_bomb 0.70:          70 / 140 / 280",
                            "  wave 0.80:                 80 / 160 / 320",
                            "  kamehameha, galick 0.85:   85 / 170 / 340",
                            "  final_flash 0.90:          90 / 180 / 360",
                            "  spiral 1.00:              100 / 200 / 400",
                            "  blast 1.20:               120 / 240 / 480",
                            "  burst 1.60:               160 / 320 / 640",
                            "  disk 1.80:                180 / 360 / 720",
                            "  lazer 2.50:               250 / 500 / 1000",
                            "  death_beam 3.00:          300 / 600 / 1200",
                            "Beyond ~128 blocks from the player the client stops tracking the entity (it is no longer drawn,",
                            "though it keeps flying on the server), and in chunks that are not loaded it freezes.",
                            "Speeds come from the technique JSONs; if you edit those, recompute")
                    .defineInRange("ki.projectile_life_ticks", 200, 20, 1200);

    private static final ModConfigSpec.DoubleValue SB_RADIUS_RAW =
            BUILDER.comment("Spirit Bomb: radius in blocks in which non-hostile creatures donate energy while charging")
                    .defineInRange("spirit_bomb.gather_radius", 32.0D, 1.0D, 128.0D);

    private static final ModConfigSpec.DoubleValue SB_GAIN_RAW =
            BUILDER.comment("Spirit Bomb: damage bonus gained per second per sqrt(creatures nearby) (0.03 = +3%)")
                    .defineInRange("spirit_bomb.gain_per_pulse", 0.03D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue SB_CAP_RAW =
            BUILDER.comment("Spirit Bomb: maximum damage bonus from gathered energy (0.5 = +50%)")
                    .defineInRange("spirit_bomb.bonus_cap", 0.5D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue SB_PARTY_GAIN_RAW =
            BUILDER.comment("Spirit Bomb: damage bonus per second added by EACH party member who is charging ki (C) nearby (0.06 = +6%)")
                    .defineInRange("spirit_bomb.party_gain_per_pulse", 0.06D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue SB_PARTY_RADIUS_RAW =
            BUILDER.comment("Spirit Bomb: radius in blocks in which party members can lend their energy. 0 = unlimited (any distance, same dimension)")
                    .defineInRange("spirit_bomb.party_radius", 0.0D, 0.0D, 1024.0D);

    private static final ModConfigSpec.IntValue SB_WINDUP_RAW =
            BUILDER.comment("Spirit Bomb: ticks between releasing the technique and the projectile leaving the hands")
                    .defineInRange("spirit_bomb.windup_ticks", 8, 0, 100);

    private static final ModConfigSpec.DoubleValue FLY_MULT_CAP_RAW =
            BUILDER.comment("Max fly multiplier (cap)")
                    .defineInRange("fly.multiplier_cap", 15.0D, 1.0D, 50.0D);

    private static final ModConfigSpec.DoubleValue FLY_SCALING_RAW =
            BUILDER.comment("How DEX-derived FlySpeed translates to fly % per 100 points")
                    .defineInRange("scaling.fly", 0.25D, 0.01D, 8.0D);

    private static final ModConfigSpec.DoubleValue FLY_BASE_SPEED_RAW =
            BUILDER.comment("Base flying speed before stat/skill multipliers (vanilla creative = 0.05).",
                            "Blocks per second ~= speed * 202")
                    .defineInRange("fly.base_speed", 0.05D, 0.01D, 0.5D);

    private static final ModConfigSpec.DoubleValue FLY_KI_DRAIN_RAW =
            BUILDER.comment("Ki drained per TICK while flying in turbo (before the Fly skill reduction)")
                    .defineInRange("fly.ki_drain_per_tick", 0.15D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FLY_TAKEOFF_IMPULSE_RAW =
            BUILDER.comment("Vertical velocity (blocks/tick) granted once when flight starts from the",
                            "ground (\"ki jump\"), on top of whatever vertical speed the player already has")
                    .defineInRange("fly.takeoff_impulse", 0.55D, 0.0D, 3.0D);

    private static final ModConfigSpec.DoubleValue FLY_TAKEOFF_KI_COST_RAW =
            BUILDER.comment("One-time ki cost of the takeoff impulse (before the Fly skill reduction)")
                    .defineInRange("fly.takeoff_ki_cost", 2.0D, 0.0D, 200.0D);

    private static final ModConfigSpec.IntValue FLY_RAMP_UP_RAW =
            BUILDER.comment("Ticks of sustained thrust (forward held, or cruise) for flight to reach its full speed bonus.",
                            "Flight starts at vanilla creative speed and ramps up. 0 = instant (old behaviour)")
                    .defineInRange("fly.ramp_up_ticks", 30, 0, 400);

    private static final ModConfigSpec.IntValue FLY_RAMP_DOWN_RAW =
            BUILDER.comment("Ticks without thrust for the flight speed bonus to fall back to vanilla creative speed.",
                            "Shorter than ramp_up on purpose (braking is faster than accelerating). 0 = instant")
                    .defineInRange("fly.ramp_down_ticks", 12, 0, 400);

    private static final ModConfigSpec.DoubleValue TURBO_DRAIN_PCT_PER_SEC_RAW =
            BUILDER.comment("Energy drained per second while in turbo, as a fraction of energyMax (0.005 = 0.5%/s)")
                    .defineInRange("aura.turbo_drain_pct_per_sec", 0.005D, 0.0D, 1.0D);

    // =====================================================================
    // SPEC — Party
    // =====================================================================

    private static final ModConfigSpec.IntValue PARTY_MAX_SIZE_CEILING_RAW =
            BUILDER.comment("Hard ceiling admins can set for /zparty maxsize (the per-party cap a LEADER can dial up to with the PartyConfig icon). 32 is the protocol's own hard cap (PartySyncPacket.MAX_MEMBERS) - this can only lower that, never raise it.")
                    .defineInRange("party.max_size_ceiling", 32, 1, 32);

    // =====================================================================
    // SPEC — Generador de energía
    // =====================================================================

    private static final ModConfigSpec.IntValue ENERGY_GENERATOR_CAPACITY_RAW =
            BUILDER.comment("FE buffer of the energy generator block. See GeneratorEnergy.")
                    .defineInRange("energy.generator_capacity", 100_000, 1, 10_000_000);

    private static final ModConfigSpec.IntValue ENERGY_GENERATOR_MAX_EXTRACT_RAW =
            BUILDER.comment("Ceiling of FE per tick and per face that the generator pushes to a neighbor. Lets you watch the bar drop instead of emptying at once when a hungry machine is connected.")
                    .defineInRange("energy.generator_max_extract", 1_000, 1, 1_000_000);

    // =====================================================================
    // SPEC — Entidades sin stats propias
    // =====================================================================

    private static final ModConfigSpec.BooleanValue VANILLA_STATS_FALLBACK_RAW =
            BUILDER.comment("Give derived Zenkai stats to mobs that have no zenkai_entities JSON.",
                            "Off = they stay vanilla-scaled and PvE becomes irrelevant past character creation")
                    .define("entity.vanilla_stats_fallback", true);

    private static final ModConfigSpec.DoubleValue VANILLA_TP_REWARD_FACTOR_RAW =
            BUILDER.comment("Fallback mobs: TP reward as a fraction of the automatic one (PL-based).",
                            "Every mob starts granting TP once the fallback is on, so mob farms can outpace training")
                    .defineInRange("entity.vanilla_tp_reward_factor", 0.25D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_PASSIVE_FACTOR_RAW =
            BUILDER.comment("Fallback mobs, passive/ambient: CON = maxHealth * factor.",
                            "1.0 keeps villagers around PL 20, canon for a human with no ki training")
                    .defineInRange("entity.factor.passive", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_HOSTILE_FACTOR_RAW =
            BUILDER.comment("Fallback mobs, MobCategory.MONSTER: CON = maxHealth * factor")
                    .defineInRange("entity.factor.hostile", 12.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_BOSS_FACTOR_RAW =
            BUILDER.comment("Fallback mobs tagged zenkai:bosses: CON = maxHealth * factor")
                    .defineInRange("entity.factor.boss", 40.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_DAMAGE_RATIO_RAW =
            BUILDER.comment("Fallback mobs: how much of the category factor applies to damage.",
                            "Below 1.0 on purpose: sharing it made creepers and the warden one-shot players")
                    .defineInRange("entity.factor.damage_ratio", 0.4D, 0.0D, 10.0D);

    // =====================================================================
    // SPEC — Pools y regeneración
    // =====================================================================

    private static final ModConfigSpec.DoubleValue BODY_SCALE_RAW =
            BUILDER.comment("bodyMax = 10 + CON * scale. WARNING: multiplies time-to-kill by the same factor")
                    .defineInRange("pools.body_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue STAMINA_SCALE_RAW =
            BUILDER.comment("staminaMax = 90 + CON * scale")
                    .defineInRange("pools.stamina_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ENERGY_SCALE_RAW =
            BUILDER.comment("energyMax = 90 + SPI * scale")
                    .defineInRange("pools.energy_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue REGEN_BODY_RAW =
            BUILDER.comment("Body regen percent per second (1.0 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.body_percent", 1.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue REGEN_STAMINA_RAW =
            BUILDER.comment("Stamina regen percent per second (1.0 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.stamina_percent", 3.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue REGEN_ENERGY_RAW =
            BUILDER.comment("Energy/Ki regen percent per second (1.0 = 1%). Meditation multiplies this, and also drives the charge rate")
                    .defineInRange("regen.base_per_second.energy_percent", 1.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FOOD_KI_RAW =
            BUILDER.comment("Ki restored per nutrition point when finishing a food item, as % of energyMax (2.0 = 2%)")
                    .defineInRange("regen.food.ki_percent_per_nutrition", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FOOD_STAMINA_RAW =
            BUILDER.comment("Stamina restored per nutrition point when finishing a food item, as % of staminaMax (3.0 = 3%)")
                    .defineInRange("regen.food.stamina_percent_per_nutrition", 3.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue REGEN_BODY_EXHAUSTION_RAW =
            BUILDER.comment("Exhaustion per second while body is regenerating. Reference: 4.0 exhaustion = 1 food point, and sprinting costs about 0.56/s. The old hardcoded 2.4 meant a full body heal cost 40 food points - the whole bar plus saturation - against 30 for a full heal in vanilla.")
                    .defineInRange("regen.food.body_exhaustion", 1.2D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue REGEN_STAMINA_EXHAUSTION_RAW =
            BUILDER.comment("Exhaustion per second while stamina is regenerating. Kept low on purpose: stamina is spent by every swing, so in combat this charge never stops - unlike body regen, which only runs while you are hurt.")
                    .defineInRange("regen.food.stamina_exhaustion", 0.15D, 0.0D, 20.0D);

    private static final ModConfigSpec.IntValue REGEN_MIN_FOOD_RAW =
            BUILDER.comment("Body and stamina stop regenerating at or below this food level. Vanilla stops natural regen below 18; this stops far lower so you can still heal while hungry, but you can no longer be drained to zero by standing still.")
                    .defineInRange("regen.food.min_food_level", 6, 0, 20);

    // =====================================================================
    // SPEC — Combate, técnicas y detección
    // =====================================================================

    private static final ModConfigSpec.BooleanValue MIRROR_HEALTH_RAW =
            BUILDER.comment("Mirror the body pool onto the vanilla health bar: 50% body = 10 hearts. Makes the heart bar meaningful again and feeds any third-party HUD correct ratios. false = old behaviour, hearts stay full and the bar is decorative.")
                    .define("combat.mirror_health", true);

    private static final ModConfigSpec.DoubleValue ABSORPTION_WEIGHT_RAW =
            BUILDER.comment("Vanilla absorption hearts as a shield over the body pool. 1.0 = an absorption heart is worth the same fraction of your body pool as a real heart, so a golden apple is worth 20% of your body at any power level - identical to vanilla in relative terms. 0 = absorption does nothing (hearts stay decorative).")
                    .defineInRange("combat.absorption_weight", 1.0D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue MIN_DAMAGE_PERCENT_RAW =
            BUILDER.comment("Damage floor as a fraction of the incoming hit, after defense reduction. 0.05 = 5%")
                    .defineInRange("combat.min_damage_percent", 0.05D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue IMMORTAL_OVERKILL_FRACTION_RAW =
            BUILDER.comment("The Immortality wish warns a blow bigger than your body can absorb will still "
                    + "kill you - this is that threshold. A single mitigated hit whose damage is >= "
                    + "body_max * this fraction skips the downed grace period and ImmortalityEffect's "
                    + "regen entirely: real death (or, in the Otherworld, the same reset combat there "
                    + "already applies). 1.0 = a hit that would empty a FULL body bar. Below that, "
                    + "immortals still fall, regen, and stand back up like anyone else.")
                    .defineInRange("combat.immortal_overkill_fraction", 1.0D, 0.01D, 100.0D);

    private static final ModConfigSpec.IntValue IN_COMBAT_TICKS_RAW =
            BUILDER.comment("Ticks the 'in combat' state lasts after the last damage dealt or received. 160 = 8 s.")
                    .defineInRange("combat.in_combat_ticks", 160, 0, 12000);

    private static final ModConfigSpec.DoubleValue IN_COMBAT_BODY_REGEN_MULT_RAW =
            BUILDER.comment("Multiplier of BODY regeneration while in combat. 0.5 = half, 0.0 = you do not heal while fighting, 1.0 = disables the penalty. Body only: stamina and ki are what you fight with.")
                    .defineInRange("combat.in_combat_body_regen_mult", 0.5D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue IN_COMBAT_RACIAL_REGEN_FLOOR_RAW =
            BUILDER.comment("Floor for the multiplier above, for races whose regeneration IS their passive (Namekian, Majin). Even if the general multiplier is 0, they keep healing at this rate: otherwise they would stop being that race in combat, which is exactly where their identity has to show.")
                    .defineInRange("combat.in_combat_racial_regen_floor", 0.25D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue TECHNIQUE_MAX_SLOTS_RAW =
            BUILDER.comment("Ki techniques: max technique slots per player")
                    .defineInRange("technique.max_slots", 12, 1, 24);

    private static final ModConfigSpec.IntValue SENSE_KI_RANGE_RAW =
            BUILDER.comment("Sense Ki: range in blocks")
                    .defineInRange("sense_ki.range", 64, 8, 256);

    private static final ModConfigSpec.BooleanValue LOCK_ON_ENABLED_RAW =
            BUILDER.comment("Lock-on: allow players to lock onto a target. Off disables it for everyone, "
                            + "including the homing of ki techniques that depends on the lock.")
                    .define("lock_on.enabled", true);

    private static final ModConfigSpec.BooleanValue LOCK_ON_WHILE_FLYING_RAW =
            BUILDER.comment("Lock-on: allow locking (and keeping the lock) while flying.")
                    .define("lock_on.while_flying", true);

    private static final ModConfigSpec.IntValue LOCK_ON_RANGE_RAW =
            BUILDER.comment("Lock-on: maximum distance in blocks at which a target can be locked. "
                            + "A locked target is released 8 blocks beyond this.")
                    .defineInRange("lock_on.range", 64, 8, 256);

    private static final ModConfigSpec.DoubleValue LOCK_ON_HOMING_TURN_RAW =
            BUILDER.comment("Lock-on: how strongly ki projectiles curve towards the locked target each tick "
                            + "(blend weight, not an angle). 0 = no homing, 0.144 = the old value, "
                            + "0.2 = default, 0.5 = very aggressive.")
                    .defineInRange("lock_on.homing_turn", 0.20D, 0.0D, 0.5D);

    private static final ModConfigSpec.BooleanValue LOCK_ON_DIRECTED_DASH_RAW =
            BUILDER.comment("Lock-on: while a target is locked, the combat dash and Quick Step are "
                            + "aimed relative to the target (forward = towards it, left/right = orbit) "
                            + "instead of relative to where the camera looks.")
                    .define("lock_on.directed_dash", true);

    private static final ModConfigSpec.DoubleValue TECHNIQUE_EXPLOSION_RADIUS_CAP_RAW =
            BUILDER.comment("Ki techniques: global ceiling (in blocks) for the explosion radius of any technique.",
                            "0 = no ceiling (default): big fights are meant to be disproportionate.",
                            "Only worth setting on servers where a max-size Supernova (radius ~19.5) is too much.")
                    .defineInRange("technique.explosion_radius_cap", 0.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_PL_FACTOR_RAW =
            BUILDER.comment("PL of vanilla mobs / raceless players = max_health * factor")
                    .defineInRange("power_level.vanilla_factor", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue SCOUTER_RANGE_RAW =
            BUILDER.comment("Scouter: crosshair scan range in blocks")
                    .defineInRange("scouter.range", 64, 8, 256);

    private static final ModConfigSpec.DoubleValue KI_COST_PER_POWER_RAW =
            BUILDER.comment("Ki cost per point of ki power (WIL). Higher = ki drains faster, SPI matters more.")
                    .defineInRange("cost.ki_per_power", 0.42D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MELEE_STAMINA_PER_HIT_RAW =
            BUILDER.comment("Stamina per point of melee damage (STR). Higher = fewer hits, CON matters more.")
                    .defineInRange("cost.melee_stamina_per_hit", 0.10D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue WEAPON_SCALE_RAW =
            BUILDER.comment("Weapon damage as a MULTIPLIER on melee: mult = 1 + (attack_damage - 1) * scale. 0.04 = diamond sword x1.28. Set to 0 to make weapons irrelevant again.")
                    .defineInRange("combat.weapon_scale", 0.04D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MELEE_ENCHANT_SCALE_RAW =
            BUILDER.comment("How much of the ATTACKER's own melee damage bonus (Sharpness/Smite/Bane of Arthropods, a mace's fall-distance smash bonus, Strength, or any other mod that changes the vanilla attack damage float) carries over as a multiplier on the STR-based hit: mult = 1 + (vanillaOriginalDamage / (attackDamageAttribute * critMultiplier) - 1) * scale. Ratio-based like projectile_scale, so it stays correct at any Power Level without hardcoding a specific enchantment. 1.0 = Sharpness/mace fall damage helps exactly as much here as it helps the vanilla number. 0 = ignored.")
                    .defineInRange("combat.melee_enchant_scale", 1.0D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue MELEE_ENCHANT_MULT_CAP_RAW =
            BUILDER.comment("Hard ceiling on the melee enchant/mace-fall multiplier above. A mace smash from a very long fall can push the vanilla ratio into double digits; the cap keeps one swing from trivializing the defense formula outright.")
                    .defineInRange("combat.melee_enchant_mult_cap", 5.0D, 1.0D, 200.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_ARMOR_WEIGHT_RAW =
            BUILDER.comment("How much of vanilla's own damage reduction (armor points, toughness, Protection, Resistance) carries over to Zenkai damage. 0.0 = armor ignored (old behaviour). 1.0 = full vanilla reduction, netherite + Prot IV cuts ~91%. 0.5 = ~45%.")
                    .defineInRange("combat.vanilla_armor_weight", 0.50D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MOB_PROJECTILE_FACTOR_RAW =
            BUILDER.comment("Fraction of a mob's melee power that its vanilla projectiles deal (arrows, fireballs, shulker bullets, potions). Without this, ranged mobs deal raw vanilla damage against a four-digit body pool and are effectively unarmed. Does not apply to players - their path is Ki Infuse.")
                    .defineInRange("combat.mob_projectile_factor", 0.60D, 0.0D, 2.0D);

    private static final ModConfigSpec.DoubleValue EXPLOSION_REFERENCE_DAMAGE_RAW =
            BUILDER.comment("Vanilla damage of a point-blank creeper blast, used to recover explosion distance falloff. The pipeline replaces explosion damage with the mob's STR, which discarded vanilla's own distance and cover calculation; this restores it as a proportion. Lower than the literal point-blank value (22) on purpose: a self-detonating mob only ever gets ONE hit, unlike a melee mob's repeated swings, so it needs to reach full falloff (1.0) well before true point-blank or it reads as harmless against a fresh player's body pool.")
                    .defineInRange("combat.explosion_reference_damage", 12.0D, 1.0D, 200.0D);

    private static final ModConfigSpec.DoubleValue PROJECTILE_BASE_DAMAGE_RAW =
            BUILDER.comment("Reference damage of a clean unenchanted arrow at full draw (vanilla: 2.0 base x 3.0 velocity). The Ki Infuse bonus on projectiles scales against this.")
                    .defineInRange("combat.projectile_base_damage", 6.0D, 0.1D, 100.0D);

    private static final ModConfigSpec.DoubleValue PROJECTILE_SCALE_RAW =
            BUILDER.comment("How much a projectile's own damage scales the Ki Infuse bonus: mult = 1 + (damage / projectile_base_damage - 1) * scale. 1.0 = a bow enchantment helps the infusion exactly as much as it helps the arrow (Power V = x1.5). 0 = enchantments irrelevant.")
                    .defineInRange("combat.projectile_scale", 1.0D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue PROJECTILE_MULT_CAP_RAW =
            BUILDER.comment("Hard ceiling on the projectile multiplier. Vanilla never reaches 2.0; the cap exists so a modded bow with 30 base damage cannot multiply a four-digit ki bonus by 5.")
                    .defineInRange("combat.projectile_mult_cap", 3.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue KI_PER_BONUS_DAMAGE_RAW =
            BUILDER.comment("Ki spent per point of BONUS damage added by Ki Infuse / Ki Fist. Higher = fewer empowered hits per bar.")
                    .defineInRange("cost.ki_per_bonus_damage", 0.30D, 0.0D, 10.0D);

    // Balance PvP (2026-10-15, ver docs/combate-y-muerte.md "Fórmula de defensa única"). Los dos costes de ki de arriba
    // bajaron un 40 % a la vez (0.70 -> 0.42 y 0.50 -> 0.30): con combates más largos el ki se agotaba antes de matar.
    private static final ModConfigSpec.DoubleValue PVP_DAMAGE_SCALE_RAW =
            BUILDER.comment("Multiplier for damage dealt by a PLAYER to another PLAYER, after defense. 0.5 = PvP fights last about twice as long. Mobs and PvE are unaffected. 1.0 = off.")
                    .defineInRange("combat.pvp_damage_scale", 0.5D, 0.05D, 2.0D);

    private static final ModConfigSpec.DoubleValue KI_DEFENSE_FACTOR_RAW =
            BUILDER.comment("Fraction of the target's defense that counts against KI projectiles (energy is harder to stop than a fist, and it can be dodged). 0.6 = 60%. 1.0 = same as melee.")
                    .defineInRange("combat.ki_defense_factor", 0.6D, 0.0D, 2.0D);

    private static final ModConfigSpec.DoubleValue KI_DAMAGE_SCALE_RAW =
            BUILDER.comment("Multiplier for the damage of ALL ki techniques (beams, blasts, bombs), fired by players and by mobs/NPCs alike. 1.5 = +50%.")
                    .defineInRange("combat.ki_damage_scale", 1.5D, 0.1D, 10.0D);

    private static final ModConfigSpec.DoubleValue COMBAT_ATTACK_SPEED_RAW =
            BUILDER.comment("Attack speed while in combat mode. Vanilla base is 4.0 (5-tick recharge); anything BELOW 4.0 enables the vanilla swing cooldown and the crosshair indicator. 1.6 = sword-like, 12.5 ticks.")
                    .defineInRange("combat.attack_speed", 1.6D, 0.1D, 4.0D);

    private static final ModConfigSpec.DoubleValue BARE_ATTACK_SPEED_RAW =
            BUILDER.comment("Attack speed while in combat mode with NO weapon in hand (bare hands). Higher than combat.attack_speed so PvP does not feel slow: 3.2 = ~6 ticks between full hits.")
                    .defineInRange("combat.bare_hand_attack_speed", 3.2D, 0.1D, 4.0D);

    private static final ModConfigSpec.DoubleValue EXPLOSION_SACRIFICE_CONVERSION_RAW =
            BUILDER.comment("Damage per point of health sacrificed by an Explosion technique")
                    .defineInRange("combat.explosion_sacrifice",0.5,0,1);

    private static final ModConfigSpec.DoubleValue OVERCHARGE_TIME_MULT_RAW =
            BUILDER.comment("Extra charge time for the 100%->200% overcharge stretch, as a multiple of the base cast time. 2.5 = the second 100% takes 2.5x as long as the first.")
                    .defineInRange("cost.overcharge_time_mult", 2.5D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue OVERCHARGE_COST_MULT_RAW =
            BUILDER.comment("Ki cost multiplier applied ONLY to the overcharged portion. 1.5 = every point past 100% costs 50% more than a normal one.")
                    .defineInRange("cost.overcharge_cost_mult", 1.5D, 1.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue BF_CHANCE_RAW =
            BUILDER.comment("Black Flash: base proc chance on a PERFECT (100% charge) Ki Infuse melee hit. Scaled down hard by charge, see black_flash.charge_exponent. 0 disables the mechanic.")
                    .defineInRange("black_flash.chance", 0.03D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue BF_MULTIPLIER_RAW =
            BUILDER.comment("Black Flash: damage multiplier, applied to the RAW hit before defense. Because defense is proportional, x3 raw lands between x3.9 (even match) and x7.3 (against a mastered SSJ4) in effective terms - but absolute damage still DROPS the more outclassed you are, so this is not a giant-killer. It turns a useless hit into a normal one.")
                    .defineInRange("black_flash.multiplier", 3.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue BF_CHARGE_EXPONENT_RAW =
            BUILDER.comment("Black Flash: chance = base * charge^exponent, using the RAW vanilla attack ticker. 3.0 means a 75% charge hit is at 42% of the chance and a 50% hit at 12.5%. Lower it to reward timing less; 0 makes charge irrelevant and turns the mechanic into a spam lottery.")
                    .defineInRange("black_flash.charge_exponent", 3.0D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue BF_LUCK_FACTOR_RAW =
            BUILDER.comment("Black Flash: how much each point of the vanilla LUCK attribute multiplies the chance. 0.5 = Luck I takes 3% to 4.5%, Unlucky takes it to 1.5%. Any mod that grants luck feeds into this for free. 0 ignores luck.")
                    .defineInRange("black_flash.luck_factor", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue BF_MAX_CHANCE_RAW =
            BUILDER.comment("Black Flash: hard ceiling after luck is applied. Exists because LUCK is unbounded - a modded +40 luck would otherwise mean a proc on every perfect hit.")
                    .defineInRange("black_flash.max_chance", 0.25D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue BF_STAT_FACTOR_RAW =
            BUILDER.comment("Black Flash: extra damage pulled from the player's BEST of STR/WIL/SPI, valued in melee scale, added on top of the multiplier. Exists so a WIL build - whose bare fists are worthless - gets a Black Flash worth landing. 1.0 = adds one full best-stat hit at the current charge. 0 disables the term and leaves a pure multiplier.")
                    .defineInRange("black_flash.stat_factor", 1.0D, 0.0D, 10.0D);

    // =====================================================================
    // SPEC — Maestría y efecto Majin
    // =====================================================================

    private static final ModConfigSpec.DoubleValue FORM_MASTERY_PER_MINUTE_RAW =
            BUILDER.comment("Form mastery gained per minute while transformed (percent points, 0-100 scale)")
                    .defineInRange("mastery.form_per_minute", 0.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.BooleanValue SSJ_BLUE_ROSE_SHARE_MASTERY_RAW =
            BUILDER.comment("Saiyan divine siblings ssj_blue/ssj_rose (see DivineForms — same",
                            "god_ki level, mutually exclusive, player picks which to wear) track ONE",
                            "shared mastery value when true (training either one raises both), or two",
                            "independent values when false. Switching which one is worn always keeps",
                            "the higher of the two accumulated values, whichever this is set to.")
                    .define("mastery.ssj_blue_rose_share", true);

    private static final ModConfigSpec.DoubleValue TECH_MASTERY_PER_USE_RAW =
            BUILDER.comment("Technique mastery gained per use (percent points, 0-100 scale)")
                    .defineInRange("mastery.technique_per_use", 0.2D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_DMG_RAW =
            BUILDER.comment("At 100% technique mastery: damage bonus fraction (0.25 = +25%)")
                    .defineInRange("mastery.tech_damage_bonus", 0.25D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_COST_RAW =
            BUILDER.comment("At 100% technique mastery: cost reduction fraction (0.30 = -30% ki/stamina)")
                    .defineInRange("mastery.tech_cost_reduction", 0.30D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_CAST_RAW =
            BUILDER.comment("At 100% technique mastery: charge-time reduction fraction (0.30 = charges 30% faster)")
                    .defineInRange("mastery.tech_cast_reduction", 0.30D, 0.0D, 0.95D);

    private static final ModConfigSpec.DoubleValue NAMEK_GIANT_STAT_RAW =
            BUILDER.comment("Stat bonus fraction while the Namekian Giant toggle is on (0.60 = +60%).",
                            "Additive with the form and Kaioken bonuses.")
                    .defineInRange("namekian.giant_stat_bonus", 0.60D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_STAT_BONUS_RAW =
            BUILDER.comment("Stat bonus fraction while under the Majin effect (0.20 = +20%, 0.15 = +15%). The downside is that the effect pins alignment at -100.")
                    .defineInRange("majin.effect_stat_bonus", 0.20D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue NAMEK_LEGEND_REGEN_RAW =
            BUILDER.comment("Namekian God form (namek_god): extra body regeneration per second while it is worn, as a percent of max body (1.0 = 1%/s). It scales from 50% (mastery 0) to 100% (mastery 100) and ignores the in-combat regen penalty: the value is in enduring.")
                    .defineInRange("namek_god.regen_percent_per_second", 1.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue NAMEK_LEGEND_REGEN_KI_RAW =
            BUILDER.comment("Namekian God form: ki spent per body point of the extra regeneration (the Namekian passive costs 1.5 per extra point). Almost nothing on purpose.")
                    .defineInRange("namek_god.regen_ki_per_point", 0.15D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue NAMEK_LEGEND_PASSIVE_COST_RAW =
            BUILDER.comment("Namekian God form: multiplier on the ki cost of the regular Namekian regeneration passive while the form is worn (0.25 = a quarter of the usual cost).")
                    .defineInRange("namek_god.passive_ki_cost_mult", 0.25D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue HUMAN_WILL_ALLY_BONUS_RAW =
            BUILDER.comment("Human God form (human_god, collective will): stat bonus per nearby ally (party member, own pet, master or friendly NPC), as a fraction (0.08 = +8%). With allies around the momentum below does not apply.")
                    .defineInRange("human_god.bonus_per_ally", 0.08D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue HUMAN_WILL_HIT_BONUS_RAW =
            BUILDER.comment("Human God form: stat bonus per hit of the current combo chain while ALONE (no ally nearby). The chain breaks when you get hit, so it rewards not being touched (0.04 = +4% per hit).")
                    .defineInRange("human_god.bonus_per_hit", 0.04D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue HUMAN_WILL_MAX_RAW =
            BUILDER.comment("Human God form: cap of the collective will bonus, from allies or from momentum (0.40 = +40%).")
                    .defineInRange("human_god.max_bonus", 0.40D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue HUMAN_WILL_RADIUS_RAW =
            BUILDER.comment("Human God form: radius in blocks in which allies count.")
                    .defineInRange("human_god.ally_radius", 24.0D, 1.0D, 128.0D);

    private static final ModConfigSpec.IntValue MAJIN_LEGEND_EXTRA_CAP_RAW =
            BUILDER.comment("Majin Legendary form (Devouring Majin): extra mass stacks it can hold above majin.mass_cap.")
                    .defineInRange("majin_legendary.extra_cap", 6, 0, 50);

    private static final ModConfigSpec.DoubleValue MAJIN_LEGEND_DURATION_MULT_RAW =
            BUILDER.comment("Majin Legendary form: multiplier on how long the mass lasts after the last absorption (the charges decay slowly).")
                    .defineInRange("majin_legendary.duration_mult", 3.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_LEGEND_STEAL_RAW =
            BUILDER.comment("Majin Legendary form: each absorption steals this fraction of the target/self power-level ratio as a temporary stat bonus (0.25 = absorbing an equal-PL target gives +25%, before the per-absorption cap).")
                    .defineInRange("majin_legendary.steal_ratio", 0.25D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_LEGEND_STEAL_MAX_RAW =
            BUILDER.comment("Majin Legendary form: most stat bonus a single absorption can steal (0.10 = +10%).")
                    .defineInRange("majin_legendary.steal_per_absorb_max", 0.10D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_LEGEND_STEAL_CAP_RAW =
            BUILDER.comment("Majin Legendary form: cap of the total stolen stat bonus (0.50 = +50%). It expires with the mass.")
                    .defineInRange("majin_legendary.steal_cap", 0.50D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_LEGEND_KI_REFUND_RAW =
            BUILDER.comment("Majin Legendary form: percent of max ki restored by each absorption. It prolongs the form, whose duration is the ki it burns.")
                    .defineInRange("majin_legendary.ki_refund_percent", 10.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue ARC_LEGEND_PERCENT_RAW =
            BUILDER.comment("Arcosian Legendary form (Broken Restraint): FIXED stat percent while it is worn (14.0 = +1400%). It does not grow with TP or mastery; God Ki resonance (legendary.divine_resonance_mult) still multiplies it.")
                    .defineInRange("arcosian_legendary.stat_percent", 14.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_LEGEND_TENSION_RAW =
            BUILDER.comment("Arcosian Legendary form: seconds of TENSION once transformed. When it runs out the form collapses (back to Base with ki and stamina at zero) and a long cooldown starts. No continuous ki drain.")
                    .defineInRange("arcosian_legendary.tension_seconds", 45.0D, 1.0D, 3600.0D);

    private static final ModConfigSpec.DoubleValue ARC_LEGEND_TENSION_MASTERY_RAW =
            BUILDER.comment("Arcosian Legendary form: extra tension from the shared mastery (1.0 = up to x2 of the tension at 100% mastery of the form it starts from).")
                    .defineInRange("arcosian_legendary.tension_mastery_bonus", 1.0D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue ARC_LEGEND_COOLDOWN_RAW =
            BUILDER.comment("Arcosian Legendary form: cooldown in seconds after a collapse. Leaving on your own earlier gives a cooldown proportional to the tension used.")
                    .defineInRange("arcosian_legendary.cooldown_seconds", 600.0D, 0.0D, 86400.0D);

    private static final ModConfigSpec.DoubleValue HUMAN_LEGEND_DEFENSE_RAW =
            BUILDER.comment("Human Legendary form (Reckless): extra damage taken while it is worn, as a fraction (0.25 = takes +25% damage). The price of its power: whoever hits first wins.")
                    .defineInRange("human_legendary.damage_taken_extra", 0.25D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue NAMEK_LEGEND_LIFESTEAL_RAW =
            BUILDER.comment("Namekian Legendary form (Ancestors): fraction of the body damage you deal that heals your own body (0.15 = 15%).")
                    .defineInRange("namek_legendary.lifesteal_percent", 0.15D, 0.0D, 10.0D);

    private static final ModConfigSpec.BooleanValue NAMEK_LEGEND_NO_REGEN_RAW =
            BUILDER.comment("Namekian Legendary form (Ancestors): if true, natural body regeneration (including the Namekian passive) is off while it is worn; healing only comes from fighting.")
                    .define("namek_legendary.block_natural_regen", true);

    private static final ModConfigSpec.DoubleValue ARC_GOD_PP_CAP_RAW =
            BUILDER.comment("Arcosian God (divine tank): multiplier on the Power Point tank capacity once the form is UNLOCKED (the tank charges in Base, so it must apply there too). A bigger tank lasts longer because the Power Points are spent in absolute amounts per second.")
                    .defineInRange("arcosian_god.pp_capacity_mult", 1.5D, 1.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue ARC_GOD_PP_DRAIN_RAW =
            BUILDER.comment("Arcosian God (divine tank): multiplier on how fast the Power Point tank drains while the form is WORN (0.5 = half as fast).")
                    .defineInRange("arcosian_god.pp_drain_mult", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue ARC_GOD_PP_STAT_RAW =
            BUILDER.comment("Arcosian God (divine tank): multiplier on the stat bonus the Power Point tank grants while the form is WORN.")
                    .defineInRange("arcosian_god.pp_stat_mult", 1.5D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue LEGENDARY_STATE_BONUS_RAW =
            BUILDER.comment("Stat bonus fraction for being Legendary (the Shenlong wish), whatever the race and form worn (0.10 = +10%). It opens more paths, so it is smaller than the Majin bonus.")
                    .defineInRange("legendary.state_stat_bonus", 0.10D, 0.0D, 5.0D);

    private static final ModConfigSpec.BooleanValue MAJIN_LEGENDARY_EXCLUSIVE_RAW =
            BUILDER.comment("If true, the Majin effect and being Legendary are mutually exclusive: a Majin cannot make the Legendary wish and a Legendary cannot be put under the Majin effect.")
                    .define("majin.legendary_mutually_exclusive", true);

    // =====================================================================
    // SPEC — Entrenamiento
    // =====================================================================

    private static final ModConfigSpec.DoubleValue TRAIN_DMG_TP_RAW =
            BUILDER.comment("Training: TP per point of effective damage dealt.",
                            "Recalibrated 2026-08-20 (0.02 -> 0.10, x5): the balance simulation (tools/tp_economy_sim.py)",
                            "showed that with the old value, even ELIMINATING fatigue entirely, a player took ~112h",
                            "to reach the reference 5,000,000 TP fighting at a moderate pace (15s/kill) -- far above",
                            "the 20-40h design target. It goes hand in hand with entity.tp_per_pl",
                            "and training.fatigue_decay_per_minute; do not touch one without looking at the other two.")
                    .defineInRange("training.damage_tp_factor", 0.10D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue TP_PER_PL_RAW =
            BUILDER.comment("Automatic ('auto') TP reward for killing ANY entity with Zenkai stats (zenkai_entities with",
                            "rewards.tp=\"auto\", and the vanilla fallback) = the victim's PL x this factor. It used to be",
                            "hardcoded and DUPLICATED in EntityStats.TP_PER_PL and EntityDeathRewardHandler.VANILLA_TP_PER_PL",
                            "(0.05 in both, not going through config); it was moved here so it can be recalibrated without",
                            "recompiling and so the same number is no longer maintained by hand in two places.",
                            "Recalibrated 2026-08-20 (0.05 -> 0.25, x5) together with training.damage_tp_factor -- see that",
                            "entry's comment for why.")
                    .defineInRange("entity.tp_per_pl", 0.25D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_AIR_TP_RAW =
            BUILDER.comment("Training: TP per air punch = own PL * factor")
                    .defineInRange("training.air_tp_factor", 0.0001D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_AIR_COST_RAW =
            BUILDER.comment("Training: air punch stamina cost as fraction of max stamina")
                    .defineInRange("training.air_stamina_cost_pct", 0.04D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue TRAIN_AIR_TICKS_RAW =
            BUILDER.comment("Training: min ticks between counted air punches")
                    .defineInRange("training.air_min_ticks", 10, 1, 200);

    private static final ModConfigSpec.DoubleValue TRAIN_HALF_LIFE_RAW =
            BUILDER.comment("Training: fatigue (session TP / own PL) at which efficiency halves")
                    .defineInRange("training.fatigue_half_life", 0.10D, 0.001D, 10.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_DECAY_RAW =
            BUILDER.comment("Training: fatigue recovered per real minute of play.",
                            "Recalibrated 2026-08-20 (0.01 -> 0.2, x20): it is the DOMINANT lever of the system. In sustained",
                            "farming, TP/hour = 60 x this value x your PL -- the combat pace (fast or slow) cancels itself out",
                            "and does NOT affect that number (checked by simulation: from 2s to 40s per kill gave the same",
                            "result with the old 0.01). Raising damage_tp_factor/entity.tp_per_pl without touching",
                            "this is of little use: the extra reward is cancelled by the extra fatigue it generates.",
                            "It is still an ESTIMATE of the model (assumes ~15s per 'leveled' kill); real playtesting is needed",
                            "to tune it -- see the balance simulation (tools/tp_economy_sim.py).",
                            "Raised 2026-10-08 (0.2 -> 0.3): at 0.2 a player with no buffs needed ~153h to reach 5,000,000 TP.",
                            "At 0.3 it is ~102h with no buffs, ~51h in the HTC and ~20h in the HTC with full weights.",
                            "Hours scale as 1 / this value. Existing worlds keep the value saved in their serverconfig file.")
                    .defineInRange("training.fatigue_decay_per_minute", 0.30D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_HTC_MULT_RAW =
            BUILDER.comment("Training: TP multiplier while inside the HTC")
                    .defineInRange("training.htc_multiplier", 2.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_MIN_EFF_RAW =
            BUILDER.comment("Training: efficiency floor (never drops to 0)")
                    .defineInRange("training.min_efficiency", 0.05D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_CAP_DIV_RAW =
            BUILDER.comment("Weights: carry capacity (tons) = (cleanPL / divisor) ^ exponent")
                    .defineInRange("training.weight_capacity_divisor", 3.4D, 0.01D, 10000.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_CAP_EXP_RAW =
            BUILDER.comment("Weights: capacity exponent. Below 1 keeps weights relevant at high PL")
                    .defineInRange("training.weight_capacity_exponent", 0.6D, 0.05D, 4.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_STAT_PEN_RAW =
            BUILDER.comment("Weights: melee/defense/ki power lost at full load (0.25 = -25%)")
                    .defineInRange("training.weight_stat_penalty", 0.25D, 0.0D, 0.95D);

    private static final ModConfigSpec.DoubleValue WEIGHT_MOVE_PEN_RAW =
            BUILDER.comment("Weights: ground/fly speed lost at full load")
                    .defineInRange("training.weight_move_penalty", 0.60D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_JUMP_PEN_RAW =
            BUILDER.comment("Weights: jump height lost at full load")
                    .defineInRange("training.weight_jump_penalty", 0.40D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_GRAVITY_JUMP_FACTOR_RAW =
            BUILDER.comment("Weights: how much AMBIENT gravity tons (Kaiosama/HTC, not equipped " +
                    "gear) count towards the jump penalty specifically, relative to how much they " +
                    "count for everything else (move/stat/TP, always 100%). 1.0 = same as equipped " +
                    "weight; 0.0 = gravity never touches jump height at all. Default 0.25: gravity " +
                    "still slows you and boosts TP at full weight, but doesn't crush your jump the " +
                    "way carrying literal dead weight does -- user feedback 2026-09-10, needs " +
                    "playtesting calibration, see .claude/pendiente/gravedad-planeta-kaiosama.md")
                    .defineInRange("training.weight_gravity_jump_factor", 0.25D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_TP_BONUS_RAW =
            BUILDER.comment("Weights: extra TP at full load (1.5 = x2.5 TP)")
                    .defineInRange("training.weight_tp_bonus", 1.5D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_OVER_THRESH_RAW =
            BUILDER.comment("Weights: load ratio above which the player is overloaded")
                    .defineInRange("training.weight_overload_threshold", 1.2D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_OVER_MOVE_RAW =
            BUILDER.comment("Weights: movement multiplier while overloaded (drag)")
                    .defineInRange("training.weight_overload_move_factor", 0.15D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_KAIOSAMA_TONS_RAW =
            BUILDER.comment("Weights: ambient tons added by Kaiosama's planet natural gravity " +
                    "(stacks with equipped weighted gear -- see .claude/pendiente/gravedad-planeta-kaiosama.md). " +
                    "200 -> 100 (2026-09-10): confirmed in-game by the user at PL ~20k, still needs more " +
                    "playtesting at other PL ranges")
                    .defineInRange("training.weight_kaiosama_ambient_tons", 100.0D, 0.0D, 1.0E7D);

    private static final ModConfigSpec.DoubleValue WEIGHT_KAIOSAMA_GRAVITY_MULT_RAW =
            BUILDER.comment("Weights: DISPLAY-ONLY gravity multiplier shown for Kaiosama's planet " +
                    "(the generic \"Gravity\" row in the Training Hub, e.g. \"x10\"). Purely cosmetic " +
                    "flavor text -- does NOT feed the load math, which always uses the flat ambient " +
                    "tons above so its effect keeps shrinking at high PL regardless of this number.")
                    .defineInRange("training.weight_kaiosama_gravity_multiplier", 10.0D, 1.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_HTC_TONS_RAW =
            BUILDER.comment("Weights: ambient tons added by the Hyperbolic Time Chamber's own " +
                    "natural gravity (the DIMENSION itself, not the future gravity chamber block -- " +
                    "stacks with equipped weighted gear, see .claude/pendiente/gravedad-planeta-kaiosama.md). " +
                    "40 -> 3 (2026-09-10): tp_economy_sim.py showed 40t overloaded (r > weight_overload_threshold, " +
                    "TP bonus ZEROED, not capped) any player below PL ~1,000-2,500 -- the HTC has no PL gate, " +
                    "used from early game, so that overloaded almost everyone who trained there")
                    .defineInRange("training.weight_htc_ambient_tons", 3.0D, 0.0D, 1.0E7D);

    private static final ModConfigSpec.DoubleValue WEIGHT_HTC_GRAVITY_MULT_RAW =
            BUILDER.comment("Weights: DISPLAY-ONLY gravity multiplier shown for the Hyperbolic Time " +
                    "Chamber (the generic \"Gravity\" row in the Training Hub, e.g. \"x2\"). Purely " +
                    "cosmetic flavor text, same caveat as weight_kaiosama_gravity_multiplier.")
                    .defineInRange("training.weight_htc_gravity_multiplier", 2.0D, 1.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue PU_TP_MULT_RAW =
            BUILDER.comment("TP multiplier while Potential Unlock is ACTIVE. You are using your potential, not training it.")
                    .defineInRange("training.potential_unlock_tp_mult", 0.50D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue PU_MAX_STAT_RAW =
            BUILDER.comment("Mystic (Potential Unlock): ABSOLUTE ceiling of the stat bonus the form can give at 100% mastery (added, 12.0 = +1200%). Mystic scales with the best form you own, which let it overtake Beast (18-24 after the 2026-10-22 balance); this caps it below Beast's untrained value so Mystic -> Beast stays an upgrade. Mastery still spans the whole curve (the cap rescales it, it does not just clip it). Races with low-bonus chains are not affected.")
                    .defineInRange("skills.potential_unlock_max_stat_percent", 12.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue PU_ALIGNMENT_REQ_RAW =
            BUILDER.comment("Minimum alignment (-100..100) required to BUY Potential Unlock. Not checked afterwards.")
                    .defineInRange("skills.potential_unlock_alignment_req", 50, -100, 100);

    // =====================================================================
    // SPEC — Power Points arcosianos, Ultra Instinct (calor), Ultra Ego (daño)
    // =====================================================================

    private static final ModConfigSpec.DoubleValue ARC_PP_GAIN_RAW =
            BUILDER.comment("Arcosian Power Points gained per second while in BASE form and power below 50%. Second form charges at half this rate and Third form at a quarter (the lower the form, the faster the tank charges, as in DBC).")
                    .defineInRange("arcosian.pp_gain_per_second", 4.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_DRAIN_RAW =
            BUILDER.comment("Power Points spent PER SECOND in the Final Form while they are being applied (Final Form or higher with power at 100% OR MORE, overdrive included). Higher forms multiply it (Golden/Black x1.5, 5th x2, Golden/Black 5th x2.75). It is an absolute amount, so a bigger tank lasts longer.")
                    .defineInRange("arcosian.pp_cost_per_second", 4.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_COMBAT_DEALT_RAW =
            BUILDER.comment("Second and Third form (restraint forms): Power Points gained per hit you DEAL while your power is below 100%.")
                    .defineInRange("arcosian.pp_combat_gain_dealt", 2.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_COMBAT_TAKEN_RAW =
            BUILDER.comment("Second and Third form (restraint forms): Power Points gained per hit you TAKE while your power is below 100%.")
                    .defineInRange("arcosian.pp_combat_gain_taken", 3.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_GOD_REFILL_RAW =
            BUILDER.comment("Arcosian God: Power Points refilled per hit you deal, at any power level (the divine tank keeps feeding itself in combat).")
                    .defineInRange("arcosian_god.pp_refill_per_hit", 2.0D, 0.0D, 1000.0D);

    // =====================================================================
    // SPEC — Majin: absorción innata y masa (feature/race/MajinAbsorption)
    // =====================================================================

    private static final ModConfigSpec.BooleanValue MAJIN_ABSORB_ENABLED_RAW =
            BUILDER.comment("Majin innate Absorption: master switch. When off the key does nothing.")
                    .define("majin.absorb_enabled", true);

    private static final ModConfigSpec.DoubleValue MAJIN_ABSORB_DOMINANT_PL_RATIO_RAW =
            BUILDER.comment("Majin Absorption: if the Majin power level is at least this many times the target power level, the target can be absorbed even at full health (1.5 = 50% more power; 2.0 = double). Below that, the health and max power level rules apply.")
                    .defineInRange("majin.absorb_dominant_pl_ratio", 1.5D, 1.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_ABSORB_MAX_HEALTH_PCT_RAW =
            BUILDER.comment("Majin Absorption: the target must be at or below this percent of its max health to be absorbed.")
                    .defineInRange("majin.absorb_max_health_percent", 30.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_ABSORB_MAX_PL_RATIO_RAW =
            BUILDER.comment("Majin Absorption: the target power level may not exceed this multiple of the Majin power level.")
                    .defineInRange("majin.absorb_max_pl_ratio", 1.5D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_ABSORB_KI_COST_PCT_RAW =
            BUILDER.comment("Majin Absorption: ki spent per absorption, as a percent of the max ki pool.")
                    .defineInRange("majin.absorb_ki_cost_percent", 15.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.BooleanValue MAJIN_ABSORB_PVP_RAW =
            BUILDER.comment("Majin Absorption: whether other PLAYERS can be absorbed (they die). Off by default; mobs are always valid.")
                    .define("majin.absorb_pvp", false);

    private static final ModConfigSpec.IntValue MAJIN_MASS_CAP_RAW =
            BUILDER.comment("Majin mass: maximum stacks of temporary mass (Mass Voracious adds majin.voraz_extra_cap on top).")
                    .defineInRange("majin.mass_cap", 5, 1, 50);

    private static final ModConfigSpec.DoubleValue MAJIN_MASS_SECONDS_RAW =
            BUILDER.comment("Majin mass: seconds all the stacks last after the LAST absorption (forms scale this).")
                    .defineInRange("majin.mass_seconds", 120.0D, 1.0D, 36000.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_MASS_STAT_PER_STACK_RAW =
            BUILDER.comment("Majin mass: stat bonus per stack, added to the form multiplier (0.04 = +4% each, +20% at 5 stacks).")
                    .defineInRange("majin.mass_stat_per_stack", 0.04D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_MASS_PERM_PER_ABSORB_RAW =
            BUILDER.comment("Majin mass: PERMANENT stat bonus gained per absorption (0.002 = +0.2% each).")
                    .defineInRange("majin.mass_permanent_per_absorb", 0.002D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_MASS_PERM_CAP_RAW =
            BUILDER.comment("Majin mass: ceiling of the permanent bonus (0.05 = +5%).")
                    .defineInRange("majin.mass_permanent_cap", 0.05D, 0.0D, 10.0D);

    private static final ModConfigSpec.IntValue MAJIN_VORAZ_EXTRA_CAP_RAW =
            BUILDER.comment("Majin mass: extra stacks Mass Voracious (majin_super) can hold above majin.mass_cap.")
                    .defineInRange("majin.voraz_extra_cap", 2, 0, 50);

    private static final ModConfigSpec.DoubleValue MAJIN_SACIADO_DURATION_MULT_RAW =
            BUILDER.comment("Majin mass: Satiated Majin (majin_harmony) RETAINS mass: it lasts this many times longer.")
                    .defineInRange("majin.saciado_duration_mult", 2.0D, 0.1D, 100.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_PRIMITIVO_BONUS_MULT_RAW =
            BUILDER.comment("Majin mass: Primitive Majin (majin_pure_chaos) RELEASES mass: every stack is worth this many times more...")
                    .defineInRange("majin.primitivo_bonus_mult", 1.5D, 0.1D, 100.0D);

    private static final ModConfigSpec.DoubleValue MAJIN_PRIMITIVO_DURATION_MULT_RAW =
            BUILDER.comment("...but lasts only this fraction of the normal time.")
                    .defineInRange("majin.primitivo_duration_mult", 0.5D, 0.05D, 100.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SCALE_RAW =
            BUILDER.comment("Form ki drain per SECOND = ki_drain (percent, from the form datapack) / 100 * (STR + WIL, race-weighted) * this scale * SPI multiplier. Higher = every form costs more ki to hold, at any power level.")
                    .defineInRange("forms.drain_scale", 4.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SPI_MIN_RAW =
            BUILDER.comment("Lower clamp of the SPI drain multiplier sqrt(spi_req / SPI). Reached when SPI is far above the form's requirement; 1.0 disables the discount.")
                    .defineInRange("forms.drain_spi_min", 1.0D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SPI_MAX_RAW =
            BUILDER.comment("Upper clamp of the SPI drain multiplier sqrt(spi_req / SPI). Reached when SPI is far below the form's requirement; 1.0 disables the penalty.")
                    .defineInRange("forms.drain_spi_max", 6.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_FLOOR_RAW =
            BUILDER.comment("Minimum form ki drain, as a fraction of the ki pool (energyMax) multiplied by the form's ki_drain percent: floor/s = ki_drain / 100 * this * energyMax. Stops a huge pool from making a form free. 0 disables the floor.")
                    .defineInRange("forms.drain_floor", 0.25D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_STAT_RAW =
            BUILDER.comment("Stat multiplier bonus (added, 2.0 = +200%) granted when the Power Point tank is full and being applied; scales linearly with the tank.")
                    .defineInRange("arcosian.pp_stat_bonus_max", 2.0D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_RATE_RAW =
            BUILDER.comment("Ultra Instinct heat bar: percent filled per second while in an Ultra Instinct form. At 100% dodging stops (the form is kept) until the bar cools down.")
                    .defineInRange("ultra_instinct.heat_percent_per_second", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_PER_DODGE_RAW =
            BUILDER.comment("Ultra Instinct heat bar: percent of heat added by EVERY dodged hit, on top of the time-based fill. The HUD shows the bar the other way round (full = fresh, empty = about to overheat), so dodging more hits drains it faster.")
                    .defineInRange("ultra_instinct.heat_per_dodge_percent", 3.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_SLOW_RAW =
            BUILDER.comment("Fraction of the heat fill rate removed per Ultra Instinct skill level above 1 (0.06 = -6% per level).")
                    .defineInRange("ultra_instinct.heat_slowdown_per_skill_level", 0.06D, 0.0D, 0.1D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_COOL_RAW =
            BUILDER.comment("Heat percent removed per second while NOT in an Ultra Instinct form.")
                    .defineInRange("ultra_instinct.heat_cooldown_per_second", 4.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_DODGE_COST_RAW =
            BUILDER.comment("Ki spent per automatic dodge, as percent of the max ki pool, at skill level 1.")
                    .defineInRange("ultra_instinct.dodge_ki_cost_percent", 8.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_DODGE_REDUCE_RAW =
            BUILDER.comment("Fraction of the dodge ki cost removed per skill level above 1 (levels 1-4 only).")
                    .defineInRange("ultra_instinct.dodge_cost_reduction_per_level", 0.1D, 0.0D, 0.3D);

    private static final ModConfigSpec.DoubleValue UI_DODGE_CHANCE_RAW =
            BUILDER.comment("Chance (percent) that Ultra Instinct dodges a hit, at skill level 1.")
                    .defineInRange("ultra_instinct.dodge_chance_percent", 40.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_DODGE_CHANCE_LEVEL_RAW =
            BUILDER.comment("Percent points of dodge chance added per Ultra Instinct skill level above 1.")
                    .defineInRange("ultra_instinct.dodge_chance_per_skill_level", 3.3333D, 0.0D, 50.0D);

    private static final ModConfigSpec.DoubleValue UI_DODGE_CHANCE_MAX_RAW =
            BUILDER.comment("Upper limit (percent) of the Ultra Instinct dodge chance.")
                    .defineInRange("ultra_instinct.dodge_chance_max_percent", 90.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_RECOVER_RAW =
            BUILDER.comment("Ultra Instinct: when the heat bar hits 100% the form is NOT lost, but dodging stops until the bar cools down to this percent. The bar cools at heat_cooldown_per_second while exhausted.")
                    .defineInRange("ultra_instinct.heat_recover_percent", 50.0D, 0.0D, 99.0D);

    private static final ModConfigSpec.DoubleValue UI_MASTERY_DODGE_RAW =
            BUILDER.comment("Ultra Instinct: percent points of dodge chance added at 100% mastery of the Ultra Instinct forms (scales linearly with mastery; the best of the two forms counts, also for the technique).")
                    .defineInRange("ultra_instinct.mastery_dodge_chance_bonus", 20.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UI_MASTERY_HEAT_RAW =
            BUILDER.comment("Ultra Instinct: fraction of the heat fill rate removed at 100% mastery (0.4 = heat fills 40% slower when mastered).")
                    .defineInRange("ultra_instinct.mastery_heat_slowdown", 0.4D, 0.0D, 0.9D);

    private static final ModConfigSpec.DoubleValue UE_MASTERY_BONUS_RAW =
            BUILDER.comment("Ultra Ego: bonus applied to BOTH the physical defense penetration and the damage-bar stat bonus at 100% mastery of the Ultra Ego form (0.5 = +50%).")
                    .defineInRange("ultra_ego.mastery_bonus", 0.5D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue UE_CHARGE_RAW =
            BUILDER.comment("Ultra Ego damage bar: percent gained per percent of max body lost in a hit.")
                    .defineInRange("ultra_ego.charge_per_damage", 2.0D, 0.0D, 50.0D);

    private static final ModConfigSpec.DoubleValue UE_DECAY_RAW =
            BUILDER.comment("Ultra Ego damage bar percent lost per second.")
                    .defineInRange("ultra_ego.charge_decay_per_second", 1.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UE_STAT_LV5_RAW =
            BUILDER.comment("Ultra Ego: stat bonus (added to the stat factor) granted by a FULL damage bar at skill level 5 (the level the transformation unlocks). The bar only acts from level 5. It is added to the form's own percent (16-22), so it has to be in whole numbers to matter: 3.0 = about +13% on a ~x23 form (level 5 full bar = Ultra Instinct), 7.0 = about +30% (level 10). Was 1.0/3.0 until 2026-10-31 (+4%/+13%, not noticeable). See tools/forms_sim_all.py.")
                    .defineInRange("ultra_ego.max_stat_bonus_level5", 3.0D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue UE_STAT_LV10_RAW =
            BUILDER.comment("Ultra Ego: stat bonus (added) granted by a FULL damage bar at skill level 10. Levels 6-9 interpolate linearly between the level 5 and level 10 values.")
                    .defineInRange("ultra_ego.max_stat_bonus_level10", 7.0D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue UE_PEN_RAW =
            BUILDER.comment("Ultra Ego: fraction of the target's defense ignored by PHYSICAL techniques at skill level 10 (0.2 = 20%). Scales linearly from level 1 (1/10 of this value). Applies while the Ultra Ego technique is on or the Ultra Ego form is worn.")
                    .defineInRange("ultra_ego.max_armor_penetration", 0.2D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue BEAST_OD_STAT_RAW =
            BUILDER.comment("Extra stat multiplier bonus (added, 0.5 = +50%) while wearing Beast with power above 100% (overdrive). Stacks with Beast's cheaper overdrive drain.")
                    .defineInRange("beast.overdrive_stat_bonus", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue LEGENDARY_OD_STAT_RAW =
            BUILDER.comment("Extra stat multiplier bonus (added, 0.3 = +30%) while wearing Legendary Full Power (ssj_green_max) with power above 100% (overdrive). Lower than beast.overdrive_stat_bonus on purpose: the Legendary overdrive is cheaper but reaches a lower ceiling.")
                    .defineInRange("legendary.overdrive_stat_bonus", 0.3D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue LEGENDARY_GROWTH_RAW =
            BUILDER.comment("Race Legendary forms (non-Saiyan): stat percent (0-100 mastery scale, 1.5 = +150%) at 5,000 TP spent on attributes. It grows with the square root of the TP spent until legendary.ceiling_percent.")
                    .defineInRange("legendary.growth_percent_at_5000_tp", 1.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue LEGENDARY_CEILING_RAW =
            BUILDER.comment("Race Legendary forms (non-Saiyan): ceiling of the stat percent without God Ki (fraction, 12.5 = +1250%). Meant to sit a bit under the race's divine form (16).")
                    .defineInRange("legendary.ceiling_percent", 12.5D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue LEGENDARY_RESONANCE_RAW =
            BUILDER.comment("Race Legendary forms (non-Saiyan): multiplier on the whole stat percent (growth AND ceiling) when the player has God Ki (divine resonance). 1.45 puts the ceiling above the divine form.")
                    .defineInRange("legendary.divine_resonance_mult", 1.45D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue BEAST_MASTERY_REQ_RAW =
            BUILDER.comment("Beast unlock, requirement 1: minimum Mystic mastery (0-100, in percent) the player must have when the fury trigger happens. Requirement 2 is the trigger itself: a tamed pet or a party member dying near the player (see beast.fury_radius).")
                    .defineInRange("beast.unlock_mystic_mastery_percent", 50.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue BEAST_FURY_RADIUS_RAW =
            BUILDER.comment("Beast unlock: radius in blocks within which the death of a tamed pet (owned by the player) or of a party member counts as the fury trigger.")
                    .defineInRange("beast.fury_radius", 32.0D, 1.0D, 256.0D);

    // God Ki (skill tree F5): prueba del maestro y ritual de N jugadores. Sustituye a la vieja
    // arcosian.god_ki_levels (niveles placeholder sin forma): desde F5 las cinco razas tienen forma divina.
    private static final ModConfigSpec.DoubleValue GODKI_TRIAL_FRACTION_RAW =
            BUILDER.comment("God Ki master trial: power of the shadow you must defeat, as a fraction of your power level wearing your highest mortal form (1.0 = an exact copy). The trial is offered by the master after the divine audience.")
                    .defineInRange("god_ki.trial_shadow_fraction", 1.5D, 0.1D, 5.0D);
    private static final ModConfigSpec.IntValue GODKI_RITUAL_PARTICIPANTS_RAW =
            BUILDER.comment("God Ki ritual: players (besides the candidate) that must charge ki around the candidate at the same time. Set it to 1 to test alone with a friend, or on a small server.")
                    .defineInRange("god_ki.ritual_participants", 5, 1, 20);
    private static final ModConfigSpec.BooleanValue GODKI_RITUAL_SAME_RACE_RAW =
            BUILDER.comment("God Ki ritual: participants must share the candidate's race.")
                    .define("god_ki.ritual_same_race", false);
    private static final ModConfigSpec.IntValue GODKI_RITUAL_MIN_ALIGNMENT_RAW =
            BUILDER.comment("God Ki ritual: minimum alignment every participant (and the candidate) needs. -100 = any alignment (default).")
                    .defineInRange("god_ki.ritual_min_alignment", -100, -100, 100);
    private static final ModConfigSpec.DoubleValue GODKI_RITUAL_RADIUS_RAW =
            BUILDER.comment("God Ki ritual: radius in blocks around the candidate in which participants count.")
                    .defineInRange("god_ki.ritual_radius", 8.0D, 2.0D, 64.0D);
    private static final ModConfigSpec.IntValue GODKI_RITUAL_SECONDS_RAW =
            BUILDER.comment("God Ki ritual: seconds everyone must keep charging ki without interruption.")
                    .defineInRange("god_ki.ritual_seconds", 10, 1, 120);

    private static final ModConfigSpec.BooleanValue ALLOW_CUSTOM_HAIR_RAW =
            BUILDER.comment("Players can import hair codes (DragonMineZ codes and Dragon Block C / HairSalon strings) in the Hair tab of the appearance screen, keep them in a personal library and wear them; everyone sees them, no files needed. When false the import UI is hidden, the server rejects new imports and anyone wearing an imported hair falls back to the default style.")
                    .define("customization.allow_custom_hair", true);

    // ── Muerte permanente y cuerpos: baja frecuencia (.get() directo) ──────────────────────────────
    public static final ModConfigSpec.BooleanValue PERMADEATH_ENABLED =
            BUILDER.comment("Permanent death. When true, dying sends you to the Otherworld and Yemma no longer revives you after a timer: you need the Dragon Balls wish or you give up and start a new character. The body that holds your items exists even when this is off.")
                    .define("permadeath.enabled", false);
    public static final ModConfigSpec.IntValue PERMADEATH_VISIT_DAYS =
            BUILDER.comment("Days (24000 ticks each) a dead player may visit the Earth through Uranai Baba. The clock only runs while the player is online.")
                    .defineInRange("permadeath.visit_days", 7, 1, 365);
    public static final ModConfigSpec.IntValue PERMADEATH_BODY_CAP =
            BUILDER.comment("Maximum bodies one player can have in the world. When exceeded, the oldest one is emptied onto the ground and removed.")
                    .defineInRange("permadeath.body_cap_per_player", 3, 1, 32);
    public static final ModConfigSpec.BooleanValue PERMADEATH_KI_DISINTEGRATES =
            BUILDER.comment("Ki techniques that hit a body disintegrate it (its items fall to the ground).")
                    .define("permadeath.ki_disintegrates_bodies", false);
    public static final ModConfigSpec.BooleanValue PERMADEATH_BODY_OPEN_PARTY =
            BUILDER.comment("Party members of the owner can open the body too. When false only the owner can.")
                    .define("permadeath.body_open_party", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── API de toggles ──────────────────────────────────────────────────────────
    public static boolean isEnabled(WishType w) {
        return switch (w) {
            case STACK            -> ENABLE_STACK.get();
            case REVIVE_PLAYER    -> ENABLE_REVIVE_PLAYER.get();
            case ENCHANT_VILLAGER -> ENABLE_ENCHANT_VILLAGER.get();
            case IMMORTAL         -> ENABLE_IMMORTAL.get();
            case TRAINING_POINTS  -> ENABLE_TRAINING_POINTS.get();
            case LEGENDARY        -> ENABLE_LEGENDARY.get();
        };
    }

    // ── Precios villager (ahora desde config) ────────────────────────────────────
    public static int villagerBookBasePrice()     { return VILLAGER_BOOK_BASE_PRICE.get(); }
    public static int villagerBookPricePerLevel()  { return VILLAGER_BOOK_PRICE_PER_LEVEL.get(); }

    private static volatile Map<ResourceLocation, Integer> STACK_OVERRIDES = Collections.emptyMap();
    private static volatile Set<ResourceLocation> BANNED_ITEMS = Collections.emptySet();
    /** Nombres en minúscula de BANNED_RACES_RAW; el default del RAW es ["majin"], y este arranque lo iguala. */
    private static volatile Set<String> BANNED_RACES = Set.of("majin");
    private static volatile Set<ResourceLocation> WHITELIST_ITEMS = Collections.emptySet();

    // =====================================================================
    // CACHÉ VOLÁTIL (hot-path, migrado desde CommonConfig) — cada valor inicial
    // DEBE igualar el default de su *_RAW. Ver el javadoc de cabecera.
    // =====================================================================

    private static volatile double TP_COEFFICIENT = 1.0D;
    private static volatile int    GLOBAL_ATTRIBUTE_CAP = 200000;
    private static volatile double AURA_REFERENCE_TP = 5_000_000D;
    private static volatile int    IN_COMBAT_TICKS = 160;
    private static volatile double IN_COMBAT_BODY_REGEN_MULT = 0.5D;
    private static volatile double IN_COMBAT_RACIAL_REGEN_FLOOR = 0.25D;

    private static volatile double SPEED_MULT_CAP = 3.0D;
    private static volatile double MOVE_SCALING = 1.0D;
    private static volatile double RUN_STAMINA_DRAIN = 2.0D;
    private static volatile int DASH_COOLDOWN = 15;
    private static volatile double DASH_SPEED = 1.1D;
    private static volatile int STEP_COOLDOWN = 60;
    private static volatile double STEP_SPEED = 1.8D;
    private static volatile double STEP_ENERGY_PCT = 0.03D;
    private static volatile int STEP_INVISIBLE = 8;
    private static volatile int STEP_IFRAME = 10;
    private static volatile double BLOCK_WEAPON_FACTOR = 0.6D;
    private static volatile double BLOCK_SHIELD_BONUS = 0.35D;
    private static volatile double BLOCK_SHIELD_CAP = 0.85D;
    private static volatile double BLOCK_SHIELD_SLOW = 0.0D;
    private static volatile int BLOCK_SHIELD_DURABILITY = 1;
    private static volatile double BLOCK_SHIELD_KI = 0.005D;
    private static volatile double GUARD_MAX = 100.0D;
    private static volatile double GUARD_DRAIN = 3.0D;
    private static volatile double GUARD_CON_BONUS = 1.0D;
    private static volatile double GUARD_CON_FULL = 500.0D;
    private static volatile double GUARD_DEX_BONUS = 1.5D;
    private static volatile double GUARD_DEX_FULL = 500.0D;
    private static volatile double GUARD_REGEN = 0.5D;
    private static volatile int GUARD_REGEN_DELAY = 40;
    private static volatile int GUARD_BREAK_STUN = 40;
    private static volatile boolean STUN_PLAYERS = true;
    private static volatile int STUN_MAX = 100;
    private static volatile double STUN_DR = 0.5D;
    private static volatile int STUN_DR_WINDOW = 100;
    private static volatile int STUN_IMMUNITY = 40;
    private static volatile int COMBO_WINDOW = 30;
    private static volatile double COMBO_DMG = 0.05D;
    private static volatile int COMBO_MAX = 10;
    private static volatile int HEAVY_TICKS = 15;
    private static volatile int HEAVY_LEVELS = 3;
    private static volatile double HEAVY_DMG = 0.35D;
    private static volatile double HEAVY_KNOCK = 0.5D;
    private static volatile double HEAVY_STAMINA = 0.5D;
    private static volatile double DIVE_MIN_HEIGHT = 8.0D;
    private static volatile double DIVE_SPEED = 2.0D;
    private static volatile int DIVE_MAX_TICKS = 80;
    private static volatile double DIVE_RADIUS = 1.5D;
    private static volatile double DIVE_MAX_RADIUS = 6.0D;
    private static volatile double DIVE_KNOCK = 1.4D;
    private static volatile int EVADE_TICKS = 34;
    private static volatile int EVADE_CHARGE_TICKS = 12;
    private static volatile double GRAB_RANGE = 3.5D;
    private static volatile int GRAB_HOLD = 0;
    private static volatile int GRAB_MOB_HOLD = 300;
    private static volatile int GRAB_CHECKS = 2;
    private static volatile double GRAB_ZONE = 0.2D;
    private static volatile int GRAB_NEEDLE = 18;
    private static volatile int GRAB_FAIL = 20;
    private static volatile int GRAB_GAP = 12;
    private static volatile double EVADE_WAVE_RADIUS = 9.0D;
    private static volatile double EVADE_WAVE_KNOCK = 3.2D;
    private static volatile int EVADE_WAVE_STUN = 0;
    private static volatile int COMBO_FINISHER_EVERY = 4;
    private static volatile double COMBO_FINISHER_KNOCK = 1.4D;
    private static volatile double BARE_ATTACK_SPEED = 3.2D;
    private static volatile double GRAB_THROW = 1.8D;
    private static volatile int GRAB_STUN = 30;
    private static volatile boolean KIAI_OMNI = true;
    private static volatile double KIAI_EXTRA_STAMINA = 0.12D;
    private static volatile int KIAI_EXTRA_CAP = 6;
    private static volatile int KIAI_GUARD_BREAK = 40;
    private static volatile boolean PARRY_ENABLED = true;
    private static volatile int PARRY_WINDOW = 6;
    private static volatile int PARRY_COOLDOWN = 40;
    private static volatile double PARRY_KNOCKBACK = 2.5D;
    private static volatile double PARRY_REFLECT = 0.25D;
    private static volatile double PARRY_NPC_CHANCE = 0.3D;
    private static volatile int PARRY_STUN = 30;
    private static volatile int PARRY_COUNTER_TICKS = 40;
    private static volatile double PARRY_COUNTER_MULT = 1.5D;
    private static volatile int PARRY_PERFECT_TICKS = 2;
    private static volatile double PARRY_PERFECT_BONUS = 0.5D;
    private static volatile int CLASH_CONTACT = 10;
    private static volatile int CLASH_RESOLVE = 14;
    private static volatile boolean CLASH_ENABLED = true;
    private static volatile int CLASH_DURATION = 100;
    private static volatile int CLASH_PERIOD_SLOW = 30;
    private static volatile int CLASH_PERIOD_MEDIUM = 18;
    private static volatile int CLASH_PERIOD_FAST = 10;
    private static volatile int CLASH_FIXED_SPEED = -1;
    private static volatile int CLASH_PING_GRACE = 6;
    private static volatile double CLASH_NO_PRESS = 0.5D;
    private static volatile double CLASH_STAT_BONUS_CAP = 100.0D;
    private static volatile double CLASH_TIE_MARGIN = 10.0D;
    private static volatile double CLASH_OUTNUMBERED_BONUS = 1.6D;
    private static volatile int CLASH_TEAM_MAX = 3;
    private static volatile double CLASH_JOIN_RANGE = 6.0D;
    private static volatile boolean CLASH_JOIN_RESETS = false;
    private static volatile double CLASH_TIE_DAMAGE = 0.5D;
    private static volatile double CLASH_SUM = 1.0D;
    private static volatile double CLASH_RETURN_SPEED = 0.9D;
    private static volatile double CLASH_HOMING = 0.08D;
    private static volatile double CLASH_HOMING_RANGE = 24.0D;
    private static volatile double CLASH_MIN_DAMAGE = 1.0D;
    private static volatile double CLASH_MOB_SKILL = 0.35D;
    private static volatile double SB_RADIUS = 32.0D;
    private static volatile int KI_LIFE = 200;
    private static volatile double SB_GAIN = 0.03D;
    private static volatile double SB_CAP = 0.5D;
    private static volatile int SB_WINDUP = 8;
    private static volatile double SB_PARTY_GAIN = 0.06D;
    private static volatile double SB_PARTY_RADIUS = 0.0D;
    private static volatile double FLY_MULT_CAP = 15.0D;
    private static volatile double FLY_SCALING = 0.25D;
    private static volatile double FLY_BASE_SPEED = 0.05D;
    private static volatile double FLY_KI_DRAIN = 0.15D;
    private static volatile double FLY_TAKEOFF_IMPULSE = 0.55D;
    private static volatile double FLY_TAKEOFF_KI_COST = 2.0D;
    private static volatile int FLY_RAMP_UP = 30;
    private static volatile int FLY_RAMP_DOWN = 12;
    private static volatile double TURBO_DRAIN_PCT_PER_SEC = 0.005D;

    private static volatile double BODY_SCALE = 1.0D, STAMINA_SCALE = 1.0D, ENERGY_SCALE = 1.0D;
    private static volatile double REGEN_BODY = 1.5, REGEN_STAMINA = 3.0, REGEN_ENERGY = 1.0;
    private static volatile double FOOD_KI_PCT = 2.0, FOOD_STAMINA_PCT = 3.0;
    private static volatile double MOB_PROJECTILE_FACTOR = 0.6D;
    private static volatile double EXPLOSION_REFERENCE_DAMAGE = 12.0D;

    private static volatile double MIN_DAMAGE_PERCENT = 0.05D;
    private static volatile double IMMORTAL_OVERKILL_FRACTION = 1.0D;
    private static volatile int    TECHNIQUE_MAX_SLOTS = 12;
    private static volatile int    SENSE_KI_RANGE = 64;
    private static volatile boolean LOCK_ON_ENABLED = true, LOCK_ON_WHILE_FLYING = true;
    private static volatile int    LOCK_ON_RANGE = 64;
    private static volatile double LOCK_ON_HOMING_TURN = 0.20D;
    private static volatile double TECHNIQUE_EXPLOSION_RADIUS_CAP = 0.0D;
    private static volatile double VANILLA_PL_FACTOR = 1.0D;
    private static volatile int    SCOUTER_RANGE = 64;
    private static volatile double ATTRIBUTE_BASE_COST = 1.0D;
    private static volatile double TRAINING_PL_RATIO_FLOOR = 0.05D;
    private static volatile double TRAINING_PL_RATIO_FULL = 0.25D;
    private static volatile double REGEN_BODY_EXHAUSTION = 1.2D;
    private static volatile double REGEN_STAMINA_EXHAUSTION = 0.15D;
    private static volatile int    REGEN_MIN_FOOD = 6;
    private static volatile int    PARTY_MAX_SIZE_CEILING = 32;
    private static volatile int    ENERGY_GENERATOR_CAPACITY = 100_000;
    private static volatile int    ENERGY_GENERATOR_MAX_EXTRACT = 1_000;

    private static volatile boolean MIRROR_HEALTH = true;
    private static volatile double ABSORPTION_WEIGHT = 1.0D;

    private static volatile double FORM_MASTERY_PER_MINUTE = 0.5D;
    private static volatile boolean SSJ_BLUE_ROSE_SHARE_MASTERY = true;
    private static volatile boolean ALLOW_CUSTOM_HAIR = true;
    private static volatile double TECH_MASTERY_PER_USE = 0.2D;
    private static volatile double M_TECH_DMG = 0.25D, M_TECH_COST = 0.30D, M_TECH_CAST = 0.30D;
    private static volatile double KI_COST_PER_POWER = 0.42D;
    private static volatile double MELEE_STAMINA_PER_HIT = 0.10D;
    private static volatile double WEAPON_SCALE = 0.04D;
    private static volatile double MELEE_ENCHANT_SCALE = 1.0D;
    private static volatile double MELEE_ENCHANT_MULT_CAP = 5.0D;
    private static volatile double VANILLA_ARMOR_WEIGHT = 0.50D;
    private static volatile double PROJECTILE_BASE_DAMAGE = 6.0D;
    private static volatile double PROJECTILE_SCALE = 1.0D;
    private static volatile double PROJECTILE_MULT_CAP = 3.0D;
    private static volatile double KI_PER_BONUS_DAMAGE = 0.30D;
    private static volatile double PVP_DAMAGE_SCALE = 0.5D;
    private static volatile double KI_DEFENSE_FACTOR = 0.6D;
    private static volatile double KI_DAMAGE_SCALE = 1.5D;
    private static volatile double COMBAT_ATTACK_SPEED = 1.6D;
    private static volatile double OVERCHARGE_TIME_MULT = 2.5D;
    private static volatile double OVERCHARGE_COST_MULT = 1.5D;
    private static volatile double MAJIN_STAT_BONUS = 0.20D;
    private static volatile double LEGENDARY_STATE_BONUS = 0.10D;
    private static volatile double ARC_GOD_PP_CAP = 1.5D;
    private static volatile double ARC_GOD_PP_DRAIN = 0.5D;
    private static volatile double ARC_GOD_PP_STAT = 1.5D;
    private static volatile double HUMAN_LEGEND_DAMAGE_TAKEN = 0.25D;
    private static volatile double NAMEK_LEGEND_LIFESTEAL = 0.15D;
    private static volatile boolean NAMEK_LEGEND_NO_REGEN = true;
    private static volatile double ARC_LEGEND_PERCENT = 14.0D;
    private static volatile double ARC_LEGEND_TENSION = 45.0D;
    private static volatile double ARC_LEGEND_TENSION_MASTERY = 1.0D;
    private static volatile double ARC_LEGEND_COOLDOWN = 600.0D;
    private static volatile int MAJIN_LEGEND_EXTRA_CAP = 6;
    private static volatile double MAJIN_LEGEND_DURATION = 3.0D;
    private static volatile double MAJIN_LEGEND_STEAL = 0.25D;
    private static volatile double MAJIN_LEGEND_STEAL_MAX = 0.10D;
    private static volatile double MAJIN_LEGEND_STEAL_CAP = 0.50D;
    private static volatile double MAJIN_LEGEND_KI_REFUND = 10.0D;
    private static volatile double HUMAN_WILL_ALLY = 0.08D;
    private static volatile double HUMAN_WILL_HIT = 0.04D;
    private static volatile double HUMAN_WILL_MAX = 0.40D;
    private static volatile double HUMAN_WILL_RADIUS = 24.0D;
    private static volatile double NAMEK_LEGEND_REGEN = 1.0D;
    private static volatile double NAMEK_LEGEND_REGEN_KI = 0.15D;
    private static volatile double NAMEK_LEGEND_PASSIVE_COST = 0.25D;
    private static volatile boolean MAJIN_LEGENDARY_EXCLUSIVE = true;
    private static volatile double NAMEK_GIANT_STAT = 0.60D;
    private static volatile boolean VANILLA_STATS_FALLBACK = true;
    private static volatile double  VANILLA_TP_REWARD_FACTOR = 0.25D;
    private static volatile double VANILLA_PASSIVE_FACTOR = 1.0D;
    private static volatile double VANILLA_HOSTILE_FACTOR = 12.0D;
    private static volatile double VANILLA_BOSS_FACTOR = 40.0D;
    private static volatile double VANILLA_DAMAGE_RATIO = 0.4D;
    private static volatile double BF_CHANCE = 0.03D;
    private static volatile double BF_MULTIPLIER = 3.0D;
    private static volatile double BF_CHARGE_EXPONENT = 3.0D;
    private static volatile double BF_LUCK_FACTOR = 0.5D;
    private static volatile double BF_MAX_CHANCE = 0.25D;
    private static volatile double BF_STAT_FACTOR = 1.0D;
    private static volatile double EXPLOSION_SACRIFICE_CONVERSION = 0.5;

    private static volatile double TRAIN_DMG_TP = 0.10D, TRAIN_AIR_TP = 0.0001D,
            TRAIN_AIR_COST = 0.04D, TRAIN_HALF_LIFE = 0.10D, TRAIN_DECAY = 0.30D,
            TRAIN_HTC_MULT = 2.0D, TRAIN_MIN_EFF = 0.05D;
    private static volatile double TP_PER_PL = 0.25D;
    private static volatile int TRAIN_AIR_TICKS = 10;
    private static volatile double WEIGHT_CAP_DIV = 3.4D, WEIGHT_CAP_EXP = 0.6D,
            WEIGHT_STAT_PEN = 0.25D, WEIGHT_MOVE_PEN = 0.60D, WEIGHT_JUMP_PEN = 0.40D,
            WEIGHT_GRAVITY_JUMP_FACTOR = 0.25D,
            WEIGHT_TP_BONUS = 1.5D, WEIGHT_OVER_THRESH = 1.2D, WEIGHT_OVER_MOVE = 0.15D,
            WEIGHT_KAIOSAMA_TONS = 100.0D, WEIGHT_KAIOSAMA_GRAVITY_MULT = 10.0D,
            WEIGHT_HTC_TONS = 3.0D, WEIGHT_HTC_GRAVITY_MULT = 2.0D;
    private static volatile double PU_TP_MULT = 0.50D;
    private static volatile int    PU_ALIGNMENT_REQ = 50;
    private static volatile double PU_MAX_STAT = 12.0D;
    private static volatile double GODKI_TRIAL_FRACTION = 1.5D;
    private static volatile int GODKI_RITUAL_PARTICIPANTS = 5;
    private static volatile boolean GODKI_RITUAL_SAME_RACE = false;
    private static volatile int GODKI_RITUAL_MIN_ALIGNMENT = -100;
    private static volatile double GODKI_RITUAL_RADIUS = 8.0D;
    private static volatile int GODKI_RITUAL_SECONDS = 10;
    private static volatile double BEAST_OD_STAT = 0.5D;
    private static volatile double LEGENDARY_OD_STAT = 0.3D;
    private static volatile double LEGENDARY_GROWTH = 1.5D;
    private static volatile double LEGENDARY_CEILING = 12.5D;
    private static volatile double LEGENDARY_RESONANCE = 1.45D;
    private static volatile double BEAST_MASTERY_REQ = 50.0D;
    private static volatile double BEAST_FURY_RADIUS = 32.0D;
    private static volatile double ARC_PP_GAIN = 4.0D;
    private static volatile double ARC_PP_DRAIN = 4.0D;
    private static volatile double ARC_PP_COMBAT_DEALT = 2.0D;
    private static volatile double ARC_PP_COMBAT_TAKEN = 3.0D;
    private static volatile double ARC_GOD_REFILL = 2.0D;
    private static volatile double ARC_PP_STAT = 2.0D;
    private static volatile boolean MAJIN_ABSORB_ENABLED = true;
    private static volatile double MAJIN_ABSORB_DOMINANT_PL_RATIO = 1.5D;
    private static volatile double MAJIN_ABSORB_MAX_HEALTH_PCT = 30.0D;
    private static volatile double MAJIN_ABSORB_MAX_PL_RATIO = 1.5D;
    private static volatile double MAJIN_ABSORB_KI_COST_PCT = 15.0D;
    private static volatile boolean MAJIN_ABSORB_PVP = false;
    private static volatile int MAJIN_MASS_CAP = 5;
    private static volatile double MAJIN_MASS_SECONDS = 120.0D;
    private static volatile double MAJIN_MASS_STAT_PER_STACK = 0.04D;
    private static volatile double MAJIN_MASS_PERM_PER_ABSORB = 0.002D;
    private static volatile double MAJIN_MASS_PERM_CAP = 0.05D;
    private static volatile int MAJIN_VORAZ_EXTRA_CAP = 2;
    private static volatile double MAJIN_SACIADO_DURATION_MULT = 2.0D;
    private static volatile double MAJIN_PRIMITIVO_BONUS_MULT = 1.5D;
    private static volatile double MAJIN_PRIMITIVO_DURATION_MULT = 0.5D;
    private static volatile double FORM_DRAIN_SCALE = 4.0D;
    private static volatile double FORM_DRAIN_SPI_MIN = 1.0D;
    private static volatile double FORM_DRAIN_SPI_MAX = 1.5D;
    private static volatile double FORM_DRAIN_FLOOR = 0.25D;
    private static volatile double UI_HEAT_RATE = 2.0D;
    private static volatile double UI_HEAT_PER_DODGE = 3.0D;
    private static volatile double UI_HEAT_SLOW = 0.06D;
    private static volatile double UI_HEAT_COOL = 4.0D;
    private static volatile double UI_DODGE_COST = 8.0D;
    private static volatile double UI_DODGE_REDUCE = 0.1D;
    private static volatile double UI_DODGE_CHANCE = 40.0D;
    private static volatile double UI_DODGE_CHANCE_LEVEL = 3.3333D;
    private static volatile double UI_DODGE_CHANCE_MAX = 90.0D;
    private static volatile double UI_HEAT_RECOVER = 50.0D;
    private static volatile double UI_MASTERY_DODGE = 20.0D;
    private static volatile double UI_MASTERY_HEAT = 0.4D;
    private static volatile double UE_MASTERY_BONUS = 0.5D;
    private static volatile double UE_CHARGE = 2.0D;
    private static volatile double UE_DECAY = 1.0D;
    private static volatile double UE_STAT_LV5 = 1.0D;
    private static volatile double UE_STAT_LV10 = 3.0D;
    private static volatile double UE_PEN = 0.2D;


    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() != SPEC) return;

        TP_COEFFICIENT       = TP_COEFFICIENT_RAW.get();
        GLOBAL_ATTRIBUTE_CAP = GLOBAL_ATTRIBUTE_CAP_RAW.get();
        AURA_REFERENCE_TP    = AURA_REFERENCE_TP_RAW.get();
        ATTRIBUTE_BASE_COST = ATTRIBUTE_BASE_COST_RAW.get();
        TRAINING_PL_RATIO_FLOOR = TRAINING_PL_RATIO_FLOOR_RAW.get();
        IN_COMBAT_TICKS              = IN_COMBAT_TICKS_RAW.get();
        IN_COMBAT_BODY_REGEN_MULT    = IN_COMBAT_BODY_REGEN_MULT_RAW.get();
        IN_COMBAT_RACIAL_REGEN_FLOOR = IN_COMBAT_RACIAL_REGEN_FLOOR_RAW.get();

        SPEED_MULT_CAP    = SPEED_MULT_CAP_RAW.get();
        MOVE_SCALING      = MOVE_SCALING_RAW.get();
        RUN_STAMINA_DRAIN = RUN_STAMINA_DRAIN_RAW.get();
        DASH_COOLDOWN     = DASH_COOLDOWN_RAW.get();
        DASH_SPEED        = DASH_SPEED_RAW.get();
        STEP_COOLDOWN     = STEP_COOLDOWN_RAW.get();
        STEP_SPEED        = STEP_SPEED_RAW.get();
        STEP_ENERGY_PCT   = STEP_ENERGY_PCT_RAW.get();
        STEP_INVISIBLE    = STEP_INVISIBLE_RAW.get();
        STEP_IFRAME       = STEP_IFRAME_RAW.get();
        BLOCK_WEAPON_FACTOR = BLOCK_WEAPON_FACTOR_RAW.get();
        BLOCK_SHIELD_BONUS = BLOCK_SHIELD_BONUS_RAW.get();
        BLOCK_SHIELD_CAP  = BLOCK_SHIELD_CAP_RAW.get();
        BLOCK_SHIELD_SLOW = BLOCK_SHIELD_SLOW_RAW.get();
        BLOCK_SHIELD_DURABILITY = BLOCK_SHIELD_DURABILITY_RAW.get();
        BLOCK_SHIELD_KI   = BLOCK_SHIELD_KI_RAW.get();
        GUARD_MAX         = GUARD_MAX_RAW.get();
        GUARD_DRAIN       = GUARD_DRAIN_RAW.get();
        GUARD_CON_BONUS   = GUARD_CON_BONUS_RAW.get();
        GUARD_CON_FULL    = GUARD_CON_FULL_RAW.get();
        GUARD_DEX_BONUS   = GUARD_DEX_BONUS_RAW.get();
        GUARD_DEX_FULL    = GUARD_DEX_FULL_RAW.get();
        GUARD_REGEN       = GUARD_REGEN_RAW.get();
        GUARD_REGEN_DELAY = GUARD_REGEN_DELAY_RAW.get();
        GUARD_BREAK_STUN  = GUARD_BREAK_STUN_RAW.get();
        STUN_PLAYERS      = STUN_PLAYERS_RAW.get();
        STUN_MAX          = STUN_MAX_RAW.get();
        STUN_DR           = STUN_DR_RAW.get();
        STUN_DR_WINDOW    = STUN_DR_WINDOW_RAW.get();
        STUN_IMMUNITY     = STUN_IMMUNITY_RAW.get();
        COMBO_WINDOW      = COMBO_WINDOW_RAW.get();
        COMBO_DMG         = COMBO_DMG_RAW.get();
        COMBO_MAX         = COMBO_MAX_RAW.get();
        HEAVY_TICKS       = HEAVY_TICKS_RAW.get();
        HEAVY_LEVELS      = HEAVY_LEVELS_RAW.get();
        HEAVY_DMG         = HEAVY_DMG_RAW.get();
        HEAVY_KNOCK       = HEAVY_KNOCK_RAW.get();
        HEAVY_STAMINA     = HEAVY_STAMINA_RAW.get();
        DIVE_MIN_HEIGHT   = DIVE_MIN_HEIGHT_RAW.get();
        DIVE_SPEED        = DIVE_SPEED_RAW.get();
        DIVE_MAX_TICKS    = DIVE_MAX_TICKS_RAW.get();
        DIVE_RADIUS       = DIVE_RADIUS_RAW.get();
        DIVE_MAX_RADIUS   = DIVE_MAX_RADIUS_RAW.get();
        DIVE_KNOCK        = DIVE_KNOCK_RAW.get();
        EVADE_TICKS       = EVADE_TICKS_RAW.get();
        EVADE_CHARGE_TICKS = EVADE_CHARGE_TICKS_RAW.get();
        GRAB_RANGE        = GRAB_RANGE_RAW.get();
        GRAB_HOLD         = GRAB_HOLD_RAW.get();
        GRAB_THROW        = GRAB_THROW_RAW.get();
        GRAB_STUN         = GRAB_STUN_RAW.get();
        GRAB_MOB_HOLD     = GRAB_MOB_HOLD_RAW.get();
        GRAB_CHECKS       = GRAB_CHECKS_RAW.get();
        GRAB_ZONE         = GRAB_ZONE_RAW.get();
        GRAB_NEEDLE       = GRAB_NEEDLE_RAW.get();
        GRAB_FAIL         = GRAB_FAIL_RAW.get();
        GRAB_GAP          = GRAB_GAP_RAW.get();
        EVADE_WAVE_RADIUS = EVADE_WAVE_RADIUS_RAW.get();
        EVADE_WAVE_KNOCK  = EVADE_WAVE_KNOCK_RAW.get();
        EVADE_WAVE_STUN   = EVADE_WAVE_STUN_RAW.get();
        COMBO_FINISHER_EVERY = COMBO_FINISHER_EVERY_RAW.get();
        COMBO_FINISHER_KNOCK = COMBO_FINISHER_KNOCK_RAW.get();
        BARE_ATTACK_SPEED = BARE_ATTACK_SPEED_RAW.get();
        KIAI_OMNI         = KIAI_OMNI_RAW.get();
        KIAI_EXTRA_STAMINA = KIAI_EXTRA_STAMINA_RAW.get();
        KIAI_EXTRA_CAP    = KIAI_EXTRA_CAP_RAW.get();
        KIAI_GUARD_BREAK  = KIAI_GUARD_BREAK_RAW.get();
        PARRY_ENABLED     = PARRY_ENABLED_RAW.get();
        PARRY_WINDOW      = PARRY_WINDOW_RAW.get();
        PARRY_COOLDOWN    = PARRY_COOLDOWN_RAW.get();
        PARRY_KNOCKBACK   = PARRY_KNOCKBACK_RAW.get();
        PARRY_REFLECT     = PARRY_REFLECT_RAW.get();
        PARRY_NPC_CHANCE  = PARRY_NPC_CHANCE_RAW.get();
        PARRY_STUN        = PARRY_STUN_RAW.get();
        PARRY_COUNTER_TICKS = PARRY_COUNTER_TICKS_RAW.get();
        PARRY_COUNTER_MULT = PARRY_COUNTER_MULT_RAW.get();
        PARRY_PERFECT_TICKS = PARRY_PERFECT_TICKS_RAW.get();
        PARRY_PERFECT_BONUS = PARRY_PERFECT_BONUS_RAW.get();
        CLASH_CONTACT     = CLASH_CONTACT_RAW.get();
        CLASH_RESOLVE     = CLASH_RESOLVE_RAW.get();
        CLASH_ENABLED     = CLASH_ENABLED_RAW.get();
        CLASH_DURATION    = CLASH_DURATION_RAW.get();
        CLASH_PERIOD_SLOW = CLASH_PERIOD_SLOW_RAW.get();
        CLASH_PERIOD_MEDIUM = CLASH_PERIOD_MEDIUM_RAW.get();
        CLASH_PERIOD_FAST = CLASH_PERIOD_FAST_RAW.get();
        CLASH_FIXED_SPEED = CLASH_FIXED_SPEED_RAW.get();
        CLASH_PING_GRACE  = CLASH_PING_GRACE_RAW.get();
        CLASH_NO_PRESS    = CLASH_NO_PRESS_RAW.get();
        CLASH_STAT_BONUS_CAP = CLASH_STAT_BONUS_CAP_RAW.get();
        CLASH_TIE_MARGIN  = CLASH_TIE_MARGIN_RAW.get();
        CLASH_OUTNUMBERED_BONUS = CLASH_OUTNUMBERED_BONUS_RAW.get();
        CLASH_TEAM_MAX    = CLASH_TEAM_MAX_RAW.get();
        CLASH_JOIN_RANGE  = CLASH_JOIN_RANGE_RAW.get();
        CLASH_JOIN_RESETS = CLASH_JOIN_RESETS_RAW.get();
        CLASH_TIE_DAMAGE  = CLASH_TIE_DAMAGE_RAW.get();
        CLASH_SUM         = CLASH_SUM_RAW.get();
        CLASH_RETURN_SPEED = CLASH_RETURN_SPEED_RAW.get();
        CLASH_HOMING      = CLASH_HOMING_RAW.get();
        CLASH_HOMING_RANGE = CLASH_HOMING_RANGE_RAW.get();
        CLASH_MIN_DAMAGE  = CLASH_MIN_DAMAGE_RAW.get();
        CLASH_MOB_SKILL   = CLASH_MOB_SKILL_RAW.get();
        SB_RADIUS         = SB_RADIUS_RAW.get();
        KI_LIFE           = KI_LIFE_RAW.get();
        SB_GAIN           = SB_GAIN_RAW.get();
        SB_CAP            = SB_CAP_RAW.get();
        SB_WINDUP         = SB_WINDUP_RAW.get();
        SB_PARTY_GAIN     = SB_PARTY_GAIN_RAW.get();
        SB_PARTY_RADIUS   = SB_PARTY_RADIUS_RAW.get();
        FLY_MULT_CAP      = FLY_MULT_CAP_RAW.get();
        FLY_SCALING       = FLY_SCALING_RAW.get();
        FLY_BASE_SPEED    = FLY_BASE_SPEED_RAW.get();
        FLY_KI_DRAIN      = FLY_KI_DRAIN_RAW.get();
        FLY_TAKEOFF_IMPULSE = FLY_TAKEOFF_IMPULSE_RAW.get();
        FLY_TAKEOFF_KI_COST = FLY_TAKEOFF_KI_COST_RAW.get();
        FLY_RAMP_UP = FLY_RAMP_UP_RAW.get();
        FLY_RAMP_DOWN = FLY_RAMP_DOWN_RAW.get();
        TURBO_DRAIN_PCT_PER_SEC = TURBO_DRAIN_PCT_PER_SEC_RAW.get();
        TRAINING_PL_RATIO_FULL   = TRAINING_PL_RATIO_FULL_RAW.get();
        REGEN_BODY_EXHAUSTION    = REGEN_BODY_EXHAUSTION_RAW.get();
        REGEN_STAMINA_EXHAUSTION = REGEN_STAMINA_EXHAUSTION_RAW.get();
        REGEN_MIN_FOOD           = REGEN_MIN_FOOD_RAW.get();
        PARTY_MAX_SIZE_CEILING   = PARTY_MAX_SIZE_CEILING_RAW.get();
        ENERGY_GENERATOR_CAPACITY    = ENERGY_GENERATOR_CAPACITY_RAW.get();
        ENERGY_GENERATOR_MAX_EXTRACT = ENERGY_GENERATOR_MAX_EXTRACT_RAW.get();

        BODY_SCALE    = BODY_SCALE_RAW.get();
        STAMINA_SCALE = STAMINA_SCALE_RAW.get();
        ENERGY_SCALE  = ENERGY_SCALE_RAW.get();
        REGEN_BODY    = REGEN_BODY_RAW.get();
        REGEN_STAMINA = REGEN_STAMINA_RAW.get();
        REGEN_ENERGY  = REGEN_ENERGY_RAW.get();
        FOOD_KI_PCT      = FOOD_KI_RAW.get();
        FOOD_STAMINA_PCT = FOOD_STAMINA_RAW.get();

        VANILLA_STATS_FALLBACK   = VANILLA_STATS_FALLBACK_RAW.get();
        VANILLA_DAMAGE_RATIO = VANILLA_DAMAGE_RATIO_RAW.get();
        VANILLA_TP_REWARD_FACTOR = VANILLA_TP_REWARD_FACTOR_RAW.get();
        VANILLA_PASSIVE_FACTOR = VANILLA_PASSIVE_FACTOR_RAW.get();
        VANILLA_BOSS_FACTOR = VANILLA_BOSS_FACTOR_RAW.get();
        VANILLA_HOSTILE_FACTOR = VANILLA_HOSTILE_FACTOR_RAW.get();
        EXPLOSION_REFERENCE_DAMAGE = EXPLOSION_REFERENCE_DAMAGE_RAW.get();
        MOB_PROJECTILE_FACTOR = MOB_PROJECTILE_FACTOR_RAW.get();
        EXPLOSION_SACRIFICE_CONVERSION = EXPLOSION_SACRIFICE_CONVERSION_RAW.get();

        MIN_DAMAGE_PERCENT  = MIN_DAMAGE_PERCENT_RAW.get();
        IMMORTAL_OVERKILL_FRACTION = IMMORTAL_OVERKILL_FRACTION_RAW.get();
        TECHNIQUE_MAX_SLOTS = TECHNIQUE_MAX_SLOTS_RAW.get();
        SENSE_KI_RANGE      = SENSE_KI_RANGE_RAW.get();
        LOCK_ON_ENABLED     = LOCK_ON_ENABLED_RAW.get();
        LOCK_ON_WHILE_FLYING = LOCK_ON_WHILE_FLYING_RAW.get();
        LOCK_ON_RANGE       = LOCK_ON_RANGE_RAW.get();
        LOCK_ON_HOMING_TURN = LOCK_ON_HOMING_TURN_RAW.get();
        TECHNIQUE_EXPLOSION_RADIUS_CAP = TECHNIQUE_EXPLOSION_RADIUS_CAP_RAW.get();
        VANILLA_PL_FACTOR   = VANILLA_PL_FACTOR_RAW.get();
        SCOUTER_RANGE       = SCOUTER_RANGE_RAW.get();

        FORM_MASTERY_PER_MINUTE = FORM_MASTERY_PER_MINUTE_RAW.get();
        SSJ_BLUE_ROSE_SHARE_MASTERY = SSJ_BLUE_ROSE_SHARE_MASTERY_RAW.get();
        ALLOW_CUSTOM_HAIR = ALLOW_CUSTOM_HAIR_RAW.get();
        TECH_MASTERY_PER_USE    = TECH_MASTERY_PER_USE_RAW.get();
        M_TECH_DMG       = MASTERY_TECH_DMG_RAW.get();
        M_TECH_COST      = MASTERY_TECH_COST_RAW.get();
        M_TECH_CAST      = MASTERY_TECH_CAST_RAW.get();
        MAJIN_STAT_BONUS = MAJIN_STAT_BONUS_RAW.get();
        LEGENDARY_STATE_BONUS = LEGENDARY_STATE_BONUS_RAW.get();
        ARC_GOD_PP_CAP = ARC_GOD_PP_CAP_RAW.get();
        ARC_GOD_PP_DRAIN = ARC_GOD_PP_DRAIN_RAW.get();
        ARC_GOD_PP_STAT = ARC_GOD_PP_STAT_RAW.get();
        HUMAN_LEGEND_DAMAGE_TAKEN = HUMAN_LEGEND_DEFENSE_RAW.get();
        NAMEK_LEGEND_LIFESTEAL = NAMEK_LEGEND_LIFESTEAL_RAW.get();
        NAMEK_LEGEND_NO_REGEN = NAMEK_LEGEND_NO_REGEN_RAW.get();
        ARC_LEGEND_PERCENT = ARC_LEGEND_PERCENT_RAW.get();
        ARC_LEGEND_TENSION = ARC_LEGEND_TENSION_RAW.get();
        ARC_LEGEND_TENSION_MASTERY = ARC_LEGEND_TENSION_MASTERY_RAW.get();
        ARC_LEGEND_COOLDOWN = ARC_LEGEND_COOLDOWN_RAW.get();
        MAJIN_LEGEND_EXTRA_CAP = MAJIN_LEGEND_EXTRA_CAP_RAW.get();
        MAJIN_LEGEND_DURATION = MAJIN_LEGEND_DURATION_MULT_RAW.get();
        MAJIN_LEGEND_STEAL = MAJIN_LEGEND_STEAL_RAW.get();
        MAJIN_LEGEND_STEAL_MAX = MAJIN_LEGEND_STEAL_MAX_RAW.get();
        MAJIN_LEGEND_STEAL_CAP = MAJIN_LEGEND_STEAL_CAP_RAW.get();
        MAJIN_LEGEND_KI_REFUND = MAJIN_LEGEND_KI_REFUND_RAW.get();
        HUMAN_WILL_ALLY = HUMAN_WILL_ALLY_BONUS_RAW.get();
        HUMAN_WILL_HIT = HUMAN_WILL_HIT_BONUS_RAW.get();
        HUMAN_WILL_MAX = HUMAN_WILL_MAX_RAW.get();
        HUMAN_WILL_RADIUS = HUMAN_WILL_RADIUS_RAW.get();
        NAMEK_LEGEND_REGEN = NAMEK_LEGEND_REGEN_RAW.get();
        NAMEK_LEGEND_REGEN_KI = NAMEK_LEGEND_REGEN_KI_RAW.get();
        NAMEK_LEGEND_PASSIVE_COST = NAMEK_LEGEND_PASSIVE_COST_RAW.get();
        MAJIN_LEGENDARY_EXCLUSIVE = MAJIN_LEGENDARY_EXCLUSIVE_RAW.get();
        NAMEK_GIANT_STAT = NAMEK_GIANT_STAT_RAW.get();

        TRAIN_DMG_TP    = TRAIN_DMG_TP_RAW.get();
        TP_PER_PL       = TP_PER_PL_RAW.get();
        TRAIN_AIR_TP    = TRAIN_AIR_TP_RAW.get();
        TRAIN_AIR_COST  = TRAIN_AIR_COST_RAW.get();
        TRAIN_AIR_TICKS = TRAIN_AIR_TICKS_RAW.get();
        TRAIN_HALF_LIFE = TRAIN_HALF_LIFE_RAW.get();
        TRAIN_DECAY     = TRAIN_DECAY_RAW.get();
        TRAIN_HTC_MULT  = TRAIN_HTC_MULT_RAW.get();
        TRAIN_MIN_EFF   = TRAIN_MIN_EFF_RAW.get();
        MIRROR_HEALTH = MIRROR_HEALTH_RAW.get();
        ABSORPTION_WEIGHT = ABSORPTION_WEIGHT_RAW.get();

        WEIGHT_CAP_DIV     = WEIGHT_CAP_DIV_RAW.get();
        WEIGHT_CAP_EXP     = WEIGHT_CAP_EXP_RAW.get();
        WEIGHT_STAT_PEN    = WEIGHT_STAT_PEN_RAW.get();
        WEIGHT_MOVE_PEN    = WEIGHT_MOVE_PEN_RAW.get();
        WEIGHT_JUMP_PEN    = WEIGHT_JUMP_PEN_RAW.get();
        WEIGHT_GRAVITY_JUMP_FACTOR = WEIGHT_GRAVITY_JUMP_FACTOR_RAW.get();
        WEIGHT_TP_BONUS    = WEIGHT_TP_BONUS_RAW.get();
        WEIGHT_OVER_THRESH = WEIGHT_OVER_THRESH_RAW.get();
        WEIGHT_OVER_MOVE   = WEIGHT_OVER_MOVE_RAW.get();
        WEIGHT_KAIOSAMA_TONS = WEIGHT_KAIOSAMA_TONS_RAW.get();
        WEIGHT_KAIOSAMA_GRAVITY_MULT = WEIGHT_KAIOSAMA_GRAVITY_MULT_RAW.get();
        WEIGHT_HTC_TONS = WEIGHT_HTC_TONS_RAW.get();
        WEIGHT_HTC_GRAVITY_MULT = WEIGHT_HTC_GRAVITY_MULT_RAW.get();
        PU_TP_MULT       = PU_TP_MULT_RAW.get();
        PU_ALIGNMENT_REQ = PU_ALIGNMENT_REQ_RAW.get();
        PU_MAX_STAT      = PU_MAX_STAT_RAW.get();
        GODKI_TRIAL_FRACTION = GODKI_TRIAL_FRACTION_RAW.get();
        GODKI_RITUAL_PARTICIPANTS = GODKI_RITUAL_PARTICIPANTS_RAW.get();
        GODKI_RITUAL_SAME_RACE = GODKI_RITUAL_SAME_RACE_RAW.get();
        GODKI_RITUAL_MIN_ALIGNMENT = GODKI_RITUAL_MIN_ALIGNMENT_RAW.get();
        GODKI_RITUAL_RADIUS = GODKI_RITUAL_RADIUS_RAW.get();
        GODKI_RITUAL_SECONDS = GODKI_RITUAL_SECONDS_RAW.get();
        BEAST_OD_STAT = BEAST_OD_STAT_RAW.get();
        LEGENDARY_OD_STAT = LEGENDARY_OD_STAT_RAW.get();
        LEGENDARY_GROWTH = LEGENDARY_GROWTH_RAW.get();
        LEGENDARY_CEILING = LEGENDARY_CEILING_RAW.get();
        LEGENDARY_RESONANCE = LEGENDARY_RESONANCE_RAW.get();
        BEAST_MASTERY_REQ = BEAST_MASTERY_REQ_RAW.get();
        BEAST_FURY_RADIUS = BEAST_FURY_RADIUS_RAW.get();
        ARC_PP_GAIN = ARC_PP_GAIN_RAW.get();
        ARC_PP_DRAIN = ARC_PP_DRAIN_RAW.get();
        ARC_PP_COMBAT_DEALT = ARC_PP_COMBAT_DEALT_RAW.get();
        ARC_PP_COMBAT_TAKEN = ARC_PP_COMBAT_TAKEN_RAW.get();
        ARC_GOD_REFILL = ARC_GOD_REFILL_RAW.get();
        ARC_PP_STAT = ARC_PP_STAT_RAW.get();
        MAJIN_ABSORB_ENABLED = MAJIN_ABSORB_ENABLED_RAW.get();
        MAJIN_ABSORB_MAX_HEALTH_PCT = MAJIN_ABSORB_MAX_HEALTH_PCT_RAW.get();
        MAJIN_ABSORB_DOMINANT_PL_RATIO = MAJIN_ABSORB_DOMINANT_PL_RATIO_RAW.get();
        MAJIN_ABSORB_MAX_PL_RATIO = MAJIN_ABSORB_MAX_PL_RATIO_RAW.get();
        MAJIN_ABSORB_KI_COST_PCT = MAJIN_ABSORB_KI_COST_PCT_RAW.get();
        MAJIN_ABSORB_PVP = MAJIN_ABSORB_PVP_RAW.get();
        MAJIN_MASS_CAP = MAJIN_MASS_CAP_RAW.get();
        MAJIN_MASS_SECONDS = MAJIN_MASS_SECONDS_RAW.get();
        MAJIN_MASS_STAT_PER_STACK = MAJIN_MASS_STAT_PER_STACK_RAW.get();
        MAJIN_MASS_PERM_PER_ABSORB = MAJIN_MASS_PERM_PER_ABSORB_RAW.get();
        MAJIN_MASS_PERM_CAP = MAJIN_MASS_PERM_CAP_RAW.get();
        MAJIN_VORAZ_EXTRA_CAP = MAJIN_VORAZ_EXTRA_CAP_RAW.get();
        MAJIN_SACIADO_DURATION_MULT = MAJIN_SACIADO_DURATION_MULT_RAW.get();
        MAJIN_PRIMITIVO_BONUS_MULT = MAJIN_PRIMITIVO_BONUS_MULT_RAW.get();
        MAJIN_PRIMITIVO_DURATION_MULT = MAJIN_PRIMITIVO_DURATION_MULT_RAW.get();
        FORM_DRAIN_SCALE = FORM_DRAIN_SCALE_RAW.get();
        FORM_DRAIN_SPI_MIN = FORM_DRAIN_SPI_MIN_RAW.get();
        FORM_DRAIN_SPI_MAX = FORM_DRAIN_SPI_MAX_RAW.get();
        FORM_DRAIN_FLOOR = FORM_DRAIN_FLOOR_RAW.get();
        UI_HEAT_RATE = UI_HEAT_RATE_RAW.get();
        UI_HEAT_PER_DODGE = UI_HEAT_PER_DODGE_RAW.get();
        UI_HEAT_SLOW = UI_HEAT_SLOW_RAW.get();
        UI_HEAT_COOL = UI_HEAT_COOL_RAW.get();
        UI_DODGE_COST = UI_DODGE_COST_RAW.get();
        UI_DODGE_REDUCE = UI_DODGE_REDUCE_RAW.get();
        UI_DODGE_CHANCE = UI_DODGE_CHANCE_RAW.get();
        UI_DODGE_CHANCE_LEVEL = UI_DODGE_CHANCE_LEVEL_RAW.get();
        UI_DODGE_CHANCE_MAX = UI_DODGE_CHANCE_MAX_RAW.get();
        UI_HEAT_RECOVER = UI_HEAT_RECOVER_RAW.get();
        UI_MASTERY_DODGE = UI_MASTERY_DODGE_RAW.get();
        UI_MASTERY_HEAT = UI_MASTERY_HEAT_RAW.get();
        UE_MASTERY_BONUS = UE_MASTERY_BONUS_RAW.get();
        UE_CHARGE = UE_CHARGE_RAW.get();
        UE_DECAY = UE_DECAY_RAW.get();
        UE_STAT_LV5 = UE_STAT_LV5_RAW.get();
        UE_STAT_LV10 = UE_STAT_LV10_RAW.get();
        UE_PEN = UE_PEN_RAW.get();

        KI_COST_PER_POWER     = KI_COST_PER_POWER_RAW.get();
        MELEE_STAMINA_PER_HIT = MELEE_STAMINA_PER_HIT_RAW.get();
        WEAPON_SCALE          = WEAPON_SCALE_RAW.get();
        MELEE_ENCHANT_SCALE    = MELEE_ENCHANT_SCALE_RAW.get();
        MELEE_ENCHANT_MULT_CAP = MELEE_ENCHANT_MULT_CAP_RAW.get();
        VANILLA_ARMOR_WEIGHT   = VANILLA_ARMOR_WEIGHT_RAW.get();
        PROJECTILE_BASE_DAMAGE = PROJECTILE_BASE_DAMAGE_RAW.get();
        PROJECTILE_SCALE       = PROJECTILE_SCALE_RAW.get();
        PROJECTILE_MULT_CAP    = PROJECTILE_MULT_CAP_RAW.get();
        KI_PER_BONUS_DAMAGE   = KI_PER_BONUS_DAMAGE_RAW.get();
        PVP_DAMAGE_SCALE      = PVP_DAMAGE_SCALE_RAW.get();
        KI_DEFENSE_FACTOR     = KI_DEFENSE_FACTOR_RAW.get();
        KI_DAMAGE_SCALE       = KI_DAMAGE_SCALE_RAW.get();
        COMBAT_ATTACK_SPEED   = COMBAT_ATTACK_SPEED_RAW.get();
        OVERCHARGE_TIME_MULT  = OVERCHARGE_TIME_MULT_RAW.get();
        OVERCHARGE_COST_MULT  = OVERCHARGE_COST_MULT_RAW.get();
        BF_LUCK_FACTOR = BF_LUCK_FACTOR_RAW.get();
        BF_CHANCE          = BF_CHANCE_RAW.get();
        BF_CHARGE_EXPONENT = BF_CHARGE_EXPONENT_RAW.get();
        BF_MAX_CHANCE        = BF_MAX_CHANCE_RAW.get();
        BF_MULTIPLIER        = BF_MULTIPLIER_RAW.get();
        BF_STAT_FACTOR        = BF_STAT_FACTOR_RAW.get();

        Map<ResourceLocation, Integer> parsedOverrides = new HashMap<>();
        for (String line : STACK_OVERRIDES_RAW.get()) {
            if (line == null || !line.contains("=")) continue;
            String[] parts = line.split("=", 2);
            if (parts.length != 2) continue;

            try {
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;

                int raw = Integer.parseInt(parts[1].trim());
                int count = Math.max(1, raw); // no limitamos aquí; el clamp final se hace en resolve
                parsedOverrides.put(id, count);
            } catch (Exception ignored) {
                // silencioso
            }
        }
        STACK_OVERRIDES = Collections.unmodifiableMap(parsedOverrides);

        Set<ResourceLocation> parsedBanned = new HashSet<>();
        for (String rawId : BANNED_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(rawId.trim());
                if (id != null) parsedBanned.add(id);
            } catch (Exception ignored) {
                // silencioso
            }
        }
        BANNED_ITEMS = Collections.unmodifiableSet(parsedBanned);

        Set<String> parsedRaces = new HashSet<>();
        for (String r : BANNED_RACES_RAW.get()) parsedRaces.add(r.trim().toLowerCase(java.util.Locale.ROOT));
        BANNED_RACES = Collections.unmodifiableSet(parsedRaces);

        Set<ResourceLocation> parsedWhitelist = new HashSet<>();
        for (String rawId : WHITELIST_ITEMS_RAW.get()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(rawId.trim());
                if (id != null) parsedWhitelist.add(id);
            } catch (Exception ignored) {
            }
        }
        WHITELIST_ITEMS = Collections.unmodifiableSet(parsedWhitelist);
    }

    public static Map<ResourceLocation, Integer> getStackOverrides() {
        return STACK_OVERRIDES;
    }

    /** ¿Está vetada esta raza en el menú de razas? (solo impide ELEGIRLA; ver banned_races) */
    public static boolean isRaceBanned(com.hmc.zenkai.feature.Race race) {
        return race != null && BANNED_RACES.contains(race.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isBanned(ResourceLocation id) {
        return BANNED_ITEMS.contains(id);
    }

    /**
     * Prohibido en el deseo de duplicación: o está en {@code banned_items} (config) o lleva el tag
     * {@code zenkai:wish_banned} (esferas del dragón, siete fundidas, piedras de esfera). El tag NO se puede
     * quitar desde la config: duplicar esferas rompería la búsqueda de las siete.
     */
    public static boolean isBanned(net.minecraft.world.item.Item item) {
        return isBanned(BuiltInRegistries.ITEM.getKey(item))
                || BuiltInRegistries.ITEM.wrapAsHolder(item).is(com.hmc.zenkai.registry.ModTags.Items.WISH_BANNED);
    }

    /**
     * Prohibido en el deseo de duplicación, mirando el STACK entero: lo de {@link #isBanned(net.minecraft.world.item.Item)}
     * y, además, todo objeto con INVENTARIO PROPIO. Duplicar una mochila copia su contenido: es un exploit fijo, y hay
     * demasiados mods de mochilas para listarlos por id. Casi todos exponen {@code Capabilities.ItemHandler.ITEM} en el
     * stack, así que se detecta por eso (mochilas, cofres portátiles...) y no por mod. También se rechazan los de vanilla
     * que guardan objetos en componentes (shulker, bundle) si llevan algo dentro. Se mira el stack y no solo el objeto
     * porque la capability y los componentes viven en el stack.
     */
    public static boolean isBanned(ItemStack stack) {
        if (isBanned(stack.getItem())) return true;
        if (stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM) != null) return true;
        var container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null && container.nonEmptyStream().findAny().isPresent()) return true;
        var bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        return bundle != null && !bundle.isEmpty();
    }

    public static Set<ResourceLocation> getBannedItems() {
        return BANNED_ITEMS;
    }

    /** Whitelist: vacía => permitido (salvo blacklist). Con ítems => solo esos. */
    public static boolean isAllowedByWhitelist(ResourceLocation id) {
        return WHITELIST_ITEMS.isEmpty() || WHITELIST_ITEMS.contains(id);
    }

    // ── Helpers de balance ──────────────────────────────────────────────────────
    public static int trainingPointsAmount()    { return TRAINING_POINTS_AMOUNT.get(); }
    public static long summonCooldownTicks()     { return (long) SUMMON_COOLDOWN_DAYS.get() * 24000L; }
    public static int shenlongWishCount()        { return SHENLONG_WISHES.get(); }
    public static int namekDragonWishCount()     { return NAMEK_DRAGON_WISHES.get(); }
    public static int broadcastRadius()          { return BROADCAST_RADIUS.get(); }
    public static boolean instantTransmissionEnabled()     { return INSTANT_TRANSMISSION_ENABLED.get(); }
    public static boolean instantTransmissionMenuEnabled() { return INSTANT_TRANSMISSION_MENU_ENABLED.get(); }

    public static double meditationTpPerCombo()          { return MEDITATION_TP_PER_COMBO.get(); }
    public static int meditationSessionTpCap()           { return MEDITATION_SESSION_TP_CAP.get(); }
    public static int meditationMinCooldownTicks()        { return MEDITATION_MIN_COOLDOWN_TICKS.get(); }
    public static double targetPracticeTpPerOrb()        { return TARGET_PRACTICE_TP_PER_ORB.get(); }
    public static int targetPracticeSessionTpCap()        { return TARGET_PRACTICE_SESSION_TP_CAP.get(); }
    public static int targetPracticeMinCooldownTicks()    { return TARGET_PRACTICE_MIN_COOLDOWN_TICKS.get(); }

    // =====================================================================
    // GETTERS hot-path (migrados desde CommonConfig) — leen SOLO la copia
    // volátil de arriba, nunca `_RAW.get()` directamente.
    // =====================================================================

    public static double tpCoefficient()   { return TP_COEFFICIENT; }
    public static int globalAttributeCap() { return GLOBAL_ATTRIBUTE_CAP; }
    /** TP total de referencia para el techo de presencia del aura. Ver AuraCeiling. */
    public static double auraReferenceTp() { return AURA_REFERENCE_TP; }
    public static double attributeBaseCost() { return ATTRIBUTE_BASE_COST; }
    public static double trainingPlRatioFloor() {return TRAINING_PL_RATIO_FLOOR;}
    public static double trainingPlRatioFull()     { return TRAINING_PL_RATIO_FULL; }
    public static double regenBodyExhaustion()     { return REGEN_BODY_EXHAUSTION; }
    public static double regenStaminaExhaustion()  { return REGEN_STAMINA_EXHAUSTION; }
    public static int    regenMinFoodLevel()       { return REGEN_MIN_FOOD; }
    /** Tope admin para /zparty maxsize — ver el comentario de PARTY_MAX_SIZE_CEILING_RAW. */
    public static int    partyMaxSizeCeiling()     { return PARTY_MAX_SIZE_CEILING; }
    /** Búfer de FE del generador de energía. Ver GeneratorEnergy. */
    public static int    energyGeneratorCapacity()   { return ENERGY_GENERATOR_CAPACITY; }
    /** Techo de FE por tick y por cara que el generador empuja a un vecino. */
    public static int    energyGeneratorMaxExtract() { return ENERGY_GENERATOR_MAX_EXTRACT; }

    public static double speedMultiplierCap()       { return SPEED_MULT_CAP; }
    public static double movementScaling()          { return MOVE_SCALING; }
    public static double runStaminaDrainPerSecond() { return RUN_STAMINA_DRAIN; }
    public static int dashCooldownTicks()           { return DASH_COOLDOWN; }
    public static double dashSpeed()                { return DASH_SPEED; }
    public static int stepCooldownTicks()           { return STEP_COOLDOWN; }
    public static double stepSpeed()                { return STEP_SPEED; }
    public static double stepEnergyPct()            { return STEP_ENERGY_PCT; }
    public static int stepInvisibleTicks()          { return STEP_INVISIBLE; }
    public static int stepIFrameTicks()             { return STEP_IFRAME; }
    public static double blockWeaponFactor()        { return BLOCK_WEAPON_FACTOR; }
    public static double blockShieldBonus()         { return BLOCK_SHIELD_BONUS; }
    public static double blockShieldCap()           { return BLOCK_SHIELD_CAP; }
    public static double blockShieldSlow()          { return BLOCK_SHIELD_SLOW; }
    public static int blockShieldDurability()       { return BLOCK_SHIELD_DURABILITY; }
    public static double blockShieldKiPct()         { return BLOCK_SHIELD_KI; }
    public static double guardMaxBase()             { return GUARD_MAX; }
    public static double guardDrainMult()           { return GUARD_DRAIN; }
    public static double guardConBonus()            { return GUARD_CON_BONUS; }
    public static double guardConFull()             { return GUARD_CON_FULL; }
    public static double guardDexBonus()            { return GUARD_DEX_BONUS; }
    public static double guardDexFull()             { return GUARD_DEX_FULL; }
    public static double guardRegenPerTick()        { return GUARD_REGEN; }
    public static int guardRegenDelayTicks()        { return GUARD_REGEN_DELAY; }
    public static int guardBreakStunTicks()         { return GUARD_BREAK_STUN; }
    public static boolean stunAffectsPlayers()      { return STUN_PLAYERS; }
    public static int stunMaxTicks()                { return STUN_MAX; }
    public static double stunDiminishingReturns()   { return STUN_DR; }
    public static int stunDrWindowTicks()           { return STUN_DR_WINDOW; }
    public static int stunImmunityTicks()           { return STUN_IMMUNITY; }
    public static int comboWindowTicks()            { return COMBO_WINDOW; }
    public static double comboDamagePerHit()        { return COMBO_DMG; }
    public static int comboMaxHits()                { return COMBO_MAX; }
    public static int heavyChargeTicksPerLevel()    { return HEAVY_TICKS; }
    public static int heavyMaxLevel()               { return HEAVY_LEVELS; }
    public static double heavyDamagePerLevel()      { return HEAVY_DMG; }
    public static double heavyKnockbackPerLevel()   { return HEAVY_KNOCK; }
    public static double heavyStaminaPerLevel()     { return HEAVY_STAMINA; }
    public static double diveMinHeight()            { return DIVE_MIN_HEIGHT; }
    public static double diveSpeed()                { return DIVE_SPEED; }
    public static int diveMaxTicks()                { return DIVE_MAX_TICKS; }
    public static double diveRadiusPerSpeed()       { return DIVE_RADIUS; }
    public static double diveMaxRadius()            { return DIVE_MAX_RADIUS; }
    public static double diveKnockback()            { return DIVE_KNOCK; }
    public static int evadeTicks()                  { return EVADE_TICKS; }
    public static int evadeChargeTicks()            { return EVADE_CHARGE_TICKS; }
    public static double grabRange()                { return GRAB_RANGE; }
    public static int grabHoldTicks()               { return GRAB_HOLD; }
    public static double grabThrowSpeed()           { return GRAB_THROW; }
    public static int grabLandingStunTicks()        { return GRAB_STUN; }
    public static int grabMobHoldTicks()            { return GRAB_MOB_HOLD; }
    public static int grabChecksToEscape()          { return GRAB_CHECKS; }
    public static double grabZoneSize()             { return GRAB_ZONE; }
    public static int grabNeedleTicks()             { return GRAB_NEEDLE; }
    public static int grabFailPenaltyTicks()        { return GRAB_FAIL; }
    public static int grabCheckGapTicks()           { return GRAB_GAP; }
    public static double evadeWaveRadius()          { return EVADE_WAVE_RADIUS; }
    public static double evadeWaveKnockback()       { return EVADE_WAVE_KNOCK; }
    public static int evadeWaveStunTicks()          { return EVADE_WAVE_STUN; }
    public static int comboFinisherEvery()          { return COMBO_FINISHER_EVERY; }
    public static double comboFinisherKnockback()   { return COMBO_FINISHER_KNOCK; }
    public static double bareHandAttackSpeed()      { return BARE_ATTACK_SPEED; }
    public static boolean kiaiOmnidirectional()     { return KIAI_OMNI; }
    public static double kiaiExtraTargetStamina()   { return KIAI_EXTRA_STAMINA; }
    public static int kiaiExtraTargetCap()          { return KIAI_EXTRA_CAP; }
    public static int kiaiGuardBreakTicks()         { return KIAI_GUARD_BREAK; }
    public static boolean parryEnabled()            { return PARRY_ENABLED; }
    public static int parryWindowTicks()            { return PARRY_WINDOW; }
    public static int parryCooldownTicks()          { return PARRY_COOLDOWN; }
    public static double parryKnockbackBlocks()     { return PARRY_KNOCKBACK; }
    public static double parryReflectFraction()     { return PARRY_REFLECT; }
    public static double parryNpcChance()           { return PARRY_NPC_CHANCE; }
    public static int parryStunTicks()              { return PARRY_STUN; }
    public static int parryCounterTicks()           { return PARRY_COUNTER_TICKS; }
    public static double parryCounterMult()         { return PARRY_COUNTER_MULT; }
    public static int parryPerfectTicks()           { return PARRY_PERFECT_TICKS; }
    public static double parryPerfectBonus()        { return PARRY_PERFECT_BONUS; }
    public static int clashContactTicks()           { return CLASH_CONTACT; }
    public static int clashResolveTicks()           { return CLASH_RESOLVE; }
    public static boolean clashEnabled()            { return CLASH_ENABLED; }
    public static int clashDurationTicks()          { return CLASH_DURATION; }
    public static int clashPeriodSlow()             { return CLASH_PERIOD_SLOW; }
    public static int clashPeriodMedium()           { return CLASH_PERIOD_MEDIUM; }
    public static int clashPeriodFast()             { return CLASH_PERIOD_FAST; }
    public static int clashFixedSpeed()             { return CLASH_FIXED_SPEED; }
    public static int clashPingGraceTicks()         { return CLASH_PING_GRACE; }
    public static double clashNoPressMultiplier()   { return CLASH_NO_PRESS; }
    public static double clashStatBonusCap()        { return CLASH_STAT_BONUS_CAP; }
    public static double clashTieMargin()           { return CLASH_TIE_MARGIN; }
    public static double clashOutnumberedBonus()    { return CLASH_OUTNUMBERED_BONUS; }
    public static int clashTeamMaxSize()            { return CLASH_TEAM_MAX; }
    public static double clashJoinRange()           { return CLASH_JOIN_RANGE; }
    public static boolean clashJoinResetsMinigame() { return CLASH_JOIN_RESETS; }
    public static double clashTieDamageFraction()   { return CLASH_TIE_DAMAGE; }
    public static double clashDamageSumFraction()   { return CLASH_SUM; }
    public static double clashReturnSpeedMultiplier() { return CLASH_RETURN_SPEED; }
    public static double clashReturnHoming()        { return CLASH_HOMING; }
    public static double clashReturnHomingRange()   { return CLASH_HOMING_RANGE; }
    public static double clashMinDamage()           { return CLASH_MIN_DAMAGE; }
    public static double clashMobSkill()            { return CLASH_MOB_SKILL; }
    public static double spiritBombGatherRadius()   { return SB_RADIUS; }
    public static int kiProjectileLifeTicks()       { return KI_LIFE; }
    public static double spiritBombGainPerPulse()   { return SB_GAIN; }
    public static double spiritBombBonusCap()       { return SB_CAP; }
    public static int spiritBombWindupTicks()       { return SB_WINDUP; }
    public static double spiritBombPartyGainPerPulse() { return SB_PARTY_GAIN; }
    public static double spiritBombPartyRadius()    { return SB_PARTY_RADIUS; }
    public static double flyMultiplierCap()         { return FLY_MULT_CAP; }
    public static double flyScaling()               { return FLY_SCALING; }
    public static double flyBaseSpeed()             { return FLY_BASE_SPEED; }
    public static double flyKiDrainPerTick()        { return FLY_KI_DRAIN; }
    public static double flyTakeoffImpulse()        { return FLY_TAKEOFF_IMPULSE; }
    public static double flyTakeoffKiCost()         { return FLY_TAKEOFF_KI_COST; }
    public static int flyRampUpTicks()              { return FLY_RAMP_UP; }
    public static int flyRampDownTicks()            { return FLY_RAMP_DOWN; }
    public static double turboDrainPctPerSec()      { return TURBO_DRAIN_PCT_PER_SEC; }
    public static int    inCombatTicks()             { return IN_COMBAT_TICKS; }
    public static double inCombatBodyRegenMult()     { return IN_COMBAT_BODY_REGEN_MULT; }
    public static double inCombatRacialRegenFloor()  { return IN_COMBAT_RACIAL_REGEN_FLOOR; }

    public static boolean mirrorHealth() { return MIRROR_HEALTH; }
    public static double absorptionWeight() { return ABSORPTION_WEIGHT; }
    public static boolean vanillaStatsFallback()   { return VANILLA_STATS_FALLBACK; }
    public static double  vanillaTpRewardFactor()  { return VANILLA_TP_REWARD_FACTOR; }
    public static double vanillaPassiveFactor() { return VANILLA_PASSIVE_FACTOR; }
    public static double vanillaHostileFactor() { return VANILLA_HOSTILE_FACTOR; }
    public static double vanillaBossFactor() { return VANILLA_BOSS_FACTOR; }
    public static double vanillaDamageRatio() { return VANILLA_DAMAGE_RATIO; }
    public static double mobProjectileFactor() { return MOB_PROJECTILE_FACTOR; }
    public static double explosionReferenceDamage() {return EXPLOSION_REFERENCE_DAMAGE;}

    public static double bodyScale()      { return BODY_SCALE; }
    public static double staminaScale()   { return STAMINA_SCALE; }
    public static double energyScale()    { return ENERGY_SCALE; }
    public static double baseRegenBody()    { return REGEN_BODY; }
    public static double baseRegenStamina() { return REGEN_STAMINA; }
    public static double baseRegenEnergy()  { return REGEN_ENERGY; }
    public static double foodKiPercentPerNutrition()      { return FOOD_KI_PCT; }
    public static double foodStaminaPercentPerNutrition() { return FOOD_STAMINA_PCT; }

    public static double minDamagePercent()        { return MIN_DAMAGE_PERCENT; }
    public static double immortalOverkillFraction() { return IMMORTAL_OVERKILL_FRACTION; }
    public static int techniqueMaxSlots()          { return TECHNIQUE_MAX_SLOTS; }
    public static int senseKiRange()               { return SENSE_KI_RANGE; }
    public static boolean lockOnEnabled()          { return LOCK_ON_ENABLED; }
    public static boolean lockOnWhileFlying()      { return LOCK_ON_WHILE_FLYING; }
    public static int lockOnRange()                { return LOCK_ON_RANGE; }
    public static double lockOnHomingTurn()        { return LOCK_ON_HOMING_TURN; }
    public static boolean lockOnDirectedDash()     { return LOCK_ON_DIRECTED_DASH_RAW.get(); }
    /** Techo global del radio de explosión de las técnicas, en bloques. 0 = sin techo. */
    public static double techniqueExplosionRadiusCap() { return TECHNIQUE_EXPLOSION_RADIUS_CAP; }
    public static double vanillaPowerLevelFactor() { return VANILLA_PL_FACTOR; }
    public static int scouterRange()               { return SCOUTER_RANGE; }

    public static double formMasteryPerMinute()      { return FORM_MASTERY_PER_MINUTE; }
    public static boolean ssjBlueRoseShareMastery()  { return SSJ_BLUE_ROSE_SHARE_MASTERY; }
    /** Peinados importados (códigos DMZ/DBC) permitidos en este mundo. */
    public static boolean allowCustomHair()          { return ALLOW_CUSTOM_HAIR; }
    public static double techMasteryPerUse()         { return TECH_MASTERY_PER_USE; }
    public static double masteryTechDamageBonus()    { return M_TECH_DMG; }
    public static double masteryTechCostReduction()  { return M_TECH_COST; }
    public static double masteryTechCastReduction()  { return M_TECH_CAST; }
    public static double majinStatBonus()            { return MAJIN_STAT_BONUS; }
    public static double legendaryStateStatBonus()   { return LEGENDARY_STATE_BONUS; }
    public static double arcosianGodPpCapacityMult() { return ARC_GOD_PP_CAP; }
    public static double arcosianGodPpDrainMult()    { return ARC_GOD_PP_DRAIN; }
    public static double arcosianGodPpStatMult()     { return ARC_GOD_PP_STAT; }
    public static double humanLegendDamageTakenExtra() { return HUMAN_LEGEND_DAMAGE_TAKEN; }
    public static double namekLegendLifesteal()      { return NAMEK_LEGEND_LIFESTEAL; }
    public static boolean namekLegendBlockRegen()    { return NAMEK_LEGEND_NO_REGEN; }
    public static double arcosianLegendPercent()     { return ARC_LEGEND_PERCENT; }
    public static double arcosianLegendTensionSeconds() { return ARC_LEGEND_TENSION; }
    public static double arcosianLegendTensionMasteryBonus() { return ARC_LEGEND_TENSION_MASTERY; }
    public static double arcosianLegendCooldownSeconds() { return ARC_LEGEND_COOLDOWN; }
    public static int majinLegendExtraCap()          { return MAJIN_LEGEND_EXTRA_CAP; }
    public static double majinLegendDurationMult()   { return MAJIN_LEGEND_DURATION; }
    public static double majinLegendStealRatio()     { return MAJIN_LEGEND_STEAL; }
    public static double majinLegendStealPerAbsorbMax() { return MAJIN_LEGEND_STEAL_MAX; }
    public static double majinLegendStealCap()       { return MAJIN_LEGEND_STEAL_CAP; }
    public static double majinLegendKiRefundPct()    { return MAJIN_LEGEND_KI_REFUND; }
    public static double humanWillBonusPerAlly()     { return HUMAN_WILL_ALLY; }
    public static double humanWillBonusPerHit()      { return HUMAN_WILL_HIT; }
    public static double humanWillMaxBonus()         { return HUMAN_WILL_MAX; }
    public static double humanWillAllyRadius()       { return HUMAN_WILL_RADIUS; }
    public static double namekLegendRegenPercent()   { return NAMEK_LEGEND_REGEN; }
    public static double namekLegendRegenKiPerPoint() { return NAMEK_LEGEND_REGEN_KI; }
    public static double namekLegendPassiveCostMult() { return NAMEK_LEGEND_PASSIVE_COST; }
    public static boolean majinLegendaryExclusive()  { return MAJIN_LEGENDARY_EXCLUSIVE; }
    public static double namekGiantStatBonus()       { return NAMEK_GIANT_STAT; }
    public static double potentialUnlockTpMult()     { return PU_TP_MULT; }
    public static int    potentialUnlockAlignmentReq() { return PU_ALIGNMENT_REQ; }
    public static double potentialUnlockMaxStatPercent() { return PU_MAX_STAT; }
    public static double godKiTrialFraction() { return GODKI_TRIAL_FRACTION; }
    public static int godKiRitualParticipants() { return GODKI_RITUAL_PARTICIPANTS; }
    public static boolean godKiRitualSameRace() { return GODKI_RITUAL_SAME_RACE; }
    public static int godKiRitualMinAlignment() { return GODKI_RITUAL_MIN_ALIGNMENT; }
    public static double godKiRitualRadius() { return GODKI_RITUAL_RADIUS; }
    public static int godKiRitualSeconds() { return GODKI_RITUAL_SECONDS; }
    public static double beastOverdriveStatBonus() { return BEAST_OD_STAT; }
    public static double legendaryOverdriveStatBonus() { return LEGENDARY_OD_STAT; }
    public static double legendaryGrowthPercent() { return LEGENDARY_GROWTH; }
    public static double legendaryCeilingPercent() { return LEGENDARY_CEILING; }
    public static double legendaryResonanceMult() { return LEGENDARY_RESONANCE; }
    public static double beastUnlockMysticMastery() { return BEAST_MASTERY_REQ; }
    public static double beastFuryRadius() { return BEAST_FURY_RADIUS; }
    public static double arcPpGain() { return ARC_PP_GAIN; }
    public static double arcPpDrain() { return ARC_PP_DRAIN; }
    public static double arcPpCombatDealt() { return ARC_PP_COMBAT_DEALT; }
    public static double arcPpCombatTaken() { return ARC_PP_COMBAT_TAKEN; }
    public static double arcosianGodPpRefillPerHit() { return ARC_GOD_REFILL; }
    public static double arcPpStat() { return ARC_PP_STAT; }
    public static boolean majinAbsorbEnabled() { return MAJIN_ABSORB_ENABLED; }
    public static double majinAbsorbDominantPlRatio() { return MAJIN_ABSORB_DOMINANT_PL_RATIO; }
    public static double majinAbsorbMaxHealthPct() { return MAJIN_ABSORB_MAX_HEALTH_PCT; }
    public static double majinAbsorbMaxPlRatio() { return MAJIN_ABSORB_MAX_PL_RATIO; }
    public static double majinAbsorbKiCostPct() { return MAJIN_ABSORB_KI_COST_PCT; }
    public static boolean majinAbsorbPvp() { return MAJIN_ABSORB_PVP; }
    public static int majinMassCap() { return MAJIN_MASS_CAP; }
    public static double majinMassSeconds() { return MAJIN_MASS_SECONDS; }
    public static double majinMassStatPerStack() { return MAJIN_MASS_STAT_PER_STACK; }
    public static double majinMassPermanentPerAbsorb() { return MAJIN_MASS_PERM_PER_ABSORB; }
    public static double majinMassPermanentCap() { return MAJIN_MASS_PERM_CAP; }
    public static int majinVorazExtraCap() { return MAJIN_VORAZ_EXTRA_CAP; }
    public static double majinSaciadoDurationMult() { return MAJIN_SACIADO_DURATION_MULT; }
    public static double majinPrimitivoBonusMult() { return MAJIN_PRIMITIVO_BONUS_MULT; }
    public static double majinPrimitivoDurationMult() { return MAJIN_PRIMITIVO_DURATION_MULT; }
    public static double formDrainScale() { return FORM_DRAIN_SCALE; }
    public static double formDrainSpiMin() { return FORM_DRAIN_SPI_MIN; }
    public static double formDrainSpiMax() { return FORM_DRAIN_SPI_MAX; }
    public static double formDrainFloor() { return FORM_DRAIN_FLOOR; }
    public static double uiHeatRate() { return UI_HEAT_RATE; }
    public static double uiHeatPerDodge() { return UI_HEAT_PER_DODGE; }
    public static double uiHeatSlow() { return UI_HEAT_SLOW; }
    public static double uiHeatCool() { return UI_HEAT_COOL; }
    public static double uiDodgeCost() { return UI_DODGE_COST; }
    public static double uiDodgeReduce() { return UI_DODGE_REDUCE; }
    public static double uiDodgeChance() { return UI_DODGE_CHANCE; }
    public static double uiDodgeChanceLevel() { return UI_DODGE_CHANCE_LEVEL; }
    public static double uiDodgeChanceMax() { return UI_DODGE_CHANCE_MAX; }
    public static double uiHeatRecover() { return UI_HEAT_RECOVER; }
    public static double uiMasteryDodge() { return UI_MASTERY_DODGE; }
    public static double uiMasteryHeat() { return UI_MASTERY_HEAT; }
    public static double ueMasteryBonus() { return UE_MASTERY_BONUS; }
    public static double ueCharge() { return UE_CHARGE; }
    public static double ueDecay() { return UE_DECAY; }
    public static double ueStatBonusLv5() { return UE_STAT_LV5; }
    public static double ueStatBonusLv10() { return UE_STAT_LV10; }
    public static double uePenetrationMax() { return UE_PEN; }

    public static double kiCostPerPower()    { return KI_COST_PER_POWER; }
    public static double meleeStaminaPerHit() { return MELEE_STAMINA_PER_HIT; }
    public static double explosionSacrificeConversion() {return EXPLOSION_SACRIFICE_CONVERSION;}

    /** El arma como multiplicador del golpe, no como suma. Ver KiInfusion. */
    public static double weaponScale()       { return WEAPON_SCALE; }
    /** Cuánto del bonus de daño melee de vanilla (Filo/Aspereza/mace por caída/pociones/otros
     *  mods) se traduce como multiplicador sobre el golpe STR. Ver KiInfusion.enchantMultiplier. */
    public static double meleeEnchantScale()   { return MELEE_ENCHANT_SCALE; }
    public static double meleeEnchantMultCap() { return MELEE_ENCHANT_MULT_CAP; }
    public static double vanillaArmorWeight()  { return VANILLA_ARMOR_WEIGHT; }
    public static double projectileBaseDamage(){ return PROJECTILE_BASE_DAMAGE; }
    public static double projectileScale()     { return PROJECTILE_SCALE; }
    public static double projectileMultCap()   { return PROJECTILE_MULT_CAP; }
    /** Ki por punto de daño EXTRA de Ki Infuse / Ki Fist. */
    public static double kiPerBonusDamage()  { return KI_PER_BONUS_DAMAGE; }
    public static double pvpDamageScale()    { return PVP_DAMAGE_SCALE; }
    public static double kiDefenseFactor()   { return KI_DEFENSE_FACTOR; }
    public static double kiDamageScale()     { return KI_DAMAGE_SCALE; }

    public static double blackFlashChance() {return BF_CHANCE;}
    public static double blackFlashMaxChance() {return BF_MAX_CHANCE;}
    public static double blackFlashMultiplier() {return BF_MULTIPLIER;}
    public static double blackFlashChargeExponent() {return BF_CHARGE_EXPONENT;}
    public static double blackFlashLuckFactor() {return BF_LUCK_FACTOR;}
    public static double blackFlashStatFactor() { return BF_STAT_FACTOR; }

    /** attack_speed objetivo en modo combate. Por debajo de 4.0 o vanilla esconde el indicador. */
    public static double combatAttackSpeed() { return COMBAT_ATTACK_SPEED; }

    /** Cuánto más lento avanza el tramo 100%->200% respecto al primer 100%. */
    public static double overchargeTimeMult() { return OVERCHARGE_TIME_MULT; }
    /** Recargo de coste aplicado SOLO a la porción sobrecargada. */
    public static double overchargeCostMult() { return OVERCHARGE_COST_MULT; }

    public static double trainingDamageTpFactor()        { return TRAIN_DMG_TP; }
    /** PL de la victima x esto = recompensa TP "auto" al matarla (zenkai_entities y fallback vanilla). */
    public static double tpPerPl()                       { return TP_PER_PL; }
    public static double trainingAirTpFactor()           { return TRAIN_AIR_TP; }
    public static double trainingAirStaminaCostPct()     { return TRAIN_AIR_COST; }
    public static int    trainingAirMinTicks()           { return TRAIN_AIR_TICKS; }
    public static double trainingFatigueHalfLife()       { return TRAIN_HALF_LIFE; }
    public static double trainingFatigueDecayPerMinute() { return TRAIN_DECAY; }
    public static double trainingHtcMultiplier()         { return TRAIN_HTC_MULT; }
    public static double trainingMinEfficiency()         { return TRAIN_MIN_EFF; }

    public static double weightCapacityDivisor()   { return WEIGHT_CAP_DIV; }
    public static double weightCapacityExponent()  { return WEIGHT_CAP_EXP; }
    public static double weightStatPenalty()       { return WEIGHT_STAT_PEN; }
    public static double weightMovePenalty()       { return WEIGHT_MOVE_PEN; }
    public static double weightJumpPenalty()       { return WEIGHT_JUMP_PEN; }
    public static double weightGravityJumpFactor() { return WEIGHT_GRAVITY_JUMP_FACTOR; }
    public static double weightTpBonus()           { return WEIGHT_TP_BONUS; }
    public static double weightOverloadThreshold() { return WEIGHT_OVER_THRESH; }
    public static double weightOverloadMoveFactor(){ return WEIGHT_OVER_MOVE; }
    public static double weightKaiosamaAmbientTons(){ return WEIGHT_KAIOSAMA_TONS; }
    public static double weightKaiosamaGravityMultiplier(){ return WEIGHT_KAIOSAMA_GRAVITY_MULT; }
    public static double weightHtcAmbientTons(){ return WEIGHT_HTC_TONS; }
    public static double weightHtcGravityMultiplier(){ return WEIGHT_HTC_GRAVITY_MULT; }

    // === LÓGICA FINAL: aplica overrides con las nuevas opciones ===
    public static ItemStack resolveWishStack(ItemStack chosen) {
        if (chosen == null || chosen.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());
        if (isBanned(chosen)) return ItemStack.EMPTY;
        if (!isAllowedByWhitelist(id)) return ItemStack.EMPTY;

        int override = STACK_OVERRIDES.getOrDefault(id, -1);
        int desired = (override > 0) ? override : chosen.getMaxStackSize();

        // clamp final según config
        int hardCap = Math.max(1, GLOBAL_HARD_CAP.get());
        if (ALLOW_ABOVE_MAX.get()) {
            desired = Math.min(desired, hardCap);
        } else {
            desired = Math.min(desired, Math.min(hardCap, chosen.getMaxStackSize()));
        }

        ItemStack copy = chosen.copy(); // copia NBT
        copy.setCount(desired);
        return copy;
    }

}
