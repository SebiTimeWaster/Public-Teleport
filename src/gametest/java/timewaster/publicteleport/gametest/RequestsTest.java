package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.HEADLINE;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.buildFloor;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.withConfig;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The "Requests" section of manual-testing.txt, plus the TPA teleport itself
 * from the "Teleports" section.
 */
public class RequestsTest {
    private static String name(TestPlayer player) {
        return player.getName().getString();
    }

    /** Checks that {@code player} ended up at most {@code distance} blocks (horizontally) from {@code target}. */
    private static void assertNear(GameTestHelper helper, TestPlayer player, TestPlayer target, int distance) {
        BlockPos pos = player.blockPosition();
        BlockPos targetPos = target.blockPosition();

        helper.assertTrue(Math.abs(pos.getX() - targetPos.getX()) <= distance
            && Math.abs(pos.getZ() - targetPos.getZ()) <= distance
            && Math.abs(pos.getY() - targetPos.getY()) <= distance,
            name(player) + " should be at most " + distance + " blocks from " + name(target) + ", but is at "
                + TestUtils.relative(helper, pos) + " and " + name(target) + " at " + TestUtils.relative(helper, targetPos));
    }

    @GameTest
    public void tpaSent(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpa " + name(receiver));
        assertLastMessage(helper, sender, "request_sent", SUCCESS, name(receiver));
        assertLastMessage(helper, receiver, "request_received", HEADLINE, name(sender));
        helper.succeed();
    }

    @GameTest
    public void tpahereSent(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpahere " + name(receiver));
        assertLastMessage(helper, sender, "request_sent", SUCCESS, name(receiver));
        assertLastMessage(helper, receiver, "request_received_rev", HEADLINE, name(sender));
        helper.succeed();
    }

    /**
     * /tpahereall asks every player on the server, so this test runs in its own
     * batch, without players of other tests. Also covers "20 players find blocks
     * around you, up to 2 blocks outward of position" from the "Teleports"
     * section.
     */
    @GameTest(environment = "public-teleport-gametest:tpahereall")
    public void tpahereallSentAndAccepted(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, new BlockPos(4, 1, 4));
        List<TestPlayer> receivers = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            // all around the edge of the area, more than 2 blocks away from the sender
            receivers.add(TestPlayer.spawn(helper, i < 10 ? new BlockPos(0, 1, i % 8) : new BlockPos(7, 1, i % 8)));
        }

        sender.runAsOwner("tpahereall");

        for (TestPlayer receiver : receivers) {
            assertLastMessage(helper, receiver, "request_received_rev", HEADLINE, name(sender));
        }
        assertEqual(helper, TestUtils.countMessages(sender, "request_sent"), (long) receivers.size(),
            "number of \"request_sent\" messages");

        for (TestPlayer receiver : receivers) {
            receiver.run("tpaccept");
            assertLastMessage(helper, receiver, "teleported_to", SUCCESS, name(sender));
            assertNear(helper, receiver, sender, 2);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void tpaTimeout(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        // times out at the next cleanup, which runs once per second
        withConfig(configWith("requestTimeout", 0), () -> sender.run("tpa " + name(receiver)));

        helper.succeedWhen(() -> {
            assertLastMessage(helper, sender, "request_timedout_sender", WARNING, name(receiver));
            assertLastMessage(helper, receiver, "request_timedout_receiver", WARNING, name(sender));
        });
    }

    @GameTest
    public void tpaSelf(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.run("tpa " + name(player));
        assertLastMessage(helper, player, "request_teleport_self", WARNING);
        helper.succeed();
    }

    @GameTest
    public void tpaRequestAlreadyExists(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);
        TestPlayer otherReceiver = TestPlayer.spawn(helper, new BlockPos(1, 1, 6));

        sender.run("tpa " + name(receiver));
        sender.run("tpa " + name(otherReceiver));
        assertLastMessage(helper, sender, "request_old_exist", ERROR, name(receiver), "/tpcancel");
        helper.succeed();
    }

    @GameTest
    public void tpcancelNoRequest(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.run("tpcancel");
        assertLastMessage(helper, player, "request_no_exist", ERROR);
        helper.succeed();
    }

    @GameTest
    public void tpcancel(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpa " + name(receiver));
        sender.run("tpcancel");
        assertLastMessage(helper, sender, "request_cancelled_sender", SUCCESS);
        assertLastMessage(helper, receiver, "request_cancelled_receiver", WARNING, name(sender));

        receiver.run("tpaccept");
        assertLastMessage(helper, receiver, "request_no_exist", ERROR);
        helper.succeed();
    }

    @GameTest
    public void tpacceptNoRequest(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.run("tpaccept");
        assertLastMessage(helper, player, "request_no_exist", ERROR);
        helper.succeed();
    }

    @GameTest
    public void tpacceptSenderOffline(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpa " + name(receiver));
        sender.leave();
        receiver.run("tpaccept");
        assertLastMessage(helper, receiver, "request_sender_no_ingame", ERROR, name(sender));
        helper.succeed();
    }

    @GameTest
    public void tpaccept(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpa " + name(receiver));
        receiver.run("tpaccept " + name(sender));
        assertLastMessage(helper, receiver, "request_accepted_receiver", SUCCESS, name(sender));
        assertLastMessage(helper, sender, "teleported_to", SUCCESS, name(receiver));
        assertNear(helper, sender, receiver, 2);
        TestUtils.assertAt(helper, receiver, STAND_FAR);
        helper.succeed();
    }

    @GameTest
    public void tpacceptTpahere(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpahere " + name(receiver));
        receiver.run("tpaccept");
        assertLastMessage(helper, sender, "request_accepted_sender", SUCCESS, name(receiver));
        assertLastMessage(helper, receiver, "teleported_to", SUCCESS, name(sender));
        assertNear(helper, receiver, sender, 2);
        TestUtils.assertAt(helper, sender, STAND);
        helper.succeed();
    }

    @GameTest
    public void tpdenyNoRequest(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.run("tpdeny");
        assertLastMessage(helper, player, "request_no_exist", ERROR);
        helper.succeed();
    }

    @GameTest
    public void tpdeny(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND_FAR);

        sender.run("tpa " + name(receiver));
        receiver.run("tpdeny " + name(sender));
        assertLastMessage(helper, sender, "request_denied_sender", WARNING, name(receiver));
        assertLastMessage(helper, receiver, "request_denied_receiver", SUCCESS, name(sender));
        TestUtils.assertAt(helper, sender, STAND);
        helper.succeed();
    }
}
