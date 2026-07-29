package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData.RespawnData;

public class CommandHandler {
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
        .hasPermission(Permissions.COMMANDS_OWNER);
    private FileHandler fileHandler;
    private RequestHandler requestHandler;
    private TeleportHandler teleportHandler;

    public CommandHandler(FileHandler fileHandler, RequestHandler requestHandler, TeleportHandler teleportHandler) {
        this.fileHandler = fileHandler;
        this.requestHandler = requestHandler;
        this.teleportHandler = teleportHandler;
    }

    private static enum SuggestionType {
        NONE, HOMES, WARPS, PLAYERS
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getPlayer();
    }

    private CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder, SuggestionType type) {
        UUID playerUuid = getPlayer(context).getUUID();

        if (type != SuggestionType.NONE) {
            if (type == SuggestionType.HOMES || type == SuggestionType.WARPS) {
                List<String> teleportNames = fileHandler
                    .getTeleportNames(type == SuggestionType.HOMES ? playerUuid : null);

                if (teleportNames != null) {
                    for (String name : teleportNames) {
                        builder.suggest(name);
                    }
                }
            }

            if (type == SuggestionType.PLAYERS) {
                for (ServerPlayer activePlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
                    if (!playerUuid.equals(activePlayer.getUUID())) {
                        builder.suggest(activePlayer.getName().getString());
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    private int contextWrapper(CommandContext<CommandSourceStack> context, Function<ServerPlayer, Boolean> callback) {
        ServerPlayer player = getPlayer(context);

        return callback.apply(player) ? 1 : 0;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> argString(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, String, Boolean> callback) {
        return Commands.argument(argName, Objects.requireNonNull(StringArgumentType.word()))
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                String argValue = Objects.requireNonNull(StringArgumentType.getString(context, argName));

                return callback.apply(player, argValue) ? 1 : 0;
            });

    }

    private RequiredArgumentBuilder<CommandSourceStack, String> argPlayer(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, ServerPlayer, Boolean> callback) {
        return Commands.argument(argName, Objects.requireNonNull(StringArgumentType.word()))
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                ServerPlayer target = EntityArgument.getPlayer(Objects.requireNonNull(context), argName);

                return callback.apply(player, target) ? 1 : 0;
            });

    }

    public void registerCommands() {
        Config config = fileHandler.getConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (config.enableSpawn()) {

                dispatcher.register(Commands.literal("setspawn").requires(PERMISSIONS_OWNER)
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        ServerLevel world = player.level();
                        RespawnData spawn = RespawnData.of(player.level().dimension(), player.blockPosition(), 0,
                            0);
                        MinecraftServer server = world.getServer();

                        fileHandler.setTeleport(null, Teleport.create(player, "spawn"));
                        world.setRespawnData(spawn);
                        server.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
                        MessageHandler.sendMessage(player, "spawn_set");

                        return true;
                    })));

                dispatcher.register(Commands.literal("spawn")
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        return teleportHandler.teleportPlayer(player, "spawn", true);
                    })));

            } else if (config.enableWarps()) {

                dispatcher.register(Commands.literal("setwarp").requires(PERMISSIONS_OWNER)
                    .then(argString("name", SuggestionType.NONE, (ServerPlayer player, String argValue) -> {
                        fileHandler.setTeleport(null, Teleport.create(player, argValue));

                        MessageHandler.sendMessage(player, "warp_set", argValue);

                        return true;
                    })));

                dispatcher.register(Commands.literal("delwarp").requires(PERMISSIONS_OWNER)
                    .then(argString("name", SuggestionType.WARPS, (ServerPlayer player, String argValue) -> {
                        boolean success = fileHandler.deleteTeleport(null, argValue);

                        MessageHandler.sendMessage(player, success ? "warp_deleted" : "warp_not_exist", argValue);

                        return success;
                    })));

                dispatcher.register(Commands.literal("warp")
                    .then(argString("name", SuggestionType.WARPS, (ServerPlayer player, String argValue) -> {
                        return teleportHandler.teleportPlayer(player, argValue, true);
                    })));

                dispatcher.register(Commands.literal("warps")
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        return MessageHandler.listTeleportNames(player, fileHandler.getTeleportNames(null), true);
                    })));

            } else if (config.enableHomes()) {

                dispatcher.register(Commands.literal("sethome")
                    .then(argString("name", SuggestionType.NONE, (ServerPlayer player, String argValue) -> {
                        if (argValue.equals("back")) {
                            MessageHandler.sendMessage(player, "reserved_name");
                            return false;
                        }

                        fileHandler.setTeleport(player.getUUID(), Teleport.create(player, argValue));
                        MessageHandler.sendMessage(player, "named_home_set", argValue);

                        return true;
                    })).executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "home"));
                        MessageHandler.sendMessage(player, "home_set");

                        return true;
                    })));

                dispatcher.register(Commands.literal("delhome")
                    .then(argString("name", SuggestionType.HOMES, (ServerPlayer player, String argValue) -> {
                        if (argValue.equals("back")) {
                            MessageHandler.sendMessage(player, "reserved_name");
                            return false;
                        }

                        boolean success = fileHandler.deleteTeleport(player.getUUID(), argValue);
                        MessageHandler.sendMessage(player, success ? "home_deleted" : "home_not_exist", argValue);

                        return success;
                    })));

                dispatcher.register(Commands.literal("home")
                    .then(argString("name", SuggestionType.HOMES, (ServerPlayer player, String argValue) -> {
                        if (argValue.equals("back")) {
                            MessageHandler.sendMessage(player, "reserved_name");
                            return false;
                        }

                        return teleportHandler.teleportPlayer(player, argValue, false);
                    })).executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        return teleportHandler.teleportPlayer(player, "home", false);
                    })));

                dispatcher.register(Commands.literal("homes")
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        return MessageHandler.listTeleportNames(player,
                            fileHandler.getTeleportNames(player.getUUID()), false);
                    })));

            } else if (config.enableBack()) {

                dispatcher.register(Commands.literal("back")
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        boolean result = teleportHandler.teleportPlayer(player, "back", false);

                        if (!result) {
                            MessageHandler.sendMessage(player, "home_not_exist", "back");
                            return false;
                        }

                        return result;
                    })));

            } else if (config.enableTpa()) {

                dispatcher.register(Commands.literal("tpa")
                    .then(argPlayer("target", SuggestionType.PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                        if (player.getName().equals(target.getName())) {
                            MessageHandler.sendMessage(player, "no_teleport_self");

                            return false;
                        }

                        requestHandler.sendTeleportRequest(player, target, false);

                        return true;
                    })));

                dispatcher.register(Commands.literal("tpahere")
                    .then(argPlayer("target", SuggestionType.PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                        if (player.getName().equals(target.getName())) {
                            MessageHandler.sendMessage(player, "no_teleport_self");

                            return false;
                        }

                        requestHandler.sendTeleportRequest(player, target, true);

                        return true;
                    })));

                dispatcher.register(Commands.literal("tpcancel")
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        requestHandler.cancelTeleportRequest(player);

                        return true;
                    })));

                dispatcher.register(Commands.literal("tpaccept")
                    .then(argPlayer("sender", SuggestionType.PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                        requestHandler.acceptTeleportRequest(player, target);

                        return true;
                    }))
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        requestHandler.acceptTeleportRequest(player, null);

                        return true;
                    })));

                dispatcher.register(Commands.literal("tpdeny")
                    .then(argPlayer("sender", SuggestionType.PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                        requestHandler.denyTeleportRequest(player, target);

                        return true;
                    }))
                    .executes(context -> contextWrapper(context, (ServerPlayer player) -> {
                        requestHandler.denyTeleportRequest(player, null);

                        return true;
                    })));

            }
        });
    }
}
