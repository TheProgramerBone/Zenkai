package com.hmc.zenkai.feature.advancement;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Los triggers de logros de Zenkai, los cinco en un archivo.
 * POR QUÉ JUNTOS Y NO UNO POR ARCHIVO. Son cinco codecs mecánicamente idénticos que solo se
 * diferencian en sus campos. Repartidos en cinco archivos, la forma común se copia cinco veces
 * y en la sexta alguien la copia mal. Aquí no pueden divergir.
 * POR QUÉ CINCO TRIGGERS CON PREDICADOS Y NO VEINTE ESPECÍFICOS. Un trigger por logro
 * ("became_ssj1", "reached_pl_1000") son veinte clases Java y un datapack que solo puede
 * expresar lo que ya previmos. Con predicados, un addon inventa condiciones nuevas sin tocar
 * Java — que es el punto de tener superficie de datapack.
 * MILESTONE es el comodín deliberado: un `kind` de texto para eventos puntuales sin más
 * condiciones (black flash, ser revivido). Añadir uno nuevo es una constante y una línea de
 * JSON, no una clase.
 * ⚠ APIs a verificar al compilar (NeoForge 1.21.1):
 *   - BuiltInRegistries.TRIGGER_TYPES como registro de CriterionTrigger
 *   - EntityPredicate.ADVANCEMENT_CODEC -> Codec&lt;ContextAwarePredicate&gt;
 *   - SimpleCriterionTrigger.SimpleInstance y la firma de codec()
 */
public final class ZenkaiTriggers {
    private ZenkaiTriggers() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, Zenkai.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, RaceChosen> RACE_CHOSEN =
            TRIGGERS.register("race_chosen", RaceChosen::new);

    public static final DeferredHolder<CriterionTrigger<?>, StatThreshold> STAT_THRESHOLD =
            TRIGGERS.register("stat_threshold", StatThreshold::new);

    public static final DeferredHolder<CriterionTrigger<?>, TechniqueUsed> TECHNIQUE_USED =
            TRIGGERS.register("technique_used", TechniqueUsed::new);

    public static final DeferredHolder<CriterionTrigger<?>, WishGranted> WISH_GRANTED =
            TRIGGERS.register("wish_granted", WishGranted::new);

    public static final DeferredHolder<CriterionTrigger<?>, Milestone> MILESTONE =
            TRIGGERS.register("milestone", Milestone::new);

    public static void register(IEventBus bus) {
        TRIGGERS.register(bus);
    }

    /** Kinds de MILESTONE. Constantes y no enum: un addon puede inventar los suyos. */
    public static final class Kinds {
        private Kinds() {}
        public static final String BLACK_FLASH    = "black_flash";
        public static final String REVIVED        = "revived";
        public static final String COMBAT_STANCE  = "combat_stance";
    }

    // =====================================================================
    // RACE_CHOSEN
    // =====================================================================

    /** Elegir raza. Los dos campos son opcionales: sin ninguno vale cualquier raza. */
    public static final class RaceChosen extends SimpleCriterionTrigger<RaceChosen.Instance> {

        public record Instance(Optional<ContextAwarePredicate> player,
                               Optional<String> race,
                               Optional<String> style) implements SimpleInstance {

            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("race").forGetter(Instance::race),
                    Codec.STRING.optionalFieldOf("style").forGetter(Instance::style)
            ).apply(i, Instance::new));

            boolean matches(String race, String style) {
                if (this.race.isPresent() && !this.race.get().equalsIgnoreCase(race)) return false;
                return this.style.isEmpty() || this.style.get().equalsIgnoreCase(style);
            }
        }

        @Override public @NotNull Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer sp, String race, String style) {
            this.trigger(sp, i -> i.matches(race, style));
        }
    }

    // =====================================================================
    // STAT_THRESHOLD
    // =====================================================================

    /**
     * Umbrales de progresión. Se dispara por SONDEO, una vez por segundo desde el tick del
     * jugador — no por evento, porque los stats cambian por media docena de vías (entrenar,
     * comprar, transformarse, ponerse pesas) y engancharlas todas sería garantizar que se
     * olvida una. Un sondeo por segundo es la misma cadencia que usa vanilla para su trigger
     * de localización.
     * Las condiciones se leen del jugador dentro de matches(), no se pasan por parámetro: así
     * añadir un campo nuevo no obliga a tocar el punto de llamada.
     */
    public static final class StatThreshold extends SimpleCriterionTrigger<StatThreshold.Instance> {

        public record Instance(Optional<ContextAwarePredicate> player,
                               Optional<Long> powerLevel,
                               Optional<Integer> tp,
                               Optional<Double> weightLoad,
                               Optional<String> skill,
                               Optional<Integer> skillLevel,
                               Optional<Boolean> allSkills,
                               Optional<Boolean> allSkillsMax) implements SimpleInstance {

            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.LONG.optionalFieldOf("power_level").forGetter(Instance::powerLevel),
                    Codec.INT.optionalFieldOf("tp").forGetter(Instance::tp),
                    Codec.DOUBLE.optionalFieldOf("weight_load").forGetter(Instance::weightLoad),
                    Codec.STRING.optionalFieldOf("skill").forGetter(Instance::skill),
                    Codec.INT.optionalFieldOf("skill_level").forGetter(Instance::skillLevel),
                    Codec.BOOL.optionalFieldOf("all_skills").forGetter(Instance::allSkills),
                    Codec.BOOL.optionalFieldOf("all_skills_max").forGetter(Instance::allSkillsMax)
            ).apply(i, Instance::new));

            boolean matches(ServerPlayer sp) {
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                if (!att.isRaceChosen()) return false;

                // PL LIMPIO, sin la penalización de las pesas. Con el penalizado, ponerte pesas
                // te alejaría del logro de PL justo cuando estás entrenando más — y además
                // chocaría con el logro de carga máxima, que quiere lo contrario.
                if (powerLevel.isPresent() && att.getReleasablePowerLevel() < powerLevel.get()) return false;
                if (tp.isPresent() && att.getTP() < tp.get()) return false;
                if (weightLoad.isPresent() && WeightSystem.computeLoad(sp) < weightLoad.get()) return false;

                if (skill.isPresent()) {
                    int need = skillLevel.orElse(1);
                    if (att.skills().level(skill.get()) < need) return false;
                }

                if (allSkills.orElse(false) && !hasAll(att, false)) return false;
                return !allSkillsMax.orElse(false) || hasAll(att, true);
            }

            /** Todas las habilidades COMPRABLES. Las de grant() quedan fuera a propósito: llegan
             *  solas con la raza y contarlas haría el logro más fácil para unas razas que otras. */
            private static boolean hasAll(PlayerStatsAttachment att, boolean maxed) {
                for (SkillDef d : SkillDef.all()) {
                    if (!d.purchasable()) continue;
                    int lvl = att.skills().level(d.id());
                    if (lvl < (maxed ? d.maxLevel() : 1)) return false;
                }
                return true;
            }
        }

        @Override public @NotNull Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer sp) {
            this.trigger(sp, i -> i.matches(sp));
        }
    }

    // =====================================================================
    // TECHNIQUE_USED
    // =====================================================================

    /** Lanzar una técnica. `charge` es fracción: 1.0 = carga llena, 2.0 = sobrecarga máxima. */
    public static final class TechniqueUsed extends SimpleCriterionTrigger<TechniqueUsed.Instance> {

        public record Instance(Optional<ContextAwarePredicate> player,
                               Optional<String> technique,
                               Optional<Double> charge) implements SimpleInstance {

            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("technique").forGetter(Instance::technique),
                    Codec.DOUBLE.optionalFieldOf("charge").forGetter(Instance::charge)
            ).apply(i, Instance::new));

            boolean matches(String tech, double chargeF) {
                if (technique.isPresent() && !technique.get().equalsIgnoreCase(tech)) return false;
                return charge.isEmpty() || chargeF >= charge.get();
            }
        }

        @Override public @NotNull Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer sp, String technique, double charge) {
            this.trigger(sp, i -> i.matches(technique, charge));
        }
    }

    // =====================================================================
    // WISH_GRANTED
    // =====================================================================

    /** Pedirle un deseo a Shenlong. Sin `wish`, vale cualquiera. */
    public static final class WishGranted extends SimpleCriterionTrigger<WishGranted.Instance> {

        public record Instance(Optional<ContextAwarePredicate> player,
                               Optional<String> wish) implements SimpleInstance {

            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("wish").forGetter(Instance::wish)
            ).apply(i, Instance::new));

            boolean matches(String id) {
                return wish.isEmpty() || wish.get().equalsIgnoreCase(id);
            }
        }

        @Override public @NotNull Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer sp, String wish) {
            this.trigger(sp, i -> i.matches(wish));
        }
    }

    // =====================================================================
    // MILESTONE
    // =====================================================================

    /** Comodín para eventos puntuales sin condiciones propias. Ver Kinds. */
    public static final class Milestone extends SimpleCriterionTrigger<Milestone.Instance> {

        public record Instance(Optional<ContextAwarePredicate> player,
                               Optional<String> kind) implements SimpleInstance {

            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("kind").forGetter(Instance::kind)
            ).apply(i, Instance::new));

            boolean matches(String id) {
                return kind.isEmpty() || kind.get().equalsIgnoreCase(id);
            }
        }

        @Override public @NotNull Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer sp, String kind) {
            this.trigger(sp, i -> i.matches(kind));
        }
    }
}