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
            case SUCCESS -> ChatFormatting.GREEN;
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

    @NotNull
    private static Component colorFormat(String text, Type color, Object... params) {
        if (params != null)
            text = String.format(text, params);

        return createText(text, color);
    }

    @NotNull
    private static Component error(String text, Object... params) {
        return colorFormat(text, Type.ERROR, params);
    }

    @NotNull
    private static Component success(String text, Object... params) {
        return colorFormat(text, Type.SUCCESS, params);
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
            case "home_deleted" -> success("Home '%s' deleted!", params);
            case "home_no_exist" -> error("Home '%s' does not exist!", params);
            case "home_reserved_name" -> error("The name 'back' cannot be used!");
            case "home_set_named" -> success("Home %s set!", params);
            case "home_set_max_reached" -> error("Could not set home, your limit of %s homes is full!", params);
            case "home_set" -> success("Home set!");
            case "no_homes" -> error("You have no homes.");
            case "no_requests" -> error("You have no pending teleport requests.");
            case "no_warps" -> error("There are no warps.");
            case "request_accepted_receiver" -> success("The teleport request from %s was accepted!", params);
            case "request_accepted_sender" -> success("The teleport request to %s was accepted!", params);
            case "request_cancelled_receiver" -> error("%s cancelled their teleport request.", params);
            case "request_cancelled_sender" -> error("Teleport request cancelled.");
            case "request_denied_receiver" -> error("The teleport request from %s was denied!", params);
            case "request_denied_sender" -> error("The teleport request to %s was denied!", params);
            case "request_no_exist" -> error("Teleport request doesn't exist.");
            case "request_old_exist" ->
                error("You have a running teleport request to %s, please cancel it with '/tpcancel' first!", params);
            case "request_sender_no_ingame" -> success("%s is no longer in-game, request cancelled!", params);
            case "request_sent" -> success("Teleport request sent to %s!", params);
            case "request_teleport_self" -> error("You cannot teleport to yourself!");
            case "request_timedout_receiver" -> error("The teleport request from %s has timed out!", params);
            case "request_timedout_sender" -> error("The teleport request to %s has timed out!", params);
            case "spawn_set" -> success("Spawn set!");
            case "teleported_to" -> success("Teleported to %s!", params);
            case "teleported" -> success("Teleported %s!", params);
            case "unknown_error" -> error("Unknown error!");
            case "warp_deleted" -> success("Warp '%s' deleted!", params);
            case "warp_no_exist" -> error("Warp '%s' does not exist!", params);
            case "warp_set" -> success("Warp %s set!", params);
            default -> error("Unknown Error");
        };

        sendMessage(player, message);
    }

    @NotNull
    public static String getMessage(String identifier, Object... params) {
        String message = switch (identifier) {
            case " " -> "  ";
            case "button_accept" -> "[Accept]";
            case "button_deny" -> "[Deny]";
            case "button_named" -> String.format("[%s]", params);
            case "err_create_dir" -> "Failed to create config directories!";
            case "err_load_config" -> "Failed to load config from {}:";
            case "err_load_file" -> "Failed to load from {}:";
            case "err_save_file" -> "Failed to save to {}:";
            case "headline_homes" -> "Homes:";
            case "headline_warps" -> "Warps:";
            case "init" -> "Public Teleport initialized!";
            case "line" -> "\n  ";
            case "request_accept" -> String.format("Accept teleport request from %s", params);
            case "request_deny" -> String.format("Deny teleport request from %s", params);
            case "request_received_rev" -> String.format("%s wants to teleport you to them.\n", params);
            case "request_received" -> String.format("%s wants to teleport to you.\n", params);
            case "teleport_to" -> String.format("Teleport to %s", params);
            default -> "Unknown Error";
        };

        return Objects.requireNonNull(message);
    }
}
