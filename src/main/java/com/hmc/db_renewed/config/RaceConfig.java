package com.hmc.db_renewed.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hmc.db_renewed.data.EnumRace;
import com.hmc.db_renewed.data.StatSet;
import com.hmc.db_renewed.data.StatType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RaceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("db_renwed");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Float>>>(){}.getType();
    private static final Path CONFIG_PATH = Path.of("config", "db_renwed", "race_stats.json");

    private static Map<String, Map<String, Float>> raceStats = new HashMap<>();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                generateDefault();
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                raceStats = GSON.fromJson(reader, TYPE);
                LOGGER.info("Loaded race stats config.");
            }
        } catch (IOException | JsonSyntaxException | JsonIOException e) {
            LOGGER.error("Failed to load race stats config", e);
        }
    }

    private static void generateDefault() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, Map<String, Float>> defaultStats = new HashMap<>();
            defaultStats.put("saiyan", Map.of("strength", 10f, "agility", 8f, "spirit", 5f));
            defaultStats.put("namekian", Map.of("strength", 6f, "agility", 7f, "spirit", 10f));

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(defaultStats, TYPE, writer);
            }

            LOGGER.info("Generated default race_stats.json");
        } catch (IOException e) {
            LOGGER.error("Failed to generate default race_stats.json", e);
        }
    }

    public static Map<String, Float> getStats(ResourceLocation raceId) {
        return raceStats.getOrDefault(raceId.getPath(), Collections.emptyMap());
    }
    
    public record RaceStats(StatSet baseStats, StatSet multipliers) {}

    private static final Map<EnumRace, RaceStats> raceData = new HashMap<>();

    public static void load(Path configDir) {
        try {
            Path file = configDir.resolve("race_stats.json");
            Type type = new TypeToken<Map<String, Map<String, Map<String, Float>>>>(){}.getType();
            Map<String, Map<String, Map<String, Float>>> raw = GSON.fromJson(new FileReader(file.toFile()), type);

            for (Map.Entry<String, Map<String, Map<String, Float>>> entry : raw.entrySet()) {
                EnumRace race = EnumRace.valueOf(entry.getKey());
                StatSet base = new StatSet(0);
                StatSet mult = new StatSet(1);

                Map<String, Map<String, Float>> values = entry.getValue();
                for (Map.Entry<String, Map<String, Float>> section : values.entrySet()) {
                    StatSet target = switch (section.getKey()) {
                        case "base" -> base;
                        case "multipliers" -> mult;
                        default -> null;
                    };

                    if (target != null) {
                        for (Map.Entry<String, Float> stat : section.getValue().entrySet()) {
                            StatType statType = StatType.valueOf(stat.getKey());
                            target.set(statType, stat.getValue());
                        }
                    }
                }

                raceData.put(race, new RaceStats(base, mult));
            }

        } catch (Exception e) {
            throw new RuntimeException("Error loading race_stats.json", e);
        }
    }

    public static StatSet getBaseStats(EnumRace race) {
        return raceData.containsKey(race) ? raceData.get(race).baseStats : new StatSet(0f);
    }

    public static float getRaceMultiplier(EnumRace race, StatType type) {
        return raceData.containsKey(race) ? raceData.get(race).multipliers.get(type) : 1f;
    }
}