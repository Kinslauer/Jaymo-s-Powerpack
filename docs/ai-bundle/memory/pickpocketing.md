---
name: pickpocketing
description: Pickpocketing in Kingdoms is a held action (stay behind the mark 1-3 s, light/armour/MCA mood scale it); merged PR #80 as Kingdoms 0.5.50 / pack 0.8.97
metadata:
  type: project
---
2026-10-13 Andy: "can you add pickpocketing?", then: shift+hold right-click, hand-in-pocket animation, random up to 3s,
succeed if you stay behind/match speed; longer with light, armour, mood; "crying villagers wont be as perceptive".
Merged PR #80 on Andy's "merge" as Kingdoms 0.5.50 / pack 0.8.97.
`Pickpocket`: hold detected by vanilla's 4-tick use repeat (HOLD_GRACE 8); caught only if the mark's body turns toward
you (TURN_COS); `holdTicks` is the hook for Stealth perks (Skill tree PR #84 builds on #80 and must re-version; its
catch-chance hooks no longer exist). Client `PickpocketPose` via `Pickpocket.Reach`. Not playtested; possible issue:
standing villagers turn to look at players, so they may catch you often. Related: [[own-blocks-no-crime]].
