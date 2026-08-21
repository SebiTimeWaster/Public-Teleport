package timewaster.publicteleport.commands;

import org.apache.commons.lang3.ArrayUtils;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.records.Config;

/**
 * Defines the Back command, registered by {@link Registrar}.
 */
public class Help {
    private static void createHelpLine(Messages.MessageBuilder message, String identifier) {
        String[] hasParamName = { "setwarp", "delwarp", "warp", "sethome", "delhome", "home" };
        String[] hasParamPlayer = { "tpa", "tpahere", "tpaccept", "tpdeny" };

        message.appendRawColored("\n  /" + identifier, Messages.MessageType.COMMAND);

        if (ArrayUtils.contains(hasParamName, identifier)) {
            message.appendRaw(" ").append("command_param_name", Messages.MessageType.COMMAND_PARAM);
        }

        if (ArrayUtils.contains(hasParamPlayer, identifier)) {
            message.appendRaw(" ").append("command_param_player", Messages.MessageType.COMMAND_PARAM);
        }

        message.appendRaw("  ").append("help_" + identifier, null);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Config config) {
        dispatcher.register(Commands.literal("helpteleport")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Messages.MessageBuilder message = new Messages.MessageBuilder();
                boolean isOwner = context.getSource().permissions().hasPermission(Permissions.COMMANDS_OWNER);

                message.append("help_headline", Messages.MessageType.HEADLINE);

                if (config.enableSpawn()) {
                    message.appendRawColored("\n Spawn:", Messages.MessageType.HEADLINE);
                    if (isOwner) {
                        createHelpLine(message, "setspawn");
                    }
                    createHelpLine(message, "spawn");
                }

                if (config.enableWarps()) {
                    message.appendRawColored("\n Warps:", Messages.MessageType.HEADLINE);
                    if (isOwner) {
                        createHelpLine(message, "setwarp");
                        createHelpLine(message, "delwarp");
                    }
                    createHelpLine(message, "warp");
                    createHelpLine(message, "warps");
                }

                if (config.enableHomes()) {
                    message.appendRawColored("\n Homes:", Messages.MessageType.HEADLINE);
                    createHelpLine(message, "sethome");
                    createHelpLine(message, "delhome");
                    createHelpLine(message, "home");
                    createHelpLine(message, "homes");
                }

                if (config.enableBack()) {
                    message.appendRawColored("\n Back:", Messages.MessageType.HEADLINE);
                    createHelpLine(message, "back");
                }

                if (config.enableTpa()) {
                    message.appendRawColored("\n TPA:", Messages.MessageType.HEADLINE);
                    createHelpLine(message, "tpa");
                    createHelpLine(message, "tpahere");
                    if (isOwner) {
                        createHelpLine(message, "tpahereall");
                    }
                    createHelpLine(message, "tpcancel");
                    createHelpLine(message, "tpaccept");
                    createHelpLine(message, "tpdeny");
                }

                message.send(player);

                return true;
            })));
    }
}
