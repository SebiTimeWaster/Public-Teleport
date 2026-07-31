package timewaster.publicteleport;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class Messages {
    public static enum Type {
        TEXT, SUCCESS, ERROR, BUTTON, REQUEST
    }

    @NotNull
    private static MutableComponent createText(String text, Type type) {
        if (text == null) {
            return Component.literal("Unknown Error").withStyle(ChatFormatting.RED);
        }

        ChatFormatting color = switch (type) {
            case TEXT -> null;
            case SUCCESS -> ChatFormatting.AQUA;
            case ERROR -> ChatFormatting.RED;
            case BUTTON -> ChatFormatting.GOLD;
            case REQUEST -> ChatFormatting.YELLOW;
            default -> null;
        };

        if (color != null) {
            return Component.literal(text).withStyle(color);
        } else {
            return Component.literal(text);
        }
    }

    private static void sendMessage(ServerPlayer player, @NotNull Component message) {
        player.sendSystemMessage(message);
    }

    public static class Builder {
        @NotNull
        private MutableComponent message = Component.literal("");

        public Builder append(String identifier, Type type, Object... params) {
            message.append(createText(getMessage(identifier, params), type));

            return this;
        }

        public Builder append(String identifier) {
            append(identifier, Type.TEXT);

            return this;
        }

        public Builder append(String identifier, Object... params) {
            append(identifier, Type.TEXT, params);

            return this;
        }

        public Builder button(String text, @NotNull String command, @NotNull String hoverText, Type type) {
            if (type == null)
                type = Type.BUTTON;

            message.append(createText(text, type).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText)))));

            return this;
        }

        public Builder button(String text, @NotNull String command, @NotNull String hoverText) {
            button(text, command, hoverText, Type.BUTTON);

            return this;
        }

        public void send(ServerPlayer player) {
            sendMessage(player, message);
        }
    }

    public static void sendMessage(ServerPlayer player, String identifier, Object... params) {
        Component message = switch (identifier) {
            case "unknown_error" -> createText("Unknown error!", Type.ERROR);
            case "reserved_name" -> createText("The name 'back' cannot be used!", Type.ERROR);
            case "teleported_to" -> createText(String.format("Teleported to %s!", params), Type.SUCCESS);
            case "teleported" -> createText(String.format("Teleported %s!", params), Type.SUCCESS);
            case "spawn_set" -> createText("Spawn set!", Type.SUCCESS);
            case "warp_set" -> createText(String.format("Warp %s set!", params), Type.SUCCESS);
            case "warp_deleted" -> createText(String.format("Warp '%s' deleted!", params), Type.SUCCESS);
            case "warp_not_exist" -> createText(String.format("Warp '%s' does not exist!", params), Type.ERROR);
            case "named_home_set" -> createText(String.format("Home %s set!", params), Type.SUCCESS);
            case "home_set" -> createText("Home set!", Type.SUCCESS);
            case "home_deleted" -> createText(String.format("Home '%s' deleted!", params), Type.SUCCESS);
            case "home_not_exist" -> createText(String.format("Home '%s' does not exist!", params), Type.ERROR);
            case "request_cancelled_sender" -> createText("Teleport request cancelled.", Type.ERROR);
            case "request_cancelled_receiver" ->
                createText(String.format("%s cancelled their teleport request.", params), Type.ERROR);
            case "no_teleport_self" -> createText("You cannot teleport to yourself!", Type.ERROR);
            case "no_warps" -> createText("There are no warps.", Type.ERROR);
            case "no_homes" -> createText("You have no homes.", Type.ERROR);
            case "no_requests" -> createText("You have no pending teleport requests.", Type.ERROR);
            case "request_timedout_sender" ->
                createText(String.format("The teleport request to %s has timed out!", params), Type.ERROR);
            case "request_timedout_receiver" ->
                createText(String.format("The teleport request from %s has timed out!", params), Type.ERROR);
            case "request_sent" -> createText(String.format("Teleport request sent to %s!", params), Type.SUCCESS);
            case "old_request_exist" -> createText(String
                .format("You have a running teleport request to %s, please cancel it with '/tpcancel' first!", params),
                Type.ERROR);
            default -> createText("Unknown Error", Type.ERROR);
        };

        sendMessage(player, message);
    }

    @NotNull
    public static String getMessage(String identifier, Object... params) {
        String message = switch (identifier) {
            case "teleport_to" -> String.format("Teleport to ", params);
            case "warps" -> "Warps:";
            case "homes" -> "Homes:";
            case "line" -> "\n  ";
            case "init" -> "Initialized!";
            case "err_create_dir" -> "Failed to create config directories!";
            case "err_load_config" -> "Failed to load config from {}:";
            case "err_load_file" -> "Failed to load from {}:";
            case "err_save_file" -> "Failed to save to {}:";
            case "button_accept" -> "[Accept]";
            case "button_deny" -> "[Deny]";
            case " " -> "  ";
            case "teleport_request" -> String.format("%s wants to teleport to you.\n", params);
            case "teleport_request_rev" -> String.format("%s wants to teleport you to them.\n", params);
            case "accept_request" -> String.format("Accept teleport request from %s", params);
            case "deny_request" -> String.format("Deny teleport request from %s", params);
            default -> "Unknown Error";
        };

        return Objects.requireNonNull(message);
    }
}
