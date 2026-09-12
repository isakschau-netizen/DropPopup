package no.isak.droppopup;

/**
 * Sjeldenhet styrer farge pa tittel og partikler.
 * Fargene folger Hypixel sine egne rarity-farger sa det kjennes kjent ut.
 */
public enum Rarity {
    RARE("RARE DROP", 0xFF5555FF, 24),
    EPIC("EPIC DROP", 0xFFAA00AA, 34),
    LEGENDARY("LEGENDARY DROP", 0xFFFFAA00, 48),
    MYTHIC("MYTHIC DROP", 0xFFFF55FF, 64),
    PET("PET DROP", 0xFFFFAA00, 48),
    REWARD("RARE REWARD", 0xFF55FFFF, 24),
    FLOOR("FLOOR DROP", 0xFF55FF55, 20);

    /** Tekst over itemet. */
    public final String title;
    /** ARGB. */
    public final int color;
    /** Hvor mange partikler spruten skal ha. */
    public final int particles;

    Rarity(String title, int color, int particles) {
        this.title = title;
        this.color = color;
        this.particles = particles;
    }

    public static Rarity fromKind(String kind, boolean pet) {
        if (pet || kind.startsWith("PET")) {
            return PET;
        }
        if (kind.startsWith("RARE REWARD")) {
            return REWARD;
        }
        if (kind.startsWith("RNG")) {
            return MYTHIC;
        }
        if (kind.startsWith("FLOOR")) {
            return FLOOR;
        }
        if (kind.startsWith("INSANE")) {
            return MYTHIC;
        }
        if (kind.startsWith("CRAZY")) {
            return LEGENDARY;
        }
        if (kind.startsWith("VERY") || kind.startsWith("SUPER")) {
            return EPIC;
        }
        return RARE;
    }
}
