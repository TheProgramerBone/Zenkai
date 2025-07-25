package com.hmc.db_renewed.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hmc.db_renewed.data.EnumStyle;
import com.hmc.db_renewed.data.StatType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StyleConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("db_renwed");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Float>>>(){}.getType();
    private static final Path CONFIG_PATH = Path.of("config", "db_renwed", "style_multipliers.json");

    private static Map<String, Map<String, Float>> styleMultipliers = new HashMap<String, Map<String, Float>>();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                generateDefault();
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                styleMultipliers = GSON.fromJson(reader, TYPE);
                LOGGER.info("Loaded style multipliers config.");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load style multipliers config", e);
        }
    }

    private static void generateDefault() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, Map<String, Float>> defaultStyles = new HashMap<>();
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(defaultStyles, TYPE, writer);
            }

            LOGGER.info("Generated default style_multipliers.json");
        } catch (IOException e) {
            LOGGER.error("Failed to generate default style_multipliers.json", e);
        }
    }

    public static Map<String, Float> getMultipliers(ResourceLocation styleId) {
        return styleMultipliers.getOrDefault(styleId.getPath(), Collections.emptyMap());
    }

    public static float getStyleMultiplier(EnumStyle style, StatType type) {
        Map<String, Float> multipliers = styleMultipliers.get(style.getSerializedName());
        if (multipliers != null && multipliers.containsKey(type.getSerializedName())) {
            return multipliers.get(type.getSerializedName());
        }
        return 1f;
    }
}
