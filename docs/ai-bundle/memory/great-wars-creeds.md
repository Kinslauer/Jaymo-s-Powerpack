---
name: great-wars-creeds
description: Kingdoms 0.7.224 (PR #540, 2026-10-26): realm creeds, daily tension into inevitable wars, great wars between rich realms (coalitions, sellswords, upkeep, rebellions, creed war goals), war wagers
metadata:
  type: project
  modified: 2026-10-26T14:32:23.152Z
---
2026-10-26 Andy: "larger scale wars between wealthy kingdoms, differing ideologies cause conflict making war inevitable. And the more money the more war." (Warring Nations link: ARR, ideas only; Reference-Notes entry added.)
#540 MERGED 2026-10-26 17:21Z (Kingdoms 0.7.224 / pack 0.17.9, compiled only, not seen in game) adds `GreatWars` (attachment `great_wars`):
creeds Crown/Chivalry/Conquest/Faith/Coin/Old Blood (65% from ruler temper, stored per realm, vassals follow overlord);
daily tension (friction x wealth) -> rivals at 35, NPC declares at 80; great wars when both worth 260 (or 750 together):
NPC kingdoms pledge to a side (Wars.onSide checks pledges; Wars.warTop for alerts), sellswords from treasury, parties up to 16,
upkeep, broke -> sues for peace, fiefdom rebellions; attacker's creed sets peace terms (GreatWars.Goal); `/kingdom war tensions|creed`, op `tension`.
2026-10-26 15:17Z Andy: "War wagers can only be done with bookie npcs in speakeasies. You can also bet on fighters in the pit... another backroom dug into the speakeasy or in a makeshift ring if the room allows." Same PR: `Bookies` (bookie in every den, clickable book, /bookie war|bout), `FightPit` (backroom ring dug off den or makeshift ring room; simulated-odds bouts, champion retires after 5). 15:42Z Andy "Sure add that, also make the villager winners/champions bring that up in conversation": players fight via /bookie fight (empty-hand punches, sneak guards, purse), fighters keep `pit_record` attachment, return to town, brag in MCA chat (FightPit.tell/prompt). See [[underworld-speakeasies]].
16:10Z Andy: "You should take real damage, but get patched up ringside afterward by an attendee... rags instead of bandages most of the time... as soon as you enter the pit, your armor and gear is held." Done: gear held at the bell (`pit_held` attachment, returned after or on next sight), villager blows real damage (yield at 4 health, never lower), attendant (`jaymoskingdoms.pit_attendant`) OFFERS rag 75% / linen bandage 25% via clickable [Let him]/[No thanks] (`/bookie patch yes|no`); 17:11Z Andy: if accepted he treats you himself, dressing every hurt part + splinting breaks (Powers `Treatment.tendAll`, called via PowersCompat; +1.5/+3 health without Powers).
Players' realms never auto-declare or auto-pledge. See [[kingdoms-war-tempers]], [[heritage-prejudice]].
