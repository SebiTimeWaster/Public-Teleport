package timewaster.publicteleport;

import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class Messages {
    public static enum Type {
        SUCCESS, WARNING, ERROR, REQUEST, BUTTON
    }

    @NotNull
    public static MutableComponent getMessage(String identifier, Type messageType, Object... params) {
        MutableComponent component;
        String key = PublicTeleport.MOD_ID + "." + identifier;
        String defaultString = PublicTeleport.storage.getTranslations().get(key);
        ChatFormatting color = switch (messageType) {
            case null -> null;
            case SUCCESS -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.YELLOW;
            case ERROR -> ChatFormatting.RED;
            case REQUEST -> ChatFormatting.AQUA;
            case BUTTON -> ChatFormatting.GOLD;
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

    public static class MessageBuilder {
        @NotNull
        private MutableComponent message = Component.literal("");

        public MessageBuilder append(String identifier, Type messageType, Object... params) {
            message.append(getMessage(identifier, messageType, params));

            return this;
        }

        public MessageBuilder append(@NotNull String text) {
            message.append(text);

            return this;
        }

        public MessageBuilder button(@NotNull MutableComponent buttonText, @NotNull MutableComponent hoverText,
            @NotNull String command) {
            message.append(buttonText.withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverText))));

            return this;
        }

        public void send(ServerPlayer player) {
            player.sendSystemMessage(message);
        }
    }
}
