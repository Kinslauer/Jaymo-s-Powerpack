# no harm behind the loading screen; Death Sorcerers unattuned, raise one weak body
- **Woke already downed** (Andy's clip, 2026-11-14): a new life on "Alone in the wild" found no wild spot and was left where
  stasis let them down (the world's spawn, a village at night) with no arrival grace, while the loading screen was still up,
  so whatever roamed there downed them before they could see. Now nothing harms a player who can't see the world yet
  (loading screen up, land settling, or the 2 s after it lifts; `StoryGround.unseen`) and mobs going for them are called
  off (`StoryStasis`), and the stay-where-you-are fallback gives the arrival grace too (`LifeStory.arrive`).
- **A start that gives up lands on its own land** (Andy: "yes i want that"): when the start and then the wild both find
  nowhere (the land budget ran out in a new, lagging world), the player is put on the land made for that start while they
  waited (`LifeStory.madeGround`: open ground first, then any floor, then its origin), not left at the world's spawn.
- **Death Sorcerers are never attuned** (Andy: "death magic is not ki, but it does draw on mana"): they hold and spend mana
  as a natural caster (`Mana.hasMana`), with no attuning flare or line, no glow the attuned can read, no sight of others'
  mana or spirits, and an amethyst seam doesn't take. One already attuned loses it and keeps the mana; a fresh one starts
  full. Arcana shows and rune feeding works for them.
- **No blade imbue for death** (Andy: "a weak reanimation of a single corpse, and they shouldnt get a blade imbuement"): a
  tap of J raises the body you look at (`Necromancy.tapKey`), one risen at a time, at half health and half its blow.
  Sneak and J is unchanged (Reap, death bolt, raise). The Reaper's sweep keeps its own cap of 3.
- Built; not played.
