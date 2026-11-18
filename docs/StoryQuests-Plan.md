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
| `Convictions.java` | What a companion truly believes (from their temperament), and their leaving you when a tale's choice goes against it or their bond falls too low. |
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
- `at`: the place they wait. They're set out when the player first comes near it. May be a switch
  (`{ "?mark:fair_in_time": "halt", "?": "winter" }`): where they are when they're set out, and if it changes while
  they stand there (a clock runs out, a mark is set) they walk to the new place.
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
| `talk` | `who`, `say`, `replies` | The player speaks with `who` (use them). `say` is a text or a list of texts (shown one after another). Each reply: `{ "say", "then", "if", "needs_item": {"item","count"}, "needs_coins": 11, "mark", "journal", "against", "returns" }` (a reply that needs an item or coins is greyed out without them, and takes them when picked). No replies: the talk just continues to `then`. A reply whose `then` is another `talk` with the same `who` goes straight on. |
| `go` | `to`, `radius` | Reach the place (default radius 16). |
| `search` | `at`, `clues`, `need` | Clues lie at the place: `[{ "name", "item", "lore": [..], "journal", "if" }]`. Pick up `need` of them (default: all). The clue's lore is what the player reads; its `journal` line goes on the Stories page. |
| `foe` | `who`, `slain`, `spared`, `taunt`, `plea`, `mercy`, `thanks`, `yield_at`, `spared_returns` | Fight `who`. Dead: `slain`. A fighter yields at `yield_at` of their health (default 0.3) or when downed, says `plea`, and can be spared by talking to them and picking `mercy`, or by walking away: `spared`. Without `spared` there is no yielding. `spared_returns`: how they come back if spared (a returns object; `who` left out is this foe). |
| `bring` | `who`, `item`, `count` | Bring so many of an item to `who`. |
| `slay` | `target`, `count` | Kill so many (`minecraft:zombie`, `#tag`, `*monster`). |
| `wait` | `days`, `wake`, `letter` | The tale rests `[least, most]` days. `wake`: `giver` (their **!** returns; talking to them goes on), `letter` (a runner brings `letter`: `{ "from", "lines" }`; opening it goes on), `none` (it just goes on). |
| `defend` | `at`, `waves`, `foes`, `stars` | Hold the town through so many waves after dark. |
| `escort` | `who`, `to` | Walk `who` to the place alive. `"with": ["role", ...]`: others of the cast who keep up with them (set out for the walk even if they're `"late": true`; their own `if` still counts). |
| `pack` | `at`, `foes`, `count`, `stars` | A pack waits at the place; kill them all. |
| `build` | `at`, `blocks`, `count` | Place so many blocks (`minecraft:oak_fence`, `#minecraft:fences`) at the place. |
| `blast` | `at` | A cave-in or a ruined wall at the place; blow it open. |
| `stash` | `at`, `item`, `count` | A chest stands at the place; leave so many of an item in it. |
| `lamps` | `at`, `count` | Light so many dark spots in the town. |
| `rescue` | `who`, `at` | Free `who` (a late villager role), held bound at the place by outlaws, and bring them home. `kin`, `stars`, `failed`. |
| `capture` | `who`, `at` | Bring `who` (a late fighter role) back alive, tied or downed, to the asker. `crime`, `stars`, `failed`, `let_go`. |

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
- `against` (also on a reply): a list of beliefs this goes against: `mercy`, `faith`, `law`, `courage`, `honour`,
  `gain`, `vengeance`, and `self` (only in a companion's own tale: it betrays them). Any companion near the player
  who truly holds one of them (by temperament, `Convictions.java`) leaves them; `self` sends the tale's companion off
  wherever they are. Cutting down a foe who yielded is always against `mercy`, untagged.
- Who comes back is decided by the choices first: a reply's `returns`, or a foe's `spared_returns`, kept till the end
  (a later choice about the same person replaces an earlier one). The ending's `returns` counts only for someone no
  choice spoke for. A foe let go with nothing said comes back by their temperament (spiteful or hotheaded: revenge;
  proud or suspicious: a warning; brave or dutiful: asking to join; the rest: thanks). Nobody comes back from the
  dead, and only a fighter comes back armed or to join (otherwise revenge becomes a warning, joining becomes thanks).
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

**What ships:** 18 tales (`data/jaymoskingdoms/tales/`), 20 to 34 steps and 5 to 10 endings each, every one resting
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
| The Wrong Light | a ship's log on a drowned body | A wreck by a false light, the temple's bell-metal, wreckers who are starving; who sold the ship changes each time. |
| The Fourth Measure | a ledger on a dead witch | Someone buys slow poison for an old man, a measure each new moon; who it is changes each time. |
| The Cold Hearth | a pilgrim's journal on a risen villager | A burned village's hearth-coal and its 22 names, carried to the Hearthkeeper's hall; the daughter's fate changes each time. |
| The Asking Price | an asker (farmer, fletcher, mason, mapmaker, shepherd, fisher) | A daughter taken on the market road for ransom: cut her free at the takers' camp, then bring their leader in alive; sometimes the household's own driver sold her. |

**How each row was built, and where it differs from the pick list**

- **A1-A3, H1, H2** as planned. A tale is done once per player (any ending, or giving it up); one turned down before it
  began can be offered again. A tale begun again starts with its own marks cleared.
- **A4** The spared come back with thanks, a warning, asking to join, or for revenge with two friends. Andy
  (2026-11-14) asked for it by each choice: replies carry `returns` and foes `spared_returns` in all 17 tales, and
  a choice outranks the ending. A foe let go with nothing said comes back as their temperament takes them.
- **A5** New memories `CUT_DOWN` and `MERCY` in Recollections and Bearings, spread by gossip like other deeds.
- **A6** Clues are items lying at the scene with the finding written on them. Accusations at the priest and blood or
  chalk marks as clues were not used: naming is done in talk with the asker.
- **B1** Rumours from passers-by (`Rumours.tell`) and from a night's drinking (`Carousing`).
- **B2** Books are found in ruin chests and on bodies (`start.book.found`). Four tales start this way: a carter's
  tally (ruins, raiders), a ship's log (the drowned), a witch's ledger (witches), a pilgrim's journal (risen villagers).
- **B3** A runner (a villager) comes to find you by day or by night (carrying a lantern after dark), out on the road
  or indoors, so long as you're on your feet, not asleep or riding. If you keep a pigeon roost the birds can find, a
  letter comes by pigeon three times in five instead. One lost on the way is sent again. A letter that starts a tale
  has no placeholders (the tale hasn't begun). Andy asked for both (2026-11-14).
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
  blasts, and work is never set in one, so no exception was needed. Rubble dug by hand comes down once more; after that only a blast clears it.
- **D3** Any item of the asked kind counts, not only one handed to you by the tale.
- **Board work, all kinds:** a party sharing a tale's job shares its count. A build or lamps job is looked at again when
  you bring it back: if it's come undone it isn't taken.
- **E1** One ending has someone join outright; three more have someone come back and ask.
- **E2** Asked by a companion whose bond is close. Seen through, the bond rises by 20; ended badly or given up, it
  falls by 12, and if that takes it below close (the least they'd ask you at), they leave you. Andy (2026-11-14): they
  also leave if you go against anything they truly believe: see `against` under endings, and `Convictions.java`. This
  holds for every companion near you in any tale, not only the one whose tale it is. A sworn greenskin captain who
  leaves goes back to his tribe as one who betrayed you; captains have no temperament, so only their bond and their
  own tale can part them from you.
- **F1** A tracker already existed (three quests). This adds the pin: only that quest, with "far to the north-east"
  instead of numbers. A pinned tale keeps its pin while it rests.
- **F2, F3** Fourth and fifth journal tabs. The two new tabs use item icons (book and quill, clock) until
  `tabs.png` gets art for them.
- **G1** Bubbles in the speakers' own tongue, a voice pitched per villager, a head shake or an arm for the line. About
  one scene every four minutes among folk; the same scene not again until 16 others.
- **G2** Each ending's `town` lines are said to you once each, by different townsfolk, over the next 12 days: a cheer
  from everyone near for the first if it went well, a shake of the head if badly. A town held through the night
  thanks you the same way. A toast at the tavern or a tended grave happens only where a tale's own words say so.

**Left for later (not in the picks):** a cast member whose body changes with the roll; a tale-only item for `stash` and `build`. (Time-of-day and moon conditions came with section 7.)

## 6. Kidnapping (lasso work)

Andy (2026-11-14): with the reworked lasso, a quest kind about kidnapping, saving a hogtied captive or capturing a
criminal, and anyone with a bounty on them can be brought back alive, with more to do with them by temperament. Built
as two more kinds of board work and a new class, `Captures.java`. **Not played.**

- **Taken by outlaws** (`hostage`, `Quest.HOSTAGE`, from board level 2): one of the poster's own (a named daughter, son,
  wife, neighbour...) is held hogtied at an outlaws' camp out in the wild. Their captors (one, and one more a star; a
  ringleader from three stars) are armed townsfolk gone bad (with MCA; an illager band without it) and go for you when
  you're within 20 blocks and in sight. Captors' knots can't be struggled out of. Use held on the captive (not sneaking)
  cuts them free with a blade, axe or shears (1.5 s) or unties them by hand (3.5 s); sneak and use still lifts them, so
  you can carry them out tied. Freed, they follow you home like a traveller (into your cart too). Done once they're
  within 12 blocks of the poster's town, tied or not; failed if they die. Ties don't outlast an unloaded area, so a
  captive who hasn't been freed is bound again when next seen.
- **Wanted alive** (`capture`, `Quest.CAPTURE`, from board level 2): a named criminal ("Edda the Fox", wanted for
  arson) camped out with a man for each star over one. They fight. Bring them back hogtied or downed within 8 blocks of
  whoever posted it, then speak to the poster: they're handed over to the cells. Failed if they die, if you let them go,
  or if you take them into your service.
- **Bounties brought in alive:** a hogtied target now counts as alive for the court's ruler, as a downed one always did.
- **Talking to the wanted** (`Captures`): use a hogtied bounty target or capture criminal with an empty hand. They plead
  in their own tongue and make the one offer their temperament makes, once:

| Temperament | Offer | What it does |
|---|---|---|
| Kindhearted | let go to make amends | untied, bounty off the board; comes back in 3-7 days with emeralds |
| Cheerful | let off, owes you | untied, off the board; comes back in 4-9 days asking to ride with you |
| Greedy | a bribe | pays 1.2x their price in the court's coin and goes free (against honour too) |
| Brave | your service | joins you (MCA follow), off the board; the court thinks less of you (-6) |
| Proud | a fair fight | cut loose and fights you; beaten, the court hears of it (+4); if you go down they walk off |
| Devout | a confession | lies quiet; pays 25% more when handed over |
| Dutiful | comes quietly | lies quiet; the court thinks better of you (+2) |
| Suspicious | who sold them out | lies quiet; tells a rumour (now and then the start of a tale) |
| Timid | where the rest hide | lies quiet; tells where another wanted townsperson was last seen |
| Hotheaded | gagged | knots pulled tight again (the hotheaded work out of knots fastest) |
| Spiteful | swear off revenge | half the time they swear; otherwise, as with any spiteful one handed over, their friends come for you in 6-14 days |
| Gloomy | given hope | lies quiet; the court thinks better of you (+2) |

  Every menu also has "untie them and let them go" (against the law: companions who hold to it leave you; it fails a
  capture job and takes their bounty down) and "leave them". The wanted aren't cut loose by a blade, so a guard stance
  with a sword never frees one by accident. Handed over, each says a last word for their temper.
- Temper also sets how hard a mob works at any hogtie: hotheaded 0.6x as long, brave 0.8x, proud 0.9x, dutiful 1.4x,
  gloomy 1.5x, timid 1.6x.
- **As tale steps** (Andy, 2026-11-14, "add it"): `rescue` (`who`: a villager role, `at`, `kin`, `stars`, `failed`) and
  `capture` (`who`: a fighter role, `at`, `crime`, `stars`, `failed`, `let_go`). The job sets the role out itself at
  its camp, bound or armed, so the role must be `late`. A rescued captive is the tale's own from then on, standing
  where they were brought (the tale's home). A capture is handed over there and then, to the asker (or at the tale's
  home if nobody asked); `let_go` is where the tale goes if you let them go or take them into your service.
  The Asking Price uses both.
- **Not in this:** outlaws who come to a town to carry someone off.

## 7. A real medieval feel (the 2026-11-17 picks)

Andy's 48 picks: twelve changes to how every tale plays, one addition to each of the nineteen tales, and seventeen new
tales. Inspiration only (deadlines that don't wait, people who keep hours, a word that may or may not land, contracts
you prepare for): the stories, names and people are ours, and each new thing is placed in `Lore.md`. The code is
`TaleWays.java` (everything below), called from `Tales.java` and `TaleCast.java`; `tools/tale_check.py` checks every field.

### more conditions

Anywhere a condition goes (`if`, `needs`, `bars`, a switch's `?` key), besides `mark:`, `done:`, `powers` and a roll:

| condition | holds when |
| --- | --- |
| `night`, `day` | the hour (night is dusk to just before dawn) |
| `moon:full`, `moon:new`, `moon:blood` | tonight's moon |
| `human`, `heritage:<kind>` | the player's blood (`heritage:nightborn`); `human` is the five human bloods |
| `cursed` | the blood or the moon has them: a vampire, or one who carries the wolf |
| `patron:<key>`, `patron:none` | who they're sworn to (`patron:lantern_warden`) |
| `reputation>=N`, `reputation<N` | their standing with the tale's settlement |
| `hearts>=N`, `hearts<N` | MCA hearts with whoever the open step is with (its `who`, else the asker) |
| `coins>=N`, `coins<N` | what's in their purse, in the settlement's coin |
| `dress:fine`, `dress:plain`, `dress:ragged`, `dress:armoured`, `dress:bloodied` | how they look: `armoured` is heavy armour, `bloodied` is blood on them (Powers), `fine` is something of the `fine_dress` item tag, `ragged` the `ragged_dress` tag or a piece worn nearly through, `plain` none of fine, ragged or armoured |
| `reads:<tongue>` | they speak that tongue (`common`, `northern`, `southern`, `wildwood`, `blossom`, `old`) |
| `late:<clock>` | that clock has run out |
| `since:<tale>>=N` | that tale ended at least N days ago (false if it never has) |
| `met:<role>` | they've spoken with, or stood near, that one of the cast |
| `companion`, `companion:<belief>` | a companion is with them (one who holds that belief) |
| `exists:<item id>` | that item is in the game (a path that needs something another of the pack's mods makes) |

### time doesn't wait: `within`, `late`, `clock`, `until`

Any step may have `"within": 3` (or `[2, 4]`): the days it stays open. When they're gone the tale goes its `"late"` way
(a step or an ending), whether the player is there or not; the two fields go together. `"until"` is the deadline as the
asker would say it, shown after the goal with the days counted: `Bring the ransom to the mill (before the miller locks
up; by tomorrow)`. A deadline that runs across several steps is a clock: one step starts it with `"clock": { "raid": 3 }`,
later steps say `"within": "raid"`, and `late:raid` is a condition. `0` days is today: it runs out at the next dawn. Text can say `{left:raid}` ("3 days left", "by
tomorrow", "today is the last day": for brackets) or `{in:raid}` ("in 3 days", "tomorrow", "today": inside a sentence).
A step left by its `late` way (or a vigil's `left`, a job's `failed`) is left undone: its `journal`, `mark` and `pay`
don't happen.

### day and night: `when`, `hours`, `not_now`, the `tavern`

`"when": "night"` (or `day`, `moon:full`, `moon:new`) on a step: it can only be done then. Someone to speak with says
their `"not_now"` line outside it (or a stock one), a place only counts by that light, a search finds nothing, a fight
doesn't begin, an escort's walk isn't over; the log says "(after dark)". The steps whose work other code does
(`slay`, `wait`, `await`, `defend`, `pack`, `build`, `blast`, `stash`, `lamps`, `rescue`, `capture`) don't wait for an
hour, and the checker refuses `when` on them: put it on a `go` before them. A cast member with `"hours": "night"` (or
`day`) is only at their place in those hours. When their hours are over and nobody is within 14 blocks, they walk off
(32 blocks, away from the player) and go in once nobody can see them: kept, out of sight and still (a few blocks under
the ground where they went in; where there's no solid ground under them they walk on further first), the same person
(their hearts, what they remember, a lie told to them), and back out when their hours come round and their place can be
read: at their place if nobody of the tale is near it or can see it, else somewhere out of sight 18 to 26 blocks off,
walking there (if nowhere is out of sight they wait, unless the open step needs them now). Someone being followed or walked somewhere (`tail`, `escort` and its `with`), fighting a `foe` step, or held
keeps no hours. When the tale ends, anyone still in goes for good.
A place can be `"at": "tavern"`: the town's tavern (its seat if it has none).

`"candle": true` on any step: when it opens, a black candle is set burning in a window of the town's tavern (the guild's
mark that someone has been paid for; lore "What goes on in the shadows"). One black candle is enough for a window: a
second tale in the same town lights no second one. `"candle": "white"` stands a white one in the same window (the
paper bought back). A tale begun outside any town (a book opened on the road) lights the nearest town's. A `when`
that names a moon (`moon:full`, `moon:new`) means that moon's night: the day before it doesn't count. A cast creature with `"lying": true` is laid
out on the ground where it's set out and doesn't get up; with a condition instead (`"lying": "mark:wl_bell_ninth"`) it
stands until that holds, then it and its `men` lie down one after another while the player is there to see (the
drowned in the burying ground). A `defend` step doesn't read `when` (its waves come after dark): put a `go` with the
`when` in front of it.

An `await` step (one the code carries on) may have `"noted": { "<mark>": "journal line" }`: when the code sets that mark
while the step waits (`Tales.remark`), the line goes in the journal. The Keeping's `keeping_rested` is the first.

### a word that may or may not land: `try`

On a reply: `"try": { "how": "persuade" | "threaten" | "bribe", "odds": -2..2, "coins": N, "helps": [conditions],
"hurts": [conditions], "fail": <then>, "refuse": "what they say" }`. The lot is drawn from the listener's temperament,
their hearts for the player, the player's standing, and what they remember of them; a threat instead weighs armour,
blood, and having cut down someone who yielded. The reply shows how it's likely to go in words: long odds, not likely,
might listen, likely to listen. A try is made once. Failed, the tale goes the try's `fail` way, or the talk goes on
without that reply. A bribe's `coins` are its price before the listener's greed (the greedy ask half again, the proud
won't be bought); `{price}` in the reply's words is what this listener asks, and the coin is only taken if they take it.
Being bought or threatened is remembered.

`"how": "dice"` is a throw against them: `coins` is the stake, won or lost on an even throw (`odds`, `helps` and
`hurts` load it; nothing about the two of you does), and the reply only says it's a throw of the dice.

### naming who did it: `"do": "name"`

`{ "do": "name", "who": <whom you tell>, "say": ..., "suspects": [ { "who": role, "say": "It was {role}.",
"proof": [conditions], "need": 1, "then": ..., "doubted": ..., "mark": ..., "journal": ... } ], "none": { "say", "then" } }`.
Only suspects the player has met are offered, with "I can't say yet". If enough of a suspect's `proof` holds (clues
set marks: a clue may have `"mark"`), it goes `then`; if not, `doubted`. `{named}` is whoever was named. A wrong name
leads wherever the tale sends it, and its ending's `marks` remember it.

### a night's watch: `"do": "vigil"`

`{ "do": "vigil", "at": place, "radius": 12, "gap": 10, "when": "night", "lines": [ { "who": role, "say": ... } |
{ "say": ..., "sound": "minecraft:entity.wolf.growl" } ], "left": <then>, "then": ... }`. While the player keeps near
the place at its hour the lines come one at a time: a cast member's as a speech bubble; the rest (what's seen and
heard) as the tracker's line while the watch lasts and a line in the journal, with a sound if given. A line may have `if` and `mark`. Walking well away once it's begun goes `left`.

### following someone: `"do": "tail"`

`{ "do": "tail", "who": role, "to": place, "when": "night", "near": 6, "far": 28, "seen": <then>, "lost": <then>,
"then": ... }`. At its hour, once the player is within `far` of them, `who` (one of the cast, not the asker) sets off on
foot for the place. Coming nearer than `near` blocks where they can see you (half that, crouched) goes `seen`; falling
more than `far` behind for twelve seconds goes `lost`; still with them when they arrive, the tale goes on, and that's
where they stand from then. Don't give them `hours` that end before the walk does.

### a field to cut: `"do": "reap"`

`{ "do": "reap", "at": place, "count": 16, "crop": "minecraft:wheat" }`. The first time the player comes near the
place a small ripe field stands there (nine by nine, ploughed where the ground is earth, a pool in the middle); the
step is done when `count` ripe plants have been cut in it. The Barley Cut uses it.

### know the beast: `weak_to`

On a foe step: `"weak_to": [ { "know": condition, "item": id or #tag, "share": 0.7, "journal": "why it was easier",
"bestiary": "the note" } ]`. For each one the player has learned (`know`) and carries (`item`, where asked) when the
fight begins, the foe's health and blow are cut to that share (never below 35%) and the journal says why. `"water":
true` instead (or as well) counts while the foe stands in water or within 2 blocks of water that runs, at the start of
the fight. The Greasy Bestiary is taught when the thing is learned: as soon as a mark named by an entry's `know` is set
(by a clue, a reply, a step, a watch's line), its `"bestiary"` line goes in the book, whether or not the thing is ever
carried. Its page is the foe step's `"bestiary_kind"` ("Blood-drinkers"), else the kind of creature the foe is, else
the foe's name; a note to a page.

### companions speak up: `for`, `irks`, `voices`

Replies may say what they stand `"for"` and what they `"irk"` (lists of beliefs: mercy, faith, law, courage, honour,
gain, vengeance). Before the choice, one companion who holds a belief it turns on says so (the step's `"voices":
{ "mercy": "..." }`, or a stock line). After it, companions who held what it stood for are a little closer (bond +3)
and those it irked a little further (bond -3), and one says so. Only a bond emptied that way makes one leave;
`against` is still the grave kind that sends them off there and then.

### it comes back weeks later: `later`

On an ending: `"later": [ { "days": [10, 40], "if": condition, "letter": { from, to, lines }, "town": [lines],
"scene": [["a", "..."], ["b", "..."]], "settles": role, "as": "guard", "grave": [lines], "leaves": "giver",
"mark": "..." } ]`. Days are 10 to 40 (the checker holds it: sooner is a `returns`). On its day the town has its lines
to say, two of its folk have the scene where the player can hear, the cast member comes to live in the town as an
ordinary villager (when the player is next there; `"as": "guard"`, which a `fighter` may do too, as one of its guards),
the mark is set; for a whole party, a settler, a grave or a leaving happens once. What waits is kept apart until it's
done: the letter until a runner or a bird has put it in the player's hand (a runner who can't set out or find them comes
another day); the `"grave"` (a mound beside the town's other graves, or on open ground at its edge, with up to four
lines of 15 letters on a board at its head facing it; only on natural ground of dirt, sand, gravel or snow, with nothing
built, placed or holding water within two blocks) until the town is loaded and a spot will do; `"leaves": "giver"` (the
asker's household packs up and goes, as a household that has had enough does, their house left empty) until the asker
is loaded, awake and in nobody's tale. Some never leave: a child, a ruler or a town's leader, someone's sworn
companion, a captive, a player's family, a visitor, one following anyone. A tale begun where no town was has its town talk, scenes, graves
and settlers in the town nearest the player. A `"to"` on a letter is the name on its outside. Words are filled in when
the tale ends, so they can use its names. `if` is asked when the tale ends (it can use the tale's rolls); `"still"` is
a second condition asked on the day itself, with the tale long over (marks, `done:`, dress: `"still":
"mark:caught:<lie>"` for something that only happens if a lie has come out by then; not rolls, clocks, who was met,
standing or hearts, which the checker refuses).

### letters you can't read: `tongue`, `mark`, `told`

A letter, a clue or a thing a step gives may have `"tongue": "old"`. To a player who doesn't speak it the words are
gibberish. They show once the player speaks the tongue (use the thing), or once a priest or a librarian has read it to
them for a coin (hold it out to one). `"mark"` is set when its true words are known (and `<mark>:aloud` if it was read
to them). `"told"` is what the reader may tell their town afterwards, about half the time (`<mark>:told` if they do). A
letter that starts or wakes a tale does nothing until it can be read.

### lies come out: `lie`

On a reply: `"lie": { "name": "road_took_her", "days": [6, 14], "unless": condition, "town": [lines],
"reputation": 15, "hearts": 40, "returns": { who, as, say, gift } }`. `lie:<name>` is marked at once. On its day,
unless the condition holds by then (asked as `still` is), the lie is out, on the one day for everyone who heard it:
`caught:<name>` is marked, the one lied to remembers it and loses those hearts (when they're next about, if they
aren't then), the town thinks that much less of the player and has the lines to say, and whoever it `returns` comes to
the player's door (as a tale's returns do). Only a townsperson keeps a grudge: `hearts` on a lie told to one of the
tale's own people is refused (they are gone by then, and a settler is someone new). Later tales can ask `mark:lie:<name>` and `mark:caught:<name>`.

### notices, and tales that grow

`"start": { "by": "notice", "notice": { "title", "lines", "hand" } }`: now and then a villager whose trade suits
(`jobs`) nails the tale up on the town board in their own hand. Pulled off, the tale is the player's, the paper with
it, and the poster is its asker (it opens with a talk with `giver`). `"arc": "The Black Candle"` at the top of a tale
lists it under that name in the journal; its parts are linked by `needs` on `done:` and marks, with `since:` to space
them, and each part's start can switch on how the last one ended.
