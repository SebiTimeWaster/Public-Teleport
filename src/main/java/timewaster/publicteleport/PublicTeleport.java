package timewaster.publicteleport;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;

public class PublicTeleport implements ModInitializer {

    static final String MOD_ID = "public-teleport";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public FileHandler fileHandler = new FileHandler(MOD_ID, LOGGER);

    static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
            .hasPermission(Permissions.COMMANDS_OWNER);

    static final long REQUEST_TIMEOUT_MS = 60_000; // 60 seconds

    record TeleportRequest(UUID sender, UUID receiver, boolean here, long expiry) {

    }

    final List<TeleportRequest> pendingRequests = new CopyOnWriteArrayList<>();

    // ------ REQUESTS
    // -------------------------------------------------------------------------------------------
    void addRequest(TeleportRequest request) {
        // remove duplicate pairs
        pendingRequests.removeIf(r -> r.sender().equals(request.sender()) && r.receiver().equals(request.receiver()));
        pendingRequests.add(request);
    }

    void removeRequest(TeleportRequest request) {
        pendingRequests.remove(request);
    }

    TeleportRequest getMostRecentRequest(UUID receiver) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver))
                .max(Comparator.comparingLong(TeleportRequest::expiry)).orElse(null);
    }

    TeleportRequest getRequest(UUID receiver, UUID sender) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver) && r.sender().equals(sender))
                .findFirst().orElse(null);
    }

    void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        pendingRequests.removeIf(r -> r.expiry() < now);
    }

    void sendTeleportRequest(ServerPlayer sender, ServerPlayer receiver, boolean here) {
        cleanupExpiredRequests();

        long expiry = System.currentTimeMillis() + REQUEST_TIMEOUT_MS;
        TeleportRequest request = new TeleportRequest(sender.getUUID(), receiver.getUUID(), here, expiry);
        addRequest(request);

        Component message = Component.literal(
                String.format("%s wants to teleport %s. ", sender.getName().getString(),
                        here ? "you to them" : "to you"))
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[Accept]").withStyle(ChatFormatting.GREEN).withStyle(
                        style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/tpaccept " + sender.getName().getString()))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal(
                                                "Accept teleport request from " + sender.getName().getString())))))
                .append(Component.literal(" "))
                .append(Component.literal("[Deny]").withStyle(ChatFormatting.RED).withStyle(
                        style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/tpdeny " + sender.getName().getString()))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal(
                                                "Deny teleport request from " + sender.getName().getString())))));

        receiver.sendSystemMessage(message);
        sender.sendSystemMessage(Component.literal("Teleport request sent to " + receiver.getName().getString())
                .withStyle(ChatFormatting.AQUA));
    }

    void cancelTeleportRequest(ServerPlayer sender) {
        cleanupExpiredRequests();

        List<TeleportRequest> requests = pendingRequests.stream().filter(r -> r.sender().equals(sender.getUUID()))
                .toList();

        if (requests.isEmpty()) {
            sender.sendSystemMessage(
                    Component.literal("You have no pending teleport requests.").withStyle(ChatFormatting.RED));
            return;
        }

        for (TeleportRequest request : requests) {
            ServerPlayer receiver = sender.level().getServer().getPlayerList().getPlayer(request.receiver());

            if (receiver != null) {
                receiver.sendSystemMessage(
                        Component.literal(sender.getName().getString() + " cancelled their teleport request.")
                                .withStyle(ChatFormatting.YELLOW));
            }

            removeRequest(request);
        }

        sender.sendSystemMessage(Component.literal("Teleport request cancelled.").withStyle(ChatFormatting.YELLOW));
    }

    void acceptTeleportRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUUID(), sender.getUUID());
        } else {
            request = getMostRecentRequest(receiver.getUUID());
        }

        if (request == null) {
            receiver.sendSystemMessage(
                    Component.literal("Teleport request expired or doesn't exist.").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayer actualSender = receiver.level().getServer().getPlayerList().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendSystemMessage(
                    Component.literal("Request sender is no longer online.").withStyle(ChatFormatting.RED));
            removeRequest(request);
            return;
        }

        if (request.here()) {
            fileHandler.setTeleport(receiver.getUUID(), Teleport.create(receiver, "back"));
            TeleportHandler.teleport(receiver, Teleport.create(actualSender, actualSender.getName().getString()));

            actualSender
                    .sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        } else {
            fileHandler.setTeleport(actualSender.getUUID(), Teleport.create(actualSender, "back"));
            TeleportHandler.teleport(actualSender, Teleport.create(receiver, receiver.getName().getString()));

            receiver.sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        }

        removeRequest(request);
    }

    void denyTeleportRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUUID(), sender.getUUID());
        } else {
            request = getMostRecentRequest(receiver.getUUID());
        }

        if (request == null) {
            receiver.sendSystemMessage(
                    Component.literal("Teleport request expired or doesn't exist.").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayer actualSender = receiver.level().getServer().getPlayerList().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendSystemMessage(
                    Component.literal("Request sender is no longer online.").withStyle(ChatFormatting.RED));
            removeRequest(request);
            return;
        }

        removeRequest(request);
    }

    // ------ COMMANDS
    // ----------------------------------------------------------------------------------------
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

    void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
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

                            sendTeleportRequest(sender, target, false);
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

                            sendTeleportRequest(sender, target, true);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("tpcancel")
                .executes(context -> {
                    ServerPlayer sender = getPlayer(context.getSource());
                    cancelTeleportRequest(sender);
                    return 1;
                }));

        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> {
                    ServerPlayer receiver = getPlayer(context.getSource());
                    acceptTeleportRequest(receiver, null);
                    return 1;
                })
                .then(Commands.argument("sender", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer receiver = getPlayer(context.getSource());
                            ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                            acceptTeleportRequest(receiver, sender);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> {
                    ServerPlayer receiver = getPlayer(context.getSource());
                    denyTeleportRequest(receiver, null);
                    return 1;
                })
                .then(Commands.argument("sender", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer receiver = getPlayer(context.getSource());
                            ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                            denyTeleportRequest(receiver, sender);
                            return 1;
                        })));
    }

    // ------ INITIALIZE
    // ----------------------------------------------------------------------------------
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "back"));

            }
        });

        LOGGER.info("Initialized!");
    }

    // -----------------------------------------------------------------------------------------------------------
}
