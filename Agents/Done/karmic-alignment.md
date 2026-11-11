---
status: done
task: 
branch: claude/project-thread-fml2w0
with: Claude (cloud thread)
pr: 1291
mods: powers, kingdoms
versions: 
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/core/wounds/Karma.java
  - src/main/resources/assets/jaymospowers/lang/parts/karma.json
---
# Karmic alignment

**Task:** Andy: "a karmic alignment system, meaning your actions are weighed against you after death ... If you were evil you reincarnate in the nether, one last chance ... if you were just you end up in the otherworld, allowed the chance to respawn instead of reincarnating (You will still have to fight in the tournament as a non z fighter)".

## Plan
- Powers `core/wounds/Karma`: a hidden score per life from deeds; weighed when Mortality ends a life.
- Just: the Other World, win the tournament to go back as you were. Wicked: one last chance in the Nether.
- Kingdoms: reputation changes feed karma; outlaws are no innocents.

## Notes
- Lore.md: "(new) The weighing" under The dead, for Andy to approve.

## Done
Built, compiles, not played. A hidden weight per life; a life that ends is weighed. Just: Other World, win the tournament
to go back as you were (lent blade, weaker spirits, no paying). Wicked: once, wake in the Nether far from a lit portal,
hunted, death there final. Numbers are Claude's first guesses (thresholds 25 / -25).
