package com.decorativewings.client;

import com.decorativewings.WingType;
import com.decorativewings.network.WingsSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

public class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !WingsSyncPayload.hasWings(player)) {
            return;
        }
        renderOnBody(poseStack, buffer, packedLight, player, this.getParentModel(),
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }

    public static void renderOnBody(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                    AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
                                    float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                    float netHeadYaw, float headPitch) {
        String style = WingsSyncPayload.getStyle(player);
        String base = WingType.base(style);
        boolean v1 = WingType.isV1(style);
        WingAnimator.prune();
        WingAnimator.State anim = WingAnimator.sample(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, style);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        poseStack.pushPose();
        model.body.translateAndRotate(poseStack);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        if (WingType.MODEL.equals(base)) {
            WingFbxMesh mesh = WingFbxMesh.get();
            VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(WingFbxMesh.TEXTURE));
            poseStack.translate(0.0F, 1.5F, 3.4F);
            if (v1) {
                WingFbxMesh.renderSidePixel(poseStack, consumer, mesh.left, true, anim, packedLight, overlay);
                WingFbxMesh.renderSidePixel(poseStack, consumer, mesh.right, false, anim, packedLight, overlay);
            } else {
                WingFbxMesh.renderSide(poseStack, consumer, mesh.left, true, anim, packedLight, overlay);
                WingFbxMesh.renderSide(poseStack, consumer, mesh.right, false, anim, packedLight, overlay);
            }
        } else {
            WingVoxelMesh mesh;
            net.minecraft.resources.ResourceLocation texture;
            if (WingType.INSECT.equals(base)) {
                mesh = WingVoxelMesh.insect();
                texture = WingVoxelMesh.TEXTURE_INSECT;
            } else if (WingType.BIRD.equals(base)) {
                mesh = WingVoxelMesh.bird();
                texture = WingVoxelMesh.TEXTURE_BIRD;
            } else {
                mesh = v1 ? WingVoxelMesh.pixels() : WingVoxelMesh.get();
                texture = WingVoxelMesh.TEXTURE;
            }
            if (mesh.left.cubeCount() == 0) {
                poseStack.popPose();
                return;
            }
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            if (v1) {
                renderWingPixel(poseStack, consumer, mesh, mesh.left, true, anim, packedLight, overlay);
                renderWingPixel(poseStack, consumer, mesh, mesh.right, false, anim, packedLight, overlay);
            } else {
                renderWing(poseStack, consumer, mesh, mesh.left, true, anim, packedLight, overlay);
                renderWing(poseStack, consumer, mesh, mesh.right, false, anim, packedLight, overlay);
            }
        }
        poseStack.popPose();
    }

    private static void renderWing(PoseStack poseStack, VertexConsumer consumer, WingVoxelMesh mesh,
                                   WingVoxelMesh.Side side, boolean left, WingAnimator.State anim,
                                   int light, int overlay) {
        float sign = left ? -1.0F : 1.0F;
        float lag = Mth.sin(anim.phase * 0.85F + (left ? 0.9F : 0.0F)) * 7.2F;
        float flutter = Mth.sin(anim.phase * 3.4F + (left ? 1.1F : 0.0F)) * anim.flutter;
        float hingeY = side.height() * 0.16F;
        float outerHingeY = side.height() * 0.22F;
        float spread = Mth.clamp(anim.spread + sign * anim.turn * 0.25F, 8.0F, 42.0F);

        poseStack.pushPose();
        poseStack.translate(sign * 3.2F, 1.5F, 3.4F);
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * (-spread + anim.turn * 0.15F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(anim.fold * 0.45F + anim.pitch * 0.5F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * (anim.sway * 0.35F + anim.bank * 0.7F + anim.dihedral * 0.5F)));
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * anim.twist * 0.12F));
        poseStack.translate(0.0F, -mesh.pivotY, 0.0F);

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * (anim.flap * 0.52F + lag * 0.28F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(anim.pitch * 0.15F));
        poseStack.scale(1.0F + (anim.stretch - 1.0F) * 0.12F, 1.0F + (anim.stretch - 1.0F) * 0.06F, 1.0F);
        renderCubes(poseStack, consumer, mesh, side.inner(), light, overlay);

        poseStack.translate(sign * side.innerWidth(), hingeY, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * (anim.flap * 0.95F + lag * 0.95F + flutter * 0.75F)));
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * (anim.spread * 0.04F + anim.turn * 0.10F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(anim.fold * 0.12F + anim.pitch * 0.2F + anim.curl * 0.25F));
        poseStack.scale(anim.stretch, 1.0F + (anim.stretch - 1.0F) * 0.18F, 1.0F);
        poseStack.translate(-sign * side.innerWidth(), -hingeY, 0.0F);
        renderCubes(poseStack, consumer, mesh, side.mid(), light, overlay);

        poseStack.translate(sign * (side.innerWidth() + side.midWidth()), outerHingeY, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * (anim.flap * 1.65F + lag * 1.7F + flutter * 1.45F)));
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * (anim.twist * 0.5F + anim.bank * 0.18F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(-anim.flap * 0.08F + anim.pitch * 0.12F + anim.curl));
        poseStack.scale(anim.stretch, anim.stretch, 1.0F + (anim.stretch - 1.0F) * 0.12F);
        poseStack.translate(-sign * (side.innerWidth() + side.midWidth()), -outerHingeY, 0.0F);
        renderCubes(poseStack, consumer, mesh, side.outer(), light, overlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderWingPixel(PoseStack poseStack, VertexConsumer consumer, WingVoxelMesh mesh,
                                        WingVoxelMesh.Side side, boolean left, WingAnimator.State anim,
                                        int light, int overlay) {
        float sign = left ? -1.0F : 1.0F;
        float spread = Mth.clamp(anim.spread + sign * anim.turn * 0.25F, 8.0F, 42.0F);
        float span = Math.max(0.5F, side.innerWidth() + side.midWidth() + side.outerWidth());
        float height = Math.max(0.5F, side.height());
        float leftOff = left ? 0.9F : 0.0F;

        poseStack.pushPose();
        poseStack.translate(sign * 3.2F, 1.5F, 3.4F);
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * (-spread + anim.turn * 0.15F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(anim.fold * 0.45F + anim.pitch * 0.5F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * (anim.flap * 0.42F + anim.sway * 0.4F + anim.bank * 0.7F + anim.dihedral * 0.5F)));
        poseStack.translate(0.0F, -mesh.pivotY, 0.0F);

        renderPixelGroup(poseStack, consumer, mesh, side.inner(), sign, span, height, leftOff, anim, light, overlay);
        renderPixelGroup(poseStack, consumer, mesh, side.mid(), sign, span, height, leftOff, anim, light, overlay);
        renderPixelGroup(poseStack, consumer, mesh, side.outer(), sign, span, height, leftOff, anim, light, overlay);
        poseStack.popPose();
    }

    private static void renderPixelGroup(PoseStack poseStack, VertexConsumer consumer, WingVoxelMesh mesh,
                                         java.util.List<WingVoxelMesh.Cube> cubes, float sign, float span, float height,
                                         float leftOff, WingAnimator.State anim, int light, int overlay) {
        for (WingVoxelMesh.Cube cube : cubes) {
            float cx = (cube.x0() + cube.x1()) * 0.5F;
            float cy = (cube.y0() + cube.y1()) * 0.5F;
            float cz = (cube.z0() + cube.z1()) * 0.5F;
            float t = Mth.clamp(Math.abs(cx) / span, 0.0F, 1.0F);
            float c = Mth.clamp(cy / height, 0.0F, 1.0F);
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
            float twist = anim.curl * t + ripple * anim.billow * 0.22F * t + trail * 4.0F + anim.twist * tip * 0.55F;
            float yaw = (anim.turn * 0.12F + wave * 4.0F + harmonic * 2.4F) * tip;
            float lift = wave * anim.wave * 0.055F * t + ripple * anim.billow * 0.04F + shiver * 0.012F * tip;
            poseStack.pushPose();
            poseStack.translate(0.0F, trail * 0.035F * t, lift);
            poseStack.translate(cx, cy, cz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(sign * flap));
            poseStack.mulPose(Axis.XP.rotationDegrees(twist));
            poseStack.mulPose(Axis.YP.rotationDegrees(sign * yaw));
            poseStack.scale(1.0F + harmonic * 0.04F * t, 1.0F + ripple * 0.03F * c, 1.0F);
            poseStack.translate(-cx, -cy, -cz);
            renderCube(poseStack, consumer, mesh, cube, light, overlay);
            poseStack.popPose();
        }
    }

    private static void renderCubes(PoseStack poseStack, VertexConsumer consumer, WingVoxelMesh mesh,
                                    java.util.List<WingVoxelMesh.Cube> cubes, int light, int overlay) {
        for (WingVoxelMesh.Cube cube : cubes) {
            renderCube(poseStack, consumer, mesh, cube, light, overlay);
        }
    }

    private static void renderCube(PoseStack poseStack, VertexConsumer consumer, WingVoxelMesh mesh,
                                   WingVoxelMesh.Cube cube, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        float du = 1.0F / mesh.textureWidth;
        float dv = 1.0F / mesh.textureHeight;
        float x0 = cube.x0();
        float y0 = cube.y0();
        float z0 = cube.z0();
        float x1 = cube.x1();
        float y1 = cube.y1();
        float z1 = cube.z1();
        float u0 = cube.u0();
        float v0 = cube.v0();
        float u1 = cube.u1();
        float v1 = cube.v1();
        float us = Math.min(u0, u1);
        quad(consumer, pose, light, overlay, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, 0.0F, 0.0F, 1.0F);
        quad(consumer, pose, light, overlay, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u1, v0, u0, v1, 0.0F, 0.0F, -1.0F);
        quad(consumer, pose, light, overlay, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, us, v0, us + du, v1, -1.0F, 0.0F, 0.0F);
        quad(consumer, pose, light, overlay, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, us, v0, us + du, v1, 1.0F, 0.0F, 0.0F);
        quad(consumer, pose, light, overlay, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v1, u1, v1 + dv, 0.0F, 1.0F, 0.0F);
        quad(consumer, pose, light, overlay, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v0 + dv, 0.0F, -1.0F, 0.0F);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float u1, float v1, float u2, float v2,
                             float nx, float ny, float nz) {
        vertex(consumer, pose, light, overlay, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(consumer, pose, light, overlay, x2, y2, z2, u2, v1, nx, ny, nz);
        vertex(consumer, pose, light, overlay, x3, y3, z3, u2, v2, nx, ny, nz);
        vertex(consumer, pose, light, overlay, x4, y4, z4, u1, v2, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
