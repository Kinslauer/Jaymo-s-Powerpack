# The Retaking - story core
- **The Retaking**, one main story for every player, in five acts, each closed by a Knowledge king: The Hearth (Goblin King), The Old Stones (an Ogre King), Lights in the Sky (the Skeleton Lord), The Dark Below (the Piglin King), Legend (the Wither).
- **Story tab** on the character menu (H): the five acts down the left (acts not reached show as ???), the picked act's milestones as plain goals, ticked when done. It says what to do, never what it means.
- **Acts can't be skipped:** a milestone of a later act only counts once the act before it is closed. Something done early has to be done again (already earned advancements are kept).
- New Act I milestones: three town errands handed in (errands handed in count for party members within 64 blocks), and finding The Long Defence (filed in the codex or read). Reaching the Aether is now awarded by Kingdoms so it can wait for Act II.
- Classes: `Retaking` (acts, milestones, gating, journal payloads), `RetakingScreen`; hooks in `PackAdvancements`, `Quests.turnIn`, `MenuCompat`.
- Compiled (main + client); not played.
