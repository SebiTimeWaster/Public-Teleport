package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.COMMAND;
import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.Registrar.SuggestionType.PORTALS;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.records.Portal;

/**
 * Defines all Portal commands, registered by {@link Registrar}.
 */
public final class Portals {
    private Portals() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        boolean portalCommandsOnlyOp = PublicTeleport.storage.getConfig().portalCommandsOnlyOp();
        PermissionCheck hasPermission = portalCommandsOnlyOp ? Commands.LEVEL_OWNERS : Commands.LEVEL_ALL;

        dispatcher.register(Commands.literal("setportal").requires(Commands.hasPermission(hasPermission))
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.word()))
                .then(Commands.literal("from").executes((context) -> timewaster.publicteleport.Portals.setPortalData(context, "from")))
                .then(Commands.literal("to").executes((context) -> timewaster.publicteleport.Portals.setPortalData(context, "to")))
                .then(Commands.literal("target").executes((context) -> timewaster.publicteleport.Portals.setPortalData(context, "targetPosition"))
                    .then(Commands.argument("url", Objects.requireNonNull(StringArgumentType.string()))
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .executes((context) -> timewaster.publicteleport.Portals.setPortalData(context, "targetUrl"))))));

        dispatcher.register(Commands.literal("delportal").requires(Commands.hasPermission(hasPermission))
            .then(Registrar.buildArgumentString("name", PORTALS,
                (ServerPlayer player, String name) -> {
                    Boolean success = PublicTeleport.storage.deletePortal(player, name);

                    if (success == null) {
                        return false;
                    }

                    if (success) {
                        Messages.sendMessage(player, "portal_deleted", SUCCESS, name);
                    } else {
                        Messages.sendMessage(player, "portal_no_exist", ERROR, name);
                        return false;
                    }

                    return true;
                })));

        dispatcher.register(Commands.literal("portals").requires(Commands.hasPermission(hasPermission))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<Portal> portals = PublicTeleport.storage.getPortals();

                if (portals.isEmpty()) {
                    Messages.sendMessage(player, "portal_none", WARNING);
                } else {
                    Messages.MessageBuilder builder = new Messages.MessageBuilder().append("headline_portals",
                        HEADLINE);

                    portals.sort(Comparator.comparing((portal) -> portal.target().name()));

                    for (Portal portal : portals) {
                        builder.appendRaw("\n  ")
                            .appendRawColored(Objects.requireNonNull(portal.target().name()),
                                COMMAND)
                            .appendRawColored("  " + portal.dimension().substring(10) + "  "
                                + portal.aX() + " " + portal.aY() + " " + portal.aZ(), null);
                    }

                    builder.send(player);
                }

                return true;
            })));
    }
}
