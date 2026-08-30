
package com.decorativewings.client;

import com.decorativewings.DecorativeWingsMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.util.*;

public class WingsTextureManager {
    private static final Map<ResourceLocation, DynamicTexture> TEXTURE_CACHE = new HashMap<>();

    public static ResourceLocation loadTexture(File folder, String fileName) {
        if (fileName == null) return null;

        String folderName = folder.getName();
        // Создаем строку ключа, чтобы избежать проблем с объектами ResourceLocation
        String cacheKey = folderName + "/" + fileName;

        // Используем отдельную карту для проверки или переделаем ResourceLocation
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("decorativewings", "textures/" + folderName + "/" + fileName);

        if (TEXTURE_CACHE.containsKey(loc)) {
            return loc;
        }

        File file = new File(folder, fileName);
        if (!file.exists()) {
            // Добавим лог, чтобы понять, почему он не видит файл, несмотря на путь в run
            DecorativeWingsMod.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
            return null;
        }

        try (InputStream is = new FileInputStream(file); NativeImage image = NativeImage.read(is)) {
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
            TEXTURE_CACHE.put(loc, dynamicTexture);
            return loc;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void invalidate() {
        TEXTURE_CACHE.clear();
    }
}