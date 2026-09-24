package timewaster.publicteleport;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

public final class Portals {
    private static final Integer MIN = Integer.MIN_VALUE;
    private static final Pattern HOST_PATTERN = Pattern.compile(
        "^(\\[[0-9A-Fa-f:]+\\]|[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*)$");
    private static Map<String, Portal> tempPortalData = new HashMap<String, Portal>();
    private static Map<ServerPlayer, Boolean> playerStates = new HashMap<ServerPlayer, Boolean>();
    private static Map<ServerPlayer, Vec3> playerPositions = new HashMap<ServerPlayer, Vec3>();

    private Portals() {
    }

    @Nullable
    private static Vec3 getBlockPositionPlayerLooksAt(ServerPlayer player, String action) {
        if (!"from".equals(action) && !"to".equals(action)) {
            return new Vec3(0.0, 0.0, 0.0);
        }
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 dir = player.getViewVector(1.0f);
        Vec3 end = start.add(dir.scale(player.blockInteractionRange()));
        BlockHitResult blockHitResult = player.level().clip(new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player));

        if (blockHitResult.getType() != BlockHitResult.Type.BLOCK) {
            Messages.sendMessage(player, "portal_set_no_block", ERROR);
            return null;
        }

        return blockHitResult.getLocation();
    }

    @Nullable
    private static String sanitiseTargetUrl(String url) {
        String[] parts = url.split(":(?=[^:]*$)");
        if (parts.length != 2) {
            return null;
        }
        String host = parts[0];
        String portString = parts[1];
        int port;

        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException exception) {
            return null;
        }

        if (port < 1 || port > 65535 || !HOST_PATTERN.matcher(host).matches()) {
            return null;
        }

        return host + ":" + port;
    }

    @Nullable
    private static Teleport createPortalTarget(CommandContext<CommandSourceStack> context, ServerPlayer player,
        String action, String name) {
        Teleport target = Teleport.create(player, name);

        if ("targetPosition".equals(action) && !TeleportSafety.isBlockTeleportable(player, target)) {
            Messages.sendMessage(player, "teleport_unsafe_set", ERROR, "Portal");
            return null;
        }

        if ("targetUrl".equals(action)) {
            String url = StringArgumentType.getString(context, "url");
            String sanitisedUrl = sanitiseTargetUrl(url);

            if (sanitisedUrl == null) {
                Messages.sendMessage(player, "portal_set_url_invalid", ERROR, url);
                return null;
            }

            target = new Teleport(name, 0, 0, 0, 0.0f, 0.0f, "url:" + sanitisedUrl);
        }

        return target;
    }

    @Nullable
    private static Portal mutateTempPortalData(CommandContext<CommandSourceStack> context, ServerPlayer player,
        String action) {
        String name = StringArgumentType.getString(context, "name");
        Vec3 blockPosition = getBlockPositionPlayerLooksAt(player, action);
        Teleport teleportTarget = createPortalTarget(context, player, action, name);
        if (blockPosition == null || teleportTarget == null) {
            return null;
        }
        String dimensionIdentifier = Utils.getDimensionNameByLevel(player.level());
        Portal existingPortalData = tempPortalData.getOrDefault(name,
            new Portal("", MIN, MIN, MIN, MIN, MIN, MIN, null));

        Portal newPortalData = new Portal(
            ("from".equals(action) || "to".equals(action)) ? dimensionIdentifier : existingPortalData.dimension(),
            "from".equals(action) ? (int) Math.floor(blockPosition.x()) : existingPortalData.aX(),
            "from".equals(action) ? (int) Math.floor(blockPosition.y()) : existingPortalData.aY(),
            "from".equals(action) ? (int) Math.floor(blockPosition.z()) : existingPortalData.aZ(),
            "to".equals(action) ? (int) Math.floor(blockPosition.x()) : existingPortalData.bX(),
            "to".equals(action) ? (int) Math.floor(blockPosition.y()) : existingPortalData.bY(),
            "to".equals(action) ? (int) Math.floor(blockPosition.z()) : existingPortalData.bZ(),
            action.startsWith("target") ? teleportTarget : existingPortalData.target());

        tempPortalData.put(name, newPortalData);

        return newPortalData;
    }

    private static Portal normalisePortal(Portal portal) {
        return new Portal(
            portal.dimension(),
            Math.min(portal.aX(), portal.bX()),
            Math.min(portal.aY(), portal.bY()),
            Math.min(portal.aZ(), portal.bZ()),
            Math.max(portal.aX(), portal.bX()),
            Math.max(portal.aY(), portal.bY()),
            Math.max(portal.aZ(), portal.bZ()),
            portal.target());
    }

    private static boolean setPortal(ServerPlayer player, Portal portal) {
        if (PublicTeleport.storage.setPortal(player, portal)) {
            Level level = Utils.getLevelbyDimension(player, portal.dimension());
            Block purplePane = BuiltInRegistries.BLOCK
                .getValue(Identifier.fromNamespaceAndPath("minecraft", "purple_stained_glass_pane"));

            for (int x = portal.aX(); x <= portal.bX(); x++) {
                for (int y = portal.aY(); y <= portal.bY(); y++) {
                    for (int z = portal.aZ(); z <= portal.bZ(); z++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        BlockState state = Block.updateFromNeighbourShapes(purplePane.defaultBlockState(),
                            Objects.requireNonNull(level), blockPos);

                        level.setBlock(blockPos, state, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }

            tempPortalData.remove(portal.target().name());
            Messages.sendMessage(player, "portal_set", SUCCESS, portal.target().name());

            return true;
        }

        return false;
    }

    private static void sendSuccess(CommandContext<CommandSourceStack> context, ServerPlayer player, String action) {
        String name = StringArgumentType.getString(context, "name");
        String successIdentifier = switch (action) {
            case "from" -> "portal_set_from";
            case "to" -> "portal_set_to";
            case "targetPosition" -> "portal_set_pos";
            case "targetUrl" -> "portal_set_url";
            default -> "unknown_error";
        };

        Messages.sendMessage(player, successIdentifier, SUCCESS, name);
    }

    private static void teleportOrRedirectPlayer(ServerPlayer player, Teleport target, Vec3 oldPosition) {
        if (target.dimension().startsWith("url:")) {
            String url = target.dimension().substring(4);
            int splitPosition = url.lastIndexOf(":");
            ClientboundTransferPacket packet = new ClientboundTransferPacket(
                Objects.requireNonNull(url.substring(0, splitPosition)),
                Integer.parseInt(url.substring(splitPosition + 1)));

            if (oldPosition != null) {
                player.setPos(oldPosition);
            }

            player.connection.send(packet);
        } else {
            Teleports.teleportPlayer(player, target);
        }
    }

    private static boolean playerIsNotInPortal(ServerPlayer player, Portal portal) {
        double playerWidth = 0.3; // player is 0.6 blocks wide, but player position is middle point
        double playerHeight = 1.8; // player is 1.8 blocks high
        double blockSize = 1.0; // width of a block to check far edges

        return !Utils.getDimensionNameByLevel(player.level()).equals(portal.dimension())
            || player.getX() < portal.aX() - playerWidth || player.getX() > portal.bX() + blockSize + playerWidth
            || player.getY() < portal.aY() - playerHeight || player.getY() > portal.bY() + blockSize
            || player.getZ() < portal.aZ() - playerWidth || player.getZ() > portal.bZ() + blockSize + playerWidth;
    }

    /**
     * Sets partial portal data while the user constructs a portal with the
     * {@code /setportal} command, when all three parts are set the portal is
     * automatically saved.
     *
     * @param context the command context
     * @param action  what action the user is performing
     * @return {@code 1} if successful
     */
    public static int setPortalData(CommandContext<CommandSourceStack> context, String action) {
        ServerPlayer player = context.getSource().getPlayer();
        Portal newPortalData = mutateTempPortalData(context, player, action);

        if (newPortalData == null) {
            return 0;
        }

        if (newPortalData.aX() > MIN && newPortalData.bX() > MIN && newPortalData.target() != null) {
            return setPortal(player, normalisePortal(newPortalData)) ? 1 : 0;
        }

        sendSuccess(context, player, action);

        return 1;
    }

    /**
     * Emits particles at all portal positions to show they are indeed portals.
     *
     * @param server the server object
     */
    public static void emitParticles(MinecraftServer server) {
        List<Portal> portals = PublicTeleport.storage.getPortals();

        for (Portal portal : portals) {
            for (ServerLevel level : server.getAllLevels()) {
                if (portal.dimension().equals(Utils.getDimensionNameByLevel(level))) {
                    int portalW = portal.bX() - portal.aX() + 1;
                    int portalH = portal.bY() - portal.aY() + 1;
                    int portalD = portal.bZ() - portal.aZ() + 1;
                    int volume = portalW * portalH * portalD;

                    level.sendParticles(
                        ParticleTypes.PORTAL,
                        true,
                        true,
                        portalW / 2.0 + portal.aX(),
                        portalH / 2.0 + portal.aY() - 0.5,
                        portalD / 2.0 + portal.aZ(),
                        volume * 2,
                        portalW / 5.5, // 5.5 was visually determined to fit
                        portalH / 5.5,
                        portalD / 5.5,
                        0.25); // 0.25 was visually determined to fit

                }
            }
        }
    }

    /**
     * Checks if a player has walked into a portal and teleports the player to the
     * associated target.
     *
     * @param server the server object
     */
    public static void check(MinecraftServer server) {
        List<Portal> portals = PublicTeleport.storage.getPortals();

        if (portals.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 oldPosition = playerPositions.get(player);
            if (oldPosition != null && player.position().equals(oldPosition)) {
                continue;
            }
            Boolean oldState = playerStates.get(player);
            boolean newState = false;

            for (Portal portal : portals) {
                if (playerIsNotInPortal(player, portal)) {
                    continue;
                }

                newState = true;

                if (oldState != null && !oldState) {
                    teleportOrRedirectPlayer(player, portal.target(), oldPosition);

                    return;
                }
            }

            playerPositions.put(player, player.position());
            playerStates.put(player, newState);
        }
    }
}
