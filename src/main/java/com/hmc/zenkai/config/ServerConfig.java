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
    public enum WishType { STACK, REVIVE_PLAYER, ENCHANT_VILLAGER, IMMORTAL, TRAINING_POINTS }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Toggles por deseo ──────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLE_STACK;
    public static final ModConfigSpec.BooleanValue ENABLE_REVIVE_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANT_VILLAGER;
    public static final ModConfigSpec.BooleanValue ENABLE_IMMORTAL;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAINING_POINTS;

    // ── Stack wish ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACK_OVERRIDES_RAW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS_RAW;
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
        BUILDER.pop();

        BUILDER.push("stack_wish");
        STACK_OVERRIDES_RAW = BUILDER
                .comment("Overrides the amount of the item given with this format: 'namespace:item=count'. Ex: minecraft:diamond=32")
                .defineListAllowEmpty("stack_overrides",
                        List.of("minecraft:ender_pearl=32"),
                        obj -> true);
        BANNED_ITEMS_RAW = BUILDER
                .comment("List of items banned from wishes. Ex: minecraft:shulker_box")
                .defineListAllowEmpty("banned_items",
                        List.of("minecraft:shulker_box"),
                        obj -> true);
        WHITELIST_ITEMS_RAW = BUILDER
                .comment("Whitelist of items allowed in the stack wish.",
                            "If EMPTY, any item is allowed except the blacklist.",
                            "If it has items, ONLY those are allowed (the blacklist still takes priority).")
                .defineListAllowEmpty("whitelist_items",
                        List.of(),
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
                    .defineInRange("block.shield_ki_cost_pct", 0.01D, 0.0D, 1.0D);

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
                    .defineInRange("clash.stat_bonus_cap", 100.0D, 0.0D, 500.0D);

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

    private static final ModConfigSpec.DoubleValue VANILLA_PL_FACTOR_RAW =
            BUILDER.comment("PL of vanilla mobs / raceless players = max_health * factor")
                    .defineInRange("power_level.vanilla_factor", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue SCOUTER_RANGE_RAW =
            BUILDER.comment("Scouter: crosshair scan range in blocks")
                    .defineInRange("scouter.range", 64, 8, 256);

    private static final ModConfigSpec.DoubleValue KI_COST_PER_POWER_RAW =
            BUILDER.comment("Ki cost per point of ki power (WIL). Higher = ki drains faster, SPI matters more.")
                    .defineInRange("cost.ki_per_power", 0.70D, 0.0D, 10.0D);

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
                    .defineInRange("cost.ki_per_bonus_damage", 0.50D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue COMBAT_ATTACK_SPEED_RAW =
            BUILDER.comment("Attack speed while in combat mode. Vanilla base is 4.0 (5-tick recharge); anything BELOW 4.0 enables the vanilla swing cooldown and the crosshair indicator. 1.6 = sword-like, 12.5 ticks.")
                    .defineInRange("combat.attack_speed", 1.6D, 0.1D, 4.0D);

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

    private static final ModConfigSpec.DoubleValue MAJIN_STAT_BONUS_RAW =
            BUILDER.comment("Stat bonus fraction while under the Majin effect (0.10 = +10%)")
                    .defineInRange("majin.effect_stat_bonus", 0.10D, 0.0D, 5.0D);

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
            BUILDER.comment("Mystic (Potential Unlock): ABSOLUTE ceiling of the stat bonus the form can give at 100% mastery (added, 16.0 = +1600%). Mystic scales with the best form you own, which let it overtake Beast (24-32); this caps it below Beast's untrained value so Mystic -> Beast stays an upgrade. Mastery still spans the whole curve (the cap rescales it, it does not just clip it). Races with low-bonus chains are not affected.")
                    .defineInRange("skills.potential_unlock_max_stat_percent", 16.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue PU_ALIGNMENT_REQ_RAW =
            BUILDER.comment("Minimum alignment (-100..100) required to BUY Potential Unlock. Not checked afterwards.")
                    .defineInRange("skills.potential_unlock_alignment_req", 50, -100, 100);

    // =====================================================================
    // SPEC — Power Points arcosianos, Ultra Instinct (calor), Ultra Ego (daño)
    // =====================================================================

    private static final ModConfigSpec.DoubleValue ARC_PP_GAIN_RAW =
            BUILDER.comment("Arcosian Power Points gained per second while in BASE form and power below 50%.")
                    .defineInRange("arcosian.pp_gain_per_second", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_DRAIN_RAW =
            BUILDER.comment("Percent of the Power Point tank spent per second while they are being applied (Final Form or higher at 100%+ power).")
                    .defineInRange("arcosian.pp_drain_percent_per_second", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SCALE_RAW =
            BUILDER.comment("Form ki drain per SECOND = ki_drain (percent, from the form datapack) / 100 * (STR + WIL, race-weighted) * this scale * SPI multiplier. Higher = every form costs more ki to hold, at any power level.")
                    .defineInRange("forms.drain_scale", 4.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SPI_MIN_RAW =
            BUILDER.comment("Lower clamp of the SPI drain multiplier sqrt(spi_req / SPI). Reached when SPI is far above the form's requirement; 1.0 disables the discount.")
                    .defineInRange("forms.drain_spi_min", 1.0D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_SPI_MAX_RAW =
            BUILDER.comment("Upper clamp of the SPI drain multiplier sqrt(spi_req / SPI). Reached when SPI is far below the form's requirement; 1.0 disables the penalty.")
                    .defineInRange("forms.drain_spi_max", 1.5D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FORM_DRAIN_FLOOR_RAW =
            BUILDER.comment("Minimum form ki drain, as a fraction of the ki pool (energyMax) multiplied by the form's ki_drain percent: floor/s = ki_drain / 100 * this * energyMax. Stops a huge pool from making a form free. 0 disables the floor.")
                    .defineInRange("forms.drain_floor", 0.25D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue ARC_PP_STAT_RAW =
            BUILDER.comment("Stat multiplier bonus (added, 2.0 = +200%) granted when the Power Point tank is full and being applied; scales linearly with the tank.")
                    .defineInRange("arcosian.pp_stat_bonus_max", 2.0D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue UI_HEAT_RATE_RAW =
            BUILDER.comment("Ultra Instinct heat bar: percent filled per second while in an Ultra Instinct form. At 100% dodging stops (the form is kept) until the bar cools down.")
                    .defineInRange("ultra_instinct.heat_percent_per_second", 2.0D, 0.0D, 100.0D);

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
                    .defineInRange("ultra_ego.charge_decay_per_second", 1.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue UE_STAT_LV5_RAW =
            BUILDER.comment("Ultra Ego: stat bonus (added, 0.2 = +20%) granted by a FULL damage bar at skill level 5 (the level the transformation unlocks). The bar only acts from level 5.")
                    .defineInRange("ultra_ego.max_stat_bonus_level5", 0.2D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue UE_STAT_LV10_RAW =
            BUILDER.comment("Ultra Ego: stat bonus (added) granted by a FULL damage bar at skill level 10. Levels 6-9 interpolate linearly between the level 5 and level 10 values.")
                    .defineInRange("ultra_ego.max_stat_bonus_level10", 0.6D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue UE_PEN_RAW =
            BUILDER.comment("Ultra Ego: fraction of the target's defense ignored by PHYSICAL techniques at skill level 10 (0.2 = 20%). Scales linearly from level 1 (1/10 of this value). Applies while the Ultra Ego technique is on or the Ultra Ego form is worn.")
                    .defineInRange("ultra_ego.max_armor_penetration", 0.2D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue BEAST_OD_STAT_RAW =
            BUILDER.comment("Extra stat multiplier bonus (added, 0.5 = +50%) while wearing Beast with power above 100% (overdrive). Stacks with Beast's cheaper overdrive drain.")
                    .defineInRange("beast.overdrive_stat_bonus", 0.5D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue BEAST_MASTERY_REQ_RAW =
            BUILDER.comment("Beast unlock, requirement 1: minimum Mystic mastery (0-100, in percent) the player must have when the fury trigger happens. Requirement 2 is the trigger itself: a tamed pet or a party member dying near the player (see beast.fury_radius).")
                    .defineInRange("beast.unlock_mystic_mastery_percent", 50.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue BEAST_FURY_RADIUS_RAW =
            BUILDER.comment("Beast unlock: radius in blocks within which the death of a tamed pet (owned by the player) or of a party member counts as the fury trigger.")
                    .defineInRange("beast.fury_radius", 32.0D, 1.0D, 256.0D);

    private static final ModConfigSpec.IntValue ARC_GODKI_LEVELS_RAW =
            BUILDER.comment("God Ki levels an Arcosian can learn. The race has no divine forms, so these levels unlock no transformation yet: they are the gate for the divine-ki mechanics planned for the race. Each level costs the same TP as the same level of the Saiyan divine chain. 0 hides God Ki from Arcosians entirely.")
                    .defineInRange("arcosian.god_ki_levels", 1, 0, 10);

    private static final ModConfigSpec.BooleanValue ALLOW_CUSTOM_HAIR_RAW =
            BUILDER.comment("Players can import hair codes (DragonMineZ codes and Dragon Block C / HairSalon strings) in the Hair tab of the appearance screen, keep them in a personal library and wear them; everyone sees them, no files needed. When false the import UI is hidden, the server rejects new imports and anyone wearing an imported hair falls back to the default style.")
                    .define("customization.allow_custom_hair", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── API de toggles ──────────────────────────────────────────────────────────
    public static boolean isEnabled(WishType w) {
        return switch (w) {
            case STACK            -> ENABLE_STACK.get();
            case REVIVE_PLAYER    -> ENABLE_REVIVE_PLAYER.get();
            case ENCHANT_VILLAGER -> ENABLE_ENCHANT_VILLAGER.get();
            case IMMORTAL         -> ENABLE_IMMORTAL.get();
            case TRAINING_POINTS  -> ENABLE_TRAINING_POINTS.get();
        };
    }

    // ── Precios villager (ahora desde config) ────────────────────────────────────
    public static int villagerBookBasePrice()     { return VILLAGER_BOOK_BASE_PRICE.get(); }
    public static int villagerBookPricePerLevel()  { return VILLAGER_BOOK_PRICE_PER_LEVEL.get(); }

    private static volatile Map<ResourceLocation, Integer> STACK_OVERRIDES = Collections.emptyMap();
    private static volatile Set<ResourceLocation> BANNED_ITEMS = Collections.emptySet();
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
    private static volatile double BLOCK_SHIELD_KI = 0.01D;
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
    private static volatile double KI_COST_PER_POWER = 0.70D;
    private static volatile double MELEE_STAMINA_PER_HIT = 0.10D;
    private static volatile double WEAPON_SCALE = 0.04D;
    private static volatile double MELEE_ENCHANT_SCALE = 1.0D;
    private static volatile double MELEE_ENCHANT_MULT_CAP = 5.0D;
    private static volatile double VANILLA_ARMOR_WEIGHT = 0.50D;
    private static volatile double PROJECTILE_BASE_DAMAGE = 6.0D;
    private static volatile double PROJECTILE_SCALE = 1.0D;
    private static volatile double PROJECTILE_MULT_CAP = 3.0D;
    private static volatile double KI_PER_BONUS_DAMAGE = 0.50D;
    private static volatile double COMBAT_ATTACK_SPEED = 1.6D;
    private static volatile double OVERCHARGE_TIME_MULT = 2.5D;
    private static volatile double OVERCHARGE_COST_MULT = 1.5D;
    private static volatile double MAJIN_STAT_BONUS = 0.10D;
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
    private static volatile double PU_MAX_STAT = 16.0D;
    private static volatile int ARC_GODKI_LEVELS = 1;
    private static volatile double BEAST_OD_STAT = 0.5D;
    private static volatile double BEAST_MASTERY_REQ = 50.0D;
    private static volatile double BEAST_FURY_RADIUS = 32.0D;
    private static volatile double ARC_PP_GAIN = 1.0D;
    private static volatile double ARC_PP_DRAIN = 2.0D;
    private static volatile double ARC_PP_STAT = 2.0D;
    private static volatile double FORM_DRAIN_SCALE = 4.0D;
    private static volatile double FORM_DRAIN_SPI_MIN = 1.0D;
    private static volatile double FORM_DRAIN_SPI_MAX = 1.5D;
    private static volatile double FORM_DRAIN_FLOOR = 0.25D;
    private static volatile double UI_HEAT_RATE = 2.0D;
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
    private static volatile double UE_DECAY = 1.5D;
    private static volatile double UE_STAT_LV5 = 0.2D;
    private static volatile double UE_STAT_LV10 = 0.6D;
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
        ARC_GODKI_LEVELS = ARC_GODKI_LEVELS_RAW.get();
        BEAST_OD_STAT = BEAST_OD_STAT_RAW.get();
        BEAST_MASTERY_REQ = BEAST_MASTERY_REQ_RAW.get();
        BEAST_FURY_RADIUS = BEAST_FURY_RADIUS_RAW.get();
        ARC_PP_GAIN = ARC_PP_GAIN_RAW.get();
        ARC_PP_DRAIN = ARC_PP_DRAIN_RAW.get();
        ARC_PP_STAT = ARC_PP_STAT_RAW.get();
        FORM_DRAIN_SCALE = FORM_DRAIN_SCALE_RAW.get();
        FORM_DRAIN_SPI_MIN = FORM_DRAIN_SPI_MIN_RAW.get();
        FORM_DRAIN_SPI_MAX = FORM_DRAIN_SPI_MAX_RAW.get();
        FORM_DRAIN_FLOOR = FORM_DRAIN_FLOOR_RAW.get();
        UI_HEAT_RATE = UI_HEAT_RATE_RAW.get();
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

    public static boolean isBanned(ResourceLocation id) {
        return BANNED_ITEMS.contains(id);
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
    public static double potentialUnlockTpMult()     { return PU_TP_MULT; }
    public static int    potentialUnlockAlignmentReq() { return PU_ALIGNMENT_REQ; }
    public static double potentialUnlockMaxStatPercent() { return PU_MAX_STAT; }
    public static int arcGodKiLevels() { return ARC_GODKI_LEVELS; }
    public static double beastOverdriveStatBonus() { return BEAST_OD_STAT; }
    public static double beastUnlockMysticMastery() { return BEAST_MASTERY_REQ; }
    public static double beastFuryRadius() { return BEAST_FURY_RADIUS; }
    public static double arcPpGain() { return ARC_PP_GAIN; }
    public static double arcPpDrain() { return ARC_PP_DRAIN; }
    public static double arcPpStat() { return ARC_PP_STAT; }
    public static double formDrainScale() { return FORM_DRAIN_SCALE; }
    public static double formDrainSpiMin() { return FORM_DRAIN_SPI_MIN; }
    public static double formDrainSpiMax() { return FORM_DRAIN_SPI_MAX; }
    public static double formDrainFloor() { return FORM_DRAIN_FLOOR; }
    public static double uiHeatRate() { return UI_HEAT_RATE; }
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
        if (isBanned(id)) return ItemStack.EMPTY;
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
