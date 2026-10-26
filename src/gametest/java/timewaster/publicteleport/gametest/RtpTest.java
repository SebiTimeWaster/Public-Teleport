package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.withConfig;

import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Teleport;

/**
 * RTP is not in manual-testing.txt, these tests follow the code of
 * {@code commands.Rtp} and {@code TeleportSafety}. Only one RTP can run at a
 * time on the whole server, so every test runs in its own batch.
 */
@SuppressWarnings("null")
public class RtpTest {
    /**
     * The message RTP sends when it's done, with the coordinates of where the
     * player is now.
     */
    private static void assertTeleported(GameTestHelper helper, TestPlayer player) {
        BlockPos pos = player.blockPosition();

        assertLastMessage(helper, player, "teleported_to", SUCCESS, pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /**
     * Checks that the player is within "rtpRadius" (+ 2 for moving to a safe
     * block) of the center RTP searches around.
     */
    private static void assertWithinRadius(GameTestHelper helper, TestPlayer player, BlockPos center, String centerName) {
        int radius = PublicTeleport.storage.getConfig().rtpRadius();
        BlockPos pos = player.blockPosition();
        double distance = Math.hypot(pos.getX() - center.getX(), pos.getZ() - center.getZ());

        helper.assertTrue(distance <= radius + 2,
            "Expected the player within " + radius + " blocks of " + centerName + ", but was " + (int) distance);
    }

    // the real limit is the timeout in seconds below, see
    // TestUtils.succeedWithinSeconds
    @GameTest(environment = "public-teleport-gametest:rtp", maxTicks = 10_000_000)
    public void rtp(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        TestPlayer other = TestPlayer.spawn(helper, STAND_FAR);
        BlockPos start = player.blockPosition();
        BlockPos center = helper.getLevel().getRespawnData().pos();

        player.run("rtp");
        assertLastMessage(helper, player, "rtp_start", WARNING);
        other.run("rtp");
        assertLastMessage(helper, other, "rtp_running", ERROR);

        TestUtils.succeedWithinSeconds(helper, 60, () -> {
            assertTeleported(helper, player);
            BlockPos pos = player.blockPosition();

            helper.assertTrue(Math.abs(pos.getX() - start.getX()) > 128 || Math.abs(pos.getZ() - start.getZ()) > 128,
                "Expected the player to be more than 128 blocks away from " + start + ", but was at " + pos);
            assertWithinRadius(helper, player, center, "the world spawn");

            // the old position is saved for /back
            Teleport back = PublicTeleport.storage.getTeleport(player, "back", false);
            assertEqual(helper, new BlockPos(back.x(), back.y(), back.z()), start, "position of \"back\"");
        });
    }

    @GameTest(environment = "public-teleport-gametest:rtp_failed", maxTicks = 100)
    public void rtpFailed(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos center = helper.getLevel().getRespawnData().pos();
        // config.json does not allow a radius below 200, so this one is set directly:
        // with a radius of 0 the only possible target is the world spawn (or up to 2
        // blocks next to it, away from the chunk border), which is less than 128 blocks
        // away from the player
        Runnable rtpAtSpawn = () -> {
            player.teleportTo(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5, Set.of(), 0,
                0, true);
            withConfig(configWith("rtpRadius", 0), () -> player.run("rtp"));
        };

        helper.startSequence()
            .thenExecute(rtpAtSpawn)
            .thenWaitUntil(() -> assertLastMessage(helper, player, "rtp_failed", ERROR))
            // a failed RTP must not block the next one (it can fail within the command, so
            // count the messages)
            .thenExecute(rtpAtSpawn)
            .thenWaitUntil(() -> {
                assertEqual(helper, TestUtils.countMessages(player, "rtp_start"), 2L, "number of \"rtp_start\"");
                assertEqual(helper, TestUtils.countMessages(player, "rtp_running"), 0L, "number of \"rtp_running\"");
                assertLastMessage(helper, player, "rtp_failed", ERROR);
            })
            .thenSucceed();
    }

    /**
     * The Nether has a ceiling, so RTP searches downwards from below it instead of
     * using the height map. Distances are the same as in the Overworld, around 0,
     * 0 instead of the world spawn.
     */
    @GameTest(environment = "public-teleport-gametest:rtp_nether", maxTicks = 10_000_000)
    public void rtpInNether(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);

        player.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0, 0, true);
        player.run("rtp");
        assertLastMessage(helper, player, "rtp_start", WARNING);

        // generating Nether chunks takes a while
        TestUtils.succeedWithinSeconds(helper, 120, () -> {
            assertTeleported(helper, player);
            BlockPos pos = player.blockPosition();

            assertEqual(helper, player.level().dimension(), Level.NETHER, "dimension");
            // the player started at 0, 0
            helper.assertTrue(Math.abs(pos.getX()) > 128 || Math.abs(pos.getZ()) > 128,
                "Expected the player to be more than 128 blocks away from 0, 0, but was at " + pos);
            assertWithinRadius(helper, player, BlockPos.ZERO, "0, 0");
            // 6 blocks below the top of the Nether, + 2 for moving to a safe block
            int top = nether.getMinY() + nether.dimensionType().logicalHeight() - 6 + 2;
            helper.assertTrue(pos.getY() <= top, "Expected the player below y = " + top + ", but was at " + pos);
            helper.assertFalse(nether.getBlockState(pos.below()).isAir(),
                "Expected the player to stand on a block, but was at " + pos);
        });
    }

    /**
     * Changes the world border until the test ends. The player is put into
     * creative mode, since the test area may end up outside the border, where
     * players take damage.
     */
    private static WorldBorder setWorldBorder(GameTestHelper helper, TestPlayer player, double centerX, double centerZ, double size) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        double oldCenterX = border.getCenterX();
        double oldCenterZ = border.getCenterZ();
        double oldSize = border.getSize();

        TestUtils.onTestEnd(helper, () -> {
            border.setCenter(oldCenterX, oldCenterZ);
            border.setSize(oldSize);
        });
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        player.setGameMode(GameType.CREATIVE);

        return border;
    }

    /**
     * The world spawn is on the east edge of the world border, so only the west
     * side can be searched, which must still work.
     */
    @GameTest(environment = "public-teleport-gametest:rtp_border", maxTicks = 10_000_000)
    public void rtpWorldBorderNextToSpawn(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos center = helper.getLevel().getRespawnData().pos();
        WorldBorder border = setWorldBorder(helper, player, center.getX() - 1000, center.getZ(), 2000);

        player.run("rtp");
        assertLastMessage(helper, player, "rtp_start", WARNING);

        TestUtils.succeedWithinSeconds(helper, 60, () -> {
            assertTeleported(helper, player);
            BlockPos pos = player.blockPosition();

            // 16 blocks inside the border, - 2 for moving to a safe block
            helper.assertTrue(border.isWithinBounds(pos.getX(), pos.getZ(), -14),
                "Expected the player at least 14 blocks inside the world border, but was at " + pos);
            assertWithinRadius(helper, player, center, "the world spawn");
        });
    }

    /**
     * The world border is so far away from the world spawn that no position is
     * both within "rtpRadius" and inside the border.
     */
    @GameTest(environment = "public-teleport-gametest:rtp_border_outside", maxTicks = 100)
    public void rtpWorldBorderOutsideRadius(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos center = helper.getLevel().getRespawnData().pos();
        BlockPos start = player.blockPosition();

        setWorldBorder(helper, player, center.getX() + 100_000, center.getZ(), 1000);
        player.run("rtp");

        helper.succeedWhen(() -> {
            assertLastMessage(helper, player, "rtp_failed", ERROR);
            assertEqual(helper, player.blockPosition(), start, "position of the player");
        });
    }

    /**
     * A player who changes dimension while RTP still searches is not teleported
     * to the position found in the old dimension.
     */
    @GameTest(environment = "public-teleport-gametest:rtp_dimension_change", maxTicks = 10_000_000)
    public void rtpDimensionChanged(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);

        player.run("rtp");
        assertLastMessage(helper, player, "rtp_start", WARNING);
        // the search waits for chunks to load, so it finishes in a later tick
        player.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0, 0, true);

        TestUtils.succeedWithinSeconds(helper, 60, () -> {
            assertLastMessage(helper, player, "rtp_failed", ERROR);
            assertEqual(helper, player.level().dimension(), Level.NETHER, "dimension");
            assertEqual(helper, player.blockPosition(), new BlockPos(0, 64, 0), "position of the player");
        });
    }
}
