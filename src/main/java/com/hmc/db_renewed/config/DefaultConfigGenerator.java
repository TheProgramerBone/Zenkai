package com.hmc.db_renewed.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultConfigGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigGenerator.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String MOD_ID = "db_renewed";

    public static void generateDefaultsIfMissing() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        Path raceFile = configDir.resolve("race_stats.json");
        Path styleFile = configDir.resolve("style_multipliers.json");

        try {
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.notExists(raceFile)) {
                writeRaceDefaults(raceFile);
            }

            if (Files.notExists(styleFile)) {
                writeStyleDefaults(styleFile);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to generate default config files for {}", MOD_ID, e);
        }
    }

    private static void writeRaceDefaults(Path file) throws IOException {
        Map<String, Map<String, Map<String, Float>>> data = new LinkedHashMap<>();

        Map<String, Map<String, Float>> human = new LinkedHashMap<>();
        human.put("base", Map.of(
                "STRENGTH", 10f,
                "CONSTITUTION", 10f,
                "DEXTERITY", 10f,
                "WILLPOWER", 10f,
                "SPIRIT", 10f,
                "MIND",10f
        ));
        human.put("multipliers", Map.of(
                "STRENGTH", 1.0f,
                "CONSTITUTION", 1.0f,
                "DEXTERITY", 1.0f,
                "WILLPOWER", 1.0f,
                "SPIRIT", 1.0f,
                "MIND",1.0f
        ));
        data.put("HUMAN", human);

        Map<String, Map<String, Float>> saiyan = new LinkedHashMap<>();
        saiyan.put("base", Map.of(
                "STRENGTH", 14f,
                "CONSTITUTION", 10f,
                "DEXTERITY", 12f,
                "WILLPOWER", 8f,
                "SPIRIT", 6f,
                "MIND", 10f
        ));
        saiyan.put("multipliers", Map.of(
                "STRENGTH", 1.3f,
                "CONSTITUTION", 1.0f,
                "DEXTERITY", 1.2f,
                "WILLPOWER", 0.8f,
                "SPIRIT", 0.7f,
                "MIND",1.0f
        ));
        data.put("SAIYAN", saiyan);

        Map<String, Map<String, Float>> namekian = new LinkedHashMap<>();
        namekian.put("base", Map.of(
                "STRENGTH", 8f,
                "CONSTITUTION", 8f,
                "DEXTERITY", 10f,
                "WILLPOWER", 11f,
                "SPIRIT", 13f,
                "MIND", 10f
        ));
        namekian.put("multipliers", Map.of(
                "STRENGTH", 0.8f,
                "CONSTITUTION", 0.9f,
                "DEXTERITY", 0.9f,
                "WILLPOWER", 1.1f,
                "SPIRIT", 1.3f,
                "MIND", 1.0f
        ));
        data.put("NAMEKIAN", namekian);

        Map<String, Map<String, Float>> cold_demon = new LinkedHashMap<>();
        cold_demon.put("base", Map.of(
                "STRENGTH", 8f,
                "CONSTITUTION", 8f,
                "DEXTERITY", 10f,
                "WILLPOWER", 12f,
                "SPIRIT", 12f,
                "MIND", 10f
        ));
        cold_demon.put("multipliers", Map.of(
                "STRENGTH", 0.9f,
                "CONSTITUTION", 0.9f,
                "DEXTERITY", 1.0f,
                "WILLPOWER", 1.2f,
                "SPIRIT", 1.1f,
                "MIND", 1.0f
        ));
        data.put("COLD DEMON", cold_demon);

        Map<String, Map<String, Float>> majin = new LinkedHashMap<>();
        majin.put("base", Map.of(
                "STRENGTH", 10f,
                "CONSTITUTION", 8f,
                "DEXTERITY", 14f,
                "WILLPOWER", 8f,
                "SPIRIT", 10f,
                "MIND", 10f
        ));
        majin.put("multipliers", Map.of(
                "STRENGTH", 0.9f,
                "CONSTITUTION", 1.3f,
                "DEXTERITY", 0.9f,
                "WILLPOWER", 1.1f,
                "SPIRIT", 0.8f,
                "MIND", 1.0f
        ));
        data.put("MAJIN", majin);

        try (BufferedWriter writer = Files.newBufferedWriter((java.nio.file.Path) file)) {
            GSON.toJson(data, writer);
        }
    }

    private static void writeStyleDefaults(Path file) throws IOException {
        Map<String, Map<String, Float>> data = new LinkedHashMap<>();

        data.put("WARRIOR", Map.of(
                "STRENGTH", 1.2f,
                "CONSTITUTION", 1.1f,
                "DEXTERITY", 1.3f,
                "WILLPOWER", 0.8f,
                "SPIRIT", 0.8f,
                "MIND",1.0f
        ));

        data.put("MARTIAL_ARTIST", Map.of(
                "STRENGTH", 1.1f,
                "CONSTITUTION", 1.0f,
                "DEXTERITY", 1.0f,
                "WILLPOWER", 1.0f,
                "SPIRIT", 1.1f,
                "MIND",1.0f
        ));

        data.put("SPIRITUALIST", Map.of(
                "STRENGTH", 0.9f,
                "CONSTITUTION", 0.9f,
                "DEXTERITY", 0.9f,
                "WILLPOWER", 1.3f,
                "SPIRIT", 1.2f,
                "MIND",1.0f
        ));

        try (BufferedWriter writer = Files.newBufferedWriter((java.nio.file.Path) file)) {
            GSON.toJson(data, writer);
        }
    }
}