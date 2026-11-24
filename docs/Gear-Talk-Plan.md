# Gear Talk plan (draft, for Andy's picks)

Status: Andy picked 2026-11-24 (below); step 1 built (`kingdoms/.../GearTalk.java`). Thread "Draft gear-based reactions from people you meet".

**Andy's picks (2026-11-24, 11:13 AM ET):** every tag family in, all six two-tag lines kept. Q1 plain folk say "more
Frostborn work than flesh", only androids, the Mad Tinkerer, Bio-built and Frostborn troopers say "cyborg". Q2 Rig limbs
only. Q3 armour, harness and crest count too. Q4 armour hides a limb unless the villager notices. Q5 masked gear is
talked about, never the blood, and gossip never ties the look to the face. Q6 yes, folk call strangers by their look in
lines and gossip only. Q7 step 1 first. Lore paragraph approved.

**Step 1 as built:** android villagers don't exist as talkable MCA villagers and troopers are mobs, so the "cyborg"
sayers today are the Mad Tinkerer (part `tinkerer`), the future's pilots (`omen_pilot`) and Bio-built villagers.
Sharp-eyed is a fixed roll per villager and player (20%, suspicious 50%), not `Notice` (that is the player's perception).

## 1. The ask, placed

**The problem.** Andy (2026-11-24): "players should get tags for the items / baubles they equip, giving the people they
meet different things to refer to them about, for example, if they encounter a hearthlander wearing frostborn gear,
(arm,leg,scouter,etc) they will ask how you got it or exclaim, you're a cyborg?! the different combinations of tags
should allow for a large flowchart of responses."

Reading taken: "a hearthlander" is the listener (a Heartlander villager) and the player is the one wearing the gear.
The canon spells the people **Heartlander** (Lore.md, "The peoples"); "Hearthkeeper" is their god.

**Who it's for.** Every player, from the first village on. It matters most to players who collect odd gear: anyone with
a Rig limb or a scouter, amputees on wooden or iron limbs, costumed players, players dripping in baubles.

**Where it lives in the lore.** It rests on lines already in the canon:
- Lore.md, "The peoples", Frostborn: "The Frostborn made the scouters, rigs, mechs, prosthetics and the other tech found
  in the otherwise medieval Overworld, and people talk about it: everyone loves having Frostborn tech. They are a proud
  people and usually carry their heritage's inventions, scouters and prosthetics above all".
- Lore.md, "Costumes and masks": costumed vigilantes "are widely disliked"; "Nobody likes either. Folk grumble at a
  vigilante passing".
- Lore.md, "Rules for writing against this": "Villagers speak plainly and keep one attitude through a whole
  conversation. No discovery chat text: sounds and speech bubbles instead." and "things are named as the folk of the
  world would name them".
- Lore.md, "Why the peoples don't like each other": the Frostborn and Emberborn grudge; Warrior-born watched by all.

The new placement paragraph is at the end of this doc (and appended to Lore.md, marked `(new)`).

**What we have to build with** (all seen in the code, see "Confirmed" at the end):
- `kingdoms/.../Talks.java` + `TalkBook.java`: "Have a word" talks. Each row is a **gate** (a test on `Talks.Context`:
  the villager, their blood, the player's blood, faith, job, memories) plus three replies, each moving trust, respect and
  warmth (`Bearings`). This is the flowchart engine already; gear tags become new gates.
- `TalkBook` already has Frostborn-work rows, but only about the **villager's** own gear (`blood_frost_tech`,
  `frost_tech_owner`, `wooden_limb`, `frost_tech_want`) and the player's blood (`frost_tech_yours`). Nothing reads what
  the **player** wears. That is the gap.
- `Speech` (words over the head, fading): for a first-sight remark, per the "no chat text" rule.
- `Recollections` (what each villager remembers of you): so a remark comes once, and a change is noticed.
- `Temperaments` (12 tempers per humanoid) and `VillagerHeritage.of` (22 bloods): the listener's side.
- `FrostTech.wearsWork` / `FrostTech.wooden`: whether the **listener** wears Frostborn work or a wooden limb.
- `Notice`: passive perception, for gear hidden under armour or a cloak.
- `Identities.masked` / `Prejudice.masked`: a costumed, masked player (hides their blood too).
- `PowersCompat.baubles(player)` (already used by Locks, Journal, NightLights, Spoils), `PowersCompat.fittedLimbs`,
  `PowersCompat.missingLimbs`; Powers' `Prosthetics.rigMask` (needs a new `PowersCompat` reader), Powers' `Rig.wears` /
  `Rig.fullSet`, the scouter item `jaymospowers:scouter` in the head slot, Kingdoms' `Harness` (12 harness places) and
  `HeraldryArmour` (a realm's arms on the crest).
- `TalkWheel` (client): answers already show as a wheel, worded by heritage and class (Decisions.md, 2026-11-21).

**Three rules for living things.** Not an animal or plant, but it is people's way of life, so:
- *Sympathy*: folk react the way people do to a stranger's prosthetic, jewels or mask: curiosity, envy, pity,
  suspicion. A scouter really is glass you see far with; a wooden leg really does slow you.
- *Feedback loop*: what you wear changes how a town talks about you, which changes prices and help (Bearings already
  move prices and favours), which changes what you choose to wear into town. Gossip spreads a striking look ahead of you.
- *Stewardship*: looking after people pays: the Heartlander with a wooden leg you told where to find a smith, or the
  Frostborn you let look at your arm, remember it. Wearing nothing odd is never punished; you simply aren't talked about.

**Obstacles.**
- Cost: tags are worked out when a talk or remark is about to happen (one villager, one player), never every tick for
  every villager. No new saved data except "remarked on tag X" in Recollections.
- Lines: a full grid (14 tag families x 22 bloods x 12 tempers) would be thousands of lines. The plan uses a fallback
  chain so a few hundred lines feel like a large flowchart (section 4).
- Kingdoms can't see Powers' classes directly: every Powers read goes through `PowersCompat` reflection, as now.
- Lang lines live in `kingdoms/.../lang/parts/`; a new part file avoids clashing with other tracks.

### Roadmap (by what players notice most)

1. **Simplest first step: Frostborn work + limbs.** Tags `scouter`, `made_limb`, `rig`, `more_made`, `wooden_limb`,
   `iron_limb`, `stump`. One first-sight bubble per villager per tag, and ~12 "Have a word" rows (Heartlander and
   Earthling, Frostborn, Warrior-born, anyone with their own wooden limb, a smith, plus temper fallbacks). Check: a
   test world, a player with a scouter and a Rig arm walks into a Heartlander village; a bubble appears once, the talk
   opens on Have a word, and a second visit doesn't repeat the bubble.
2. **Masks and suits.** `masked`, `hooded`, `suited` (symbiote). Ties to the vigilante dislike already in the canon.
   Check: masked player gets a grumble; the same villager talks plainly once the mask is off.
3. **Baubles by theme.** One data file maps every bauble id to a theme tag (`holy`, `jewels`, `fae`, `sky_made`,
   `beastly`, `grim`, `trade`, `fool`, `lucky`, `brawler`, `delver`). Other mods' items join by item tag. Check: a
   datapack-only addition gives a new bauble a tag with no code.
4. **Armour and arms.** `knight` (full harness, gilded), `realm_arms` (your crest: their realm, a rival, an enemy at
   war), `ragged` (nothing worn). Check: a guard of a realm at war with your crest's realm says so.
5. **Combinations and gossip.** Two-tag lines (section 4), "changed since last time" lines, neighbours passing on what
   they saw, and (if picked) strangers calling you by your look before you're introduced ("the one with the glass eye").

## 2. The sceptic

- **"You're a cyborg?!"** No villager of the medieval Overworld has that word; the canon says things are named "as the
  folk of the world would name them". Plain folk would say "Is any of you still flesh?" or "You're more Frostborn work
  than man!". The word could still be heard from those who'd know it: an android, the Mad Tinkerer, a Bio-built, a
  Frostborn trooper, a future-folk (Los Perrito) speaker. Question 1.
- **"Everyone loves having Frostborn tech."** So the default reaction to Frostborn work must be wonder and envy, not
  fear. Fear is only for the timid and for "more made than flesh". A Frostborn villager reacts differently: pride if
  you're Frostborn, "who fitted that?" if you're not.
- **Tag count isn't the point.** If every bauble got a line, folk would never stop commenting. One remark at a time,
  about the most striking thing, and only once per villager per tag. Commenting on a lucky rabbit's foot every time
  would be noise.
- **Hidden gear.** A Rig arm under a full harness's arms and hands can't be seen. The plan lets armour hide limbs and
  `Notice` decide if it's spotted anyway. Simpler: everything always shows. Question 4.
- **Clash with secret identities.** A masked player's blood is hidden (`Prejudice.masked`). Gear talk must not give it
  away: a masked Frostborn in their people's work gets the outsider lines, not "you're one of us". A unique Rig limb
  could also identify a masked player across towns; Question 5.
- **Clash with "no discovery chat text".** First-sight remarks are speech bubbles (`Speech`), never chat.
- **Too big for one PR.** Split into the five steps above; step 1 is a PR on its own.
- **Missed opportunity.** `TalkWheel` already words your answers by heritage and class, so a Frostborn player's answer
  to "where'd you get that arm?" can read differently from a Heartlander's with no new system.

## 3. Keep what works

Stays as it is:
- `TalkBook`'s rows and their gates; the existing `frost_tech_*` rows stay about the villager's own gear.
- `Talks`' draw: weights, one talk per villager per day, 24 days before a repeat (`TALKS_PER_DAY`, `TALK_REPEAT_DAYS`).
- `Bearings` deltas per reply; `Prejudice` for heritage; `Introductions` for names; `FrostTech` villager gear.

What holds it back: no gate can see the player's gear, and there's no remark-on-sight hook for talk that isn't a ruler
or a witness.

Focused changes:
1. New `kingdoms/.../GearTags.java`: `Set<Tag> of(ServerPlayer player, LivingEntity looker)` reading armour slots,
   `Harness`, baubles, limbs, Rig, mask; `Tag striking(...)` picks the one to talk about; an `enum Tag` with a weight.
2. `PowersCompat`: add `rigLimbs(entity)` (Prosthetics.rigMask) and `rigWorn(player)` (Rig.wears / fullSet), beside
   `fittedLimbs`.
3. `Talks.Context`: add `Set<GearTags.Tag> gear` (worked out once when the talk opens).
4. `TalkBook`: a new block "the player's gear" with rows gated on `c.gear()`.
5. New `GearRemarks.java`: when a player first comes within ~6 blocks of a grown villager who has line of sight, roll
   for one bubble about the most striking unremarked tag (`Speech`), record it in `Recollections`.
6. `data/jaymoskingdoms/gear_tags.json`: item id or item tag -> gear tag, so baubles from any mod join without code.
7. Lang: new part `lang/parts/gear-talk` (keeps clear of other tracks' parts).

## 4. Decisions, like a specialist

### How combinations become a flowchart

Factors: lines to write, how varied it feels, how predictable it is to test, lag.

- **A. Full grid.** Every tag x blood x temper. Most varied, thousands of lines, never finished.
- **B. Ordered rule table with fallbacks (recommended).** Each row names a gear tag (or two), and optionally the
  listener's blood, temper, job, own gear, and the player's blood. The most specific row that matches wins (more
  conditions = more specific; ties drawn at random). If none matches, fall back: two tags -> one tag + blood -> one tag +
  temper -> one tag alone. ~200-400 lines give hundreds of distinct paths, and a new row only ever makes things more
  specific.
- **C. Generated lines** (templates filling in "your {thing}"). Cheap, but every line sounds the same; it breaks
  "villagers speak plainly" less than it breaks charm.

Pick B. What would change it: if Andy wants every blood to have its own voice for every tag, A for step 1 only.

### Where the reactions show

- **Bubble on first sight** (short, once per villager per tag) plus **Have a word** talks with replies (recommended
  both). Bubbles are what players notice; talks are where Bearings move.
- Only talks: quieter, but most players never see it.

### Tag families (draft list)

| Tag | Worn | Read from |
|---|---|---|
| `scouter` | Scouter in the head slot | `jaymospowers:scouter` on HEAD |
| `made_limb` | A Rig arm or leg | `Prosthetics.rigMask` (new reader) |
| `rig` | The Rig suit, any or full set | `Rig.wears` / `Rig.fullSet` (new reader) |
| `more_made` | 3+ pieces of Frostborn work (scouter, Rig limbs, Rig) | derived |
| `wooden_limb` / `iron_limb` | A smith's or carpenter's limb | `PowersCompat.fittedLimbs` + `ironMask` |
| `stump` | A lost limb with nothing fitted | `PowersCompat.missingLimbs` minus fitted |
| `masked` / `hooded` | Costume mask, hood | `Identities.masked` / `hooded` |
| `suited` | The symbiote's suit | `PowersCompat.costumeSuited` (to check: it may be costume-only) |
| `knight` | Full harness, finished or gilded | `Harness`, `HeraldryArmour.finished/gilded` |
| `realm_arms` | A realm's arms on the crest | `HeraldryArmour` crest |
| `ragged` | No armour, no harness, no baubles | derived |
| `holy` | Cross necklace, a patron's symbol | `gear_tags.json` |
| `jewels` | Gem amulet, circlet, ring, bracelet, golden hook | `gear_tags.json` |
| `fae` | Bottled fairy | `gear_tags.json` |
| `sky_made` | Aether gear, cloud in a bottle, featherfall cloak | `gear_tags.json`, `Heavens.carriesAether` |
| `beastly` | Feral or digging claws, kitty slippers, owl mask | `gear_tags.json` |
| `grim` | Withered bracelet, vampiric glove, obsidian skull, thorn pendant | `gear_tags.json` |
| `trade` | Smithing gloves, smelter's bracer, toolbelts, fisher's/alewife/rider's hat | `gear_tags.json` |
| `fool` | Jester's cap, jester's cushion | `gear_tags.json` |
| `lucky` | Rabbit's foot, lucky scarf, heartstone, totems | `gear_tags.json` |
| `brawler` | Brawler's belt, iron knuckles, thrower belts, ram bracer, fire gauntlet | `gear_tags.json` |
| `delver` | Delver's ring, diver's circlet, reed breather, flippers | `gear_tags.json` |

Striking order (who gets talked about first): `more_made` > `suited` > `masked` > `rig` > `stump` > `made_limb` >
`grim` > `scouter` > `knight` > `realm_arms` > `wooden_limb`/`iron_limb` > `sky_made` > `fae` > `holy` > `jewels` >
`beastly` > `fool` > `brawler` > `trade` > `delver` > `lucky` > `ragged`. Claude's order; Andy may reorder.

### Sample flowchart (Frostborn work, step 1)

```
Player in a village. Most striking unremarked tag?
|- more_made (3+ pieces)
|   |- listener Frostborn
|   |   |- player Frostborn, unmasked ...... "Hail's own, head to heel. Who fitted you? I'd know their hand."
|   |   '- anyone else ...................... "That's our work. All of it. Who sold you, or who did you take it off?"
|   |- listener Warrior-born ................ "Frostborn steel on a ground-dweller. They taught us the stars, you know."
|   |- listener Heartlander / Earthling
|   |   |- timid ............................ "Hearthkeeper keep us. What are you?"
|   |   |- greedy ........................... "What did all that cost you? An arm? Ha. Well, two."
|   |   |- devout ........................... "The gods made you whole once. Who made the rest?"
|   |   '- (any) ............................ "Is there any of you left that's flesh?!"
|   '- listener android / Bio-built / Tinkerer  "A cyborg. Haven't seen a proper one since... well." (Question 1)
|- made_limb
|   |- listener has a wooden limb ........... "Mine's oak and it creaks. Yours moves like it was born on you."
|   |- listener smith ....................... "I couldn't forge that in a hundred years. May I look at the joint?"
|   |- listener Frostborn, player not ....... "That arm's Frostborn-made. Who fitted it for you?"
|   '- (any) ................................ "Can you feel things with that? Does it get cold?"
|- scouter
|   |- listener suspicious .................. "What's that glass for? Seeing through walls? Through me?"
|   |- listener patrolman ................... "Good. Nobody should walk about without one."
|   '- (any) ................................ "Is that glass for seeing far? Can I have a look through it?"
|- wooden_limb / iron_limb
|   |- listener has one too ................. "Oak, is it? Mine too. Rain gets in the joint."
|   |- listener smith (wooden) .............. "That peg'll split by winter. I could forge you iron, for a price."
|   |- listener kindhearted ................. "Sit a while, if it pains you. I'll bring a stool."
|   '- listener spiteful .................... "Lost it in a fight, did you? Lost the fight too, by the look."
'- stump (nothing fitted)
    |- listener smith ....................... "There's a smith here can fit you. That's me."
    '- (any) ................................ "You're managing with one? You'd want a peg for that, at least."
```

Each leaf is a bubble line; each also has a "Have a word" talk of three replies, e.g. for Heartlander + `more_made`:
- Open: "Is there any of you left that's flesh? Where does a body even get all that?"
- a "Frostborn friends." -> "Friends like that, I'd like to have." (trust +1, warmth +2)
- b "Off a dead trooper." -> "...Right. I'll not ask where his arms went." (trust -1, respect +1)
- c "None of your business." -> "Fair. Only it's hard not to look." (warmth -1)

### Two-tag lines (examples for step 5)

- `masked` + `made_limb`: "A mask won't hide an arm like that, friend. Half the valley knows it."
- `holy` + `grim`: "A cross on your neck and that skull on your belt. Which one's lying?"
- `jewels` + `ragged`: "Gold on your fingers and holes in your boots. Stolen or saving up?"
- `knight` + `wooden_limb`: "A knight on a peg leg. Must have been some fight."
- `fool` + `realm_arms`: "The king's arms on a jester's coat. Is that meant to be funny?"
- `scouter` + `fae`: "Frostborn glass and a fairy in a bottle. You don't like being in the dark, do you?"

## 5. Check your own draft

Against Andy's words: tags from equipped items and baubles (yes, section 4 table, includes armour and harness too:
flagged as an extension in Question 3); people react by what they see (yes: listener blood, temper, job, own gear);
the Heartlander + Frostborn gear example (yes, the flowchart's first branch, both "how did you get it" and the
exclamation); "a large flowchart" (yes, rule table with fallbacks, section 4).

**Confirmed** (seen in the code or canon today):
- `Talks.Context` fields and `TalkBook.Gate`: `kingdoms/.../Talks.java:63`, `TalkBook.java:30`.
- Existing Frostborn-work talk rows about the villager's own gear: `TalkBook.java:506-517`; nothing in `TalkBook`
  reads `PowersCompat` or the player's gear (grep).
- `PowersCompat.baubles` (`PowersCompat.java:636`), `missingLimbs` (`:4512`), `fittedLimbs` (`:4555`),
  `costumeSuited` (`:2585`).
- Powers: `Prosthetics.rigMask` / `ironMask` / `fitted` (`core/Prosthetics.java:152-170`), Kind WOOD/IRON/RIG; `Rig`
  has `wears`, `wearsAny`, `fullSet` (codemap); scouter item `jaymospowers:scouter` (`ZFighterScouter.java:93`).
- Bauble ids from `data/jaymospowers/tags/item/baubles/*.json`; `Harness.Slot` (12 places); 12 temperaments
  (`Temperaments.java:73`); `Speech`, `Recollections`, `Notice`, `Identities`, `TalkWheel` (codemap).
- Lore quotes from Lore.md sections "The peoples", "Costumes and masks", "Rules for writing against this".

**Assumed or inferred:**
- Players' "Frostborn-made limb" = a Rig limb (villagers' "made" limbs are Kingdoms looks only; the starwrights life
  story gives the player `jaymospowers:iron_arm`, which is smith-grade). Question 2.
- `costumeSuited` covers the symbiote's suit: its name suggests costumes; not opened.
- Striking order and all line text are Claude's.

**Not checked:** no game run; `Rig.wears` signature not opened; how `HeraldryArmour` stores the crest's realm not read;
`Notice.chance` arguments not read.

## Questions for Andy

1. **"Cyborg"**: plain folk say "more Frostborn work than flesh", and the word itself only from androids, the Mad
   Tinkerer, Bio-built and Frostborn troopers? *(recommended)* Or anyone may say it?
2. **Which limbs count as Frostborn work** on a player: Rig limbs only *(recommended)*, or iron limbs too?
3. **Scope of "equipped"**: baubles + limbs + scouter + Rig + mask *(Andy's words)*, plus armour/harness/crest
   *(recommended, it's where knights and realm arms come from)*?
4. **Hidden gear**: armour over a limb hides it unless the villager notices (Notice) *(recommended)*, or everything
   always shows?
5. **Masks**: a masked player's gear is still talked about, but never their blood, and gossip doesn't link a masked
   look to your face *(recommended)*?
6. **Strangers' names**: before introductions, should folk call you by your look ("the one with the glass eye") in
   their lines and gossip? *(recommended yes, lines only, never over your head)*
7. **First patch**: Frostborn work + limbs *(recommended)*, or all tag families at once?

## Lore placement (Andy approved 2026-11-24)

**What folk see on you** (Claude's placement for Andy's gear talk, Andy approved 2026-11-24). Folk read a stranger by what they wear long before they ask a name. Frostborn work is the first thing
anyone sees: a scouter's glass over one eye, an arm that moves like flesh and isn't. Everyone wants some, nobody outside
the Frostborn can say how it works, and a stranger wearing a great deal of it is talked about at the well for days
("more Frostborn work than flesh"). The Frostborn look at it as one smith looks at another's blade: whose hand made it,
who fitted it, and whether it was come by honestly. A wooden or iron limb draws pity from some and fellow-feeling from
anyone else who walks on one. A mask still marks a vigilante, and folk treat it as they treat any vigilante. Jewels, a
priest's cross, a fairy in a bottle or a dead thing's trinket each say something about the one wearing it, and folk say
it back.
