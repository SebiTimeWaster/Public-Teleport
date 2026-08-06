package timewaster.publicteleport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.records.Teleport;

public class Requests {
    private final List<Request> pendingRequests = new ArrayList<Request>();
    private final Storage storage;
    private final Teleports teleports;

    private static final record Request(
        @NotNull UUID sender,
        @NotNull UUID receiver,
        String senderName,
        String receiverName,
        boolean reverse,
        long expires) {
    }

    public Requests(Storage storage, Teleports teleports) {
        this.storage = storage;
        this.teleports = teleports;
    }

    private Request getRequest(@Nullable UUID sender, @Nullable UUID receiver) {
        return pendingRequests.stream()
            .filter(request -> {
                return (sender == null || request.sender().equals(sender))
                    && (receiver == null || request.receiver().equals(receiver));
            }).findFirst().orElse(null);
    }

    private Request getRequest(UUID sender) {
        return getRequest(sender, null);
    }

    @Nullable
    private ServerPlayer getPlayerByOtherPlayer(@NotNull UUID playerToGet, ServerPlayer otherPlayer) {
        return otherPlayer.level().getServer().getPlayerList().getPlayer(playerToGet);
    }

    public void cleanup(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Request request : pendingRequests) {
            ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
            ServerPlayer receiver = server.getPlayerList().getPlayer(request.receiver());

            if (request.expires() <= now) {
                if (sender != null) {
                    Messages.sendMessage(sender, "request_timedout_sender", Messages.Type.TEXT, request.receiverName());
                }
                if (receiver != null) {
                    Messages.sendMessage(receiver, "request_timedout_receiver", Messages.Type.TEXT,
                        request.senderName());
                }
            }
        }

        pendingRequests.removeIf(request -> request.expires() <= now);
    }

    public boolean sendRequest(ServerPlayer sender, ServerPlayer receiver, boolean reverse) {
        Request oldRequest = getRequest(sender.getUUID());
        if (oldRequest != null) {
            Messages.sendMessage(sender, "request_old_exist", Messages.Type.ERROR, oldRequest.receiverName(),
                "/tpcancel");
            return false;
        }
        long expires = System.currentTimeMillis() + storage.getConfig().requestTimeout() * 1000;
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

    public boolean cancelRequest(ServerPlayer sender) {
        Request request = getRequest(sender.getUUID());

        if (request == null) {
            Messages.sendMessage(sender, "request_no_exist", Messages.Type.ERROR);
            return false;
        }

        ServerPlayer receiver = getPlayerByOtherPlayer(request.receiver(), sender);
        if (receiver != null) {
            Messages.sendMessage(receiver, "request_cancelled_receiver", Messages.Type.ERROR,
                sender.getName().getString());
        }
        Messages.sendMessage(sender, "request_cancelled_sender", Messages.Type.ERROR);

        pendingRequests.remove(request);

        return true;
    }

    public boolean acceptRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
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
            teleports.teleportPlayer(sender, Teleport.create(receiver, request.receiverName()));
        } else {
            teleports.teleportPlayer(receiver, Teleport.create(sender, sender.getName().getString()));
        }

        pendingRequests.remove(request);

        return true;
    }

    public boolean denyRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
        Request request = getRequest(sender != null ? sender.getUUID() : null, receiver.getUUID());

        if (request == null) {
            Messages.sendMessage(receiver, "request_no_exist", Messages.Type.ERROR);
            return false;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        if (sender != null) {
            Messages.sendMessage(sender, "request_denied_sender", Messages.Type.ERROR, request.receiverName());
        }
        Messages.sendMessage(receiver, "request_denied_receiver", Messages.Type.ERROR, request.senderName());

        pendingRequests.remove(request);

        return true;
    }

}
