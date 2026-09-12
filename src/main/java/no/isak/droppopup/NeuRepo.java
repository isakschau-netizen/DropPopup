package no.isak.droppopup;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Riktige modeller, hentet fra NotEnoughUpdates sitt item-repo.
 *
 * <p>Repoet kjenner hvert eneste Skyblock-item, og for hvert av dem to ting vi
 * bryr oss om:
 * <ul>
 *   <li><code>ItemModel</code> - id-en til Hypixel sin egen modell, f.eks.
 *       <code>hypixel_skyblock:item/uncategorized/divans_alloy</code>. Den
 *       ligger i server-resourcepacken, sa den virker bare hvis du har den pa.</li>
 *   <li><code>SkullOwner</code>-teksturen for alle items som er hoder. Den
 *       virker uansett, uten resourcepack.</li>
 * </ul>
 *
 * <p>Repoet lastes ned en gang (~22 MB zip), kokes ned til en liten indeks, og
 * zipen kastes. Nedlastingen skjer i bakgrunnen, sa spillet starter som vanlig.
 */
public final class NeuRepo {

    private static final String ZIP_URL =
            "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
    private static final String INDEX_FILE = "droppopup-neu-index-v2.json";

    private static final Gson GSON = new GsonBuilder().create();

    /** Fargekoder i visningsnavn: "§6Divan's Alloy". */
    private static final Pattern COLOR = Pattern.compile("§.");
    private static final Pattern ITEM_MODEL = Pattern.compile("ItemModel:\"([^\"]+)\"");
    /** Forste tekstur-verdi inni SkullOwner. */
    private static final Pattern SKULL_TEXTURE = Pattern.compile("Value:\"([^\"]+)\"");
    /** "[Lvl {LVL}] " eller "[Lvl 100] " foran kjaeledyrnavn. */
    private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl [^\\]]*\\]\\s*");

    /** Fast uuid - vi bruker profilen bare til a baere en tekstur. */
    private static final UUID SKIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** NEU-repoet bruker fortsatt noen 1.8-navn. */
    private static final Map<String, String> LEGACY_IDS = Map.of(
            "minecraft:skull", "minecraft:player_head",
            "minecraft:golden_helmet", "minecraft:golden_helmet",
            "minecraft:record_13", "minecraft:music_disc_13",
            "minecraft:water_bucket", "minecraft:water_bucket");

    private static volatile Map<String, Entry> index;
    private static volatile boolean loading;

    private NeuRepo() {
    }

    /** Lastes i bakgrunnen ved oppstart. */
    public static void loadAsync() {
        if (loading || index != null) {
            return;
        }
        loading = true;
        Thread thread = new Thread(NeuRepo::load, "droppopup-neu-repo");
        thread.setDaemon(true);
        thread.start();
    }

    private static void load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(INDEX_FILE);
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Map<String, Entry> fromDisk =
                            GSON.fromJson(reader, new TypeToken<HashMap<String, Entry>>() { }.getType());
                    if (fromDisk != null && !fromDisk.isEmpty()) {
                        index = fromDisk;
                        DropPopup.LOGGER.info("NEU-indeks lastet: {} items", fromDisk.size());
                        return;
                    }
                } catch (Exception e) {
                    DropPopup.LOGGER.warn("Kunne ikke lese NEU-indeksen, bygger den pa nytt", e);
                }
            }

            DropPopup.LOGGER.info("Laster ned NEU item-repo (engangsjobb, ~22 MB)...");
            Map<String, Entry> built = download();
            index = built;

            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(built, writer);
            }
            DropPopup.LOGGER.info("NEU-indeks bygget: {} items -> {}", built.size(), path);
        } catch (Throwable t) {
            DropPopup.LOGGER.error("NEU-repoet kunne ikke lastes. Faller tilbake til gjetting.", t);
            index = Map.of();
        } finally {
            loading = false;
        }
    }

    /** Leser zipen rett fra nettet og plukker ut bare det vi trenger. */
    private static Map<String, Entry> download() throws Exception {
        Map<String, Entry> built = new HashMap<>();

        try (InputStream raw = URI.create(ZIP_URL).toURL().openStream();
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.contains("/items/") || !name.endsWith(".json")) {
                    continue;
                }

                try {
                    // Zip-streamen ma ikke lukkes av Gson, derfor ingen try-with-resources her.
                    Reader reader = new InputStreamReader(zip, StandardCharsets.UTF_8);
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null || !json.has("displayname")) {
                        continue;
                    }

                    String display = clean(json.get("displayname").getAsString());
                    if (display.isEmpty()) {
                        continue;
                    }

                    Entry parsed = new Entry();
                    parsed.id = json.has("itemid") ? json.get("itemid").getAsString() : null;
                    if (json.has("internalname")) {
                        // Kjaeledyr heter "GOLDEN_DRAGON;4" - nivaet skal vekk.
                        String internal = json.get("internalname").getAsString();
                        int semi = internal.indexOf(';');
                        parsed.internal = semi < 0 ? internal : internal.substring(0, semi);
                    }

                    if (json.has("nbttag")) {
                        String nbt = json.get("nbttag").getAsString();
                        Matcher model = ITEM_MODEL.matcher(nbt);
                        if (model.find()) {
                            parsed.model = model.group(1);
                        }
                        int skull = nbt.indexOf("SkullOwner");
                        if (skull >= 0) {
                            Matcher texture = SKULL_TEXTURE.matcher(nbt.substring(skull));
                            if (texture.find()) {
                                parsed.texture = texture.group(1);
                            }
                        }
                    }

                    built.putIfAbsent(display, parsed);
                } catch (Exception ignored) {
                    // Et enkelt item med rar json skal ikke stoppe hele indeksen.
                }
            }
        }

        return built;
    }

    /**
     * Kjaeledyr heter "§7[Lvl {LVL}] §6Golden Dragon" i repoet, mens chatten
     * bare sier "Golden Dragon". Nivaprefikset ma vekk for at nokkelen skal
     * matche.
     */
    private static String clean(String displayName) {
        String name = COLOR.matcher(displayName).replaceAll("");
        name = PET_LEVEL.matcher(name).replaceAll("");
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Returnerer null hvis indeksen ikke er klar eller navnet er ukjent. */
    public static ItemStack lookup(String skyblockName) {
        Map<String, Entry> current = index;
        if (current == null || current.isEmpty()) {
            return null;
        }

        Entry entry = current.get(skyblockName.toLowerCase(Locale.ROOT).trim());
        if (entry == null) {
            return null;
        }

        ItemStack stack = new ItemStack(baseItem(entry.id));

        // Hodetekstur virker uten resourcepack, sa den setter vi alltid.
        if (entry.texture != null) {
            try {
                Multimap<String, Property> properties = ArrayListMultimap.create();
                properties.put("textures", new Property("textures", entry.texture));
                GameProfile profile = new GameProfile(SKIN_UUID, "", new PropertyMap(properties));
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            } catch (Exception e) {
                DropPopup.LOGGER.warn("Kunne ikke sette hodetekstur for '{}'", skyblockName, e);
            }
        }

        // Teksturmoder som Catharsis/FurfSky kjenner igjen items pa Skyblock-id-en,
        // ikke pa modellnavnet. Uten denne blir det bare et stykke papir.
        if (entry.internal != null) {
            try {
                CompoundTag root = new CompoundTag();
                // Hypixel legger id-en direkte i custom_data pa 26.1, og det er
                // der skyblock-api (og dermed Catharsis/FurfSky) leter.
                root.putString("id", entry.internal);

                // Eldre moder ser fortsatt etter den gamle 1.8-strukturen.
                CompoundTag extra = new CompoundTag();
                extra.putString("id", entry.internal);
                root.put("ExtraAttributes", extra);

                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            } catch (Exception e) {
                DropPopup.LOGGER.warn("Kunne ikke sette ExtraAttributes for '{}'", skyblockName, e);
            }
        }

        // Hypixel-modellen er den ekte varen, men den finnes bare hvis
        // server-resourcepacken er pa. Sett den bare hvis den faktisk er lastet.
        if (entry.model != null && !entry.model.equals("minecraft:player_head")) {
            Identifier id = Identifier.tryParse(entry.model);
            if (id != null && modelExists(id)) {
                stack.set(DataComponents.ITEM_MODEL, id);
            }
        }

        return stack;
    }

    private static Item baseItem(String itemId) {
        if (itemId == null) {
            return Items.PAPER;
        }
        String mapped = LEGACY_IDS.getOrDefault(itemId, itemId);
        Identifier id = Identifier.tryParse(mapped);
        if (id == null) {
            return Items.PAPER;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? Items.PAPER : item;
    }

    /**
     * Sjekker om en modell er lastet. Spillet gir samme "missing"-modell tilbake
     * for alt som ikke finnes, sa vi sammenligner med et navn vi vet ikke finnes.
     */
    private static boolean modelExists(Identifier id) {
        try {
            var manager = Minecraft.getInstance().getModelManager();
            var missing = manager.getItemModel(
                    Identifier.fromNamespaceAndPath(DropPopup.MOD_ID, "definitely_missing_model"));
            return manager.getItemModel(id) != missing;
        } catch (Exception e) {
            return false;
        }
    }

    /** Det vi tar vare pa per item. Holdes liten - indeksen lagres pa disk. */
    private static final class Entry {
        String id;
        String model;
        String texture;
        /** Skyblock sin egen id, f.eks. DIVAN_ALLOY. */
        String internal;
    }
}
