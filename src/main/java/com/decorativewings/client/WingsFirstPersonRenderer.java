package com.decorativewings.client;

import com.decorativewings.network.WingsSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class WingsFirstPersonRenderer {
    private WingsFirstPersonRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!WingsClientOptions.showFirstPerson()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || player.isInvisible() || !WingsSyncPayload.hasWings(player)) {
            return;
        }
        if (minecraft.getCameraEntity() != player) {
            return;
        }

        DeltaTracker timer = event.getPartialTick();
        float partialTick = timer.getGameTimeDeltaPartialTick(false);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        if (!(dispatcher.getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }

        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double y = Mth.lerp(partialTick, player.yo, player.getY());
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        var camera = event.getCamera().getPosition();

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float age = player.tickCount + partialTick;
        float limbSwingAmount = player.isAlive() ? Math.min(1.0F, player.walkAnimation.speed(partialTick)) : 0.0F;
        float limbSwing = player.isAlive() ? player.walkAnimation.position(partialTick) : 0.0F;
        int light = dispatcher.getPackedLightCoords(player, partialTick);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        var model = renderer.getModel();
        model.young = player.isBaby();
        model.crouching = player.isCrouching();
        model.riding = player.isPassenger();
        model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(player, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        WingsLayer.renderOnBody(poseStack, buffer, light, player, model, limbSwing, limbSwingAmount, partialTick, age, netHeadYaw, headPitch);
        buffer.endBatch();
        poseStack.popPose();
    }
}
