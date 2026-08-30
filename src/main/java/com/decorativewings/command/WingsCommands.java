package com.decorativewings.command;

import com.decorativewings.client.WingFbxMesh;
import com.decorativewings.client.WingVoxelMesh;
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

import java.util.List;

public final class WingsCommands {
    // Теперь подсказки генерируются динамически из списка загруженных JSON
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS =
            (context, builder) -> {
                List<String> availableWings = WingVoxelMesh.getAvailableWingIds();
                availableWings.addAll(WingFbxMesh.getAvailableWingIds());
                return SharedSuggestionProvider.suggest(availableWings, builder);
            };

    private WingsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wings")
                .requires(source -> source.hasPermission(2))
                .executes(WingsCommands::usage)
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> give(ctx, "wing.png")) // По умолчанию даем базовые крылья
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
                        .executes(ctx -> give(ctx, "wing.png"))
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

        // Проверяем, существует ли такой ID в наших загруженных определениях
        if (!WingVoxelMesh.getAvailableWingIds().contains(raw) && !WingFbxMesh.getAvailableWingIds().contains(raw)) {
            context.getSource().sendFailure(Component.translatable("commands.decorativewings.unknown", raw));
            return 0;
        }
        return give(context, raw);
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
        // Устанавливаем пустую строку вместо WingType.NONE
        WingsSyncPayload.setStyle(player, "");
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.decorativewings.remove", player.getGameProfile().getName()), true);
        return 1;
    }
}