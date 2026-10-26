package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessageParts;
import static timewaster.publicteleport.gametest.TestUtils.assertTeleportAt;
import static timewaster.publicteleport.gametest.TestUtils.buildFloor;
import static timewaster.publicteleport.gametest.TestUtils.setup;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Teleports" section of manual-testing.txt. The TPA cases of this
 * section are in {@link RequestsTest}, "target not found" is covered by
 * {@link HomesTest#homeDoesNotExist}.
 */
@SuppressWarnings("null")
public class TeleportsTest {
    /**
     * A standing position 4 blocks in the air, on top of {@link #PILLAR} and far
     * enough from {@link TestUtils#STAND} to teleport to.
     */
    private static final BlockPos HIGH = new BlockPos(6, 4, 6);
    /**
     * The single block that makes {@link #HIGH} safe; without it no block within 2
     * blocks is safe.
     */
    private static final BlockPos PILLAR = new BlockPos(6, 3, 6);
    /** A standing position under the ceiling built by {@link #buildLowCeiling}. */
    private static final BlockPos LOW = new BlockPos(5, 1, 5);
    /** A standing position far enough from {@link #LOW} to teleport to it. */
    private static final BlockPos CORNER = new BlockPos(0, 1, 0);

    private static String name(TestPlayer player) {
        return player.getName().getString();
    }

    @GameTest
    public void homeTeleport(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        player.moveTo(helper, STAND_FAR);
        player.run("home");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "home");
        assertAt(helper, player, STAND);
        helper.succeed();
    }

    /**
     * The Camel is teleported along and the player stays seated, the Home is set
     * on the ground the Camel stands on, not at the height the player sits.
     */
    @GameTest
    public void homeTeleportRiding(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        Entity camel = TestUtils.spawnRide(helper, TestUtils.entityType("camel"), STAND, player);

        player.run("sethome");
        assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "home", false), STAND);

        TestUtils.moveRide(helper, camel, Vec3.atBottomCenterOf(STAND_FAR), 0);
        player.run("home");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "home");
        TestUtils.assertRideAt(helper, camel, STAND, player);
        helper.succeed();
    }

    /**
     * In Boats and Minecarts the player sits lower than the bottom of the
     * vehicle, which must not count as being stuck in the floor.
     */
    @GameTest
    public void homeTeleportInLowVehicles(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        for (EntityType<? extends Entity> type : List.of(TestUtils.entityType("oak_boat"), TestUtils.entityType("minecart"))) {
            player.moveTo(helper, STAND_FAR);
            Entity vehicle = TestUtils.spawnRide(helper, type, STAND_FAR, player);

            player.run("home");
            assertLastMessage(helper, player, "teleported_to", SUCCESS, "home");
            TestUtils.assertRideAt(helper, vehicle, STAND, player);
            player.stopRiding();
            vehicle.discard();
        }
        helper.succeed();
    }

    /**
     * Puts a ceiling 2 blocks above the floor over everything within 2 blocks of
     * {@link #LOW}, and makes the blocks below the floor solid: room for a player,
     * but not for a ride.
     */
    private static void buildLowCeiling(GameTestHelper helper) {
        for (int x = LOW.getX() - 2; x <= LOW.getX() + 2; x++) {
            for (int z = LOW.getZ() - 2; z <= LOW.getZ() + 2; z++) {
                helper.setBlock(x, -1, z, Blocks.STONE);
                helper.setBlock(x, 3, z, Blocks.STONE);
            }
        }
    }

    @GameTest
    public void homeTooLowForRide(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos home = LOW;

        buildLowCeiling(helper);
        player.moveTo(helper, home);
        player.run("sethome");
        player.moveTo(helper, CORNER);
        Entity camel = TestUtils.spawnRide(helper, TestUtils.entityType("camel"), CORNER, player);

        player.run("home");
        assertLastMessage(helper, player, "teleport_unsafe_riding", ERROR, "home");
        TestUtils.assertRideAt(helper, camel, CORNER, player);

        // without the Camel the player fits
        player.stopRiding();
        player.moveTo(helper, CORNER);
        player.run("home");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "home");
        assertAt(helper, player, home);
        helper.succeed();
    }

    /** When the destination is not safe on foot either, dismounting is not suggested. */
    @GameTest
    public void homeUnsafeRiding(GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(PILLAR, Blocks.STONE);
        TestPlayer player = TestPlayer.spawn(helper, HIGH);

        player.run("sethome");
        helper.setBlock(PILLAR, Blocks.AIR);
        player.moveTo(helper, STAND);
        TestUtils.spawnRide(helper, TestUtils.entityType("camel"), STAND, player);
        player.run("home");
        assertLastMessage(helper, player, "teleport_unsafe", ERROR, "home");
        helper.succeed();
    }

    @GameTest
    public void tpacceptTooLowForRide(GameTestHelper helper) {
        buildFloor(helper);
        buildLowCeiling(helper);
        TestPlayer sender = TestPlayer.spawn(helper, CORNER);
        TestPlayer receiver = TestPlayer.spawn(helper, LOW);
        Entity camel = TestUtils.spawnRide(helper, TestUtils.entityType("camel"), CORNER, sender);

        sender.run("tpa " + name(receiver));
        receiver.run("tpaccept");
        assertLastMessage(helper, sender, "teleport_unsafe_tpa_riding", ERROR, name(receiver));
        assertLastMessage(helper, receiver, "teleport_unsafe_target_riding", ERROR, name(sender));
        TestUtils.assertRideAt(helper, camel, CORNER, sender);
        helper.succeed();
    }

    @GameTest
    public void homeAlreadyThere(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        player.run("home");
        assertLastMessage(helper, player, "teleport_unnecessary", WARNING, "home");
        helper.succeed();
    }

    @GameTest
    public void setUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        // floating: no block within 2 blocks to stand on
        TestPlayer player = TestPlayer.spawn(helper, HIGH);

        player.run("sethome");
        assertLastMessage(helper, player, "teleport_unsafe_set", ERROR, "Home");

        player.runAsOwner("setwarp " + TestUtils.uniqueName("w"));
        assertLastMessage(helper, player, "teleport_unsafe_set", ERROR, "Warp");

        // does not change the Spawn, so it's safe to run next to other tests
        player.runAsOwner("setspawn");
        assertLastMessage(helper, player, "teleport_unsafe_set", ERROR, "Spawn");
        helper.succeed();
    }

    @GameTest
    public void homeUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(PILLAR, Blocks.STONE);
        TestPlayer player = TestPlayer.spawn(helper, HIGH);

        player.run("sethome");
        helper.setBlock(PILLAR, Blocks.AIR);
        player.moveTo(helper, STAND);
        player.run("home");
        assertLastMessage(helper, player, "teleport_unsafe", ERROR, "home");
        assertAt(helper, player, STAND);
        helper.succeed();
    }

    /**
     * Fire and soul fire are different blocks, both make the Home on top of
     * {@link #PILLAR} unsafe.
     */
    @GameTest
    public void homeInFire(GameTestHelper helper) {
        buildFloor(helper);
        // both kinds of fire can burn on soul soil
        helper.setBlock(PILLAR, Blocks.SOUL_SOIL);
        TestPlayer player = TestPlayer.spawn(helper, HIGH);

        player.run("sethome");
        for (Block fire : List.of(Blocks.FIRE, Blocks.SOUL_FIRE)) {
            helper.setBlock(HIGH, fire);
            helper.assertBlockPresent(fire, HIGH);
            player.moveTo(helper, STAND);
            player.run("home");
            assertLastMessage(helper, player, "teleport_unsafe", ERROR, "home");
            assertAt(helper, player, STAND);
        }
        helper.succeed();
    }

    @GameTest
    public void warpUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(PILLAR, Blocks.STONE);
        TestPlayer player = TestPlayer.spawn(helper, HIGH);
        String warp = TestUtils.uniqueName("w");

        player.runAsOwner("setwarp " + warp);
        helper.setBlock(PILLAR, Blocks.AIR);
        player.moveTo(helper, STAND);
        player.run("warp " + warp);
        assertLastMessage(helper, player, "teleport_unsafe", ERROR, warp);
        assertAt(helper, player, STAND);
        PublicTeleport.storage.deleteTeleport(player, warp, true);
        helper.succeed();
    }

    @GameTest
    public void tpacceptUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, STAND);
        TestPlayer receiver = TestPlayer.spawn(helper, HIGH);

        sender.run("tpa " + name(receiver));
        receiver.run("tpaccept");
        assertLastMessage(helper, sender, "teleport_unsafe_tpa", ERROR, name(receiver));
        assertLastMessage(helper, receiver, "teleport_unsafe_target", ERROR, name(sender));
        assertAt(helper, sender, STAND);
        helper.succeed();
    }

    @GameTest
    public void tpacceptTpahereUnsafe(GameTestHelper helper) {
        buildFloor(helper);
        TestPlayer sender = TestPlayer.spawn(helper, HIGH);
        TestPlayer receiver = TestPlayer.spawn(helper, STAND);

        sender.run("tpahere " + name(receiver));
        receiver.run("tpaccept");
        assertLastMessage(helper, receiver, "teleport_unsafe_tpa", ERROR, name(sender));
        assertLastMessage(helper, sender, "teleport_unsafe_target", ERROR, name(receiver));
        assertAt(helper, receiver, STAND);
        helper.succeed();
    }

    @GameTest
    public void homesNone(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("homes");
        assertLastMessage(helper, player, "home_none", WARNING);
        helper.succeed();
    }

    @GameTest
    public void homesList(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome zulu");
        player.run("sethome alpha");
        player.run("homes");
        // sorted, "back" is not listed
        assertLastMessageParts(helper, player, "headline_homes()", "button_named(alpha)", "button_named(zulu)");
        helper.succeed();
    }

    /** Warps are shared by all players, so this test runs in its own batch. */
    @GameTest(environment = "public-teleport-gametest:warps_list")
    public void warpsNoneAndList(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        // remove Warps other tests may have left behind
        for (String warp : PublicTeleport.storage.getTeleportNames(player, true)) {
            PublicTeleport.storage.deleteTeleport(player, warp, true);
        }

        player.run("warps");
        assertLastMessage(helper, player, "warp_none", WARNING);

        player.runAsOwner("setwarp zulu");
        player.runAsOwner("setwarp alpha");
        player.run("warps");
        assertLastMessageParts(helper, player, "headline_warps()", "button_named(alpha)", "button_named(zulu)");

        PublicTeleport.storage.deleteTeleport(player, "zulu", true);
        PublicTeleport.storage.deleteTeleport(player, "alpha", true);
        helper.succeed();
    }

    private static void testHomeOnTopOf(GameTestHelper helper, Block block, double height) {
        TestPlayer player = setup(helper);

        helper.setBlock(STAND, block);
        player.moveTo(helper, new Vec3(STAND.getX() + 0.5, STAND.getY() + height, STAND.getZ() + 0.5), 0, 0);
        player.run("sethome");
        assertLastMessage(helper, player, "home_set", SUCCESS, "home");
        assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "home", false), STAND.above());

        player.moveTo(helper, STAND_FAR);
        player.run("home");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "home");
        assertAt(helper, player, STAND.above());
        helper.succeed();
    }

    @GameTest
    public void homeOnSlab(GameTestHelper helper) {
        testHomeOnTopOf(helper, Blocks.STONE_SLAB, 0.5);
    }

    @GameTest
    public void homeOnCarpet(GameTestHelper helper) {
        testHomeOnTopOf(helper, TestUtils.block("white_carpet"), 0.0625);
    }
}
