package com.decorativewings.network;

import com.decorativewings.DecorativeWingsMod;
import com.decorativewings.WingType;
import com.decorativewings.attachment.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WingsSyncPayload(int entityId, String style) implements CustomPacketPayload {
    public static final Type<WingsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DecorativeWingsMod.MOD_ID, "wings_sync"));

    public static final StreamCodec<ByteBuf, WingsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WingsSyncPayload::entityId,
            ByteBufCodecs.STRING_UTF8, WingsSyncPayload::style,
            WingsSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WingsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof Player player) {
                player.setData(ModAttachments.WING_STYLE, payload.style() == null ? "" : payload.style());
            }
        });
    }

    public static String getStyle(Player player) {
        String style = player.getData(ModAttachments.WING_STYLE);
        return style == null ? "" : style;
    }

    public static boolean hasWings(Player player) {
        return WingType.isSet(getStyle(player));
    }

    public static void setStyle(Player player, String style) {
        player.setData(ModAttachments.WING_STYLE, style == null ? "" : style);
        syncToTracking(player);
    }

    public static void setWings(Player player, boolean value) {
        setStyle(player, value ? WingType.SPRITE : WingType.NONE);
    }

    public static void syncToTracking(Entity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new WingsSyncPayload(player.getId(), getStyle(player)));
    }

    public static void syncToWatcher(Entity target, Entity watcher) {
        if (!(target instanceof Player player) || !(watcher instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String style = getStyle(player);
        if (!WingType.isSet(style)) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new WingsSyncPayload(player.getId(), style));
    }
}
