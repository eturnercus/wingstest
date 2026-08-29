package com.decorativewings.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class WingsClientCommands {
    private WingsClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("wingsfp")
                .executes(WingsClientCommands::toggle)
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false))));
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        boolean enabled = WingsClientOptions.toggleFirstPerson();
        context.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "commands.decorativewings.firstperson.on" : "commands.decorativewings.firstperson.off"), false);
        return enabled ? 1 : 0;
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean value) {
        WingsClientOptions.setFirstPerson(value);
        context.getSource().sendSuccess(() -> Component.translatable(
                value ? "commands.decorativewings.firstperson.on" : "commands.decorativewings.firstperson.off"), false);
        return value ? 1 : 0;
    }
}
