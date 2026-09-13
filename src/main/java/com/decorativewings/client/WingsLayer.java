package com.decorativewings.client;

import com.decorativewings.network.WingsSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
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
        if (style == null || style.isEmpty()) return;

        WingAnimator.prune();
        WingAnimator.State anim = WingAnimator.sample(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, style);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        WingMesh mesh = WingMeshProvider.getById(style);
        if (mesh == null) return;

        ResourceLocation texture = mesh.getTexture();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(texture));

        poseStack.pushPose();
        model.body.translateAndRotate(poseStack);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        mesh.render(poseStack, consumer, anim, packedLight, overlay, true);
        mesh.render(poseStack, consumer, anim, packedLight, overlay, false);

        poseStack.popPose();
    }
}
