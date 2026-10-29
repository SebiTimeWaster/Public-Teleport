package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertTeleportAt;
import static timewaster.publicteleport.gametest.TestUtils.buildFloor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Spawn" section of manual-testing.txt, plus "/spawn target not spawn
 * safe" from the "Teleports" section. "/setspawn target not spawn safe" is in
 * {@link TeleportsTest#setUnsafe}.
 */
public class SpawnTest {
    /**
     * There is only one Spawn for the whole server, so everything that changes
     * it happens in this one test, which runs in its own batch.
     */
    @GameTest(environment = "public-teleport-gametest:spawn")
    public void setspawnAndSpawn(GameTestHelper helper) {
        BlockPos high = new BlockPos(6, 4, 6);
        BlockPos pillar = high.below();

        buildFloor(helper);
        helper.setBlock(pillar, Blocks.STONE);
        TestPlayer player = TestPlayer.spawn(helper, high);

        player.runAsOwner("setspawn");
        assertLastMessage(helper, player, "spawn_set", SUCCESS);
        assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "spawn", true), high);

        player.moveTo(helper, STAND);
        player.run("spawn");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "spawn");
        assertAt(helper, player, high);

        // without the pillar no block within 2 blocks is safe
        helper.setBlock(pillar, Blocks.AIR);
        player.moveTo(helper, STAND);
        player.run("spawn");
        assertLastMessage(helper, player, "teleport_unsafe", ERROR, "spawn");
        assertAt(helper, player, STAND);

        PublicTeleport.storage.deleteTeleport(player, "spawn", true);
        helper.succeed();
    }

    /**
     * Without /setspawn, /spawn leads to the world spawn in the Overworld, here
     * from the Nether. Runs in its own batch, so no other test sets the Spawn
     * meanwhile.
     */
    @GameTest(environment = "public-teleport-gametest:spawn_world_spawn")
    public void spawnWithoutSetspawn(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);
        ServerLevel nether = TestUtils.nether(helper);
        BlockPos room = new BlockPos(100, 70, 40);
        BlockPos worldSpawn = helper.getLevel().getRespawnData().pos();

        PublicTeleport.storage.deleteTeleport(player, "spawn", true);
        TestUtils.buildRoom(nether, room);
        player.moveTo(nether, room);
        player.run("spawn");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "spawn");
        assertEqual(helper, player.level().dimension(), Level.OVERWORLD, "dimension");
        // up to 2 blocks next to it, if the world spawn itself is not safe
        helper.assertTrue(player.blockPosition().distChessboard(worldSpawn) <= 2,
            "Expected the player at most 2 blocks from the world spawn " + worldSpawn + ", but was at " + player.blockPosition());
        helper.succeed();
    }

    @GameTest
    public void setspawnNeedsOwner(GameTestHelper helper) {
        TestPlayer player = TestUtils.setup(helper);

        player.run("setspawn");
        TestUtils.assertNoMessage(helper, player);
        helper.succeed();
    }
}
