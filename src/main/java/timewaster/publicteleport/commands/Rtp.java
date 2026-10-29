package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.WARNING;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.Utils;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines the RTP command, registered by {@link Registrar}.
 */
public final class Rtp {
    private static boolean rtpInProgress = false;

    private Rtp() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                if (rtpInProgress) {
                    Messages.sendMessage(player, "rtp_running", ERROR);
                    return false;
                } else {
                    Messages.sendMessage(player, "rtp_start", WARNING);
                }
                rtpInProgress = true; // NOPMD - UnusedAssignment false positive, reset asynchronously below
                ServerLevel level = player.level();

                TeleportSafety.findRandomTeleportablePosition(player).whenCompleteAsync((blockPos, throwable) -> {
                    rtpInProgress = false;

                    // the player may have logged out, died or changed dimension during the search
                    if (player.isRemoved()) {
                        return;
                    }
                    if (throwable != null || blockPos == null || player.level() != level) {
                        Messages.sendMessage(player, "rtp_failed", ERROR);
                        return;
                    }

                    Teleports.teleportPlayer(player, new Teleport(
                        "public_teleport_rtp",
                        blockPos.getX(),
                        blockPos.getY(),
                        blockPos.getZ(),
                        null,
                        null,
                        Utils.getDimensionNameByLevel(level)));
                }, Utils.getServerByPlayer(player));

                return true;
            })));
    }
}
