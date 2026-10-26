package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.withConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import timewaster.publicteleport.PublicTeleport;

/**
 * The "Homes" section of manual-testing.txt.
 */
@SuppressWarnings("null")
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
    public void sethomeReservedPrefix(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("sethome public_teleport_none_found");
        assertLastMessage(helper, player, "name_reserved_prefix", WARNING, "public_teleport_");
        assertHomes(helper, player);
        helper.succeed();
    }

    /**
     * A Homes file that is empty or only contains "null" counts as no Homes,
     * not as a file error.
     */
    @GameTest
    public void homesFileEmpty(GameTestHelper helper) {
        TestUtils.buildFloor(helper);

        for (String content : List.of("", "null")) {
            TestPlayer player = TestPlayer.spawn(helper, TestUtils.STAND);
            Path file = FabricLoader.getInstance().getConfigDir().resolve(PublicTeleport.MOD_ID).resolve("homes")
                .resolve(player.getUUID() + ".json");

            try {
                // Homes are loaded on first use, so this is read by "/homes"
                Files.writeString(file, content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            player.run("homes");
            assertLastMessage(helper, player, "home_none", WARNING);
            player.run("sethome");
            assertLastMessage(helper, player, "home_set", SUCCESS, "home");
            assertHomes(helper, player, "home");
        }
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

    /**
     * "back" does not count towards "maxHomes", and an existing Home can still be
     * moved when the limit is reached.
     */
    @GameTest
    public void sethomeMaxReachedBackAndExisting(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        int maxHomes = PublicTeleport.storage.getConfig().maxHomes();

        helper.assertTrue(maxHomes > 0, "this test needs maxHomes > 0 in the test config");

        // teleporting saves "back"
        player.run("sethome home0");
        player.moveTo(helper, TestUtils.STAND_FAR);
        player.run("home home0");
        helper.assertTrue(!"public_teleport_not_found".equals(PublicTeleport.storage.getTeleport(player, "back", false).name()),
            "Expected \"back\" to be saved");

        for (int i = 1; i < maxHomes; i++) {
            player.run("sethome home" + i);
            assertLastMessage(helper, player, "home_set_named", SUCCESS, "home" + i);
        }
        player.run("sethome extra");
        assertLastMessage(helper, player, "home_set_max_reached", WARNING, maxHomes);

        player.moveTo(helper, TestUtils.STAND_FAR);
        player.run("sethome home0");
        assertLastMessage(helper, player, "home_set_named", SUCCESS, "home0");
        TestUtils.assertTeleportAt(helper, PublicTeleport.storage.getTeleport(player, "home0", false), TestUtils.STAND_FAR);
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
