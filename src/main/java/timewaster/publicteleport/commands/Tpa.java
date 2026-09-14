package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Registrar.SuggestionType.PLAYERS;
import static timewaster.publicteleport.Requests.RequestType.NORMAL;
import static timewaster.publicteleport.Requests.RequestType.REVERSE;
import static timewaster.publicteleport.Requests.RequestType.REVERSE_ALL;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.Requests;

/**
 * Defines all TPA commands, registered by {@link Registrar}.
 */
public class Tpa {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Registrar.buildArgumentPlayer("player", PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                return Requests.sendRequest(player, target, NORMAL);
            })));

        dispatcher.register(Commands.literal("tpahere")
            .then(Registrar.buildArgumentPlayer("player", PLAYERS, (ServerPlayer player, ServerPlayer target) -> {
                return Requests.sendRequest(player, target, REVERSE);
            })));

        dispatcher.register(Commands.literal("tpahereall").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.sendRequest(player, null, REVERSE_ALL);
            })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.cancelRequest(player);
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(Registrar.buildArgumentPlayer("player", PLAYERS, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.acceptRequest(sender, player);
            }))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.acceptRequest(null, player);
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(Registrar.buildArgumentPlayer("player", PLAYERS, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.denyRequest(sender, player);
            }))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.denyRequest(null, player);
            })));
    }
}
