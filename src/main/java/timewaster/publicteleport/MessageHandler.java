package timewaster.publicteleport;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class MessageHandler {
    @NotNull
    private static MutableComponent text(String text) {
        if (text == null) {
            return Component.literal("Unknown Error").withStyle(ChatFormatting.RED);
        }

        return Component.literal(text);
    }

    @NotNull
    private static MutableComponent colored(String text, @NotNull ChatFormatting color) {
        return text(text).withStyle(color);
    }

    @NotNull
    private static MutableComponent error(String text) {
        return colored(text, ChatFormatting.RED);
    }

    @NotNull
    private static MutableComponent success(String text) {
        return colored(text, ChatFormatting.AQUA);
    }

    @NotNull
    private static MutableComponent list(String text) {
        return colored(text, ChatFormatting.GOLD);
    }

    private static void sendMessage(ServerPlayer player, @NotNull Component message) {
        player.sendSystemMessage(message);
    }

    public static boolean listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        if (teleportNames == null) {
            sendMessage(player, "unknown_error");
            return false;
        }

        if (teleportNames.size() == 0) {
            sendMessage(player, isWarps ? "no_warps" : "no_homes");
        } else {
            MutableComponent message = Component.literal(getMessage(isWarps ? "warps" : "homes"));
            String command = getMessage(isWarps ? "command_warp" : "command_home");

            for (String name : teleportNames) {
                message.append(text(getMessage("line")));
                message.append(list(name).withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand(command + name))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(getMessage("teleport_to") + name)))));
            }

            sendMessage(player, message);
        }

        return true;
    }

    public static void sendMessage(ServerPlayer player, String identifier, Object... params) {
        Component message = switch (identifier) {
            case "unknown_error" -> error("Unknown error!");
            case "reserved_name" -> error("The name 'back' cannot be used!");
            case "teleported_to" -> success(String.format("Teleported to %s!", params));
            case "teleported" -> success(String.format("Teleported %s!", params));
            case "spawn_set" -> success("Spawn set!");
            case "warp_set" -> success(String.format("Warp %s set!", params));
            case "warp_deleted" -> success(String.format("Warp '%s' deleted!", params));
            case "warp_not_exist" -> error(String.format("Warp '%s' does not exist!", params));
            case "named_home_set" -> success(String.format("Home %s set!", params));
            case "home_set" -> success("Home set!");
            case "home_deleted" -> success(String.format("Home '%s' deleted!", params));
            case "home_not_exist" -> error(String.format("Home '%s' does not exist!", params));
            case "no_teleport_self" -> error("You cannot teleport to yourself!");
            case "no_warps" -> error("There are no warps.");
            case "no_homes" -> error("You have no homes.");
            default -> error("Unknown Error");
        };

        sendMessage(player, message);
    }

    @NotNull
    public static String getMessage(String identifier) {
        String message = switch (identifier) {
            case "teleport_to" -> "Teleport to ";
            case "warps" -> "Warps:";
            case "homes" -> "Homes:";
            case "line" -> "\n  ";
            case "command_warp" -> "/warp ";
            case "command_home" -> "/home ";
            case "init" -> "Initialized!";
            case "err_create_dir" -> "Failed to create config directories!";
            case "err_load_config" -> "Failed to load config from {}:";
            case "err_load_file" -> "Failed to load from {}:";
            case "err_save_file" -> "Failed to save to {}:";
            default -> "Unknown Error";
        };

        return message;
    }
}
