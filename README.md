# Minecraft Bounty Hunt (Fabric 1.20.1, Java 17)

Implements Phases 1-6 of the handoff spec (the "vertical slice"): bounty generation, personal bounties,
a 3x3 town notice board, chest-menu UI (board, confirmation, party lobby), a live-tracking Hunting Compass,
parties with betrayal, and hunts with scaled, glowing monsters.

## Revision 6 changes: Bounty Zombie (first custom monster)
- **Own entity and models per difficulty:** zombie bounties now spawn `bountyhunt:bounty_zombie` (vanilla zombie behaviour) with one model per tier: Normal "Grave Walker" (rag belt, wraps), Medium "Ravager" (iron pauldrons, chest plate, bone horns), Hard "Warlord" (horned helm, spiked pauldrons, back spikes, bracers), Legendary "Dread King" (gold crown, swept horns, obsidian armour with gold spikes, cape, greaves). Stats, size and glow are unchanged.
- **Loot per difficulty:** `data/bountyhunt/loot_tables/entities/bounty_zombie/tier_0..3.json` (placeholder items; the real economy comes later). Loot drops in the world like vanilla; per-player loot belongs to the reward phase.
- **Data-driven:** the extra geometry lives in `assets/bountyhunt/monster_models/bounty_zombie.json` and the textures are painted from scratch (no Mojang assets). Edit `tools/gen_bounty_zombie.py` (parts, colours, loot) and re-run `python tools/gen_bounty_zombie.py` from the project root to regenerate everything.
- **Other monsters** are still vanilla (with the tier textures from `gen_monster_textures.py`). To add one later: an entity class with a tier, a renderer, a JSON and a generator section.
- Risk spots: `ZombieEntity` overrides (`initDataTracker`, `getLootTableId` (fixed: `getLootTable` is final), `initEquipment`), `FabricEntityTypeBuilder`, and `ZombieEntityModel` generics. Dragging the model and loot between difficulties is handled by `MonsterScaling.apply` setting the tier.

## Revision 5 changes
- **Surface spawns:** the monster always spawns on real ground at the chunk surface. At spawn time the surface is re-read from the chunk (not the Y saved when the bounty was created), walking down past logs and leaves, rejecting water/lava. If the scaled hitbox does not fit, it takes the nearest spot within 8 blocks where it does. Bounty targets are now also picked on real ground, so the shown Y is solid land.

## Revision 4 changes
- **Scroll icons:** redrawn with shading, rolled ends, ribbon and wax seal per tier. Medium has silver caps and a gem seal; Hard has charred edges, iron spikes and a skull seal with flickering embers (animated); Legendary has gold caps and border with a sparkling seal (animated).
- **Tougher monster textures:** Medium (purple), Hard (red) and Legendary (gold) monsters can use darker, harsher textures with glowing cracks, scars and a glowing face. Generate them once: `pip install pillow` then `python tools/gen_monster_textures.py` (reads your own Minecraft 1.20.1 jar; add `--preview sheet.png` to see vanilla vs the three tiers). Normal keeps the vanilla look. No generated file = vanilla texture, so the mod works either way. Limits: texture only (no new horns or armour geometry), and the glow is painted, not emissive. Tune `TIERS` at the top of the script and re-run. If you publish the mod, ship the script rather than the generated PNGs (derived from Mojang assets).

## Revision 3 changes
- **Board menu:** a custom panel, not a chest: 27 public slots, a genuinely blank gap (no filler items), then 3 personal slots, one blank column, and two real buttons (icon + label): Reroll and Lobby (with tooltips; Reroll shows rerolls left). Only the 27 + 3 bounty slots exist. Open personal parties of other players appear first in the public grid.
- **"Create a party?":** the vanilla confirm screen (same look as the server resource-pack prompt) with `Yes`/`No`. Esc = No. No returns to the board; Yes opens the lobby.
- **Lobby:** a real screen: the target monster's 3D model on the left, the roster with player heads and names on the right, buttons bottom-left (Commence, Leave, Disband/Abandon, Back). Host can kick via the Kick link on each row.
- **Icons:** colour icons come from the mod's own bitmap font (`font/icons.json`, `util/Icons.java`). The PNGs shipped are plain placeholders; run `pip install pillow` then `python tools/fetch_emoji.py` (needs internet) to replace them with Twemoji, then rebuild.
- **Compass:** uses the vanilla `CompassAnglePredicateProvider` itself (same maths, wobble and frames as a compass pointing at world spawn); target updates 4 times a second. Action bar text is multi-coloured (Target / coordinates / distance number / "blocks").
- **Announcements:** monster name removed; title only on the black bar.
- **Phase 7 downed state:** a lethal hit on a hunter in a commenced hunt downs them (6 hearts, 25% speed, crawling pose, cannot attack/build/use items). Before the monster spawns downs are unlimited; after it spawns the 4th down sends the player to spectator. A teammate holds right-click on a downed player for 5 s to revive (one reviver at a time; letting go interrupts); revived at 50% max health, down count is kept. Monsters only keep a downed target while it is the nearest hunter. Spectators stay spectators across reconnects until the hunt ends.
  Decisions: a hit that would kill a downed player eliminates them; void and /kill still kill (and eliminate); a killing blow by a party member on the host stays a betrayal (real death, host removed, killer leads); if every remaining hunter is downed or eliminated the hunt fails, since nobody could revive.

## Revision 2 changes
- **Board:** 3x3 cowboy-town notice board (posts, roof, wanted posters). One item places all nine parts; right-click any part; breaking any part removes the whole board. Needs a free 3-wide, 3-tall space in front of you.
- **UI:** ordinary chest menus. Bounties are scrolls (own texture per difficulty); hover for monster info. Click a scroll -> Yes/No confirmation -> party lobby with player heads. Lobby: Commence, Leave, Disband/Abandon, Kick (click a head), Back.
- **Announcements:** big text on a black bar with sounds: "Hunt Commence!" (raid horn), "Hunt or be Hunted" (thunder, when the monster spawns), "The monster have fallen" (challenge-complete), "Your party have been slain" (explosion). Each shows the monster name, e.g. "Hard Zombie".
- **Compass:** needle tracks the monster in real time (spawn point until it exists). Right-click shows exact coordinates in the action bar above the XP bar (about 3 s, then fades).
- **Monsters:** infinite Glowing effect; spawn radius 50; size x1 / x1.5 / x2 / x2.5.
- **Failure (interim, until Phase 7):** after the monster spawns, a death eliminates that player (spectator until the hunt ends). When every member is eliminated the hunt fails.
- If you already ran an earlier build, `config/bountyhunt.json` still holds `spawnRadius: 100`; edit it to 50 or delete the file.

**Not yet implemented:** Phase 7 (downed / revive / spectator, down limit, hunt failure when all eliminated)
and Phase 8 (reward screens and claims). `RewardManager.onHuntCompleted` is the hook for Phase 8.

## Build (the jar has to be built on your machine)
The authoring environment had no internet, so no jar is included; the project is complete source.
1. Install JDK 17 and set Gradle JVM to 17 (IntelliJ: Settings > Build Tools > Gradle; or set JAVA_HOME).
2. Copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` from any working Fabric project into this folder. `gradle/wrapper/gradle-wrapper.properties` here already pins Gradle 8.5 (Loom 1.5 does not work with Gradle 9).
3. Run `./gradlew build` (Windows: `gradlew build`). The mod jar is `build/libs/bountyhunt-0.1.0.jar` (not the `-sources` jar).
4. Put the jar and Fabric API 0.92.2+1.20.1 in `.minecraft/mods` (Fabric Loader 0.15+, Java 17). For a dedicated server put both in the server's `mods` folder.
Optional: `pip install pillow` and `python tools/fetch_emoji.py` first to swap the placeholder icons for Twemoji.

## Build (details)

1. Copy `gradlew`, `gradlew.bat` and the `gradle/wrapper` folder from any Fabric project (Gradle 8.x), or run `gradle wrapper --gradle-version 8.5`.
2. `./gradlew build` -> jar in `build/libs/`. `./gradlew runClient` to test.
3. Dev versions are in `gradle.properties` (Loom 1.5, Yarn 1.20.1+build.10, Fabric API 0.92.2+1.20.1).

This project was written without being able to compile it (no network in the authoring environment).
Expect to fix a few compile errors or mixin target mismatches on the first build; the riskiest spots are listed below.

## Testing the vertical slice

1. Creative: take the **Bounty Board** (Functional tab) and place it. Right-click it.
2. Select a bounty -> **Accept / Join** -> **Commence**. You receive a Hunting Compass; right-click shows exact coordinates and distance.
3. `/tp` near the target (or walk). Within 100 blocks the scaled monster spawns once.
4. Kill it: the hunt completes (reward message is a placeholder).
5. Handy commands: `/bounty status`, `/bounty leave`, `/bounty abandon`, `/bounty regenerate` (op).
   Use `/time add 24000` to trigger the next daily generation.
6. Restart the world mid-hunt: party, host, coordinates and monster association persist; no duplicate monster.

## Decisions for the spec's open questions (section 15)

All are defaults chosen to get a playable build; change them in `bountyhunt.json` or the code.

| Topic | Default used |
|---|---|
| Daily reset | Overworld time-of-day / 24000 (sleeping and `/time` advance days). One batch per new day. |
| Party formation | Contract is reserved as soon as someone accepts it (PARTY_FORMING); others join that party. Unused-contract expiry only applies to AVAILABLE contracts. |
| Host kicks | Only before Commence, and never after taking power by betrayal. |
| Host disconnect | Leadership passes to a random online member. Only the *original* host regains it on reconnect. |
| Abandon active hunt | Monster is removed (also when its chunk next loads); contract is deleted. |
| Personal contracts | A player can be in one party at a time. A personal bounty in progress survives the daily refresh and blocks rerolling. |
| Distances | Horizontal (X/Z) only, board distance rounded to 10 blocks; compass distance is from the player. |
| Monster spawn trigger | Horizontal distance <= 50 blocks (`spawnRadius`). |
| Target location | 600-3000 blocks from world spawn, land only (ocean/river biomes rejected, solid non-liquid ground). Y is fixed at creation. Chunks are generated at creation to read the surface. |
| Monster roster | Zombie, Husk, Skeleton, Stray, Spider, Cave Spider, Creeper, Witch, Enderman. Drowned/Slime/Phantom disabled until handled individually. |
| Damage scaling | One multiplier on all damage a bounty monster deals (melee, arrows, potions, explosions). Health and armor use attributes; zero-armor mobs use a baseline of 2 before multiplying. |
| Sunlight | Bounty monsters take no sun-burn damage (they would otherwise die in minutes). |
| Compass | Creative tab item; also given to every party member on Commence (`giveCompassOnCommence`). Texture is the vanilla compass. |

## Known risk spots (check first if the build or launch fails)

- Chest menus subclass `GenericContainerScreenHandler` and override `onSlotClick`; the lobby/board also call `refresh()` once a second.
- Board multiblock: part orientation (left/right) is derived from `facing.rotateYCounterclockwise()`; if the posts look mirrored in the world, swap the two post x-ranges in the models.
- Compass: `new CompassAnglePredicateProvider((world, stack, entity) -> BlockPos)` in `BountyHuntClient`. The target callback returns a `GlobalPos` (fixed).
- Board menu: custom `ExtendedScreenHandlerType` + `HandledScreen`; lobby uses `InventoryScreen.drawEntity` and `PlayerSkinDrawer.draw` (1.20.1 signatures).
- Downed: `ServerLivingEntityEvents.ALLOW_DEATH` cancels death after setting health; `PlayerEntityMixin` forces the crawl pose; revive relies on the client repeating the right-click interaction about every 4 ticks while held.
- `LivingEntityMixin`: adds a `TrackedData` from a mixin static initializer; `@ModifyVariable` on `damage` with an extra `DamageSource` parameter.
- `EntityMixin`: injects `Entity.getDimensions(EntityPose)`. Mobs that override it without calling `super` will not resize (test each mob).
- `LivingEntityRendererMixin`: INVOKE target on `LivingEntityRenderer.scale(...)`.
- Creeper explosion radius is not scaled (only the damage multiplier applies).
- A board part destroyed by something other than a player (explosion, piston) can leave a partial board.

## Layout

`bounty/` generation, expiry, personal slots - `party/` roster, host rules, betrayal - `hunt/` lifecycle, spawning, persistence guards -
`monster/` roster, scaling, `BountyScaled` duck interface - `data/` PersistentState (UUID-keyed) - `network/` snapshot + actions -
`gui/` chest menus - `client/` HUD announcements + compass needle - `mixin/` scale, hitbox, damage, render - `reward/` Phase 8 hook.

## Credits
If you use `tools/fetch_emoji.py`: Twemoji graphics (c) Twitter, Inc. and other contributors, licensed CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Keep this credit.
