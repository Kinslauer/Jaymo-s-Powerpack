# A werewolf tearing out no longer crashes the server
- **Fix (server crash)**: when the beast tears out of its host, everyone who was fighting the host turns on the
  beast. Finding them read each nearby mob's attack-target memory, and a mob whose brain doesn't keep one (most
  hostiles) made the game throw (Deck crash 2026-11-14 8:42 AM). Only mobs that keep that memory are asked now.
  The other 16 direct memory reads in the three mods were checked: all guarded or villager-only.
- Built.
