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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Storage;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

/**
 * Portals are not in manual-testing.txt, these tests follow the code of
 * {@code Portals} and {@code commands.Portals}. Portals are shared by all
 * players and teleport anyone who walks into them, so every test uses its own
 * Portal names and deletes its Portals at the end.
 */
@SuppressWarnings("null")
public class PortalsTest {
    /** The block the player looks at for "from" and "to" of a one block Portal. */
    private static final BlockPos PORTAL_BLOCK = new BlockPos(3, 2, 7);
    /**
     * Where to stand to look at {@link #PORTAL_BLOCK}: 3 blocks north of it, facing
     * south.
     */
    private static final BlockPos LOOK_AT_PORTAL = new BlockPos(3, 1, 4);
    /** Where to stand to be in the Portal at {@link #PORTAL_BLOCK} (with the head). */
    private static final BlockPos IN_PORTAL = new BlockPos(PORTAL_BLOCK.getX(), 1, PORTAL_BLOCK.getZ());

    private static Portal findPortal(String name) {
        return PublicTeleport.storage.getPortals().stream().filter(portal -> portal.target().name().equals(name))
            .findFirst().orElse(null);
    }

    /**
     * If the Portal is in portals.json: a new {@link Storage} reads the files
     * again, like after a server restart.
     */
    private static boolean portalSaved(String name) {
        return new Storage().getPortals().stream().anyMatch(portal -> portal.target().name().equals(name));
    }

    private static void deletePortal(TestPlayer player, String name) {
        PublicTeleport.storage.deletePortal(player, name);
    }

    /**
     * Sets "from" and "to" of a one block Portal at {@link #PORTAL_BLOCK}, the
     * player stays at {@link #LOOK_AT_PORTAL}.
     */
    private static void setPortalCorners(GameTestHelper helper, TestPlayer player, String name) {
        helper.setBlock(PORTAL_BLOCK, Blocks.STONE);
        player.moveTo(helper, LOOK_AT_PORTAL);
        player.runAsOwner("setportal " + name + " from");
        player.runAsOwner("setportal " + name + " to");
    }

    /**
     * Builds a one block Portal at {@link #PORTAL_BLOCK} that leads to
     * {@link TestUtils#STAND}, far enough from the Portal to teleport to.
     */
    private static void buildPortal(GameTestHelper helper, TestPlayer player, String name) {
        setPortalCorners(helper, player, name);
        player.moveTo(helper, STAND);
        player.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
    }

    /**
     * Adds steps to {@code sequence}: waits until the Portals were checked with
     * the player where they stand now (outside the Portal), then moves them into
     * the Portal at {@link #PORTAL_BLOCK}. Portals are checked every 5 ticks.
     */
    private static GameTestSequence thenEnterPortal(GameTestHelper helper, GameTestSequence sequence, TestPlayer player) {
        return sequence.thenIdle(10).thenExecute(() -> player.moveTo(helper, IN_PORTAL));
    }

    /** Checks that the Portal built by {@link #buildPortal} teleported the player. */
    private static void assertTeleportedByPortal(GameTestHelper helper, TestPlayer player, String name) {
        assertLastMessage(helper, player, "teleported_to", SUCCESS, name);
        assertAt(helper, player, STAND);
    }

    @GameTest
    public void setportalFromToTarget(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        // a 3 wide, 2 high wall with an empty middle column; "from" is the top right
        // corner, "to" the bottom left one
        for (int x = 3; x <= 5; x += 2) {
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
        // the corners and air are replaced, other blocks are left alone
        helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), 5, 2, 5);
        helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), 3, 1, 5);
        helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), 4, 1, 5);
        helper.assertBlockPresent(TestUtils.block("purple_stained_glass_pane"), 4, 2, 5);
        helper.assertBlockPresent(Blocks.STONE, 3, 2, 5);
        helper.assertBlockPresent(Blocks.STONE, 5, 1, 5);
        helper.assertTrue(portalSaved(name), "Expected the Portal to be saved in portals.json");

        deletePortal(player, name);
        helper.succeed();
    }

    /**
     * "from" and "to" must use the block looked at, whichever of its six faces the
     * player looks at.
     */
    @GameTest
    public void setportalFromAnySide(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos block = new BlockPos(3, 3, 3);
        record Side(String name, BlockPos stand, float yaw, float pitch) {
        }
        // standing at y = 2 puts the eyes (1.62 above the feet) at the height of the
        // block
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

    /**
     * Portals of 1000 blocks or more are saved without glass panes. Here 8 x 16 x 8
     * = 1024 blocks, from the floor to a block high above it.
     */
    @GameTest
    public void setportalTooLargeForGlass(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");
        BlockPos top = new BlockPos(7, 15, 7);

        helper.setBlock(top, Blocks.STONE);
        player.moveTo(helper, new BlockPos(0, 1, 0), 0, 90);
        player.runAsOwner("setportal " + name + " from");
        player.moveTo(helper, top.above(), 0, 90);
        player.runAsOwner("setportal " + name + " to");
        player.moveTo(helper, STAND);
        player.runAsOwner("setportal " + name + " target");

        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
        assertEqual(helper, TestUtils.countMessages(player, "portal_set_no_glass"), 1L, "number of \"portal_set_no_glass\"");
        helper.assertBlockPresent(Blocks.STONE, 0, 0, 0);
        helper.assertBlockPresent(Blocks.STONE, top);
        helper.assertBlockPresent(Blocks.AIR, 3, 5, 3);

        deletePortal(player, name);
        helper.succeed();
    }

    /**
     * Partial Portals are kept per player: another player using the same name
     * does not complete the first player's Portal.
     */
    @GameTest
    public void setportalPartsArePerPlayer(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        TestPlayer other = TestPlayer.spawn(helper, STAND_FAR);
        String name = uniqueName("p");

        setPortalCorners(helper, player, name);
        other.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, other, "portal_set_pos", SUCCESS, name);
        helper.assertTrue(findPortal(name) == null, "Another player's target must not complete the Portal");

        player.moveTo(helper, STAND);
        player.runAsOwner("setportal " + name + " target");
        assertLastMessage(helper, player, "portal_set", SUCCESS, name);
        TestUtils.assertTeleportAt(helper, findPortal(name).target(), STAND);

        deletePortal(player, name);
        helper.succeed();
    }

    /**
     * "from" and "to" have to be in the same dimension; setting "from" again in
     * the other dimension starts over there.
     */
    @GameTest
    public void setportalCornersInDifferentDimensions(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        String name = uniqueName("p");
        // room to stand in the Nether, facing a block 2 blocks south at eye height
        BlockPos netherStand = new BlockPos(8, 70, 8);

        for (BlockPos pos : List.of(netherStand, netherStand.above(), netherStand.south(), netherStand.south().above())) {
            nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        nether.setBlockAndUpdate(netherStand.south(2).above(), Blocks.STONE.defaultBlockState());

        helper.setBlock(PORTAL_BLOCK, Blocks.STONE);
        player.moveTo(helper, LOOK_AT_PORTAL);
        player.runAsOwner("setportal " + name + " from");
        assertLastMessage(helper, player, "portal_set_from", SUCCESS, name);

        player.teleportTo(nether, netherStand.getX() + 0.5, netherStand.getY(), netherStand.getZ() + 0.5, Set.of(), 0, 0, true);
        player.runAsOwner("setportal " + name + " to");
        assertLastMessage(helper, player, "portal_set_other_dimension", ERROR, name);

        player.runAsOwner("setportal " + name + " from");
        assertLastMessage(helper, player, "portal_set_from", SUCCESS, name);
        player.runAsOwner("setportal " + name + " to");
        assertLastMessage(helper, player, "portal_set_to", SUCCESS, name);
        helper.succeed();
    }

    @GameTest
    public void setportalReservedPrefix(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        for (String part : List.of("from", "to", "target")) {
            player.runAsOwner("setportal public_teleport_none_found " + part);
            assertLastMessage(helper, player, "name_reserved_prefix", WARNING, "public_teleport_");
        }
        helper.assertTrue(findPortal("public_teleport_none_found") == null, "Expected no Portal to be saved");
        helper.succeed();
    }

    @GameTest
    public void setportalTargetUrl(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        player.runAsOwner("setportal " + name + " target \"example.com:25565\"");
        assertLastMessage(helper, player, "portal_set_url", SUCCESS, name);

        setPortalCorners(helper, player, name);
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

        thenEnterPortal(helper, helper.startSequence(), player)
            .thenWaitUntil(() -> assertTeleportedByPortal(helper, player, name))
            // /back leads to where the player entered the Portal
            .thenExecute(() -> TestUtils.assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "back", false), IN_PORTAL))
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    /**
     * A Portal with a URL as target sends the player to the other server, and puts
     * them back where they were before entering it, so they are not in the Portal
     * when they come back.
     */
    @GameTest(maxTicks = 100)
    public void walkIntoUrlPortal(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        player.runAsOwner("setportal " + name + " target \"example.com:25565\"");
        setPortalCorners(helper, player, name);

        thenEnterPortal(helper, helper.startSequence(), player)
            .thenWaitUntil(() -> {
                assertEqual(helper, player.getSentPackets(ClientboundTransferPacket.class),
                    List.of(new ClientboundTransferPacket("example.com", 25565)), "transfers to another server");
                assertAt(helper, player, LOOK_AT_PORTAL);
            })
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    /**
     * A player who is in a Portal when it is first checked, e.g. after joining the
     * server there, is not teleported until they leave it and enter it again.
     */
    @GameTest(maxTicks = 100)
    public void portalOnlyEnteredFromOutside(GameTestHelper helper) {
        TestPlayer builder = setup(helper);
        String name = uniqueName("p");

        buildPortal(helper, builder, name);
        // off the target, where the player would land next to them otherwise
        builder.moveTo(helper, STAND_FAR);
        TestPlayer player = TestPlayer.spawn(helper, IN_PORTAL);

        GameTestSequence sequence = helper.startSequence()
            .thenIdle(15)
            .thenExecute(() -> {
                assertAt(helper, player, IN_PORTAL);
                TestUtils.assertNoMessage(helper, player);
                player.moveTo(helper, LOOK_AT_PORTAL);
            });
        thenEnterPortal(helper, sequence, player)
            .thenWaitUntil(() -> assertTeleportedByPortal(helper, player, name))
            .thenExecute(() -> deletePortal(builder, name))
            .thenSucceed();
    }

    /**
     * A Portal only works in its own dimension, not at the same coordinates in
     * another one.
     */
    @GameTest(maxTicks = 100)
    public void portalInOtherDimension(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");
        BlockPos block = helper.absolutePos(PORTAL_BLOCK);

        // like buildPortal, but in the Nether
        PublicTeleport.storage.setPortal(player, new Portal(Level.NETHER.identifier().toString(), block.getX(), block.getY(),
            block.getZ(), block.getX(), block.getY(), block.getZ(), Teleport.create(player, name)));

        thenEnterPortal(helper, helper.startSequence(), player)
            .thenIdle(15)
            .thenExecute(() -> {
                assertAt(helper, player, IN_PORTAL);
                TestUtils.assertNoMessage(helper, player);
            })
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    /**
     * A Camel is so wide that the glass pane stops it before its riders reach the
     * Portal, so the Portal is entered as soon as the Camel touches it. Both
     * riders are teleported along and stay seated.
     */
    @GameTest(maxTicks = 100)
    public void rideIntoPortal(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        TestPlayer passenger = TestPlayer.spawn(helper, STAND);
        String name = uniqueName("p");

        buildPortal(helper, player, name);
        Entity camel = TestUtils.spawnRide(helper, TestUtils.entityType("camel"), LOOK_AT_PORTAL, player, passenger);

        helper.startSequence()
            .thenIdle(10)
            // facing west, the riders sit one behind the other along x; the Camel (0.85 blocks to
            // each side) reaches 0.05 blocks into the Portal, its riders (0.3 blocks) stay 0.5 blocks in front of it
            .thenExecute(() -> TestUtils.moveRide(helper, camel, new Vec3(PORTAL_BLOCK.getX() + 0.5, 1, PORTAL_BLOCK.getZ() - 0.8), 90))
            .thenWaitUntil(() -> TestUtils.assertRideAt(helper, camel, STAND, player, passenger))
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    /**
     * If {@code Portals} still stores data of this player object, read from its
     * private maps.
     */
    private static boolean portalsTrack(ServerPlayer player) {
        try {
            for (String mapName : List.of("playersPosition", "playersInPortal")) {
                Field field = timewaster.publicteleport.Portals.class.getDeclaredField(mapName);

                field.setAccessible(true);
                if (((Map<?, ?>) field.get(null)).containsKey(player)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Portals.playersPosition or playersInPortal not found, were they renamed?", e);
        }

        return false;
    }

    /**
     * Respawning replaces the player object, the Portal data of the old one has
     * to be removed, otherwise it stays until the server stops.
     */
    @GameTest(maxTicks = 100)
    public void portalDataRemovedOnRespawn(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        PlayerList playerList = helper.getLevel().getServer().getPlayerList();
        String name = uniqueName("p");

        // Portals are only checked while there are any
        buildPortal(helper, player, name);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(portalsTrack(player), "Expected the Portals to track the player"))
            .thenExecute(() -> {
                player.kill(helper.getLevel());
                ServerPlayer respawned = playerList.respawn(player, false, Entity.RemovalReason.KILLED);

                TestUtils.onTestEnd(helper, () -> playerList.remove(respawned));
                helper.assertFalse(portalsTrack(player), "Expected the Portal data of the old player object to be removed");
            })
            .thenExecute(() -> deletePortal(player, name))
            .thenSucceed();
    }

    @GameTest
    public void delportal(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String name = uniqueName("p");

        buildPortal(helper, player, name);
        List<String> suggestions = player.getSuggestions("delportal ", true);
        helper.assertTrue(suggestions.contains(name), "Expected the suggestions to contain " + name + ", but were " + suggestions);

        player.runAsOwner("delportal " + name);
        assertLastMessage(helper, player, "portal_deleted", SUCCESS, name);
        helper.assertTrue(findPortal(name) == null, "Expected the Portal to be deleted");
        helper.assertFalse(portalSaved(name), "Expected the Portal to be deleted from portals.json");
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

    /**
     * "/portals" lists the Portals of all players, so this test runs in its own
     * batch.
     */
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
