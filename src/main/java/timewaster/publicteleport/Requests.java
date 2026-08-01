package timewaster.publicteleport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

            if (request.expires() < now) {
                if (sender != null)
                    Messages.sendMessage(sender, "request_timedout_sender", request.receiverName());
                if (receiver != null)
                    Messages.sendMessage(receiver, "request_timedout_receiver", request.senderName());
            }
        }

        pendingRequests.removeIf(request -> request.expires() < now);
    }

    public boolean sendRequest(ServerPlayer sender, ServerPlayer receiver, boolean reverse) {
        long expires = System.currentTimeMillis() + storage.getConfig().requestTimeout();
        String senderName = sender.getName().getString();
        Request oldRequest = getRequest(sender.getUUID());
        Request newRequest = new Request(sender.getUUID(), receiver.getUUID(), senderName,
            receiver.getName().toString(), reverse, expires);

        if (oldRequest != null) {
            Messages.sendMessage(sender, "request_old_exist", oldRequest.receiverName());
            return false;
        }

        pendingRequests.add(newRequest);

        new Messages.Builder()
            .append(reverse ? "request_received_rev" : "request_received", Messages.Type.REQUEST, senderName)
            .button(Messages.getMessage("button_accept"), "/tpaccept " + senderName,
                Messages.getMessage("request_accept", senderName), Messages.Type.SUCCESS)
            .append(" ")
            .button(Messages.getMessage("button_deny"), "/tpdeny " + senderName,
                Messages.getMessage("request_deny", senderName), Messages.Type.ERROR)
            .send(receiver);
        Messages.sendMessage(sender, "request_sent", receiver.getName().getString());

        return true;
    }

    public boolean cancelRequest(ServerPlayer sender) {
        Request request = getRequest(sender.getUUID());

        if (request == null) {
            Messages.sendMessage(sender, "no_requests");
            return false;
        }

        ServerPlayer receiver = getPlayerByOtherPlayer(request.receiver(), sender);
        if (receiver != null) {
            Messages.sendMessage(receiver, "request_cancelled_receiver", sender.getName().getString());
        }
        Messages.sendMessage(sender, "request_cancelled_sender");

        pendingRequests.remove(request);

        return true;
    }

    @SuppressWarnings("unused")
    public boolean acceptRequest(@Nullable ServerPlayer sender, ServerPlayer receiver) {
        Request request = getRequest(sender != null ? sender.getUUID() : null, receiver.getUUID());

        if (request == null) {
            Messages.sendMessage(receiver, "request_no_exist");
            return false;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        if (sender == null) {
            Messages.sendMessage(receiver, "request_sender_no_ingame", request.senderName());
            pendingRequests.remove(request);
            return false;
        }

        Messages.sendMessage(sender, "request_accepted_sender", request.receiverName());
        Messages.sendMessage(receiver, "request_accepted_receiver", request.senderName());

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
            Messages.sendMessage(receiver, "request_no_exist");
            return false;
        }

        if (sender == null) {
            sender = getPlayerByOtherPlayer(request.sender(), receiver);
        }

        if (sender != null) {
            Messages.sendMessage(sender, "request_denied_sender", request.receiverName());
        }
        Messages.sendMessage(receiver, "request_denied_receiver", request.senderName());

        pendingRequests.remove(request);

        return true;
    }

}
