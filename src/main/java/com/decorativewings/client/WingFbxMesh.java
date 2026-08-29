package com.decorativewings.client;

import com.decorativewings.DecorativeWingsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loaded WingsTest.fbx mesh: a left/right pair in YZ, remapped onto the back.
 */
public final class WingFbxMesh {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "textures/entity/wing_model.png");
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "models/wing_fbx.json");

    public record Vert(float x, float y, float z, float u, float v) {
    }

    public record Tri(Vert a, Vert b, Vert c, int bone) {
    }

    public record Side(List<Tri> inner, List<Tri> mid, List<Tri> outer, float innerWidth, float midWidth, float outerWidth) {
        public int triCount() {
            return inner.size() + mid.size() + outer.size();
        }
    }

    private static volatile WingFbxMesh instance;

    public final Side left;
    public final Side right;

    private WingFbxMesh(Side left, Side right) {
        this.left = left;
        this.right = right;
    }

    public static void invalidate() {
        instance = null;
    }

    public static WingFbxMesh get() {
        WingFbxMesh mesh = instance;
        if (mesh == null) {
            mesh = load();
            instance = mesh;
        }
        return mesh;
    }

    private static WingFbxMesh load() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MODEL);
        if (resource.isEmpty()) {
            DecorativeWingsMod.LOGGER.error("Missing {}", MODEL);
            return empty();
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float maxz = root.get("maxz").getAsFloat();
            JsonArray trisJson = root.getAsJsonArray("tris");
            float extra = 1.75F;
            List<float[]> packed = new ArrayList<>();
            float minAbsX = Float.MAX_VALUE;
            float maxAbsX = 0.0F;
            float minYp = Float.MAX_VALUE;
            float maxYp = -Float.MAX_VALUE;
            for (JsonElement el : trisJson) {
                JsonArray v = el.getAsJsonObject().getAsJsonArray("v");
                float[] row = new float[9];
                for (int i = 0; i < 3; i++) {
                    JsonArray p = v.get(i).getAsJsonArray();
                    float ox = p.get(0).getAsFloat();
                    float oy = p.get(1).getAsFloat();
                    float oz = p.get(2).getAsFloat();
                    float x = oy * extra;
                    float y = (maxz - oz) * extra * 1.12F;
                    float z = ox * extra * 0.07F;
                    row[i * 3] = x;
                    row[i * 3 + 1] = y;
                    row[i * 3 + 2] = z;
                    minAbsX = Math.min(minAbsX, Math.abs(x));
                    maxAbsX = Math.max(maxAbsX, Math.abs(x));
                    minYp = Math.min(minYp, y);
                    maxYp = Math.max(maxYp, y);
                }
                packed.add(row);
            }
            float spanU = Math.max(1.0E-5F, maxAbsX - minAbsX);
            float spanV = Math.max(1.0E-5F, maxYp - minYp);

            List<Tri> leftInner = new ArrayList<>();
            List<Tri> leftMid = new ArrayList<>();
            List<Tri> leftOuter = new ArrayList<>();
            List<Tri> rightInner = new ArrayList<>();
            List<Tri> rightMid = new ArrayList<>();
            List<Tri> rightOuter = new ArrayList<>();
            float maxSpan = 0.0F;
            for (float[] row : packed) {
                Vert[] verts = new Vert[3];
                float cx = 0.0F;
                for (int i = 0; i < 3; i++) {
                    float x = row[i * 3];
                    float y = row[i * 3 + 1];
                    float z = row[i * 3 + 2];
                    float u = Mth.clamp((Math.abs(x) - minAbsX) / spanU, 0.0F, 1.0F);
                    float tv = Mth.clamp((y - minYp) / spanV, 0.0F, 1.0F);
                    verts[i] = new Vert(x, y, z, u, tv);
                    cx += x;
                }
                cx /= 3.0F;
                maxSpan = Math.max(maxSpan, Math.abs(cx));
                int bone;
                float dist = Math.abs(cx);
                if (dist < 2.4F * extra) {
                    bone = 0;
                } else if (dist < 4.8F * extra) {
                    bone = 1;
                } else {
                    bone = 2;
                }
                Tri tri = new Tri(verts[0], verts[1], verts[2], bone);
                List<Tri> bucket;
                boolean left = cx < 0.0F;
                if (bone == 0) {
                    bucket = left ? leftInner : rightInner;
                } else if (bone == 1) {
                    bucket = left ? leftMid : rightMid;
                } else {
                    bucket = left ? leftOuter : rightOuter;
                }
                bucket.add(tri);
            }
            DecorativeWingsMod.LOGGER.info("FBX wings: {} tris, span {}", packed.size(), maxSpan);
            float innerW = 2.4F * extra;
            float midW = 2.4F * extra;
            float outerW = Math.max(0.5F, maxSpan - innerW - midW);
            return new WingFbxMesh(
                    new Side(List.copyOf(leftInner), List.copyOf(leftMid), List.copyOf(leftOuter), innerW, midW, outerW),
                    new Side(List.copyOf(rightInner), List.copyOf(rightMid), List.copyOf(rightOuter), innerW, midW, outerW)
            );
        } catch (Exception exception) {
            DecorativeWingsMod.LOGGER.error("Failed to load FBX wing mesh", exception);
            return empty();
        }
    }

    private static WingFbxMesh empty() {
        Side empty = new Side(List.of(), List.of(), List.of(), 1.0F, 1.0F, 1.0F);
        return new WingFbxMesh(empty, empty);
    }

    public static void renderSide(PoseStack poseStack, VertexConsumer consumer, Side side, boolean left,
                                  WingAnimator.State anim, int light, int overlay) {
        float sign = left ? -1.0F : 1.0F;
        float lag = Mth.sin(anim.phase * 0.85F + (left ? 0.9F : 0.0F)) * 7.2F;
        float flutter = Mth.sin(anim.phase * 3.4F + (left ? 1.1F : 0.0F)) * anim.flutter;
        float spread = Mth.clamp(anim.spread + sign * anim.turn * 0.25F, 8.0F, 42.0F);
        poseStack.pushPose();
        poseStack.translate(sign * 2.4F, 0.0F, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * (-spread + anim.turn * 0.15F)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(anim.fold * 0.45F + anim.pitch * 0.5F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * (anim.sway * 0.35F + anim.bank * 0.7F + anim.dihedral * 0.5F)));

        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * (anim.flap * 0.32F + lag * 0.18F)));
        poseStack.scale(1.0F + (anim.stretch - 1.0F) * 0.10F, 1.0F + (anim.stretch - 1.0F) * 0.08F, 1.0F);
        renderTris(poseStack, consumer, side.inner(), light, overlay);

        poseStack.translate(sign * side.innerWidth(), 0.8F, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * (anim.flap * 0.75F + lag * 0.7F + flutter * 0.5F)));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * anim.spread * 0.08F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(anim.curl * 0.25F));
        poseStack.scale(anim.stretch, 1.0F + (anim.stretch - 1.0F) * 0.15F, 1.0F);
        poseStack.translate(-sign * side.innerWidth(), -0.8F, 0.0F);
        renderTris(poseStack, consumer, side.mid(), light, overlay);

        poseStack.translate(sign * (side.innerWidth() + side.midWidth()), 1.1F, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * (anim.flap * 1.35F + lag * 1.35F + flutter * 1.25F)));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * anim.twist * 0.3F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(anim.curl));
        poseStack.scale(anim.stretch, anim.stretch, 1.0F);
        poseStack.translate(-sign * (side.innerWidth() + side.midWidth()), -1.1F, 0.0F);
        renderTris(poseStack, consumer, side.outer(), light, overlay);
        poseStack.popPose();
        poseStack.popPose();
    }

    public static void renderSidePixel(PoseStack poseStack, VertexConsumer consumer, Side side, boolean left,
                                       WingAnimator.State anim, int light, int overlay) {
        float sign = left ? -1.0F : 1.0F;
        float spread = Mth.clamp(anim.spread + sign * anim.turn * 0.25F, 8.0F, 42.0F);
        float span = Math.max(0.5F, side.innerWidth() + side.midWidth() + side.outerWidth());
        float leftOff = left ? 0.9F : 0.0F;
        poseStack.pushPose();
        poseStack.translate(sign * 2.4F, 0.0F, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * (-spread + anim.turn * 0.15F)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(anim.fold * 0.45F + anim.pitch * 0.5F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * (anim.flap * 0.42F + anim.sway * 0.4F + anim.bank * 0.7F + anim.dihedral * 0.5F)));
        pixelTris(poseStack, consumer, side.inner(), sign, span, leftOff, anim, light, overlay);
        pixelTris(poseStack, consumer, side.mid(), sign, span, leftOff, anim, light, overlay);
        pixelTris(poseStack, consumer, side.outer(), sign, span, leftOff, anim, light, overlay);
        poseStack.popPose();
    }

    private static void pixelTris(PoseStack poseStack, VertexConsumer consumer, List<Tri> tris, float sign, float span,
                                  float leftOff, WingAnimator.State anim, int light, int overlay) {
        for (Tri tri : tris) {
            float cx = (tri.a().x() + tri.b().x() + tri.c().x()) / 3.0F;
            float cy = (tri.a().y() + tri.b().y() + tri.c().y()) / 3.0F;
            float cz = (tri.a().z() + tri.b().z() + tri.c().z()) / 3.0F;
            float t = Mth.clamp(Math.abs(cx) / span, 0.0F, 1.0F);
            float c = Mth.clamp(cy / 12.0F, 0.0F, 1.0F);
            float tip = t * t;
            float morph = 0.5F + 0.5F * Mth.sin(anim.phase * 0.19F + leftOff);
            float waveA = Mth.sin(anim.phase * 1.35F - t * 3.1F + leftOff);
            float waveB = Mth.sin(anim.phase * 2.15F - t * 5.4F + c * 2.4F + leftOff * 1.3F);
            float wave = waveA * (1.0F - morph) + waveB * morph;
            float ripple = Mth.sin(anim.phase2 * 1.8F + c * 4.2F + t * 2.0F + leftOff);
            float shiver = Mth.sin(anim.phase2 * 4.6F + t * 9.0F + c * 7.0F + leftOff * 2.0F);
            float trail = Mth.sin(anim.phase2 * 3.25F + c * 6.8F - t * 1.6F + leftOff) * c;
            float harmonic = Mth.sin(anim.phase * 2.7F - t * 7.5F + leftOff * 0.6F);
            float flap = anim.flap * (0.35F + 1.35F * t)
                    + wave * anim.wave * (0.35F + 0.9F * tip)
                    + shiver * anim.flutter * (0.45F + 0.9F * tip)
                    + ripple * anim.billow * t * 0.55F
                    + trail * anim.billow * 0.45F
                    + harmonic * anim.wave * tip * 0.35F;
            float lift = wave * anim.wave * 0.055F * t + ripple * anim.billow * 0.04F + shiver * 0.012F * tip;
            poseStack.pushPose();
            poseStack.translate(0.0F, trail * 0.035F * t, lift);
            poseStack.translate(cx, cy, cz);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * flap));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                    anim.curl * t + ripple * anim.billow * 0.22F * t + trail * 4.0F + anim.twist * tip * 0.55F));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                    sign * (anim.turn * 0.12F + wave * 4.0F + harmonic * 2.4F) * tip));
            poseStack.scale(1.0F + harmonic * 0.04F * t, 1.0F + ripple * 0.03F * c, 1.0F);
            poseStack.translate(-cx, -cy, -cz);
            renderTri(poseStack, consumer, tri, light, overlay);
            poseStack.popPose();
        }
    }

    private static void renderTris(PoseStack poseStack, VertexConsumer consumer, List<Tri> tris, int light, int overlay) {
        for (Tri tri : tris) {
            renderTri(poseStack, consumer, tri, light, overlay);
        }
    }

    private static void renderTri(PoseStack poseStack, VertexConsumer consumer, Tri tri, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        float half = 0.22F;
        float nx = (tri.b().y() - tri.a().y()) * (tri.c().z() - tri.a().z())
                - (tri.b().z() - tri.a().z()) * (tri.c().y() - tri.a().y());
        float ny = (tri.b().z() - tri.a().z()) * (tri.c().x() - tri.a().x())
                - (tri.b().x() - tri.a().x()) * (tri.c().z() - tri.a().z());
        float nz = (tri.b().x() - tri.a().x()) * (tri.c().y() - tri.a().y())
                - (tri.b().y() - tri.a().y()) * (tri.c().x() - tri.a().x());
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-5F) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        Vert a1 = offset(tri.a(), nx, ny, nz, half);
        Vert b1 = offset(tri.b(), nx, ny, nz, half);
        Vert c1 = offset(tri.c(), nx, ny, nz, half);
        Vert a0 = offset(tri.a(), nx, ny, nz, -half);
        Vert b0 = offset(tri.b(), nx, ny, nz, -half);
        Vert c0 = offset(tri.c(), nx, ny, nz, -half);
        emit(consumer, pose, a1, b1, c1, light, overlay, nx, ny, nz);
        emit(consumer, pose, a0, c0, b0, light, overlay, -nx, -ny, -nz);
    }

    private static Vert offset(Vert v, float nx, float ny, float nz, float amount) {
        return new Vert(v.x() + nx * amount, v.y() + ny * amount, v.z() + nz * amount, v.u(), v.v());
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, Vert a, Vert b, Vert c,
                             int light, int overlay, float nx, float ny, float nz) {
        vert(consumer, pose, a, light, overlay, nx, ny, nz);
        vert(consumer, pose, b, light, overlay, nx, ny, nz);
        vert(consumer, pose, c, light, overlay, nx, ny, nz);
    }

    private static void vert(VertexConsumer consumer, PoseStack.Pose pose, Vert v, int light, int overlay,
                             float nx, float ny, float nz) {
        consumer.addVertex(pose, v.x(), v.y(), v.z())
                .setColor(255, 255, 255, 255)
                .setUv(v.u(), v.v())
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
