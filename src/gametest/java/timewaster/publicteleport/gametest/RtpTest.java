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
import net.minecraft.world.level.Level;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Teleport;

/**
 * RTP is not in manual-testing.txt, these tests follow the code of
 * {@code commands.Rtp} and {@code TeleportSafety}. Only one RTP can run at a
 * time on the whole server, so every test runs in its own batch.
 */
public class RtpTest {
    /** The message RTP sends when it's done, with the coordinates of where the player is now. */
    private static void assertTeleported(GameTestHelper helper, TestPlayer player) {
        BlockPos pos = player.blockPosition();

        assertLastMessage(helper, player, "teleported_to", SUCCESS, pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    // the real limit is the timeout in seconds below, see TestUtils.succeedWithinSeconds
    @GameTest(environment = "public-teleport-gametest:rtp", maxTicks = 10_000_000)
    public void rtp(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        TestPlayer other = TestPlayer.spawn(helper, STAND_FAR);
        BlockPos start = player.blockPosition();
        BlockPos center = helper.getLevel().getRespawnData().pos();
        int radius = PublicTeleport.storage.getConfig().rtpRadius();

        player.run("rtp");
        assertLastMessage(helper, player, "rtp_start", WARNING);
        other.run("rtp");
        assertLastMessage(helper, other, "rtp_running", ERROR);

        TestUtils.succeedWithinSeconds(helper, 60, () -> {
            assertTeleported(helper, player);
            BlockPos pos = player.blockPosition();

            // at least 128 blocks away, within "rtpRadius" of the world spawn (+ 2 for moving to a safe block)
            helper.assertTrue(Math.abs(pos.getX() - start.getX()) > 128 || Math.abs(pos.getZ() - start.getZ()) > 128,
                "Expected the player to be more than 128 blocks away from " + start + ", but was at " + pos);
            double distance = Math.hypot(pos.getX() - center.getX(), pos.getZ() - center.getZ());
            helper.assertTrue(distance <= radius + 2,
                "Expected the player within " + radius + " blocks of the world spawn, but was " + (int) distance);

            // the old position is saved for /back
            Teleport back = PublicTeleport.storage.getTeleport(player, "back", false);
            assertEqual(helper, new BlockPos(back.x(), back.y(), back.z()), start, "position of \"back\"");
        });
    }

    @GameTest(environment = "public-teleport-gametest:rtp_failed", maxTicks = 100)
    public void rtpFailed(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos center = helper.getLevel().getRespawnData().pos();
        // with a radius of 0 the only possible target is the world spawn, which is too close to the player
        Runnable rtpAtSpawn = () -> {
            player.teleportTo(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5, Set.of(), 0,
                0, true);
            withConfig(configWith("rtpRadius", 0), () -> player.run("rtp"));
        };

        helper.startSequence()
            .thenExecute(rtpAtSpawn)
            .thenWaitUntil(() -> assertLastMessage(helper, player, "rtp_failed", ERROR))
            // a failed RTP must not block the next one (it can fail within the command, so count the messages)
            .thenExecute(rtpAtSpawn)
            .thenWaitUntil(() -> {
                assertEqual(helper, TestUtils.countMessages(player, "rtp_start"), 2L, "number of \"rtp_start\"");
                assertEqual(helper, TestUtils.countMessages(player, "rtp_running"), 0L, "number of \"rtp_running\"");
                assertLastMessage(helper, player, "rtp_failed", ERROR);
            })
            .thenSucceed();
    }

    /** The Nether has a ceiling, so RTP searches downwards from below it instead of using the height map. */
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
            // 6 blocks below the top of the Nether, + 2 for moving to a safe block
            int top = nether.getMinY() + nether.dimensionType().logicalHeight() - 6 + 2;
            helper.assertTrue(pos.getY() <= top, "Expected the player below y = " + top + ", but was at " + pos);
            helper.assertFalse(nether.getBlockState(pos.below()).isAir(),
                "Expected the player to stand on a block, but was at " + pos);
        });
    }
}
