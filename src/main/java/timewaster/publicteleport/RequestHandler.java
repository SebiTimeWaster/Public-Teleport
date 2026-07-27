package timewaster.publicteleport;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

public class RequestHandler {
    private FileHandler fileHandler;
    private static final long REQUEST_TIMEOUT_MS = 60_000; // 60 seconds
    private final List<Request> pendingRequests = new CopyOnWriteArrayList<>();

    public RequestHandler(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    void addRequest(Request request) {
        // remove duplicate pairs
        pendingRequests.removeIf(r -> r.sender().equals(request.sender()) && r.receiver().equals(request.receiver()));
        pendingRequests.add(request);
    }

    void removeRequest(Request request) {
        pendingRequests.remove(request);
    }

    Request getMostRecentRequest(UUID receiver) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver))
                .max(Comparator.comparingLong(Request::expiry)).orElse(null);
    }

    Request getRequest(UUID receiver, UUID sender) {
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
        Request request = new Request(sender.getUUID(), receiver.getUUID(), here, expiry);
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

        List<Request> requests = pendingRequests.stream().filter(r -> r.sender().equals(sender.getUUID()))
                .toList();

        if (requests.isEmpty()) {
            sender.sendSystemMessage(
                    Component.literal("You have no pending teleport requests.").withStyle(ChatFormatting.RED));
            return;
        }

        for (Request request : requests) {
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
            removeRequest(request);
            return;
        }

        removeRequest(request);
    }

}
