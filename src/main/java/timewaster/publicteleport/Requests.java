package timewaster.publicteleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.records.Teleport;

public class Requests {
    private final List<Request> pendingRequests = new ArrayList<Request>();
    private final Storage storage;
    private final Teleports teleports;

    private static final record Request(
        UUID sender,
        UUID receiver,
        String senderName,
        String receiverName,
        boolean reverse,
        long expires) {
    }

    public Requests(Storage storage, Teleports teleports) {
        this.storage = storage;
        this.teleports = teleports;
    }

    private static boolean compareRequests(Request request, @Nullable UUID sender, @Nullable UUID receiver) {
        return (sender == null || request.sender().equals(sender))
            && (receiver == null || request.receiver().equals(receiver));
    }

    private boolean compareRequests(Request request, Request otherRequest) {
        return compareRequests(request, otherRequest.sender(), otherRequest.receiver());
    }

    private Request getRequest(UUID sender, UUID receiver) {
        return pendingRequests.stream()
            .filter(request -> compareRequests(request, sender, receiver)).findFirst().orElse(null);
    }

    private Request getRequest(UUID sender) {
        return pendingRequests.stream()
            .filter(request -> compareRequests(request, sender, null)).findFirst().orElse(null);
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

    public void sendRequest(ServerPlayer sender, ServerPlayer receiver, boolean reverse) {
        long expires = System.currentTimeMillis() + storage.getConfig().requestTimeout();
        String senderName = sender.getName().getString();
        Request oldRequest = getRequest(sender.getUUID());
        Request newRequest = new Request(sender.getUUID(), receiver.getUUID(), senderName,
            receiver.getName().toString(), reverse, expires);

        if (oldRequest != null) {
            Messages.sendMessage(sender, "old_request_exist", oldRequest.receiverName());
            return;
        }

        pendingRequests.add(newRequest);

        new Messages.Builder()
            .append(reverse ? "teleport_request_rev" : "teleport_request", Messages.Type.REQUEST, senderName)
            .button(Messages.getMessage("button_accept"), "/tpaccept " + senderName,
                Messages.getMessage("accept_request", senderName), Messages.Type.SUCCESS)
            .append(" ")
            .button(Messages.getMessage("button_deny"), "/tpdeny " + senderName,
                Messages.getMessage("deny_request", senderName), Messages.Type.ERROR)
            .send(receiver);
        Messages.sendMessage(sender, "request_sent", receiver.getName().getString());
    }

    public void cancelRequest(ServerPlayer sender) {
        List<Request> requests = pendingRequests.stream()
            .filter(request -> compareRequests(request, sender.getUUID(), null)).toList();

        if (requests.isEmpty()) {
            Messages.sendMessage(sender, "no_requests");
            return;
        }

        for (Request request : requests) {
            ServerPlayer receiver = sender.level().getServer().getPlayerList().getPlayer(request.receiver());

            if (receiver != null) {
                Messages.sendMessage(receiver, "request_cancelled_receiver", sender.getName().getString());
            }

            pendingRequests.remove(request);
        }

        Messages.sendMessage(sender, "request_cancelled_sender");
    }

    public void acceptRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        Request request;

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
            pendingRequests.remove(request);
            return;
        }

        if (request.reverse()) {
            teleports.teleportPlayer(receiver, Teleport.create(actualSender, actualSender.getName().getString()));

            actualSender
                .sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        } else {
            teleports.teleportPlayer(actualSender, Teleport.create(receiver, receiver.getName().getString()));

            receiver.sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        }

        pendingRequests.remove(request);
    }

    public void denyRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        Request request;

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
            pendingRequests.remove(request);
            return;
        }

        pendingRequests.remove(request);
    }

}
