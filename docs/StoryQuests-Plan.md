# Story quests: tales, board work and village life (Kingdoms)

Andy asked whether Mebahel's RPG: Villager Quests and Companions could improve our questing (2026-11-14). That mod is
All Rights Reserved and has no 26.3 build, so nothing of it is used: no code, no quests, no names. This is our own
version of the ideas he ticked, all 29 rows of the pick list
(https://claude.ai/artifact/SEdDPJyz5cuPAvaJ3oA1Hj). His follow-up: "these quests sound witcher 3 style, super in depth
and layered, with multi day or even week involvements. i love it. please expand as you see fit", and "all in one PR".

All code is in `kingdoms/` (package `com.jaymoskingdoms`). Tuning numbers are `static final` constants at the top of
each class.

## 1. What the player sees

- **Tales.** A few villagers with work have more than an errand: a tale. It starts as a talk, not a list. You're told
  what's wrong, you answer, and the tale goes where your answers and your deeds take it: a road to walk, a scene to
  search, people to ask, someone to face. Tales take days. A part ends, the tale rests, and some days on its next part
  comes to you: the asker has more to say (their gold **!** is back), or a runner finds you on the road with a letter.
- **Choices.** Named foes yield when they're beaten. Finish them, or let them go; each ends the tale differently, is
  written in your journal, is remembered by the town, and can decide which later tales you're ever offered. Some of the
  spared come back.
- **Investigations.** Some tales are a wrong with nobody to blame yet. The scene holds clues to pick up and read, the
  town holds people who saw something, and at the end you name who did it. Which of the suspects it was is decided when
  the tale starts, so the same tale isn't the same twice. Name the wrong one and the town pays for it.
- **Finding them.** Tales also start from a drunk's rumour (go and look; nothing is written down until you get there), a
  book or a diary found in a ruin or on a body (reading it is the start), and letters.
- **Board work.** Town boards carry star-rated work: pack hunts, elite targets, incursions to clear, holding the town
  for a night, escorts overland, lamps to set in dark corners, things to build, cave-ins to blast, goods to leave in a
  chest. One to five stars: more and harder foes, better pay. A board grows with the work done from it.
- **Companions.** A few people you meet in tales can join you for good, and a companion you're close to may ask for
  help with their own past.
- **Journal.** A Stories page tells each tale so far, with what you chose. A Past page keeps finished, failed and
  given-up quests. Tick the box on a quest's row to track only that one on screen, with a rough direction.
- **Village life.** Villagers stop and talk to each other where you can overhear, and a town talks about how your
  tales there ended.

## 2. Files

| File | What it does |
|---|---|
| `Tale.java` | A tale as read from `data/<ns>/tales/*.json`: its start, cast, places, steps, endings; conditions and text. |
| `Tales.java` | The engine: starting tales (asker, rumour, book, letter, companion), each player's runs, steps, talks, waits, endings, marks, party sharing, the Stories page. |
| `TaleCast.java` | The people and foes of a tale in the world: set out when you come near, kept in place, yielding, sparing, coming back, joining. |
| `QuestJobs.java` | The new kinds of work, for the board and for tale steps: pack, elite, defend, trek, incursion, lamps, build, blast, stash. |
| `BoardWork.java` | Star-rated work on the town board, and each board's level. |
| `Overheard.java` | Villagers' scenes with each other (`data/<ns>/scenes/*.json`) and a town's talk about your tales. |
| `QuestPast.java` | Finished, failed and given-up quests, for the journal. |
| client `JournalScreen`, `QuestClient` | Stories and Past pages; the pin box on a quest's row tracks it alone on screen. |

## 3. Writing a tale (the data format)

A tale is one JSON file, `kingdoms/src/main/resources/data/jaymoskingdoms/tales/<id>.json`. All its words are in the
file. `python3 tools/pr_checks.py` checks every tale (steps exist, every path ends, roles and places are defined).

```json
{
  "title": "The Name Of The Tale",
  "start": { "by": "giver", "jobs": ["farmer", "shepherd"], "weight": 2, "needs": [], "bars": [] },
  "roll": { "culprit": ["miller", "widow"] },
  "cast": { "<role>": { "name": ["Odo Marrec", "Hale Brandt"], "body": "villager", "job": "farmer", "at": "<place>" } },
  "places": { "<key>": { "at": "wild", "from": "home", "dist": [150, 300], "name": "the old sheepfold" } },
  "first": "<step id>",
  "steps": { "<step id>": { "do": "talk", "...": "..." } },
  "endings": { "<ending id>": { "text": "...", "reward": { "coins": 12 } } }
}
```

### start

- `by`: `giver` (a villager with work offers it), `rumour` (a drunk or a passer-by tells it), `book` (a book you find
  and read), `letter` (a runner brings it), `companion` (a companion you're close to asks).
- `jobs` (giver only): the asker's trade, as villager profession paths (`farmer`, `shepherd`, `fisherman`, `fletcher`,
  `butcher`, `leatherworker`, `mason`, `toolsmith`, `weaponsmith`, `armorer`, `librarian`, `cleric`, `cartographer`,
  `nitwit`, `none`, and MCA's `guard`, `archer`, `miner`, `baker`). Empty or missing: anyone.
- `weight`: how often it's picked against other tales that fit (default 1).
- `needs`, `bars`: conditions (below); all of `needs` must hold, none of `bars`.
- `rumour` (by rumour): what the teller says. `{way:<place>}` gives a direction and a rough distance.
- `book` (by book): `{ "title", "author", "pages": [..], "found": ["ruin", "mob:<entity id or #tag>"], "chance": 0.05 }`.
- `letter` (by letter): `{ "from": "...", "lines": ["..."], "days": [2, 5] }`: sent this many days after its needs hold.

### roll

Values picked once when the tale starts, kept for the whole tale. Use them in conditions (`culprit=miller`) and in
text (`{culprit}`; if the value is a cast role, the text shows that person's name).

### cast

Each role is a person or a creature of the tale. The asker is always the role `giver` (never defined here).

- `name`: a name, or a list to pick one from. Our own names; plain folk names, no titles from other works.
- `body`: `villager` (a townsperson: stands and talks), `fighter` (an armed person: can be fought, yields, can be
  spared), or an entity id (`minecraft:wolf`, `jaymoskingdoms:...`) for a beast or monster.
- `job` (villager): their trade, for clothes. `female`: true or false, if it matters.
- `at`: the place they wait. They're set out when the player first comes near it.
- `if`: a condition (below). Someone who is only in some tellings (`"if": "culprit=wolves"`) isn't set out in the
  others. `late`: true for someone who must not be seen before the step that's about them (a beast met at the end).
  Whoever the open step names as `who` is always set out.
- `health`, `damage`, `armor`: numbers for a fighter or a beast (defaults 40, 5, 4).
- `hand`, `head`, `chest`: item ids they carry and wear.
- `men`: how many stand with a foe; `men_body`: `fighter` or an entity id (default: the same as the foe).

### places

- `at`: `home` (where the asker stood), `town` (their town's seat), `other_town` (the nearest other settlement),
  `wild` (open ground), `road` (on a road out of town, else wild), `ruin` (the nearest ruin or buried treasure, else
  wild), `camp` (the nearest raider camp, else wild), `near` (a few dozen blocks from `from`).
- `from`: `home` (default) or another place's key. `dist`: `[least, most]` blocks from `from` (wild, road, near).
- `name`: what it's called in the journal and in text (`{place:<key>}`).

### steps

Every step has `do`, and may have:

- `goal`: the line shown in the tracker and the journal while it's the open step. Short, an order to yourself.
- `journal`: a line written on the Stories page when the step is finished. Past tense, as the character would write.
- `then`: the next step id, or `end:<ending id>`. May be a switch (below).
- `give`: items handed over when the step starts: `[{ "item": "minecraft:paper", "count": 1, "name": "...", "lore": ["..."] }]`.
- `mark`: a mark set on the player when the step is finished.
- `pay`: coins paid when the step is finished (a part's wages).
- `hidden`: true keeps the tale out of the tracker and journal until this step is done (rumours).

| `do` | Fields | What happens |
|---|---|---|
| `talk` | `who`, `say`, `replies` | The player speaks with `who` (use them). `say` is a text or a list of texts (shown one after another). Each reply: `{ "say", "then", "if", "needs_item": {"item","count"}, "needs_coins": 11, "mark", "journal" }` (a reply that needs an item or coins is greyed out without them, and takes them when picked). No replies: the talk just continues to `then`. A reply whose `then` is another `talk` with the same `who` goes straight on. |
| `go` | `to`, `radius` | Reach the place (default radius 16). |
| `search` | `at`, `clues`, `need` | Clues lie at the place: `[{ "name", "item", "lore": [..], "journal", "if" }]`. Pick up `need` of them (default: all). The clue's lore is what the player reads; its `journal` line goes on the Stories page. |
| `foe` | `who`, `slain`, `spared`, `taunt`, `plea`, `mercy`, `thanks`, `yield_at` | Fight `who`. Dead: `slain`. A fighter yields at `yield_at` of their health (default 0.3) or when downed, says `plea`, and can be spared by talking to them and picking `mercy`, or by walking away: `spared`. Without `spared` there is no yielding. |
| `bring` | `who`, `item`, `count` | Bring so many of an item to `who`. |
| `slay` | `target`, `count` | Kill so many (`minecraft:zombie`, `#tag`, `*monster`). |
| `wait` | `days`, `wake`, `letter` | The tale rests `[least, most]` days. `wake`: `giver` (their **!** returns; talking to them goes on), `letter` (a runner brings `letter`: `{ "from", "lines" }`; opening it goes on), `none` (it just goes on). |
| `defend` | `at`, `waves`, `foes`, `stars` | Hold the town through so many waves after dark. |
| `escort` | `who`, `to` | Walk `who` to the place alive. |
| `pack` | `at`, `foes`, `count`, `stars` | A pack waits at the place; kill them all. |
| `build` | `at`, `blocks`, `count` | Place so many blocks (`minecraft:oak_fence`, `#minecraft:fences`) at the place. |
| `blast` | `at` | A cave-in or a ruined wall at the place; blow it open. |
| `stash` | `at`, `item`, `count` | A chest stands at the place; leave so many of an item in it. |
| `lamps` | `at`, `count` | Light so many dark spots in the town. |

### endings

```json
"mercy": {
  "text": "What the journal says of how it ended.",
  "reward": { "coins": 14, "items": ["minecraft:bread*3"], "hearts": 10, "reputation": 15 },
  "marks": ["spared_marrec"],
  "failed": false,
  "town": ["A line the townsfolk say afterwards.", "Another."],
  "returns": { "who": "rook", "days": [4, 8], "as": "thanks", "say": "...", "gift": ["minecraft:iron_ingot*4"] },
  "joins": "rook"
}
```

- `returns.as`: `thanks` (they find you, say their piece, leave a gift), `warning` (the same, with news), `recruit`
  (they ask to join you; `joins` is implied on yes), `revenge` (they come back armed, with friends).
- `joins`: that role becomes a companion of the player.
- `failed`: true writes it in the journal's Past page as failed.

### conditions and switches

A condition is a string: `culprit=miller` (a roll), `mark:<name>`, `done:<tale id>`, `done:<tale id>:<ending id>`,
`powers` (Jaymo's Powers is loaded). A leading `!` turns it round. Several joined with `&` must all hold
(`mark:spared_robber & !fate=slain`).

Anywhere a text, a `then`, or a number is wanted, a switch may stand instead: an object whose keys are `?<condition>`,
tried in order, with `?` as the catch-all:

```json
"say": { "?culprit=miller": "It was the flour on his boots.", "?": "I saw nothing." }
```

### text

`{player}` the player's name as the speaker knows it, `{giver}` the asker, `{town}` their town, `{<role>}` a cast
member's name, `{place:<key>}` a place's name, `{way:<key>}` a direction and rough distance to it, `{<roll key>}`,
`{coins:11}` a sum of money in the town's own coin (never name a coin yourself: each realm strikes its own).

### rules for the words

- Villagers speak plainly and keep one attitude through a talk. Nothing explains the old war; lore is shown, never told.
- No on-screen discovery text: what the player learns is said by someone, written on a thing they hold, or written in
  their own journal.
- Our own names, places and stories. Nothing from Mebahel's mod, The Witcher or any other work.
- A tale should take days: at least one `wait`, and more than one way to end.

## 4. Scenes (overheard talk)

`data/jaymoskingdoms/scenes/<id>.json`:

```json
{ "jobs": ["farmer", "*"], "when": "day", "lines": [["a", "..."], ["b", "..."], ["a", "..."]] }
```

`jobs`: the trades of speaker `a` and speaker `b` (`*`: anyone). `when`: `day`, `night`, `rain`, or `any`. Two to six
lines. The speakers stop, face each other and say them in turn.

## 5. As built

All 29 picked rows are built, in one PR. Built and compiled; `tools/tale_check.py` passes on every file and runs in
`tools/pr_checks.py`. **Nothing has been played.**

**What ships:** 14 tales (`data/jaymoskingdoms/tales/`), 21 to 34 steps and 5 to 10 endings each, every one resting
for days between its parts (three to five waits in each, 1 to 10 days long), and 40 overheard scenes
(`data/jaymoskingdoms/scenes/`).

| Tale | Starts | What it is |
|---|---|---|
| The Empty Fold | shepherd, farmer, butcher, leatherworker | A fold found empty: a neighbour, den folk or wolves. |
| The Lantern That Went Out | cleric, mason, librarian | A barrow's light is out; a three-night vigil; what to do with the one who took it. |
| Salt and Iron | fisherman, smiths | An investigation between two houses; a wrong name makes it worse. |
| The Night Bell | guard, archer, mason, cleric | Lamps, a palisade, a night defence, then who opened the postern. |
| The Fair Left Behind | nitwit, unemployed, baker, farmer | A child the fair left; why, and where they belong. |
| Hail Glass | librarian, cartographer | A guest's eye-glass is stolen, and three people could have taken it. |
| The Cave-In | miner, mason, toolsmith | Blast a man out, and find out who he was. Someone can join you. |
| The One Who Walks at Moonrise | cleric, butcher, shepherd, farmer | Cursed, a wolf, or a murder dressed as one. |
| The Bride Road | anyone with work | Two escorts and an ambush someone paid for. |
| The Stone That Hums | a rumour | Never explained. |
| A Debt in Potatoes | a ledger found in a ruin chest or on a raider | Smugglers or the watch. |
| What Was Owed | a close companion | Their past: a grave, a widow, a carter's son. |
| The Brother Who Stayed | a close companion | Buried, bitter or outlawed. |
| The Name She Left | a letter, only if you let the bride go | The sequel: her father went into the hills to look for her. |

**How each row was built, and where it differs from the pick list**

- **A1-A3, H1, H2** as planned. A tale is done once per player (any ending, or giving it up); one turned down before it
  began can be offered again. A tale begun again starts with its own marks cleared.
- **A4** The spared come back with thanks (19 endings), a warning (3), asking to join (3) or for revenge with two
  friends (1). Who comes back is fixed per ending, not per choice inside it.
- **A5** New memories `CUT_DOWN` and `MERCY` in Recollections and Bearings, spread by gossip like other deeds.
- **A6** Clues are items lying at the scene with the finding written on them. Accusations at the priest and blood or
  chalk marks as clues were not used: naming is done in talk with the asker.
- **B1** Rumours from passers-by (`Rumours.tell`) and from a night's drinking (`Carousing`).
- **B2** Books are found in ruin chests and on bodies (`start.book.found`). One tale starts this way so far.
- **B3** A runner (a villager) walks up by day under open sky, never a pigeon. If you're indoors or it's night they
  wait for another day. A letter that starts a tale has no placeholders (the tale hasn't begun).
- **C1, C2** Stars are on the notice and the quest title. A board levels every 3 jobs finished from it (by anyone),
  to level 5: one more notice from level 3, more often, harder kinds from levels 2 and 3, a little more pay.
- **C3, C7** Humanoid foes go down before they die, as everywhere: a downed raider has to be finished to count.
- **C4** From board level 3, three stars and up. The pick list said "a Nemesis card once you've met": `Nemesis` is the
  Deck's hunter, so an elite you flee from or fall to marks you through it instead (hunters are sent).
- **C5** Waves come once it's dark and you're in town. Lost if you leave, or if whoever posted it (they ring the bell)
  is downed or killed. Guards and golems fight as they always do; nothing new was added for them.
- **C6** The traveller follows on foot, and climbs into whatever you're riding if it has a seat. One ambush at most, off
  settlement land. No cab wagon is hired for you.
- **C8** Dark spots are picked on the town's own ground first, where `Lamplight` keeps lights. Torches count as well as
  lanterns. Late is failed.
- **D1** Counts blocks of the asked kind standing within 6 blocks of the mark, over what stood there before.
- **D2** The pick list said the town's no-build rules would let this blast through: only tagged no-build dungeons block
  blasts, and work is never set in one, so no exception was needed. Rubble dug by hand comes down again until it's blasted.
- **D3** Any item of the asked kind counts, not only one handed to you by the tale.
- **E1** One ending has someone join outright; three more have someone come back and ask.
- **E2** Asked by a companion whose bond is close. Seen through, the bond rises by 20; ended badly or given up, it
  falls by 12. No ending has the companion leave you.
- **F1** A tracker already existed (three quests). This adds the pin: only that quest, with "far to the north-east"
  instead of numbers. A pinned tale keeps its pin while it rests.
- **F2, F3** Fourth and fifth journal tabs. The two new tabs use item icons (book and quill, clock) until
  `tabs.png` gets art for them.
- **G1** Bubbles in the speakers' own tongue, a voice pitched per villager, a head shake or an arm for the line. About
  one scene every four minutes among folk; the same scene not again until 16 others.
- **G2** Each ending's `town` lines are said to you once each, by different townsfolk, over the next 12 days: a cheer
  from everyone near for the first if it went well, a shake of the head if badly. A town held through the night
  thanks you the same way. A toast at the tavern or a tended grave happens only where a tale's own words say so.

**Left for later (not in the picks):** time-of-day or moon conditions on a step; `returns` that depend on a choice;
a cast member whose body changes with the roll; a tale-only item for `stash` and `build`.
