package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.setup;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.PlainMessage;

/**
 * The "Help" section of manual-testing.txt. Only the default config (all
 * features enabled) can be tested: like the commands themselves, the help
 * reads the config once when the server starts.
 */
public class HelpTest {
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
}
