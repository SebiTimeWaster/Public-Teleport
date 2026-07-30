package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.MessageHandler;
import timewaster.publicteleport.RequestHandler;

public class Tpa {
    private final Registrar registrar;
    private RequestHandler requestHandler;

    public Tpa(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, RequestHandler requestHandler) {
        this.registrar = registrar;
        this.requestHandler = requestHandler;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(registrar.buildArgumentPlayer("target", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    if (player.getName().equals(target.getName())) {
                        MessageHandler.sendMessage(player, "no_teleport_self");
                        return false;
                    }

                    requestHandler.sendTeleportRequest(player, target, false);

                    return true;
                })));

        dispatcher.register(Commands.literal("tpahere")
            .then(registrar.buildArgumentPlayer("target", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    if (player.getName().equals(target.getName())) {
                        MessageHandler.sendMessage(player, "no_teleport_self");
                        return false;
                    }

                    requestHandler.sendTeleportRequest(player, target, true);

                    return true;
                })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requestHandler.cancelTeleportRequest(player);

                return true;
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(registrar.buildArgumentPlayer("sender", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    requestHandler.acceptTeleportRequest(player, target);

                    return true;
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requestHandler.acceptTeleportRequest(player, null);

                return true;
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(registrar.buildArgumentPlayer("sender", Registrar.SuggestionType.PLAYERS,
                (ServerPlayer player, ServerPlayer target) -> {
                    requestHandler.denyTeleportRequest(player, target);

                    return true;
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                requestHandler.denyTeleportRequest(player, null);

                return true;
            })));
    }
}
