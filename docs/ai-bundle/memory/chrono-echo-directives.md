---
name: chrono-echo-directives
description: Timeless Chrono echoes take orders from the H screen (Standby/Harvest/Guard/Record-Loop, unlocked over time) + Time Flow for all Timeless, PR #468 MERGED 2026-10-24 00:56Z (pack 0.15.144)
metadata:
  type: project
  modified: 2026-10-24T00:44:42.655Z
---
2026-10-24 Andy pasted Gemini code (ChronoEchoBridge stubs, a PowerpackMenuScreen for jetpacks/FE systems we don't have, and a "jar" that was really a saved cookie-check HTML page). ChronoClones (record/replay clones) is a real mod but NeoForge 26.2 only, so we remade the idea natively.
Andy's ruling (decision card): echoes stay the curse until the owner is Timeless (ascended), then they obey ("Bind it once Timeless"). Andy also said: "chrono should now get time flow when ascended", so ChronoFlow.available is ascended-only now (corruption no longer needed). The 20-ish combo rule in Time Flow still corrupts.
PR #468, pack 0.15.144 (master took .143 meanwhile). Round 2, 00:50Z, Andy: "each of these should be unlocked over time, and stay greyed out until then. remove mirror, and make it do the bloom effect passively, but while on standby". So Mirror and Bloom are gone; Standby casts Time Bloom every 30 s between fights; Harvest/Guard/Record unlock after 1/2/3 in-game days of play as Timeless (SERVED attachment, UNLOCK_DAYS). Code: ChronoDirectives (orders attachment, Record button, Order payload, SHOWN synced to owner), ChronoEchoWork (per-echo brains), ChronoEchoFight ally mode. Bound echo: mobs hurt it, a lethal hit shatters it (it returns), the owner's hits keep the curse rules (still killable for good in the Nether for the future unlock). Orders are buttons at the bottom of the H screen, with overlay text and no chat. Not playtested. Jar /mnt/project-files/chrono-directives/jaymos-powers-0.15.144.jar.
Related: [[chrono-overclock-toggle]], [[chrono-g-reset]], [[future-los-perrito]].
2026-10-24 PR #474 MERGED 22:15Z (pack 0.15.156): recording also captures PLACE/INTERACT (UseBlockCallback), echoes keep a 4-slot haul (saved, dropped when gone), loops place blocks and use chests (deposit + restock 16), toggle levers/buttons/doors; 3 loop slots + Clear (tier 3). Orders now live on the H screen's Chrono tab (tabbed vanilla PowerScreen; inventory left tab opens it). Not playtested.
