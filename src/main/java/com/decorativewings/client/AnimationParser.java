
package com.decorativewings.client;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;

public class AnimationParser {
    private static AnimationConfig config = new AnimationConfig();
    private static final Gson GSON = new Gson();

    public static void load() {
        try {
            Path path = Paths.get("config/decorativewings/animations.json");
            if (Files.exists(path)) {
                config = GSON.fromJson(Files.newBufferedReader(path), AnimationConfig.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to load animations.json, using defaults: " + e.getMessage());
        }
    }

    public static AnimationConfig.StateParams getParams(String type, String state) {
        if (config.types == null) return new AnimationConfig.StateParams();
        var typeMap = config.types.get(type);
        if (typeMap == null) return new AnimationConfig.StateParams();
        return typeMap.getOrDefault(state, new AnimationConfig.StateParams());
    }
}