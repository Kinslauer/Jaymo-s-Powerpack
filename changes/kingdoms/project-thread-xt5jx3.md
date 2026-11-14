# The Reaper's host, the living raiders, the tomb and the sea
Andy's picks from Immersive Pillagers, Mebahel's Draugr and Moog's Ocean Structures (`docs/ReaperHost-Plan.md`).
- **People aboard Moog's boats** (`SeaSites`, through `IllagerSites`; needs Moog's Ocean Structures and MCA): the long
  ships are held by barbarians, who keep to their ship and never raid from it; the hauling ship has a skipper with a
  trade and his haulers; the small ship has fishers, one selling the catch; the travel ship a skipper and passengers; each
  wanderer raft one drifter for hire, who never moves along. They move in when nobody is watching, like the illager
  places. The peaceful ones greet you once a day, and their talk hints at the drowned courts. Moog's chests keep Moog's loot.
- **The host rides** (`ReapedHost.mount`): about a quarter of a host's reaped pillagers and vindicators come on skeleton
  horses, and a fifth of its pillagers on spiders, which carry them straight up a wall. Only the reaped ride spiders. A
  skeleton horse whose rider falls is left tame for whoever wants it; the Reaper takes the mounts back with the host.
- **The reaped evoker raises** (`ReapedEvoker.RaiseGoal`, `ReapedHost.raise`): in place of vexes it lifts up to three
  bodies within 12 blocks back into the fight (a dead illager as the same kind of reaped, anything else as the old dead),
  and they fall in with its host. Without Jaymo's Kitchen's corpses it keeps its vexes.
- **Folk fear them**: villagers run from the reaped as from living pillagers (`VillagerHostilesSensorMixin`), village
  gossip now and then turns to the horn nobody was blowing, and a friendly illager's chat to their own dead walking.
- **The Call** (`Reaper.host`, `ZombieHordes.call`): a Reaper keeper at the Shroud, sneaking with the Reaper key and no
  body near to raise, calls six of the reaped. They keep to the keeper and go for whoever the keeper last struck or was
  struck by, for two minutes, then are taken back. 10 Toll (held above the Shroud's own), ten minutes' cooldown.
- **The Reaper's holds** (`ReapedHolds`): when one of the reaped kills an illager camp's warlord the camp changes hands
  instead of falling. Every illager left in it gets up as the same kind of reaped, a Reaped Warlord takes the seat, and
  the camp goes on as a camp: it raids the towns round it with the reaped, grows by the living illagers its people kill
  (to 30), and courts within 600 blocks post its warlord on the bounty board (40). Kill the warlord to break it. Hosts
  never march on a hold.
- **A mangonel** (`ReapedHost.engine`): a host of ten or more with a seat to march on pushes a mangonel there with two of
  its own as crew, sets it down 34 blocks short and batters the seat and whoever defends it.
- **A black long ship** (`ReapedHost.longship`): by the water, half the hosts come in a dark oak drakkar moored within 40
  blocks, out of sight, and wade in from her deck. She is left where she moored.
- Lore: `Lore.md`, "The host at war" (new, for Andy).
- **The hit squad arrives by the land it crosses** (`SquadMounts`; living illagers only): two to a camel in deserts and
  badlands, on horses on plains and savanna, two to a boat where there is water within 20 blocks (rowed toward you; they
  shoot from it and step out where it grounds), and one time in five under open sky two to a hot air balloon let down from
  26 blocks up (needs Jaymo's Powers). Mounts, boats and balloons are left behind for whoever wants them.
- **Wanted by the illagers** (`RaiderGear`): every living illager you kill adds to a mark (6, a warlord 20, to 100). From
  30 their hit squads come at any hour. It fades 3 a day, a friendly illager sells it back in the talk menu (an emerald's
  worth a point), and beating a horn's squad takes 15 off.
- **Wanted posters**: a squad's leader carries (and drops) the poster of whoever it hunts; use your own to tear it up (25
  off). A Blank Poster (paper, ink sac, charcoal), named for a player on an anvil and used on a wall within 40 blocks of a
  living illager camp's fire, is pinned there in a frame and adds 40 to that player's mark.
- **Raider's Horn**: every camp warlord drops one, a squad leader one time in five. Blow it (6 uses, 2 minutes apart) and a
  squad comes for you now, by day too. Kill all of it and no squad comes within 160 blocks of there for 5 days.
- **Rusty Key**: 6% from a living illager you kill; used on a locked chest of lock level 1 or 2 it opens it and is used up.
- **Evoker's lab**: illager camps raise one as they grow (after the last tent): a lean-to with a brewing stand, a lectern,
  a cauldron and a chest locked at level 2 holding 2 to 4 Evoker's Notes. Evokers drop a page about one time in three.
  Reading a page teaches 2 points of the illager tongue once a day. Five pages used on a brewing stand with an emerald
  block in the other hand make a Crude Totem six times in ten (it saves you from death once, with Regeneration I for 15
  s and Weakness for 30 s); otherwise smoke and Weakness.
- Lore: `Lore.md`, "The illagers keep accounts" (new, for Andy).

- **The tomb's ranks.** The Stormborn Tomb's draugr are no longer all sword-and-shield or axe: about half still are, and
the rest rise as an **archer** (stands off and shoots, weak with its hands), a **wight** (rimed cloak; a frost bolt from
far off that slows and chills, its blade up close), a **scourge** (hooded, with a staff; a slow ki blast that bursts
where it lands, no blade, backs away, and a blow breaks its casting) or a **hall-guard** (two swords and a crest, more
health, steps aside from blows and most arrows). Bolts fly at where you stood a moment before, so keep moving. They
read the fight too: a shield goes up (and takes everything from in front) while you draw a bow or ready a throw, a
warrior or wight nearly dead backs off for a breath, and all of them press faster when you are nearly down. Draugr
already in a world stay warriors. Each rank's gear is on the draugr's own model (`kingdoms/art/draugr.bbmodel`, sheet
now 128 x 176); `kingdoms/tools/bbmodel_render.py` draws a textured picture of any of our Blockbench projects.

- **Laughing bones.** Of the buried rooms that are not kept barrows, four in ten hold one or two laughing bones: a
skeleton whose skull rolls away laughing when it falls and grows the body back after 12 seconds unless you smash it.
The body drops nothing; the smashed skull spills bones, arrows and experience (and rarely the skull). A golden weapon
ends the body for good. Compiled, not played.
