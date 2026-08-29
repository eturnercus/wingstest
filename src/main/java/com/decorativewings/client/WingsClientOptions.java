package com.decorativewings.client;

import com.decorativewings.DecorativeWingsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WingsClientOptions {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("decorativewings-client.txt");
    private static boolean firstPerson;

    private WingsClientOptions() {
    }

    public static boolean showFirstPerson() {
        return firstPerson;
    }

    public static boolean toggleFirstPerson() {
        setFirstPerson(!firstPerson);
        return firstPerson;
    }

    public static void setFirstPerson(boolean value) {
        firstPerson = value;
        save();
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) {
            firstPerson = false;
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            firstPerson = text.contains("firstPerson=true");
        } catch (IOException exception) {
            DecorativeWingsMod.LOGGER.warn("Failed to read {}", FILE, exception);
            firstPerson = false;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "firstPerson=" + firstPerson + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DecorativeWingsMod.LOGGER.warn("Failed to write {}", FILE, exception);
        }
    }
}
