package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Requests;

public class Tpa {
    private final Registrar registrar;
    private final Requests requests;

    public Tpa(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, Requests requests) {
        this.registrar = registrar;
        this.requests = requests;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(registrar.buildArgumentPlayer("target", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    if (player.getName().equals(target.getName())) {
                        Messages.sendMessage(player, "no_teleport_self");
                        return false;
                    }

                    requests.sendRequest(player, target, false);

                    return true;
                })));

        dispatcher.register(Commands.literal("tpahere")
            .then(registrar.buildArgumentPlayer("target", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    if (player.getName().equals(target.getName())) {
                        Messages.sendMessage(player, "no_teleport_self");
                        return false;
                    }

                    requests.sendRequest(player, target, true);

                    return true;
                })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requests.cancelRequest(player);

                return true;
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(registrar.buildArgumentPlayer("sender", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    requests.acceptRequest(player, target);

                    return true;
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requests.acceptRequest(player, null);

                return true;
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(registrar.buildArgumentPlayer("sender", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    requests.denyRequest(player, target);

                    return true;
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requests.denyRequest(player, null);

                return true;
            })));
    }
}
