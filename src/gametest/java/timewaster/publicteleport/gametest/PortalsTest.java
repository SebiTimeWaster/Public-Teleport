package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessageParts;
import static timewaster.publicteleport.gametest.TestUtils.buildFloor;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.uniqueName;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Portal;

/**
 * Portals are not in manual-testing.txt, these tests follow the code of
 * {@code Portals} and {@code commands.Portals}. Portals are shared by all
 * players and teleport anyone who walks into them, so every test uses its own
 * Portal names and deletes its Portals at the end.
 */
public class PortalsTest {
    /** The block the player looks at for "from" and "to" of a one block Portal. */
    private static final BlockPos PORTAL_BLOCK = new BlockPos(3, 2, 5);
    /** Where to stand to look at {@link #PORTAL_BLOCK}: 3 blocks north of it, facing south. */
    private static final BlockPos LOOK_AT_PORTAL = new BlockPos(3, 1, 2);

    private static Portal findPortal(String name) {
        return PublicTeleport.storage.getPortals().stream().filter(portal -> portal.target().name().equals(name))
            .findFirst().orElse(null);
    }

    private static void deletePortal(TestPlayer player, String name) {
        PublicTeleport.storage.deletePortal(player, name);
    }

    /** Builds a one block Portal at {@link #PORTAL_BLOCK} that leads to {@link TestUtils#STAND_FAR}. */
    private static void buildPortal(GameTestHelper helper, TestPlayer player, String name) {
        helper.setBlock(PORTAL_BLOCK, Blocks.STONE);
        player.moveTo(helper, LOOK_AT_PORTAL);
        player.runAsOwner("setportal " + name + " from");
        player.runAsOwner("setportal " + name + " to");
        player.moveTo(helper, STAND_FAR);
        player.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
    }

    @GameTest
    public void setportalFromToTarget(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        // a 3 wide, 2 high wall; "from" is the top right corner, "to" the bottom left one
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(x, 1, 5, Blocks.STONE);
            helper.setBlock(x, 2, 5, Blocks.STONE);
        }

        player.moveTo(helper, new BlockPos(5, 1, 2), 0, 0);
        player.runAsOwner("setportal " + name + " from");
        assertLastMessage(helper, player, "portal_set_from", SUCCESS, name);

        // looking slightly down hits the lower row of the wall
        player.moveTo(helper, new BlockPos(3, 1, 2), 0, 24);
        player.runAsOwner("setportal " + name + " to");
        assertLastMessage(helper, player, "portal_set_to", SUCCESS, name);
        helper.assertTrue(findPortal(name) == null, "The Portal should not be saved before it is complete");

        player.moveTo(helper, STAND);
        player.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);

        Portal portal = findPortal(name);
        helper.assertTrue(portal != null, "Expected the Portal to be saved");
        // saved with the lower corner first
        assertEqual(helper, TestUtils.relative(helper, new BlockPos(portal.aX(), portal.aY(), portal.aZ())),
            new BlockPos(3, 1, 5), "lower corner");
        assertEqual(helper, TestUtils.relative(helper, new BlockPos(portal.bX(), portal.bY(), portal.bZ())),
            new BlockPos(5, 2, 5), "upper corner");
        TestUtils.assertTeleportAt(helper, portal.target(), STAND);
        for (int x = 3; x <= 5; x++) {
            helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), x, 1, 5);
            helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), x, 2, 5);
        }

        deletePortal(player, name);
        helper.succeed();
    }

    /** "from" and "to" must use the block looked at, whichever of its six faces the player looks at. */
    @GameTest
    public void setportalFromAnySide(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos block = new BlockPos(3, 3, 3);
        record Side(String name, BlockPos stand, float yaw, float pitch) {
        }
        // standing at y = 2 puts the eyes (1.62 above the feet) at the height of the block
        List<Side> sides = List.of(
            new Side("north", new BlockPos(3, 2, 0), 0, 0),
            new Side("south", new BlockPos(3, 2, 6), 180, 0),
            new Side("east", new BlockPos(6, 2, 3), 90, 0),
            new Side("west", new BlockPos(0, 2, 3), -90, 0),
            new Side("top", new BlockPos(3, 4, 3), 0, 90),
            new Side("bottom", new BlockPos(3, 1, 3), 0, -90));

        for (Side side : sides) {
            String name = uniqueName("p");

            // the previous Portal turned the block into a glass pane
            helper.setBlock(block, Blocks.STONE);
            player.moveTo(helper, side.stand(), side.yaw(), side.pitch());
            player.runAsOwner("setportal " + name + " from");
            player.runAsOwner("setportal " + name + " to");
            player.moveTo(helper, STAND);
            player.runAsOwner("setportal " + name + " target");

            Portal portal = findPortal(name);
            helper.assertTrue(portal != null, "Expected the Portal to be saved (" + side.name() + " face)");
            deletePortal(player, name);
            assertEqual(helper, TestUtils.relative(helper, new BlockPos(portal.aX(), portal.aY(), portal.aZ())), block,
                "Portal block when looking at the " + side.name() + " face");
        }
        helper.succeed();
    }

    @GameTest
    public void setportalTargetFirst(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        helper.setBlock(PORTAL_BLOCK, Blocks.STONE);
        player.moveTo(helper, STAND_FAR);
        player.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, player, "portal_set_pos", SUCCESS, name);

        player.moveTo(helper, LOOK_AT_PORTAL);
        player.runAsOwner("setportal " + name + " to");
        assertLastMessage(helper, player, "portal_set_to", SUCCESS, name);
        player.runAsOwner("setportal " + name + " from");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
        helper.assertTrue(findPortal(name) != null, "Expected the Portal to be saved");

        deletePortal(player, name);
        helper.succeed();
    }

    @GameTest
    public void setportalTargetUrl(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        player.runAsOwner("setportal " + name + " target \"example.com:25565\"");
        assertLastMessage(helper, player, "portal_set_url", SUCCESS, name);

        helper.setBlock(PORTAL_BLOCK, Blocks.STONE);
        player.moveTo(helper, LOOK_AT_PORTAL);
        player.runAsOwner("setportal " + name + " from");
        player.runAsOwner("setportal " + name + " to");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
        assertEqual(helper, findPortal(name).target().dimension(), "url:example.com:25565", "Portal target");

        deletePortal(player, name);
        helper.succeed();
    }

    @GameTest
    public void setportalTargetUrlInvalid(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        for (String url : List.of("example.com", "example.com:0", "example.com:65536", "example.com:port",
            "exa mple.com:25565", "-example.com:25565")) {
            player.runAsOwner("setportal " + name + " target \"" + url + "\"");
            assertLastMessage(helper, player, "portal_set_url_invalid", ERROR, url);
        }
        helper.succeed();
    }

    @GameTest
    public void setportalNoBlock(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        // nothing within reach in front of the player
        player.runAsOwner("setportal " + uniqueName("p") + " from");
        assertLastMessage(helper, player, "portal_set_no_block", ERROR);
        helper.succeed();
    }

    @GameTest
    public void setportalTargetUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        // floating: no block within 2 blocks to stand on
        TestPlayer player = TestPlayer.spawn(helper, new BlockPos(4, 4, 4));

        player.runAsOwner("setportal " + uniqueName("p") + " target");
        assertLastMessage(helper, player, "teleport_unsafe_set", ERROR, "Portal");
        helper.succeed();
    }

    @GameTest
    public void portalCommandsNeedOwner(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        // "portalCommandsOnlyOp" is true by default
        player.run("setportal " + uniqueName("p") + " target");
        player.run("delportal " + uniqueName("p"));
        player.run("portals");
        TestUtils.assertNoMessage(helper, player);
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void walkIntoPortal(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        buildPortal(helper, player, name);

        helper.startSequence()
            // Portals are checked every 5 ticks; the first check has to see the player outside the Portal
            .thenIdle(10)
            .thenExecute(() -> player.moveTo(helper, new BlockPos(PORTAL_BLOCK.getX(), 1, PORTAL_BLOCK.getZ())))
            .thenWaitUntil(() -> {
                assertLastMessage(helper, player, "teleported_to", SUCCESS, name);
                assertAt(helper, player, STAND_FAR);
            })
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    @GameTest
    public void delportal(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        buildPortal(helper, player, name);
        player.runAsOwner("delportal " + name);
        assertLastMessage(helper, player, "portal_deleted", SUCCESS, name);
        helper.assertTrue(findPortal(name) == null, "Expected the Portal to be deleted");
        helper.succeed();
    }

    @GameTest
    public void delportalDoesNotExist(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        player.runAsOwner("delportal " + name);
        assertLastMessage(helper, player, "portal_no_exist", ERROR, name);
        helper.succeed();
    }

    /** "/portals" lists the Portals of all players, so this test runs in its own batch. */
    @GameTest(environment = "public-teleport-gametest:portals_list")
    public void portalsNoneAndList(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        // remove Portals other tests may have left behind
        for (Portal portal : List.copyOf(PublicTeleport.storage.getPortals())) {
            deletePortal(player, portal.target().name());
        }

        player.runAsOwner("portals");
        assertLastMessage(helper, player, "portal_none", WARNING);

        buildPortal(helper, player, "alpha");
        player.runAsOwner("portals");
        assertLastMessageParts(helper, player, "headline_portals()");
        String list = TestUtils.lastModMessage(helper, player).getString();
        helper.assertTrue(list.contains("alpha"), "Expected the Portal list to contain \"alpha\", but was: " + list);

        deletePortal(player, "alpha");
        helper.succeed();
    }
}
