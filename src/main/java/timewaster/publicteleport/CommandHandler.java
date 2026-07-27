package timewaster.publicteleport;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.world.level.storage.LevelData;

import net.minecraft.commands.Commands;
import java.util.function.Predicate;
import net.minecraft.server.permissions.Permissions;

public class CommandHandler {
    private FileHandler fileHandler;
    private RequestHandler requestHandler;
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
            .hasPermission(Permissions.COMMANDS_OWNER);

    public CommandHandler(FileHandler fileHandler, RequestHandler requestHandler) {
        this.fileHandler = fileHandler;
        this.requestHandler = requestHandler;
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

    private boolean teleportPlayer(ServerPlayer player, String target) {
        Teleport back = Teleport.create(player, "back");
        boolean result = TeleportHandler.teleport(player, fileHandler.getTeleport(player.getUUID(), target));

        if (result && target != "back") {
            fileHandler.setTeleport(player.getUUID(), back);
        }

        return result;
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sethome").then(
                        Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = getPlayer(context.getSource());
                                    String homeName = StringArgumentType.getString(context, "name");

                                    if (homeName.equals("back")) {
                                        MessageHandler.sendMessage(player, "reserved_name");
                                        return 0;
                                    }

                                    fileHandler.setTeleport(player.getUUID(), Teleport.create(player, homeName));

                                    player.sendSystemMessage(Component.literal(String.format("Home %s set!", homeName))
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
                                player.sendSystemMessage(Component.literal("Home '" + homeName + "' does not exist!")
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

                            boolean result = teleportPlayer(player, homeName);

                            return result ? 1 : 0;
                        }))
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    boolean result = teleportPlayer(player, "home");

                    return result ? 1 : 0;
                }));

        dispatcher.register(Commands.literal("homes")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    List<String> homes = fileHandler.getTeleportNames(player.getUUID());

                    MessageHandler.listTeleportNames(player, homes, false);

                    return 1;
                }));

        dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    boolean result = teleportPlayer(player, "back");

                    return result ? 1 : 0;
                }));

        dispatcher.register(Commands.literal("setwarp")
                .requires(PERMISSIONS_OWNER)
                .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    String warpName = StringArgumentType.getString(context, "name");

                    fileHandler.setTeleport(null, Teleport.create(player, warpName));

                    player.sendSystemMessage(
                            Component.literal(String.format("Warp %s set!", warpName)).withStyle(ChatFormatting.AQUA));
                    return 1;
                })));

        dispatcher.register(Commands.literal("delwarp")
                .requires(PERMISSIONS_OWNER)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(false))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());
                            String warpName = StringArgumentType.getString(context, "name");
                            boolean success = fileHandler.deleteTeleport(null, warpName);

                            if (success) {
                                player.sendSystemMessage(Component.literal("Warp '" + warpName + "' deleted!")
                                        .withStyle(ChatFormatting.AQUA));
                            } else {
                                player.sendSystemMessage(Component.literal("Warp '" + warpName + "' does not exist!")
                                        .withStyle(ChatFormatting.RED));
                            }

                            return success ? 1 : 0;
                        })));

        dispatcher.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(false))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());
                            String warpName = StringArgumentType.getString(context, "name");
                            boolean result = teleportPlayer(player, warpName);

                            return result ? 1 : 0;
                        })));

        dispatcher.register(Commands.literal("warps")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    List<String> warps = fileHandler.getTeleportNames(null);

                    MessageHandler.listTeleportNames(player, warps, true);

                    return 1;
                }));

        dispatcher.register(Commands.literal("setspawn")
                .requires(PERMISSIONS_OWNER)
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());

                    fileHandler.setTeleport(null, Teleport.create(player, "spawn"));

                    ServerLevel world = player.level();
                    world.setRespawnData(LevelData.RespawnData.of(
                            player.level().dimension(),
                            player.blockPosition(),
                            0,
                            0));
                    world.getServer().getGameRules().set(GameRules.RESPAWN_RADIUS, 0, world.getServer());

                    player.sendSystemMessage(Component.literal("Spawn set!").withStyle(ChatFormatting.AQUA));
                    return 1;
                }));

        dispatcher.register(Commands.literal("spawn")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    boolean result = teleportPlayer(player, "spawn");

                    return result ? 1 : 0;
                }));

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
