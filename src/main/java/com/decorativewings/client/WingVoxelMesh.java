package com.decorativewings.client;

import com.decorativewings.DecorativeWingsMod;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.*;
import java.util.*;

/**
 * Builds wings from a 2D sprite. Now supports dynamic loading from JSON definitions.
 */
public final class WingVoxelMesh {
    public static final File CONFIG_WINGS_DIR = new File("config/decorativewings/wings/");
    public static final File CONFIG_TEXTURES_DIR = new File("config/decorativewings/textures/");
    private static final Gson GSON = new Gson();

    public record Cube(float x0, float y0, float z0, float x1, float y1, float z1,
                       float u0, float v0, float u1, float v1) {}

    public record Side(List<Cube> inner, List<Cube> mid, List<Cube> outer,
                       float innerWidth, float midWidth, float outerWidth, float height) {
        public int cubeCount() { return inner.size() + mid.size() + outer.size(); }
    }

    private record Spec(ResourceLocation texture, boolean sculpt, boolean attachOnRight, float targetHeight,
                        boolean perPixel) {}

    private static final Map<Spec, WingVoxelMesh> CACHE = new HashMap<>();
    private static final Map<String, WingDefinition> DEFINITIONS = new HashMap<>();

    public final Side left;
    public final Side right;
    public final ResourceLocation texture;
    public final int textureWidth;
    public final int textureHeight;
    public final float pivotY;

    private WingVoxelMesh(Side left, Side right, ResourceLocation texture, int textureWidth, int textureHeight, float pivotY) {
        this.left = left;
        this.right = right;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.pivotY = pivotY;
    }

    /**
     * Scans the config/decorativewings/wings/ folder for JSON definitions.
     * Call this during mod initialization or when reloading config.
     */
    public static void loadDefinitions() {
        DEFINITIONS.clear();
        if (!CONFIG_WINGS_DIR.exists()) {
            CONFIG_WINGS_DIR.mkdirs();
            CONFIG_TEXTURES_DIR.mkdirs();
        }

        File[] files = CONFIG_WINGS_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (Reader reader = new FileReader(file)) {
                    WingDefinition def = GSON.fromJson(reader, WingDefinition.class);
                    if (def != null && def.id() != null) {
                        DEFINITIONS.put(def.id(), def);
                    }
                } catch (IOException | JsonSyntaxException e) {
                    DecorativeWingsMod.LOGGER.error("Failed to load wing definition from {}: {}", file.getName(), e.getMessage());
                }
            }
        }
    }

    public static List<String> getAvailableWingIds() {
        return new ArrayList<>(DEFINITIONS.keySet());
    }

    public static WingVoxelMesh getById(String id) {
        WingDefinition def = DEFINITIONS.get(id);
        if (def == null) {
            DecorativeWingsMod.LOGGER.error("Wing definition not found for id: {}", id);
            return empty();
        }

        // We use a ResourceLocation as a key for the cache, mapping it to the texture file name
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "dynamic/" + def.texture());
        return get(loc, def.sculpt(), def.attachOnRight(), def.targetHeight(), def.perPixel());
    }

    public static void invalidate() {
        CACHE.clear();
        DEFINITIONS.clear();
    }

    public static WingVoxelMesh get(ResourceLocation texture, boolean sculpt, boolean attachOnRight,
                                    float targetHeight, boolean perPixel) {
        Spec spec = new Spec(texture, sculpt, attachOnRight, targetHeight, perPixel);
        return CACHE.computeIfAbsent(spec, WingVoxelMesh::build);
    }

    private static WingVoxelMesh build(Spec spec) {
        String fileName = spec.texture().getPath().substring(spec.texture().getPath().lastIndexOf('/') + 1);
        File configFile = new File(CONFIG_TEXTURES_DIR, fileName);

        ResourceLocation finalTex = WingsTextureManager.loadTexture(CONFIG_TEXTURES_DIR, fileName);
        if (finalTex == null) {
            finalTex = spec.texture();
        }

        if (configFile.exists()) {
            try (InputStream stream = new FileInputStream(configFile); NativeImage image = NativeImage.read(stream)) {
                return voxelize(image, spec, finalTex);
            } catch (IOException exception) {
                DecorativeWingsMod.LOGGER.error("Failed to load wing texture from config: {}", fileName, exception);
            }
        }

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(spec.texture());
        if (resource.isEmpty()) {
            DecorativeWingsMod.LOGGER.error("Missing wing texture {} and no config override found", spec.texture());
            return empty();
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return voxelize(image, spec, finalTex);
        } catch (Exception exception) {
            DecorativeWingsMod.LOGGER.error("Failed to voxelize wing texture", exception);
            return empty();
        }
    }


    private static WingVoxelMesh empty() {
        Side empty = new Side(List.of(), List.of(), List.of(), 0.1F, 0.1F, 0.1F, 1.0F);
        return new WingVoxelMesh(empty, empty, ResourceLocation.fromNamespaceAndPath("minecraft", "empty"), 1, 1, 0.0F);
    }

    private static WingVoxelMesh voxelize(NativeImage image, Spec spec, ResourceLocation texture) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] opaque = new boolean[height][width];
        int[][] lum = new int[height][width];
        int minX = width, minY = height, maxX = -1, maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(pixel) > 16) {
                    opaque[y][x] = true;
                    int r = FastColor.ABGR32.red(pixel);
                    int g = FastColor.ABGR32.green(pixel);
                    int b = FastColor.ABGR32.blue(pixel);
                    lum[y][x] = (r * 3 + g * 4 + b) / 8;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < 0) return empty();

        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        float scale = spec.targetHeight() > 1.0F ? spec.targetHeight() / boxH : 1.0F;
        float innerEnd = boxW / 3.0F;
        float midEnd = boxW * 2.0F / 3.0F;
        boolean attachOnRight = spec.attachOnRight();

        int attachX = attachOnRight ? maxX : minX;
        float pivotSum = 0.0F;
        int pivotCount = 0;
        for (int y = minY; y <= maxY; y++) {
            if (opaque[y][attachX]) {
                pivotSum += (y - minY) + 0.5F;
                pivotCount++;
            }
        }
        float pivotY = (pivotCount == 0 ? boxH * 0.35F : pivotSum / pivotCount) * scale;

        List<Cube> leftInner = new ArrayList<>(), leftMid = new ArrayList<>(), leftOuter = new ArrayList<>();
        List<Cube> rightInner = new ArrayList<>(), rightMid = new ArrayList<>(), rightOuter = new ArrayList<>();

        if (spec.sculpt()) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!opaque[y][x]) continue;
                    float dist = attachOnRight ? (maxX + 0.5F) - (x + 0.5F) : (x + 0.5F) - (minX + 0.5F);
                    float spanT = Mth.clamp(dist / (float) boxW, 0.0F, 1.0F);
                    float chordT = Mth.clamp((y + 0.5F - minY) / (float) boxH, 0.0F, 1.0F);
                    boolean rib = lum[y][x] < 48, dark = lum[y][x] < 92;
                    int open = 0;
                    if (x == minX || !opaque[y][x - 1]) open++;
                    if (x == maxX || !opaque[y][x + 1]) open++;
                    if (y == minY || !opaque[y - 1][x]) open++;
                    if (y == maxY || !opaque[y + 1][x]) open++;

                    float thick = rib ? 2.20F : dark ? 1.45F : open > 0 ? 1.05F : 0.70F + (1.0F - chordT) * 0.40F;
                    float camber = spanT * spanT * 3.4F + Mth.sin(spanT * Mth.PI) * 2.1F + chordT * 0.70F + (rib ? 0.55F : 0);

                    addCube(leftInner, leftMid, leftOuter, rightInner, rightMid, rightOuter,
                            x, y, 1, 1, width, height, minY, minX, maxX, scale, innerEnd, midEnd,
                            attachOnRight, camber - thick * 0.5F, camber + thick * 0.5F);
                }
            }
        } else if (spec.perPixel()) {
            float thickness = 0.16F;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!opaque[y][x]) continue;
                    addCube(leftInner, leftMid, leftOuter, rightInner, rightMid, rightOuter,
                            x, y, 1, 1, width, height, minY, minX, maxX, scale, innerEnd, midEnd,
                            attachOnRight, -thickness * 0.5F, thickness * 0.5F);
                }
            }
        } else {
            float thickness = 0.16F;
            for (int[] rect : greedyRects(opaque, minX, minY, maxX, maxY)) {
                addCube(leftInner, leftMid, leftOuter, rightInner, rightMid, rightOuter,
                        rect[0], rect[1], rect[2], rect[3], width, height, minY, minX, maxX, scale, innerEnd, midEnd,
                        attachOnRight, -thickness * 0.5F, thickness * 0.5F);
            }
        }

        float innerW = innerEnd * scale, midW = (midEnd - innerEnd) * scale, outerW = (boxW - midEnd) * scale;
        float meshHeight = boxH * scale;
        Side left = new Side(List.copyOf(leftInner), List.copyOf(leftMid), List.copyOf(leftOuter), innerW, midW, outerW, meshHeight);
        Side right = new Side(List.copyOf(rightInner), List.copyOf(rightMid), List.copyOf(rightOuter), innerW, midW, outerW, meshHeight);

        return new WingVoxelMesh(left, right, texture, width, height, pivotY);
    }

    private static void addCube(List<Cube> leftInner, List<Cube> leftMid, List<Cube> leftOuter,
                                List<Cube> rightInner, List<Cube> rightMid, List<Cube> rightOuter,
                                int x, int y, int rw, int rh, int width, int height, int minY, int minX, int maxX,
                                float scale, float innerEnd, float midEnd, boolean attachOnRight, float z0, float z1) {
        float dist = attachOnRight ? (maxX + 0.5F) - (x + rw * 0.5F) : (x + rw * 0.5F) - (minX + 0.5F);
        List<Cube> lb = dist < innerEnd ? leftInner : dist < midEnd ? leftMid : leftOuter;
        List<Cube> rb = dist < innerEnd ? rightInner : dist < midEnd ? rightMid : rightOuter;

        float y0 = (y - minY) * scale, y1 = (y + rh - minY) * scale;
        float u0 = x / (float) width, v0 = y / (float) height, u1 = (x + rw) / (float) width, v1 = (y + rh) / (float) height;

        if (attachOnRight) {
            lb.add(new Cube((x - maxX - 1) * scale, y0, z0, (x + rw - maxX - 1) * scale, y1, z1, u0, v0, u1, v1));
            rb.add(new Cube((maxX + 1 - (x + rw)) * scale, y0, z0, (maxX + 1 - x) * scale, y1, z1, u1, v0, u0, v1));
        } else {
            rb.add(new Cube((x - minX) * scale, y0, z0, (x + rw - minX) * scale, y1, z1, u0, v0, u1, v1));
            lb.add(new Cube(-(x + rw - minX) * scale, y0, z0, -(x - minX) * scale, y1, z1, u1, v0, u0, v1));
        }
    }

    private static List<int[]> greedyRects(boolean[][] opaque, int minX, int minY, int maxX, int maxY) {
        int height = opaque.length, width = opaque[0].length;
        boolean[][] visited = new boolean[height][width];
        List<int[]> rects = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!opaque[y][x] || visited[y][x]) continue;
                int x2 = x;
                while (x2 + 1 <= maxX && opaque[y][x2 + 1] && !visited[y][x2 + 1]) x2++;
                int y2 = y;
                grow:
                while (y2 + 1 <= maxY) {
                    for (int xx = x; xx <= x2; xx++) {
                        if (!opaque[y2 + 1][xx] || visited[y2 + 1][xx]) break grow;
                    }
                    y2++;
                }
                for (int yy = y; yy <= y2; yy++) {
                    for (int xx = x; xx <= x2; xx++) visited[yy][xx] = true;
                }
                rects.add(new int[]{x, y, x2 - x + 1, y2 - y + 1});
            }
        }
        return rects;
    }
}