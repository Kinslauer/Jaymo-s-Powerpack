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
