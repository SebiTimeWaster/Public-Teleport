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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Requests" section of manual-testing.txt, plus the TPA teleport itself
 * from the "Teleports" section.
 */
@SuppressWarnings("null")
public class RequestsTest {
    /** The two players of a request. */
    private record Players(TestPlayer sender, TestPlayer receiver) {
    }

    private static String name(TestPlayer player) {
        return player.getName().getString();
    }

    /**
     * Spawns a sender at {@link TestUtils#STAND} and a receiver at
     * {@link TestUtils#STAND_FAR}, far enough apart to teleport between them.
     */
    private static Players spawnPlayers(GameTestHelper helper) {
        buildFloor(helper);

        return new Players(TestPlayer.spawn(helper, STAND), TestPlayer.spawn(helper, STAND_FAR));
    }

    /**
     * Like {@link #spawnPlayers}, then the sender sends a request with
     * {@code command}, i.e. "tpa" or "tpahere".
     */
    private static Players sendRequest(GameTestHelper helper, String command) {
        Players players = spawnPlayers(helper);

        players.sender().run(command + " " + name(players.receiver()));

        return players;
    }

    /**
     * Like {@link #sendRequest} with "tpa", then the sender goes offline.
     */
    private static Players sendRequestAndLeave(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpa");

        players.sender().leave();

        return players;
    }

    /**
     * Checks that {@code player} ended up at most {@code distance} blocks
     * (horizontally) from {@code target}.
     */
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
        Players players = sendRequest(helper, "tpa");

        assertLastMessage(helper, players.sender(), "request_sent", SUCCESS, name(players.receiver()));
        assertLastMessage(helper, players.receiver(), "request_received", HEADLINE, name(players.sender()));
        helper.succeed();
    }

    @GameTest
    public void tpahereSent(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpahere");

        assertLastMessage(helper, players.sender(), "request_sent", SUCCESS, name(players.receiver()));
        assertLastMessage(helper, players.receiver(), "request_received_rev", HEADLINE, name(players.sender()));
        helper.succeed();
    }

    /** Spawns the sender and 20 receivers for the /tpahereall tests. */
    private static List<TestPlayer> spawnTpahereallReceivers(GameTestHelper helper) {
        List<TestPlayer> receivers = new ArrayList<>();

        buildFloor(helper);
        for (int i = 0; i < 20; i++) {
            // in two rows at the edge of the area, more than 4 blocks away from STAND_FAR
            receivers.add(TestPlayer.spawn(helper, new BlockPos(i < 10 ? 0 : 1, 1, i % 8)));
        }

        return receivers;
    }

    /**
     * /tpahereall asks every player on the server, so this test runs in its own
     * batch, without players of other tests. Also covers "20 players find blocks
     * around you, up to 2 blocks outward of position" from the "Teleports"
     * section.
     */
    @GameTest(environment = "public-teleport-gametest:tpahereall")
    public void tpahereallSentAndAccepted(GameTestHelper helper) {
        List<TestPlayer> receivers = spawnTpahereallReceivers(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND_FAR);

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

    /**
     * /tpcancel after /tpahereall withdraws the requests to all players. Runs in
     * its own batch for the same reason as {@link #tpahereallSentAndAccepted}.
     */
    @GameTest(environment = "public-teleport-gametest:tpahereall_cancel")
    public void tpahereallCancelled(GameTestHelper helper) {
        List<TestPlayer> receivers = spawnTpahereallReceivers(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND_FAR);

        sender.runAsOwner("tpahereall");
        sender.run("tpcancel");
        // one message for each receiver, naming them
        assertEqual(helper, TestUtils.messageParameters(sender, "request_cancelled_sender_named"),
            receivers.stream().map(receiver -> List.of(name(receiver))).toList(), "\"request_cancelled_sender_named\" messages");

        for (TestPlayer receiver : receivers) {
            assertLastMessage(helper, receiver, "request_cancelled_receiver", WARNING, name(sender));
            receiver.run("tpaccept");
            assertLastMessage(helper, receiver, "request_no_exist", ERROR);
        }

        // the requests are gone, so a new one can be sent
        sender.runAsOwner("tpahereall");
        assertLastMessage(helper, sender, "request_sent", SUCCESS, name(receivers.getLast()));
        helper.succeed();
    }

    /**
     * /tpahereall without any other player online. Runs in its own batch, so no
     * players of other tests are online.
     */
    @GameTest(environment = "public-teleport-gametest:tpahereall_alone")
    public void tpahereallNoOtherPlayers(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.runAsOwner("tpahereall");
        assertLastMessage(helper, player, "request_no_players", WARNING);
        // no request was created, so there is nothing to cancel
        player.run("tpcancel");
        assertLastMessage(helper, player, "request_no_exist", ERROR);
        helper.succeed();
    }

    @GameTest
    public void tpahereallNeedsOwner(GameTestHelper helper) {
        Players players = spawnPlayers(helper);

        players.sender().run("tpahereall");
        TestUtils.assertNoMessage(helper, players.sender());
        TestUtils.assertNoMessage(helper, players.receiver());
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void tpaTimeout(GameTestHelper helper) {
        Players players = spawnPlayers(helper);
        TestPlayer sender = players.sender();
        TestPlayer receiver = players.receiver();

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

    /** Tab completion offers the other players, but not the player themselves. */
    @GameTest
    public void tpaSuggestions(GameTestHelper helper) {
        Players players = spawnPlayers(helper);
        List<String> suggestions = players.sender().getSuggestions("tpa ", false);

        helper.assertTrue(suggestions.contains(name(players.receiver())),
            "Expected the suggestions to contain " + name(players.receiver()) + ", but were " + suggestions);
        helper.assertFalse(suggestions.contains(name(players.sender())),
            "Expected the suggestions not to contain the player themselves, but were " + suggestions);
        helper.succeed();
    }

    @GameTest
    public void tpaRequestAlreadyExists(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpa");
        TestPlayer otherReceiver = TestPlayer.spawn(helper, new BlockPos(1, 1, 6));

        players.sender().run("tpa " + name(otherReceiver));
        assertLastMessage(helper, players.sender(), "request_old_exist", ERROR, name(players.receiver()), "/tpcancel");
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
        Players players = sendRequest(helper, "tpa");
        TestPlayer receiver = players.receiver();

        players.sender().run("tpcancel");
        assertLastMessage(helper, players.sender(), "request_cancelled_sender_named", SUCCESS, name(receiver));
        assertLastMessage(helper, receiver, "request_cancelled_receiver", WARNING, name(players.sender()));

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
        Players players = sendRequestAndLeave(helper);
        TestPlayer receiver = players.receiver();

        receiver.run("tpaccept");
        assertLastMessage(helper, receiver, "request_sender_no_ingame", ERROR, name(players.sender()));
        helper.succeed();
    }

    /** The sender is teleported to the receiver and can go back with /back. */
    @GameTest
    public void tpaccept(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpa");
        TestPlayer sender = players.sender();
        TestPlayer receiver = players.receiver();

        receiver.run("tpaccept " + name(sender));
        assertLastMessage(helper, receiver, "request_accepted_receiver", SUCCESS, name(sender));
        assertLastMessage(helper, sender, "teleported_to", SUCCESS, name(receiver));
        assertNear(helper, sender, receiver, 2);
        TestUtils.assertAt(helper, receiver, STAND_FAR);
        TestUtils.assertTeleportAt(helper, PublicTeleport.storage.getTeleport(sender, "back", false), STAND);
        helper.succeed();
    }

    @GameTest
    public void tpacceptTpahere(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpahere");
        TestPlayer sender = players.sender();
        TestPlayer receiver = players.receiver();

        receiver.run("tpaccept");
        assertLastMessage(helper, sender, "request_accepted_sender", SUCCESS, name(receiver));
        assertLastMessage(helper, receiver, "teleported_to", SUCCESS, name(sender));
        assertNear(helper, receiver, sender, 2);
        TestUtils.assertAt(helper, sender, STAND);
        helper.succeed();
    }

    /** The receiver is in the Nether, the sender in the Overworld. */
    @GameTest
    public void tpacceptOtherDimension(GameTestHelper helper) {
        Players players = spawnPlayers(helper);
        TestPlayer sender = players.sender();
        TestPlayer receiver = players.receiver();
        ServerLevel nether = TestUtils.nether(helper);
        BlockPos room = new BlockPos(80, 70, 40);

        TestUtils.buildRoom(nether, room);
        receiver.moveTo(nether, room);
        sender.run("tpa " + name(receiver));
        receiver.run("tpaccept");
        assertLastMessage(helper, sender, "teleported_to", SUCCESS, name(receiver));
        assertEqual(helper, sender.level().dimension(), Level.NETHER, "dimension of " + name(sender));
        assertNear(helper, sender, receiver, 2);
        helper.succeed();
    }

    /** Without a name, /tpaccept and /tpdeny answer the oldest request first. */
    @GameTest
    public void tpacceptTpdenyOldestFirst(GameTestHelper helper) {
        Players players = sendRequest(helper, "tpa");
        TestPlayer first = players.sender();
        TestPlayer receiver = players.receiver();
        TestPlayer second = TestPlayer.spawn(helper, new BlockPos(1, 1, 6));

        second.run("tpa " + name(receiver));

        receiver.run("tpaccept");
        assertLastMessage(helper, receiver, "request_accepted_receiver", SUCCESS, name(first));
        assertNear(helper, first, receiver, 2);
        TestUtils.assertAt(helper, second, new BlockPos(1, 1, 6));

        receiver.run("tpdeny");
        assertLastMessage(helper, second, "request_denied_sender", WARNING, name(receiver));
        TestUtils.assertAt(helper, second, new BlockPos(1, 1, 6));
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
        Players players = sendRequest(helper, "tpa");
        TestPlayer sender = players.sender();
        TestPlayer receiver = players.receiver();

        receiver.run("tpdeny " + name(sender));
        assertLastMessage(helper, sender, "request_denied_sender", WARNING, name(receiver));
        assertLastMessage(helper, receiver, "request_denied_receiver", SUCCESS, name(sender));
        TestUtils.assertAt(helper, sender, STAND);
        helper.succeed();
    }

    /** A request of a player who went offline can still be denied, and is gone afterwards. */
    @GameTest
    public void tpdenySenderOffline(GameTestHelper helper) {
        Players players = sendRequestAndLeave(helper);
        TestPlayer receiver = players.receiver();

        receiver.run("tpdeny");
        assertLastMessage(helper, receiver, "request_denied_receiver", SUCCESS, name(players.sender()));
        receiver.run("tpdeny");
        assertLastMessage(helper, receiver, "request_no_exist", ERROR);
        helper.succeed();
    }
}
