package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.withinSeconds;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.PlainMessage;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Config;

/**
 * The "Help" section of manual-testing.txt. Which commands exist, and which
 * sections the help shows, is decided when Minecraft builds its commands: on
 * server start and on every {@code /reload}.
 */
public class HelpTest {
    /** The config option of each feature and its commands. */
    private static final Map<String, List<String>> FEATURE_COMMANDS = Map.of(
        "enableSpawn", List.of("setspawn", "spawn"),
        "enableWarps", List.of("setwarp", "delwarp", "warp", "warps"),
        "enableHomes", List.of("sethome", "delhome", "home", "homes"),
        "enableBack", List.of("back"),
        "enableRtp", List.of("rtp"),
        "enablePortals", List.of("setportal", "delportal", "portals"),
        "enableTpa", List.of("tpa", "tpahere", "tpahereall", "tpcancel", "tpaccept", "tpdeny"));
    private static final List<String> OWNER_ONLY = List.of("/setspawn", "/setwarp", "/delwarp", "/tpahereall",
        "<domain/ip>");

    /** The text of each section of the help dialog. */
    private static List<String> sections(GameTestHelper helper, TestPlayer player) {
        Dialog dialog = player.getLastDialog();

        helper.assertTrue(dialog != null, "Expected the help dialog to open");
        assertEqual(helper, ((TranslatableContents) dialog.common().title().getContents()).getKey(),
            "public-teleport.help_headline", "dialog title");

        return dialog.common().body().stream().map(body -> ((PlainMessage) body).contents().getString()).toList();
    }

    private static List<String> headlines(List<String> sections) {
        return sections.stream().map(section -> section.lines().findFirst().orElse("")).toList();
    }

    @GameTest
    public void helpAsOwner(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.runAsOwner("helpteleport");
        List<String> sections = sections(helper, player);
        assertEqual(helper, headlines(sections), List.of("Spawn", "Warps", "Homes", "Back", "RTP", "Portals", "TPA"),
            "help sections");
        String text = String.join("\n", sections);
        for (String ownerOnly : OWNER_ONLY) {
            helper.assertTrue(text.contains(ownerOnly), "Expected the help to contain \"" + ownerOnly + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void helpAsPlayer(GameTestHelper helper) {
        TestPlayer player = setup(helper);

        player.run("helpteleport");
        List<String> sections = sections(helper, player);
        // Portals are for OPs only by default ("portalCommandsOnlyOp")
        assertEqual(helper, headlines(sections), List.of("Spawn", "Warps", "Homes", "Back", "RTP", "TPA"),
            "help sections");
        String text = String.join("\n", sections);
        for (String ownerOnly : OWNER_ONLY) {
            helper.assertFalse(text.contains(ownerOnly), "Expected the help not to contain \"" + ownerOnly + "\"");
        }
        helper.succeed();
    }

    /** Does the same as the {@code /reload} command. */
    private static CompletableFuture<Void> reload(MinecraftServer server) {
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    /** Switches off all features except {@code enabled}. */
    private static void enableOnly(List<String> enabled) {
        for (String option : FEATURE_COMMANDS.keySet()) {
            PublicTeleport.storage.setConfig(configWith(option, enabled.contains(option)));
        }
    }

    /** Checks that the commands of the {@code enabled} features exist and all others don't. */
    private static void assertCommands(GameTestHelper helper, List<String> enabled, boolean hasHelp) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();

        FEATURE_COMMANDS.forEach((option, commands) -> {
            for (String command : commands) {
                boolean exists = root.getChild(command) != null;
                helper.assertTrue(exists == enabled.contains(option),
                    "Expected /" + command + (exists ? " not" : "") + " to exist (" + option + ")");
            }
        });
        helper.assertTrue((root.getChild("helpteleport") != null) == hasHelp,
            "Expected /helpteleport " + (hasHelp ? "" : "not ") + "to exist");
    }

    /**
     * Switches features off, runs {@code /reload} and checks commands and help,
     * then switches them back on. {@code /reload} changes the commands of the
     * whole server, so this test runs in its own batch.
     */
    @GameTest(environment = "public-teleport-gametest:config_reload", maxTicks = 10_000_000)
    public void featuresSwitchedOffAndReloaded(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Config original = PublicTeleport.storage.getConfig();
        List<String> all = List.copyOf(FEATURE_COMMANDS.keySet());
        AtomicReference<CompletableFuture<Void>> reloading = new AtomicReference<>();
        Runnable waitForReload = () -> helper.assertTrue(reloading.get().isDone(), "Expected /reload to be done");

        // if the test fails halfway, don't leave later batches with features switched off
        TestUtils.onTestEnd(helper, () -> {
            if (PublicTeleport.storage.getConfig() != original) {
                PublicTeleport.storage.setConfig(original);
                reload(server);
            }
        });

        helper.startSequence()
            .thenExecute(() -> {
                enableOnly(List.of("enableHomes"));
                reloading.set(reload(server));
            })
            .thenWaitUntil(withinSeconds(helper, 60, waitForReload))
            .thenExecute(() -> {
                assertCommands(helper, List.of("enableHomes"), true);
                player.runAsOwner("helpteleport");
                assertEqual(helper, headlines(sections(helper, player)), List.of("Homes"), "help sections");

                // without any commands there is no help either
                enableOnly(List.of());
                reloading.set(reload(server));
            })
            .thenWaitUntil(withinSeconds(helper, 60, waitForReload))
            .thenExecute(() -> {
                assertCommands(helper, List.of(), false);

                PublicTeleport.storage.setConfig(original);
                reloading.set(reload(server));
            })
            .thenWaitUntil(withinSeconds(helper, 60, waitForReload))
            .thenExecute(() -> {
                assertCommands(helper, all, true);
                player.runAsOwner("helpteleport");
                assertEqual(helper, headlines(sections(helper, player)),
                    List.of("Spawn", "Warps", "Homes", "Back", "RTP", "Portals", "TPA"), "help sections");
            })
            .thenSucceed();
    }
}
