package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.withConfig;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Homes" section of manual-testing.txt.
 */
public class HomesTest {
    private static void assertHomes(GameTestHelper helper, TestPlayer player, String... expected) {
        assertEqual(helper, PublicTeleport.storage.getTeleportNames(player, false), List.of(expected), "homes");
    }

    @GameTest
    public void sethomeReservedName(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome back");
        assertLastMessage(helper, player, "home_reserved_name", WARNING);
        assertHomes(helper, player);
        helper.succeed();
    }

    @GameTest
    public void homeBackWhenBackDisabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableBack", false), () -> {
            player.run("home back");
            assertLastMessage(helper, player, "home_no_exist", ERROR, "back");
        });
        helper.succeed();
    }

    @GameTest
    public void homeBackWhenBackEnabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableBack", true), () -> {
            player.run("home back");
            assertLastMessage(helper, player, "home_reserved_name_get", WARNING, "/back");
        });
        helper.succeed();
    }

    @GameTest
    public void sethomeUnnamed(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        assertLastMessage(helper, player, "home_set", SUCCESS, "home");
        assertHomes(helper, player, "home");
        helper.succeed();
    }

    @GameTest
    public void sethomeNamed(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome base");
        assertLastMessage(helper, player, "home_set_named", SUCCESS, "base");
        assertHomes(helper, player, "base");
        helper.succeed();
    }

    @GameTest
    public void sethomeMaxReached(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        int maxHomes = PublicTeleport.storage.getConfig().maxHomes();
        helper.assertTrue(maxHomes > 0, "this test needs maxHomes > 0 in the test config");

        for (int i = 0; i < maxHomes; i++) {
            player.run("sethome home" + i);
        }

        player.run("sethome extra");
        assertLastMessage(helper, player, "home_set_max_reached", WARNING, maxHomes);

        player.run("sethome");
        assertLastMessage(helper, player, "home_set_max_reached", WARNING, maxHomes);

        assertEqual(helper, PublicTeleport.storage.getTeleportNames(player, false).size(), maxHomes,
            "number of homes");
        helper.succeed();
    }

    @GameTest
    public void delhomeNamed(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome base");
        player.run("delhome base");
        assertLastMessage(helper, player, "home_deleted", SUCCESS, "base");
        assertHomes(helper, player);
        helper.succeed();
    }

    @GameTest
    public void delhomeUnnamed(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome");
        player.run("delhome home");
        assertLastMessage(helper, player, "home_deleted", SUCCESS, "home");
        assertHomes(helper, player);
        helper.succeed();
    }

    @GameTest
    public void delhomeBack(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("delhome back");
        assertLastMessage(helper, player, "home_no_exist", ERROR, "back");
        helper.succeed();
    }

    @GameTest
    public void homeDoesNotExist(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("home nope");
        assertLastMessage(helper, player, "home_no_exist", ERROR, "nope");

        player.run("home");
        assertLastMessage(helper, player, "home_no_exist", ERROR, "home");
        helper.succeed();
    }

    @GameTest
    public void delhomeDoesNotExist(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("delhome nope");
        assertLastMessage(helper, player, "home_no_exist", ERROR, "nope");
        helper.succeed();
    }
}
