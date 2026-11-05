# Patch 11b: Patron gods, part (b) (turning away, clerics, realms)

Kingdoms **0.7.513** (`kingdoms/gradle.properties`), Powers **0.20.14** (`gradle.properties`, one line in
`Wounds.healPart`). Kitchen unchanged (0.5.57). Tag new code comments `(0.7.513)` and `(0.20.14)`.

Part (a) is merged (`docs/Patrons-Plan.md`, Kingdoms 0.7.509). This is part (b). Part (c) (temples, champion bosses
fought as a group, the patrons' weapons) is **not** built here. (b) leaves it one hook: the `hunted` mark (section 3.6).

Andy's plan text: "Turning away. You can swear to another patron, but the old one marks you. First a scar (a lasting
small penalty in the old patron's domain), then trials: the old patron's omens follow you and their champion hunts you
once. A server setting can soften this." Plus clerics in town, Faith-creed realms, great wars (holy wars between rival
patrons' realms), and reputation (people who share your patron like you more).

Andy's rulings that shape this:
- Turning away is harsh: "the harsher the better for this pack".
- "Clerics will preach against the patron saints, and will aid you in turning away if you choose to in the first place."
- The Truth "usually is the faith among humans, you will rarely find one who entertains another creed. This is another
  reason humans are in a constant battle with the other ideologies / species."
- Dawnborn hold the Crowned Sun and say "Praise the sun" (built in (a); sermons answer it).
- No slash commands for players, no discovery chat text. Speech bubbles, sounds, overlays and screens are fine.

What the code already gives (checked against the worktree at `107fe5442`):
- `Patrons.State.marks` (`List<Mark(kind, patron, day)>`) is saved, kept through death (`copyOnDeath`), synced to its
  owner, and copied by every `State.with*` and `sworn(...)`. Nothing writes it yet.
- `Patrons.prayed(sp, shrine, pos, today)` (`Patrons.java:344`). Its sworn-to-another branch (370-375) only shows
  `shrine.strange`. (b) replaces that branch.
- `Patrons.decay` floors favour at `TIER_AT[0]` (491-505).
- `Faiths.realmFaith` gives a haven "any of the seven" with no patron (227-237).
- `K/Omens.java` is the raid-omen system. The new code never uses "omen" in a class or id. The class is `Turning`;
  "omens" appears only in player-facing text.

---

## 1. Files

### Kingdoms, new (main, `kingdoms/src/main/java/com/jaymoskingdoms/`)
| File | What |
|---|---|
| `Turning.java` | Turning away: the scar, trial and hunted marks, the two gamerules, every scar and trial effect (small functions called from the existing hook points), the per-second trial tick, and the rite's terms (`rite(...)`). |
| `Rites.java` | The cleric's Leaving Rite: the `OpenRite` and `TakeRite` payloads, the offer from the Interact page, the payment (`Money.charge`), and what happens after. |
| `Sermons.java` | The clerics preach: evening sermons on the square of Truth realms and villages, sermons at shrines given to one of the seven, words for those sworn to the seven, apostates denounced, and the Truth's townsfolk shunning an apostate. Speech bubbles only. |
| `RealmFaiths.java` | Each realm's state patron (Faith creed: The Truth; otherwise from a daily count of its folk's faiths, with a fallback from its blood), holy-war tension between rival patrons' realms, and the holy-war list. An Overworld attachment. |

### Kingdoms, new (client, `kingdoms/src/client/java/com/jaymoskingdoms/client/`)
| File | What |
|---|---|
| `RiteScreen.java` | The Leaving Rite's small screen: what it does, the price, Pay / Not now. |

### Kingdoms, changed
| File | Change |
|---|---|
| `Patrons.java` | `prayed`: the turning branch (section 3.2). New `lose(sp, n)`. `decay`: no floor while trials run. `blessing`: `"wounds_slow"`. `tick`: calls `Turning.tick`. `State.withMarks`. New `swornTurned(...)`. Tier 0 name in `retier`. `day(long clock)` helper for the client. Part (c) API `hunters` / `hunted`. DISCONNECT and SERVER_STOPPED also call `Turning.forget` / `Turning.clear`. `register()` calls `Turning.register()`. |
| `Shrine.java` | `pray`: the shrine of a patron whose trials you are under gives no heal ("cold"). |
| `Blessings.java` | Turning hooks inside `wear`, `zoneBonus`, `reputation`, `haltGap`, `searchEase`, `valorGrowth`, `pickHold`, `lockWidth`, `wantedTicks`, `accuseGain`, `dealt` (section 4). Callers are not changed. |
| `Faiths.java` | `RealmFaith` always has a patron (from `RealmFaiths`). `minds`: the old patron's faithful shun an apostate, the Truth's scar, clerics colder to the seven's sworn. `reputation`: a branch for a shared faith, and a general rival branch. New `kinHearts`. `prompt`: the apostate line. |
| `GreatWars.java` | `daily`: drops dead holy-war ids. `stir`: holy tension and the holy rivals line. `begun`: a holy war is always great and gets its own line. `rally`/`lean`: realms of a side's patron lean to it. `ended`: drops the holy mark. |
| `InteractActions.java` | New action `RITE` ("Leaving Rite") for a cleric. |
| `Theft.java` | `witnesses`: range times `Turning.witnessRange(player)`. |
| `Senses.java` | `factor`: times `Turning.sight(player, looker, level)` before the clamp. |
| `disasters/Storms.java` | `roll`: a storm cell for a Deep Mother trial. `strike`: bolts drawn near that player. |
| `mixin/LivingEntityHealMixin.java` | `@ModifyVariable` on `heal`: a player's heal times `1 - Turning.healing(player)`. |
| `mixin/mca/McaDialogueMixin.java` | `warm` also adds `Faiths.kinHearts(...)`. |
| `Standing.java` | The ruler's lines gain the realm's faith (`standing.jaymoskingdoms.faith`). |
| `JaymosKingdoms.java` | `Rites.register(); Sermons.register(); RealmFaiths.register();` right after `Patrons.register()` (line 501). |
| `kingdoms/src/client/.../PatronScreen.java` | Scars and trials: notches on the list, a trials note in the header, a marks block in the detail, the turning line, a warning on other patrons (section 8). |
| `kingdoms/src/client/.../PatronKeys.java` | Registers the `Rites.OpenRite` receiver, which opens `RiteScreen`. |
| `kingdoms/src/client/.../StoryHoldScreen.java` | `HINTS = 90`. |
| `assets/jaymoskingdoms/lang/en_us.json` | Section 9. Remove `message.jaymoskingdoms.shrine.strange`, which is no longer used. |
| `kingdoms/MODLOG.md`, `kingdoms/gradle.properties` (`version=0.7.513`) | Section 10. |

### Powers, changed (0.20.14)
| File | Change |
|---|---|
| `src/main/java/com/jaymospowers/core/wounds/Wounds.java` | `healPart`: right after the early-return guard, `amount *= 1.0F - MedicineSkill.blessing(entity, "wounds_slow");` with `if (amount <= 0.0F) return 0.0F;`. `MedicineSkill.blessing` is package-private static in the same package, and gives 0 for anything but a player and on any failure. Every Powers heal (Resting, Herbal, Bleeding, Mending, Treatment) and Kingdoms' `PowersCompat.healEveryPart` (shrine prayer, Hearth's Warmth, Hearthfire) goes through `healPart`. |
| `gradle.properties` (`version=0.20.14`), root `MODLOG.md` | Section 10. |

No new mixin class, so the mixin JSONs are unchanged. There are no new textures and no recipes.

---

## 2. Marks (`Patrons.Mark`, no codec change)

All kinds are plain strings (constants in `Turning`). `patron` is a key. `day` is a shrine day (`Patrons.today`).

| Kind | `patron` | `day` | Meaning |
|---|---|---|---|
| `scar` | the patron left | the day you left | Permanent. One per leaving, so leaving the same patron again adds another. Effects count at most `SCAR_MOST_EACH` per patron. |
| `trial` | the patron left | the day the trials **end** | The old patron's omens follow you until that day. |
| `trial_eased` | the patron left | the day they end | The same after a cleric's rite: shorter, with the omens at `TRIAL_EASED` strength. |
| `rite` | the patron you are turning **to** | the day of the rite | A rite paid during a turning streak. It is used up at the turning: the trials start eased and shortened. It lasts `RITE_KEEP_DAYS`. |
| `hunted` | the patron left | the day you left | Part (c): that patron's champion still has to hunt you. Written only when trials are on. (b) never reads it for an effect or shows it. |
| `hunted_done` | the patron | the day of the hunt | Part (c) turns `hunted` into this. |

Bookkeeping in `Turning.tick` (daily): drop `trial`/`trial_eased` when `day <= today`, and `rite` when
`day < today - RITE_KEEP_DAYS`. Never drop `scar` or `hunted`. If marks ever exceed `MARKS_MOST`, drop the oldest
`hunted_done`, then `rite`.

`Patrons.State` gains:
```java
State withMarks(List<Mark> m) {
	return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, boonReady, known, List.copyOf(m), seeded);
}
```

---

## 3. Turning away (`Turning.java`, `Patrons.java`)

### 3.1 Server settings (gamerules, registered in `Turning.register()`, which `Patrons.register()` calls first)
```java
/** (0.7.513) jaymoskingdoms:patron_trial_days: days of trials after turning from a patron (0: no trials, no champion's hunt). */
public static GameRule<Integer> TRIAL_DAYS;
/** (0.7.513) jaymoskingdoms:patron_scars: turning from a patron leaves its scar. */
public static GameRule<Boolean> SCARS;
TRIAL_DAYS = GameRuleBuilder.forInteger(TRIAL_DAYS_DEFAULT).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("patron_trial_days"));
SCARS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("patron_scars"));
```
This is the same pattern as `Disasters.register()` (`disasters/Disasters.java:60-62`). Read with
`server.overworld().getGameRules().get(TRIAL_DAYS)`, clamped to `0..TRIAL_DAYS_MOST`. The defaults are harsh. An admin
softens with `/gamerule jaymoskingdoms:patron_trial_days 2` and `/gamerule jaymoskingdoms:patron_scars false` (admin
commands, not player commands).

### 3.2 The prayer (`Patrons.prayed`, the sworn-to-another branch)
Prayer is still once a day in total (`Shrine.PRAYED`), so turning takes `SWEAR_DAYS` (3) days of prayer at the new
patron's shrine, as swearing does.
```java
} else {
	// (0.7.513) praying your way to another patron: the streak counts toward them, and your own patron notices each day
	Patron mine = s.patronOf();
	Streak st = s.streak;
	int days = st.patron.equals(shrine.key) && st.lastDay == today - 1 ? st.days + 1 : 1;
	if (st.patron.equals(shrine.key) && st.lastDay == today) days = st.days;
	lose(sp, FAVOUR_STRAYED);
	if (days >= SWEAR_DAYS && mine != null) {
		Turning.turn(sp, mine, shrine, today);
	} else {
		set(sp, state(sp).withStreak(new Streak(shrine.key, days, today)));
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.turning", coloured(shrine), mine == null ? "" : coloured(mine), days, SWEAR_DAYS));
		// a low bell, to the player
		level.playSound(null, sp.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 0.5F);
	}
}
```
- `static final int FAVOUR_STRAYED = 5;` (in Patrons tuning). Each day spent praying toward another patron costs your
  own patron 5 favour, and this can fall below tier 1.
- `static void lose(ServerPlayer sp, int n)`: `favour = max(0, favour - n)`, then `retier`. `lastFavourDay` is unchanged.
- An unsworn player's streak is unchanged from (a).

### 3.3 `Turning.turn(ServerPlayer sp, Patron from, Patron to, long today)`
1. `days = trialDays(server)`, `scars = SCARS rule`.
2. `marks = new ArrayList<>(state.marks())`.
3. If `scars`, add `Mark("scar", from.key, today)`.
4. `boolean rite = marks.removeIf(m -> "rite".equals(m.kind()) && m.patron().equals(to.key) && m.day() >= today - RITE_KEEP_DAYS);`
5. If `days > 0`: add `Mark(rite ? "trial_eased" : "trial", from.key, today + (rite ? riteDays(days) : days))`. If
   there is no `hunted` mark for `from`, add `Mark("hunted", from.key, today)`.
6. `Patrons.swornTurned(sp, to, TURNED_FAVOUR, today, marks)`. This is `set(sp, state.sworn(to, favour, today).withMarks(marks))`,
   `learn(sp, to)`, `Blessings.forget(sp.getUUID())` (timed boons of the old patron end now), then `Blessings.refresh(sp)`.
   Favour with the new patron starts at `TURNED_FAVOUR` = 2, which is tier 0, so no blessing until 10.
7. Screen and sound (no chat):
   - `sp.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F))` (the
     elder guardian's face and curse sound, to this player only; checked in 26.3).
   - `BEACON_DEACTIVATE` at the player, 0.8 / 0.7. `DustParticleOptions(from.colour & 0xFFFFFF, 1.4F)` x30 and
     `ParticleTypes.LARGE_SMOKE` x12 around them.
   - Overlay `message.jaymoskingdoms.shrine.turned` (`to`, `from`), in `from`'s colour.
   - No `TOTEM` burst. Swearing fresh (unsworn) keeps (a)'s.

`riteDays(int d) = max(RITE_LEAST_DAYS, (int)Math.ceil(d * RITE_SHORTEN))`.

### 3.4 The trials (`Turning`, read by hooks)
`static float trial(Player p, Patron patron)` gives 1.0 for a live `trial` mark of `patron` (`day > today`),
`TRIAL_EASED` for a live `trial_eased`, and the largest if there are several, else 0. `static int scars(Player p, Patron patron)`
gives the count, capped at `SCAR_MOST_EACH`. `static boolean anyTrial(Player p)`. Every reader returns at once when
`marks().isEmpty()` (`Senses.factor` is hot). Client-safe for the screen: they read only the synced state. On the
client, "today" is `Patrons.day(level.getOverworldClockTime())`.

`today` on the server is `Patrons.today(server)`. For hot reads without a server handle, use
`Patrons.day(player.level().getOverworldClockTime())`.

### 3.5 Effects (each multiplies by the trial strength `s`, 1 or `TRIAL_EASED`; scars by the count `n`)

| Patron | Trial (the old patron's omens) | Scar (each, stacking) | Hook |
|---|---|---|---|
| Hearthkeeper | Wounds heal `TRIAL_HEAL` x s slower | `SCAR_HEAL` x n slower | `Turning.healing(p)` = min(`HEAL_CUT_MOST`, sum). Read by Powers' `Wounds.healPart` via `Patrons.blessing(p, "wounds_slow")`, and by `LivingEntityHealMixin` for vanilla heals (food, potions). |
| Anvil-Father | Each point of wear on a hand item gets +1 with chance `TRIAL_WEAR` x s. Anvil zone `-TRIAL_ZONE` x s | +1 wear with chance `SCAR_WEAR` x n. Zone `-SCAR_ZONE` x n | `Blessings.wear` (hand items, after Father's Fire, before Sound Steel), `Blessings.zoneBonus` + `Turning.zone(smith)`, which is never below `-ZONE_SLIP_MOST`. |
| Deep Mother | Storms find you, bolts are drawn to you, extra air loss under water, and a downward tug in water | Extra air loss `SCAR_AIR` x n a second under water | `Storms.roll` / `Storms.strike` (3.5.1), `Turning.tick` (air, tug). |
| Green Huntsman | Wild beasts see you `TRIAL_BEAST_SIGHT` x s farther and turn on you | Beasts see you `SCAR_BEAST_SIGHT` x n farther | `Senses.factor`, `Turning.tick` (beasts turn). |
| Lantern Warden | At night the undead see you `TRIAL_UNDEAD_SIGHT` x s farther and are lured to you | Blows on undead and vampires `-SCAR_GRAVE` x n. Undead at night see you `SCAR_UNDEAD_SIGHT` x n farther | `Senses.factor`, `Turning.tick` (lure), `Blessings.dealt`. |
| Crowned Sun | Guards halt you more often (gap x `TRIAL_HALT` at s=1, x lerp(1, TRIAL_HALT, s)), +`TRIAL_SEARCH` search, good name won `TRIAL_SUN_REP` x s slower with every realm | Halt gap x (1 - `SCAR_HALT` x n). Valor feats x (1 - `SCAR_VALOR` x n) | `Blessings.haltGap` x `Turning.halt(p)` (floor `HALT_LEAST`), `Blessings.searchEase` - `Turning.search(p)`, `Blessings.reputation`, `Blessings.valorGrowth`. |
| Ash Whisperer | Witness range x (1 + `TRIAL_WITNESS` x s). Wanted time x (1 + `TRIAL_WANTED` x s) | Lock sweet spot x (1 - `SCAR_LOCK` x n). Pick hold x (1 + `SCAR_PICK` x n) | `Theft.witnesses`, `Blessings.wantedTicks`, `Blessings.lockWidth`, `Blessings.pickHold`. |
| The Truth | The Truth's townsfolk shun you (minds `TRIAL_MINDS` x s x realm weight), clerics denounce you on sight, and the Truth's folk mutter at you | The Truth's folk mind you `SCAR_TRUTH_MINDS` x n (dearer, slower hearts, more guard trouble, cooler good name in Truth realms). True accusations win `SCAR_ACCUSE` x n less | `Faiths.minds`, `Faiths.reputation`, `Sermons`, `Blessings.accuseGain`. |
| All | The old patron's faithful (villagers and folk who hold it) mind you at `TRIAL_MINDS` x s. Their shrines are cold (no prayer heal). Now and then an omen sign shows (3.5.2) | (only the domain scar) | `Faiths.minds`, `Shrine.pray`, `Turning.tick`. |

A sworn player prays to their own patron, so "mind" from a patron's own folk is 0 when you have come back to it
(`theirs == mine` returns first). Masked players (`Prejudice.masked`) are not known, so they are not shunned.

#### 3.5.1 Deep Mother storms (`disasters/Storms.java`)
- In `roll`, after the existing two branches (it already returns when `SERVER.size() >= MOST`):
  ```java
  // (0.7.513) the Deep Mother's omens: a thunderstorm finds one who turned from her
  ServerPlayer bait = com.jaymoskingdoms.Turning.stormBait(level, random);
  if (bait != null && !dry(level, bait.blockPosition())) form(level, bait.position(), Storm.THUNDER, random, true);
  ```
  `stormBait` goes through players with `s = trial(p, DEEP_MOTHER) > 0` and returns the first with
  `random.nextDouble() < ROLL_TICKS / (TRIAL_STORM_DAYS * 24000.0) * s`. That is about one cell a day at full
  strength (`ROLL_TICKS` is package-private in `Storms`, so pass it in: `stormBait(level, random, ROLL_TICKS)`).
  THUNDER cells send no chat (only SUPERCELL does).
- In `strike`, right after `BlockPos top = level.getHeightmapPos(...)` and before the precipitation check:
  ```java
  BlockPos drawn = com.jaymoskingdoms.Turning.boltDrawn(level, s.x, s.z, s.radius, random);
  if (drawn != null) top = drawn;
  ```
  `boltDrawn` finds a player with the Mother's trial within `radius` of the storm (horizontal). With chance
  `TRIAL_BOLT_DRAWN` x s it takes a spot `TRIAL_BOLT_NEAR_MIN..TRIAL_BOLT_NEAR_MAX` blocks from them in a random
  direction and returns `getHeightmapPos(MOTION_BLOCKING, ...)`, or null. The existing rain check, Calm Water and
  lightning rods still apply after it, so a rod still saves you.

#### 3.5.2 `Turning.tick(MinecraftServer server, long today, int tick)` (from `Patrons.tick`, every `TICK_EVERY` = 20)
Per alive, non-spectator player with marks:
- **Daily:** on the first tick of a new day (`SEEN` map, `UUID -> day`, not saved), expire marks (section 2). If a trial
  ended, overlay `message.jaymoskingdoms.trial.over` (patron) and `AMETHYST_BLOCK_RESONATE` at 0.6 / 1.2. Otherwise,
  for each live trial, overlay `message.jaymoskingdoms.trial.day` (patron, days left).
- **Every second, Deep Mother:** if `sp.isUnderWater()` and not aboard (`Ships.aboard(sp) == null && !(sp.getVehicle() instanceof AbstractBoat)`):
  `sp.setAirSupply(Math.max(-19, sp.getAirSupply() - round(TRIAL_AIR * s) - SCAR_AIR * n))`. With the trial,
  `sp.isInWater()`, not aboard, and `random < TRIAL_TUG_CHANCE * s`: `sp.push(0, -TRIAL_TUG, 0); sp.syncVelocity = true;`
  (the `WillClash` pattern).
- **Every `TRIAL_EVERY` (100) ticks:**
  - Green Huntsman trial: wild beasts (`Turning.wildBeast(e)`: an untamed `Wolf`, `PolarBear`, `beasts.WildBoar`,
    `wild.WildBeast`, the same set as `Patrons.killed`) within `TRIAL_BEAST_RANGE`, alive, with no target, that can see
    the player (`hasLineOfSight`). Each turns with chance `TRIAL_BEAST_CHANCE` x s: `mob.setTarget(sp)`, and for a
    `NeutralMob`, `setPersistentAngerTarget(EntityReference.of(sp))` and `startPersistentAngerTimer()` (the
    `Encounters.after` pattern). Not for a creative player.
  - Lantern Warden trial, at night (`Vampires.night(level.getOverworldClockTime())`): mobs with `EntityTypeTags.UNDEAD`
    within `TRIAL_UNDEAD_RANGE` and no target: `Senses.lure(mob, sp.position(), sp.getUUID(), TRIAL_LURE_LOUDNESS * s)`.
- **Every `TRIAL_SIGN_EVERY` ticks**, with chance `TRIAL_SIGN_CHANCE` x s per live trial, an omen sign to that player
  only: `level.sendParticles(sp, new DustParticleOptions(p.colour & 0xFFFFFF, 1.0F), true, false, x, y + 1, z, 14, 0.7, 0.9, 0.7, 0.0)`
  and `sp.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT, x, eyeY, z, 0.5F, pitch, seed))`.
  The sounds (all checked in 26.3 `SoundEvents`): Hearthkeeper `FIRE_EXTINGUISH`, Anvil-Father `ANVIL_LAND` (0.3, 0.5),
  Deep Mother `CONDUIT_DEACTIVATE`, Green Huntsman `POLAR_BEAR_WARNING` (0.4, 0.6), Lantern Warden `CANDLE_EXTINGUISH`
  (plus `ParticleTypes.SOUL` x4), Crowned Sun `BEACON_DEACTIVATE`, Ash Whisperer `SCULK_CLICKING`, The Truth `VILLAGER_NO` (0.6, 0.7).

`Turning.forget(UUID)` drops `SEEN`. `Turning.clear()` empties it. Both are called next to `Blessings.forget/clear` in
`Patrons.register()`.

#### 3.5.3 Sight (`Senses.factor`, before `return Math.max(...)`)
`f *= Turning.sight(player, looker, level);` gives 1, or the product of:
- `looker.getType().is(EntityTypeTags.UNDEAD) && Vampires.night(clock)`: `1 + TRIAL_UNDEAD_SIGHT * s(LANTERN_WARDEN) + SCAR_UNDEAD_SIGHT * n(LANTERN_WARDEN)`.
- `Turning.wildBeast(looker)`: `1 + TRIAL_BEAST_SIGHT * s(GREEN_HUNTSMAN) + SCAR_BEAST_SIGHT * n(GREEN_HUNTSMAN)`.

### 3.6 Part (c) hook (in `Patrons`)
```java
/** (0.7.513) For part (c): patrons whose champion has still to hunt this player, oldest first (the "hunted" marks). */
public static List<Patron> hunters(Player player)
/** (0.7.513) For part (c): {@code p}'s champion has had its hunt; the mark is kept as "hunted_done". */
static void hunted(ServerPlayer sp, Patron p)
```
Not shown on any screen in (b), and no effect yet.

### 3.7 Decay while in trials (`Patrons.decay`)
While `Turning.anyTrial(sp)`, the floor is 0 and the "below tier 1 keeps its favour" guard is skipped:
`int floor = Turning.anyTrial(sp) ? 0 : TIER_AT[0];`. After the trials the floor is `TIER_AT[0]` again, which never
raises favour (the guard keeps a sub-floor favour).

### 3.8 Cold shrine (`Shrine.pray`)
Before the heal: `Patron p = state.getValue(DEDICATION).patron(); if (p != null && Turning.trial(player, p) > 0)`.
Set `PRAYED`, give no heal and no `easeFever`, play `CANDLE_EXTINGUISH` (0.8, 0.8), show overlay
`message.jaymoskingdoms.shrine.cold` (p), then still call `Patrons.prayed` (praying your way back to the old patron
counts as a turning streak). Return.

### 3.9 Tier 0
`retier`: name `"patron.jaymoskingdoms.tier." + tier` (tier 0 now has a name). `PatronScreen.header`: the same.
`BoonScreen` is unchanged (it shows `.boon.locked` below tier 4).

### 3.10 Tuning (`static final` at the top of `Turning`)
```java
static final int TRIAL_DAYS_DEFAULT = 7, TRIAL_DAYS_MOST = 60;
static final int TURNED_FAVOUR = 2;
static final int SCAR_MOST_EACH = 3, MARKS_MOST = 40;
/** An eased trial's strength (after a cleric's rite). */
static final float TRIAL_EASED = 0.5F;
static final float RITE_SHORTEN = 0.5F;
static final int RITE_LEAST_DAYS = 1, RITE_KEEP_DAYS = 3;
// Hearthkeeper
static final float TRIAL_HEAL = 0.4F, SCAR_HEAL = 0.1F, HEAL_CUT_MOST = 0.6F;
// Anvil-Father
static final float TRIAL_WEAR = 0.5F, SCAR_WEAR = 0.1F;
static final double TRIAL_ZONE = 0.6, SCAR_ZONE = 0.15, ZONE_SLIP_MOST = 1.0;
// Deep Mother
static final int TRIAL_AIR = 8, SCAR_AIR = 2;
static final double TRIAL_TUG = 0.12;
static final float TRIAL_TUG_CHANCE = 0.3F, TRIAL_BOLT_DRAWN = 0.35F;
static final double TRIAL_STORM_DAYS = 1.0;
static final int TRIAL_BOLT_NEAR_MIN = 3, TRIAL_BOLT_NEAR_MAX = 8;
// Green Huntsman
static final double TRIAL_BEAST_RANGE = 16.0, TRIAL_BEAST_SIGHT = 0.5, SCAR_BEAST_SIGHT = 0.1;
static final float TRIAL_BEAST_CHANCE = 0.2F;
// Lantern Warden
static final double TRIAL_UNDEAD_RANGE = 40.0, TRIAL_LURE_LOUDNESS = 8.0, TRIAL_UNDEAD_SIGHT = 0.6, SCAR_UNDEAD_SIGHT = 0.1;
static final float SCAR_GRAVE = 0.1F;
// Crowned Sun
static final float TRIAL_HALT = 0.4F, SCAR_HALT = 0.15F, HALT_LEAST = 0.2F, TRIAL_SUN_REP = 0.3F, SCAR_VALOR = 0.1F;
static final int TRIAL_SEARCH = 1;
// Ash Whisperer
static final float TRIAL_WITNESS = 0.5F, TRIAL_WANTED = 0.5F, SCAR_LOCK = 0.08F, SCAR_PICK = 0.08F;
// The Truth (and every patron's faithful)
static final float TRIAL_MINDS = 1.0F, SCAR_TRUTH_MINDS = 0.25F, SCAR_ACCUSE = 0.15F;
static final int TRIAL_EVERY = 100, TRIAL_SIGN_EVERY = 1200;
static final float TRIAL_SIGN_CHANCE = 0.5F;
```
`Patrons`: `static final int FAVOUR_STRAYED = 5;`.

Anvil zone check: Powers' `Forging.perfectHalf` is `max(MIN_PERFECT_HALF 2.0, ...) + bonus` (Forging.java:140-141).
The Kingdoms total is never below `-ZONE_SLIP_MOST` (-1.0): True Strike (+0.75) cannot apply, because you have left
the Anvil-Father, and `Provenance` only adds. So the perfect half stays at 1.0 or more, and Powers needs no clamp.

---

## 4. Blessings hooks (`Blessings.java`, the callers are unchanged)

| Function | Change (0.7.513) |
|---|---|
| `wear(stack, player, wear)` | After the hand check and the `FATHERS_FIRE` return: `wear = Turning.wear(player, wear);` (adds wear for the Anvil-Father's trial and scar), then Sound Steel as before. |
| `zoneBonus(smith)` | `(has(...) ? TRUE_STRIKE_ZONE : 0.0) + Turning.zone(smith)` |
| `reputation(kingdom, player, by)` | First: `by = Turning.reputation(player, by);` (Sun trial: a gain x (1 - TRIAL_SUN_REP x s), at least 1 if it was positive). |
| `haltGap(player, f, ticks)` | `m *= Turning.halt(player);` |
| `searchEase(player, f)` | `- Turning.search(player)` (negative ease means more searching, and `Approaches:644` already does `max(0, ...)`). |
| `valorGrowth(holder)` | `* (holder instanceof Player p ? Turning.valor(p) : 1)` |
| `pickHold(player)` / `lockWidth(player)` | `* Turning.pickHold(player)` / `* Turning.lockWidth(player)` |
| `wantedTicks(player, ticks)` | `Turning.wanted(player, ...)` around the result |
| `accuseGain(accuser, gain)` | `* Turning.accuse(accuser)` = `1 - SCAR_ACCUSE x n(TRUTH)` |
| `dealt(target, source, amount)` | In the attacker branch, if `target.is(UNDEAD) or Vampires.isVampire(target)`: `amount *= Turning.graveBlows(attacker)` = `1 - SCAR_GRAVE x n(LANTERN_WARDEN)` |

`Patrons.blessing(player, key)`: `"wounds_slow"` returns `Turning.healing(player)`.

`LivingEntityHealMixin`:
```java
/** (0.7.513) The Hearthkeeper's trial and scar: a player heals slower (food, potions; Powers' part heals read it in healPart). */
@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
private float jaymoskingdoms$turnedHeal(float amount) {
	return (Object)this instanceof Player p && !p.level().isClientSide() ? amount * (1.0F - Turning.healing(p)) : amount;
}
```
Powers' `DownedHealMixin` uses the same `@ModifyVariable` shape. Powers' `healPart` sets health directly and does not
call `heal`, so nothing is cut twice.

`Theft.witnesses` (line 571): `double range = (...) * Identities.hoodSight(player) * Turning.witnessRange(player);`.

---

## 5. The cleric's Leaving Rite (`Rites.java`, `InteractActions.java`, `RiteScreen.java`)

Andy: clerics "will aid you in turning away if you choose to". A town cleric (`Accusations.priest`: a grown villager
with the CLERIC profession) does the rite on MCA's Interact page, the same place as Ask about Money. MCA is in the
pack, as for the accusation. The rite **never removes a scar**.

### 5.1 When it is offered (`Turning.rite(ServerPlayer sp, long today)` returns `@Nullable Rite`)
`record Rite(Patron from, Patron to, int daysLeft, int daysAfter, boolean streak)`:
1. **Trials running:** the first live `trial` mark (not already eased). `from` is its patron, `to` is the current
   patron, `daysLeft = day - today`, `daysAfter = riteDays(daysLeft)`, `streak = false`.
2. **Turning under way:** sworn, `streak.patron` set, not your own patron, `streak.lastDay >= today - 1`, no `rite` mark
   for that target, and `trialDays > 0`. `from` is the current patron, `to` is the streak's patron, `daysLeft = -1`,
   `daysAfter = riteDays(trialDays)`, `streak = true`.
3. Otherwise null, and the button is hidden.

Any patron to any other, The Truth included (from it or to it).

### 5.2 Price (`Rites.price(sp, rite)`, milli)
`emeralds = (RITE_COST + RITE_COST_PER_SCAR * all scars) * (from == TRUTH ? RITE_FROM_TRUTH : 1) * (to == TRUTH ? RITE_TO_TRUTH : 1)`,
rounded and at least 1, times `Money.UNIT`. It is paid in the coin of the cleric's settlement:
`Faction f = Quests.settlement(level, priest)`, `Money.canPay(sp, f, milli)`, `Money.charge(sp, f, milli)` (all or
nothing; a creative player pays nothing), and the price text is `Money.text(f, milli)`.
```java
static final int RITE_COST = 8, RITE_COST_PER_SCAR = 4;
static final float RITE_FROM_TRUTH = 2.0F, RITE_TO_TRUTH = 0.5F;
static final double RITE_REACH = 6.0;
```

### 5.3 Interact page (`InteractActions`)
- `public static final String RITE = "rite";` and `ALL = List.of(AUDIENCE, WARES, TRIBUTE, SPY, RAID, MONEY, RITE)`.
  `McaScreensMixin` builds the buttons from `ALL`, and `McaActionButtonsMixin` stacks the shown ones under Ask the
  Way, so the client needs only the lang key.
- `offers`: `case RITE -> Accusations.priest(v) && player.level() instanceof ServerLevel l && Turning.rite(player, Patrons.today(l.getServer())) != null;`
- `act`: `case RITE -> { if (McaBridge.heartsOf((Villager)v, player) < Directions.REFUSE_HEARTS) return Component.translatable("talk.jaymoskingdoms.rite.refuse"); Rites.offer(player, (Villager)v); return null; }`

### 5.4 Payloads (registered in `Rites.register()`)
- Clientbound `OpenRite(int entityId, String from, String to, int daysLeft, int daysAfter, Component price, boolean canPay)`,
  `Type<>(JaymosKingdoms.id("patron_rite"))`, `StreamCodec.composite(VAR_INT, STRING_UTF8, STRING_UTF8, VAR_INT, VAR_INT, ComponentSerialization.STREAM_CODEC, BOOL, ...)`.
  26.3's `StreamCodec.composite` takes up to 12 fields.
- Serverbound `TakeRite(int entityId)`, `Type<>(JaymosKingdoms.id("patron_rite_take"))`. The receiver calls
  `Rites.take(context.player(), entityId)`.

`Rites.take` checks everything again: the entity is in the player's level, a priest, alive, not downed, within
`RITE_REACH`, and `Turning.rite(...)` is non-null and the same `from`/`to`. Then it charges. If the charge fails:
overlay `message.jaymoskingdoms.rite.poor`, `VILLAGER_NO`. If it is paid:
- Trials case: replace that mark with `Mark("trial_eased", from, today + daysAfter)`.
- Streak case: add `Mark("rite", to, today)`.
- The cleric speaks, `Speech.sayTo(level, priest, sp, line, Speech.GOLD)`: `speech.jaymoskingdoms.rite.home.<0..1>` if
  `to == TRUTH`, else `speech.jaymoskingdoms.rite.done.<0..1>`.
- `BELL_RESONATE` at the priest (0.8, 1.2), `END_ROD` x16 round the player, overlay
  `message.jaymoskingdoms.rite.done` (from).
- If `to == TRUTH` and the player is sworn to it: `Patrons.deed(sp, TRUTH, DEED_SMALL, "rite")`.

### 5.5 `RiteScreen` (client, `extends Screen`, not a pause screen)
`W = 240, H = 132`, centred, `Ui.panel` in `from`'s colour. Lines (`Ui.line` / wrapped):
1. Title `gui.jaymoskingdoms.rite.title`, gold.
2. `daysLeft >= 0`: `gui.jaymoskingdoms.rite.trial` (from, daysLeft). Otherwise `gui.jaymoskingdoms.rite.streak` (from, to).
3. `daysLeft >= 0`: `gui.jaymoskingdoms.rite.after` (daysAfter). Otherwise `gui.jaymoskingdoms.rite.after_streak` (daysAfter).
4. `gui.jaymoskingdoms.rite.scar`, `Ui.HINT`.
5. `gui.jaymoskingdoms.rite.price` (price), `Ui.TEXT`. If `!canPay`, then `gui.jaymoskingdoms.rite.cannot` in `Ui.BAD`.
6. Buttons: **Pay** (`gui.jaymoskingdoms.rite.pay`, active only if `canPay`; sends `TakeRite`, then closes) and
   **Not now** (`gui.jaymoskingdoms.rite.leave`).

`PatronKeys.register()`: `ClientPlayNetworking.registerGlobalReceiver(Rites.OpenRite.TYPE, (payload, context) -> context.client().gui.setScreen(new RiteScreen(payload)));`
(the same call as the `OpenShrine` receiver there). `RiteScreen.onClose` goes to `null`. MCA's screen has already closed,
because `McaCommandMixin` calls `stopInteracting()` before `act`.

---

## 6. Clerics preach (`Sermons.java`)

Every cleric holds The Truth (`Faiths.of`, `Accusations.priest`). Speech bubbles only (`Speech.say` / `sayTo`, in the
speaker's tongue through `Languages.heard`). `Speech.say` falls back to a chat line only for a client without the mod,
which never happens in the pack.

`register()`: `END_SERVER_TICK -> tick` and `SERVER_STOPPED` clears the maps. `tick` runs every `SCAN_EVERY`.

### 6.1 On the square (evening sermon)
For each faction `f` (`Factions.all`) that is not fallen, not a barbarian camp, with `RealmFaiths.patron(server, f) == TRUTH`
and `level.isPositionEntityTicking(f.seat())`. It must not be a festival day (`Festivals.festivalDay(level, f, day)`),
`Royals.timeOfDay(level)` must be in `[SQUARE_FROM, SQUARE_TILL)`, and a player must be within `HEAR_RANGE` of the seat:
- The preacher is the nearest priest to the seat within `SQUARE_DRAW` that is awake, not downed and not a baby. It is
  kept for the day in `PREACHER` (`int realm -> UUID`).
- On the first scan of the day: `BELL_BLOCK` at the seat (2.0, 0.9). This is the only signal, with no chat.
- If the preacher is farther than `SQUARE_RANGE`: `priest.getNavigation().moveTo(seat.x + 0.5, seat.y, seat.z + 0.5, SQUARE_WALK_SPEED)`,
  re-issued every scan, the same way Festivals walks villagers to the seat.
- Within it: every `LINE_GAP` ticks, `Speech.say(level, priest, translatable("speech.jaymoskingdoms.sermon." + i), Speech.WHITE, SPEECH_RANGE)`.
  Lines run `i = (day + n) % SERMON_LINES`, so each evening starts on a different line.

### 6.2 Near a shrine of the seven
Each scan, for each priest within `HEAR_RANGE` of a player whose `SHRINE_GAP` cooldown (`SHRINE_NEXT`, `UUID -> gameTime`)
has run out: scan `BlockPos.betweenClosed(pos.offset(-SHRINE_SCAN, -SHRINE_SCAN_Y, -SHRINE_SCAN), pos.offset(SHRINE_SCAN, SHRINE_SCAN_Y, SHRINE_SCAN))`
for a `Shrine` block with `DEDICATION.patron()` of the seven. That is at most 17 x 7 x 17 = 2023 blocks, once per
priest per `SHRINE_GAP`. A cooldown is set whether a shrine is found or not, so the scan is cheap. On a find, say
`speech.jaymoskingdoms.sermon.shrine.<0..2>` (`Patrons.coloured(patron)`) in `Speech.YELLOW`. No shrine registry is needed.

### 6.3 Words for players near a priest (`DENOUNCE_RANGE`, per priest and player `DENOUNCE_GAP`, `SPOKE` map)
Not for a masked player (`Prejudice.masked`), and only when the priest can see them (`hasLineOfSight`). The first that
fits is said:
1. In The Truth's trials (an apostate): `speech.jaymoskingdoms.sermon.apostate.<0..2>`, `Speech.RED`, to everyone near (`say`).
2. Sworn to The Truth with another patron's trial running (just came over): `speech.jaymoskingdoms.sermon.home.<0..2>`,
   `Speech.GOLD`, `sayTo`. These lines hint that the cleric can help with the omens. This is how the rite is found,
   with no chat.
3. Sworn to one of the seven, with chance `HEATHEN_CHANCE`: `speech.jaymoskingdoms.sermon.heathen.<0..2>` (their
   patron's name), `Speech.YELLOW`, `sayTo`.

### 6.4 Shunning (The Truth's trial)
For a player in The Truth's trials, every scan: Truth-holding villagers and folk (`Faiths.holds`, `Faiths.of == TRUTH`,
not priests) within `SHUN_RANGE`, with chance `SHUN_CHANCE`, and a per-player `SHUN_GAP` (`SHUNNED` map), say
`speech.jaymoskingdoms.shun.<0..2>` in `Speech.RED`. One villager per gap.

### 6.5 Tuning
```java
static final int SCAN_EVERY = 100, LINE_GAP = 200, SERMON_LINES = 6;
static final double HEAR_RANGE = 32.0, SQUARE_RANGE = 8.0, SQUARE_DRAW = 48.0, SQUARE_WALK_SPEED = 0.6, SPEECH_RANGE = 24.0;
static final long SQUARE_FROM = 9000L, SQUARE_TILL = 11000L;          // the free evening hours (Routines.EVENING..)
static final int SHRINE_SCAN = 8, SHRINE_SCAN_Y = 3, SHRINE_GAP = 2400, SHRINE_LINES = 3;
static final double DENOUNCE_RANGE = 8.0, SHUN_RANGE = 5.0;
static final int DENOUNCE_GAP = 1200, SHUN_GAP = 1200, APOSTATE_LINES = 3, HOME_LINES = 3, HEATHEN_LINES = 3, SHUN_LINES = 3;
static final float HEATHEN_CHANCE = 0.35F, SHUN_CHANCE = 0.15F;
```

### 6.6 Clerics colder to the seven's sworn (`Faiths.minds`)
If `Accusations.priest(who)` and the player's patron is one of the seven, the final value is times `CLERIC_COLD` (1.5),
so priests' prices and hearts are worse for them. Clerics already sell to the Truth's flock cheaper (`Blessings.flock`).

---

## 7. Realms (`RealmFaiths.java`, `Faiths.java`, `GreatWars.java`, `Standing.java`)

### 7.1 State patron (`RealmFaiths.patron(MinecraftServer, Faction)`, never null for a non-barbarian faction)
1. Barbarian camp: null (as now).
2. `GreatWars.creed(server, f) == FAITH`: **The Truth**.
3. Else the realm's stored count, if any (`Holdings.patrons`, `realm id -> key`).
4. Else the fallback: a haven (`Havens.of(server, f)` non-empty) of one blood takes `Faiths.lean(Heritage.Kind.byKey(haven))`.
   A mixed haven under an orc king (`Havens.orcRuled`) takes the Anvil-Father. Any other mixed haven takes one of the four
   bloods' leans, `{DEEP_MOTHER, ANVIL_FATHER, CROWNED_SUN, GREEN_HUNTSMAN}[floorMod(f.id() * 31 + f.seat().hashCode(), 4)]`.
   A non-haven realm takes The Truth.

**Daily count** (`RealmFaiths.tick`, every `CENSUS_EVERY`, once per `Production.day`): for each faction that is not
fallen and not barbarian, with its seat ticking, count `Faiths.of(e)` over
`level.getEntitiesOfClass(LivingEntity.class, new AABB(f.seat()).inflate(CENSUS_RANGE, CENSUS_HEIGHT, CENSUS_RANGE), x -> x.isAlive() && Faiths.holds(x) && !Accusations.priest(x) && (!(x instanceof Folk folk) || folk.factionId() == f.id()))`.
With at least `CENSUS_MIN` counted, the patron with the most is stored if its share is at least `STATE_SHARE`.
Otherwise the fallback is stored. An unloaded seat keeps its last count. In practice a human realm comes out as The
Truth (94% of humans hold it), a Dawnborn town as the Crowned Sun, and a haven as its blood's patron.

Attachment, on the Overworld (`Factions.level(server)`):
```java
record Holdings(Map<String, String> patrons, List<Integer> holy, long lastDay) { EMPTY; CODEC (all optionalFieldOf) }
static final AttachmentType<Holdings> HOLDINGS = AttachmentRegistry.create(JaymosKingdoms.id("realm_faiths"), b -> b.persistent(Holdings.CODEC));
```
`RealmFaiths.register()` is called from `JaymosKingdoms` at init, so the attachment type is created before worlds load
(as `GreatWars.LEDGER` is). `GreatWars.Ledger` is left alone.

### 7.2 `Faiths.realmFaith` (`Faiths.java:227`)
```java
record RealmFaith(Patron patron, boolean seven, float weight) {}
static @Nullable RealmFaith realmFaith(MinecraftServer server, @Nullable Faction f) {
	Patron p = RealmFaiths.patron(server, f);
	if (p == null) return null;
	return new RealmFaith(p, p.seven, GreatWars.creed(server, f) == GreatWars.Creed.FAITH ? FAITH_CREED_HARSH : 1.0F);
}
```
`Blessings.truthRealm` (`rf.patron() == TRUTH`) is unchanged. A haven now holds a named patron, so a haven of the Ash
Whisperer is cool to the Crowned Sun's sworn as a named rival.

### 7.3 Reputation (`Faiths.reputation`, called from `Reputation.change:72`)
This replaces the clash-only body:
```java
Patron mine = Patrons.of(player); RealmFaith rf = realmFaith(...);   // null either: return by
Patron theirs = rf.patron();
float m = 0;
if (Turning.trial(player, theirs) > 0) m = TRIAL_MINDS * Turning.trial(player, theirs);          // the realm's faith you left
else if (theirs == mine) {                                                                           // (0.7.513) Andy: those who share your patron like you more
	return by > 0 ? Math.max(1, Math.round(by * (1 + REP_SHARED_GAIN * rf.weight()))) : Math.round(by * (1 - REP_SHARED_EASE));
}
else if (mine.rivals(theirs)) m = (mine.rival == theirs || theirs.rival == mine ? RIVAL_MINDS : FAITH_RIVALRY);
if (theirs == Patron.TRUTH) m += Turning.scars(player, Patron.TRUTH) * Turning.SCAR_TRUTH_MINDS;
if (m <= 0) return by;
m *= rf.weight();
// then the existing gain cut / loss growth with m
```
Masked (`Identities.hidden`) still returns `by` first.
```java
static final float REP_SHARED_GAIN = 0.15F, REP_SHARED_EASE = 0.15F;
```

### 7.4 Villagers (`Faiths.minds`, `kinHearts`, `prompt`)
`minds(who, whom)`: keep the top (masked, Lantern Warden vs vampire, `theirs == null || theirs == mine -> 0`). Then
compute `m` instead of returning:
```java
float m = mine.rival == theirs ? RIVAL_MINDS : mine.rivals(theirs) ? FAITH_RIVALRY * weight : 0.0F;
if (whom instanceof Player p) {
	m = Math.max(m, Turning.trial(p, mine) * Turning.TRIAL_MINDS * weight);      // (0.7.513) the old patron's faithful know an apostate
	if (mine == Patron.TRUTH) m += Turning.scars(p, Patron.TRUTH) * Turning.SCAR_TRUTH_MINDS;
}
if (Accusations.priest(who) && theirs.seven) m *= CLERIC_COLD;
return m;
```
Every cost point reads it, so an apostate pays more, warms slower, is searched and halted more by guards of the old
faith, and gets cooler neighbours.

`public static int kinHearts(Villager v, ServerPlayer player, int gained)`: `gained > 0`, not masked,
`of(v) == Patrons.of(player)`, and `random < KIN_HEART_CHANCE` gives `KIN_HEARTS`, else 0. `McaDialogueMixin:495`:
`int warm = Blessings.flockHearts(...) + Faiths.kinHearts(villager, player, gained);`.
```java
static final float CLERIC_COLD = 1.5F, KIN_HEART_CHANCE = 0.5F;
static final int KIN_HEARTS = 1;
```

`prompt(villager, player)`: if `Turning.trial(player, mine) > 0`, append `prompt.jaymoskingdoms.faith.apostate` (mine)
instead of the rival line.

### 7.5 Holy wars (`GreatWars`)
`RealmFaiths.holy(server, a, b)` gives points: `HOLY_NAMED` (2) for a named rival pair (Sun and Ash Whisperer, Mother and
Anvil-Father), `HOLY_TRUTH` (1) for The Truth against one of the seven, else 0. Same patron: -1 (kindred).
- `stir` (`GreatWars.java:345`), after the trade gain and before the alliance override:
  ```java
  // (0.7.513) faiths at odds: holy-war friction (Andy: humans are in a constant battle with the other ideologies)
  delta += RealmFaiths.holyTension(server, a, b);
  ```
  `holyTension` is `holy * HOLY_GAIN * (either side's creed == FAITH ? HOLY_FAITH_CREED : 1)` for holy > 0, and
  `-KINDRED_FAITH_EASE` for the same patron. Not times wealth: faith is not about money. The alliance and truce
  overrides still apply after it.
- The rivals line: when the existing `RIVAL_AT` branch fires and `holy > 0`, announce
  `message.jaymoskingdoms.holy.rivals` (names and patrons) instead of `creed.rivals`. This is world news through
  `KingdomEvents.announce`, as the creed lines are now.
- `begun(server, war)` (455): `boolean holy = RealmFaiths.holy(server, a, d) > 0;`. If holy, skip the wealth return
  (a holy war is always great) and call `RealmFaiths.markHoly(server, war.id())`. Announce
  `message.jaymoskingdoms.holy_war.begun` (a, its patron, d, its patron) instead of `great_war.begun`. The rest
  (horn, hire, rally) is unchanged.
- `rally`: pass `holy` into `lean(server, h, friend, foe, now, extra)`, with `extra = holy ? RealmFaiths.rally(server, h, friend, foe) : 0`
  added before the `>= 2.0` threshold. `rally` is `HOLY_RALLY` (3.0) if h shares `friend`'s patron and that patron
  rivals `foe`'s, `HOLY_RALLY_RIVAL` (1.5) if h's patron only rivals `foe`'s, else 0.
- `ended`: `RealmFaiths.unmarkHoly(server, war.id())`. `daily`/`forgetEnded`: `RealmFaiths.forgetEnded(server, live)`
  drops ids of wars that are gone. There is no conversion of state patrons on a win in (b) (the Faith creed's existing
  `converts` already makes the loser Faith-creed, and so of The Truth).
- `public static boolean holyWar(MinecraftServer, int war)` is for part (c) (champions in a holy war).

```java
static final int CENSUS_EVERY = 1200, CENSUS_MIN = 6, HOLY_NAMED = 2, HOLY_TRUTH = 1;
static final double CENSUS_RANGE = 64.0, CENSUS_HEIGHT = 24.0;
static final float STATE_SHARE = 0.5F, HOLY_GAIN = 2.0F, HOLY_FAITH_CREED = 1.5F, KINDRED_FAITH_EASE = 1.0F;
static final double HOLY_RALLY = 3.0, HOLY_RALLY_RIVAL = 1.5;
```
For scale: The Truth against a haven of the seven gains +2 a day (+3 with a Faith creed) on top of creed friction,
so `RIVAL_AT` 35 comes in under three weeks from that alone, and sooner with the creeds. Sun against Ash gains +4 a day.

### 7.6 Showing a realm's faith (`Standing.java`)
In the ruler's lines, after the temper line: `standing.jaymoskingdoms.faith` (`Patrons.coloured(patron)`) with hover
`standing.jaymoskingdoms.faith.hover`. This is on the existing MCA interact info screen, with no chat.

---

## 8. Screens (client)

### 8.1 `PatronScreen` (the shrine's screen and the skill screen's)
- **List rows:** a patron you hold scars of gets small `Ui.BAD` dots at the right end of its row (one 2x2 per scar, up to
  3, 3 px apart). A patron whose trials are on gets a 2 px `Ui.BAD` bar at the right edge of the row.
- **Header:** if sworn and the streak is toward another patron, the standing line reads `gui.jaymoskingdoms.patrons.turning`
  (to, days, SWEAR_DAYS) in `Ui.BAD`. If any trial runs, right-align on the title line (y + 7)
  `gui.jaymoskingdoms.patrons.trials` (the longest days left) in `Ui.BAD`. The tier name uses tier 0's key.
- **Detail**, for any known patron, after the domain line and before the offerings, when you have marks of it:
  - Heading `gui.jaymoskingdoms.patrons.marks` (`Ui.TEXT`).
  - With scars: `gui.jaymoskingdoms.patrons.scarred` (n), `Ui.BAD`, then `gui.jaymoskingdoms.patrons.scar.<key>` (`Ui.DIM`, indented 6).
  - With a live trial: `gui.jaymoskingdoms.patrons.trial` or `.trial.eased` (days left), `Ui.BAD`, then
    `gui.jaymoskingdoms.patrons.trial.<key>` and `gui.jaymoskingdoms.patrons.trial.shunned` (patron), `Ui.DIM`, indented 6.
  - With a trial that is not eased: `gui.jaymoskingdoms.patrons.trial.rite_hint`, `Ui.HINT`.
- **Detail, warning:** for a known patron that is not yours while you are sworn:
  `gui.jaymoskingdoms.patrons.turn_warning` (p, mine), `Ui.HINT`, after the rivals line.
- Days left: `mark.day() - Patrons.day(minecraft.level.getOverworldClockTime())`, with a new
  `public static long day(long clock)` on `Patrons` (`Shrine.DAY` is package-private).
- Scrolling already uses `contentH`, so the extra lines scroll.

### 8.2 `RiteScreen`: section 5.5. `BoonScreen` and `PatronKeys` keys: unchanged.

---

## 9. Lang (`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`)

Add these. Remove `message.jaymoskingdoms.shrine.strange`.
```json
"gamerule.jaymoskingdoms.patron_trial_days": "Days of trials after turning from a patron (0: none)",
"gamerule.jaymoskingdoms.patron_scars": "Turning from a patron leaves a lasting scar",

"patron.jaymoskingdoms.tier.0": "Unproven",

"message.jaymoskingdoms.shrine.turning": "%1$s hears you, and %2$s sees it. (%3$s of %4$s days)",
"message.jaymoskingdoms.shrine.turned": "You are sworn to %1$s. %2$s marks you.",
"message.jaymoskingdoms.shrine.cold": "%s's shrine is cold to you.",
"message.jaymoskingdoms.trial.day": "%1$s's omens follow you. (%2$s days left)",
"message.jaymoskingdoms.trial.over": "%s's omens leave you. The scar stays.",
"message.jaymoskingdoms.rite.done": "The rite is done. %s's omens grow dim.",
"message.jaymoskingdoms.rite.poor": "You can't pay the cleric.",

"gui.button.act.rite": "Leaving Rite",
"talk.jaymoskingdoms.rite.refuse": "Not for you. Find yourself another cleric.",

"gui.jaymoskingdoms.rite.title": "Leaving Rite",
"gui.jaymoskingdoms.rite.trial": "%1$s's omens follow you: %2$s days left.",
"gui.jaymoskingdoms.rite.streak": "You are praying your way from %1$s to %2$s.",
"gui.jaymoskingdoms.rite.after": "After the rite: %s days, and the omens dulled by half.",
"gui.jaymoskingdoms.rite.after_streak": "When you turn, the trials last %s days, and the omens are dulled by half.",
"gui.jaymoskingdoms.rite.scar": "The scar stays. No rite takes it away.",
"gui.jaymoskingdoms.rite.price": "Price: %s",
"gui.jaymoskingdoms.rite.cannot": "You can't pay that.",
"gui.jaymoskingdoms.rite.pay": "Pay",
"gui.jaymoskingdoms.rite.leave": "Not now",

"gui.jaymoskingdoms.patrons.turning": "Turning to %1$s: %2$s of %3$s days. Your patron will mark you.",
"gui.jaymoskingdoms.patrons.trials": "Trials: %s days",
"gui.jaymoskingdoms.patrons.marks": "Your marks",
"gui.jaymoskingdoms.patrons.scarred": "Scarred for leaving (x%s):",
"gui.jaymoskingdoms.patrons.trial": "Trials: %s days left",
"gui.jaymoskingdoms.patrons.trial.eased": "Trials, eased by a rite: %s days left",
"gui.jaymoskingdoms.patrons.trial.shunned": "%s's faithful shun you, and their shrines are cold.",
"gui.jaymoskingdoms.patrons.trial.rite_hint": "A town cleric can ease the trials, for a price.",
"gui.jaymoskingdoms.patrons.turn_warning": "Swear to %1$s and %2$s will scar you for good and send its omens after you for days.",
"gui.jaymoskingdoms.patrons.scar.hearthkeeper": "Every wound heals a little slower.",
"gui.jaymoskingdoms.patrons.scar.anvil_father": "Tools wear a little faster; the anvil's perfect zone is narrower.",
"gui.jaymoskingdoms.patrons.scar.deep_mother": "Your breath runs out a little faster under water.",
"gui.jaymoskingdoms.patrons.scar.green_huntsman": "Wild beasts notice you from farther off.",
"gui.jaymoskingdoms.patrons.scar.lantern_warden": "Your blows on the dead land weaker, and the dead see you farther at night.",
"gui.jaymoskingdoms.patrons.scar.crowned_sun": "Guards halt you more often; Valor feats are worth less.",
"gui.jaymoskingdoms.patrons.scar.ash_whisperer": "Locks are tighter and pockets slower for you.",
"gui.jaymoskingdoms.patrons.scar.truth": "Those of the Truth think less of you, and your accusations carry less weight.",
"gui.jaymoskingdoms.patrons.trial.hearthkeeper": "Your wounds heal far slower.",
"gui.jaymoskingdoms.patrons.trial.anvil_father": "Tools in your hands wear faster, and your smithing slips.",
"gui.jaymoskingdoms.patrons.trial.deep_mother": "Storms find you, lightning is drawn to you, and the water pulls you under.",
"gui.jaymoskingdoms.patrons.trial.green_huntsman": "Wild beasts see you sooner and turn on you.",
"gui.jaymoskingdoms.patrons.trial.lantern_warden": "At night the dead see you from afar and come for you.",
"gui.jaymoskingdoms.patrons.trial.crowned_sun": "Guards halt and search you more, and good name comes slower.",
"gui.jaymoskingdoms.patrons.trial.ash_whisperer": "Witnesses see you from farther, and the guards hunt you longer.",
"gui.jaymoskingdoms.patrons.trial.truth": "The Truth's townsfolk shun you, and the clerics call you out.",

"speech.jaymoskingdoms.sermon.0": "Hear me! There is one Truth, and it does not hide in stone and candle smoke.",
"speech.jaymoskingdoms.sermon.1": "The seven are old fears given names. Leave their shrines to the moss.",
"speech.jaymoskingdoms.sermon.2": "The hearth will not feed you and the anvil will not mend you. The Truth asks only honesty.",
"speech.jaymoskingdoms.sermon.3": "They light candles to the Lantern Warden and still fear the dark. We walk in plain sight.",
"speech.jaymoskingdoms.sermon.4": "The sun rises because it rises. It wants no praise from you!",
"speech.jaymoskingdoms.sermon.5": "Swear to the seven and they own you. Leave them and they mark you. The Truth marks no one.",
"speech.jaymoskingdoms.sermon.shrine.0": "Another altar to %s. Who keeps putting these up?",
"speech.jaymoskingdoms.sermon.shrine.1": "This stone is %s's. It will not answer you, child.",
"speech.jaymoskingdoms.sermon.shrine.2": "Pray to %s if you must. The Truth will still be here when it fails you.",
"speech.jaymoskingdoms.sermon.apostate.0": "You turned your back on the Truth. Everyone here will know it.",
"speech.jaymoskingdoms.sermon.apostate.1": "Apostate! Walk on.",
"speech.jaymoskingdoms.sermon.apostate.2": "The Truth does not forget those who leave it.",
"speech.jaymoskingdoms.sermon.home.0": "Welcome home. The old one's omens will pass.",
"speech.jaymoskingdoms.sermon.home.1": "The old one will chase you a while. Come to me if its omens grow heavy.",
"speech.jaymoskingdoms.sermon.home.2": "You chose well. I can ease what follows you, if you ask.",
"speech.jaymoskingdoms.sermon.heathen.0": "Sworn to %s, in our streets. Come and talk with me some day.",
"speech.jaymoskingdoms.sermon.heathen.1": "%s takes, child. The Truth gives.",
"speech.jaymoskingdoms.sermon.heathen.2": "You carry %s's mark. Put it down, and I will help you.",
"speech.jaymoskingdoms.shun.0": "Don't look at them.",
"speech.jaymoskingdoms.shun.1": "That's the one who turned from the Truth.",
"speech.jaymoskingdoms.shun.2": "Keep walking, apostate.",
"speech.jaymoskingdoms.rite.done.0": "Go in peace. The omens will follow you less, and not for long.",
"speech.jaymoskingdoms.rite.done.1": "It is done. The mark stays: that is yours to carry.",
"speech.jaymoskingdoms.rite.home.0": "Welcome back to the Truth. Let the old one rage.",
"speech.jaymoskingdoms.rite.home.1": "It is done. Stand in plain sight, and its omens will fade.",

"prompt.jaymoskingdoms.faith.apostate": "This traveller turned away from %s, your faith, not long ago. You want little to do with an apostate.",

"standing.jaymoskingdoms.faith": "Holds to %s",
"standing.jaymoskingdoms.faith.hover": "The realm's faith, from its folk. Those who share it think better of you; its rivals think worse.",

"message.jaymoskingdoms.holy.rivals": "%1$s (%2$s) and %3$s (%4$s) cannot share the world: their faiths are at odds.",
"message.jaymoskingdoms.holy_war.begun": "A HOLY WAR! %1$s, for %2$s, against %3$s, for %4$s. The faithful will take sides.",

"gui.jaymoskingdoms.story.hint.89": "Swear to a new patron and the old one marks you: a scar that never fades, and days of trials in which its omens follow you.",
"gui.jaymoskingdoms.story.hint.90": "A town cleric can ease the trials of one who turns away, for a price. The scar stays."
```
`shrine.turning` args: `coloured(shrine)`, `coloured(mine)`, days, `SWEAR_DAYS`.

---

## 10. MODLOG text

`kingdoms/MODLOG.md`:
```
## 0.7.513: patron gods, part (b): turning away, clerics, realms (pack 0.20.14)
`docs/Patrons-B-Plan.md`. Andy: turning away should be harsh, "the harsher the better for this pack".
- **Turning away** (`Turning`): pray three days at another patron's shrine while sworn and you turn. Each of those days
  costs your patron 5 favour. The old patron leaves a **scar**, a small penalty in its domain that never fades and
  stacks with each leaving. Then come the **trials**, 7 days (gamerule `jaymoskingdoms:patron_trial_days`, 0 for none;
  `jaymoskingdoms:patron_scars` false for no scar): the old patron's omens follow you. Hearthkeeper: wounds heal slow.
  Anvil-Father: tools wear faster and smithing slips. Deep Mother: storms find you, bolts fall near you, the water pulls
  you under. Green Huntsman: wild beasts turn on you. Lantern Warden: the dead come for you at night. Crowned Sun: guards
  halt and search you more and good name comes slower. Ash Whisperer: witnesses see farther, guards hunt you longer.
  The Truth: townsfolk shun you and clerics call you out. The old faith's people mind you hard and its shrines are cold.
  The new patron's favour starts at 2 and can fall to nothing during the trials. A mark is left for part (c)'s champion hunt.
- **Clerics** (`Sermons`, `Rites`): clerics preach The Truth against the seven. Evening sermons on the square of Truth
  realms and villages (a bell, then speech bubbles), words at shrines given to one of the seven and to those sworn to
  them, apostates denounced. They are colder to the seven's sworn. The **Leaving Rite** (a cleric's Interact page) halves
  the trials and dulls the omens, during a turning or after it. It costs 8 emeralds' worth in the town's coin, 4 more a
  scar, double to leave The Truth and half to come to it. It never takes a scar.
- **Realms** (`RealmFaiths`): Faith-creed realms hold The Truth. Others hold the patron most of their folk do (counted
  daily). A haven holds its blood's patron. The ruler's info shows it. Folk who share your patron like you more (good name
  won 15% faster, lost 15% slower, now and then a heart more). The faith you left and its rivals like you less.
- **Holy wars** (`GreatWars`): realms of rival patrons grow tense each day (a named rival pair faster, more under the
  Faith creed). A war between them is a holy war: always a great war, and realms of the same faith rally to it.
- Screens: the patrons screen shows your scars and trials. The rite has its own screen. Loading hints 89-90.
- Powers 0.20.14: the Hearthkeeper's trial and scar slow every part heal.
- Not compile-checked yet at this entry; not played.
```
Root `MODLOG.md`:
```
## 0.20.14
- (Kingdoms 0.7.513) `Wounds.healPart` reads Kingdoms' `Patrons.blessing(player, "wounds_slow")` by reflection
  (`MedicineSkill.blessing`): the Hearthkeeper's trial and scar on one who turned from it slow every part heal.
```
Docs to touch when built: `docs/Otectus-Plan.md` row 11 ("(b) built in Kingdoms 0.7.513 (`docs/Patrons-B-Plan.md`)").
In `docs/Abilities.md`, a new row 482, "Everyone (Kingdoms) | Interact page on a cleric | **Leaving Rite** | (Kingdoms
0.7.513) While turning to another patron or in its trials: pay the cleric to halve the trials and dull the omens; the
scar stays." Row 479 (shrine controls) gains: "Three days at another patron's shrine while sworn turns you away (a
scar, then trials)."

---

## 11. Build order and tracks (two at a time, own worktrees, no shared files)

- **Track A (turning, core and client):** `Turning`, `Rites`, `Patrons`, `Shrine`, `Blessings`, `Theft`, `Senses`,
  `disasters/Storms`, `mixin/LivingEntityHealMixin`, `InteractActions`, `JaymosKingdoms` (all three register lines),
  client `PatronScreen`, `RiteScreen`, `PatronKeys`, `StoryHoldScreen`, **all of the lang**, `kingdoms/gradle.properties`,
  `kingdoms/MODLOG.md`, and Powers `Wounds.java`, `gradle.properties` and root `MODLOG.md`.
- **Track B (realms and clerics):** `RealmFaiths`, `Sermons`, `Faiths`, `GreatWars`, `Standing`,
  `mixin/mca/McaDialogueMixin`. B codes against Track A's `Turning` API exactly as in sections 3.4-3.5
  (`trial`, `scars`, `anyTrial`, `TRIAL_MINDS`, `SCAR_TRUTH_MINDS`). Merge B after A. B's lang keys are in section 9,
  which A adds.
- Build `kingdoms/` (`./gradlew build`) and the root. Check against the 26.3 sources (done for this plan):
  `ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT`, `Entity.push(double,double,double)`, `isUnderWater`,
  `setAirSupply`, the `SoundEvents` names in 3.5.2, `Attributes.OXYGEN_BONUS` (min 0, so a negative modifier does
  nothing; the air is taken directly instead), `StreamCodec.composite` arity, and `ComponentSerialization.STREAM_CODEC`.
  Still to check while building: `NeutralMob.setPersistentAngerTarget(EntityReference)` (used by `Encounters`, so it
  compiles), and `GameRules.get(GameRule)` (used by `Storms`). No long dev runs.

---

## 12. Open questions for Andy

1. **Seven days of trials, and favour starting at 2** (no blessing for some days). Harsh enough, or more? The gamerule
   softens it per server.
2. **Each day of praying toward another patron costs your own patron 5 favour.** Keep it?
3. **The rite's price**: 8 emeralds' worth plus 4 a scar, double to leave The Truth, half to come to it. A cleric also
   helps one leave The Truth for the seven, because the brief says "The Truth included". Should a cleric refuse that
   instead?
4. **The elder guardian's face** flashes on screen at the moment you turn. Too much?
5. **Holy-war news** goes in the chat like the creed and great-war lines that are already there (world news, not
   discovery). OK, or bells and sounds only?
6. **A mixed haven's patron** is counted from its folk, or picked from the four bloods' patrons if nobody has been counted
   yet. Fine?
```
