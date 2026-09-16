package timewaster.publicteleport;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Manages TPA (player-to-player teleport request) state and behavior.
 */
public final class Requests {
    private static final List<Request> pendingRequests = new ArrayList<Request>();

    public enum RequestType {
        NORMAL, REVERSE, REVERSE_ALL
    }

    private record Request(
        @NotNull UUID sender,
        @NotNull UUID receiver,
        String senderName,
        String receiverName,
        RequestType requestType,
        long expires) {
    }

    private record ResolvedRequest(Request request, @Nullable ServerPlayer sender) {
    }

    private Requests() {
    }

    private static Request getRequest(@Nullable UUID sender, @Nullable UUID receiver) {
        return pendingRequests.stream()
            .filter((request) -> {
                return (sender == null || request.sender().equals(sender))
                    && (receiver == null || request.receiver().equals(receiver));
            }).findFirst().orElse(null);
    }

    private static Request getRequest(UUID sender) {
        return getRequest(sender, null);
    }

    @Nullable
    private static ResolvedRequest resolveRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
        Request request = getRequest(sender != null ? sender.getUUID() : null, receiver.getUUID());

        if (request == null) {
            Messages.sendMessage(receiver, "request_no_exist", ERROR);
            return null;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        return new ResolvedRequest(request, sender);
    }

    @Nullable
    private static ServerPlayer getPlayerByOtherPlayer(@NotNull UUID playerToGet, ServerPlayer otherPlayer) {
        return otherPlayer.level().getServer().getPlayerList().getPlayer(playerToGet);
    }

    private static void createRequest(ServerPlayer sender, ServerPlayer receiver, RequestType requestType) {
        long expires = System.currentTimeMillis() + PublicTeleport.storage.getConfig().requestTimeout() * 1000;
        String senderName = sender.getName().getString();
        String receiverName = receiver.getName().getString();
        String headlineIdentifier = (requestType == RequestType.NORMAL) ? "request_received" : "request_received_rev";
        MutableComponent acceptButtonText = Messages.getMessage("button_accept", SUCCESS);
        MutableComponent acceptHoverText = Messages.getMessage("request_accept", null, senderName);
        MutableComponent denyButtonText = Messages.getMessage("button_deny", ERROR);
        MutableComponent denyHoverText = Messages.getMessage("request_deny", null, senderName);

        pendingRequests
            .add(new Request(sender.getUUID(), receiver.getUUID(), senderName, receiverName, requestType, expires));

        new Messages.MessageBuilder()
            .append(headlineIdentifier, HEADLINE, senderName)
            .button(acceptButtonText, acceptHoverText, "/tpaccept " + senderName)
            .appendRaw("  ")
            .button(denyButtonText, denyHoverText, "/tpdeny " + senderName)
            .send(receiver);
        Messages.sendMessage(sender, "request_sent", SUCCESS, receiverName);
    }

    /**
     * Checks for stale requests and cleans them up once per second.
     *
     * @param server the server object to get involved players
     */
    public static void cleanup(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Request request : pendingRequests) {
            if (request.expires() <= now) {
                ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
                ServerPlayer receiver = server.getPlayerList().getPlayer(request.receiver());

                if (sender != null) {
                    Messages.sendMessage(sender, "request_timedout_sender", WARNING,
                        request.receiverName());
                }
                if (receiver != null) {
                    Messages.sendMessage(receiver, "request_timedout_receiver", WARNING,
                        request.senderName());
                }
            }
        }

        pendingRequests.removeIf((request) -> request.expires() <= now);
    }

    /**
     * Sends a TPA request from one player to another, notifying both players.
     *
     * @param sender      the player initiating the request
     * @param receiver    the player being asked to accept or deny the request or
     *                        {@code null} if {@link RequestType.REVERSE_ALL}
     * @param requestType what type of request is being made
     * @return {@code true} if the request was created and sent
     */
    public static boolean sendRequest(ServerPlayer sender, @Nullable ServerPlayer receiver, RequestType requestType) {
        if (sender == receiver) {
            Messages.sendMessage(sender, "request_teleport_self", WARNING);
            return false;
        }

        Request oldRequest = getRequest(sender.getUUID());
        if (oldRequest != null) {
            Messages.sendMessage(sender, "request_old_exist", ERROR, oldRequest.receiverName(),
                "/tpcancel");
            return false;
        }

        if (requestType == RequestType.REVERSE_ALL) {
            List<ServerPlayer> onlinePlayers = sender.level().getServer().getPlayerList().getPlayers();

            for (ServerPlayer onlinePlayer : onlinePlayers) {
                if (sender != onlinePlayer) {
                    createRequest(sender, onlinePlayer, requestType);
                }
            }
        } else {
            createRequest(sender, receiver, requestType);
        }

        return true;
    }

    /**
     * Cancels all pending TPA requests outgoing from {@code sender}, notifying
     * both players.
     *
     * @param sender the player who originally sent the request
     * @return {@code true} if a pending request was found and removed,
     *         {@code false} if {@code sender} had no pending request
     */
    public static boolean cancelRequest(ServerPlayer sender) {
        List<Request> requests = pendingRequests.stream().filter((request) -> request.sender().equals(sender.getUUID()))
            .toList();

        if (requests.isEmpty()) {
            Messages.sendMessage(sender, "request_no_exist", ERROR);
            return false;
        }

        for (Request request : requests) {
            ServerPlayer receiver = getPlayerByOtherPlayer(request.receiver(), sender);

            if (receiver != null) {
                Messages.sendMessage(receiver, "request_cancelled_receiver", WARNING,
                    sender.getName().getString());
            }
            Messages.sendMessage(sender, "request_cancelled_sender", SUCCESS);

            pendingRequests.remove(request);
        }

        return true;
    }

    /**
     * Accepts a pending TPA request addressed to {@code receiver}, notifies
     * both players, and performs the teleport.
     *
     * @param sender   the player who originally sent the request, or {@code null}
     *                     to find the oldest incoming request {@code receiver} has
     * @param receiver the player accepting the request
     * @return {@code true} if a matching request was found and executed
     */
    public static boolean acceptRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
        ResolvedRequest resolved = resolveRequest(sender, receiver);
        if (resolved == null) {
            return false;
        }
        Request request = resolved.request();
        sender = resolved.sender();

        if (sender == null) {
            Messages.sendMessage(receiver, "request_sender_no_ingame", ERROR,
                request.senderName());
            pendingRequests.remove(request);
            return false;
        }

        Messages.sendMessage(sender, "request_accepted_sender", SUCCESS, request.receiverName());
        Messages.sendMessage(receiver, "request_accepted_receiver", SUCCESS, request.senderName());

        if (request.requestType == RequestType.NORMAL) {
            Teleports.teleportPlayer(sender, receiver, false);
        } else {
            Teleports.teleportPlayer(receiver, sender, request.requestType == RequestType.REVERSE_ALL);
        }

        pendingRequests.remove(request);

        return true;
    }

    /**
     * Denies a pending TPA request addressed to {@code receiver}, notifying
     * both players.
     *
     * @param sender   the player who originally sent the request, or {@code null}
     *                     to find the oldest incoming request {@code receiver} has
     * @param receiver the player denying the request
     * @return {@code true} if a matching request was found and removed
     */
    public static boolean denyRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
        ResolvedRequest resolved = resolveRequest(sender, receiver);
        if (resolved == null) {
            return false;
        }
        Request request = resolved.request();
        sender = resolved.sender();

        if (sender != null) {
            Messages.sendMessage(sender, "request_denied_sender", WARNING, request.receiverName());
        }
        Messages.sendMessage(receiver, "request_denied_receiver", SUCCESS, request.senderName());

        pendingRequests.remove(request);

        return true;
    }

}
