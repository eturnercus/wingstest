package com.decorativewings;

import com.decorativewings.attachment.ModAttachments;
import com.decorativewings.client.DecorativeWingsClient;
import com.decorativewings.command.WingsCommands;
import com.decorativewings.network.WingsSyncPayload;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(DecorativeWingsMod.MOD_ID)
public class DecorativeWingsMod {
    public static final String MOD_ID = "decorativewings";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DecorativeWingsMod(IEventBus modBus) {
        ModAttachments.REGISTER.register(modBus);
        modBus.addListener(DecorativeWingsMod::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(DecorativeWingsMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DecorativeWingsMod::onStartTracking);
        NeoForge.EVENT_BUS.addListener(DecorativeWingsMod::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(DecorativeWingsMod::onRespawn);
        NeoForge.EVENT_BUS.addListener(DecorativeWingsMod::onChangedDimension);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DecorativeWingsClient.register(modBus);
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(WingsSyncPayload.TYPE, WingsSyncPayload.STREAM_CODEC, WingsSyncPayload::handle);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        WingsCommands.register(event.getDispatcher());
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        WingsSyncPayload.syncToWatcher(event.getTarget(), event.getEntity());
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        WingsSyncPayload.syncToTracking(event.getEntity());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        WingsSyncPayload.syncToTracking(event.getEntity());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        WingsSyncPayload.syncToTracking(event.getEntity());
    }
}
