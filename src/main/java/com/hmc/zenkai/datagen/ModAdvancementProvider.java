package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.registry.ModBiomes;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Los 31 logros de Zenkai, en UNA sola pestaña.
 * POR QUÉ UNA PESTAÑA. Tres pestañas de un mod en la pantalla de logros gritan "conversión
 * total", y ese es justo el problema que el resto del diseño se esfuerza en no tener.
 * LA RAÍZ OTORGA EL MUNDO al entrar (trigger `tick` de vanilla, igual que la raíz de
 * la historia de Minecraft). No es decoración: la selección de raza vive detrás de un keybind y
 * NADA en el juego se lo dice al jugador. El toast de la raíz es el tutorial, y usa el sistema
 * que Minecraft ya tiene en vez de inventar una GUI. Quien no quiera tocar el mod ignora el
 * toast y sigue jugando: la raíz no obliga a nada.
 * RECOMPENSAS: casi ninguna. Un logro que da poder deja de ser un marcador y pasa a ser una
 * checklist obligatoria, y la economía de TP ya está calibrada. Solo XP en los dos challenge, y
 * a mano — el frame `challenge` da el mensaje morado y el sonido, pero NO da experiencia solo.
 */
public class ModAdvancementProvider extends AdvancementProvider {

    public ModAdvancementProvider(PackOutput output,
                                  CompletableFuture<HolderLookup.Provider> registries,
                                  ExistingFileHelper existingFileHelper) {
        super(output, registries, existingFileHelper, List.of(new ZenkaiAdvancements()));
    }

    private static final class ZenkaiAdvancements implements AdvancementProvider.AdvancementGenerator {

        private static final ResourceLocation BACKGROUND =
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/advancements/backgrounds/zenkai.png");

        @Override
        public void generate(HolderLookup.@NotNull Provider registries, @NotNull Consumer<AdvancementHolder> saver,
                             @NotNull ExistingFileHelper efh) {

            HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);

            // ── RAÍZ ────────────────────────────────────────────────────────
            AdvancementHolder root = Advancement.Builder.advancement()
                    .display(ModItems.SENZU_BEAN.get(), title("awakening"), desc("awakening"),
                            BACKGROUND, AdvancementType.TASK, true, true, false)
                    .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                    .save(saver, id("awakening"), efh);

            AdvancementHolder chooseRace = child(saver, efh, root, "choose_race",
                    ModItems.SPACE_POD_ITEM.get(), AdvancementType.TASK, false,
                    "race", raceChosen());

            // ── PODER ───────────────────────────────────────────────────────
            AdvancementHolder firstTp = child(saver, efh, chooseRace, "first_tp",
                    net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, AdvancementType.TASK, false,
                    "tp", tp(1));

            AdvancementHolder pl1k = child(saver, efh, firstTp, "pl_1000",
                    ModItems.ENERGY_CRYSTAL.get(), AdvancementType.TASK, false,
                    "pl", powerLevel(1_000));

            AdvancementHolder pl100k = child(saver, efh, pl1k, "pl_100k",
                    ModItems.NAMEK_CRYSTAL.get(), AdvancementType.TASK, false,
                    "pl", powerLevel(100_000));

            Advancement.Builder.advancement().parent(pl100k)
                    .display(ModItems.SACRED_STONE.get(), title("pl_1m"), desc("pl_1m"),
                            null, AdvancementType.CHALLENGE, true, true, true)
                    .rewards(AdvancementRewards.Builder.experience(100))
                    .addCriterion("pl", powerLevel(1_000_000))
                    .save(saver, id("pl_1m"), efh);

            // ── ENTRENAMIENTO ───────────────────────────────────────────────
            AdvancementHolder weights = child(saver, efh, chooseRace, "weights",
                    ModItems.WEIGHTED_STRAPS.get(), AdvancementType.TASK, false,
                    "equip", hasItems(ModItems.WEIGHTED_STRAPS.get()));

            child(saver, efh, weights, "max_load",
                    ModItems.WEIGHTED_CAPE.get(), AdvancementType.GOAL, false,
                    "load", weightLoad(1.0));

            // ── HABILIDADES ─────────────────────────────────────────────────
            child(saver, efh, chooseRace, "fly",
                    ModItems.KINTOUN_ITEM.get(), AdvancementType.TASK, false,
                    "skill", skill("fly", 1));

            AdvancementHolder kiSense = child(saver, efh, chooseRace, "ki_sense",
                    net.minecraft.world.item.Items.ENDER_EYE, AdvancementType.TASK, false,
                    "skill", skill("ki_sense", 1));

            child(saver, efh, kiSense, "scouter",
                    ModItems.SCOUTER.get(), AdvancementType.TASK, false,
                    "get", hasItems(ModItems.SCOUTER.get()));

            child(saver, efh, chooseRace, "kaioken",
                    net.minecraft.world.item.Items.BLAZE_POWDER, AdvancementType.TASK, false,
                    "skill", skill("kaioken", 1));

            // No oculto por el mismo motivo que combat_stance/wheel_menu: mantener TAB (y encima
            // hace falta clic derecho, no solo soltar la tecla) no se adivina, y su descripción
            // tiene que poder leerse en la pantalla de logros ANTES de completarlo. Dispara al
            // completar el gesto de verdad (InstantTransmissionSystem.tryBlink), no solo al
            // comprar la skill — un jugador que compra el nivel 1 y nunca prueba la tecla no
            // debería ver esto ya tachado.
            AdvancementHolder instantTransmission = child(saver, efh, chooseRace, "instant_transmission",
                    net.minecraft.world.item.Items.ENDER_PEARL, AdvancementType.TASK, false,
                    "used", milestone(ZenkaiTriggers.Kinds.INSTANT_TRANSMISSION_USED));

            // Colgado del anterior, mismo patrón que wheel_menu bajo combat_stance: es la misma
            // tecla, un paso más — soltar tras el clic derecho blinkea, mantenerse quieto en vez
            // de soltar arma el menú de planetas. Requiere nivel 3 (SkillEffects.
            // instantTransmissionMenuUnlocked), así que este logro ya implica el anterior.
            child(saver, efh, instantTransmission, "instant_transmission_menu",
                    net.minecraft.world.item.Items.FILLED_MAP, AdvancementType.TASK, false,
                    "opened", milestone(ZenkaiTriggers.Kinds.INSTANT_TRANSMISSION_MENU_OPENED));

            // No oculto por el mismo motivo que combat_stance: la tecla H no se adivina, y su
            // descripción tiene que poder leerse ANTES de desbloquear la primera forma.
            // Nivel 2, NO 1: el nivel 1 de super_forms es el suelo REGALADO a cualquier raza
            // con transformaciones (ver SuperForms javadoc) — con el criterio en 1 el logro se
            // completaba solo con elegir raza, sin haber comprado ni transformado nunca. El
            // nivel 2 es la primera forma de verdad comprada.
            child(saver, efh, chooseRace, "transformation",
                    net.minecraft.world.item.Items.GOLDEN_APPLE, AdvancementType.TASK, false,
                    "skill", skill(SuperForms.SKILL, 2));

            AdvancementHolder allSkills = Advancement.Builder.advancement().parent(chooseRace)
                    .display(ModItems.ELITE_CIRCUIT.get(), title("all_skills"), desc("all_skills"),
                            null, AdvancementType.GOAL, true, true, true)
                    .addCriterion("skills", allSkills(false))
                    .save(saver, id("all_skills"), efh);

            Advancement.Builder.advancement().parent(allSkills)
                    .display(Items.NETHER_STAR, title("all_skills_max"), desc("all_skills_max"),
                            null, AdvancementType.CHALLENGE, true, true, true)
                    .rewards(AdvancementRewards.Builder.experience(100))
                    .addCriterion("skills", allSkills(true))
                    .save(saver, id("all_skills_max"), efh);

            // ── COMBATE ─────────────────────────────────────────────────────
            // No oculto a propósito: su descripción (qué tecla entra/sale de la postura de
            // combate, y qué hacen 1-9/click derecho una vez dentro) tiene que poder leerse
            // en la pantalla de logros ANTES de completarlo, si no no sirve de tutorial.
            AdvancementHolder combatStance = child(saver, efh, chooseRace, "combat_stance",
                    net.minecraft.world.item.Items.IRON_SWORD, AdvancementType.TASK, false,
                    "stance", milestone(ZenkaiTriggers.Kinds.COMBAT_STANCE));

            // No oculto por el mismo motivo que combat_stance: MANTENER pulsada la X (en vez de
            // solo pulsarla) es un gesto que nadie adivina, y su descripción tiene que poder
            // leerse ANTES de completarlo. Colgado de combat_stance porque es la misma tecla,
            // un paso más — pulsar entra en combate, mantener abre el menú radial.
            child(saver, efh, combatStance, "wheel_menu",
                    net.minecraft.world.item.Items.COMPASS, AdvancementType.TASK, false,
                    "wheel", milestone(ZenkaiTriggers.Kinds.WHEEL_USED));

            // ── TÉCNICAS ────────────────────────────────────────────────────
            AdvancementHolder firstTech = child(saver, efh, chooseRace, "first_technique",
                    net.minecraft.world.item.Items.FIRE_CHARGE, AdvancementType.TASK, false,
                    "use", technique(0.0));

            AdvancementHolder fullCharge = child(saver, efh, firstTech, "full_charge",
                    net.minecraft.world.item.Items.GLOWSTONE_DUST, AdvancementType.TASK, false,
                    "use", technique(1.0));

            child(saver, efh, fullCharge, "overcharge",
                    ModBlocks.ENERGY_CRYSTAL_BLOCK.get(), AdvancementType.GOAL, false,
                    "use", technique(2.0));

            // ── OCULTOS DE COMBATE ──────────────────────────────────────────
            Advancement.Builder.advancement().parent(chooseRace)
                    .display(Items.POLISHED_BLACKSTONE, title("black_flash"), desc("black_flash"),
                            null, AdvancementType.GOAL, true, true, true)
                    .addCriterion("proc", milestone(ZenkaiTriggers.Kinds.BLACK_FLASH))
                    .save(saver, id("black_flash"), efh);

            Advancement.Builder.advancement().parent(chooseRace)
                    .display(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, title("revived"), desc("revived"),
                            null, AdvancementType.TASK, true, true, true)
                    .addCriterion("revived", milestone(ZenkaiTriggers.Kinds.REVIVED))
                    .save(saver, id("revived"), efh);

            Advancement.Builder.advancement().parent(chooseRace)
                    .display(net.minecraft.world.item.Items.NETHER_STAR, title("overdrive"), desc("overdrive"),
                            null, AdvancementType.GOAL, true, true, true)
                    .addCriterion("overdrive", milestone(ZenkaiTriggers.Kinds.OVERDRIVE))
                    .save(saver, id("overdrive"), efh);

            // ── ESFERAS DEL DRAGÓN ──────────────────────────────────────────
            AdvancementHolder firstBall = child(saver, efh, chooseRace, "first_ball",
                    ModBlocks.DRAGON_BALL_4.get(), AdvancementType.TASK, false,
                    "ball", hasTag(ModTags.Items.DRAGON_BALLS_ITEM));

            AdvancementHolder radar = child(saver, efh, firstBall, "radar",
                    ModItems.DRAGON_BALL_RADAR.get(), AdvancementType.TASK, false,
                    "get", hasItems(ModItems.DRAGON_BALL_RADAR.get()));

            AdvancementHolder sevenEarth = child(saver, efh, radar, "seven_earth",
                    ModItems.ALL_DRAGON_BALLS_ITEM.get(), AdvancementType.GOAL, false,
                    "seven", hasItems(
                            ModBlocks.DRAGON_BALL_1.get(), ModBlocks.DRAGON_BALL_2.get(),
                            ModBlocks.DRAGON_BALL_3.get(), ModBlocks.DRAGON_BALL_4.get(),
                            ModBlocks.DRAGON_BALL_5.get(), ModBlocks.DRAGON_BALL_6.get(),
                            ModBlocks.DRAGON_BALL_7.get()));

            child(saver, efh, radar, "seven_namek",
                    ModBlocks.NAMEK_DRAGON_BALL_7.get(), AdvancementType.GOAL, false,
                    "seven", hasItems(
                            ModBlocks.NAMEK_DRAGON_BALL_1.get(), ModBlocks.NAMEK_DRAGON_BALL_2.get(),
                            ModBlocks.NAMEK_DRAGON_BALL_3.get(), ModBlocks.NAMEK_DRAGON_BALL_4.get(),
                            ModBlocks.NAMEK_DRAGON_BALL_5.get(), ModBlocks.NAMEK_DRAGON_BALL_6.get(),
                            ModBlocks.NAMEK_DRAGON_BALL_7.get()));

            AdvancementHolder firstWish = child(saver, efh, sevenEarth, "first_wish",
                    ModBlocks.ALL_DRAGON_BALLS.get(), AdvancementType.TASK, false,
                    "wish", wish(null));

            // Los seis criterios y ninguna llamada a requirements(): el default de vanilla es
            // AND, o sea que hacen falta LOS SEIS. Es justo lo que queremos aquí.
            Advancement.Builder.advancement().parent(firstWish)
                    .display(net.minecraft.world.item.Items.DRAGON_EGG, title("all_wishes"), desc("all_wishes"),
                            null, AdvancementType.CHALLENGE, true, true, false)
                    .rewards(AdvancementRewards.Builder.experience(100))
                    .addCriterion("immortality", wish("immortality"))
                    .addCriterion("training_points", wish("training_points"))
                    .addCriterion("revive_player", wish("revive_player"))
                    .addCriterion("revive_pet", wish("revive_pet"))
                    .addCriterion("enchant_villager", wish("enchant_villager"))
                    .addCriterion("stack", wish("stack"))
                    .save(saver, id("all_wishes"), efh);

            // ── LUGARES ─────────────────────────────────────────────────────
            child(saver, efh, chooseRace, "namek",
                    ModBlocks.NAMEKIAN_GRASS_BLOCK.get(), AdvancementType.TASK, false,
                    "go", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModDimensions.NAMEK_LEVEL));

            child(saver, efh, chooseRace, "htc",
                    ModBlocks.HTC_BLOCK.get(), AdvancementType.TASK, false,
                    "go", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModDimensions.HTC_LEVEL));

            AdvancementHolder otherworld = Advancement.Builder.advancement().parent(chooseRace)
                    .display(ModItems.HALO.get(), title("otherworld"), desc("otherworld"),
                            null, AdvancementType.TASK, true, true, true)
                    .addCriterion("go", ChangeDimensionTrigger.TriggerInstance
                            .changedDimensionTo(ModDimensions.OTHERWORLD_LEVEL))
                    .save(saver, id("otherworld"), efh);

            // Los 3 criterios y ninguna llamada a requirements(): el default de vanilla es AND
            // (mismo patrón que all_wishes arriba), o sea que hacen falta LOS TRES biomas.
            // Primer uso de LocationTrigger/LocationPredicate de vanilla en el mod — no hace
            // falta ningún trigger propio de Zenkai para "el jugador está en tal bioma".
            Advancement.Builder.advancement().parent(otherworld)
                    .display(Items.BONE_BLOCK, title("hfil_biomes"), desc("hfil_biomes"),
                            null, AdvancementType.TASK, true, true, true)
                    .addCriterion("blood_shore", inBiome(biomes, ModBiomes.HFIL_BLOOD_SHORE))
                    .addCriterion("needle_wastes", inBiome(biomes, ModBiomes.HFIL_NEEDLE_WASTES))
                    .addCriterion("cinder_dunes", inBiome(biomes, ModBiomes.HFIL_CINDER_DUNES))
                    .save(saver, id("hfil_biomes"), efh);
        }

        // =================================================================
        // HELPERS
        // =================================================================

        /** Un logro corriente colgado de otro. Existe para que las 20 llamadas de arriba quepan
         *  en una línea y las excepciones (challenge, oculto, recompensa) se vean de lejos. */
        private static AdvancementHolder child(Consumer<AdvancementHolder> saver, ExistingFileHelper efh,
                                               AdvancementHolder parent, String name, ItemLike icon,
                                               AdvancementType type, boolean hidden,
                                               String critName, Criterion<?> crit) {
            return Advancement.Builder.advancement().parent(parent)
                    .display(icon, title(name), desc(name), null, type, true, true, hidden)
                    .addCriterion(critName, crit)
                    .save(saver, id(name), efh);
        }

        /** El overload de save() que acepta ExistingFileHelper es la extensión de NeoForge y
         *  pide ResourceLocation; el de String es el de vanilla, que no valida el padre. */
        private static ResourceLocation id(String name) {
            return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
        }
        private static Component title(String name) { return Component.translatable("advancements.zenkai." + name + ".title"); }
        private static Component desc(String name)  { return Component.translatable("advancements.zenkai." + name + ".description"); }

        // ── Criterios de Zenkai ──────────────────────────────────────────

        private static Criterion<?> raceChosen() {
            return ZenkaiTriggers.RACE_CHOSEN.get().createCriterion(
                    new ZenkaiTriggers.RaceChosen.Instance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        private static Criterion<?> stat(Optional<Long> pl, Optional<Integer> tp, Optional<Double> load,
                                         Optional<String> skill, Optional<Integer> skillLvl,
                                         Optional<Boolean> all, Optional<Boolean> allMax) {
            return ZenkaiTriggers.STAT_THRESHOLD.get().createCriterion(
                    new ZenkaiTriggers.StatThreshold.Instance(Optional.empty(), pl, tp, load, skill, skillLvl, all, allMax));
        }

        private static Criterion<?> powerLevel(long v) {
            return stat(Optional.of(v), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        private static Criterion<?> tp(int v) {
            return stat(Optional.empty(), Optional.of(v), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        private static Criterion<?> weightLoad(double v) {
            return stat(Optional.empty(), Optional.empty(), Optional.of(v),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        private static Criterion<?> skill(String id, int lvl) {
            return stat(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(id), Optional.of(lvl), Optional.empty(), Optional.empty());
        }

        private static Criterion<?> allSkills(boolean maxed) {
            return stat(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(),
                    maxed ? Optional.empty() : Optional.of(true),
                    maxed ? Optional.of(true) : Optional.empty());
        }

        private static Criterion<?> technique(double charge) {
            return ZenkaiTriggers.TECHNIQUE_USED.get().createCriterion(
                    new ZenkaiTriggers.TechniqueUsed.Instance(Optional.empty(), Optional.empty(),
                            charge <= 0.0 ? Optional.empty() : Optional.of(charge)));
        }

        private static Criterion<?> wish(String id) {
            return ZenkaiTriggers.WISH_GRANTED.get().createCriterion(
                    new ZenkaiTriggers.WishGranted.Instance(Optional.empty(), Optional.ofNullable(id)));
        }

        private static Criterion<?> milestone(String kind) {
            return ZenkaiTriggers.MILESTONE.get().createCriterion(
                    new ZenkaiTriggers.Milestone.Instance(Optional.empty(), Optional.of(kind)));
        }

        // ── Criterios de vanilla ─────────────────────────────────────────

        private static Criterion<?> hasItems(ItemLike... items) {
            return InventoryChangeTrigger.TriggerInstance.hasItems(items);
        }

        private static Criterion<?> hasTag(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
            return InventoryChangeTrigger.TriggerInstance.hasItems(
                    ItemPredicate.Builder.item().of(tag));
        }

        /** El jugador está actualmente en el bioma dado — mismo trigger CriteriaTriggers.LOCATION
         *  de vanilla que ya usa PlayerTrigger.TriggerInstance.located(...) para "pisando tal
         *  bloque"; aquí con un LocationPredicate de bioma en vez de bloque. Sondeo periódico de
         *  vanilla, misma cadencia que StatThreshold (ver su javadoc en ZenkaiTriggers). Usado
         *  por hfil_biomes; no hace falta ningún trigger propio de Zenkai para esto. */
        private static Criterion<?> inBiome(HolderGetter<Biome> biomes, ResourceKey<Biome> biome) {
            return PlayerTrigger.TriggerInstance.located(
                    LocationPredicate.Builder.inBiome(biomes.getOrThrow(biome)));
        }
    }
}