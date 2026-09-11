package no.isak.droppopup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Selve popupen: stort roterende item midt pa skjermen, tittel over,
 * og en partikkelsprut i rarity-fargen.
 *
 * <p>Alt er ren klient-rendering. Ingenting sendes til serveren.
 */
public final class PopupOverlay {

    /** Full lysstyrke (blokk 15, himmel 15) pakket slik spillet vil ha det. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Hvordan itemet vises. */
    private enum Style {
        /** Vanilla sin totem-animasjon - itemet svulmer opp og driver mot kamera. */
        ACTIVATION,
        /** Var egen: item som star stille midt pa skjermen og spinner. */
        SPIN,
    }

    private static final Style STYLE = Style.ACTIVATION;

    /** Partikkelen som spruter rundt spilleren i verden. */
    private static final SimpleParticleType WORLD_PARTICLE = ParticleTypes.SCRAPE;

    private static final float DURATION = 3.0f;      // sekunder totalt
    private static final float FADE_IN = 0.25f;
    private static final float FADE_OUT = 0.6f;
    private static final float SPINS_PER_SECOND = 0.5f;
    private static final int ITEM_SIZE = 64;         // "radius" i gui-piksler

    /** Tekstoppsett. Font-hoyden er 9 piksler, tittelen tegnes i dobbel storrelse. */
    private static final float TITLE_SCALE = 2.0f;
    private static final int TITLE_HEIGHT = (int) (9 * TITLE_SCALE);
    private static final int NAME_HEIGHT = 9;
    private static final int TEXT_GAP = 3;           // mellom tittel og navn
    private static final int BLOCK_GAP = 12;         // mellom tekst og item

    private static final Random RANDOM = new Random();

    private static DropMatcher.DropEvent current;
    private static ItemStack stack = ItemStack.EMPTY;
    private static long startedAt;
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static long lastFrameAt;
    /** Slas av hvis 3D-banen feiler, sa vi ikke prover igjen hver frame. */
    private static boolean use3d = true;

    private PopupOverlay() {
    }

    /**
     * Sjeldenheter som ikke er verdt en popup. Vanlig "RARE DROP!" gjelder
     * Enchanted Spider Eye, Enchanted Potato og annet smatteri som faller hele
     * tiden. Ta RARE ut av settet hvis du vil ha dem tilbake.
     */
    private static final java.util.EnumSet<Rarity> SKIPPED = java.util.EnumSet.of(Rarity.RARE);

    /** Start en ny popup. Erstatter den som eventuelt vises. */
    public static void show(DropMatcher.DropEvent event) {
        // Tier alene holder ikke: "RARE DROP!" daekker bade Enchanted Spider Eye
        // og Judgement Core. Derfor slipper whitelist-items alltid gjennom.
        if (SKIPPED.contains(event.rarity()) && !Whitelist.contains(event.item())) {
            DropPopup.LOGGER.info("Hopper over {} ({} er slatt av)", event.item(), event.rarity());
            return;
        }

        current = event;
        stack = ItemLookup.resolve(event.item());
        startedAt = System.currentTimeMillis();
        lastFrameAt = startedAt;

        PARTICLES.clear();
        if (STYLE == Style.SPIN) {
            for (int i = 0; i < event.rarity().particles; i++) {
                PARTICLES.add(Particle.burst(RANDOM));
            }
        }

        playEffects();

        DropPopup.LOGGER.info("Popup: {} x{} ({})", event.item(), event.quantity(), event.rarity());
    }

    /**
     * Lyd, verdenspartikler og - i ACTIVATION-stil - vanilla sin egen
     * totem-animasjon. Samme oppskrift som Skyblocker bruker: spillet har
     * allerede en polert 3D-animasjon for dette, sa vi later som om itemet
     * ble "aktivert".
     */
    private static void playEffects() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        if (STYLE == Style.ACTIVATION) {
            client.gameRenderer.displayItemActivation(stack);
        }

        // Ekte partikler rundt spilleren, ikke bare i hud-laget.
        client.particleEngine.createTrackingEmitter(client.player, WORLD_PARTICLE, 30);
        client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public static void render(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        if (current == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float elapsed = (now - startedAt) / 1000.0f;
        if (elapsed >= DURATION) {
            current = null;
            PARTICLES.clear();
            return;
        }

        float dt = Math.min((now - lastFrameAt) / 1000.0f, 0.1f);
        lastFrameAt = now;

        float alpha = fade(elapsed);
        int centerX = gfx.guiWidth() / 2;
        int textBlock = TITLE_HEIGHT + TEXT_GAP + NAME_HEIGHT;

        if (STYLE == Style.ACTIVATION) {
            // Vanilla tegner selve itemet over midten av skjermen. Vi legger
            // bare rarity-teksten under, sa den ikke ligger oppa modellen.
            renderText(gfx, centerX, gfx.guiHeight() / 2 + ITEM_SIZE, alpha);
            return;
        }

        // Tittel + navn + item behandles som en blokk, og blokka sentreres.
        // Ellers havner tyngdepunktet for hoyt fordi teksten henger over itemet.
        int total = textBlock + BLOCK_GAP + ITEM_SIZE * 2;
        int top = gfx.guiHeight() / 2 - total / 2;
        int itemCenterY = top + textBlock + BLOCK_GAP + ITEM_SIZE;

        renderParticles(gfx, centerX, itemCenterY, dt, alpha);
        renderItem(gfx, centerX, itemCenterY, elapsed, alpha);
        renderText(gfx, centerX, top, alpha);
    }

    /** 0 -> 1 inn, 1 en stund, 1 -> 0 ut. */
    private static float fade(float elapsed) {
        if (elapsed < FADE_IN) {
            return elapsed / FADE_IN;
        }
        float fadeOutStart = DURATION - FADE_OUT;
        if (elapsed > fadeOutStart) {
            return Math.max(0.0f, 1.0f - (elapsed - fadeOutStart) / FADE_OUT);
        }
        return 1.0f;
    }

    private static void renderItem(GuiGraphicsExtractor gfx, int centerX, int centerY,
                                   float elapsed, float alpha) {
        // Liten "pop" nar den kommer inn.
        float pop = elapsed < FADE_IN ? 0.6f + 0.4f * (elapsed / FADE_IN) : 1.0f;
        float spin = elapsed * SPINS_PER_SECOND * (float) (Math.PI * 2.0);

        if (use3d) {
            try {
                renderSpinning3d(gfx, centerX, centerY, spin, pop);
                return;
            } catch (Throwable t) {
                // Render-state-detaljer kan endre seg mellom versjoner. Da faller vi
                // tilbake til 2D i stedet for a spamme loggen eller kaste i render.
                DropPopup.LOGGER.warn("3D-rendering feilet, bruker 2D-fallback", t);
                use3d = false;
            }
        }

        renderSpinning2d(gfx, centerX, centerY, spin, pop);
    }

    /**
     * Ekte 3D: vi bygger samme render-state som et item pa bakken bruker, og
     * lar spillet tegne modellen med vare egne rotasjoner.
     */
    private static void renderSpinning3d(GuiGraphicsExtractor gfx, int centerX, int centerY,
                                         float spin, float pop) {
        Minecraft client = Minecraft.getInstance();

        ItemEntityRenderState state = new ItemEntityRenderState();
        state.entityType = EntityType.ITEM;
        state.count = 1;
        state.seed = 0;
        state.bobOffset = 0.0f;
        state.ageInTicks = 0.0f;
        state.lightCoords = FULL_BRIGHT;
        state.shadowRadius = 0.0f;
        state.isInvisible = false;
        state.isDiscrete = true;

        client.getItemModelResolver().updateForTopItem(
                state.item, stack, ItemDisplayContext.GROUND, client.level, null, 0);

        // Gui-rommet har Y ned, verden har Y opp. Uten denne halvrotasjonen om
        // Z star modellen pa hodet - vanilla gjor det samme for entities i gui.
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(15.0))
                .rotateY(spin);

        int half = Math.round(ITEM_SIZE * pop);
        gfx.entity(state, ITEM_SIZE * pop, new Vector3f(0.0f, 0.0f, 0.0f), rotation, null,
                centerX - half, centerY - half, centerX + half, centerY + half);
    }

    /**
     * Fallback: vanlig gui-item som klemmes sammen i bredden slik at det ser ut
     * som om det roterer om Y-aksen.
     */
    private static void renderSpinning2d(GuiGraphicsExtractor gfx, int centerX, int centerY,
                                         float spin, float pop) {
        float squash = (float) Math.cos(spin);
        float width = Math.max(0.08f, Math.abs(squash));

        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.scale(ITEM_SIZE / 16.0f * pop * width, ITEM_SIZE / 16.0f * pop);
        gfx.item(stack, -8, -8);
        pose.popMatrix();
    }

    private static void renderText(GuiGraphicsExtractor gfx, int centerX, int titleY, float alpha) {
        Minecraft client = Minecraft.getInstance();
        DropMatcher.DropEvent event = current;
        if (event == null) {
            return;
        }

        String title = event.rarity().title;
        String subtitle = event.quantity() > 1
                ? event.quantity() + "x " + event.item()
                : event.item();

        int titleColor = withAlpha(event.rarity().color, alpha);
        int white = withAlpha(0xFFFFFFFF, alpha);

        Matrix3x2fStack pose = gfx.pose();

        // Rarity oyverst i dobbel storrelse, itemnavnet rett under i vanlig storrelse.
        pose.pushMatrix();
        pose.translate(centerX, titleY);
        pose.scale(TITLE_SCALE, TITLE_SCALE);
        gfx.centeredText(client.font, title, 0, 0, titleColor);
        pose.popMatrix();

        gfx.centeredText(client.font, subtitle, centerX, titleY + TITLE_HEIGHT + TEXT_GAP, white);
    }

    private static void renderParticles(GuiGraphicsExtractor gfx, int centerX, int centerY,
                                        float dt, float alpha) {
        DropMatcher.DropEvent event = current;
        if (event == null) {
            return;
        }

        int base = event.rarity().color;
        for (Particle p : PARTICLES) {
            p.tick(dt);
            if (p.life <= 0.0f) {
                continue;
            }
            int x = centerX + Math.round(p.x);
            int y = centerY + Math.round(p.y);
            int size = Math.max(1, Math.round(p.size));
            gfx.fill(x, y, x + size, y + size, withAlpha(base, alpha * p.life));
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Enkel 2D-partikkel i gui-rommet. */
    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float size;

        static Particle burst(Random random) {
            Particle p = new Particle();
            // Skyter oppover i en smal vifte. Gui-rommet har Y nedover, sa
            // "opp" er negativ fart, og tyngdekraften trekker dem ned igjen.
            p.x = (random.nextFloat() - 0.5f) * ITEM_SIZE;
            p.y = 0.0f;
            p.vx = (random.nextFloat() - 0.5f) * 70.0f;
            p.vy = -(120.0f + random.nextFloat() * 190.0f);
            p.life = 1.0f;
            p.size = 1.0f + random.nextFloat() * 2.5f;
            return p;
        }

        void tick(float dt) {
            x += vx * dt;
            y += vy * dt;
            vy += 220.0f * dt;   // litt tyngdekraft, sa spruten faller
            vx *= 0.96f;
            life -= dt * 0.8f;
        }
    }
}
