package timewaster.publicteleport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.records.Teleport;

/**
 * Manages TPA (player-to-player teleport request) state and behavior.
 */
public class Requests {
    private static final List<Request> pendingRequests = new ArrayList<Request>();
    private static int tickCounter = 0;

    private static final record Request(
        @NotNull UUID sender,
        @NotNull UUID receiver,
        String senderName,
        String receiverName,
        boolean reverse,
        long expires) {
    }

    private static Request getRequest(@Nullable UUID sender, @Nullable UUID receiver) {
        return pendingRequests.stream()
            .filter(request -> {
                return (sender == null || request.sender().equals(sender))
                    && (receiver == null || request.receiver().equals(receiver));
            }).findFirst().orElse(null);
    }

    private static Request getRequest(UUID sender) {
        return getRequest(sender, null);
    }

    @Nullable
    private static ServerPlayer getPlayerByOtherPlayer(@NotNull UUID playerToGet, ServerPlayer otherPlayer) {
        return otherPlayer.level().getServer().getPlayerList().getPlayer(playerToGet);
    }

    private static void cleanup(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Request request : pendingRequests) {
            ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
            ServerPlayer receiver = server.getPlayerList().getPlayer(request.receiver());

            if (request.expires() <= now) {
                if (sender != null) {
                    Messages.sendMessage(sender, "request_timedout_sender", Messages.Type.WARNING,
                        request.receiverName());
                }
                if (receiver != null) {
                    Messages.sendMessage(receiver, "request_timedout_receiver", Messages.Type.WARNING,
                        request.senderName());
                }
            }
        }

        pendingRequests.removeIf(request -> request.expires() <= now);
    }

    /**
     * Registers a periodic server-tick listener that removes stale pending TPA
     * requests.
     */
    public static void registerTickEvent() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // 20 ticks = 1 second
            if (tickCounter >= 20) {
                tickCounter = 0;

                cleanup(server);
            }
        });
    }

    /**
     * Sends a TPA request from one player to another, notifying both players.
     *
     * @param sender   the player initiating the request
     * @param receiver the player being asked to accept or deny the request
     * @param reverse  if {@code true}, accepting teleports {@code receiver} to
     *                     {@code sender} instead of the normal direction
     * @return {@code true} if the request was created and sent, {@code false}
     *         if {@code sender} already had a pending request
     */
    public static boolean sendRequest(ServerPlayer sender, ServerPlayer receiver, boolean reverse) {
        Request oldRequest = getRequest(sender.getUUID());
        if (oldRequest != null) {
            Messages.sendMessage(sender, "request_old_exist", Messages.Type.ERROR, oldRequest.receiverName(),
                "/tpcancel");
            return false;
        }
        long expires = System.currentTimeMillis() + PublicTeleport.storage.getConfig().requestTimeout() * 1000;
        String senderName = sender.getName().getString();
        String receiverName = receiver.getName().getString();
        String headlineIdentifier = reverse ? "request_received_rev" : "request_received";
        MutableComponent acceptButtonText = Messages.getMessage("button_accept", Messages.Type.SUCCESS);
        MutableComponent acceptHoverText = Messages.getMessage("request_accept", null, senderName);
        MutableComponent denyButtonText = Messages.getMessage("button_deny", Messages.Type.ERROR);
        MutableComponent denyHoverText = Messages.getMessage("request_deny", null, senderName);

        pendingRequests
            .add(new Request(sender.getUUID(), receiver.getUUID(), senderName, receiverName, reverse, expires));

        new Messages.MessageBuilder()
            .append(headlineIdentifier, Messages.Type.REQUEST, senderName)
            .button(acceptButtonText, acceptHoverText, "/tpaccept " + senderName)
            .append("  ")
            .button(denyButtonText, denyHoverText, "/tpdeny " + senderName)
            .send(receiver);
        Messages.sendMessage(sender, "request_sent", Messages.Type.SUCCESS, receiverName);

        return true;
    }

    /**
     * Cancels the pending TPA request outgoing from {@code sender}, notifying
     * both players.
     *
     * @param sender the player who originally sent the request
     * @return {@code true} if a pending request was found and removed,
     *         {@code false} if {@code sender} had no pending request
     */
    public static boolean cancelRequest(ServerPlayer sender) {
        Request request = getRequest(sender.getUUID());

        if (request == null) {
            Messages.sendMessage(sender, "request_no_exist", Messages.Type.ERROR);
            return false;
        }

        ServerPlayer receiver = getPlayerByOtherPlayer(request.receiver(), sender);
        if (receiver != null) {
            Messages.sendMessage(receiver, "request_cancelled_receiver", Messages.Type.WARNING,
                sender.getName().getString());
        }
        Messages.sendMessage(sender, "request_cancelled_sender", Messages.Type.SUCCESS);

        pendingRequests.remove(request);

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
        Request request = getRequest(sender != null ? sender.getUUID() : null, receiver.getUUID());

        if (request == null) {
            Messages.sendMessage(receiver, "request_no_exist", Messages.Type.ERROR);
            return false;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        if (sender == null) {
            Messages.sendMessage(receiver, "request_sender_no_ingame", Messages.Type.ERROR, request.senderName());
            pendingRequests.remove(request);
            return false;
        }

        Messages.sendMessage(sender, "request_accepted_sender", Messages.Type.SUCCESS, request.receiverName());
        Messages.sendMessage(receiver, "request_accepted_receiver", Messages.Type.SUCCESS, request.senderName());

        if (!request.reverse()) {
            Teleports.teleportPlayer(sender, Teleport.create(receiver, request.receiverName()));
        } else {
            Teleports.teleportPlayer(receiver, Teleport.create(sender, sender.getName().getString()));
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
        Request request = getRequest(sender != null ? sender.getUUID() : null, receiver.getUUID());

        if (request == null) {
            Messages.sendMessage(receiver, "request_no_exist", Messages.Type.ERROR);
            return false;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        if (sender != null) {
            Messages.sendMessage(sender, "request_denied_sender", Messages.Type.WARNING, request.receiverName());
        }
        Messages.sendMessage(receiver, "request_denied_receiver", Messages.Type.SUCCESS, request.senderName());

        pendingRequests.remove(request);

        return true;
    }

}
