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
    };

    private static Set<String> loaded;

    private Whitelist() {
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
