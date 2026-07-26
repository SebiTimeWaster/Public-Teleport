package timewaster.publicteleport;

import java.util.List;

import org.jspecify.annotations.NonNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class MessageHandler {

    private static @NonNull MutableComponent text(String text) {
        if (text == null) {
            return Component.literal("Unknown Error").withStyle(ChatFormatting.RED);
        }

        return Component.literal(text);
    }

    private static @NonNull MutableComponent colored(String text, @NonNull ChatFormatting color) {
        return text(text).withStyle(color);
    }

    private static @NonNull MutableComponent error(String text) {
        return colored(text, ChatFormatting.RED);
    }

    private static void sendMessage(ServerPlayer player, @NonNull Component message) {
        player.sendSystemMessage(message);
    }

    public static void listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        MutableComponent text = Component.literal("");

        if (teleportNames.size() == 0) {
            text.append(error(isWarps ? "There are no warps." : "You have no homes."));
        } else {
            text.append(text(isWarps ? "Warps: " : "Homes: "));

            for (String name : teleportNames) {
                text.append(text(" "));
                text.append(colored(name, ChatFormatting.GOLD).withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand((isWarps ? "/warp " : "/home ") + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Teleport to " + name)))));
            }
        }

        sendMessage(player, text);
    }

    public static void sendMessage(ServerPlayer player, String identifier, Object... params) {
        Component message = switch (identifier) {
            case "no_teleport" -> error("That warp/home doesn't exist!");
            case "no_dimension" -> error("That dimension doesn't exist!");
            case "reserved_name" -> error("That name cannot be used!");
            case "teleported_to" -> colored(String.format("Teleported to %s!", params), ChatFormatting.AQUA);
            case "teleported" -> colored(String.format("Teleported %s!", params), ChatFormatting.AQUA);
            default -> error("Unknown Error");
        };

        sendMessage(player, message);
    }

}
