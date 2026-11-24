---
status: done
task: mace-blunt
branch: claude/mace-blunt-62da3e
with: 
pr: 2037
mods: powers
versions: 
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/core/Fractures.java
  - src/main/java/com/jaymospowers/core/Dismember.java
  - src/main/java/com/jaymospowers/core/BlastGore.java
  - src/main/java/com/jaymospowers/core/finisher/Finishers.java
  - src/main/resources/data/jaymospowers/tags/item/crushing.json
---
# The flanged mace is blunt: no stab-through

**Task:** Andy: "The mace im using a blunt weapon despite the flanges. it should work like one, therefore no stab through, the blood particles make sense as it is bladed, but there is no stabbing".

## Plan
- The flanged mace (and cudgels, Anvil Father's hammer, Ogre King's maul) sit in minecraft:swords, so Finishers picked Run Through / Impale / D_BLADE and Fractures.blunt said no.
- Add them to jaymospowers:crushing; read that tag before the blade/sword tests.

## Notes
- Kept in the swords tag (about 40 call sites use it as "is a weapon", plus enchantments).
- Left alone: Kitchen CorpseEntity still lets any sword-tagged weapon cut a corpse apart (cudgels included).

## Done
Behaviour change only; no lore needed. Built by CI; not played.
