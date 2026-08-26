package com.hmc.zenkai.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Config común del mod. Patrón: cada opción tiene su *_RAW (spec) y una copia volátil que se
 * refresca en onConfigLoad; el resto del código lee SOLO los getters, que son thread-safe.
 * IMPORTANTE: los valores iniciales de las copias volátiles deben coincidir con el default de
 * su *_RAW. Se usan durante el arranque, antes de que la config cargue, y si difieren el mod
 * trabaja con números equivocados en esa ventana.
 * Orden de atributos en las listas de raza/estilo: [STR, DEX, CON, WIL, SPI, MND] para las bases
 * y [STR, CON, DEX, WIL, SPI, MND] para los multiplicadores (heredado de recalcAll).
 */
public final class CommonConfig {
    private CommonConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // =====================================================================
    // SPEC — TP y topes
    // =====================================================================

    private static final ModConfigSpec.DoubleValue TP_COEFFICIENT_RAW =
            BUILDER.comment("m de la recta de coste: cost(n) = attribute_base_cost + coef * n. A 1.0 el punto n cuesta 1+n TP, o sea el coste sube de uno en uno. Bajarlo aplana la curva; subirlo la endurece de golpe, porque es lineal y no exponencial.")
                    .defineInRange("stats.tp_coefficient", 1.0D, 0.0D, 100D);

    private static final ModConfigSpec.IntValue GLOBAL_ATTRIBUTE_CAP_RAW =
            BUILDER.comment("Max per attribute. 5 counted attrs x 200000 = PL cap 1,000,000")
                    .defineInRange("caps.global_attribute", 200000, 1, 1000000);

    private static final ModConfigSpec.DoubleValue AURA_REFERENCE_TP_RAW =
            BUILDER.comment("Aura presence scale: total TP an endgame player is expected to have invested. The ONLY reference number AuraCeiling needs to normalize the log10 presence scale - it does NOT change when a form/Kaioken step is added, the ceiling recomputes from the whole registry on its own. Raise it if TP income outpaces this and auras start looking maxed out too early; lower it if the top of the scale never gets reached.")
                    .defineInRange("aura.reference_tp", 5_000_000D, 1.0D, 1.0E12D);

    private static final ModConfigSpec.DoubleValue ATTRIBUTE_BASE_COST_RAW =
            BUILDER.comment("b de la recta de coste: lo que cuesta el primer punto, con cero invertidos.")
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
            BUILDER.comment("Ticks que dura el estado 'en combate' desde el ultimo dano dado o recibido. 160 = 8 s.")
                    .defineInRange("combat.in_combat_ticks", 160, 0, 12000);

    private static final ModConfigSpec.DoubleValue IN_COMBAT_BODY_REGEN_MULT_RAW =
            BUILDER.comment("Multiplicador de la regeneración de BODY mientras estas en combate. 0.5 = la mitad, 0.0 = no te curas peleando, 1.0 = desactiva la penalizacion. Solo body: la estamina y el ki son con lo que se pelea.")
                    .defineInRange("combat.in_combat_body_regen_mult", 0.5D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue IN_COMBAT_RACIAL_REGEN_FLOOR_RAW =
            BUILDER.comment("Suelo del multiplicador anterior para las razas cuya regeneración ES su pasiva (namekiano, majin). Aunque el mult general este a 0, ellos siguen curandose a este ritmo: si no, dejarian de ser esa raza en combate, que es justo donde su identidad tiene que notarse.")
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

    private static final ModConfigSpec.DoubleValue TECH_MASTERY_PER_USE_RAW =
            BUILDER.comment("Technique mastery gained per use (percent points, 0-100 scale)")
                    .defineInRange("mastery.technique_per_use", 0.2D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_FORM_DRAIN_RED_RAW =
            BUILDER.comment("At 100% form mastery: fraction of form ki drain removed (0.50 = -50%)")
                    .defineInRange("mastery.form_drain_reduction", 0.50D, 0.0D, 1.0D);

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
                            "Recalibrado 2026-08-20 (0.02 -> 0.10, x5): la simulacion de .claude/pendiente/economia-tp.md",
                            "mostro que con el valor viejo, incluso ELIMINANDO la fatiga por completo, un jugador tardaba ~112h",
                            "en llegar a los 5,000,000 TP de referencia peleando a ritmo moderado (15s/kill) -- muy por",
                            "encima de las 20-40h que el usuario fijo como objetivo. Va de la mano con entity.tp_per_pl",
                            "y training.fatigue_decay_per_minute; no tocar uno sin mirar los otros dos.")
                    .defineInRange("training.damage_tp_factor", 0.10D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue TP_PER_PL_RAW =
            BUILDER.comment("Recompensa TP 'auto' al matar CUALQUIER entidad con stats Zenkai (zenkai_entities con",
                            "rewards.tp=\"auto\", y el fallback vanilla) = PL de la victima x este factor. Antes vivia",
                            "hardcodeado y DUPLICADO en EntityStats.TP_PER_PL y EntityDeathRewardHandler.VANILLA_TP_PER_PL",
                            "(0.05 en los dos, sin pasar por config); se migro aqui para poder recalibrarlo sin recompilar",
                            "y para dejar de tener el mismo numero mantenido a mano en dos sitios.",
                            "Recalibrado 2026-08-20 (0.05 -> 0.25, x5) junto con training.damage_tp_factor -- ver el",
                            "comentario de esa entrada para el porque.")
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
                            "Recalibrado 2026-08-20 (0.01 -> 0.2, x20): es la palanca DOMINANTE del sistema. En granjeo",
                            "sostenido, TP/hora = 60 x este valor x tu PL -- el ritmo de combate (rapido o lento) se",
                            "cancela solo y NO afecta ese numero (comprobado por simulacion: de 2s a 40s por kill daba",
                            "el mismo resultado con el 0.01 viejo). Subir damage_tp_factor/entity.tp_per_pl sin tocar",
                            "esto no sirve de mucho: el reward extra se cancela con la fatiga extra que genera.",
                            "Sigue siendo una ESTIMACION del modelo (asume ~15s por kill 'de nivel'); hace falta",
                            "playtesting real para afinarlo -- ver .claude/pendiente/economia-tp.md.")
                    .defineInRange("training.fatigue_decay_per_minute", 0.20D, 0.0D, 10.0D);

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

    private static final ModConfigSpec.DoubleValue WEIGHT_TP_BONUS_RAW =
            BUILDER.comment("Weights: extra TP at full load (1.5 = x2.5 TP)")
                    .defineInRange("training.weight_tp_bonus", 1.5D, 0.0D, 20.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_OVER_THRESH_RAW =
            BUILDER.comment("Weights: load ratio above which the player is overloaded")
                    .defineInRange("training.weight_overload_threshold", 1.2D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue WEIGHT_OVER_MOVE_RAW =
            BUILDER.comment("Weights: movement multiplier while overloaded (drag)")
                    .defineInRange("training.weight_overload_move_factor", 0.15D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue PU_TP_MULT_RAW =
            BUILDER.comment("TP multiplier while Potential Unlock is ACTIVE. You are using your potential, not training it.")
                    .defineInRange("training.potential_unlock_tp_mult", 0.50D, 0.0D, 1.0D);

   private static final ModConfigSpec.IntValue PU_ALIGNMENT_REQ_RAW =
            BUILDER.comment("Minimum alignment (-100..100) required to BUY Potential Unlock. Not checked afterwards.")
                    .defineInRange("skills.potential_unlock_alignment_req", 50, -100, 100);

    private static final ModConfigSpec.ConfigValue<String> TECH_DUMP_DIR_RAW =
            BUILDER.comment("DEV ONLY. Extra folder where /zenkai tech dump also writes the technique JSONs,",
                            "on top of the world datapack. Point it at src/main/resources/data/zenkai/zenkai_techniques",
                            "to keep in-game tuning. Empty = disabled.")
                    .define("dev.technique_dump_dir", "");


    public static final ModConfigSpec SPEC = BUILDER.build();

    // =====================================================================
    // CACHÉ VOLÁTIL — cada valor inicial DEBE igualar el default de su *_RAW
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
    private static volatile double FLY_MULT_CAP = 15.0D;
    private static volatile double FLY_SCALING = 0.25D;
    private static volatile double FLY_BASE_SPEED = 0.05D;
    private static volatile double FLY_KI_DRAIN = 0.15D;
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

    private static volatile boolean MIRROR_HEALTH = true;
    private static volatile double ABSORPTION_WEIGHT = 1.0D;

    private static volatile double FORM_MASTERY_PER_MINUTE = 0.5D;
    private static volatile double TECH_MASTERY_PER_USE = 0.2D;
    private static volatile double M_FORM_DRAIN = 0.50D,
            M_TECH_DMG = 0.25D, M_TECH_COST = 0.30D, M_TECH_CAST = 0.30D;
    private static volatile double KI_COST_PER_POWER = 0.70D;
    private static volatile double MELEE_STAMINA_PER_HIT = 0.10D;
    private static volatile double WEAPON_SCALE = 0.04D;
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
            TRAIN_AIR_COST = 0.04D, TRAIN_HALF_LIFE = 0.10D, TRAIN_DECAY = 0.20D,
            TRAIN_HTC_MULT = 2.0D, TRAIN_MIN_EFF = 0.05D;
    private static volatile double TP_PER_PL = 0.25D;
    private static volatile int TRAIN_AIR_TICKS = 10;
    private static volatile double WEIGHT_CAP_DIV = 3.4D, WEIGHT_CAP_EXP = 0.6D,
            WEIGHT_STAT_PEN = 0.25D, WEIGHT_MOVE_PEN = 0.60D, WEIGHT_JUMP_PEN = 0.40D,
            WEIGHT_TP_BONUS = 1.5D, WEIGHT_OVER_THRESH = 1.2D, WEIGHT_OVER_MOVE = 0.15D;
    private static volatile double PU_TP_MULT = 0.50D;
    private static volatile int    PU_ALIGNMENT_REQ = 50;
    private static volatile String TECH_DUMP_DIR = "";

    // =====================================================================
    // CARGA
    // =====================================================================

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
        FLY_MULT_CAP      = FLY_MULT_CAP_RAW.get();
        FLY_SCALING       = FLY_SCALING_RAW.get();
        FLY_BASE_SPEED    = FLY_BASE_SPEED_RAW.get();
        FLY_KI_DRAIN      = FLY_KI_DRAIN_RAW.get();
        TURBO_DRAIN_PCT_PER_SEC = TURBO_DRAIN_PCT_PER_SEC_RAW.get();
        TRAINING_PL_RATIO_FULL   = TRAINING_PL_RATIO_FULL_RAW.get();
        REGEN_BODY_EXHAUSTION    = REGEN_BODY_EXHAUSTION_RAW.get();
        REGEN_STAMINA_EXHAUSTION = REGEN_STAMINA_EXHAUSTION_RAW.get();
        REGEN_MIN_FOOD           = REGEN_MIN_FOOD_RAW.get();
        PARTY_MAX_SIZE_CEILING   = PARTY_MAX_SIZE_CEILING_RAW.get();

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
        TECH_MASTERY_PER_USE    = TECH_MASTERY_PER_USE_RAW.get();
        M_FORM_DRAIN     = MASTERY_FORM_DRAIN_RED_RAW.get();
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
        WEIGHT_TP_BONUS    = WEIGHT_TP_BONUS_RAW.get();
        WEIGHT_OVER_THRESH = WEIGHT_OVER_THRESH_RAW.get();
        WEIGHT_OVER_MOVE   = WEIGHT_OVER_MOVE_RAW.get();
        PU_TP_MULT       = PU_TP_MULT_RAW.get();
        PU_ALIGNMENT_REQ = PU_ALIGNMENT_REQ_RAW.get();

        KI_COST_PER_POWER     = KI_COST_PER_POWER_RAW.get();
        MELEE_STAMINA_PER_HIT = MELEE_STAMINA_PER_HIT_RAW.get();
        WEAPON_SCALE          = WEAPON_SCALE_RAW.get();
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
        TECH_DUMP_DIR    = TECH_DUMP_DIR_RAW.get();
    }

    // =====================================================================
    // GETTERS (thread-safe)
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

    public static double speedMultiplierCap()       { return SPEED_MULT_CAP; }
    public static double movementScaling()          { return MOVE_SCALING; }
    public static double runStaminaDrainPerSecond() { return RUN_STAMINA_DRAIN; }
    public static double flyMultiplierCap()         { return FLY_MULT_CAP; }
    public static double flyScaling()               { return FLY_SCALING; }
    public static double flyBaseSpeed()             { return FLY_BASE_SPEED; }
    public static double flyKiDrainPerTick()        { return FLY_KI_DRAIN; }
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
    public static double techMasteryPerUse()         { return TECH_MASTERY_PER_USE; }
    public static double masteryFormDrainReduction() { return M_FORM_DRAIN; }
    public static double masteryTechDamageBonus()    { return M_TECH_DMG; }
    public static double masteryTechCostReduction()  { return M_TECH_COST; }
    public static double masteryTechCastReduction()  { return M_TECH_CAST; }
    public static double majinStatBonus()            { return MAJIN_STAT_BONUS; }
    public static double potentialUnlockTpMult()     { return PU_TP_MULT; }
    public static int    potentialUnlockAlignmentReq() { return PU_ALIGNMENT_REQ; }


    public static double kiCostPerPower()    { return KI_COST_PER_POWER; }
    public static double meleeStaminaPerHit() { return MELEE_STAMINA_PER_HIT; }
    public static double explosionSacrificeConversion() {return EXPLOSION_SACRIFICE_CONVERSION;}

    /** El arma como multiplicador del golpe, no como suma. Ver KiInfusion. */
    public static double weaponScale()       { return WEAPON_SCALE; }
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
    public static double weightTpBonus()           { return WEIGHT_TP_BONUS; }
    public static double weightOverloadThreshold() { return WEIGHT_OVER_THRESH; }
    public static double weightOverloadMoveFactor(){ return WEIGHT_OVER_MOVE; }
    public static String techniqueDumpDir()          { return TECH_DUMP_DIR; }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static List<Integer> ints(int... vals) {
        return Arrays.stream(vals).boxed().toList();
    }

    private static List<Double> doubles(double... vals) {
        return Arrays.stream(vals).boxed().toList();
    }

    private static int[] toIntArray(List<? extends Integer> list, int[] fallback) {
        if (list == null || list.size() != 6) return fallback.clone();
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            Integer v = list.get(i);
            out[i] = (v != null) ? v : fallback[i];
        }
        return out;
    }

    private static double[] toDoubleArray(List<? extends Double> list, double[] fallback) {
        if (list == null || list.size() != 6) return fallback.clone();
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            Double v = list.get(i);
            out[i] = (v != null) ? v : fallback[i];
        }
        return out;
    }
}