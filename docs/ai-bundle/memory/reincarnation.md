---
name: reincarnation
description: Andy 2026-11-05 - first two deaths of a life always wake; final death button "Reincarnate"; new life wipes all our attachments + MCA creator anew (PR #998)
metadata:
  type: project
  modified: 2026-11-05T09:12:49.546Z
---
2026-11-05 Andy: "you should always get a black screen wake up roll for your first two deaths"; final death button says "Reincarnate"; "EVERYTHING resets" with a new character creator.
- Jung's clip: shot by a skeleton while downed, lost the 50% SKELETON roll (not a gun bug).
- PR #998 MERGED 2026-11-05 09:45Z (Powers 0.20.83 / Kingdoms 0.7.573 / hint 110), branch claude/project-thread-jzd2cq, built, not played.
- Mortality.SURE_WAKES=2 counted in WOKEN; exceptions that can still end it: beheaded, void, execution by player/boss. 3rd+ death rolls by cause as before (my reading; Andy may want 3rd always final).
- Mortality.wipe removes every jaymospowers/jaymoskingdoms/jaymoskitchen attachment except OUTLIVES (roads_taken, heritage_unlocked, item holders: backpack, baubles, stashes, costume, post, crew, pit). Then LifeStory.reincarnateAnew: MCA PlayerSaveData.reset + setEntityDataSet(false) + launchDestiny, then reincarnate. Judge rebirth unchanged (heritage/class/story only).
- Not reset: villagers' MCA hearts for the player, gear lying with the body.
Related: [[downed-self-rise]], [[gemini-handoff-ki-forge]].
- Same PR: "taken in" wake (Andy "yes add this"): Powers Mortality.SHELTERS asked after CAPTORS; Kingdoms Shelter (non-hostile settlement seat within 320, 45%, a free bed near seat, Treatment.tendAll, then BedRest.lay once come round). Wake line "sheltered".
- 2026-11-05 11:41Z follow-up (Andy: judges multiplying, spawned in a wall, "wipe absoluteley everything", "black screen until it's ready"): PR #1011 MERGED 2026-11-05 12:06Z (Powers 0.20.94 / Kingdoms 0.7.583; also black death screen fade-in + Mortality.WAKE_LINE read large on the wake black). Final deaths no longer go to the Other World (STAY dropped on respawn); ZFighterAfterlife.wake uses Mortality.safe; OUTLIVES removed, wipe also clears inventory/ender chest/XP/effects/spawn; Mortality.REBORNING black screen until Kingdoms LifeStory.making false; hall NPCs only re-made once areEntitiesLoaded, extras discarded.
