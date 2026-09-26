package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.assertTeleportAt;
import static timewaster.publicteleport.gametest.TestUtils.setup;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Back" section of manual-testing.txt.
 */
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
