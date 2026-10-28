package timewaster.publicteleport;

import static timewaster.publicteleport.Messages.MessageType.BUTTON;
import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.records.Teleport;

/**
 * Performs the mod's actual teleportation logic, and holds adjacent helpers.
 */
public final class Teleports {
    private static final String RESERVED_PREFIX = "public_teleport_";

    private Teleports() {
    }

    @SuppressWarnings("null")
    private static void teleportEffect(ServerPlayer player) {
        player.level().playSound(
            null,
            player.getBlockX() + 0.5, // middle point of player (roughly)
            player.getBlockY() + 1.0,
            player.getBlockZ() + 0.5,
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS,
            1.0f,
            1.0f);

        // all numbers where visually determined to fit
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

    private static boolean teleport(ServerPlayer player, Teleport target, ServerLevel level) {
        if (target == null || level == null) {
            return false;
        }
        Vec3 targetPosition = new Vec3(target.x() + 0.5, target.y() + 0.01, target.z() + 0.5); // middle point of block, slightly above ground
        float targetYaw = target.yaw() != null ? (float) target.yaw() : player.getYRot();
        float targetPitch = target.pitch() != null ? (float) target.pitch() : player.getXRot();
        Set<Relative> emptySet = Objects.requireNonNull(Set.of());
        Entity entity = player.getRootVehicle();
        Map<ServerPlayer, Teleport> otherRidersBack = new HashMap<>();
        boolean result;

        Teleport back = Teleport.create(player, "back");
        entity.getSelfAndPassengers().forEach(rider -> {
            if (rider instanceof ServerPlayer otherRider && otherRider != player) {
                otherRidersBack.put(otherRider, Teleport.create(otherRider, "back"));
            }
        });

        teleportEffect(player);

        if (entity == player) {
            result = player.teleportTo(
                level,
                targetPosition.x,
                targetPosition.y,
                targetPosition.z,
                emptySet,
                targetYaw,
                targetPitch,
                true);
        } else {
            result = entity.teleport(new TeleportTransition(
                level,
                targetPosition,
                Vec3.ZERO,
                targetYaw - (player.getYRot() - entity.getYRot()),
                targetPitch - (player.getXRot() - entity.getXRot()),
                emptySet,
                TeleportTransition.DO_NOTHING)) != null;

            // This temporarily fixes
            // https://report.bugs.mojang.com/servicedesk/customer/portal/2/MC-312974
            level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(BlockPos.containing(targetPosition)), 1);
            player.getRootVehicle().getSelfAndPassengers().forEach(rider -> {
                if (rider instanceof ServerPlayer riderPlayer) {
                    riderPlayer.level().getChunkSource().move(riderPlayer);
                }
            });
        }

        if (result) {
            teleportEffect(player);

            if (!"back".equals(target.name())) {
                PublicTeleport.storage.setTeleport(player, back, false);
            }
            otherRidersBack.forEach((otherRider, otherRiderBack) -> {
                PublicTeleport.storage.setTeleport(otherRider, otherRiderBack, false);
                Messages.sendMessage(otherRider, "teleported_along", SUCCESS, player.getName().getString());
            });

            if ("public_teleport_rtp".equals(target.name())) {
                Messages.sendMessage(player, "teleported_to", SUCCESS, target.x() + ", " + target.y() + ", " + target.z());
            } else {
                Messages.sendMessage(player, "teleported_to", SUCCESS, target.name());
            }

            if (PublicTeleport.LOGGER.isInfoEnabled()) {
                PublicTeleport.LOGGER.info(PublicTeleport.prefix("Teleported \"{}\" to target \"{}\" on position \"{}\" in dimension \"{}\""),
                    player.getName().getString(), target.name(), target.x() + ", " + target.y() + ", " + target.z(), target.dimension());

                otherRidersBack.keySet().forEach(otherRider -> PublicTeleport.LOGGER.info(PublicTeleport.prefix("Teleported \"{}\" along with \"{}\""),
                    otherRider.getName().getString(), player.getName().getString()));
            }
        } else {
            Messages.sendMessage(player, "unknown_error", ERROR);
        }

        return result;
    }

    @Nullable
    private static Teleport teleportPreflightCheck(ServerPlayer player, @Nullable ServerPlayer targetPlayer, Teleport target, ServerLevel level,
        boolean ignorePlayerClipping) {
        if (level == null) {
            Messages.sendMessage(player, "level_no_exist", ERROR);
            return null;
        }

        if (!Utils.doesPlayerClearTargetBlock(player, new BlockPos(target.x(), target.y(), target.z()), target.dimension(), 4.0, 5.0)) {
            Messages.sendMessage(player, "teleport_unnecessary", WARNING, target.name());
            return null;
        }

        Teleport testedTarget = TeleportSafety.findTeleportablePosition(player, target, ignorePlayerClipping);
        boolean fitsOnFoot = "public_teleport_teleportable_on_foot".equals(testedTarget.name());
        if ("public_teleport_none_found".equals(testedTarget.name()) || fitsOnFoot) {
            if (targetPlayer == null) {
                Messages.sendMessage(player, "teleport_unsafe" + (fitsOnFoot ? "_riding" : ""), ERROR, target.name());
            } else {
                Messages.sendMessage(player, "teleport_unsafe_tpa" + (fitsOnFoot ? "_riding" : ""), ERROR, targetPlayer.getName().getString());
                Messages.sendMessage(targetPlayer, "teleport_unsafe_target" + (fitsOnFoot ? "_riding" : ""), ERROR, player.getName().getString());
            }

            return null;
        }

        return testedTarget;
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
     * Teleports the player to an already-resolved {@link Teleport} destination.
     *
     * @param player         the player to teleport
     * @param teleportTarget the destination to teleport the player to
     * @return {@code true} if the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, Teleport teleportTarget) {
        ServerLevel level = Utils.getLevelbyDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, null, teleportTarget, level, false);

        return teleport(player, testedTarget, level);
    }

    /**
     * Teleports the player to another player (TPA destination).
     *
     * @param player       the player to teleport
     * @param targetPlayer the destination to teleport the player to
     * @param isTpaHereAll if it is a /tpahereall command usage
     * @return {@code true} if the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, ServerPlayer targetPlayer, boolean isTpaHereAll) {
        Teleport teleportTarget = Teleport.create(targetPlayer, targetPlayer.getName().getString());
        ServerLevel level = Utils.getLevelbyDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, targetPlayer, teleportTarget, level, isTpaHereAll);

        return teleport(player, testedTarget, level);
    }

    /**
     * Looks up a named teleport destination (a home or a warp) and teleports the
     * player to it if found. Uses the fallback if it is specified otherwise.
     *
     * @param player     the player to teleport
     * @param targetName the name of the home or warp to teleport to
     * @param fallback   is used if not {@code null} and the target cannot be found
     * @param isWarp     {@code true} if the teleport is a Warp, not a Home
     * @return {@code true} if the destination was found and the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, String targetName, @Nullable Teleport fallback, boolean isWarp) {
        Teleport teleportTarget = PublicTeleport.storage.getTeleport(player, targetName, isWarp);
        if (teleportTarget == null) {
            return false;
        }
        if ("public_teleport_not_found".equals(teleportTarget.name())) {
            if (fallback == null) {
                Messages.sendMessage(player, isWarp ? "warp_no_exist" : "home_no_exist", ERROR, targetName);
                return false;
            } else {
                teleportTarget = fallback;
            }
        }
        ServerLevel level = Utils.getLevelbyDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, null, teleportTarget, level, false);

        return teleport(player, testedTarget, level);
    }

    /**
     * Looks up a named teleport destination (a home or a warp) and teleports
     * the player to it if found.
     *
     * @param player     the player to teleport
     * @param targetName the name of the home or warp to teleport to
     * @param isWarp     {@code true} if the teleport is a Warp, not a Home
     * @return {@code true} if the destination was found and the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, String targetName, boolean isWarp) {
        return teleportPlayer(player, targetName, null, isWarp);
    }

    /**
     * Checks if a Home, Warp or Portal name uses the prefix reserved for internal
     * teleport names, if so the player is notified.
     *
     * @param player the player choosing the name
     * @param name   the name to check
     * @return {@code true} if the name cannot be used
     */
    public static boolean isNameReserved(ServerPlayer player, String name) {
        if (name.startsWith(RESERVED_PREFIX)) {
            Messages.sendMessage(player, "name_reserved_prefix", WARNING, RESERVED_PREFIX);
            return true;
        }

        return false;
    }

    /**
     * Checks that {@code target} is a safe place to stand on and, if so, persists
     * it as a Warp or Home.
     *
     * @param player the player trying to save the data
     * @param target the {@link Teleport} to save
     * @param isWarp if it is a Warp, not a Home
     * @param type   the type of teleport
     * @return {@code true} if saved; {@code false} if a new teleport would go over
     *         the max homes limit; {@code null} if the name is reserved, the
     *         position was unsafe or a file error occurred
     */
    @Nullable
    public static Boolean setSafeTeleport(ServerPlayer player, Teleport target, boolean isWarp, String type) {
        if (isNameReserved(player, target.name()) || !TeleportSafety.isPositionToSetTeleportable(player, target, type)) {
            return null;
        }

        return PublicTeleport.storage.setTeleport(player, target, isWarp);
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
        if (teleportNames.isEmpty()) {
            Messages.sendMessage(player, isWarps ? "warp_none" : "home_none", WARNING);
        } else {
            Messages.MessageBuilder builder = new Messages.MessageBuilder().append(isWarps ? "headline_warps" : "headline_homes", HEADLINE);

            for (String name : teleportNames) {
                MutableComponent buttonText = Messages.getMessage("button_named", BUTTON, name);
                MutableComponent hoverText = Messages.getMessage("teleport_to", null, name);
                String command = isWarps ? "/warp " + name : "/home " + name;

                builder.appendRaw("\n  ").button(buttonText, hoverText, command);
            }

            builder.send(player);
        }
    }
}
