# Drop Popup

Standalone Fabric client mod for Minecraft 26.1.2. When you get a rare drop in
Hypixel Skyblock it renders a large, spinning 3D model of the item over your
crosshair, with a rarity-coloured title and a particle burst.

Client-side only. It reads chat the client has already received and draws on
your own screen — nothing is sent to the server, and the game is not otherwise
touched.

## Install

1. Build (or take `build/libs/droppopup-1.0.0.jar`):
   ```
   gradlew build
   ```
2. Drop the jar into your instance's `mods/` folder.
3. Requires **Fabric API** and **Java 25** (same as any 26.1.2 Fabric mod).

## Try it without farming

Press **å** in game for a test popup. Each press steps to the next sample, so
you can see every rarity:

1. **Divan's Alloy** — RNG DROP, mythic (pink, biggest particle burst)
2. **Necron's Handle** — legendary (gold)
3. **Golden Dragon** — pet (gold)
4. **Giant's Sword** — epic (purple)
5. **32x Toxic Arrow Poison** — rare (blue), shows the stack count

Rebind under Options → Controls → Drop Popup.

GLFW names keys by their position on a US keyboard, so the binding is
registered as `LEFT_BRACKET` — that is the physical key printed **å** on a
Norwegian layout. If your layout puts it elsewhere, just rebind it.

## What it detects

Patterns taken from real logs, not guessed:

| Chat line | Shown as |
| --- | --- |
| `RARE DROP! Enchanted Ancient Claw (+320% Magic Find!)` | RARE (blue) |
| `VERY RARE DROP! (◆ Bite Rune I)` | EPIC (purple) |
| `CRAZY RARE DROP! Necron's Handle` | LEGENDARY (gold) |
| `PET DROP! [Lvl 1] Golden Dragon` | PET (gold) |
| `RARE REWARD! Wither Blood` | REWARD (aqua) |
| `FLOOR DROP! You found Fig Log x85 on the ground!` | FLOOR (green), 85x |
| `RNG DROP! [MVP++] YourName just found a Giant's Sword!` | MYTHIC (pink) |

Stacks (`(32x Toxic Arrow Poison)`) are unwrapped and the count shown.

Two kinds of message are deliberately **ignored**, because they are about other
players, not you:

* `RARE REWARD! SomeGuy found a Recombobulator 3000 in their Bedrock Chest!`
* `RNG DROP! [MVP+] SomeoneElse just found a Wither Shield!` — RNG drops are
  broadcast server-wide, so the mod compares the name against your own and only
  pops up for yours.

## Which item model is shown

Skyblock items do not exist in the vanilla registry, so the mod guesses:
`Enchanted Spider Eye` → Spider Eye, `Sharp Felthorn Reaper` → the closest
vanilla match, with a small override table in `ItemLookup.java` for common
Skyblock-only items. Anything unrecognised falls back to a Nether Star, so the
popup always shows something.

Add your own mappings to `OVERRIDES` in `ItemLookup.java`.

## The animation

Default style is `ACTIVATION`: the mod hands the item to vanilla's own
`gameRenderer.displayItemActivation(stack)` - the Totem of Undying pop. The
item swells from the centre, spins and drifts toward the camera. Polished, 3D,
and zero custom render code. This is the approach Skyblocker uses.

Alongside it:

* `particleEngine.createTrackingEmitter(player, ParticleTypes.SCRAPE, 30)` -
  real world particles around the player, not flat HUD sprites.
* `SoundEvents.UI_TOAST_CHALLENGE_COMPLETE` - the advancement chime.
* The rarity title and item name are drawn under the animation, in the rarity
  colour.

Set `STYLE = Style.SPIN` at the top of `PopupOverlay.java` to go back to the
custom centered spinning model with its own upward particle fountain. Both
paths are kept.

## Which drops get a popup

Two rules, both in `PopupOverlay`:

1. Rarities in `SKIPPED` are muted. `Rarity.RARE` is there by default - that is
   the tier Enchanted Spider Eye and Enchanted Potato fall in.
2. **Unless** the item is on the whitelist in
   `config/droppopup-whitelist.json`, which always wins.

The whitelist exists because tier alone is a bad signal: plain `RARE DROP!`
covers both Enchanted Spider Eye and Judgement Core. The defaults are
Skyblocker's curated list (Judgement Core, Overflux Capacitor, Warden Heart,
Minos Relic, Tiki Mask, ...) plus a few obvious big-ticket items. Edit the file
and restart to change it.

## Rendering notes

26.1's GUI layer is 2D only (`pose()` returns a `Matrix3x2fStack`), so a real
3D spin cannot come from `gfx.item()`. Instead the mod builds the same
`ItemEntityRenderState` a dropped item on the ground uses and hands it to
`gfx.entity(...)` with its own quaternion rotation — genuine 3D.

If that path ever throws (render-state internals change between versions), it
logs once and permanently falls back to a 2D "coin flip" effect, so the mod
cannot crash your client.

## Texture packs (Catharsis / FurfSky)

Skyblock texture packs do not retexture by model path - they resolve the item's
**Skyblock id**. On 26.1 that id lives at the *root* of the `custom_data`
component:

```
custom_data = { id: "DIVAN_ALLOY", ... }
```

not nested under `ExtraAttributes` as it was on 1.8. `skyblockapi`
(`getUnsafeTag()` -> `CUSTOM_DATA` -> key `id`) is what Catharsis calls, and it
contains no reference to `ExtraAttributes` at all.

So every popup stack carries that id, taken from the NEU repo's
`internalname`. That is what makes FurfSky-style packs texture the spinning
model correctly, and it works whether or not Hypixel's server resource pack is
loaded.

## Tuning

Constants at the top of `PopupOverlay.java`:

| Constant | Meaning |
| --- | --- |
| `DURATION` | Seconds the popup stays up (3.0) |
| `SPINS_PER_SECOND` | Rotation speed (0.5) |
| `ITEM_SIZE` | Half-size of the model in GUI pixels (64) |
| `FADE_IN` / `FADE_OUT` | Ease in/out timings |

Particle counts and colours live in `Rarity.java`.
