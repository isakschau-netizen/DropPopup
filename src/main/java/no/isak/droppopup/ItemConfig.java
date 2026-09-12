package no.isak.droppopup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Skyblock-navn -> vanilla item, lagret i
 * <code>config/droppopup-items.json</code>.
 *
 * <p>Poenget er at du skal kunne rette en feil modell selv, uten a bygge moden
 * pa nytt: apne fila, endre item-id-en, og start spillet pa nytt.
 */
public final class ItemConfig {

    private static final String FILE_NAME = "droppopup-overrides.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Tom som standard: NEU-repoet gir riktig modell for nesten alt, og en
     * hardkodet default her ville overstyrt repoet med noe darligere.
     * Legg inn egne linjer hvis du vil overstyre en enkelt modell.
     */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    private static Map<String, Item> loaded;

    private ItemConfig() {
    }

    /** Lowercase Skyblock-navn -> item. Leses en gang per oppstart. */
    public static synchronized Map<String, Item> overrides() {
        if (loaded == null) {
            loaded = resolve(readOrCreate());
        }
        return loaded;
    }

    private static Map<String, String> readOrCreate() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<String, String> fromDisk =
                        GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() { }.getType());
                if (fromDisk != null) {
                    DropPopup.LOGGER.info("Leste {} item-oppslag fra {}", fromDisk.size(), path);
                    return fromDisk;
                }
            } catch (Exception e) {
                DropPopup.LOGGER.warn("Klarte ikke lese {}, bruker standardoppslag", path, e);
            }
            return DEFAULTS;
        }

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(DEFAULTS, writer);
            }
            DropPopup.LOGGER.info("Skrev standard item-oppslag til {}", path);
        } catch (IOException e) {
            DropPopup.LOGGER.warn("Klarte ikke skrive {}", path, e);
        }
        return DEFAULTS;
    }

    private static Map<String, Item> resolve(Map<String, String> raw) {
        Map<String, Item> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getValue());
            if (id == null) {
                DropPopup.LOGGER.warn("Ugyldig item-id '{}' for '{}'", entry.getValue(), entry.getKey());
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) {
                DropPopup.LOGGER.warn("Fant ikke item '{}' for '{}'", entry.getValue(), entry.getKey());
                continue;
            }
            map.put(entry.getKey().toLowerCase(Locale.ROOT), item);
        }
        return map;
    }
}
