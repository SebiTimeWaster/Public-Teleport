package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.COMMAND;
import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.Registrar.SuggestionType.PORTALS;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.Utils;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Portal commands, registered by {@link Registrar}.
 */
public final class Portals {
    private static final Integer MIN = Integer.MIN_VALUE;
    private static final Pattern HOST_PATTERN = Pattern.compile(
        "^(\\[[0-9A-Fa-f:]+\\]|[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*)$");
    private static Map<String, Portal> tempPortalData = new HashMap<String, Portal>();

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
        portal = normalisePortal(portal);

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

    private static int setPortalData(CommandContext<CommandSourceStack> context, String action) {
        ServerPlayer player = context.getSource().getPlayer();

        Portal newPortalData = mutateTempPortalData(context, player, action);

        if (newPortalData == null) {
            return 0;
        }

        if (newPortalData.aX() > MIN && newPortalData.bX() > MIN && newPortalData.target() != null) {
            return setPortal(player, newPortalData) ? 1 : 0;
        }

        sendSuccess(context, player, action);

        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        boolean portalCommandsOnlyOp = PublicTeleport.storage.getConfig().portalCommandsOnlyOp();
        PermissionCheck hasPermission = portalCommandsOnlyOp ? Commands.LEVEL_OWNERS : Commands.LEVEL_ALL;

        dispatcher.register(Commands.literal("setportal").requires(Commands.hasPermission(hasPermission))
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.word()))
                .then(Commands.literal("from").executes((context) -> setPortalData(context, "from")))
                .then(Commands.literal("to").executes((context) -> setPortalData(context, "to")))
                .then(Commands.literal("target").executes((context) -> setPortalData(context, "targetPosition"))
                    .then(Commands.argument("url", Objects.requireNonNull(StringArgumentType.string()))
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .executes((context) -> setPortalData(context, "targetUrl"))))));

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
