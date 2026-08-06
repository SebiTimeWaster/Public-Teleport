package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Requests;

public class Tpa {
    private static final Registrar.SuggestionType typePlayers = Registrar.SuggestionType.PLAYERS;
    private final Registrar registrar;
    private final Requests requests;

    public Tpa(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, Requests requests) {
        this.registrar = registrar;
        this.requests = requests;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(registrar.buildArgumentPlayer("target", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                if (player.getName().equals(target.getName())) {
                    Messages.sendMessage(player, "request_teleport_self", Messages.Type.WARNING);
                    return false;
                }

                return requests.sendRequest(player, target, false);
            })));

        dispatcher.register(Commands.literal("tpahere")
            .then(registrar.buildArgumentPlayer("target", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                if (player == target) {
                    Messages.sendMessage(player, "request_teleport_self", Messages.Type.WARNING);
                    return false;
                }

                return requests.sendRequest(player, target, true);
            })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return requests.cancelRequest(player);
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(registrar.buildArgumentPlayer("sender", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return requests.acceptRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return requests.acceptRequest(null, player);
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(registrar.buildArgumentPlayer("sender", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return requests.denyRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return requests.denyRequest(null, player);
            })));
    }
}
