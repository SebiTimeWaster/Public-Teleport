package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Requests;

/**
 * Defines all TPA commands, registered by {@link Registrar}.
 */
public class Tpa {
    private static final Registrar.SuggestionType typePlayers = Registrar.SuggestionType.PLAYERS;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Registrar.buildArgumentPlayer("target", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                if (player.getName().equals(target.getName())) {
                    Messages.sendMessage(player, "request_teleport_self", Messages.Type.WARNING);
                    return false;
                }

                return Requests.sendRequest(player, target, false);
            })));

        dispatcher.register(Commands.literal("tpahere")
            .then(Registrar.buildArgumentPlayer("target", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                if (player == target) {
                    Messages.sendMessage(player, "request_teleport_self", Messages.Type.WARNING);
                    return false;
                }

                return Requests.sendRequest(player, target, true);
            })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.cancelRequest(player);
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(Registrar.buildArgumentPlayer("sender", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.acceptRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.acceptRequest(null, player);
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(Registrar.buildArgumentPlayer("sender", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.denyRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.denyRequest(null, player);
            })));
    }
}
