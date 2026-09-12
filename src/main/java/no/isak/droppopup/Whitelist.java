package no.isak.droppopup;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Items som alltid fortjener en popup, uansett hvilken tier Hypixel kaller dem.
 *
 * <p>Grunnen: "RARE DROP!" daekker bade Enchanted Spider Eye og Judgement Core.
 * Tier alene er derfor ikke nok til a skille sott fra surt. Skyblocker loser det
 * med en fast liste over drops som faktisk er spennende - samme ide her, men
 * listen ligger i <code>config/droppopup-whitelist.json</code> sa du kan endre
 * den uten a bygge moden pa nytt.
 */
public final class Whitelist {

    private static final String FILE_NAME = "droppopup-whitelist.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Utgangspunktet: Skyblocker sin liste, pluss et par apenbare. */
    private static final String[] DEFAULTS = {
            // Mythological Ritual
            "Enchanted Book (Chimera I)", "Fateful Stinger", "Manti-core",
            "Minos Relic", "Shimmering Wool",
            // Slayer - zombie
            "Scythe Blade", "Shredded Sinew", "Severed Hand", "Warden Heart",
            // Slayer - spider
            "Shriveled Wasp", "Digested Mosquito", "Ensnared Snail", "Primordial Eye",
            // Slayer - wolf
            "Overflux Capacitor",
            // Slayer - enderman
            "End Stone Idol", "Judgement Core",
            // Slayer - blaze
            "High Class Archfiend Dice",
            // Fisking
            "Prince's Crown Jewel", "Pocket-sized Igloo", "Radioactive Vial",
            "Tiki Mask", "Titanoboa Shed",
            // Dungeons / diverse storfangst
            "Necron's Handle", "Giant's Sword", "Wither Shield", "Shadow Warp",
            "Implosion", "Recombobulator 3000", "Divan's Alloy",
            "Fuming Potato Book", "Dark Claymore", "Precursor Eye",
            // Enchanted books
            "Enchanted Book (First Strike V)", "Enchanted Book (Giant Killer VI)",
            "Enchanted Book (Growth VI)", "Enchanted Book (Sharpness VI)",
            "Enchanted Book (Snipe IV)",
            // Master Stars er egne items, ikke boker
            "First Master Star", "Second Master Star", "Third Master Star",
            "Fourth Master Star", "Fifth Master Star",
            // Pets - parseren stripper "[Lvl N]", sa navnet star uten "Pet"
            "Phoenix", "Deep Sea Orca",
            // Dyer
            "Jolly Pink Dye", "Wild Strawberry Dye", "Celestine Dye", "Cyclamen Dye",
            "Necron Dye",
            // Kuudra
            "Kuudra Mandible", "Burning Kuudra Core", "Fiery Kuudra Core",
            "Infernal Kuudra Core",
            // Diverse storfangst
            "Grand Searing Rune", "Chili Pepper", "Exp Share Core", "Quick Claw",
            "Subzero Inverter", "Sorrow", "Chamber Silk", "Plasma Core", "Grizzly Bait",
            "Red Nose", "Precursor Relic", "Sadan's Brooch", "L.A.S.R.'s Eye", "Diamante's Handle",
            "Bigfoot's Foot", "Midas Staff", "Inquisition Artifact", "Wheel of Fate",
            "Artifact of Power", "Exceedingly Rare Ender Artifact Upgrade",
            "Sliver of Alacrity", "Siren Tear", "Volcanic Stone", "Ender Monocle",
            "Golden Plate", "Synthetic Core", "Lapis Crystal",
    };

    private static Set<String> loaded;

    private Whitelist() {
    }

    /**
     * Leser lista med en gang, sa <code>config/droppopup-whitelist.json</code>
     * finnes rett etter oppstart. Uten dette ble fila forst skrevet den dagen
     * du faktisk fikk et RARE-drop, fordi {@link #contains} er eneste vei inn.
     */
    public static void init() {
        names();
    }

    public static boolean contains(String itemName) {
        return names().contains(itemName.toLowerCase(Locale.ROOT).trim());
    }

    private static synchronized Set<String> names() {
        if (loaded == null) {
            loaded = read();
        }
        return loaded;
    }

    private static Set<String> read() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Set<String> fromDisk =
                        GSON.fromJson(reader, new TypeToken<LinkedHashSet<String>>() { }.getType());
                if (fromDisk != null) {
                    DropPopup.LOGGER.info("Whitelist: {} items fra {}", fromDisk.size(), path);
                    return fromDisk.stream()
                            .map(n -> n.toLowerCase(Locale.ROOT).trim())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                }
            } catch (Exception e) {
                DropPopup.LOGGER.warn("Klarte ikke lese {}, bruker standardlista", path, e);
            }
        } else {
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(DEFAULTS, writer);
                }
                DropPopup.LOGGER.info("Skrev standard whitelist til {}", path);
            } catch (Exception e) {
                DropPopup.LOGGER.warn("Klarte ikke skrive {}", path, e);
            }
        }

        return Arrays.stream(DEFAULTS)
                .map(n -> n.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
