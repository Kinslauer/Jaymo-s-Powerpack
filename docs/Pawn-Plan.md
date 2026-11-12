# Pawn Plan: one brain for friend and foe

Andy (2026-11-12), after Dragon's Dogma's pawns: "give those qualities a checklist we can add to the companions and
combat npcs in the pack. It should be a system that you experience on both sides, friend and foe." He ticked all 30
rows of the pick list (https://claude.ai/artifact/TmzKRwr6dtykEbMQ8xhAgy), friend and foe sides both. Ideas only, from a
commercial game: our own names, lines, code and art.

**The rule.** Every fighting humanoid runs the same parts: a hired sellsword, a realm's guard, a war party's soldier, a
camp's raider, an orc, an illager, a captain's men. Whatever your companion does for you, a raider band does for its
own against you, and you can see and hear it happen.

**What it builds on.** Temperaments (every humanoid's nature, the voice layer), Learners, Drifters, Followers and
Strangers (hired and involved MCA villagers), Speech (bubbles, never chat), Downed (tending, Powers' downed state),
Recollections (memory of players), Captains/CaptainWays (captains already learn from how they lost), RangedTactics,
Languages, Raids/WarParties/Camps.

## Patch 1: the shared brain (this PR)

Rows C1, D1, D5, E1, and the tending half of F1 and F4.

- **Leanings (C1).** Each fighter carries a main and a second leaning (`Comrades`), drawn once from its temperament and
  role and kept (saved, so later patches can shift it): takes on the biggest, hunts bowmen and casters, clears the weak,
  keeps by the leader, minds the fallen, first in, after the spoils (the best-armed foe), backs the others up (focus).
  Leanings only choose *among foes already in the fight*: they never start a fight, never pick a downed body, and leave
  anyone busy (arresting, tending, captive-keeping) alone. A switch needs a clearly better target, so nobody dithers.
- **Sides.** One notion of a side for everyone (`Comrades.side`): a player and the villagers following or under orders
  from them; a camp or realm by faction; an illager raid by camp; guards by the realm they stand in; other mobs by kind.
- **Callouts (D1) and no repeats (D5).** Short bubbles, coloured by side (gold for those on yours, red for those against
  you), in the speaker's temperament (`Callouts`): the target they're taking, a warning when a foe comes at a friend from
  behind, a friend down, going to them, being badly hurt, the fight won. Foes call too: "they're out of arrows", "get the
  one tending them". Every speaker waits between lines, one side says a kind of line once per stretch near any spot,
  quiet temperaments speak less, and nobody repeats a line they said recently.
- **Four quick orders (E1).** Keys (rebindable), no menu: Go = Up arrow, Help = Left arrow, Wait = Right arrow, Come =
  the Down arrow (Andy moved crawl off it). Go: spread out and take foes by leaning, or out of a fight scout a little ahead.
  Come: to me, and fight only what's near me. Wait: stop, hold here, stop attacking (stays until another order). Help:
  get the fallen up, you first; if nobody's down, see to your wounds. One companion answers in a bubble. The orders
  screen (comma) keeps the long orders.
- **Band leaders give the same orders (E1, foe side).** A chief, warchief, captain, pillager captain or war party
  leader in a fight shouts them aloud, and the band visibly does it: Help when one of theirs is down, Come when the
  leader is hurt, Go when they outnumber you. A leader who is down or dead gives none, and the band fights each on its
  own leaning.
- **Field medics (F1, F4).** Fighters who mind the fallen kneel and get their own side's downed up mid-fight, friend
  and foe: a raid you thought won can come back unless you stop the one tending. Companions tend you when you're down.

## Patch 2: hands on (built, same PR as patch 1)

F1 carrying and dragging (Powers' Carry opened to NPC carriers; companions carry you out of danger, foes drag their own
out of reach), F2 holding a foe for you (and two raiders pinning you), F3 a hand up ledges and over gaps, F5 readying
before a fight and ambush set-ups, H5 grabbing loot mid-fight (a goblin runs off with your dropped sword).

As built: medics drag (not lift) the fallen about 9 blocks clear before tending (`FieldMedics`, Powers'
`Carry.npcDrag`); `Grapples` (hold a foe for you, raiders pin you; hit a holder or jump to break free); `LegUps`
(jump at a 2-3 block wall beside a companion; raiders boost each other); `Ambushes` (companions call "ready" and close
up; camp bands crouch and spring); `Scavengers` (spoils leaning grabs weapons, tools, armour; goblins run off).
Left for later: being boosted over gaps, and companions lifting you over a shoulder rather than dragging.

## Patch 3: they learn (built, same PR)

B1 foe knowledge in stars per creature kind for companions, and per player for camps, bands and realms (a camp that
has met you three times knows you parry and feints, knows you shoot and raises shields). B4 catching up, B5 drift
(leanings shift with how you play and with who they travel beside; enemy bands shift with what worked against you), A2
lines that name who taught them.

As built: `FoeLore` (companions' stars per kind, 2/6/14 kills, +8% damage a star, "know"/"know.taught" lines;
bands' knowledge of each player kept on the player, counters from the third meeting, "fights like" another player);
`Learners` past ten steps and catch-up; `LeaningDrift` (orders, kills and travel mates nudge leanings; bands that lose
archers or leaders turn to guarding and shield their archers). Left: a captain who sits out fights catching up, and
bands reading parries and feints (needs a hook from Powers' guard).

## Patch 4: talk and table

D2 talk about the world, D3 talk about you, D4 sitting down together at a tavern table or campfire (they ask two or
three questions on a screen: keep or swap their second leaning, talk more or less, how to take a win), with Andy's note:
you share a meal there, and enemy bands do the same in their camps, so you can ambush, poison or confront them at the
fire. C2 leaning shown in the body (idle, victory, pointing at a target before going for it). G1 bond. B3 quest
knowledge, with Andy's note: the more learned companions give the kind of lore tips the loading screen hints give. G2
specialties taught by books from grateful villagers (a tongue-speaker for Languages, field medic, forager, haggler,
packhorse, the quiet one) and foe specialists.

## Patch 5: hire and lend

A1 companions lent between players (while you're offline or left on loan at a tavern; they come home with what they
learned, a gift and coin), A6 the parting gift and word, A3 veterans for hire on the roads (who also sell to camps),
A5 errands left with a companion, with Andy's note: some are hired to work, not to fight, B2 they know the way
(guidance, short cuts, a chest they once found), H4 rival parties built from the same parts.

## Patch 6: the dark mirror

H1 turned for a while (sorcerers, a thrall-bite or the Grue seize a companion; knock them down to clear it), H2 the
sickness that rides between companions (the Murmur, Andy approved the name), H3 the masterless
(a reincarnated player's old companion lives on, lost).

## Andy's answers

1. Keys: the four arrows (Andy, 2026-11-12: Come takes Down, crawl goes unbound).
2. H2 (Andy, 2026-11-12): townsfolk the Murmur turns on can die, and the sick can heal themselves at a cleric or priest.
3. A1 (Andy, 2026-11-12): both: lent while you're offline, and when you leave them at a tavern.
