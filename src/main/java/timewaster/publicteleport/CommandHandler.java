package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.commands.Commands;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.permissions.Permissions;

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

    MinecraftServer getServer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    ServerPlayer getPlayer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("You must be a player to use this command."));
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        }
        return player;
    }

    SuggestionProvider<CommandSourceStack> suggestWarps(boolean player) {
        return (context, builder) -> {
            UUID uuid = null;

            if (player) {
                uuid = getPlayer(context.getSource()).getUUID();
            }

            for (String name : fileHandler.getTeleportNames(uuid)) {
                builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }

    SuggestionProvider<CommandSourceStack> suggestPlayers() {
        return (context, builder) -> {
            ServerPlayer sender = getPlayer(context.getSource());

            List<ServerPlayer> players = sender.level().getServer().getPlayerList().getPlayers();

            for (ServerPlayer player : players) {
                if (!sender.getUUID().equals(player.getUUID())) {
                    builder.suggest(player.getName().getString());
                }
            }

            return builder.buildFuture();
        };
    }

    /*
     * blka
     * blka
     * blka
     * blka
     * blka
     * blka
     */

    private static LiteralArgumentBuilder<CommandSourceStack> createCommandLiteral(@NonNull String commandName,
            boolean owner) {
        if (owner) {
            return Commands.literal(commandName).requires(PERMISSIONS_OWNER);
        } else {
            return Commands.literal(commandName);
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> createCommandArgument(@NonNull String paramName,
            @Nullable SuggestionProvider<CommandSourceStack> suggestions) {
        StringArgumentType word = Objects.requireNonNull(StringArgumentType.word());

        if (suggestions != null) {
            return Commands.argument(paramName, word).suggests(suggestions);
        } else {
            return Commands.argument(paramName, word);
        }
    }

    private void registerWithoutParam(CommandDispatcher<CommandSourceStack> dispatcher, @NonNull String commandName,
            boolean owner, Function<ServerPlayer, Boolean> callBack) {
        dispatcher.register(createCommandLiteral(commandName, owner).executes(context -> {
            ServerPlayer player = getPlayer(context.getSource());

            return callBack.apply(player) ? 1 : 0;
        }));
    }

    private void registerWithParam(CommandDispatcher<CommandSourceStack> dispatcher, @NonNull String commandName,
            @NonNull String paramName, boolean owner, @Nullable SuggestionProvider<CommandSourceStack> suggestions,
            BiFunction<ServerPlayer, @NonNull String, Boolean> callBack) {

        dispatcher.register(createCommandLiteral(commandName, owner).then(
                createCommandArgument(paramName, suggestions).executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    String paramValue = Objects.requireNonNull(StringArgumentType.getString(context, paramName));

                    return callBack.apply(player, paramValue) ? 1 : 0;
                })));
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        Config config = fileHandler.getConfig();

        if (config.enableSpawn()) {
            registerWithoutParam(dispatcher, "setspawn", true, player -> {
                ServerLevel world = player.level();
                RespawnData spawn = RespawnData.of(player.level().dimension(), player.blockPosition(), 0, 0);

                fileHandler.setTeleport(null, Teleport.create(player, "spawn"));
                world.setRespawnData(spawn);
                world.getServer().getGameRules().set(GameRules.RESPAWN_RADIUS, 0, world.getServer());
                MessageHandler.sendMessage(player, "spawn_set");

                return true;
            });

            registerWithoutParam(dispatcher, "spawn", false, player -> {
                return (Boolean) teleportHandler.teleportPlayer(player, "spawn", true);
            });
        } else if (config.enableWarps()) {
            registerWithParam(dispatcher, "setwarp", "name", true, null, (player, warpName) -> {
                fileHandler.setTeleport(null, Teleport.create(player, warpName));
                MessageHandler.sendMessage(player, "warp_set", warpName);

                return true;
            });

            registerWithParam(dispatcher, "delwarp", "name", true, suggestWarps(false), (player, warpName) -> {
                boolean success = fileHandler.deleteTeleport(null, warpName);

                if (success) {
                    MessageHandler.sendMessage(player, "warp_deleted", warpName);
                } else {
                    MessageHandler.sendMessage(player, "warp_not_exist", warpName);
                }

                return success;
            });

            registerWithParam(dispatcher, "warp", "name", false, suggestWarps(false), (player, warpName) -> {
                return teleportHandler.teleportPlayer(player, warpName, true);
            });

            registerWithoutParam(dispatcher, "warps", false, player -> {
                MessageHandler.listTeleportNames(player, fileHandler.getTeleportNames(null), true);

                return true;
            });
        } else if (config.enableHomes()) {
            dispatcher.register(Commands.literal("sethome")
                    .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> {
                                ServerPlayer player = getPlayer(context.getSource());
                                String homeName = StringArgumentType.getString(context, "name");
                                if (homeName.equals("back")) {
                                    MessageHandler.sendMessage(player, "reserved_name");
                                    return 0;
                                }
                                fileHandler.setTeleport(player.getUUID(), Teleport.create(player, homeName));
                                player.sendSystemMessage(
                                        Component.literal(String.format("Home %s set!", homeName))
                                                .withStyle(ChatFormatting.AQUA));
                                return 1;
                            }))
                    .executes(context -> {
                        ServerPlayer player = getPlayer(context.getSource());
                        fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "home"));
                        player.sendSystemMessage(Component.literal("Home set!").withStyle(ChatFormatting.AQUA));
                        return 1;
                    }));

            dispatcher.register(Commands.literal("delhome")
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(suggestWarps(true))
                            .executes(context -> {
                                ServerPlayer player = getPlayer(context.getSource());

                                String homeName = StringArgumentType.getString(context, "name");
                                boolean success = fileHandler.deleteTeleport(player.getUUID(), homeName);

                                if (success) {
                                    player.sendSystemMessage(Component.literal("Home '" + homeName + "' deleted!")
                                            .withStyle(ChatFormatting.AQUA));
                                } else {
                                    player.sendSystemMessage(
                                            Component.literal("Home '" + homeName + "' does not exist!")
                                                    .withStyle(ChatFormatting.RED));
                                }

                                return success ? 1 : 0;
                            })));

            dispatcher.register(Commands.literal("home")
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(suggestWarps(true))
                            .executes(context -> {
                                ServerPlayer player = getPlayer(context.getSource());
                                String homeName = StringArgumentType.getString(context, "name");

                                if (homeName.equals("back")) {
                                    MessageHandler.sendMessage(player, "no_teleport");
                                    return 0;
                                }

                                boolean result = teleportHandler.teleportPlayer(player, homeName, false);

                                return result ? 1 : 0;
                            }))
                    .executes(context -> {
                        ServerPlayer player = getPlayer(context.getSource());
                        boolean result = teleportHandler.teleportPlayer(player, "home", false);

                        return result ? 1 : 0;
                    }));

            dispatcher.register(Commands.literal("homes")
                    .executes(context -> {
                        ServerPlayer player = getPlayer(context.getSource());
                        List<String> homes = fileHandler.getTeleportNames(player.getUUID());

                        MessageHandler.listTeleportNames(player, homes, false);

                        return 1;
                    }));
        } else if (config.enableBack()) {
            dispatcher.register(Commands.literal("back")
                    .executes(context -> {
                        ServerPlayer player = getPlayer(context.getSource());
                        boolean result = teleportHandler.teleportPlayer(player, "back", false);

                        return result ? 1 : 0;
                    }));

        } else if (config.enableTpa()) {
            dispatcher.register(Commands.literal("tpa")
                    .then(Commands.argument("target", EntityArgument.player())
                            .suggests(suggestPlayers())
                            .executes(context -> {
                                ServerPlayer sender = getPlayer(context.getSource());
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");

                                if (sender.equals(target)) {
                                    sender.sendSystemMessage(Component.literal("You cannot teleport to yourself!")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }

                                requestHandler.sendTeleportRequest(sender, target, false);
                                return 1;
                            })));

            dispatcher.register(Commands.literal("tpahere")
                    .then(Commands.argument("target", EntityArgument.player())
                            .suggests(suggestPlayers())
                            .executes(context -> {
                                ServerPlayer sender = getPlayer(context.getSource());
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");

                                if (sender.equals(target)) {
                                    sender.sendSystemMessage(Component.literal("You cannot teleport to yourself!")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }

                                requestHandler.sendTeleportRequest(sender, target, true);
                                return 1;
                            })));

            dispatcher.register(Commands.literal("tpcancel")
                    .executes(context -> {
                        ServerPlayer sender = getPlayer(context.getSource());
                        requestHandler.cancelTeleportRequest(sender);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("tpaccept")
                    .executes(context -> {
                        ServerPlayer receiver = getPlayer(context.getSource());
                        requestHandler.acceptTeleportRequest(receiver, null);
                        return 1;
                    })
                    .then(Commands.argument("sender", EntityArgument.player())
                            .suggests(suggestPlayers())
                            .executes(context -> {
                                ServerPlayer receiver = getPlayer(context.getSource());
                                ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                                requestHandler.acceptTeleportRequest(receiver, sender);
                                return 1;
                            })));

            dispatcher.register(Commands.literal("tpdeny")
                    .executes(context -> {
                        ServerPlayer receiver = getPlayer(context.getSource());
                        requestHandler.denyTeleportRequest(receiver, null);
                        return 1;
                    })
                    .then(Commands.argument("sender", EntityArgument.player())
                            .suggests(suggestPlayers())
                            .executes(context -> {
                                ServerPlayer receiver = getPlayer(context.getSource());
                                ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                                requestHandler.denyTeleportRequest(receiver, sender);
                                return 1;
                            })));
        }
    }
}
