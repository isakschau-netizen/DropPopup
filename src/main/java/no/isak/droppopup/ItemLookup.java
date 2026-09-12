package no.isak.droppopup;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gjor et Skyblock-navn om til et vanilla item vi kan tegne.
 *
 * <p>Skyblock-items finnes ikke i vanilla-registeret, sa vi gjetter sa godt vi
 * kan: "Enchanted Spider Eye" -> Spider Eye, "Sharp Felthorn Reaper" ->
 * Netherite Sword. Treffer vi ingenting, faller vi tilbake til en stjerne slik
 * at popupen alltid viser noe.
 */
public final class ItemLookup {

    /** Ord Skyblock setter foran navn, som ikke finnes i vanilla. */
    private static final String[] DROPPABLE_PREFIXES = {
            "enchanted", "fierce", "sharp", "spicy", "heroic", "legendary", "epic",
            "rare", "uncommon", "common", "mythic", "withered", "fabled", "suspicious",
            "ancient", "necrotic", "loving", "ridiculous", "godly", "strong", "superior",
            "unpleasant", "keen", "hasty", "fast", "neat", "fine", "grand", "wise",
            "pure", "smart", "titanic", "clean", "gentle", "odd", "bizarre", "itchy",
            "unreal", "silky", "bloody", "shaded", "sweet", "double-bit", "warped",
            "toil", "blessed", "earthy", "mossy", "bulky", "refined", "stellar",
            "dirty", "moil", "lucky", "gilded", "cubic", "necrotic", "fruitful",
            "magnetic", "fleet", "mithraic", "auspicious", "heated", "ambered",
            "fortified", "gentle", "excellent", "perfect", "jaded", "coldfused",
            "renowned", "giant", "submerged", "salty", "treacherous", "stiff",
            "lush", "green-thumb", "blooming", "robust", "zooming", "peasant",
            "festive", "bustling", "hyper", "coldfused", "dimensional", "chomp",
    };

    /** Bygges en gang: vanilla visningsnavn (lowercase) -> item. */
    private static Map<String, Item> byName;

    private ItemLookup() {
    }

    private static synchronized Map<String, Item> names() {
        if (byName == null) {
            Map<String, Item> map = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                try {
                    String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                    map.putIfAbsent(name, item);
                } catch (Exception ignored) {
                    // Et enkelt item som ikke kan navngis skal ikke velte oppslaget.
                }
            }
            byName = map;
        }
        return byName;
    }

    public static ItemStack resolve(String skyblockName) {
        String name = skyblockName.toLowerCase(Locale.ROOT).trim();

        // Config-fila vinner, sa du kan overstyre repoet hvis du vil.
        Item override = ItemConfig.overrides().get(name);
        if (override != null) {
            return log(name, new ItemStack(override), "config");
        }

        // NEU-repoet har den ekte Hypixel-modellen.
        ItemStack fromRepo = NeuRepo.lookup(name);
        if (fromRepo != null) {
            return log(name, fromRepo, "NEU repo");
        }

        Map<String, Item> map = names();

        Item exact = map.get(name);
        if (exact != null) {
            return log(name, new ItemStack(exact), "exact name");
        }

        // Dropp kjente Skyblock-prefikser ord for ord: "enchanted spider eye" -> "spider eye".
        String[] words = name.split("\\s+");
        int start = 0;
        while (start < words.length - 1 && isPrefixWord(words[start])) {
            start++;
            Item hit = map.get(String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length)));
            if (hit != null) {
                return log(name, new ItemStack(hit), "prefix stripped");
            }
        }

        // Siste utvei: prov de bakerste ordene ("... felthorn reaper" -> "reaper").
        for (int from = start; from < words.length; from++) {
            Item hit = map.get(String.join(" ", java.util.Arrays.copyOfRange(words, from, words.length)));
            if (hit != null) {
                return log(name, new ItemStack(hit), "tail words");
            }
        }

        return log(name, new ItemStack(Items.NETHER_STAR), "fallback");
    }

    /**
     * Logger hva vi landet pa. Gjor det mulig a se hvorfor en modell ble feil,
     * og hvilket navn du ma legge inn i config-fila for a rette den.
     */
    private static ItemStack log(String name, ItemStack stack, String how) {
        DropPopup.LOGGER.info("Item-oppslag: '{}' -> {} ({})",
                name, BuiltInRegistries.ITEM.getKey(stack.getItem()), how);
        return stack;
    }

    private static boolean isPrefixWord(String word) {
        for (String prefix : DROPPABLE_PREFIXES) {
            if (prefix.equals(word)) {
                return true;
            }
        }
        return false;
    }
}
