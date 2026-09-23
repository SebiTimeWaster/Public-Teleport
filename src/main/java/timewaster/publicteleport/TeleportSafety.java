package timewaster.publicteleport;

import static timewaster.publicteleport.Messages.MessageType.ERROR;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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

    private static boolean isBlockEmpty(Level level, @NotNull BlockPos blockPos) {
        Block block = level.getBlockState(blockPos).getBlock();

        return block != Blocks.LAVA && block != Blocks.FIRE
            && (block instanceof ScaffoldingBlock || !blockHasCollision(level, blockPos));
    }

    private static boolean isBlockTeleportable(Level level, BlockPos blockPos) {
        return blockHasCollision(level, blockPos.below())
            && isBlockEmpty(level, blockPos)
            && isBlockEmpty(level, blockPos.above());
    }

    private static int findTeleportableYBelowCeiling(ServerLevel level, int x, int z) {
        // start 6 blocks below ceiling to avoid entrapment
        int top = level.getMinY() + level.dimensionType().logicalHeight() - 6;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);

        for (; pos.getY() > level.getMinY(); pos.move(0, -1, 0)) {
            if (isBlockTeleportable(level, pos)) {
                return pos.getY();
            }
        }

        return Integer.MIN_VALUE;
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
     *         the max homes limit; {@code null} if the position was unsafe or a
     *         file error occured
     */
    @Nullable
    public static Boolean setSpawnableTeleport(ServerPlayer player, Teleport target, boolean isWarp, String type) {
        if (!isBlockTeleportable(player, target)) {
            Messages.sendMessage(player, "teleport_unsafe_set", ERROR, type);
            return null;
        }

        return PublicTeleport.storage.setTeleport(player, target, isWarp);
    }

    /**
     * Checks if {@code blockPos} is a safe place to stand on and if it is not
     * occupied by other players.
     *
     * @param player   the player trying to teleport here
     * @param level    the level of the block to test
     * @param blockPos the block position to check
     * @return {@code true} if safe
     */
    public static boolean isBlockTeleportableAndWithoutPlayers(ServerPlayer player, Level level, BlockPos blockPos) {
        boolean isBlockAvailable = isBlockTeleportable(level, blockPos);
        boolean isBlockedByPlayer = false;

        if (isBlockAvailable) {
            List<ServerPlayer> onlinePlayers = Utils.getPlayersByLevel(level);

            for (ServerPlayer onlinePlayer : onlinePlayers) {
                // player width/height
                if (onlinePlayer != player
                    && !doesPlayerClearTarget(onlinePlayer, blockPos, Utils.getDimensionNameByLevel(level), 0.6, 1.8)) {
                    isBlockedByPlayer = true;
                }
            }
        }

        return isBlockAvailable && !isBlockedByPlayer;
    }

    /**
     * Searches for a random teleportable position within {@code rtpRadius} blocks
     * of the dimensions center (spawn) with a maximum of 3 attempts.
     *
     * @param player the player to teleport
     * @return a future resolving to a random {@link Teleport} position, or to
     *         {@code null} if none was found
     */
    public static CompletableFuture<BlockPos> findRandomTeleportablePosition(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos center = level.dimension().equals(Level.OVERWORLD) ? level.getRespawnData().pos() : BlockPos.ZERO;
        int radius = PublicTeleport.storage.getConfig().rtpRadius();

        return findRandomTeleportablePositionAttempt(player, level, center, radius, 1);
    }

    private static CompletableFuture<BlockPos> findRandomTeleportablePositionAttempt(ServerPlayer player,
        ServerLevel level, BlockPos center, int radius, int attempt) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
        // 128 blocks = 8 chunks minimum distance
        if (!doesPlayerClearTarget(player, new BlockPos(x, level.getSeaLevel(), z),
            Utils.getDimensionNameByLevel(level), 128, level.getMaxY())) {
            if (attempt >= 3) {
                return CompletableFuture.completedFuture(null);
            } else {
                return findRandomTeleportablePositionAttempt(player, level, center, radius, attempt + 1);
            }
        }
        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));

        return level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, chunkPos, 0)
            .thenComposeAsync(ignored -> {
                LevelChunk levelChunk = level.getChunk(chunkPos.x(), chunkPos.z());
                int y;
                if (level.dimensionType().hasCeiling()) {
                    y = findTeleportableYBelowCeiling(level, x, z);
                } else {
                    y = levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
                }
                BlockPos blockPos = new BlockPos(x, y, z);

                PublicTeleport.LOGGER.info("random pos: {}", blockPos);

                if (y > Integer.MIN_VALUE && isBlockTeleportableAndWithoutPlayers(player, level, blockPos)) {
                    return CompletableFuture.completedFuture(blockPos);
                }

                if (attempt >= 3) {
                    return CompletableFuture.completedFuture(null);
                }

                return findRandomTeleportablePositionAttempt(player, level, center, radius, attempt + 1);
            }, level.getServer());
    }

    /**
     * Checks if the player's position collides with a block positions plus a
     * specified clearance around it.
     *
     * @param player      the player to check
     * @param blockPos    the block position to check
     * @param dimension   the dimension the block position is in
     * @param clearanceXZ the clearance in the X and Z directions
     * @param clearanceY  the clearance in the Y direction
     * @return {@code true} if player clears the area
     */
    public static boolean doesPlayerClearTarget(ServerPlayer player, BlockPos blockPos, String dimension,
        double clearanceXZ, double clearanceY) {

        return !Utils.getDimensionNameByLevel(player.level()).equals(dimension)
            || Math.abs(player.getX() - (blockPos.getX() + 0.5)) > clearanceXZ // middle point of block
            || Math.abs(player.getY() - (blockPos.getY() + 0.01)) > clearanceY // slightly above ground
            || Math.abs(player.getZ() - (blockPos.getZ() + 0.5)) > clearanceXZ;
    }

    /**
     * Gets the players int position and rounds up fractional Y positions.
     *
     * @param player the player whos position to get
     * @return the position with corrected Y position
     */
    public static BlockPos getPlayerBlockPos(ServerPlayer player) {
        double playerY = player.getY();
        double fractionY = playerY - Math.floor(playerY);
        // prevent weird scaffolding Y = x.00032, a carpet is 0.0625 high
        double realY = fractionY > 0.06 ? Math.ceil(playerY) : Math.floor(playerY);

        return new BlockPos((int) Math.floor(player.getX()), (int) realY, (int) Math.floor(player.getZ()));
    }

    /**
     * Checks if a specified {@link BlockPos} is a valid teleport target.
     *
     * @param player the player to be teleported
     * @param target the position to check
     * @return {@code true} is position is clear to use
     */
    public static boolean isBlockTeleportable(ServerPlayer player, Teleport target) {
        BlockPos blockPos = new BlockPos(target.x(), target.y(), target.z());
        Level level = Utils.getLevelbyDimension(player, target.dimension());

        return isBlockTeleportable(level, blockPos);
    }

    /**
     * Checks if any of the blocks in a certain radius around the given
     * {@link target} position is a valid teleport target and if no other player is
     * currently blocking it.
     *
     * @param player        the player to be teleported
     * @param target        the position to check
     * @param ignorePlayers if block checks around the target block should ignore
     *                          players blocking them to make sure /tpahereall works
     * @return {@link Teleport} the position that is teleportable to or {@code null}
     *         is none was found
     */
    @Nullable
    public static Teleport findTeleportablePosition(ServerPlayer player, Teleport target, boolean ignorePlayers) {
        Level level = Utils.getLevelbyDimension(player, target.dimension());

        if (!isBlockTeleportableAndWithoutPlayers(player, level, new BlockPos(target.x(), target.y(), target.z()))) {
            int[] orderY = { 0, 1, -1, 2, -2 };
            List<Integer> orderXZ = Arrays.asList(0, 1, 2, 3, 4, 16, 17, 18, 19, 20, 32, 33, 35, 36, 48, 49, 50, 51, 52,
                64, 65, 66, 67, 68); // this defines the order blocks are searched in
            Collections.shuffle(orderXZ);

            for (int y : orderY) {
                for (int i : orderXZ) {
                    int x = ((i & 0x000000F0) >>> 4) - 2;
                    int z = (i & 0x0000000F) - 2;
                    BlockPos testPos = new BlockPos(target.x() + x, target.y() + y, target.z() + z);

                    if ((ignorePlayers && isBlockTeleportable(level, testPos))
                        || isBlockTeleportableAndWithoutPlayers(player, level, testPos)) {
                        return new Teleport(
                            target.name(),
                            target.x() + x,
                            target.y() + y,
                            target.z() + z,
                            target.yaw(),
                            target.pitch(),
                            target.dimension());
                    }
                }
            }

            return null;
        }

        return target;
    }

    /**
     * Checks if a player position and a {@link Teleport} target intersect within
     * specified clearances
     *
     * @param player      the player whos position to check
     * @param target      the position to check
     * @param clearanceXZ the minimum clearance in the X and Z directions needed
     * @param clearanceY  the minimum clearance in the Y direction needed
     * @return
     */
    public static boolean doesPlayerClearTarget(ServerPlayer player, Teleport target, double clearanceXZ,
        double clearanceY) {
        return doesPlayerClearTarget(player, new BlockPos(target.x(), target.y(), target.z()), target.dimension(),
            clearanceXZ, clearanceY);
    }
}
