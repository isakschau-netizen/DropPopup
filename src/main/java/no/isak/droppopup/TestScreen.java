package no.isak.droppopup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Testskjerm for whitelist-items der navnet kan vaere feil.
 *
 * <p>Whitelisten er bare tekst. Skriver du "Phoenix Pet" der Hypixel sier
 * "Phoenix", matcher den aldri - og du oppdager det forst den dagen dropet
 * faktisk faller. Hver knapp her kjorer en ekte chat-linje gjennom
 * {@link DropMatcher} og {@link PopupOverlay}, altsa noyaktig samme vei som et
 * virkelig drop, og sier i chatten hva som skjedde.
 *
 * <p>Utvalget er de oppforingene som har en reell sjanse for a vaere feil:
 * nye navn, apostrofer, parenteser i navnet, kjaeledyr uten "Pet", og navn der
 * modelloppslaget lett faller tilbake til Nether Star.
 */
public final class TestScreen extends Screen {

    /** En testbar oppforing: knappetekst, grunnen til tvil, og chat-linja. */
    private record Case(String label, String why, String chat) {
    }

    private static final String MF = " (+320% Magic Find!)";

    private static final Case[] CASES = {
            // Helt nye navn - ingen av dem er sett i praksis enna.
            new Case("First Master Star", "nytt navn", "RARE DROP! First Master Star" + MF),
            new Case("Fifth Master Star", "nytt navn", "RARE DROP! Fifth Master Star" + MF),
            // Parentes inne i selve navnet - matcheren stripper parenteser.
            new Case("Ench. Book (Sharpness VI)", "parentes i navnet",
                    "RARE DROP! Enchanted Book (Sharpness VI)" + MF),
            new Case("Ench. Book (First Strike V)", "parentes i navnet",
                    "RARE DROP! Enchanted Book (First Strike V)" + MF),
            // Apostrofer.
            new Case("Sadan's Brooch", "apostrof", "RARE DROP! Sadan's Brooch" + MF),
            new Case("L.A.S.R.'s Eye", "punktum i navnet", "RARE DROP! L.A.S.R.'s Eye" + MF),
            new Case("Diamante's Handle", "apostrof", "RARE DROP! Diamante's Handle" + MF),
            new Case("Bigfoot's Foot", "apostrof", "RARE DROP! Bigfoot's Foot" + MF),
            // Kjaeledyr: "[Lvl N]" strippes, og Hypixel sier ikke "Pet".
            new Case("Phoenix (pet)", "navn uten \"Pet\"",
                    "PET DROP! [Lvl 1] Phoenix (+15% Magic Find!)"),
            new Case("Deep Sea Orca (pet)", "navn uten \"Pet\"",
                    "PET DROP! [Lvl 1] Deep Sea Orca (+15% Magic Find!)"),
            // Navn som lett gir fallback-modell.
            new Case("Sorrow", "ett generisk ord", "RARE DROP! Sorrow" + MF),
            new Case("Chamber Silk", "modelloppslag", "RARE DROP! Chamber Silk" + MF),
            new Case("Exp Share Core", "modelloppslag", "RARE DROP! Exp Share Core" + MF),
            new Case("Sliver of Alacrity", "liten \"of\"", "RARE DROP! Sliver of Alacrity" + MF),
            new Case("Jolly Pink Dye", "farge", "RARE DROP! Jolly Pink Dye" + MF),
            new Case("Necron Dye", "farge", "RARE DROP! Necron Dye" + MF),
            new Case("Burning Kuudra Core", "Kuudra-materiale",
                    "RARE DROP! Burning Kuudra Core" + MF),
            new Case("Exceedingly Rare Ender Artifact Upgrade", "svaert langt navn",
                    "RARE DROP! Exceedingly Rare Ender Artifact Upgrade" + MF),
    };

    private static final int BUTTON_WIDTH = 210;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 2;

    public TestScreen() {
        super(Component.literal("Drop Popup - test"));
    }

    @Override
    protected void init() {
        int rows = (CASES.length + 1) / 2;
        int top = Math.max(34, (this.height - rows * (BUTTON_HEIGHT + GAP)) / 2 - 6);
        int left = this.width / 2 - BUTTON_WIDTH - GAP;

        for (int i = 0; i < CASES.length; i++) {
            Case c = CASES[i];
            int x = left + (i % 2) * (BUTTON_WIDTH + GAP * 2);
            int y = top + (i / 2) * (BUTTON_HEIGHT + GAP);
            Button button = Button.builder(Component.literal(c.label()), b -> run(c))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(
                            Component.literal(c.why() + "\n\n" + c.chat())))
                    .build();
            this.addRenderableWidget(button);
        }

        int bottom = top + rows * (BUTTON_HEIGHT + GAP) + 6;
        this.addRenderableWidget(Button.builder(Component.literal("Test alle"), b -> runAll())
                .bounds(this.width / 2 - BUTTON_WIDTH - GAP, bottom, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Lukk"), b -> this.onClose())
                .bounds(this.width / 2 + GAP, bottom, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        gfx.centeredText(this.font,
                Component.literal("Svaret kommer i chatten. \"Test alle\" sjekker uten popup."),
                this.width / 2, 24, 0xFFAAAAAA);
    }

    /** Kjorer en enkelt test og viser popupen, slik et ekte drop ville gjort. */
    private void run(Case c) {
        this.onClose();
        DropMatcher.DropEvent event = parse(c);
        Result result = resultFor(event);
        if (result == Result.FALLBACK_MODEL || result == Result.OK) {
            PopupOverlay.show(event);
        }
        say(message(c, event, result));
    }

    /**
     * Sjekker alle uten a vise popup. Popupen erstatter seg selv, sa atten
     * etter hverandre hadde uansett bare vist den siste.
     */
    private void runAll() {
        this.onClose();
        say(Component.literal("§b[DropPopup] tester " + CASES.length + " oppforinger"));
        int ok = 0;
        for (Case c : CASES) {
            DropMatcher.DropEvent event = parse(c);
            Result result = resultFor(event);
            if (result == Result.OK) {
                ok++;
            }
            say(message(c, event, result));
        }
        say(Component.literal("§b[DropPopup] " + ok + " av " + CASES.length + " gikk gjennom"));
    }

    private enum Result { PARSE_FAILED, MUTED, FALLBACK_MODEL, OK }

    private static Result resultFor(DropMatcher.DropEvent event) {
        if (event == null) {
            return Result.PARSE_FAILED;
        }
        if (!PopupOverlay.wouldShow(event)) {
            return Result.MUTED;
        }
        // Nether Star er fallbacken. Da vises popupen, men med feil modell.
        return ItemLookup.resolve(event.item()).is(Items.NETHER_STAR)
                ? Result.FALLBACK_MODEL : Result.OK;
    }

    private static Component message(Case c, DropMatcher.DropEvent event, Result result) {
        String name = event == null ? c.label() : event.item();
        return switch (result) {
            case PARSE_FAILED -> Component.literal(
                    "§c[DropPopup] " + c.label() + ": linja ble ikke gjenkjent i det hele tatt");
            case MUTED -> Component.literal("§e[DropPopup] " + name
                    + ": tolket, men filtrert bort - star den i whitelisten?");
            case FALLBACK_MODEL -> Component.literal("§6[DropPopup] " + name
                    + ": popup ja, men ingen modell funnet (viser Nether Star)");
            case OK -> Component.literal("§a[DropPopup] " + name + ": ok");
        };
    }

    private static DropMatcher.DropEvent parse(Case c) {
        Minecraft mc = Minecraft.getInstance();
        String self = mc.getGameProfile().name();
        return DropMatcher.match(c.chat(), self);
    }

    /**
     * Skriver bade i chatten og i loggen. Chat lagt til klientsiden havner
     * ikke i latest.log av seg selv, og da er resultatet borte sa snart du
     * lukker spillet.
     */
    private static void say(Component message) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
        DropPopup.LOGGER.info("[test] {}", message.getString().replaceAll("§.", ""));
    }
}
