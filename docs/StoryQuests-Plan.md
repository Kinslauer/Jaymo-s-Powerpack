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
  given-up quests. Click a quest to track only that one on screen.
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
| client `JournalScreen` | Stories and Past pages; click a quest to track it. |

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
`powers` (Jaymo's Powers is loaded). A leading `!` turns it round.

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

(Filled in at the end of the build.)
