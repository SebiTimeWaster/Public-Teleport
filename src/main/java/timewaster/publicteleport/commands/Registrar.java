package timewaster.publicteleport.commands;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Config;

public class Registrar {
    public static enum SuggestionType {
        NONE, HOMES, WARPS, PLAYERS
    }

    public static void registerCommands() {
        Config config = PublicTeleport.storage.getConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (config.enableSpawn()) {
                Spawn.register(dispatcher);
            }

            if (config.enableWarps()) {
                Warps.register(dispatcher);
            }

            if (config.enableHomes()) {
                Homes.register(dispatcher);
            }

            if (config.enableBack()) {
                Back.register(dispatcher);
            }

            if (config.enableTpa() && FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                Tpa.register(dispatcher);
            }
        });
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getPlayer();
    }

    private static CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder, SuggestionType type) {
        ServerPlayer player = getPlayer(context);

        if (type != SuggestionType.NONE) {
            if (type == SuggestionType.HOMES || type == SuggestionType.WARPS) {
                List<String> teleportNames = PublicTeleport.storage.getTeleportNames(player,
                    type == SuggestionType.WARPS);

                if (teleportNames != null) {
                    for (String name : teleportNames) {
                        builder.suggest(name);
                    }
                }
            }

            if (type == SuggestionType.PLAYERS) {
                for (ServerPlayer activePlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(activePlayer.getUUID())) {
                        builder.suggest(activePlayer.getName().getString());
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    public static int contextWrapper(CommandContext<CommandSourceStack> context,
        Function<ServerPlayer, Boolean> callback) {
        ServerPlayer player = getPlayer(context);

        return callback.apply(player) ? 1 : 0;
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> buildArgumentString(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, String, Boolean> callback) {
        return Commands.argument(argName, Objects.requireNonNull(StringArgumentType.word()))
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                String argValue = Objects.requireNonNull(StringArgumentType.getString(context, argName));

                return callback.apply(player, argValue) ? 1 : 0;
            });
    }

    public static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> buildArgumentPlayer(
        @NotNull String argName, SuggestionType suggestionType,
        BiFunction<ServerPlayer, ServerPlayer, Boolean> callback) {
        return Commands.argument(argName, EntityArgument.player())
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                ServerPlayer target = EntityArgument.getPlayer(Objects.requireNonNull(context), argName);

                return callback.apply(player, target) ? 1 : 0;
            });
    }
}
