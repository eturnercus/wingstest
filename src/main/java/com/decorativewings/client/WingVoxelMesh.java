package com.decorativewings.client;

import com.decorativewings.DecorativeWingsMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds wings from a 2D sprite. Pivot is the attach-edge centre so the root sits on the shoulders.
 */
public final class WingVoxelMesh {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "textures/entity/wing.png");
    public static final ResourceLocation TEXTURE_INSECT =
            ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "textures/entity/wing_insect.png");
    public static final ResourceLocation TEXTURE_BIRD =
            ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "textures/entity/wing_bird.png");

    public record Cube(float x0, float y0, float z0, float x1, float y1, float z1,
                       float u0, float v0, float u1, float v1) {
    }

    public record Side(List<Cube> inner, List<Cube> mid, List<Cube> outer,
                       float innerWidth, float midWidth, float outerWidth, float height) {
        public int cubeCount() {
            return inner.size() + mid.size() + outer.size();
        }
    }

    private record Spec(ResourceLocation texture, boolean sculpt, boolean attachOnRight, float targetHeight,
                        boolean perPixel) {
    }

    private static final Map<Spec, WingVoxelMesh> CACHE = new HashMap<>();

    public final Side left;
    public final Side right;
    public final int textureWidth;
    public final int textureHeight;
    public final float pivotY;

    private WingVoxelMesh(Side left, Side right, int textureWidth, int textureHeight, float pivotY) {
        this.left = left;
        this.right = right;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.pivotY = pivotY;
    }

    public static void invalidate() {
        CACHE.clear();
    }

    public static WingVoxelMesh get() {
        return get(TEXTURE, false, true, 15.0F, false);
    }

    public static WingVoxelMesh pixels() {
        return get(TEXTURE, false, true, 15.0F, true);
    }

    public static WingVoxelMesh insect() {
        return get(TEXTURE_INSECT, true, false, 15.0F, true);
    }

    public static WingVoxelMesh bird() {
        return get(TEXTURE_BIRD, true, false, 15.0F, true);
    }

    public static WingVoxelMesh get(ResourceLocation texture, boolean sculpt, boolean attachOnRight,
                                    float targetHeight, boolean perPixel) {
        Spec spec = new Spec(texture, sculpt, attachOnRight, targetHeight, perPixel);
        WingVoxelMesh mesh = CACHE.get(spec);
        if (mesh == null) {
            mesh = build(spec);
            CACHE.put(spec, mesh);
        }
        return mesh;
    }

    private static WingVoxelMesh build(Spec spec) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(spec.texture());
        if (resource.isEmpty()) {
            DecorativeWingsMod.LOGGER.error("Missing wing texture {}", spec.texture());
            return empty();
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return voxelize(image, spec);
        } catch (Exception exception) {
            DecorativeWingsMod.LOGGER.error("Failed to voxelize wing texture", exception);
            return empty();
        }
    }

    private static WingVoxelMesh empty() {
        Side empty = new Side(List.of(), List.of(), List.of(), 0.1F, 0.1F, 0.1F, 1.0F);
        return new WingVoxelMesh(empty, empty, 1, 1, 0.0F);
    }

    private static WingVoxelMesh voxelize(NativeImage image, Spec spec) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] opaque = new boolean[height][width];
        int[][] lum = new int[height][width];
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
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
        if (maxX < 0) {
            DecorativeWingsMod.LOGGER.error("Wing texture has no opaque pixels");
            return empty();
        }

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

        List<Cube> leftInner = new ArrayList<>();
        List<Cube> leftMid = new ArrayList<>();
        List<Cube> leftOuter = new ArrayList<>();
        List<Cube> rightInner = new ArrayList<>();
        List<Cube> rightMid = new ArrayList<>();
        List<Cube> rightOuter = new ArrayList<>();

        if (spec.sculpt()) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!opaque[y][x]) {
                        continue;
                    }
                    float dist = attachOnRight
                            ? (maxX + 0.5F) - (x + 0.5F)
                            : (x + 0.5F) - (minX + 0.5F);
                    float spanT = Mth.clamp(dist / (float) boxW, 0.0F, 1.0F);
                    float chordT = Mth.clamp((y + 0.5F - minY) / (float) boxH, 0.0F, 1.0F);
                    boolean rib = lum[y][x] < 48;
                    boolean dark = lum[y][x] < 92;
                    int open = 0;
                    if (x == minX || !opaque[y][x - 1]) {
                        open++;
                    }
                    if (x == maxX || !opaque[y][x + 1]) {
                        open++;
                    }
                    if (y == minY || !opaque[y - 1][x]) {
                        open++;
                    }
                    if (y == maxY || !opaque[y + 1][x]) {
                        open++;
                    }
                    float thick;
                    if (rib) {
                        thick = 2.20F;
                    } else if (dark) {
                        thick = 1.45F;
                    } else if (open > 0) {
                        thick = 1.05F;
                    } else {
                        thick = 0.70F + (1.0F - chordT) * 0.40F;
                    }
                    float camber = spanT * spanT * 3.4F + Mth.sin(spanT * Mth.PI) * 2.1F + chordT * 0.70F;
                    if (rib) {
                        camber += 0.55F;
                    }
                    addCube(leftInner, leftMid, leftOuter, rightInner, rightMid, rightOuter,
                            x, y, 1, 1, width, height, minY, minX, maxX, scale, innerEnd, midEnd,
                            attachOnRight, camber - thick * 0.5F, camber + thick * 0.5F);
                }
            }
        } else if (spec.perPixel()) {
            float thickness = 0.16F;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!opaque[y][x]) {
                        continue;
                    }
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

        float innerW = innerEnd * scale;
        float midW = (midEnd - innerEnd) * scale;
        float outerW = (boxW - midEnd) * scale;
        float meshHeight = boxH * scale;
        Side left = new Side(List.copyOf(leftInner), List.copyOf(leftMid), List.copyOf(leftOuter),
                innerW, midW, outerW, meshHeight);
        Side right = new Side(List.copyOf(rightInner), List.copyOf(rightMid), List.copyOf(rightOuter),
                innerW, midW, outerW, meshHeight);
        DecorativeWingsMod.LOGGER.info("Voxelized {}: {}x{} sculpt={} scale={} pivotY={} cubes={}",
                spec.texture(), width, height, spec.sculpt(), scale, pivotY, left.cubeCount());
        return new WingVoxelMesh(left, right, width, height, pivotY);
    }

    private static void addCube(List<Cube> leftInner, List<Cube> leftMid, List<Cube> leftOuter,
                                List<Cube> rightInner, List<Cube> rightMid, List<Cube> rightOuter,
                                int x, int y, int rw, int rh, int width, int height, int minY, int minX, int maxX,
                                float scale, float innerEnd, float midEnd, boolean attachOnRight, float z0, float z1) {
        float dist = attachOnRight
                ? (maxX + 0.5F) - (x + rw * 0.5F)
                : (x + rw * 0.5F) - (minX + 0.5F);
        List<Cube> leftBone;
        List<Cube> rightBone;
        if (dist < innerEnd) {
            leftBone = leftInner;
            rightBone = rightInner;
        } else if (dist < midEnd) {
            leftBone = leftMid;
            rightBone = rightMid;
        } else {
            leftBone = leftOuter;
            rightBone = rightOuter;
        }

        float y0 = (y - minY) * scale;
        float y1 = (y + rh - minY) * scale;
        float u0 = x / (float) width;
        float v0 = y / (float) height;
        float u1 = (x + rw) / (float) width;
        float v1 = (y + rh) / (float) height;

        if (attachOnRight) {
            float leftX0 = (x - maxX - 1) * scale;
            float leftX1 = (x + rw - maxX - 1) * scale;
            leftBone.add(new Cube(leftX0, y0, z0, leftX1, y1, z1, u0, v0, u1, v1));
            float rightX0 = (maxX + 1 - (x + rw)) * scale;
            float rightX1 = (maxX + 1 - x) * scale;
            rightBone.add(new Cube(rightX0, y0, z0, rightX1, y1, z1, u1, v0, u0, v1));
        } else {
            float rightX0 = (x - minX) * scale;
            float rightX1 = (x + rw - minX) * scale;
            rightBone.add(new Cube(rightX0, y0, z0, rightX1, y1, z1, u0, v0, u1, v1));
            float leftX0 = -(x + rw - minX) * scale;
            float leftX1 = -(x - minX) * scale;
            leftBone.add(new Cube(leftX0, y0, z0, leftX1, y1, z1, u1, v0, u0, v1));
        }
    }

    private static List<int[]> greedyRects(boolean[][] opaque, int minX, int minY, int maxX, int maxY) {
        int height = opaque.length;
        int width = opaque[0].length;
        boolean[][] visited = new boolean[height][width];
        List<int[]> rects = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!opaque[y][x] || visited[y][x]) {
                    continue;
                }
                int x2 = x;
                while (x2 + 1 <= maxX && opaque[y][x2 + 1] && !visited[y][x2 + 1]) {
                    x2++;
                }
                int y2 = y;
                grow:
                while (y2 + 1 <= maxY) {
                    for (int xx = x; xx <= x2; xx++) {
                        if (!opaque[y2 + 1][xx] || visited[y2 + 1][xx]) {
                            break grow;
                        }
                    }
                    y2++;
                }
                for (int yy = y; yy <= y2; yy++) {
                    for (int xx = x; xx <= x2; xx++) {
                        visited[yy][xx] = true;
                    }
                }
                rects.add(new int[]{x, y, x2 - x + 1, y2 - y + 1});
            }
        }
        return rects;
    }
}
