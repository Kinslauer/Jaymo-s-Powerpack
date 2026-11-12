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
archers or leaders turn to guarding and shield their archers). Andy called the last two vital, so they're in too:
bands read your parries (Powers' `Guard.PARRIED`) and then strike past the guard and feint; `CatchingUp` lifts a
captain two ranks behind his tribe's best by one rank a day (half the time).

## Patch 4: talk and table (built, same PR)

D2 talk about the world, D3 talk about you, D4 sitting down together at a tavern table or campfire (they ask two or
three questions on a screen: keep or swap their second leaning, talk more or less, how to take a win), with Andy's note:
you share a meal there, and enemy bands do the same in their camps, so you can ambush, poison or confront them at the
fire. C2 leaning shown in the body (idle, victory, pointing at a target before going for it). G1 bond. B3 quest
knowledge, with Andy's note: the more learned companions give the kind of lore tips the loading screen hints give. G2
specialties taught by books from grateful villagers (a tongue-speaker for Languages, field medic, forager, haggler,
packhorse, the quiet one) and foe specialists.

As built: `Remarks` (land kinds by biome name, ruins by structure id, tripwires and pressure plates, town mood from
MCA moods; hurt, starving, new armour, back up; quest kinds remembered at turn-in tell the way; lore tips are the
loading-screen hints; bands warn you off camp ground, name your quest giver, taunt). `TableTalk` and its screen (any
seat by a lit campfire, a firepit or within two blocks of a block named "table"; one companion a day each; the meal is
one bite of your food; a sworn captain's men take his chosen leaning as their second). `CampSuppers` (dusk, 12000 to
13800; sneaking unseen past 4 blocks, first unseen blow x2, poison on the fire within 4 blocks, open approach within 12
calls you out). `Bonds` (0 to 100, close at 60; MCA hearts, the table, being got up with you near; left down 30 s with
you 40 blocks off costs 10; `Bonds.resists` is ready for patch 6's turnings). `Gestures`. `Specialties` (the books
are written books with the knack in their data; foe trackers 8%, medics 6%, tongue-speakers 5%). Added after (Andy:
"Add whats missing"): `GroundSeats` (a companion sits on a free cushion near, or on a cushion of its own put on the
ground and taken away when it stands); a camp without a fire builds a campfire for supper; `LegUps` throws across gaps
(2 to 5 wide, landing within a block up or down, both sides); Powers' `Carry.npcLift` (shoulder carry by mobs, healthy
medics use it).

## Patch 5: hire and lend

A1 companions lent between players (while you're offline or left on loan at a tavern; they come home with what they
learned, a gift and coin), A6 the parting gift and word, A3 veterans for hire on the roads (who also sell to camps),
A5 errands left with a companion, with Andy's note: some are hired to work, not to fight, B2 they know the way
(guidance, short cuts, a chest they once found), H4 rival parties built from the same parts.

As built: `Loans` (owner stamped on every companion; lendable while the owner is offline unless told "keep me", or
when left at a tavern from the table; coin used on one opens the hire screen, fight price 2 + learned steps / 2 +
good words / 2 (capped 5), work half; a day's hire; the parting screen asks the borrower at the end, or when they use
coin on them; words, gifts, coin and wins learned wait on the companion until it meets its owner; errands gather 16 a
day of a raw thing, at most a stack; taken companions: downed, owner 24+ blocks off, none of the owner's side near,
a camp illager within 8; ransom 3 times the price; a close bond (60+) breaks free when the owner comes within 12).
`Veterans` (road drifters 30% veteran, 8% knight, tavern 15% veteran; health 18/24, damage 3/4, 10/24 wins, price
5/10; an unhired one sells to a camp within 192 blocks as a guard of its band, and calls out players it pitched to).
`Wayfinding` (settlements and loot chests within 12 blocks remembered, 32 at most; Go leads to the nearest within 30
degrees of your look, 24 to 800 blocks; chests already opened are forgotten; bands within 64 of camp that lose sight
of you get a short burst of speed). `BandTallies` (damage on players and companions; praise at 6+, +2 health, 3 times
at most). `RivalParties` (3 or 4 MCA guards, a knight or veteran leading, from a road near a player's settlement,
chance 15% every 2 minutes, two at most; camp within 320 or else a settlement within 400; rest 2 minutes in towns;
gone after 3 days with nobody near; the leader's price is the sum of the members'). Not built: short cuts for
companions (only the bands' own-ground run), and the best of a band being sent after you on its next raid (they're
just tougher).

## Patch 6: the dark mirror

H1 turned for a while (sorcerers, a thrall-bite or the Grue seize a companion; knock them down to clear it), H2 the
sickness that rides between companions (the Murmur, Andy approved the name), H3 the masterless
(a reincarnated player's old companion lives on, lost).

## Andy's answers

1. Keys: the four arrows (Andy, 2026-11-12: Come takes Down, crawl goes unbound).
2. H2 (Andy, 2026-11-12): townsfolk the Murmur turns on can die, and the sick can heal themselves at a cleric or priest.
3. A1 (Andy, 2026-11-12): both: lent while you're offline, and when you leave them at a tavern.
