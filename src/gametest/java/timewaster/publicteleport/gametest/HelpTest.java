package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.assertLastMessage;
import static timewaster.publicteleport.gametest.TestUtils.configWith;
import static timewaster.publicteleport.gametest.TestUtils.setup;
import static timewaster.publicteleport.gametest.TestUtils.withinSeconds;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
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
@SuppressWarnings("null")
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
    private static final List<String> ALL_SECTIONS = List.of("Spawn", "Warps", "Homes", "Back", "RTP", "Portals", "TPA");

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
        assertEqual(helper, headlines(sections), ALL_SECTIONS, "help sections");
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

    /**
     * Adds steps to {@code sequence}: changes the config, runs {@code /reload},
     * waits until it is done and then runs {@code check}.
     */
    private static void thenReload(GameTestHelper helper, GameTestSequence sequence, Runnable changeConfig, Runnable check) {
        AtomicReference<CompletableFuture<Void>> reloading = new AtomicReference<>();

        sequence
            .thenExecute(() -> {
                changeConfig.run();
                reloading.set(reload(helper.getLevel().getServer()));
            })
            .thenWaitUntil(withinSeconds(helper, 60, () -> helper.assertTrue(reloading.get().isDone(), "Expected /reload to be done")))
            .thenExecute(check);
    }

    /**
     * If the test fails halfway, restores {@code original} and reloads, so later
     * batches don't run with a changed config.
     */
    private static void restoreConfigOnTestEnd(GameTestHelper helper, Config original) {
        TestUtils.onTestEnd(helper, () -> {
            if (PublicTeleport.storage.getConfig() != original) {
                PublicTeleport.storage.setConfig(original);
                reload(helper.getLevel().getServer());
            }
        });
    }

    /** Switches off all features except {@code enabled}. */
    private static void enableOnly(List<String> enabled) {
        for (String option : FEATURE_COMMANDS.keySet()) {
            PublicTeleport.storage.setConfig(configWith(option, enabled.contains(option)));
        }
    }

    /**
     * Checks that the commands of the {@code enabled} features exist and all others
     * don't.
     */
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
        Config original = PublicTeleport.storage.getConfig();
        List<String> all = List.copyOf(FEATURE_COMMANDS.keySet());
        GameTestSequence sequence = helper.startSequence();

        restoreConfigOnTestEnd(helper, original);
        thenReload(helper, sequence, () -> enableOnly(List.of("enableHomes")), () -> {
            assertCommands(helper, List.of("enableHomes"), true);
            player.runAsOwner("helpteleport");
            assertEqual(helper, headlines(sections(helper, player)), List.of("Homes"), "help sections");
        });
        // without any commands there is no help either
        thenReload(helper, sequence, () -> enableOnly(List.of()), () -> assertCommands(helper, List.of(), false));
        thenReload(helper, sequence, () -> PublicTeleport.storage.setConfig(original), () -> {
            assertCommands(helper, all, true);
            player.runAsOwner("helpteleport");
            assertEqual(helper, headlines(sections(helper, player)), ALL_SECTIONS, "help sections");
        });
        sequence.thenSucceed();
    }

    /**
     * If a player without owner permissions can use a command node: the Portal
     * commands, or the URL argument of "/setportal &lt;name&gt; target".
     */
    private static boolean canUse(TestPlayer player, String... path) {
        RootCommandNode<CommandSourceStack> root = player.level().getServer().getCommands().getDispatcher().getRoot();
        CommandNode<CommandSourceStack> node = root;

        for (String name : path) {
            node = node.getChild(name);
        }

        return node.canUse(player.createCommandSourceStack());
    }

    /**
     * With "portalCommandsOnlyOp" switched off every player can use the Portal
     * commands and sees them in the help, except for a URL as target, which is
     * only for owners no matter what. Runs in its own batch, since
     * {@code /reload} changes the commands of the whole server.
     */
    @GameTest(environment = "public-teleport-gametest:portal_commands_for_everyone", maxTicks = 10_000_000)
    public void portalCommandsForEveryone(GameTestHelper helper) {
        TestPlayer player = setup(helper);
        Config original = PublicTeleport.storage.getConfig();
        String name = TestUtils.uniqueName("p");
        GameTestSequence sequence = helper.startSequence();

        restoreConfigOnTestEnd(helper, original);
        thenReload(helper, sequence, () -> PublicTeleport.storage.setConfig(configWith("portalCommandsOnlyOp", false)), () -> {
            for (String command : FEATURE_COMMANDS.get("enablePortals")) {
                helper.assertTrue(canUse(player, command), "Expected every player to be able to use /" + command);
            }
            helper.assertFalse(canUse(player, "setportal", "name", "target", "url"), "Expected a URL as target to be only for owners");

            player.run("setportal " + name + " target");
            assertLastMessage(helper, player, "portal_set_pos", SUCCESS, name);
            player.run("setportal " + name + " target \"example.com:25565\"");
            // still the message of the previous command
            assertLastMessage(helper, player, "portal_set_pos", SUCCESS, name);
            player.run("delportal " + name);
            assertLastMessage(helper, player, "portal_no_exist", ERROR, name);

            player.run("helpteleport");
            List<String> sections = sections(helper, player);
            assertEqual(helper, headlines(sections), ALL_SECTIONS, "help sections");
            helper.assertFalse(String.join("\n", sections).contains("<domain/ip>"), "Expected the help not to contain \"<domain/ip>\"");
        });
        thenReload(helper, sequence, () -> PublicTeleport.storage.setConfig(original), () -> {
            for (String command : FEATURE_COMMANDS.get("enablePortals")) {
                helper.assertFalse(canUse(player, command), "Expected /" + command + " to be only for owners again");
            }
        });
        sequence.thenSucceed();
    }
}
