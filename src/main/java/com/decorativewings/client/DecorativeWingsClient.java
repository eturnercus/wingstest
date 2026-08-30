package com.decorativewings.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class DecorativeWingsClient {
    private DecorativeWingsClient() {
    }

    public static void register(IEventBus modBus) {
        WingsClientOptions.load();
        modBus.addListener(DecorativeWingsClient::addLayers);
        modBus.addListener(DecorativeWingsClient::onReload);
        NeoForge.EVENT_BUS.addListener(WingsClientCommands::register);
        NeoForge.EVENT_BUS.addListener(WingsFirstPersonRenderer::onRenderLevel);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new WingsLayer(playerRenderer));
            }
        }
    }

    private static void onReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            com.decorativewings.client.AnimationParser.load();
            WingVoxelMesh.invalidate();
            WingVoxelMesh.loadDefinitions();
            WingFbxMesh.invalidate();
            WingFbxMesh.loadDefinitions();
            WingsTextureManager.invalidate(); // Очищаем текстуры в GPU
        });
    }
}
