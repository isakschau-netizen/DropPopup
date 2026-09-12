package no.isak.droppopup;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Klient-mod som viser et stort, roterende 3D-item midt pa skjermen nar du far
 * et sjeldent drop i Hypixel Skyblock.
 *
 * <p>Moden leser bare chat slik klienten allerede har mottatt den, og tegner
 * pa din egen skjerm. Den sender ingenting og rorer ikke spillet ellers.
 */
public final class DropPopup implements ClientModInitializer {

    public static final String MOD_ID = "droppopup";
    public static final Logger LOGGER = LoggerFactory.getLogger("DropPopup");

    /** Trykk for a se en test-popup uten a mate pa drops. */
    private static KeyMapping testKey;

    /** Apner testskjerma for whitelist-oppforinger. */
    private static KeyMapping guiKey;

    /**
     * Test-drops, ett per trykk. Gar rundt i lokke sa du far sett alle
     * sjeldenhetene - farge, partikkelmengde og tekst er forskjellig.
     */
    private static final DropMatcher.DropEvent[] SAMPLES = {
            new DropMatcher.DropEvent("RNG DROP", "Divan's Alloy", 1, Rarity.MYTHIC),
            new DropMatcher.DropEvent("CRAZY RARE DROP", "Necron's Handle", 1, Rarity.LEGENDARY),
            new DropMatcher.DropEvent("PET DROP", "Golden Dragon", 1, Rarity.PET),
            new DropMatcher.DropEvent("VERY RARE DROP", "Giant's Sword", 1, Rarity.EPIC),
            new DropMatcher.DropEvent("FLOOR DROP", "Fig Log", 85, Rarity.FLOOR),
    };

    private static int nextSample;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "popup"),
                (HudElement) PopupOverlay::render);

        // Systemmeldinger - det er her Hypixel sine drop-meldinger havner.
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> handle(message));

        // Vanlige spillermeldinger, i tilfelle serveren bruker den kanalen.
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) -> handle(message));

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        // GLFW navngir taster etter posisjon pa et amerikansk tastatur. Tasten
        // som heter "å" pa norsk layout ligger der LEFT_BRACKET ligger der.
        testKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.droppopup.test", InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_LEFT_BRACKET, category));

        // "ae" ligger der APOSTROPHE ligger pa et amerikansk tastatur.
        guiKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.droppopup.gui", InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_APOSTROPHE, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = false;
            while (testKey.consumeClick()) {
                pressed = true;
            }
            if (pressed) {
                PopupOverlay.show(SAMPLES[nextSample]);
                nextSample = (nextSample + 1) % SAMPLES.length;
            }

            boolean openGui = false;
            while (guiKey.consumeClick()) {
                openGui = true;
            }
            if (openGui && client.screen == null) {
                client.setScreen(new TestScreen());
            }
        });

        Whitelist.init();
        NeuRepo.loadAsync();

        LOGGER.info("Drop Popup lastet");
    }

    private static void handle(Component message) {
        try {
            // RNG-drops kringkastes til alle, sa matcheren trenger navnet ditt
            // for a skille dine egne fra alle andres.
            String self = Minecraft.getInstance().getGameProfile().name();
            DropMatcher.DropEvent event = DropMatcher.match(message.getString(), self);
            if (event != null) {
                PopupOverlay.show(event);
            }
        } catch (Throwable t) {
            // En rar chat-linje skal aldri kunne velte klienten.
            LOGGER.error("Klarte ikke a tolke chat-linje", t);
        }
    }
}
