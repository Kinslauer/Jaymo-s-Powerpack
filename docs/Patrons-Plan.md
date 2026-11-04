# Patch 11a: Patron gods (patrons, shrines, swearing, favour, blessings)

Kingdoms **0.7.509** (`kingdoms/gradle.properties`), Powers **0.20.8** (`gradle.properties`, one small reflective read),
Kitchen **0.5.57** (`kitchen/gradle.properties`, one small hook; optional, see Q4). Tag new code comments `(0.7.509)`,
`(0.20.8)` and `(Kitchen 0.5.57)`.

Andy picked the Runic Gods idea; this is our own version, nothing copied. Part (a) only: the eight patrons, shrines,
swearing, favour, blessings and the villagers' faiths. Part (b) (turning away, clerics preaching, realm side) and part
(c) (temples, champions, weapons) are **not** built here; section 12 lists the hooks they slot into.

Rules from Andy that shape this:
- No new dimension, no lore written on screen. The gods are learned from clerics, shrines and books. The screen shows
  names, offerings, favour, tier and what the blessings do (mechanics), never lore. Lore is in the patrons' books.
- Eight patrons: the seven plus **The Truth**, the human faith. Clerics serve The Truth and never one of the seven.
  Most humans hold The Truth. Non-human folk lean to the seven. Truth and the seven are cooler to each other both ways,
  on top of heritage prejudice.
- The existing shrine is the way in: hold use to pray as now; an offering dedicates it; three days running and you are
  sworn. Devout life stories start sworn. Four tiers; tiers 1 to 3 each add a passive blessing; tier 4 adds the active
  boon on the Z key menu (long cooldown). The Devotion perk slows decay.

---

## 1. Ownership (decided: Kingdoms)

The shrine, the Skill tree (Devotion), creeds, temperaments, heritage, prejudice, reputation and the life story all live
in Kingdoms, so the patron registry and per-player state live in **Kingdoms** (`com.jaymoskingdoms`, flat package like
the rest, because the hooks call package-private helpers such as `Will.magical`, `GreatWars.creed`, `Havens.is`,
`Accusations.priest`, `Prejudice.masked`, `Vampires.expose`).

Powers never compiles against Kingdoms. It reads one blessing by reflection on
`com.jaymoskingdoms.Patrons.blessing(Player, String)`, the same way `core/wounds/MedicineSkill` already reads
`com.jaymoskingdoms.Skills.rank(Player, String)`. Kingdoms reaches Powers through the existing `PowersCompat` lookups and
`PowersCompat.listen(...)` on Powers' public listener lists. Powers gets **no new attachment**, so
`JaymosPowers.registerAttachments()` is unchanged.

---

## 2. New and changed files

### Kingdoms, new (main)
| File | Responsibility |
|---|---|
| `kingdoms/src/main/java/com/jaymoskingdoms/Patron.java` | The eight-patron enum: key, colour, `seven` flag, rival, offering tag, glyph, deed list, blessing keys. |
| `kingdoms/src/main/java/com/jaymoskingdoms/Patrons.java` | Per-player state attachment, favour, tiers, swearing streak, decay, deeds, known patrons, the devout start, the payloads, the boon call, `blessing(Player, String)` for Powers. |
| `kingdoms/src/main/java/com/jaymoskingdoms/Blessings.java` | Every blessing's effect and the boons: the functions other classes call (`dealt`, `sneak`, `wear`, `haltGap`...), the per-second blessing tick, the eight boons. |
| `kingdoms/src/main/java/com/jaymoskingdoms/Faiths.java` | Each villager's and folk's faith (drawn once, stored), realm faiths, the faith rivalry as an opinion modifier (prices, hearts, guards, neighbours, reputation, MCA prompt). |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/ItemStackWearMixin.java` | Sound Steel and Father's Fire: inject at `RETURN` of `ItemStack.processDurabilityChange(int, ServerLevel, ServerPlayer)` (private; verified in 26.3), `cir.setReturnValue(Blessings.wear(self, player, cir.getReturnValue()))`. Powers' `GradedWearMixin`/`TinkerToolWearMixin` call the real method through `@Shadow`, so graded and tinker tools are covered too. |

### Kingdoms, new (client)
| File | Responsibility |
|---|---|
| `kingdoms/src/client/java/com/jaymoskingdoms/client/PatronScreen.java` | The patrons screen (section 8). Copies `TierScreen`'s list-and-detail layout and `Ui` colours. |
| `kingdoms/src/client/java/com/jaymoskingdoms/client/BoonScreen.java` | The boon menu (section 8). |
| `kingdoms/src/client/java/com/jaymoskingdoms/client/PatronKeys.java` | The boon key, the `OpenShrine` receiver, `register()`. |

### Kingdoms, new (resources)
- `data/jaymoskingdoms/tags/item/offerings/<key>.json`, eight files (section 3).
- `data/jaymoskingdoms/lore/patron_<key>.json`, eight lore books (section 10).
- `assets/jaymoskingdoms/models/block/shrine_dedicated.json` (the shrine model plus a glyph tile) and
  `models/block/shrine_<key>.json` x8 (`"parent": "jaymoskingdoms:block/shrine_dedicated"`, `"textures": {"glyph":
  "jaymoskingdoms:block/shrine_glyph_<key>"}`).
- `assets/jaymoskingdoms/textures/block/shrine_glyph_<key>.png` x8 (section 11), drawn by new `tools/gen_shrine_glyphs.py`.

### Kingdoms, changed
| File | Change |
|---|---|
| `Shrine.java` | `DEDICATION` block-state property, `useItemOn` for offerings, sneak-use opens the screen, prayer feeds `Patrons.prayed(...)` (section 4). |
| `Healing.java` | Shrine registration: `registerDefaultState` is in `Shrine`'s constructor; nothing else changes. The debug line also logs `Patrons.STATE` and `Faiths.FAITH`. |
| `assets/jaymoskingdoms/blockstates/shrine.json` | One variant per `patron` value (section 11). |
| `Skills.java` | Append `DEVOTION` last in `Perk` (section 7); `sneakFactor` subtracts `Blessings.sneak(player)`; `holdTicks` times `Blessings.pickHold(player)`; `pickedClean` and `unseenTheft` call `Patrons.deed(...)`. |
| `JaymosKingdoms.java` | `Faiths.register()` right after `VillagerHeritage.register()` (line 458); `Patrons.register()` after `PlayerTemperaments.register()` (line 495). |
| `LifeStory.java` | `Patrons.storyStart(player)` after `Heritage.set(player, heritage)` in `live()` (~line 956) and after `PlayerTemperaments.settle(player, true)` in the later-road path (~line 1029). |
| `VillagerHeritage.java` | `born(...)`: `Faiths.forget(villager)` at the end (a new blood draws its faith again). `inherit(...)`: `Faiths.inherit(child, mother, father)` after `born(...)`. |
| `Prejudice.java` | Unchanged. Faith is added beside it at each cost point, not inside `minds`, so blood mutters stay about blood. |
| `mixin/VillagerPriceMixin.java` | After `Prejudice.prices(...)`: `com.jaymoskingdoms.Faiths.prices((Villager)(Object)this, player);` |
| `mixin/mca/McaDialogueMixin.java` (~line 483) | `taken` also adds `Faiths.hearts(villager, player, gained)`; after it, `int warm = Blessings.flockHearts(villager, player, gained); if (warm > 0) modHearts(warm)`. |
| `Approaches.java` (~lines 643-653) | `int minds = Prejudice.guard(mob, player) + Faiths.guard(mob, player) - Blessings.searchEase(player, f);` (clamped at 0 where used); the halt gap becomes `Blessings.haltGap(player, f, Faiths.haltGap(mob, player, Prejudice.haltGap(mob, player, HALT_TICKS)))`. |
| `Neighbours.java` (line 195) | `r -= Faiths.neighbourLean(a, b);` after the prejudice line. |
| `Reputation.java` `change` | After `Prejudice.reputation(...)`: `by = Faiths.reputation(kingdom, player, by); by = Blessings.reputation(kingdom, player, by);` |
| `ChatPrompts.java` | After the blood prompt: `String faith = Faiths.prompt(villager, player);` appended the same way. |
| `mixin/LivingEntityHurtMixin.java` | Wrap `Heritage.dealt(...)` in `Blessings.dealt(self, source, ...)` (so it runs right after the heritages). |
| `Valor.java` `featKeyed` | After the `POWERED_GROWTH` line: `worth *= Blessings.valorGrowth(holder);` |
| `WarParties.java` | `start(...)` gains a trailing `int extra` added to `size` (line 240); the call-up in `join` (~line 369) passes `Blessings.partyExtra(player)`, every other caller passes 0. `deliver(...)` calls `Patrons.deed(p, Patron.CROWNED_SUN, Patrons.DEED_GREAT, "siege")` for each marched player. |
| `Quests.java` | `turnIn(...)`: on success with `q.royal()`, `Patrons.deed(player, Patron.CROWNED_SUN, Patrons.DEED_MID, "royal_quest")`. Hunt quest's beast call (line 1177): `Sightings.HUNT_RANGE` becomes `Blessings.huntRange(player, Sightings.HUNT_RANGE)`. |
| `Theft.java` | `witnesses(...)`: return `List.of()` first when `Blessings.unseen(player)`. Line 647: `long until = level.getGameTime() + Blessings.wantedTicks(player, w == Weight.HEAVY ? OUTLAW_TICKS : HUNT_TICKS);` |
| `Lockpicking.java` (lines ~225 and ~306) | `width *= Blessings.lockWidth(sp);` On each PICKED result (the three `Skills.grant(..., XP_PICK ...)` sites): `Patrons.deed(player, Patron.ASH_WHISPERER, Patrons.DEED_SMALL, "lock")`. |
| `Accusations.java` | `guilty` becomes package-private; the guilty branch's `EXPOSED.put` + `Suspicion.witnessed` move into `static void expose(ServerLevel level, ServerPlayer accused, long now)`; the true branch's `GAIN` and the false branch's `LOSS`/`DEAF_TICKS` go through `Blessings.accuseGain(accuser, GAIN)`, `Blessings.accuseLoss(accuser, LOSS)`, `Blessings.deafTicks(accuser, DEAF_TICKS)`; a true accusation calls `Patrons.deed(accuser, Patron.TRUTH, Patrons.DEED_GREAT, "accusation")`. |
| `nightfall/Nightfall.java` `plagueDoctor` | `... || mob instanceof Player p && Patrons.blessed(p, Patron.LANTERN_WARDEN, 3)`. Make `Patrons.blessed` public (Nightfall is another package). |
| `disasters/Storms.java` `strike` | After `top` is found: `if (Blessings.calmsSea(level, top)) return;` (`Blessings.calmsSea` public). |
| `KitchenCompat.java` `skills()` | Registers Clean Cuts and the carcass deed on Kitchen's new `Hanging` hooks by reflection, with the usual "too old" warning. |
| `PowersCompat.java` | New `zoneBonus(ToDoubleFunction<ServerPlayer>)`: adds to Powers' `com.jaymospowers.tinkering.smithing.Forging.ZONE_BONUS` (a public `List<ToDoubleFunction<ServerPlayer>>`), same shape as `listen`. |
| `client/StoryHoldScreen.java` | `HINTS = 87`. |
| `client/SkillScreen.java` | A "Patrons" button left of the World Tier button: `.bounds(right - 60 - 4 - 80 - 4 - 70, fy, 70, 16)`, opens `new PatronScreen(this, null)`. |
| `client/JaymosKingdomsClient.java` | `PatronKeys.register();` |
| `jaymoskingdoms.combat.mixins.json` | Add `"ItemStackWearMixin"`. |
| `lang/en_us.json` | Section 9. |
| `kingdoms/gradle.properties` | `version=0.7.509`. |
| `kingdoms/MODLOG.md` | Section 13. |

### Powers, changed (0.20.8)
| File | Change |
|---|---|
| `src/main/java/com/jaymospowers/core/wounds/MedicineSkill.java` | A second reflective `Method` looked up once next to `KINGDOMS_RANK`: `Class.forName("com.jaymoskingdoms.Patrons").getMethod("blessing", Player.class, String.class)` (null without Kingdoms or on an older one). `static float blessing(LivingEntity healer, String key)` returns 0 on anything but a player or on any failure. `ticks(...)` becomes `Math.max(1, Math.round(ticks * (1 - STEADY_HANDS_PER_RANK * rank(...)) * (1 - blessing(healer, "gentle_hands"))))`. Javadoc line: `(0.20.8) the Hearthkeeper's Gentle Hands (Kingdoms' patrons).` |
| `gradle.properties` | `version=0.20.8`. |
| `MODLOG.md` | Section 13. |

### Kitchen, changed (0.5.57, optional: Q4)
| File | Change |
|---|---|
| `kitchen/src/main/java/com/jaymoskitchen/corpse/Hanging.java` | `public static void addExtraCuts(ToIntFunction<Player> extra)` and `public static void addOnDone(BiConsumer<Player, Entity> done)`. In `cut(...)` when `done == 0`: after `corpse.addLoot(corpse.blockExtras())`, sum the extras and add `blockExtras()` that many more times. In the `cutsLeft <= 1` branch before `discard()`: tell each `onDone`. Kitchen stays free of Kingdoms. |
| `kitchen/gradle.properties` | `version=0.5.57`; rebuild and replace `libs/jaymos-kitchen-0.5.56.jar` with the 0.5.57 jar. |

---

## 3. The patrons (`Patron.java`)

```java
public enum Patron {
	HEARTHKEEPER("hearthkeeper", 0xFFE0A050, true),
	ANVIL_FATHER("anvil_father", 0xFFB0B4BC, true),
	DEEP_MOTHER("deep_mother", 0xFF3A8FB7, true),
	GREEN_HUNTSMAN("green_huntsman", 0xFF5E9E3A, true),
	LANTERN_WARDEN("lantern_warden", 0xFF9AA8FF, true),
	CROWNED_SUN("crowned_sun", 0xFFFFC21A, true),
	ASH_WHISPERER("ash_whisperer", 0xFF8A5A4A, true),
	// (0.7.509) Andy: "an 8th patron known as the truth ... usually the faith among humans"
	TRUTH("truth", 0xFFF2F2F2, false);
	public final String key; public final int colour; public final boolean seven;
	public final TagKey<Item> offerings;   // jaymoskingdoms:offerings/<key>
	public @Nullable Patron rival;          // set in a static block
	public static @Nullable Patron byKey(@Nullable String key);
	public Component named();               // "patron.jaymoskingdoms.<key>"
	public boolean rivals(Patron other);    // rival either way, or Truth vs any of the seven
}
```
- **Stored by key, never ordinal** (the Perk-ordinal lesson). Appending a ninth later moves nothing.
- Rivals (static block): `CROWNED_SUN.rival = ASH_WHISPERER`, `ASH_WHISPERER.rival = CROWNED_SUN`,
  `DEEP_MOTHER.rival = ANVIL_FATHER`, `ANVIL_FATHER.rival = DEEP_MOTHER`. The Lantern Warden's rival is the vampires'
  dark, not a patron (handled in `Faiths.minds`). The Truth's rivals are all seven (`rivals()`), at the lighter
  `Faiths.FAITH_RIVALRY` weight; a named rival pair weighs `Faiths.RIVAL_MINDS`.
- `Shrine.Dedication` (nested in `Shrine`, `StringRepresentable`): `NONE` plus one value per patron with the same
  serialised name, `@Nullable Patron patron()`, `static Dedication of(Patron)`.

### Domains, offerings (item tags) and deeds
Each item belongs to one patron only. Tags are data, so tuning is a JSON edit.

| Patron | Domain (screen line) | `offerings/<key>.json` | Deeds (favour) and the hook |
|---|---|---|---|
| Hearthkeeper | Home, healing and the downed | bread, cake, pumpkin_pie, cookie, honey_bottle | Bring round a downed villager or folk: 2 (Powers `Downed.BROUGHT_ROUND_LISTENERS` via `PowersCompat.listen`, body is a `Villager` or `Folk`). Finish a treatment on someone else: 1 (`Treatment.TENDED_LISTENERS`). |
| Anvil-Father | Smithing and mining | iron_ingot, gold_ingot, copper_ingot, raw_iron, raw_gold, raw_copper, netherite_scrap | Forge a Masterwork: 4; a Perfect: 2 (`PowersCompat.onForged`; read the stack's `jaymospowers:forging_grade` component by id: `"master"` / `"perfect"`). Mine an ore below Y 0: 1 (`PlayerBlockBreakEvents.AFTER`, `ConventionalBlockTags.ORES`). |
| Deep Mother | The sea, whaling and sailing | `#minecraft:fishes`, nautilus_shell, prismarine_shard, jaymoskingdoms:ambergris, jaymoskingdoms:whale_bone | Kill a great whale: 4 (`AFTER_DEATH`, `Whaling.GREAT_WHALE`, killer is the player or their harpoon's owner). Five minutes aboard a boat or ship on an ocean biome: 1 (blessing tick). |
| Green Huntsman | The wilds, beasts and butchery | leather, rabbit_hide, rabbit_foot, feather, beef, porkchop, mutton, rabbit, chicken | Slay a beast of legend: 4 (`AFTER_DEATH`, `Monsters.legend(type)`). Slay a wild beast: 1 (`wild.WildBeast`, `Beasts.WILD_BOAR`, untamed wolf, polar bear). Finish a carcass on a meat hook: 1 (Kitchen `Hanging.addOnDone`). |
| Lantern Warden | The night and the dead | lantern, soul_lantern, candle, glow_ink_sac, bone | Slay an undead at night: 1 (`AFTER_DEATH`, `EntityTypeTags.UNDEAD`, `Vampires.night(clock)`). Slay a vampire: 4 (`Vampires.isVampire`). |
| Crowned Sun | Kings, law and war | sunflower, gold_nugget, glowstone_dust, golden_carrot | March home from a won siege with a war party: 4 (`WarParties.deliver`). Turn in a royal quest: 2 (`Quests.turnIn`, `q.royal()`). |
| Ash Whisperer | Thieves, fire and secrets | coal, charcoal, blaze_powder, gunpowder, ink_sac, fire_charge | Pick a lock: 1 (`Lockpicking`). A theft nobody saw: 1 (`Skills.unseenTheft`, after its own cooldown). A clean pickpocket: 1 (`Skills.pickedClean`). |
| The Truth | Law, certainty and the plain word | book, writable_book, paper (never a lore book: `LoreBooks.isLore` refused) | Expose a vampire or werewolf with a true accusation: 4 (`Accusations`). Down or slay a criminal: 1 (`AFTER_DEATH`, `Crooks.isCriminal` or `Outlaws.is`). |

---

## 4. The shrine (`Shrine.java`)

```java
public static final EnumProperty<Dedication> DEDICATION = EnumProperty.create("patron", Dedication.class);
// constructor: registerDefaultState(stateDefinition.any().setValue(DEDICATION, Dedication.NONE));
@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(DEDICATION); }
```
A broken shrine drops plain (loot table unchanged), so re-dedicating is "break it and set it again". No new recipe.

**Controls** (verified in 26.3 `ServerPlayerGameMode.useItemOn`: sneaking with something in hand skips the block, so
offerings can't be a sneak-use; sneaking with an **empty** hand still reaches `useWithoutItem`):

| Input | Result |
|---|---|
| Use, empty hand or holding anything that isn't an offering (hold) | Pray as now (5 s, once a day). |
| Use holding a patron's offering | One click, one offering (section 5). Further uses while the button stays held (gaps of at most `BREAK_TICKS`) do nothing, so a held button never feeds a stack in. Mid-prayer, an offering in hand just carries on praying. |
| Sneak + use, empty hand | Opens the patrons screen with this shrine's patron picked (clientbound `OpenShrine(pos)`); marks that patron known. No prayer. |

`useItemOn(stack, state, level, pos, player, hand, hit)`:
1. Client: `SUCCESS` if `Patrons.offeringFor(stack) != null` (tags are on the client), else `TRY_WITH_EMPTY_HAND`.
2. Server: if a prayer is running for this player at `pos` (`PRAYERS` entry within `BREAK_TICKS`) return
   `TRY_WITH_EMPTY_HAND`. If `OFFERING.get(uuid)` is this pos and within `BREAK_TICKS`, refresh it and return `SUCCESS`.
3. `Patron p = Patrons.offeringFor(stack)`; null (or a lore book) → `TRY_WITH_EMPTY_HAND`.
4. Undedicated → `level.setBlock(pos, state.setValue(DEDICATION, Dedication.of(p)), 3)`, take one, chime, `SOUL`/`END_ROD`
   particles, overlay `shrine.dedicated`, `Patrons.met(sp, p, pos)` (known; the patron's book is left on the shrine the
   first time that patron becomes known to this player).
5. Dedicated to `p` → `Patrons.offered(sp, p)` (it takes one only if the offering counts today).
6. Dedicated to another → overlay `shrine.theirs`, nothing taken, `SUCCESS`.
7. Put `OFFERING.put(uuid, new Offer(pos, now))`. `OFFERING` is a new static map, cleared with `PRAYERS`.

`useWithoutItem`: at the top, server side, `if (sp.isShiftKeyDown()) { Patrons.openShrine(sp, pos, state); return SUCCESS; }`.
`pray(...)` gains the state: after the heal, `Patrons.prayed(sp, state.getValue(DEDICATION).patron(), today)`.

---

## 5. Per-player state (`Patrons.java`)

### Attachment
```java
public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("patron"),
	b -> b.persistent(State.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(State.CODEC), AttachmentSyncPredicate.targetOnly()));
```
(Same shape as `Careers.CAREER`. Loaded at init by `Healing`'s debug line and `Patrons.register()`.)

```java
public record State(String patron, int favour, int tier, long swornDay, Streak streak, long lastFavourDay, long decayDay,
		Daily daily, long boonReady, List<String> known, List<Mark> marks, boolean seeded) {
	public static final State EMPTY = new State("", 0, 0, -1L, Streak.NONE, -1L, -1L, Daily.NONE, 0L, List.of(), List.of(), false);
}
public record Streak(String patron, int days, long lastDay) {}          // the swearing streak
public record Daily(long day, int deeds, boolean offered) {}           // today's deed favour and offering
/** (0.7.509) Reserved for part (b): turning away, a shrine defiled, a rival's rite. Nothing in (a) writes it. */
public record Mark(String kind, String patron, long day) {}
```
Every field `optionalFieldOf` with the `EMPTY` default, so later fields can be added without breaking saves.

### Tuning (top of `Patrons`)
```java
static final int SWEAR_DAYS = 3;
static final int FAVOUR_MAX = 100;
static final int[] TIER_AT = {10, 30, 55, 80};          // tier 1..4
static final int FAVOUR_SWORN = 12, FAVOUR_DEVOUT = 20;  // on swearing; a devout life story's start
static final int FAVOUR_PRAYER = 4, FAVOUR_OFFERING = 3;
public static final int DEED_SMALL = 1, DEED_MID = 2, DEED_GREAT = 4;
static final int DEEDS_DAILY_MOST = 8;                   // favour from deeds in one day, at most
static final int GRACE_DAYS = 2, DECAY_PER_DAY = 5, DEVOTION_PER_RANK = 1, DECAY_LEAST = 2;
static final long BOON_COOLDOWN = 3 * 24000L;            // three in-game days (game time)
static final int TICK_EVERY = 20, BOOK_SCAN_EVERY = 100, SEA_DEED_TICKS = 6000;
```
`today(server) = Math.floorDiv(server.overworld().getOverworldClockTime(), Shrine.DAY)`, the shrine's day.

### Swearing (`prayed(sp, @Nullable Patron shrine, today)`)
- Undedicated shrine: nothing more (the heal already happened).
- Unsworn, shrine of `p`: if `streak.patron == p.key && streak.lastDay == today - 1` then `days + 1`, else `1`. Overlay
  `shrine.streak` (`%1$s` name, `%2$s` days, `%3$s` `SWEAR_DAYS`). At `SWEAR_DAYS`: sworn (`patron = p.key`,
  `swornDay = today`, favour `FAVOUR_SWORN`, `lastFavourDay = decayDay = today`), overlay `shrine.sworn`, a
  `BEACON_ACTIVATE` and `TOTEM_OF_UNDYING` particles in `p.colour` (`DustParticleOptions`).
- Sworn to `p`, at `p`'s shrine: `gain(sp, FAVOUR_PRAYER)`.
- Sworn to another: overlay `shrine.strange` ("This shrine is %s's. You are sworn to %s."); no favour, no streak. (Part b
  decides what praying to a rival costs.)
- Sworn players can't swear to another in (a); leaving a patron is part (b).

### Offerings (`offered(sp, p)`)
Sworn to `p` and `!daily.offered` (daily rolled over when `daily.day != today`): take one, `gain(FAVOUR_OFFERING)`,
`daily.offered = true`, overlay `shrine.offered`. Already offered today: overlay `shrine.offered_today`, nothing taken.
Unsworn or sworn elsewhere: overlay `shrine.theirs_only` ("%s takes offerings only from those sworn to them."), nothing
taken.

### Deeds (`public static void deed(ServerPlayer, Patron, int amount, String kind)`)
Only if sworn to that patron. Counts toward `DEEDS_DAILY_MOST` (`daily.deeds`), then `gain`. `kind` is for (b)'s marks
and a debug log; no message (deeds are quiet; the tier change speaks).

### Gain, tier and decay
- `gain(sp, n)`: `favour = min(FAVOUR_MAX, favour + n)`, `lastFavourDay = today`, retier.
- Retier: `tier` = highest `i + 1` with `favour >= TIER_AT[i]`. Up: overlay `tier.up` (gold) and a chime; down: overlay
  `tier.down` (grey). Blessing attribute modifiers are re-applied on any tier change.
- Decay, once a day per online player (in `tick`, when `decayDay < today`):
  `days = max(0, today - max(decayDay, lastFavourDay + GRACE_DAYS))`;
  `perDay = max(DECAY_LEAST, DECAY_PER_DAY - DEVOTION_PER_RANK * Skills.rank(sp, Skills.Perk.DEVOTION))`;
  `favour = max(TIER_AT[0], favour - days * perDay)` (never below tier 1 in (a)); `decayDay = today`.
  Days a player is offline still pass (the overworld clock runs); they are counted on the next check.

### Known patrons
`known` holds the keys a player has met. Added: dedicating or sneak-using a shrine of that patron (`met`), praying at
one, being sworn, the devout start, carrying that patron's lore book (`LoreBooks.idOf(stack)` is
`jaymoskingdoms:patron_<key>`, scanned every `BOOK_SCAN_EVERY` ticks), and at the story start (a human knows The
Truth; a non-human knows their heritage's patron, `Faiths.lean(kind)`). The first time a patron becomes known through a
shrine, its book (`LoreBooks.book(id)`, a new package-private wrapper around the private `dataBook`) is popped on top of
the shrine.

### The devout start (`storyStart(sp)`, also on `JOIN` for old players)
Once (`seeded`): mark the story-start known patron. If `PlayerTemperaments.of(sp) == DEVOUT` and unsworn: sworn to
`Faiths.human(Heritage.of(sp)) ? TRUTH : Faiths.lean(Heritage.of(sp))` at `FAVOUR_DEVOUT`, `swornDay = today`, and that
patron's book goes in their pack. Devout answers today: raised by the wizard, the Tideborn keepers or the Dawnborn
sunpriests, or a childhood of bells (`PlayerTemperaments.RAISED/CHILD`). No message beyond the temperament line the
story already shows.

### Payloads (registered in `Patrons.register()`)
- Clientbound `OpenShrine(BlockPos pos)`, `Type<>(JaymosKingdoms.id("patron_shrine"))`, `BlockPos.STREAM_CODEC`.
- Serverbound `CallBoon()`, `Type<>(JaymosKingdoms.id("patron_boon"))`, `StreamCodec.unit(INSTANCE)`. The server checks:
  sworn, `tier == 4`, `gameTime >= boonReady`, alive, not downed (`Downed.isDowned`), not spectator. Then
  `Blessings.boon(sp, patron)`, `boonReady = gameTime + BOON_COOLDOWN`, overlay `boon.called`.

### For Powers
```java
/** (0.7.509) Read by Jaymo's Powers by reflection (MedicineSkill): a blessing's strength for this player, 0 if they lack it. */
public static float blessing(Player player, String key)   // "gentle_hands" -> Blessings.GENTLE_HANDS at Hearthkeeper tier 2+
public static boolean blessed(Player player, Patron p, int tier)
public static @Nullable Patron of(Player player)          // sworn patron or null (client side too: synced)
```

### `register()`
Payloads; `ServerTickEvents.END_SERVER_TICK` → `tick` (every `TICK_EVERY`: daily rollover and decay, the book scan, the
sea deed, then `Blessings.tick`); `ServerPlayConnectionEvents.JOIN` → `storyStart`; `ServerLivingEntityEvents.AFTER_DEATH`
→ the kill deeds; `PlayerBlockBreakEvents.AFTER` → deep ore deed and Rich Seam; `PowersCompat.listen(... "Downed",
"BROUGHT_ROUND_LISTENERS", ...)` and `("...Treatment", "TENDED_LISTENERS", ...)`; `PowersCompat.onForged(...)`;
`PowersCompat.zoneBonus(Blessings::zoneBonus)`; `ServerLifecycleEvents.SERVER_STOPPED` clears the maps.

---

## 6. Blessings and boons (`Blessings.java`)

Tiers 1 to 3 are passive; tier 4 is the boon (cooldown `Patrons.BOON_COOLDOWN`, three in-game days). Each tier keeps the
ones below. Lang: `blessing.jaymoskingdoms.<key>.<1..4>` and `.desc`.

| Patron | T1 | T2 | T3 | T4 boon (boon key menu, left bracket) |
|---|---|---|---|---|
| Hearthkeeper | **Hearth's Warmth**: not hurt for `HEARTH_CALM_TICKS`, every `HEARTH_MEND_TICKS` every part heals `HEARTH_MEND` (`PowersCompat.healEveryPart`, `heal()` without Powers). | **Gentle Hands**: revives and treatments take `GENTLE_HANDS` less time (Powers `MedicineSkill.ticks`). | **Back on Their Feet**: a villager or folk you bring round gets Regeneration II for `TENDED_REGEN_TICKS` and Absorption I for `TENDED_ABSORB_TICKS` (BROUGHT_ROUND listener). | **Hearthfire**: you, players, tamed animals and villagers within `HEARTHFIRE_RANGE` heal `HEARTHFIRE_HEAL` on every part, festering eased (`PowersCompat.easeFever`), and the downed among them are asked up (`Downed.REVIVE_TAG`). |
| Anvil-Father | **Sound Steel**: a point of wear on what's in your hands is spared `SOUND_STEEL_SPARE` of the time (`ItemStackWearMixin`). | **True Strike**: the Smithing Anvil's perfect zone `TRUE_STRIKE_ZONE` wider (`Forging.ZONE_BONUS`). | **Rich Seam**: an ore you mine drops again `RICH_SEAM_CHANCE` of the time (`PlayerBlockBreakEvents.AFTER`, `ConventionalBlockTags.ORES`, survival, no Silk Touch: `Block.getDrops(state, level, pos, blockEntity, player, tool)` popped once more). | **Father's Fire**: for `FATHERS_FIRE_TICKS`, Haste II, every ore drops again, and nothing in your hands wears. |
| Deep Mother | **Mother's Breath**: `Attributes.OXYGEN_BONUS` +`MOTHERS_BREATH` (modifier id `"patron_breath"`, via `Skills.modifier(sp, Attributes.OXYGEN_BONUS, "patron_breath", amount, ADD_VALUE)`, 0 when unblessed). | **Calm Water**: while you're in the water or aboard a boat or ship, no storm bolt falls within `CALM_RANGE` of you (`Storms.strike`). | **Whale Road**: aboard on a deep ocean, every `WHALE_ROAD_EVERY` with no great whale within `WHALE_ROAD_CLEAR`, `WHALE_ROAD_CHANCE` that a pod of 1 to 2 surfaces `WHALE_ROAD_MIN`-`WHALE_ROAD_MAX` blocks off (`Whaling.GREAT_WHALE.spawn(level, pos, EntitySpawnReason.NATURAL)` in water). | **Mother's Embrace**: Water Breathing, Dolphin's Grace and Conduit Power for `EMBRACE_TICKS`. |
| Green Huntsman | **Soft Tread**: quieter steps: `Skills.sneakFactor` less `SOFT_TREAD` (mobs' sight in `Senses` and theft witnesses in `Theft`, like the Sylvan's). | **Clean Cuts**: a carcass you cut on a meat hook gives its butcher-table extras `CLEAN_CUTS_EXTRA` more time (Kitchen `Hanging.addExtraCuts`). | **Hunter's Eye**: a slay quest's beast of legend comes for you from `HUNTERS_EYE_RANGE` times as far (`Quests`), and legends within `HUNTERS_EYE_SENSE` show you a faint mark (owner-only particles). | **Wild Hunt**: for `WILD_HUNT_TICKS`, Speed I, your blows on beasts `WILD_HUNT_DAMAGE` harder, and your steps silent (Soft Tread to the floor). |
| Lantern Warden | **Warden's Eye**: see in the dark: Night Vision (ambient, hidden) while the light at your eyes is at most `WARDENS_EYE_LIGHT`, refreshed to `NIGHT_VISION_TICKS` below `NIGHT_VISION_REFRESH` so it never flickers. | **Grave Blows**: undead and vampires take `GRAVE_BLOWS` more from your blows (`Blessings.dealt`). | **Lamp Against the Night**: the Nightfall plague takes you as it takes a Plague Doctor: half as long, never past I, half its pricks (`Nightfall.plagueDoctor`). | **Dawnlight**: undead and vampires (not players) within `DAWNLIGHT_RANGE` burn `DAWNLIGHT_BURN` seconds and take `DAWNLIGHT_DAMAGE` (indirect magic, yours). |
| Crowned Sun | **Sun's Favour**: good name with any realm (not a haven) is won `SUN_REP` faster, and guards halt you `SUN_HALT` times less often (`Approaches`). | **Valour's Light**: Valor feats are worth `SUN_VALOR` more (`Valor.featKeyed`). | **Banner-Bearer**: a war party you call up musters `SUN_PARTY_EXTRA` more soldiers (`WarParties.start`'s `extra`). | **Sun's Muster**: you, your groupmates (`Groups.together`) and your realm's folk within `SUN_MUSTER_RANGE` get Strength I and Resistance I for `SUN_MUSTER_TICKS`. |
| Ash Whisperer | **Quick Fingers**: a pickpocket's hold `QUICK_FINGERS` shorter (`Skills.holdTicks`) and a lock's sweet spot that much wider (`Lockpicking`). | **Ash-Skinned**: fire hurts you `ASH_SKIN` less (`DamageTypeTags.IS_FIRE`, `Blessings.dealt`). | **Short Memories**: guards hunt you for `SHORT_MEMORY` of the time after a crime, outlaws included (`Theft` wanted time). | **Smoke and Whispers**: a burst of smoke, Invisibility for `SMOKE_TICKS`, and nobody bears witness to what you do meanwhile (`Theft.witnesses`). |
| The Truth | **Plain Sight**: the cursed among townsfolk (vampire villagers and folk, `Vampires.isVampire`; cursed werewolves, `Lycans.cursed`) show you a mark within `PLAIN_SIGHT_RANGE` and in sight, every `PLAIN_SIGHT_EVERY` ticks (owner-only `SOUL` particles: `level.sendParticles(sp, ParticleTypes.SOUL, true, false, ...)`). Players are never shown. | **Of the Flock**: human townsfolk of the Truth, clerics too, charge you `FLOCK_PRICE` less (`Faiths.prices`) and warm `FLOCK_HEARTS` more heart on a good chat (`McaDialogueMixin`). | **Iron Certainty**: spells and powers harm you `CERTAINTY` less (`Will.magical`, stacks with Grounded), and you are believed: a true accusation wins `BELIEVED_GAIN` times the good name, a false one costs `BELIEVED_LOSS` of it and the priests are deaf half as long; guards of a Truth realm halt you `BELIEVED_HALT` times less often and search you `BELIEVED_SEARCH` less. | **Judgement**: every harmful effect on you is lifted (Atrophy, poison, wither, weakness and the rest), and every vampire and cursed werewolf within `JUDGEMENT_RANGE` is shown for what it is: players exposed till dawn as by holy water (`Accusations.expose`), vampire folk exposed for the night (`Vampires.expose`), all of them Glowing for `JUDGEMENT_GLOW` ticks. |

### Tuning (top of `Blessings`)
```java
static final int HEARTH_CALM_TICKS = 200, HEARTH_MEND_TICKS = 200; static final float HEARTH_MEND = 0.5F;
static final float GENTLE_HANDS = 0.2F;
static final int TENDED_REGEN_TICKS = 200, TENDED_ABSORB_TICKS = 1200;
static final double HEARTHFIRE_RANGE = 12.0; static final float HEARTHFIRE_HEAL = 6.0F;
static final float SOUND_STEEL_SPARE = 0.25F; static final double TRUE_STRIKE_ZONE = 0.75; static final float RICH_SEAM_CHANCE = 0.15F;
static final int FATHERS_FIRE_TICKS = 3600;
static final double MOTHERS_BREATH = 3.0, CALM_RANGE = 32.0;
static final int WHALE_ROAD_EVERY = 3600, WHALE_ROAD_MIN = 40, WHALE_ROAD_MAX = 64; static final double WHALE_ROAD_CLEAR = 128.0;
static final float WHALE_ROAD_CHANCE = 0.2F; static final int EMBRACE_TICKS = 6000;
static final double SOFT_TREAD = 0.1; static final int CLEAN_CUTS_EXTRA = 1;
static final double HUNTERS_EYE_RANGE = 1.5, HUNTERS_EYE_SENSE = 64.0;
static final int WILD_HUNT_TICKS = 2400; static final float WILD_HUNT_DAMAGE = 0.3F;
static final int WARDENS_EYE_LIGHT = 4, NIGHT_VISION_TICKS = 400, NIGHT_VISION_REFRESH = 220;
static final float GRAVE_BLOWS = 0.25F; static final double DAWNLIGHT_RANGE = 16.0; static final int DAWNLIGHT_BURN = 8;
static final float DAWNLIGHT_DAMAGE = 8.0F;
static final float SUN_REP = 0.15F, SUN_HALT = 2.0F, SUN_VALOR = 0.2F; static final int SUN_PARTY_EXTRA = 2;
static final double SUN_MUSTER_RANGE = 16.0; static final int SUN_MUSTER_TICKS = 2400;
static final float QUICK_FINGERS = 0.15F, ASH_SKIN = 0.35F, SHORT_MEMORY = 0.5F; static final int SMOKE_TICKS = 600;
static final double PLAIN_SIGHT_RANGE = 12.0; static final int PLAIN_SIGHT_EVERY = 40;
static final float FLOCK_PRICE = 0.05F; static final int FLOCK_HEARTS = 1;
static final float CERTAINTY = 0.2F, BELIEVED_GAIN = 2.0F, BELIEVED_LOSS = 0.5F, BELIEVED_HALT = 1.5F; static final int BELIEVED_SEARCH = 1;
static final double JUDGEMENT_RANGE = 24.0; static final int JUDGEMENT_GLOW = 600;
```

### Functions other classes call
`dealt(target, source, amount)` (Grave Blows, Ash-Skinned, Iron Certainty, Wild Hunt; attacker checks as `Heritage.dealt`:
`by == source.getDirectEntity()` for melee), `sneak(player)`, `pickHold(player)`, `lockWidth(player)`,
`wear(stack, player, wear)` (only for a stack in the player's main or off hand; armour is untouched),
`zoneBonus(smith)`, `reputation(kingdom, player, by)`, `haltGap(player, f, ticks)`, `searchEase(player, f)`,
`valorGrowth(holder)`, `partyExtra(player)`, `huntRange(player, base)`, `wantedTicks(player, ticks)`, `unseen(player)`,
`accuseGain/accuseLoss/deafTicks(...)`, `flockHearts(villager, player, gained)`, `calmsSea(level, pos)` (public),
`tick(server)` (Hearth's Warmth, Mother's Breath modifier, Warden's Eye, Plain Sight, Hunter's Eye marks, Whale Road),
`boon(player, patron)`. Timed boon states (Father's Fire, Wild Hunt, Smoke and Whispers) are a `Map<UUID, Long>` of
end times, not saved, cleared on stop and disconnect.

---

## 7. Devotion (`Skills.Perk`)

Appended **last**, after `QUIET_WORD`, so no ordinal moves:
```java
// (0.7.509) the patrons' favour slips more slowly (Patrons); last, so no ordinal moves
DEVOTION(Pillar.SURVIVAL, 3, 1, 3, 0, 2);
```
Survival's first page, tier 0, column 2 (free: the page's roots are Meditation in column 0 and Forager in column 1),
needs Survival level 3, three ranks of 1 point. Each rank takes `Patrons.DEVOTION_PER_RANK` (1) off the daily slip
(5 → 4 → 3 → 2, never below `DECAY_LEAST`).

---

## 8. Screens and controls

### Patrons screen (`PatronScreen`, `extends Screen`, not a pause screen)
Opened by sneak + use on a shrine with an empty hand (server sends `OpenShrine`), and by the new **Patrons** button on
the skill screen (K) and on the boon menu. Reads only the synced `Patrons.STATE` and the item tags, so it needs no data
payload. Layout copied from `TierScreen` (`MAX_W 400`, `MAX_H 236`, `LIST_W 112`, `LIST_ROW 19`, `HEADER 34`, `FOOTER 24`,
`Ui.PANEL`/`Ui.ROW`/`Ui.GOLD`).
- Header: title `gui.jaymoskingdoms.patrons.title`; under it your standing: `.sworn` (name in its colour, day sworn,
  tier name) or `.unsworn`, and while a streak is running `.streak`.
- Left list, eight rows: a 2 px stripe in the patron's colour and the name; an unknown patron's row is dim and reads
  `patron.jaymoskingdoms.unknown`. Your patron's row has the gold stripe (`Ui.GOLD`). Opened from a shrine, its patron
  is picked.
- Right, for a known patron: name; domain line; `.offerings` and up to 8 offering item icons
  (`graphics.item(stack, x, y)`, from `BuiltInRegistries.ITEM.getTagOrEmpty(p.offerings)`); `.deeds` and its deed lines;
  `.rivals` ("Cool to: ..."); for your own patron a favour bar (0 to 100, ticks at 10/30/55/80, filled in its colour,
  `.favour`), then the four blessings, each with its name and desc, lit when your tier reaches it and dim otherwise;
  the boon's line shows `.boon.ready` or `.boon.wait`; and `.decay`. For an unknown patron: `.unknown_hint` only.
- Footer: Done.

### Boon menu (`BoonScreen`)
Opened by the **left square bracket key** (the boon key) while on the ground with no screen open and sworn. A small centred panel: your
patron's name in its colour, the boon's name and desc, its state (`.boon.ready`, `.boon.wait` with days and hours,
`.boon.locked` below tier 4), a **Call** button (active only when tier 4 and ready; sends `CallBoon`, then closes), and
**Patrons...** (opens `PatronScreen`).

### Keys (`PatronKeys`)
`key.jaymoskingdoms.boon`, default `InputConstants.KEY_LBRACKET`, category `KeyMapping.Category.register(JaymosKingdoms.id("patrons"))`.
26.3's `KeyMapping.MAP` holds a list per key, so Powers' Z (`key.jaymospowers.mode`) still fires alongside (verified in
`KeyMapping.java`); ours is the left square bracket, alone, on the ground, so Z (and sneak + Z) stay with Powers and the full stop stays with Shoulder Surfing's swap shoulder. Rebindable in Controls.

---

## 9. Lang (`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`)

Patron names and domains:
```
"patron.jaymoskingdoms.hearthkeeper": "The Hearthkeeper",
"patron.jaymoskingdoms.hearthkeeper.domain": "Home, healing and the downed",
"patron.jaymoskingdoms.anvil_father": "The Anvil-Father",
"patron.jaymoskingdoms.anvil_father.domain": "Smithing and mining",
"patron.jaymoskingdoms.deep_mother": "The Deep Mother",
"patron.jaymoskingdoms.deep_mother.domain": "The sea, whaling and sailing",
"patron.jaymoskingdoms.green_huntsman": "The Green Huntsman",
"patron.jaymoskingdoms.green_huntsman.domain": "The wilds, beasts and butchery",
"patron.jaymoskingdoms.lantern_warden": "The Lantern Warden",
"patron.jaymoskingdoms.lantern_warden.domain": "The night and the dead",
"patron.jaymoskingdoms.crowned_sun": "The Crowned Sun",
"patron.jaymoskingdoms.crowned_sun.domain": "Kings, law and war",
"patron.jaymoskingdoms.ash_whisperer": "The Ash Whisperer",
"patron.jaymoskingdoms.ash_whisperer.domain": "Thieves, fire and secrets",
"patron.jaymoskingdoms.truth": "The Truth",
"patron.jaymoskingdoms.truth.domain": "Law, certainty and the plain word",
"patron.jaymoskingdoms.unknown": "A patron you haven't heard of",
"patron.jaymoskingdoms.tier.1": "Sworn",
"patron.jaymoskingdoms.tier.2": "Devoted",
"patron.jaymoskingdoms.tier.3": "Trusted",
"patron.jaymoskingdoms.tier.4": "Chosen",
```
Deeds (one line each, shown under `.deeds`):
```
"patron.jaymoskingdoms.hearthkeeper.deed.1": "Bring a downed villager round",
"patron.jaymoskingdoms.hearthkeeper.deed.2": "Treat someone's wounds",
"patron.jaymoskingdoms.anvil_father.deed.1": "Forge a Masterwork (or a Perfect piece)",
"patron.jaymoskingdoms.anvil_father.deed.2": "Mine ore in the deep",
"patron.jaymoskingdoms.deep_mother.deed.1": "Kill a great whale",
"patron.jaymoskingdoms.deep_mother.deed.2": "Spend time at sea",
"patron.jaymoskingdoms.green_huntsman.deed.1": "Slay a beast of legend",
"patron.jaymoskingdoms.green_huntsman.deed.2": "Hunt wild beasts and dress their carcasses",
"patron.jaymoskingdoms.lantern_warden.deed.1": "Slay the undead by night",
"patron.jaymoskingdoms.lantern_warden.deed.2": "Slay a vampire",
"patron.jaymoskingdoms.crowned_sun.deed.1": "Come home from a won siege",
"patron.jaymoskingdoms.crowned_sun.deed.2": "Serve a king or a lord",
"patron.jaymoskingdoms.ash_whisperer.deed.1": "Pick locks and pockets",
"patron.jaymoskingdoms.ash_whisperer.deed.2": "Take what nobody sees you take",
"patron.jaymoskingdoms.truth.deed.1": "Unmask a vampire or a werewolf before a priest",
"patron.jaymoskingdoms.truth.deed.2": "Bring down a criminal",
```
Blessings (`.1`-`.3` passive, `.4` the boon):
```
"blessing.jaymoskingdoms.hearthkeeper.1": "Hearth's Warmth",
"blessing.jaymoskingdoms.hearthkeeper.1.desc": "Out of a fight, your wounds close a little every few seconds.",
"blessing.jaymoskingdoms.hearthkeeper.2": "Gentle Hands",
"blessing.jaymoskingdoms.hearthkeeper.2.desc": "Your revives and treatments take a fifth less time.",
"blessing.jaymoskingdoms.hearthkeeper.3": "Back on Their Feet",
"blessing.jaymoskingdoms.hearthkeeper.3.desc": "A villager you bring round gets up stronger: healing and a little toughness for a while.",
"blessing.jaymoskingdoms.hearthkeeper.4": "Hearthfire",
"blessing.jaymoskingdoms.hearthkeeper.4.desc": "Heals everyone friendly around you and asks the downed to rise.",
"blessing.jaymoskingdoms.anvil_father.1": "Sound Steel",
"blessing.jaymoskingdoms.anvil_father.1.desc": "What you hold wears more slowly.",
"blessing.jaymoskingdoms.anvil_father.2": "True Strike",
"blessing.jaymoskingdoms.anvil_father.2.desc": "The anvil's perfect mark is wider for you.",
"blessing.jaymoskingdoms.anvil_father.3": "Rich Seam",
"blessing.jaymoskingdoms.anvil_father.3.desc": "Now and then an ore you mine gives twice.",
"blessing.jaymoskingdoms.anvil_father.4": "Father's Fire",
"blessing.jaymoskingdoms.anvil_father.4.desc": "For three minutes you dig fast, every ore gives twice and nothing you hold wears.",
"blessing.jaymoskingdoms.deep_mother.1": "Mother's Breath",
"blessing.jaymoskingdoms.deep_mother.1.desc": "Your breath lasts far longer under water.",
"blessing.jaymoskingdoms.deep_mother.2": "Calm Water",
"blessing.jaymoskingdoms.deep_mother.2.desc": "At sea, the storms' lightning keeps away from you.",
"blessing.jaymoskingdoms.deep_mother.3": "Whale Road",
"blessing.jaymoskingdoms.deep_mother.3.desc": "On the deep ocean, great whales come up near your boat more often.",
"blessing.jaymoskingdoms.deep_mother.4": "Mother's Embrace",
"blessing.jaymoskingdoms.deep_mother.4.desc": "For five minutes you breathe water and swim like the sea's own.",
"blessing.jaymoskingdoms.green_huntsman.1": "Soft Tread",
"blessing.jaymoskingdoms.green_huntsman.1.desc": "Your steps are quieter: you are harder to see sneaking.",
"blessing.jaymoskingdoms.green_huntsman.2": "Clean Cuts",
"blessing.jaymoskingdoms.green_huntsman.2.desc": "A carcass you dress on a meat hook gives more.",
"blessing.jaymoskingdoms.green_huntsman.3": "Hunter's Eye",
"blessing.jaymoskingdoms.green_huntsman.3.desc": "Beasts of legend are easier to find and to draw out.",
"blessing.jaymoskingdoms.green_huntsman.4": "Wild Hunt",
"blessing.jaymoskingdoms.green_huntsman.4.desc": "For two minutes you run fast, strike beasts harder and make no sound.",
"blessing.jaymoskingdoms.lantern_warden.1": "Warden's Eye",
"blessing.jaymoskingdoms.lantern_warden.1.desc": "You see in the dark.",
"blessing.jaymoskingdoms.lantern_warden.2": "Grave Blows",
"blessing.jaymoskingdoms.lantern_warden.2.desc": "The undead and vampires take more from your blows.",
"blessing.jaymoskingdoms.lantern_warden.3": "Lamp Against the Night",
"blessing.jaymoskingdoms.lantern_warden.3.desc": "The Nightfall plague takes a weaker hold on you.",
"blessing.jaymoskingdoms.lantern_warden.4": "Dawnlight",
"blessing.jaymoskingdoms.lantern_warden.4.desc": "The undead and vampires around you burn.",
"blessing.jaymoskingdoms.crowned_sun.1": "Sun's Favour",
"blessing.jaymoskingdoms.crowned_sun.1.desc": "Lords think well of you sooner, and guards stop you less.",
"blessing.jaymoskingdoms.crowned_sun.2": "Valour's Light",
"blessing.jaymoskingdoms.crowned_sun.2.desc": "Your deeds earn more Valor.",
"blessing.jaymoskingdoms.crowned_sun.3": "Banner-Bearer",
"blessing.jaymoskingdoms.crowned_sun.3.desc": "A war party you call up musters more soldiers.",
"blessing.jaymoskingdoms.crowned_sun.4": "Sun's Muster",
"blessing.jaymoskingdoms.crowned_sun.4.desc": "You and those who fight beside you grow strong and hard to hurt for two minutes.",
"blessing.jaymoskingdoms.ash_whisperer.1": "Quick Fingers",
"blessing.jaymoskingdoms.ash_whisperer.1.desc": "Pockets and locks come easier.",
"blessing.jaymoskingdoms.ash_whisperer.2": "Ash-Skinned",
"blessing.jaymoskingdoms.ash_whisperer.2.desc": "Fire hurts you less.",
"blessing.jaymoskingdoms.ash_whisperer.3": "Short Memories",
"blessing.jaymoskingdoms.ash_whisperer.3.desc": "The guards give up the hunt for you sooner.",
"blessing.jaymoskingdoms.ash_whisperer.4": "Smoke and Whispers",
"blessing.jaymoskingdoms.ash_whisperer.4.desc": "You vanish in smoke, and for half a minute nobody bears witness to what you do.",
"blessing.jaymoskingdoms.truth.1": "Plain Sight",
"blessing.jaymoskingdoms.truth.1.desc": "The cursed among townsfolk can't hide from your eyes.",
"blessing.jaymoskingdoms.truth.2": "Of the Flock",
"blessing.jaymoskingdoms.truth.2.desc": "Townsfolk and clerics of the Truth charge you less and warm to you sooner.",
"blessing.jaymoskingdoms.truth.3": "Iron Certainty",
"blessing.jaymoskingdoms.truth.3.desc": "Spells bite you less, and guards and priests believe your word.",
"blessing.jaymoskingdoms.truth.4": "Judgement",
"blessing.jaymoskingdoms.truth.4.desc": "Every curse on you is lifted, and the cursed around you are shown for what they are.",
```
Screens, key, messages, perk, hints:
```
"gui.jaymoskingdoms.patrons.title": "Patrons",
"gui.jaymoskingdoms.patrons.button": "Patrons",
"gui.jaymoskingdoms.patrons.sworn": "Sworn to %1$s since day %2$s: %3$s",
"gui.jaymoskingdoms.patrons.unsworn": "Sworn to no patron",
"gui.jaymoskingdoms.patrons.streak": "Prayed at %1$s's shrine %2$s of %3$s days running",
"gui.jaymoskingdoms.patrons.offerings": "Offerings",
"gui.jaymoskingdoms.patrons.deeds": "Their kind of deeds",
"gui.jaymoskingdoms.patrons.rivals": "Cool to the followers of: %s",
"gui.jaymoskingdoms.patrons.rivals.seven": "the seven",
"gui.jaymoskingdoms.patrons.rivals.dark": "the vampires' dark",
"gui.jaymoskingdoms.patrons.favour": "Favour %1$s / %2$s",
"gui.jaymoskingdoms.patrons.decay": "Favour slips after %s days without prayer, offering or deed.",
"gui.jaymoskingdoms.patrons.unknown_hint": "Shrines, books and clerics would tell you more.",
"gui.jaymoskingdoms.patrons.boon": "Boon",
"gui.jaymoskingdoms.patrons.boon.call": "Call",
"gui.jaymoskingdoms.patrons.boon.ready": "Ready",
"gui.jaymoskingdoms.patrons.boon.wait": "Ready in %1$s days %2$s hours",
"gui.jaymoskingdoms.patrons.boon.locked": "Reach the fourth tier",
"key.category.jaymoskingdoms.patrons": "Patrons",
"key.jaymoskingdoms.boon": "Patron's boon (with sneak)",
"message.jaymoskingdoms.shrine.dedicated": "The shrine is %s's now.",
"message.jaymoskingdoms.shrine.theirs": "This shrine is %s's.",
"message.jaymoskingdoms.shrine.theirs_only": "%s takes offerings only from those sworn to them.",
"message.jaymoskingdoms.shrine.strange": "This shrine is %1$s's. You are sworn to %2$s.",
"message.jaymoskingdoms.shrine.offered": "%s takes your offering.",
"message.jaymoskingdoms.shrine.offered_today": "You have made an offering today.",
"message.jaymoskingdoms.shrine.streak": "%1$s hears you. (%2$s of %3$s days)",
"message.jaymoskingdoms.shrine.sworn": "You are sworn to %s.",
"message.jaymoskingdoms.patron.tier.up": "%1$s's favour grows: %2$s.",
"message.jaymoskingdoms.patron.tier.down": "%1$s's favour slips: %2$s.",
"message.jaymoskingdoms.boon.called": "%s answers.",
"message.jaymoskingdoms.boon.waiting": "Your patron is silent for now.",
"skill.jaymoskingdoms.perk.devotion": "Devotion",
"skill.jaymoskingdoms.perk.devotion.desc": "Your patron's favour slips more slowly: a point less a day for each rank.",
"gui.jaymoskingdoms.story.hint.86": "Set a patron's offering on a plain shrine to give it to them. Pray there three days running and you are sworn.",
"gui.jaymoskingdoms.story.hint.87": "Favour slips if you leave your patron a few days. Pray, bring fitting offerings and do their kind of deeds.",
```
Faith prompt lines for MCA's chat AI (`Faiths.prompt`):
```
"prompt.jaymoskingdoms.faith.own": "You hold to %s.",
"prompt.jaymoskingdoms.faith.same": "This traveller is sworn to %s, as you are, and you take them for one of your own.",
"prompt.jaymoskingdoms.faith.rival": "This traveller is sworn to %s, a faith you distrust; you are cooler to them for it.",
```
(`Faiths.prompt` resolves these with `Component.translatable(...).getString()`, like `Prejudice.prompt`.)

---

## 10. Faiths of villagers and folk (`Faiths.java`)

```java
/** On a villager or folk: their faith (a Patron key). Drawn once, kept. Not synced. */
static final AttachmentType<String> FAITH = AttachmentRegistry.create(JaymosKingdoms.id("faith"), b -> b.persistent(Codec.STRING));
static final float TRUTH_SHARE_HUMAN = 0.94F, LEAN_SHARE = 0.7F, CONVERT_SHARE = 0.05F;
static final float FAITH_RIVALRY = 0.75F, RIVAL_MINDS = 1.0F, FAITH_CREED_HARSH = 1.5F;
static final float PRICE_SHARE = 0.06F, HEARTS_CUT = 0.15F, REP_GAIN_CUT = 0.08F, REP_LOSS_ADD = 0.08F; // per degree, as Prejudice
static final int NEIGHBOUR_STEP = 8, GUARD_SEARCH_W = 1; static final float GUARD_HALT_SOONER = 0.5F;
```
- **Who has one**: `Villager` (MCA's included) and `Folk` with `VillagerHeritage.humanFolk(role)`.
- **`of(e)`**: stored key, else drawn now on the server and stored (as `Temperaments.of`). A priest
  (`Accusations.priest(e)`) is always The Truth, re-set if it was anything else (a villager can take the cleric's trade
  later). Also drawn on `ServerEntityEvents.ENTITY_LOAD` (registered after `VillagerHeritage`'s, so blood comes first).
- **The draw**:
  - A realm's folk whose realm creed is Faith (`GreatWars.creed(server, f) == Creed.FAITH`): The Truth.
  - Human blood (`human(kind)`: `kind == null` or `Prejudice.degree(kind) == 0`, except Verdant and the broods): The
    Truth `TRUTH_SHARE_HUMAN`; otherwise a fitting one of the seven by trade: fisherman → Deep Mother; armorer,
    toolsmith, weaponsmith, miner → Anvil-Father; butcher, leatherworker, shepherd, woodcutter → Green Huntsman;
    farmer → Hearthkeeper; guard, lord, king → Crowned Sun; anyone else a random one of the seven.
  - Non-human blood: `lean(kind)` `LEAN_SHARE`, any of the seven up to `1 - CONVERT_SHARE`, else The Truth.
  - `lean`: Tideborn → Deep Mother; Stoneborn, Bio-built, Orc → Anvil-Father; Sylvan, Beastfolk → Green Huntsman;
    Verdant → Hearthkeeper; Warrior-born → Crowned Sun; Hollow → Lantern Warden; Goblin → Ash Whisperer. Human bloods
    return Hearthkeeper (only used for a rare human of the seven without a trade lean).
- `forget(villager)` removes it (a new blood draws again); `inherit(child, mother, father)`: the mother's, else the
  father's.
- **`minds(who, whom)`** (0 or more, in Prejudice's degree units), only for a villager/folk `who` with a faith and a
  sworn player `whom` (or a villager `whom` for neighbours), and 0 for a masked player (`Prejudice.masked`):
  - Truth vs any of the seven, either way: `FAITH_RIVALRY`, times `FAITH_CREED_HARSH` if `who`'s realm holds the Faith
    creed.
  - A named rival pair (Crowned Sun / Ash Whisperer, Deep Mother / Anvil-Father): `RIVAL_MINDS`.
  - The vampires' dark: a Lantern Warden villager minds an exposed vampire player (`Vampires.exposed` or
    `Accusations.exposed`), and a vampire villager (`Vampires.isVampire(who)`) minds a Lantern Warden player, both
    `RIVAL_MINDS`.
  - Same faith or none: 0. Stacks with `Prejudice.minds` because each cost point adds both.
- **Cost points**, each a small mirror of Prejudice's: `prices` (dearer by `PRICE_SHARE` per degree, at least 1; then
  `Blessings` Of the Flock's discount for a human Truth villager or a cleric: `-max(1, floor(cost * FLOCK_PRICE))`),
  `hearts` (taken back, `HEARTS_CUT` per degree), `guard` (`GUARD_SEARCH_W` per degree), `haltGap` (`1 + GUARD_HALT_SOONER *
  m`), `neighbourLean` (`NEIGHBOUR_STEP * max(minds both ways)`), `prompt`.
- **`realmFaith(server, f)`**: `null` for a barbarian camp; `SEVEN` (any of the seven) for a haven (`Havens.is`); The
  Truth for every other realm, fiefdom and village. A record `RealmFaith(@Nullable Patron patron, boolean seven, float
  weight)` with weight `FAITH_CREED_HARSH` for a Faith-creed realm, else 1.
- **`reputation(kingdom, player, by)`**: a sworn player whose faith clashes with the realm's (sworn to the seven in a
  Truth realm, or to The Truth in a haven): gains cut `REP_GAIN_CUT * FAITH_RIVALRY * weight`, losses
  `REP_LOSS_ADD * ...` more. Skipped for a hidden player (`Identities.hidden`), as in Prejudice.

Kept small on purpose (Andy: a small reputation/opinion modifier in (a)). The great-wars tension is part (b): it reads
`realmFaith` (section 12).

---

## 11. Assets and textures

- `blockstates/shrine.json`:
  ```json
  {"variants": {
    "patron=none": {"model": "jaymoskingdoms:block/shrine"},
    "patron=hearthkeeper": {"model": "jaymoskingdoms:block/shrine_hearthkeeper"},
    "patron=anvil_father": {"model": "jaymoskingdoms:block/shrine_anvil_father"},
    "patron=deep_mother": {"model": "jaymoskingdoms:block/shrine_deep_mother"},
    "patron=green_huntsman": {"model": "jaymoskingdoms:block/shrine_green_huntsman"},
    "patron=lantern_warden": {"model": "jaymoskingdoms:block/shrine_lantern_warden"},
    "patron=crowned_sun": {"model": "jaymoskingdoms:block/shrine_crowned_sun"},
    "patron=ash_whisperer": {"model": "jaymoskingdoms:block/shrine_ash_whisperer"},
    "patron=truth": {"model": "jaymoskingdoms:block/shrine_truth"}}}
  ```
- `models/block/shrine_dedicated.json`: a copy of `shrine.json` (same nine elements, `#shrine`) plus one element, a
  glyph tile on the altar top between the three candles: `"from": [6.5, 11, 6.5], "to": [9.5, 11.25, 9.5]`, `up`
  face `#glyph` uv `[0, 0, 16, 16]`, the four sides `#shrine`. Textures `"particle"`, `"shrine"`
  (`jaymoskingdoms:block/shrine`) and `"glyph"` (overridden per patron).
- Textures to draw, 16 x 16, by `tools/gen_shrine_glyphs.py` (run `python -I tools/gen_shrine_glyphs.py` from the root;
  plain PIL, a dark stone ground `#3A3A40` with the sign in the patron's colour and a 1 px darker edge):
  | File | Sign |
  |---|---|
  | `shrine_glyph_hearthkeeper.png` | a small flame over a hearth arch, amber |
  | `shrine_glyph_anvil_father.png` | an anvil silhouette, iron grey |
  | `shrine_glyph_deep_mother.png` | three wave lines over a whale's tail, sea blue |
  | `shrine_glyph_green_huntsman.png` | antlers, leaf green |
  | `shrine_glyph_lantern_warden.png` | a lantern with a ring of light, pale moon blue |
  | `shrine_glyph_crowned_sun.png` | a rayed sun with a three-point crown, gold |
  | `shrine_glyph_ash_whisperer.png` | a closed eye over an ember, ash red-brown |
  | `shrine_glyph_truth.png` | an open eye in a plain square, white |
- No new recipe, no new item, no new loot table.
- `data/jaymoskingdoms/lore/patron_<key>.json`: eight lore books, no `drop` (handed out at shrines and at the devout
  start; clerics hand out the Truth's in part b). In-world voice, short; the patron's offerings are named in the text,
  which is how a player who has never met them learns what to bring. Text to use:
  - `patron_hearthkeeper`: title "The Kept Fire", author "a midwife". Pages: "Where a fire is kept and the door opened to
    the hurt, the Hearthkeeper sits by it. She asks for bread, cake, honey: what a guest is given." / "Lift the fallen,
    bind the cut, and she remembers. Leave her hearth cold too long and she forgets you as easily."
  - `patron_anvil_father`: "Ringing Stone", "a smith of the deep roads". "Iron, copper, gold, raw from the seam or
    poured: lay it on his stone and he hears the hammer in it." / "He loves a true blow and a deep cut into the
    mountain. The sea is no friend of his; she rusts what he makes."
  - `patron_deep_mother`: "Salt Hymn", "unsigned". "Fish, shell, the grey of the whale's bone, a pearl of ambergris: give
    the Mother back a little of what she gives." / "Her children hunt the great whales and breathe long under the
    swell. The miners' god she does not forgive."
  - `patron_green_huntsman`: "Tracks in Wet Earth", "a trapper". "Hide and feather, the foot of a hare, good red meat:
    leave it where he can find it." / "He walks without a sound and he knows where the old beasts lie. Dress what you
    kill and waste none of it."
  - `patron_lantern_warden`: "The Night Watch", "a gravedigger". "A lantern, a candle, a bone, the glow of the deep
    squid: light given back to the one who keeps the dark at bay." / "The dead that walk are his to put down, and the
    blood-drinkers hate his light."
  - `patron_crowned_sun`: "The Sun's Due", "a herald". "Gold in small coin, the sunflower, glowstone, the golden root:
    the Sun takes tribute as a king does." / "Serve a crown and march under a banner and he warms to you. Thieves and
    the smoke they hide in are his shame."
  - `patron_ash_whisperer`: "What the Coals Said", "no one". "Coal, charcoal, blaze dust, black powder, a squid's ink:
    things that burn or hide." / "Quick hands, a shut mouth, fire that does not bite. The Sun's men hang her followers
    when they catch them."
  - `patron_truth`: "The Plain Word", "the clerics". "There is one Truth and it needs no idols. Bring it the written
    word: paper, a book, a book left blank to be filled." / "The cursed cannot hide from it, nor sorcery bend it. The
    seven are tales told by those who would not hear it."

---

## 12. Hooks left for parts (b) and (c) (not built)

| For | Hook in (a) |
|---|---|
| (b) Turning away (harsh) | `State.marks` (`Mark(kind, patron, day)`) is saved and synced but never written in (a); `State.patron` / `swornDay` are cleared by (b); the favour floor `TIER_AT[0]` is where (b) lets favour fall to nothing. `Patrons.deed(..., kind)` already carries a kind string for marks. |
| (b) Clerics preach against the seven and help turn away | Every priest's faith is The Truth (`Faiths.of`); the cleric talk option sits beside the accusation one (`McaDialogueMixin`, `McaAnswersMixin`, `Accusations.priest`). Nothing preached in (a). |
| (b) Great-wars tension | `Faiths.realmFaith(server, f)`: (b) adds friction in `GreatWars.friction` where the two realms' faiths differ. |
| (b)/(c) Realm state patron, temples | `GreatWars.Creed.FAITH` already makes a realm's folk all of The Truth and weighs its dislike `FAITH_CREED_HARSH`; a temple can set `Shrine.DEDICATION` on its altars. |
| (c) Champions, weapons | `Patrons.blessed(player, patron, tier)` and `Blessings.boon(...)` are the entry points; `Patron` gains fields by appending. |

---

## 13. MODLOG text

`kingdoms/MODLOG.md`:
```
## 0.7.509: patron gods, part (a) (pack 0.20.8, Kitchen 0.5.57)
Andy picked the Runic Gods idea (our own version, nothing taken from it). `docs/Patrons-Plan.md`.
- **Eight patrons** (`Patron`): the Hearthkeeper, the Anvil-Father, the Deep Mother, the Green Huntsman, the Lantern
  Warden, the Crowned Sun, the Ash Whisperer, and The Truth, the human faith (Andy: "it usually is the faith among
  humans, you will rarely find one who entertains another creed"). Rivals: the Sun and the Ash Whisperer, the Mother and
  the Anvil-Father, the Lantern Warden and the vampires' dark; The Truth and the seven.
- **Shrines** (`Shrine`): use a patron's offering on a plain shrine to give it to them (a glyph shows on the altar).
  Hold use to pray as before. Sneak and use with an empty hand for the patrons screen.
- **Swearing** (`Patrons`): pray at a patron's shrine three days running. A devout life story starts sworn: The Truth
  for a human, their blood's patron for anyone else.
- **Favour**: prayer, a fitting offering once a day, their kind of deeds; four tiers (10, 30, 55, 80 of 100); after two
  days without any it slips 5 a day (the new Survival perk **Devotion** takes a point off a rank), never below the
  first tier for now.
- **Blessings** (`Blessings`): a passive at tiers 1-3 and a boon at tier 4 (left bracket key, every three days), each
  hooked into what was there: Wounds' healing and revives, the Smithing Anvil, tool wear, ore drops, breath, storms,
  great whales, sneaking, Kitchen's meat hooks, legend hunts, Night Vision, blows on the undead, the Nightfall plague,
  reputation, guards, Valor, war parties, pickpocketing, locks, fire, the guards' hunt, accusations, spells.
- **Faiths** (`Faiths`): every villager and townsperson holds a faith, drawn once: most humans The Truth, clerics always,
  Faith-creed realms' folk always; non-human folk mostly their blood's patron. Truth and the seven, and rival pairs,
  think less of each other: a little dearer, slower to warm, more guard trouble, cooler neighbours and good name, on
  top of blood prejudice. MCA's chat AI is told.
- The patrons' books (lore) are left on a shrine the first time you meet its patron.
- Loading hints 87-88.
- Not compile-checked yet at this entry; not played.
```
Root `MODLOG.md`:
```
## 0.20.8
- (Kingdoms 0.7.509) The Hearthkeeper's Gentle Hands: `MedicineSkill.ticks` also reads Kingdoms'
  `Patrons.blessing(player, "gentle_hands")` by reflection (a fifth off revives and treatments).
```
Kitchen (its own log or `kitchen/` notes):
```
## 0.5.57
- `Hanging.addExtraCuts` and `Hanging.addOnDone`: hooks for Kingdoms' Green Huntsman (more from a carcass, and the deed).
```
Docs to touch when built: `docs/Otectus-Plan.md` row 11 ("(a) built in Kingdoms 0.7.509"), `docs/Healing-Plan.md`
section 11 (offerings and the screen on the shrine), `docs/Abilities.md` rows for the shrine controls, the boon key and
the eight boons (Everyone, Kingdoms).

---

## 14. Build order and tracks

Two tracks, two at a time (CLAUDE.md), each in its own worktree, no shared files:
- **Track A (core)**: `Patron`, `Patrons`, `Faiths`, `Shrine`, `Healing`, `Skills` (Devotion only), `LifeStory`,
  `VillagerHeritage`, `JaymosKingdoms`, the cost-point edits (`VillagerPriceMixin`, `McaDialogueMixin`, `Approaches`,
  `Neighbours`, `Reputation`, `ChatPrompts`), the client (`PatronScreen`, `BoonScreen`, `PatronKeys`, `SkillScreen`,
  `JaymosKingdomsClient`, `StoryHoldScreen`), all lang, assets, data, tools, MODLOG.
- **Track B (blessings)**: `Blessings` (coded against the API in sections 5-6), `ItemStackWearMixin` and its mixin json,
  `LivingEntityHurtMixin`, `Valor`, `WarParties`, `Quests`, `Theft`, `Lockpicking`, `Accusations`, `Nightfall`, `Storms`,
  `KitchenCompat`, `PowersCompat`, `Skills.sneakFactor/holdTicks/pickedClean/unseenTheft`, Powers `MedicineSkill`,
  Kitchen `Hanging`. Track A owns `Skills.java`'s Perk enum; Track B touches only the four helper methods there, so merge
  B after A.
- Build `kingdoms/` (`./gradlew build`), root, and `kitchen/`. Check `useItemOn`, `EnumProperty.create`,
  `ItemStack.processDurabilityChange`, `KeyMapping.MAP`, `GuiGraphicsExtractor.item` and `ByteBufCodecs.fromCodec` in
  the 26.3 sources (all looked up for this plan). No long dev runs.

---

## 15. Open questions for Andy

1. **Left bracket for the boon menu.** Z and sneak + Z are taken by Powers and the full stop by Shoulder Surfing, so the
   boon menu opens on the left square bracket (rebindable). Good, or another key?
2. **Raised by the sunpriests.** Answered: Dawnborn start with the Crowned Sun (devout ones sworn), most Dawnborn villagers hold
   it, and they praise the sun at sunrise.
3. **Favour never falls below the first tier in (a).** Losing a patron entirely belongs to turning away (b). OK for now?
4. **Clean Cuts needs a small Kitchen hook** (Kitchen 0.5.57 and a new jar in `libs/`). Keep it, or give the Green
   Huntsman a different second blessing and leave Kitchen alone?
5. **The Truth's dislike of the seven** is set lighter (0.75) than a named rival pair (1.0), and a Faith-creed realm's
   folk mind it 1.5 times as hard. Right weight?
