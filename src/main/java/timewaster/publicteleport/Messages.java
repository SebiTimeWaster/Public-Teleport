package timewaster.publicteleport;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class Messages {
    private static String modId;
    private static Map<String, String> defaultTranslations = new HashMap<String, String>();

    public static enum Type {
        TEXT, SUCCESS, ERROR, BUTTON, REQUEST
    }

    public static void setModId(String modIdToSet) {
        modId = modIdToSet;
    }

    public static void setTranslations(Map<String, String> translations) {
        defaultTranslations = translations;
    }

    @NotNull
    public static MutableComponent getMessage(String identifier, Type messageType, Object... params) {
        MutableComponent component;
        String key = modId + "." + identifier;
        String defaultString = defaultTranslations.get(key);
        ChatFormatting color = switch (messageType) {
            case null -> null;
            case TEXT -> null;
            case SUCCESS -> ChatFormatting.GREEN;
            case ERROR -> ChatFormatting.RED;
            case BUTTON -> ChatFormatting.GOLD;
            case REQUEST -> ChatFormatting.YELLOW;
            default -> null;
        };

        if (defaultString == null) {
            defaultString = key;
        }

        if (params != null) {
            component = Component.translatableWithFallback(key, defaultString, params);
        } else {
            component = Component.translatableWithFallback(key, defaultString);
        }

        if (color != null) {
            component = component.withStyle(color);
        }

        return component;
    }

    public static void sendMessage(ServerPlayer player, String identifier, Type messageType, Object... params) {
        player.sendSystemMessage(getMessage(identifier, messageType, params));
    }

    public static class Builder {
        @NotNull
        private MutableComponent message = Component.literal("");

        public Builder append(String identifier, Type messageType, Object... params) {
            if (messageType == null) {
                messageType = Type.TEXT;
            }

            message.append(getMessage(identifier, messageType, params));

            return this;
        }

        public Builder append(String text) {
            if (text != null) {
                message.append(text);
            }

            return this;
        }

        public Builder button(@NotNull MutableComponent buttonText, @NotNull MutableComponent hoverText,
            @NotNull String command) {
            message.append(buttonText).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverText)));

            return this;
        }

        public void send(ServerPlayer player) {
            player.sendSystemMessage(message);
        }
    }
}
