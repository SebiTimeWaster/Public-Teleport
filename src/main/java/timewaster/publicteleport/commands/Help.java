package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.COMMAND;
import static timewaster.publicteleport.Messages.MessageType.COMMAND_PARAM;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.records.Config;

/**
 * Defines the Help command, registered by {@link Registrar}.
 */
public final class Help {
    private Help() {
    }

    private static void createHelpLine(Messages.MessageBuilder message, String command, String identifier, String params) {
        String[] hasParamName = { "setwarp", "delwarp", "warp", "sethome", "delhome", "home", "setportal", "delportal" };
        String[] hasParamPlayer = { "tpa", "tpahere", "tpaccept", "tpdeny" };

        message.appendRawColored("\n/" + command, COMMAND);
        if (ArrayUtils.contains(hasParamName, command)) {
            message.appendRaw(" ").append("command_param_name", COMMAND_PARAM);
        }
        if (ArrayUtils.contains(hasParamPlayer, command)) {
            message.appendRaw(" ").append("command_param_player", COMMAND_PARAM);
        }
        if (!"".equals(params)) {
            message.appendRaw(" ").appendRawColored(Objects.requireNonNull(params), COMMAND_PARAM);
        }

        message.appendRaw("  ").append("help_" + identifier, null);
    }

    private static void createHelpLine(Messages.MessageBuilder message, String identifier) {
        createHelpLine(message, identifier, identifier, "");
    }

    private static void addBlock(List<DialogBody> list, Messages.MessageBuilder builder) {
        list.add(new PlainMessage(Objects.requireNonNull(builder.getComponent()), 300));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Config config) {
        dispatcher.register(Commands.literal("helpteleport")
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<DialogBody> body = new ArrayList<DialogBody>();
                boolean isOwner = context.getSource().permissions().hasPermission(Permissions.COMMANDS_OWNER);

                if (config.enableSpawn()) {
                    Messages.MessageBuilder spawn = new Messages.MessageBuilder();

                    spawn.appendRawColored("Spawn", HEADLINE);
                    if (isOwner) {
                        createHelpLine(spawn, "setspawn");
                    }
                    createHelpLine(spawn, "spawn");

                    addBlock(body, spawn);
                }

                if (config.enableWarps()) {
                    Messages.MessageBuilder warps = new Messages.MessageBuilder();

                    warps.appendRawColored("Warps", HEADLINE);
                    if (isOwner) {
                        createHelpLine(warps, "setwarp");
                        createHelpLine(warps, "delwarp");
                    }
                    createHelpLine(warps, "warp");
                    createHelpLine(warps, "warps");

                    addBlock(body, warps);
                }

                if (config.enableHomes()) {
                    Messages.MessageBuilder homes = new Messages.MessageBuilder();

                    homes.appendRawColored("Homes", HEADLINE);
                    createHelpLine(homes, "sethome");
                    createHelpLine(homes, "delhome");
                    createHelpLine(homes, "home");
                    createHelpLine(homes, "homes");

                    addBlock(body, homes);
                }

                if (config.enableBack()) {
                    Messages.MessageBuilder back = new Messages.MessageBuilder();

                    back.appendRawColored("Back", HEADLINE);
                    createHelpLine(back, "back");

                    addBlock(body, back);
                }

                if (config.enableRtp()) {
                    Messages.MessageBuilder rtp = new Messages.MessageBuilder();

                    rtp.appendRawColored("RTP", HEADLINE);
                    createHelpLine(rtp, "rtp");

                    addBlock(body, rtp);
                }

                if (config.enablePortals() && (!config.portalCommandsOnlyOp() || isOwner)) {
                    Messages.MessageBuilder portals = new Messages.MessageBuilder();

                    portals.appendRawColored("Portals", HEADLINE);
                    createHelpLine(portals, "setportal", "setportal_from", "from");
                    createHelpLine(portals, "setportal", "setportal_to", "to");
                    createHelpLine(portals, "setportal", "setportal_target", "target");
                    if (isOwner) {
                        createHelpLine(portals, "setportal", "setportal_target_url", "target \"<domain/ip>:<port>\"");
                    }
                    portals.appendRaw("\n  ").append("help_setportal_three_part", null);
                    createHelpLine(portals, "delportal");
                    createHelpLine(portals, "portals");

                    addBlock(body, portals);
                }

                if (config.enableTpa()) {
                    Messages.MessageBuilder tpa = new Messages.MessageBuilder();

                    tpa.appendRawColored("TPA", HEADLINE);
                    createHelpLine(tpa, "tpa");
                    createHelpLine(tpa, "tpahere");
                    if (isOwner) {
                        createHelpLine(tpa, "tpahereall");
                    }
                    createHelpLine(tpa, "tpcancel");
                    createHelpLine(tpa, "tpaccept");
                    createHelpLine(tpa, "tpdeny");

                    addBlock(body, tpa);
                }

                Dialog dialog = new NoticeDialog(
                    new CommonDialogData(
                        Messages.getMessage("help_headline", HEADLINE),
                        Objects.requireNonNull(Optional.empty()),
                        true,
                        true,
                        DialogAction.CLOSE,
                        body,
                        Objects.requireNonNull(List.of())),
                    new ActionButton(new CommonButtonData(Messages.getMessage("help_close", null), 150), Objects.requireNonNull(Optional.empty())));
                player.openDialog(Holder.direct(dialog));

                return true;
            })));
    }
}
