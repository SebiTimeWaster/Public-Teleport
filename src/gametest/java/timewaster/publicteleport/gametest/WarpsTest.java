package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.gametest.TestUtils.STAND;
import static timewaster.publicteleport.gametest.TestUtils.STAND_FAR;
import static timewaster.publicteleport.gametest.TestUtils.assertAt;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.uniqueName;
import static timewaster.publicteleport.gametest.TestUtils.withConfig;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Storage;

/**
 * The "Warps" section of manual-testing.txt. Warps are shared by all players,
 * so every test uses its own Warp names.
 */
@SuppressWarnings("null")
public class WarpsTest {
    private static boolean warpExists(TestPlayer player, String name) {
        return PublicTeleport.storage.getTeleportNames(player, true).contains(name);
    }

    /**
     * If the Warp is in warps.json: a new {@link Storage} reads the files again,
     * like after a server restart.
     */
    private static boolean warpSaved(TestPlayer player, String name) {
        return new Storage().getTeleportNames(player, true).contains(name);
    }

    @GameTest
    public void setwarpSpawnWhenSpawnDisabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableSpawn", false), () -> {
            player.runAsOwner("setwarp spawn");
            assertLastMessage(helper, player, "warp_reserved_name", WARNING);
        });
        helper.succeed();
    }

    @GameTest
    public void warpSpawnWhenSpawnDisabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableSpawn", false), () -> {
            player.run("warp spawn");
            assertLastMessage(helper, player, "warp_no_exist", ERROR, "spawn");
        });
        helper.succeed();
    }

    @GameTest
    public void setwarpSpawnWhenSpawnEnabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableSpawn", true), () -> {
            player.runAsOwner("setwarp spawn");
            assertLastMessage(helper, player, "warp_reserved_spawn_set", WARNING, "/setspawn");
        });
        helper.succeed();
    }

    @GameTest
    public void warpSpawnWhenSpawnEnabled(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        withConfig(configWith("enableSpawn", true), () -> {
            player.run("warp spawn");
            assertLastMessage(helper, player, "warp_reserved_spawn_get", WARNING, "/spawn");
        });
        helper.succeed();
    }

    @GameTest
    public void setwarpReservedPrefix(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.runAsOwner("setwarp public_teleport_none_found");
        assertLastMessage(helper, player, "name_reserved_prefix", WARNING, "public_teleport_");
        helper.assertFalse(warpExists(player, "public_teleport_none_found"), "Expected the Warp not to be set");
        helper.succeed();
    }

    @GameTest
    public void setwarp(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String warp = uniqueName("w");

        player.runAsOwner("setwarp " + warp);
        assertLastMessage(helper, player, "warp_set", SUCCESS, warp);
        helper.assertTrue(warpExists(player, warp), "Expected Warp \"" + warp + "\" to exist");
        helper.assertTrue(warpSaved(player, warp), "Expected Warp \"" + warp + "\" to be saved in warps.json");

        PublicTeleport.storage.deleteTeleport(player, warp, true);
        helper.succeed();
    }

    /**
     * Setting an existing Warp again moves it. /warp is for every player, not
     * only for owners.
     */
    @GameTest
    public void warpTeleport(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String warp = uniqueName("w");

        player.moveTo(helper, STAND_FAR);
        player.runAsOwner("setwarp " + warp);
        player.moveTo(helper, STAND);
        player.runAsOwner("setwarp " + warp);

        player.moveTo(helper, STAND_FAR);
        player.run("warp " + warp);
        assertLastMessage(helper, player, "teleported_to", SUCCESS, warp);
        assertAt(helper, player, STAND);
        TestUtils.assertEqual(helper, PublicTeleport.storage.getTeleportNames(player, true).stream().filter(warp::equals).count(),
            1L, "number of Warps named \"" + warp + "\"");

        PublicTeleport.storage.deleteTeleport(player, warp, true);
        helper.succeed();
    }

    /** "maxHomes" only limits Homes, not Warps. */
    @GameTest
    public void warpsNotLimitedByMaxHomes(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        List<String> warps = List.of(uniqueName("w"), uniqueName("w"));

        withConfig(configWith("maxHomes", 1), () -> warps.forEach(warp -> player.runAsOwner("setwarp " + warp)));
        for (String warp : warps) {
            helper.assertTrue(warpExists(player, warp), "Expected Warp \"" + warp + "\" to exist");
            PublicTeleport.storage.deleteTeleport(player, warp, true);
        }
        helper.succeed();
    }

    @GameTest
    public void delwarpSpawn(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.runAsOwner("delwarp spawn");
        assertLastMessage(helper, player, "warp_no_exist", ERROR, "spawn");
        helper.succeed();
    }

    @GameTest
    public void delwarp(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String warp = uniqueName("w");

        player.runAsOwner("setwarp " + warp);
        player.runAsOwner("delwarp " + warp);
        assertLastMessage(helper, player, "warp_deleted", SUCCESS, warp);
        helper.assertFalse(warpExists(player, warp), "Expected Warp \"" + warp + "\" to be deleted");
        helper.assertFalse(warpSaved(player, warp), "Expected Warp \"" + warp + "\" to be deleted from warps.json");
        helper.succeed();
    }

    @GameTest
    public void warpDoesNotExist(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String warp = uniqueName("w");

        player.run("warp " + warp);
        assertLastMessage(helper, player, "warp_no_exist", ERROR, warp);

        player.runAsOwner("delwarp " + warp);
        assertLastMessage(helper, player, "warp_no_exist", ERROR, warp);
        helper.succeed();
    }

    @GameTest
    public void warpCommandsNeedOwner(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        String warp = uniqueName("w");

        player.run("setwarp " + warp);
        TestUtils.assertNoMessage(helper, player);
        helper.assertFalse(warpExists(player, warp), "Expected Warp \"" + warp + "\" not to be set");

        player.runAsOwner("setwarp " + warp);
        player.run("delwarp " + warp);
        // still the message of /setwarp
        assertLastMessage(helper, player, "warp_set", SUCCESS, warp);
        helper.assertTrue(warpExists(player, warp), "Expected Warp \"" + warp + "\" not to be deleted");

        PublicTeleport.storage.deleteTeleport(player, warp, true);
        helper.succeed();
    }
}
