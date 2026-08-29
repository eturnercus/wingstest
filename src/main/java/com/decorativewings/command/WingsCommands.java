package com.decorativewings.command;

import com.decorativewings.WingType;
import com.decorativewings.network.WingsSyncPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WingsCommands {
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(new String[]{
                    "sprite", "model", "insect", "bird",
                    "sprite_v1", "model_v1", "insect_v1", "bird_v1"
            }, builder);

    private WingsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wings")
                .requires(source -> source.hasPermission(2))
                .executes(WingsCommands::usage)
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> give(ctx, WingType.SPRITE))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(TYPE_SUGGESTIONS)
                                        .executes(WingsCommands::giveTyped))))
                .then(Commands.literal("type")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(TYPE_SUGGESTIONS)
                                        .executes(WingsCommands::giveTyped))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(WingsCommands::remove)))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> give(ctx, WingType.SPRITE))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGESTIONS)
                                .executes(WingsCommands::giveTyped))));
    }

    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("commands.decorativewings.usage"), false);
        return 0;
    }

    private static int giveTyped(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "type");
        String style = WingType.normalize(raw);
        if (style == null || style.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.decorativewings.unknown", raw));
            return 0;
        }
        return give(context, style);
    }

    private static int give(CommandContext<CommandSourceStack> context, String style) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String current = WingsSyncPayload.getStyle(player);
        if (style.equals(current)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.decorativewings.already", player.getGameProfile().getName(), style));
            return 0;
        }
        WingsSyncPayload.setStyle(player, style);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.decorativewings.give", player.getGameProfile().getName(), style), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        if (!WingsSyncPayload.hasWings(player)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.decorativewings.none", player.getGameProfile().getName()));
            return 0;
        }
        WingsSyncPayload.setStyle(player, WingType.NONE);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.decorativewings.remove", player.getGameProfile().getName()), true);
        return 1;
    }
}
