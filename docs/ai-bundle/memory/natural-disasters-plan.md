---
name: natural-disasters-plan
description: Natural disasters (own take on Weather2): all 3 plan stages MERGED (stage 1 PR #127; stages 2-3 PR #205 as pack 0.10.130 / Kingdoms 0.7.43)
metadata:
  type: project
---
2026-10-13 Andy linked Weather, Storms & Tornadoes (Corosus Weather2): Forge/NeoForge only, ARR. Ideas only.
Plan: /mnt/project-files/disasters/Disasters-Plan.md (stage 2 sandstorms/blizzards/wildfires/floods/hail; 3 earthquakes, relief quests, forecasts). Andy said "start" on stage 1.
Stage 1 = PR #127 (merged) (branch claude/project-thread-niz1dm, Kingdoms 0.6.12 / pack 0.9.26): package com.jaymoskingdoms.disasters (Storms in-memory cells, Tornadoes, Instruments storm bell/weathervane/barometer, StormNet), LevelWeatherMixin (precipitationAt + client rain/thunder level), client/disasters particles. Gamerules jaymoskingdoms:disaster_damage / disaster_days (6). Op /kingdom disaster <...>. Powers: WindPower tags jaymospowers_wind + jaymospowers_wind_lance:<time>; WindWeather reads Storms.thunderAt by reflection.
Seen in cloud playtest: rain under cell, debris, funnel, dust devils. Storm clouds look faint from far (fog). Wind riding untested.
Dev server needs a c:ingots/silver tag datapack in run/world/datapacks (Kingdoms silver_sword recipe fails without Powers) — pre-existing.
Related: [[lunar-events-omens]], [[cloud-playtest-setup]].
2026-10-13 Andy: thrown blocks must fall (not float) and whole trees should be torn out by strength. Done: THROWN_RIDE 50 ticks then gravity; Trees.uproot + FallingTree.fling (tumbling flight). Seen working in dev server.
2026-10-14 Andy: "tornadoes should be far more rare". PR #137 MERGED 2026-10-14 05:23Z (Kingdoms 0.7.2 / pack 0.10.9): Storms.TORNADO_SUPERCELLS 0.1, only 1 natural supercell in 10 can touch down (~1 tornado / 60 days). Dust devils unchanged.
2026-10-14 Andy asked about Simple Clouds (modrinth simple-clouds): Forge/NeoForge 1.20.1/1.21.1 only, PolyForm Perimeter licence (no competing ports), own localized weather. Not addable; offered to restyle our own storm clouds after its look (unanswered).
2026-10-14 Andy OK'd Simple-Clouds-style look "without too much extra resource drain": PR #172 MERGED 2026-10-14 17:06Z (Kingdoms 0.7.16 / pack 0.10.63): StormsClient layers same puffs into base/tower/anvil + tornado wall cloud.

2026-10-15 stages 2+3 = PR #205 MERGED 2026-10-15 (pack 0.10.130 / Kingdoms 0.7.43): disasters/Calamities (sandstorm, blizzard, hail synced to client; wildfire, flood), Earthquakes, SandLayerBlock, Almanac, fog + shake mixins; com.jaymoskingdoms.DisasterRelief (news, treasury repairs, tyrant storm tax, paranoid blame, relief quests: food GATHER + Quest.RELIEF=15 rubble; sailing's ESCORT holds 14); forecasts half a day ahead (wizard sneak-use, Journal, Almanac); Harvest Moon none, Blood Moon x2. Storm/Tornado untouched (Smoke's SkyCalm). Not playtested in game. Jar: /mnt/project-files/disasters/stage-2/.
