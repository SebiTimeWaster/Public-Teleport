package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import timewaster.publicteleport.records.Teleport;

/**
 * Performs the mod's actual teleportation logic, and holds adjacent helpers.
 */
public class Teleports {
    @SuppressWarnings("null")
    private static void doTeleportEffect(ServerPlayer player) {
        player.level().playSound(
            null,
            player.getBlockX() + 0.5,
            player.getBlockY() + 1.0,
            player.getBlockZ() + 0.5,
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS,
            1.0f,
            1.0f);

        player.level().sendParticles(
            ParticleTypes.PORTAL,
            true,
            true,
            player.getX(),
            player.getY() + 0.75,
            player.getZ(),
            100,
            0.5,
            0.75,
            0.5,
            0.25);
    }

    private static boolean teleport(ServerPlayer player, Teleport target) {
        ServerLevel level = TeleportSafety.getLevelFromTeleport(player, target);

        if (level == null) {
            Messages.sendMessage(player, "level_no_exist", Messages.Type.ERROR);
            return false;
        }

        doTeleportEffect(player);

        boolean result = player.teleportTo(
            level,
            target.x() + 0.5,
            target.y() + 0.01,
            target.z() + 0.5,
            Objects.requireNonNull(Set.of()),
            target.yaw() != null ? (float) target.yaw() : player.getYRot(),
            target.pitch() != null ? (float) target.pitch() : player.getXRot(),
            true);

        if (result) {
            doTeleportEffect(player);
        } else {
            Messages.sendMessage(player, "unknown_error", Messages.Type.ERROR);
        }

        return result;
    }

    private static boolean teleportPlayer(ServerPlayer player, Teleport target, String originalTargetName,
        boolean isWarp) {
        if (target == null) {
            return false;
        }

        if (target.name().equals("public_teleport_not_found")) {
            Messages.sendMessage(player, isWarp ? "warp_no_exist" : "home_no_exist", Messages.Type.ERROR,
                originalTargetName);
            return false;
        }

        if (!TeleportSafety.doesPlayerClearTarget(player, target, 2.0, 3.0)) {
            Messages.sendMessage(player, "teleport_unnecessary", Messages.Type.WARNING, originalTargetName);
            return false;
        }

        if (!TeleportSafety.isPositionTeleportable(player, target)) {
            Messages.sendMessage(player, "teleport_unsafe", Messages.Type.ERROR, originalTargetName);
            return false;
        }

        Teleport back = Teleport.create(player, "back");
        boolean result = teleport(player, target);

        if (result) {
            if (!target.name().equals("back")) {
                PublicTeleport.storage.setTeleport(player, back, false);
            }

            Messages.sendMessage(player, "teleported_to", Messages.Type.SUCCESS, target.name());
        }

        return result;
    }

    /**
     * Registers a listener that records a player's {@code back} location
     * whenever they die.
     */
    public static void registerDeathEvent() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                PublicTeleport.storage.setTeleport(player, Teleport.create(player, "back"), false);
            }
        });
    }

    /**
     * Looks up a named teleport destination (a home or a warp) and teleports
     * the player to it if found.
     *
     * @param player             the player to teleport
     * @param originalTargetName the name of the home or warp to teleport to
     * @param isWarp             {@code true} if the teleport is a Warp, not a Home
     * @return {@code true} if the destination was found and the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, String originalTargetName, boolean isWarp) {
        Teleport teleportTarget = PublicTeleport.storage.getTeleport(player, originalTargetName, isWarp);

        if (teleportTarget == null) {
            return false;
        }

        return teleportPlayer(player, teleportTarget, originalTargetName, isWarp);
    }

    /**
     * Teleports the player to an already-resolved destination.
     *
     * @param player the player to teleport
     * @param target the destination to teleport the player to
     * @return {@code true} if the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, Teleport target) {
        return teleportPlayer(player, target, target.name(), false);
    }

    /**
     * Sends a player a chat message listing a set of teleport names as
     * clickable buttons.
     *
     * @param player        the player to send the listing to
     * @param teleportNames a list of teleport names
     * @param isWarps       {@code true} if the list has Warps, not Homes
     */
    public static void listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        if (teleportNames.size() == 0) {
            Messages.sendMessage(player, isWarps ? "warp_none" : "home_none", Messages.Type.WARNING);
        } else {
            Messages.MessageBuilder builder = new Messages.MessageBuilder().append(
                isWarps ? "headline_warps" : "headline_homes", Messages.Type.REQUEST);

            for (String name : teleportNames) {
                MutableComponent buttonText = Messages.getMessage("button_named", Messages.Type.BUTTON, name);
                MutableComponent hoverText = Messages.getMessage("teleport_to", null, name);
                String command = isWarps ? "/warp " + name : "/home " + name;

                builder.append("\n  ").button(buttonText, hoverText, command);
            }

            builder.send(player);
        }
    }
}
