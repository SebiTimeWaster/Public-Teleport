package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertTeleportAt;
import static timewaster.publicteleport.gametest.TestUtils.setup;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Back" section of manual-testing.txt.
 */
@SuppressWarnings("null")
public class BackTest {
    @GameTest
    public void backAfterTeleport(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        player.moveTo(helper, STAND_FAR);
        player.run("home");
        assertAt(helper, player, STAND);

        player.run("back");
        assertLastMessage(helper, player, "teleported_to", SUCCESS, "back");
        assertAt(helper, player, STAND_FAR);
        helper.succeed();
    }

    /**
     * /back itself does not save a new "back", so using it twice does not jump
     * back and forth.
     */
    @GameTest
    public void backTwice(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        player.moveTo(helper, STAND_FAR);
        player.run("home");
        player.run("back");
        assertAt(helper, player, STAND_FAR);

        player.run("back");
        assertLastMessage(helper, player, "teleport_unnecessary", WARNING, "back");
        assertAt(helper, player, STAND_FAR);
        helper.succeed();
    }

    /** A teleport that fails does not save "back". */
    @GameTest
    public void backNotSavedOnFailedTeleport(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        BlockPos high = new BlockPos(6, 4, 6);

        helper.setBlock(high.below(), Blocks.STONE);
        player.moveTo(helper, high);
        player.run("sethome");
        helper.setBlock(high.below(), Blocks.AIR);
        player.moveTo(helper, STAND);
        player.run("home");
        assertLastMessage(helper, player, "teleport_unsafe", ERROR, "home");
        assertEqual(helper, PublicTeleport.storage.getTeleport(player, "back", false).name(), "public_teleport_not_found",
            "\"back\"");
        helper.succeed();
    }

    @GameTest
    public void backAfterDeath(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.moveTo(helper, STAND_FAR);
        player.kill(helper.getLevel());
        // a dead player can't run /back, so check the stored position instead
        assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "back", false), STAND_FAR);
        helper.succeed();
    }

    @GameTest
    public void backNotSet(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("back");
        assertLastMessage(helper, player, "home_no_exist", ERROR, "back");
        helper.succeed();
    }
}
