package com.hmc.zenkai.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Genera data/zenkai/zenkai_entities/*.json — las fichas de stats de entidades.
 * POR QUÉ EN CÓDIGO Y NO A MANO: son 45 fichas que comparten el 90% de su forma. Sueltas, cada
 * campo nuevo (como `alignment`) obliga a abrir 45 archivos y basta un olvido para que una
 * entidad se comporte distinto sin que nadie sepa por qué. Aquí los PL están todos en la misma
 * pantalla, que es lo que hace falta para calibrarlos unos contra otros.
 * ⚠ ANTES DE USARLO: borra src/main/resources/data/zenkai/zenkai_entities/ entero. Los dos
 * directorios de recursos se montan juntos y tendrías cada ficha duplicada.
 * ALINEAMIENTO (-100..+100): es la maldad SENTIDA, no la hostilidad mecánica. Un enderman es
 * peligroso pero no malvado; un piglin zombificado es una víctima. Alimenta el color de la
 * llama y de la silueta del sentir el ki.
 */
public class ModEntityStatsProvider implements DataProvider {

    private final PackOutput.PathProvider path;

    public ModEntityStatsProvider(PackOutput output) {
        this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "zenkai_entities");
    }

    @Override
    public @NotNull String getName() { return "Zenkai Entity Stats"; }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cache) {
        Map<String, JsonObject> defs = new LinkedHashMap<>();
        build(defs);

        List<CompletableFuture<?>> out = new ArrayList<>(defs.size());
        for (Map.Entry<String, JsonObject> e : defs.entrySet()) {
            out.add(DataProvider.saveStable(cache, e.getValue(),
                    path.json(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, e.getKey()))));
        }
        return CompletableFuture.allOf(out.toArray(CompletableFuture[]::new));
    }

    // ─────────────────────────────────────────────────────────── el catálogo

    private static void build(Map<String, JsonObject> out) {

        // --- Zenkai ---------------------------------------------------------
        put(out, "saibaman", Def.of("zenkai:saibaman", 1200, "brawler", -80)
                .attr("strength", "+20%").mults(1.0, 1.0)
                .ki("wave",  3, "0x33CCFF", 400, 20, null)
                .ki("blast", 5, "0xFFAA00", 200, 24, 1.3));

        put(out, "namekian_warrior", Def.of("zenkai:namekian_warrior", 3000, "brawler", 60)
                .attr("strength", "+20%").mults(1.0, 1.0)
                .ki("wave",  3, "0x49cc5d", 400, 20, null)
                .ki("blast", 5, "0xFFAA00", 200, 24, 1.3));

        // --- Jefes ----------------------------------------------------------
        put(out, "warden",         Def.of("minecraft:warden",         13000, "tank",  -60).attr("strength", "+30%"));
        put(out, "wither",         Def.of("minecraft:wither",          9300, "boss",  -95));
        put(out, "ender_dragon",   Def.of("minecraft:ender_dragon",    7440, "boss",  -85));
        put(out, "elder_guardian", Def.of("minecraft:elder_guardian",  1120, "balanced", -60));

        // --- Hostiles -------------------------------------------------------
        put(out, "iron_golem",       Def.of("minecraft:iron_golem",       715, "tank",       70).attr("strength", "+25%"));
        put(out, "ravager",          Def.of("minecraft:ravager",          715, "tank",      -75));
        put(out, "evoker",           Def.of("minecraft:evoker",           760, "balanced",  -80));
        put(out, "ghast",            Def.of("minecraft:ghast",            760, "balanced",  -40));
        put(out, "blaze",            Def.of("minecraft:blaze",            665, "balanced",  -50));
        put(out, "breeze",           Def.of("minecraft:breeze",           665, "speedster", -30));
        put(out, "illusioner",       Def.of("minecraft:illusioner",       665, "balanced",  -75));
        put(out, "pillager",         Def.of("minecraft:pillager",         665, "balanced",  -70));
        put(out, "guardian",         Def.of("minecraft:guardian",         595, "balanced",  -50));
        put(out, "witch",            Def.of("minecraft:witch",            595, "balanced",  -60));
        put(out, "bogged",           Def.of("minecraft:bogged",           545, "balanced",  -55));
        put(out, "shulker",          Def.of("minecraft:shulker",          545, "balanced",  -40));
        put(out, "stray",            Def.of("minecraft:stray",            545, "balanced",  -55));
        put(out, "enderman",         Def.of("minecraft:enderman",         540, "speedster", -10));
        put(out, "skeleton",         Def.of("minecraft:skeleton",         500, "balanced",  -50));
        put(out, "creeper",          Def.of("minecraft:creeper",          445, "brawler",   -45));
        put(out, "piglin_brute",     Def.of("minecraft:piglin_brute",     445, "brawler",   -55).attr("strength", "+15%"));
        put(out, "vindicator",       Def.of("minecraft:vindicator",       445, "brawler",   -80));
        put(out, "vex",              Def.of("minecraft:vex",              360, "speedster", -65));
        put(out, "wither_skeleton",  Def.of("minecraft:wither_skeleton",  360, "brawler",   -65));
        put(out, "cave_spider",      Def.of("minecraft:cave_spider",      300, "speedster", -40));
        put(out, "spider",           Def.of("minecraft:spider",           300, "speedster", -30));
        put(out, "hoglin",           Def.of("minecraft:hoglin",           305, "brawler",   -30));
        put(out, "zoglin",           Def.of("minecraft:zoglin",           305, "brawler",   -50));
        put(out, "phantom",          Def.of("minecraft:phantom",          280, "speedster", -45));
        put(out, "slime",            Def.of("minecraft:slime",            280, "balanced",  -10));
        put(out, "wolf",             Def.of("minecraft:wolf",             280, "speedster",  30));
        put(out, "piglin",           Def.of("minecraft:piglin",           265, "brawler",   -25));
        put(out, "polar_bear",       Def.of("minecraft:polar_bear",       265, "brawler",   -15));
        put(out, "zombified_piglin", Def.of("minecraft:zombified_piglin", 265, "brawler",   -20));
        put(out, "husk",             Def.of("minecraft:husk",             240, "brawler",   -55));
        put(out, "endermite",        Def.of("minecraft:endermite",        225, "speedster", -25));
        put(out, "silverfish",       Def.of("minecraft:silverfish",       225, "speedster", -25));
        put(out, "drowned",          Def.of("minecraft:drowned",          215, "brawler",   -50));
        put(out, "magma_cube",       Def.of("minecraft:magma_cube",       215, "brawler",   -35));
        put(out, "zombie",           Def.of("minecraft:zombie",           215, "brawler",   -50));
        put(out, "zombie_villager",  Def.of("minecraft:zombie_villager",  215, "brawler",   -45));

        // --- Pacíficos ------------------------------------------------------
        // El gólem de nieve no persigue: lanza bolas y punto. Es el único pacífico con stats
        // de combate, porque es el único que ataca.
        put(out, "snow_golem", Def.of("minecraft:snow_golem", 36, "balanced", 50).noMelee());

        // El resto va display_only: PL para el scouter y alineamiento para el sentido, sin
        // stats de combate. Sin ficha caían al heurístico de hostilidad, que devuelve 0 para
        // lo que no es Enemy — y 0 se pinta neutro. Un aldeano no es neutro.
        put(out, "allay",            Def.displayOnly("minecraft:allay",            12,  85));
        put(out, "villager",         Def.displayOnly("minecraft:villager",         20,  55));
        put(out, "wandering_trader", Def.displayOnly("minecraft:wandering_trader", 22,  55));
        put(out, "dolphin",          Def.displayOnly("minecraft:dolphin",          30,  50));
        put(out, "sniffer",          Def.displayOnly("minecraft:sniffer",          40,  45));
        put(out, "cat",              Def.displayOnly("minecraft:cat",              14,  45));
        put(out, "parrot",           Def.displayOnly("minecraft:parrot",           10,  40));
        put(out, "axolotl",          Def.displayOnly("minecraft:axolotl",          18,  35));
        put(out, "horse",            Def.displayOnly("minecraft:horse",            30,  35));
        put(out, "donkey",           Def.displayOnly("minecraft:donkey",           25,  35));
        put(out, "mule",             Def.displayOnly("minecraft:mule",             28,  35));
        put(out, "panda",            Def.displayOnly("minecraft:panda",            45,  35));
        put(out, "ocelot",           Def.displayOnly("minecraft:ocelot",           18,  30));
        put(out, "turtle",           Def.displayOnly("minecraft:turtle",           15,  30));
        put(out, "camel",            Def.displayOnly("minecraft:camel",            35,  30));
        put(out, "fox",              Def.displayOnly("minecraft:fox",              20,  25));
        put(out, "armadillo",        Def.displayOnly("minecraft:armadillo",        18,  25));
        put(out, "cow",              Def.displayOnly("minecraft:cow",              15,  25));
        put(out, "mooshroom",        Def.displayOnly("minecraft:mooshroom",        15,  25));
        put(out, "pig",              Def.displayOnly("minecraft:pig",              12,  25));
        put(out, "sheep",            Def.displayOnly("minecraft:sheep",            12,  25));
        put(out, "chicken",          Def.displayOnly("minecraft:chicken",           8,  25));
        put(out, "rabbit",           Def.displayOnly("minecraft:rabbit",            8,  25));
        put(out, "bee",              Def.displayOnly("minecraft:bee",              14,  20));
        put(out, "llama",            Def.displayOnly("minecraft:llama",            30,  20));
        put(out, "trader_llama",     Def.displayOnly("minecraft:trader_llama",     30,  20));
        put(out, "strider",          Def.displayOnly("minecraft:strider",          12,  20));
        put(out, "frog",             Def.displayOnly("minecraft:frog",             10,  15));
        put(out, "tadpole",          Def.displayOnly("minecraft:tadpole",           3,  15));
        put(out, "goat",             Def.displayOnly("minecraft:goat",             25,  10));
        put(out, "squid",            Def.displayOnly("minecraft:squid",            10,  10));
        put(out, "glow_squid",       Def.displayOnly("minecraft:glow_squid",       10,  10));
        put(out, "cod",              Def.displayOnly("minecraft:cod",               5,  10));
        put(out, "salmon",           Def.displayOnly("minecraft:salmon",            5,  10));
        put(out, "tropical_fish",    Def.displayOnly("minecraft:tropical_fish",     4,  10));
        put(out, "pufferfish",       Def.displayOnly("minecraft:pufferfish",        6,   5));
        put(out, "bat",              Def.displayOnly("minecraft:bat",               6,   5));

        // --- Solo display ---------------------------------------------------
        // Sin stats de combate: el maniquí solo enseña un PL. Namespace ajeno a propósito
        // (mod externo opcional): si no está instalado, la ficha simplemente no se usa.
        put(out, "target_dummy_display", Def.displayOnly("dummmmmmy:target_dummy", 1, 0));
    }

    private static void put(Map<String, JsonObject> out, String name, Def def) {
        out.put(name, def.json());
    }

    // ─────────────────────────────────────────────────────────── constructor

    /**
     * Constructor fluido de una ficha. Los valores por defecto son los del 90% de las entidades
     * (melee sí, recompensa de TP automática), así que solo se escribe lo que se sale de la
     * norma y las excepciones se ven de un vistazo en la lista de arriba.
     */
    private static final class Def {
        private final JsonObject root = new JsonObject();
        private JsonObject overrides;
        private JsonObject attributes;
        private JsonObject moveset;
        private JsonArray kiAttacks;

        private Def() {}

        static Def of(String entity, long powerLevel, String archetype, int alignment) {
            Def d = new Def();
            d.root.addProperty("entity", entity);
            d.root.addProperty("power_level", powerLevel);
            d.root.addProperty("archetype", archetype);
            d.root.addProperty("alignment", clampAlign(alignment));

            d.moveset = new JsonObject();
            d.moveset.addProperty("melee", true);

            return d;
        }

        static Def displayOnly(String entity, long powerLevel, int alignment) {
            Def d = new Def();
            d.root.addProperty("entity", entity);
            d.root.addProperty("power_level", powerLevel);
            d.root.addProperty("display_only", true);
            d.root.addProperty("alignment", clampAlign(alignment));
            return d;
        }

        Def attr(String name, String value) {
            if (overrides == null) overrides = new JsonObject();
            if (attributes == null) {
                attributes = new JsonObject();
                overrides.add("attributes", attributes);
            }
            attributes.addProperty(name, value);
            return this;
        }

        Def mults(double body, double ki) {
            if (overrides == null) overrides = new JsonObject();
            overrides.addProperty("body_mult", body);
            overrides.addProperty("ki_mult", ki);
            return this;
        }

        Def noMelee() {
            if (moveset != null) moveset.addProperty("melee", false);
            return this;
        }

        /** damageMult null = no se escribe el campo (el cargador ya tiene su defecto). */
        Def ki(String type, int size, String rgb, int cooldown, int range, Double damageMult) {
            if (moveset == null) return this;
            if (kiAttacks == null) {
                kiAttacks = new JsonArray();
                moveset.add("ki_attacks", kiAttacks);
            }
            JsonObject o = new JsonObject();
            o.addProperty("type", type);
            o.addProperty("size", size);
            o.addProperty("rgb", rgb);
            o.addProperty("cooldown", cooldown);
            o.addProperty("range", range);
            if (damageMult != null) o.addProperty("damage_mult", damageMult);
            kiAttacks.add(o);
            return this;
        }

        JsonObject json() {
            // El ensamblado se hace AQUÍ y no en cada setter para que el orden de las claves
            // del JSON sea siempre el mismo, llames a los setters en el orden que llames.
            if (overrides != null) root.add("overrides", overrides);
            if (moveset != null) {
                root.add("moveset", moveset);
                JsonObject rewards = new JsonObject();
                rewards.addProperty("tp", "auto");
                root.add("rewards", rewards);
            }
            return root;
        }

        private static int clampAlign(int a) {
            return Math.max(-100, Math.min(100, a));
        }
    }
}