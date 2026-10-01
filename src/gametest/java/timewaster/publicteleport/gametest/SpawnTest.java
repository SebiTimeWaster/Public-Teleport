package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertTeleportAt;
import static timewaster.publicteleport.gametest.TestUtils.buildFloor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
        BlockPos high = new BlockPos(4, 4, 4);
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
}
