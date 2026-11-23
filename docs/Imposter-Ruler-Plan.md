# The stolen crown: a ruler's life taken (Kingdoms)

Andy (2026-11-23), after finding Queen Laila chained under her own market stall (a bug, fixed in #2034): "you can have
captive royalty, it just needs to really hide them, and the kidnapper would have to take their place and probably be
easily found out. if that happens the whole kingdom is on alert till they are found, you finding a king or queen this
way should be extremely handsomely rewarded". His picks are in `Decisions.md` (2026-11-23, imposter ruler).

Built on what's there: `Doubles` (stolen lives: the real one held, a copy in their place, slips, `expose`),
`Captives` (chains, freeing, the walk home, the bounty by standing), `Doubts` (a town in doubt), `ShapeshifterAlarm`,
`Elections`/`Rulers` (crowning, regents, `stepDown`), `WarAlert` (a realm on alert), `EstateCharters` (a manor plot),
`TitleChoice` (earned titles), `Post` (news, tidings).

## Track 1: the taking and the vault (PR 1)
- **When**: rarely. A kingdom or fiefdom ruler (a villager, with MCA) on their throne while a player is near the seat,
  at most once per realm in a long while (`ROYAL_GAP`, longer than a village's `TAKEN_GAP`), a small daily roll.
  Never a haven's ruler, never in a war, never a player's own realm.
- **The swap**: the real ruler is saved and taken away (as a stolen life's body is); a copy, made from that body with
  a new identity and `Doubles.COPY` on it, stands where they stood and the realm's leader becomes the copy, so every
  system (talk, desk, court, guards) treats it as the ruler. It keeps the crown and regalia.
- **The vault** (Andy: "really hide them", picked "far, sealed, with a trail"): the real one waits, saved, for a spot
  chosen 220 to 400 blocks from the seat, outside every settlement's land. When a player first comes within reach of the
  spot, a sealed vault is dug there (deeper than a village cellar, no trapdoor showing: the way in is a buried hatch
  under a cairn of stones), the ruler is put in it in chains (a `Captives` captive of their own realm, standing
  `ROYAL`), and two or three of the kidnapper's kind keep it (shapeshifters; a changeling's own keepers are Sylvan).

## Track 2: the tells (PR 2)
- **Court slips**: like a village copy's, but about its court and more often: its heir's or spouse's name, its realm's
  crest beast, the name of its own seat.
- **Odd decrees**: each day there's a chance it decrees something a ruler never would: a sudden toll on its own people,
  guards pulled off the walls, the day's feast cancelled. Townsfolk grumble about it and the news carries it.
- **Its own people notice**: the real ruler's spouse, heir or court wizard tells a player near them that "the
  king isn't himself", and now and then a sharp-eyed guard challenges it to its face; challenged twice, the face slips.
- **Moonlight, a heavy blow, downing it**: the village copies' unmasks, unchanged.

## Track 3: the realm on alert (PR 2)
From the unmasking until the ruler is home ("the whole kingdom is on alert till they are found"):
- **A regent holds the throne**: the real ruler's spouse, else the eldest grown kin, else the best councillor, crowned as
  keeper (a seat of its own, `"keeper"`, that only ends when the ruler comes home, not when they are loaded somewhere).
- **The town in doubt, realm-wide and until found**: every settlement of the realm keeps asking the small things
  (`Doubts`, held rather than timed).
- **Search parties**: a few guards ride out by day toward where the trail points; a player may join one (follow it).
- **Gates shut, trade stops**: strangers aren't let in by its guards, stalls don't sell, caravans don't leave.

## Track 4: the trail, the homecoming and the reward (PR 1)
- **The trail**: the unmasked imposter, fleeing, runs toward the vault, and whoever saw it is told which way it
  went; each dawn after, the realm's news gives a sharper hint (a herder saw strangers digging so far that way).
- **Brought home**: freed and led to their seat as any captive; the copy, if still on the throne, drops its face when
  they arrive (`Doubles.arrived`); the keeper steps down and the real ruler is crowned again (`Rulers.crown`, chosen).
- **The reward** (Andy's picks: a fortune, a title, land): a royal bounty from the treasury (standing `ROYAL`, far above
  a ruler's kin), a title (sworn to that realm: Knight, or Lord if already a Knight; not sworn: an earned title,
  "Saviour of <realm>"), and an estate charter for a manor in that realm. The realm owes the most favours there are.

## Open questions
- Should the imposter use the realm (raise taxes on the treasury, start a war) or only act strangely? Built as only
  strange decrees.
- A player who isn't sworn to the realm: an earned title only, or should the crown offer to swear them in as a Knight?
