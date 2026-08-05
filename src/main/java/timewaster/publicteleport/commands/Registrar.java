package timewaster.publicteleport.commands;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
import timewaster.publicteleport.Requests;
import timewaster.publicteleport.Storage;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Config;

public class Registrar {
    private final Storage storage;
    private final Requests requests;
    private final Teleports teleports;

    public static enum SuggestionType {
        NONE, HOMES, WARPS, PLAYERS
    }

    public Registrar(Storage storage, Requests requests, Teleports teleports) {
        this.storage = storage;
        this.requests = requests;
        this.teleports = teleports;

        register();
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getPlayer();
    }

    private CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder, SuggestionType type) {
        ServerPlayer player = getPlayer(context);

        if (type != SuggestionType.NONE) {
            if (type == SuggestionType.HOMES || type == SuggestionType.WARPS) {
                List<String> teleportNames = storage.getTeleportNames(player, type == SuggestionType.WARPS);

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

    private void register() {
        Config config = storage.getConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (config.enableSpawn())
                new Spawn(dispatcher, storage, teleports);

            if (config.enableWarps())
                new Warps(dispatcher, this, storage, teleports);

            if (config.enableHomes())
                new Homes(dispatcher, this, storage, teleports);

            if (config.enableBack())
                new Back(dispatcher, teleports);

            if (config.enableTpa() && FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER)
                new Tpa(dispatcher, this, requests);
        });
    }

    public static int contextWrapper(CommandContext<CommandSourceStack> context,
        Function<ServerPlayer, Boolean> callback) {
        ServerPlayer player = getPlayer(context);

        return callback.apply(player) ? 1 : 0;
    }

    public RequiredArgumentBuilder<CommandSourceStack, String> buildArgumentString(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, String, Boolean> callback) {
        return Commands.argument(argName, Objects.requireNonNull(StringArgumentType.word()))
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                String argValue = Objects.requireNonNull(StringArgumentType.getString(context, argName));

                return callback.apply(player, argValue) ? 1 : 0;
            });
    }

    public RequiredArgumentBuilder<CommandSourceStack, EntitySelector> buildArgumentPlayer(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, ServerPlayer, Boolean> callback) {
        return Commands.argument(argName, EntityArgument.player())
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                ServerPlayer target = EntityArgument.getPlayer(Objects.requireNonNull(context), argName);

                return callback.apply(player, target) ? 1 : 0;
            });
    }
}
