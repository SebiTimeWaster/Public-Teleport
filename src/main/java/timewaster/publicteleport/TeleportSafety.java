package timewaster.publicteleport;

import static timewaster.publicteleport.Messages.MessageType.ERROR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import timewaster.publicteleport.records.Teleport;

/**
 * A collection of Utils to ensure safe teleportation.
 */
public final class TeleportSafety {
    private TeleportSafety() {
    }

    private static boolean blockHasCollision(Level level, @NotNull BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);

        return blockState.getBlock() != Blocks.MAGMA_BLOCK && !blockState.getCollisionShape(level, blockPos).isEmpty();
    }

    private static boolean blockIsEmpty(Level level, @NotNull BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        Block block = blockState.getBlock();

        return block != Blocks.LAVA && !(block instanceof BaseFireBlock) && block != Blocks.MAGMA_BLOCK
            && (block instanceof ScaffoldingBlock || blockState.getCollisionShape(level, blockPos).isEmpty());
    }

    private static boolean blockAreaIsEmpty(Level level, @NotNull AABB boundingBox) {
        for (BlockPos pos : BlockPos.betweenClosed(boundingBox)) {
            if (!blockIsEmpty(level, Objects.requireNonNull(pos))) {
                return false;
            }
        }

        return true;
    }

    // true=safe, false=safe on foot, null=not safe
    @Nullable
    private static Boolean isPositionTeleportableChecks(ServerPlayer player, Level level, AABB boundingBoxRide, AABB boundingBoxPlayer, boolean withRide,
        boolean ignorePlayerClipping) {
        if (!blockHasCollision(level, BlockPos.containing(boundingBoxRide.getBottomCenter()).below())
            || !blockAreaIsEmpty(level, Objects.requireNonNull(boundingBoxPlayer))) {
            return null;
        }
        if (withRide && !blockAreaIsEmpty(level, boundingBoxRide)) {
            return false;
        }

        if (!ignorePlayerClipping) {
            for (ServerPlayer onlinePlayer : Utils.getPlayersByLevel(level)) {
                if (onlinePlayer.level() == level && onlinePlayer != player && (!withRide || onlinePlayer.getRootVehicle() != player.getRootVehicle())) {
                    if (Utils.getPlayerBoundingBox(onlinePlayer, true).intersects(boundingBoxPlayer)) {
                        return null;
                    }
                    if (withRide && Utils.getPlayerBoundingBox(onlinePlayer, true).intersects(boundingBoxRide)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // true=safe, false=safe on foot, null=not safe
    @Nullable
    private static Boolean isPositionTeleportable(ServerPlayer player, int x, int y, int z, String dimension, boolean withRide, boolean ignorePlayerClipping) {
        Level level = Utils.getLevelbyDimension(player, dimension);
        BlockPos testBlockPos = new BlockPos(x, y, z);
        AABB boundingBoxPlayer = Utils.moveBoundingBoxTo(Utils.getPlayerBoundingBox(player, false), testBlockPos);
        AABB boundingBoxRide = boundingBoxPlayer;
        if (withRide) {
            boundingBoxRide = Utils.moveBoundingBoxTo(Utils.getPlayerBoundingBox(player, true), testBlockPos);
        }

        return isPositionTeleportableChecks(player, level, boundingBoxRide, boundingBoxPlayer, withRide, ignorePlayerClipping);
    }

    @Nullable
    private static BlockPos findRandomTeleportableYPosition(ServerPlayer player, ServerLevel level, ChunkPos chunkPos, int x, int z) {
        String dimension = Utils.getDimensionNameByLevel(level);

        if (level.dimensionType().hasCeiling()) {
            int top = level.getMinY() + level.dimensionType().logicalHeight() - 6; // start 6 blocks below ceiling to avoid entrapment
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
            int playerHeight = (int) Math.ceil(Utils.getPlayerBoundingBox(player, true).getYsize());
            int numberOfAirBlocks = 0;

            for (; pos.getY() > level.getMinY(); pos.move(0, -1, 0)) {
                if (blockIsEmpty(level, pos)) {
                    numberOfAirBlocks++;
                } else if (numberOfAirBlocks >= playerHeight && blockHasCollision(level, pos)
                    && Boolean.TRUE.equals(isPositionTeleportable(player, x, pos.getY() + 1, z, dimension, true, false))) {
                    return new BlockPos(x, pos.getY() + 1, z);
                } else {
                    numberOfAirBlocks = 0;
                }
            }

        } else {
            int y = level.getChunk(chunkPos.x(), chunkPos.z()).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;

            if (Boolean.TRUE.equals(isPositionTeleportable(player, x, y, z, dimension, true, false))) {
                return new BlockPos(x, y, z);
            }
        }

        return null;
    }

    private static int moveInsideChunk(int coordinate) {
        return (coordinate & ~15) + Math.clamp(coordinate & 15, 3, 12);
    }

    // picks a random position within the radius around center and at least 16
    // blocks inside the world border, or null if there is none (Y is always 0)
    @Nullable
    private static BlockPos findRandomPositionInsideBorder(ServerLevel level, BlockPos center, int radius) {
        WorldBorder border = level.getWorldBorder();
        double minX = Math.max(center.getX() - radius, border.getMinX() + 16);
        double maxX = Math.min(center.getX() + radius, border.getMaxX() - 16);
        double minZ = Math.max(center.getZ() - radius, border.getMinZ() + 16);
        double maxZ = Math.min(center.getZ() + radius, border.getMaxZ() - 16);

        if (minX >= maxX || minZ >= maxZ) {
            return null;
        }

        // positions in the corners of the box are outside the radius, so redraw them;
        // this is cheap since no chunks are loaded, the limit prevents an endless loop
        for (int i = 0; i < 50; i++) {
            double x = ThreadLocalRandom.current().nextDouble(minX, maxX);
            double z = ThreadLocalRandom.current().nextDouble(minZ, maxZ);

            if (Math.pow(x - center.getX(), 2) + Math.pow(z - center.getZ(), 2) <= (double) radius * radius) {
                return new BlockPos(moveInsideChunk((int) Math.floor(x)), 0, moveInsideChunk((int) Math.floor(z)));
            }
        }

        return null;
    }

    private static CompletableFuture<@Nullable BlockPos> findRandomTeleportablePositionAttempt(ServerPlayer player, ServerLevel level, BlockPos center,
        int dimensionRadius, int attempt) {
        BlockPos randomPos = findRandomPositionInsideBorder(level, center, dimensionRadius);
        if (attempt > 3 || randomPos == null) {
            return CompletableFuture.completedFuture(null);
        }
        int x = randomPos.getX();
        int z = randomPos.getZ();
        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));

        // safety net, moveInsideChunk() moves at most 3 blocks; 128 blocks = 8 chunks
        // minimum distance to player
        if (!level.getWorldBorder().isWithinBounds(x, z)
            || !Utils.doesPlayerClearTargetBlock(player, new BlockPos(x, level.getSeaLevel(), z), Utils.getDimensionNameByLevel(level), 128, level.getMaxY())) {
            return findRandomTeleportablePositionAttempt(player, level, center, dimensionRadius, attempt + 1);
        }

        return level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, chunkPos, 0).thenComposeAsync(ignored -> {
            BlockPos blockPos = findRandomTeleportableYPosition(player, level, chunkPos, x, z);
            if (blockPos == null) {
                return findRandomTeleportablePositionAttempt(player, level, center, dimensionRadius, attempt + 1);
            }

            return CompletableFuture.completedFuture(blockPos);
        }, level.getServer());
    }

    /**
     * Checks if a {@link Teleport} position is a valid target to be saved, if not
     * the player is notified.
     *
     * @param player the player setting the teleport
     * @param target the position to check
     * @param type   the type of teleport, used in the error message
     * @return {@code true} if the position is clear to use
     */
    public static boolean isPositionToSetTeleportable(ServerPlayer player, Teleport target, String type) {
        Boolean isTeleportable = isPositionTeleportable(player, target.x(), target.y(), target.z(), target.dimension(), false, true);

        if (isTeleportable == null) {
            Messages.sendMessage(player, "teleport_unsafe_set", ERROR, type);
            return false;
        }

        return true;
    }

    /**
     * Searches the blocks within 2 blocks of {@code target} for a position that
     * is safe for the player and their ride, and not blocked by other players.
     *
     * @param player               the player to be teleported
     * @param target               the position to check
     * @param ignorePlayerClipping if block checks should ignore players blocking
     *                                 them to make sure /tpahereall works
     * @return the teleportable position; if none was found a {@link Teleport}
     *         named {@code public_teleport_teleportable_on_foot} (safe without
     *         the ride) or {@code public_teleport_none_found}
     */
    public static Teleport findTeleportablePosition(ServerPlayer player, Teleport target, boolean ignorePlayerClipping) {
        int[] orderY = { 0, 1, -1, 2, -2 };
        // list of relative positions that are searched for around the target position;
        // x axis=high nibble; z axis=low nibble; both add 2 so they are positive
        List<Integer> orderXZ = new ArrayList<>(List.of(0, 1, 2, 3, 4, 16, 17, 18, 19, 20, 32, 33, 35, 36, 48, 49, 50, 51, 52, 64, 65, 66, 67, 68));
        boolean isTeleportableOnFoot = false;

        Collections.shuffle(orderXZ);
        orderXZ.addFirst(34); // position 0, 0 always needs to be checked first

        for (int y : orderY) {
            for (int i : orderXZ) {
                int x = ((i & 0x000000F0) >>> 4) - 2; // extract high nibble and subtract 2 for correct relation
                int z = (i & 0x0000000F) - 2; // extract low nibble and subtract 2 for correct relation
                Boolean isTeleportable = isPositionTeleportable(player, target.x() + x, target.y() + y, target.z() + z, target.dimension(), true,
                    (x == 0 && y == 0 && z == 0) ? false : ignorePlayerClipping);

                if (isTeleportable != null) {
                    if (isTeleportable) {
                        return new Teleport(target.name(), target.x() + x, target.y() + y, target.z() + z, target.yaw(), target.pitch(), target.dimension());
                    } else {
                        isTeleportableOnFoot = true;
                    }
                }
            }
        }

        return new Teleport("public_teleport_" + (isTeleportableOnFoot ? "teleportable_on_foot" : "none_found"), 0, 0, 0, 0.0f, 0.0f, "");
    }

    /**
     * Searches for a random teleportable position within {@code rtpRadius} blocks
     * of the dimensions center (spawn) and inside the world border with a maximum
     * of 3 attempts.
     *
     * @param player the player to teleport
     * @return a future resolving to a random {@link BlockPos}, or to
     *         {@code null} if none was found
     */
    public static CompletableFuture<BlockPos> findRandomTeleportablePosition(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos center = level.dimension().equals(Level.OVERWORLD) ? level.getRespawnData().pos() : BlockPos.ZERO;
        int radius = PublicTeleport.storage.getConfig().rtpRadius();

        return findRandomTeleportablePositionAttempt(player, level, center, radius, 1);
    }
}
