package timewaster.publicteleport;

import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds and sends the mod's chat messages.
 */
public class Messages {
    public static enum Type {
        SUCCESS, WARNING, ERROR, REQUEST, BUTTON
    }

    /**
     * Resolves an identifier into a message and formats it
     *
     * @param identifier  the message identifier without the {@link MOD_ID} prefix
     * @param messageType the {@link Type} determining the message's chat color
     * @param params      the values for the translation placeholders
     * @return the resolved, colored, translatable component
     */
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

        if (params == null) {
            component = Component.translatableWithFallback(key, defaultString);
        } else {
            component = Component.translatableWithFallback(key, defaultString, params);
        }

        if (color != null) {
            component = component.withStyle(color);
        }

        return component;
    }

    /**
     * Resolves a message via {@link #getMessage} and sends it to a player's chat.
     *
     * @param player      the player to send the message to
     * @param identifier  the message identifier without the {@link MOD_ID} prefix
     * @param messageType the {@link Type} determining the message's chat color
     * @param params      the values for the translation placeholders
     */
    public static void sendMessage(ServerPlayer player, String identifier, Type messageType, Object... params) {
        player.sendSystemMessage(getMessage(identifier, messageType, params));
    }

    /**
     * Incrementally builds a single chat message out of multiple parts
     */
    public static class MessageBuilder {
        @NotNull
        private MutableComponent message = Component.literal("");

        /**
         * Resolves a message via {@link Messages#getMessage} and appends it.
         *
         * @param identifier  the message identifier without the {@link MOD_ID} prefix
         * @param messageType the {@link Type} determining the message's chat color
         * @param params      the values for the translation placeholders
         * @return this builder, for chaining
         */
        public MessageBuilder append(String identifier, Type messageType, Object... params) {
            message.append(getMessage(identifier, messageType, params));

            return this;
        }

        /**
         * Appends raw, non-translated text to the message.
         *
         * @param text the text to append
         * @return this builder, for chaining
         */
        public MessageBuilder append(@NotNull String text) {
            message.append(text);

            return this;
        }

        /**
         * Appends a clickable, hoverable button to the message.
         *
         * @param buttonText the visible text of the button
         * @param hoverText  the text shown in the tooltip when hovering
         * @param command    the command executed when the button is clicked
         * @return this builder, for chaining
         */
        public MessageBuilder button(@NotNull MutableComponent buttonText, @NotNull MutableComponent hoverText,
            @NotNull String command) {
            message.append(buttonText.withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverText))));

            return this;
        }

        /**
         * Sends the fully assembled message to a player's chat.
         *
         * @param player the player to send the message to
         */
        public void send(ServerPlayer player) {
            player.sendSystemMessage(message);
        }
    }
}
