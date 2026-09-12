package no.isak.droppopup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kjenner igjen drop-meldinger fra Hypixel Skyblock.
 *
 * <p>Monstrene er hentet fra ekte logger, ikke gjettet:
 * <pre>
 *   RARE DROP! Enchanted Ancient Claw (+320% Magic Find!)
 *   RARE DROP! (32x Toxic Arrow Poison) (+112% Magic Find)
 *   VERY RARE DROP! (◆ Bite Rune I) (+112% Magic Find)
 *   PET DROP! [Lvl 1] Golden Dragon (+15% Magic Find!)
 * </pre>
 */
public final class DropMatcher {

    private static final Pattern DROP = Pattern.compile(
            "^(?<kind>PET DROP|RARE REWARD|RNG DROP|FLOOR DROP"
                    + "|(?:CRAZY |VERY |SUPER |INSANE )?RARE DROP)!\\s*(?<item>.+)$");

    /** FLOOR DROP! You found Fig Log x85 on the ground! */
    private static final Pattern FLOOR =
            Pattern.compile("^You found (?<item>.+?)(?: x(?<qty>\\d+))? on the ground!$");

    /**
     * RNG DROP! [MVP+] Gedss just found a Wither Shield!
     *
     * <p>Denne kringkastes til hele serveren, sa den gjelder som oftest en
     * annen spiller. Vi viser den bare hvis navnet er ditt eget.
     */
    private static final Pattern RNG = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?(?<who>\\S+) just found (?:a |an |the )?(?<item>.+?)!$");

    /** Hale pa formen "(+320% Magic Find!)". */
    private static final Pattern TRAILING = Pattern.compile("\\s*\\(\\+.*?\\)\\s*$");
    /** Pynt Hypixel setter foran navnet. */
    private static final Pattern LEADING_DECOR = Pattern.compile("^[✦★✯◆»\\s]+");
    /** Stabler kommer pakket: "(32x Toxic Arrow Poison)". */
    private static final Pattern WRAPPED = Pattern.compile("^\\((.+)\\)$");
    private static final Pattern QUANTITY = Pattern.compile("^(\\d+)\\s*x\\s+");
    private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl \\d+\\]\\s*");
    /** Stjerne-oppgraderinger bakerst: "Crimson Boots ✪✪✪✪✪". */
    private static final Pattern STARS = Pattern.compile("[✪➀-➄❝]+\\s*$");
    /**
     * "RARE REWARD! SomePlayer found a Recombobulator 3000 in their Bedrock Chest!"
     * er en kringkasting om andre spillere - ikke ditt drop.
     */
    private static final Pattern OTHER_PLAYER =
            Pattern.compile("^\\S+ found (?:a|an|the)\\b.*\\bin their\\b", Pattern.CASE_INSENSITIVE);

    private DropMatcher() {
    }

    /**
     * Returnerer null hvis linja ikke er ditt eget drop.
     *
     * @param selfName spillernavnet ditt, brukt til a skille dine RNG-drops fra
     *                 alle andres. Null godtar ingen RNG-drops.
     */
    public static DropEvent match(String chat, String selfName) {
        Matcher m = DROP.matcher(chat);
        if (!m.matches()) {
            return null;
        }

        String kind = m.group("kind");
        String body = m.group("item");

        if (kind.equals("FLOOR DROP")) {
            return matchFloor(body);
        }
        if (kind.equals("RNG DROP")) {
            return matchRng(body, selfName);
        }

        String item = body;
        item = TRAILING.matcher(item).replaceAll("");
        item = LEADING_DECOR.matcher(item).replaceAll("").trim();

        if (OTHER_PLAYER.matcher(item).find()) {
            return null;
        }

        Matcher wrapped = WRAPPED.matcher(item);
        if (wrapped.matches()) {
            // Pynten kan ligge inni parentesen ogsa: "(◆ Bite Rune I)"
            item = LEADING_DECOR.matcher(wrapped.group(1).trim()).replaceAll("").trim();
        }

        int quantity = 1;
        Matcher qty = QUANTITY.matcher(item);
        if (qty.find()) {
            quantity = Integer.parseInt(qty.group(1));
            item = item.substring(qty.end()).trim();
        }

        boolean pet = PET_LEVEL.matcher(item).find();
        item = PET_LEVEL.matcher(item).replaceAll("");
        item = STARS.matcher(item).replaceAll("").trim();

        if (item.isEmpty()) {
            return null;
        }

        return new DropEvent(kind, item, quantity, Rarity.fromKind(kind, pet));
    }

    private static DropEvent matchFloor(String body) {
        Matcher f = FLOOR.matcher(body);
        if (!f.matches()) {
            return null;
        }
        String qty = f.group("qty");
        int quantity = qty == null ? 1 : Integer.parseInt(qty);
        String item = STARS.matcher(f.group("item").trim()).replaceAll("").trim();
        return item.isEmpty() ? null
                : new DropEvent("FLOOR DROP", item, quantity, Rarity.FLOOR);
    }

    private static DropEvent matchRng(String body, String selfName) {
        Matcher r = RNG.matcher(body);
        if (!r.matches()) {
            return null;
        }
        // Kringkastes til hele serveren - bare ditt eget drop skal poppe opp.
        if (selfName == null || !selfName.equalsIgnoreCase(r.group("who"))) {
            return null;
        }
        String item = STARS.matcher(r.group("item").trim()).replaceAll("").trim();
        return item.isEmpty() ? null
                : new DropEvent("RNG DROP", item, 1, Rarity.MYTHIC);
    }

    /** Et drop klart til visning. */
    public record DropEvent(String kind, String item, int quantity, Rarity rarity) {
    }
}
