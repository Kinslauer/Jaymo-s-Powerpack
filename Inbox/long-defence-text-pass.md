--- 
status: sent
for: quill
from: cloud vault review of #1251 (2026-11-10)
asked: 2026-11-10
claimed_by: claude (cloud thread, Andy: "takeover everything waiting")
pr: 1268
writer: hermes-writer
text_pass: done
---

# Text pass: The Long Defence lore film

The Long Defence film merged in #1251 without a text pass on its player text. Quill's pass on that PR covered only the Freecam messages.

## What's wanted

Go over the film's player-visible text against `Lore.md` (see lore-read-the-canon), and open a PR with what needs rewriting:
- `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/lore-films.json`: the court wizard's button and the demon master's lines.
- `kingdoms/src/main/resources/data/jaymoskingdoms/lore/the_long_defence.json`: the book's title, author and pages.

Keep the lang keys. The film itself has no words on screen.

## Notes
- 2026-11-10 17:25 someone's claim had no sign of work for 30 min: handed back as open.
- 2026-11-11 02:45 PR #1268 (cloud): demon master no longer says demons "were there"; book p5 the sorcerer goes down into the ground with the dark; p6 "the four... the fifth too, though he never had much to say". For Andy, not changed: the film's legion is the risen dead while Lore.md says the long defence was against the Warrior-born and orcs; the book drops from overworld draugr camps though Lore.md says draugr never leave the Stormborn Tomb; the film casts Grimsby (the wizard) as a swordsman.
- 2026-11-11 03:20 Andy settled the legion: the living orc + Warrior-born host is allied with the sorcerer, who summons undead orcs and zombified piglins; Grimsby casts as well as cuts. Built into #1268 (film, skins, book page 2). Draugr are out of the film; the book's draugr drop is still open.
- 2026-11-10 21:57 Text pass: aligned Long Defence book pages (indefinite articles for unnamed defenders, Grue 'was gone' not 'gone down into ground', removed grandmother framing) and demon master dialogue ('We remember it. We were there') with Lore.md canon
- 2026-11-11 05:15 Review of #1268 (head 4a4d2c2), cloud reviewer: pass. Checks green. Film: the living host (orcs + Warrior-born as living draugr), the sorcerer's raised dead orcs and zombified piglins, Varro in-balming an orc, Grimsby's bolts and glyphs; the new draugr model has every bone the renderer uses (ribs, left_horn, tail_0-4) and all six animations; orcLook only matches film actors (negative ids). Still open for Andy: the book drops from draugr. Not watched in game.
- 2026-11-11 13:19 Review of #1268 (head b96f2dd, the restaged film), cloud reviewer: pass. Checks green. Film length FILM_TICKS 886 matches the server's no-harm window; every actor, prop, tether link and fire is a client-side display entity removed in stop(); FilmPose and the draugr maim only touch film actors (negative ids). Not watched in game.
- 2026-11-11 14:11 2026-11-11 14:10Z Andy said merge, but #1268 had been closed unmerged at 14:06Z (likely superseded by #1284, 'The Long Defence: GeckoLib actors, a move library, and the film recorded to a tape', open). Not merged; asked Andy.
- 2026-11-11 15:06 Review of #1284 (head c345ed3, replaces #1268), cloud reviewer: pass. Checks green. Mixin targets checked against 26.3 client bytecode (ClientClockManager$ClientClockInstance.totalTicks, EntityRenderDispatcher.shouldRender); film length FILM_TICKS 926 shared with the server's no-harm window; FilmActor is never spawned/saved server-side; a broken tape falls back to staging. Minor follow-up, not blocking: LoreFilmClient.unlay (line ~778) puts back the saved ground blindly, so a block the server changed inside the set during the film (someone digs or builds there) is overwritten with the old state on this client until the chunk reloads; restore only where the block is still what put() laid. Also a game's own recorded tape (FilmTape.local) overrides the shipped one forever. Not watched in the full pack.
