# Plan: Symbiote 1.1.3 picks (Andy, 2026-11-25)

Andy picked 49 of the 54 rows on the pick page (https://claude.ai/artifact/5odt4qNiVyJvYSxQo6apNr, db `picks/andy`).
They are ideas from "Symbiote: A Bonding Experience" 1.1.3 (kitigawa, Forge 1.20.1, All Rights Reserved), so this is
our own code, lines and art, with nothing copied from the jar. Not picked: the arm mining a vein, longer reach, gear
over the shell, the graft, the lab.

Andy's notes on rows:
- **Carapace:** "this should be black's shroud instead". **Aegis (white):** "replaces shroud". So the Shroud tile
  (`WebSymbiote.SHROUD`) is Carapace for the black strain and Aegis for the white. The red strain keeps Shroud. The
  takeover's flight (`takeoverRun`, PR #2162) still goes unseen for every strain, since it is the symbiote running
  with the body, not the tile.
- **Doors:** "since all the large suits are bigger than the 1x2 doors fill, they should automatically congeal through
  the doorways and reconvene whole". The big body and the red slender body squeeze through a door or a 1x2 gap and
  form whole again on the other side (not only takeover walks: any time the big body is driven into one).
- **Sleep walk:** "you can see a timelapse around you when you sleep so the blob should really go around". While you
  sleep, the symbiote leaves your body as its blob, really roams and hunts in the world during the night skip, and
  comes back into you by dawn.

Patches run as two tracks at a time, each its own PR, split so no two tracks edit the same files. Default keys are
unchanged; new moves go on the radial or the skill bar. Tuning numbers are `static final` constants at the top of each
class. Every patch ships `changes/powers/<branch>.md`.

---

## Patch 1: attacks and strain powers (7 rows)
Files: new `WebCarapace`, `WebAegis`, `WebRupture`, `WebNightstep`, `WebOnslaught`, `WebLash` in `power/web`; the
Shroud case in `WebSymbiote.use` (one switch line per strain); `WebTendrilCombos` for the two-foe slam; client poses
in new classes beside `WebCounterPose`.
- **Tendril Lash:** a sweep that whips every enemy within reach once, damage by bond stage; on the skill bar
  (`WebBarMoves`) and the Tendrils chip; satiety cost and cooldown.
- **Carapace (black, the Shroud tile):** the suit hardens for a few seconds and soaks a share of every hit
  (absorption plus damage cut), then softens; cooldown as Shroud's.
- **Aegis (white, the Shroud tile):** a hard shell that throws part of each blow back at whoever struck, from the
  Integrated stage.
- **Rupture pounce (red):** leap onto prey in reach, pin it and stab it several times in a short scene on the
  execution code; a kill this way is carried and fed on as usual (never fed on alive).
- **Nightstep (black):** in the dark (light at both ends at most `NIGHTSTEP_LIGHT`), step from shadow to shadow a few
  blocks off, further at night; on the bar while suited.
- **Crowned onslaught:** at a whole bond in the big suit, tendrils rise round the head and strike in volleys (two a
  volley) for several seconds; spends fury.
- **Two-foe slam reflex:** exactly two foes within reach at once, the instinct grabs both and claps them (the clap
  combo's code), on its own cooldown.

## Patch 2: it saves you (9 rows)
Files: new `WebSymbioteReflexes` (server), called from the instinct tick; Living Armor's damage hook for arrows.
- **Drowning save:** air nearly out, it takes the body and hauls you straight up, breaking ice; gives up after
  `DROWN_GIVE_UP` ticks under a solid roof.
- **Buried alive:** in a suffocating block, the tendrils break it (not bedrock-hard blocks), with `WebCracks`.
- **Powder snow:** freezing, it breaks the snow around you and shoves you out.
- **Lava heave:** in lava, it anchors on the nearest solid shore and heaves you onto it.
- **Ledge pull (white):** walking toward a drop that would kill you, it pulls you back with a line (off while
  swinging, gliding or in the air on purpose).
- **Saves you with your items:** about to die, an arm uses a totem, a golden apple or milk from your bag, once per
  scare, with its line.
- **Arrows turn on the shell:** arrows bounce off Living Armor; now and then one very big hit is capped, at a cost
  in satiety and stress.
- **Feeds you when you forget:** food bar low, it eats food from your bag for you (not meat, which is its own).
- **A goodbye in the void:** one line when you fall into the void.

## Patch 3: its mind (10 rows)
Files: `WebSymbioteMind` (new saved fields), new `WebSymbioteMemory` (memories, fears, first meetings, first places),
`WebSymbioteFidget` (client), lang `web_mind.json`.
- **Temper:** a nature rolled at bonding (watchful, sour, warm, wary), saved, colouring mood and lines.
- **Memories:** a short saved log of moments (starved, held you together, a tool pried out, a request ignored) with
  callback lines now and then.
- **Fear of a kind of foe:** a kind that once hurt you badly is feared; it flinches near it and asks to walk wide;
  fades over time.
- **Jealousy:** another player or a pet close too long (never someone fighting beside you) and it flares: a line and
  a red pull at the screen edge.
- **First meetings:** a line the first time it meets each kind of creature, sometimes a walk over to stare; a line the
  first time it eats one; kept through death.
- **Idle fidgets:** standing still, a tendril pokes nearby blocks or tastes the rain (client only).
- **First places:** the Nether, the End, a thunderstorm, the Deep Dark, an Ancient City.
- **It tracks other hosts:** a mob another symbiote has taken: far, closer, further, holding, coming, feeding,
  whether it has noticed you.
- **The bond fades:** a small daily fade with nothing done together; a line when it drops a stage.
- **Careless hurts:** a line for a fall, a cactus, walking into fire.

## Patch 4: its body and the arm (9 rows)
Files: `WebSymbioteBody` (the arm), `WebSymbioteCollect` (orbs), new `WebSymbioteMolt`, an item for the cast skin,
`WebStrength`-style modifiers for Living Armor, client `WebBloom` on the big head.
- **The arm fights:** a stored weapon swings at foes on its own while you fight (from Cooperative).
- **The arm helps itself:** when you won't feed it, it takes food from your bag; swats small pests; digs ore it spots.
- **It keeps a good tool:** now and then it takes a fine tool or weapon into the arm and gives it back once trust
  rises; prying it out angers it.
- **A torch for your eyes:** in the dark with torches in your bag, it sets one down.
- **Collect takes XP:** Collect also pulls in experience orbs.
- **Chest curiosity:** looks in chests near you, loots a dungeon chest, re-sorts your chests, sometimes steals a
  tool; never town chests or other people's.
- **Living Armor moves you:** a little faster and a full-block step while the armour is on.
- **Molting** and **cast skin:** see the lore paragraph below. A molt you stay close for raises the bond; running or
  fighting through it costs stress. The skin is food that mends a torn, sleeping symbiote faster; organs from other
  mods count as meat.
- **The bloom:** cornered, starving or panicking, the big head splits into a crown of petal tendrils for a while.

## Patch 5: takeovers and the world (5 rows)
Files: `WebTakeoverPath`, `WebSymbioteLife`, `WebSymbioteBlob`, new `WebSymbioteSleep`, client squeeze in `WebSuitGrowth`.
- **It walks while you sleep:** low trust; while you sleep the blob slips out, roams and hunts through the night skip
  for real, and returns into you by dawn; the morning says what it killed.
- **Night prowl:** at night the black one sometimes takes you to stalk and kill a hostile mob for sport.
- **Doors:** the big bodies congeal through a door or a 1x2 gap and form whole on the other side; takeover walks rip
  wooden doors off and bite through leaves (iron doors stop them).
- **Elder against young:** go back to your old remains while another symbiote rides you, and they fight over your
  body; the elder usually wins.
- **Golems and villagers know:** iron golems turn on a bonded host; villagers shy away from the black one.

## Patch 6: screens and HUD (8 rows)
Files: client only except stamina: new `WebSymbioteHud`, `WebSymbioteSettingsScreen`, `WebArmScreen`, the Status
page, `WebScent`/`WebRedSense` markers; stamina in a new `WebStamina`.
- **HUD:** bond pip with a mood socket, its hunger bar, the standing order's icon, near the hotbar.
- **Its words on a plate:** whispers on a dark plate, low and centred, instead of the action bar.
- **Stamina:** tendril moves and wall clinging spend stamina that comes back at rest.
- **Strain blurb:** one hinting line per strain on the Status page.
- **Stage cards:** a picture per stage in each strain's colours, when you reach it.
- **Settings screen:** turn off fidgets, jealousy, requests, hints, the edge membrane, from the Status page.
- **Shader markers:** with Iris shaders on, small diamonds over what the scent and the heartbeat sense pick up.
- **Arm screen:** the arm's slots in a screen, with a button in the inventory.

## Order
Patches 1 and 2 first (no lore needed), then 3 and 4, then 5 and 6. Patches 4 and 5 wait for Andy's OK on the lore
paragraph "What it does when you aren't looking" (`Lore.md`, under the symbiote).

## Open questions
- Red keeps Shroud as its tile (assumed from Andy's notes naming only black and white).
- The cast skin: a new item, or a Kitchen meat? Plan: a Powers item, `jaymospowers:symbiote_skin`, tagged as meat.
