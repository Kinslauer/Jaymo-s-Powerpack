# Wounded NPCs want their limbs before gold
- **Asking what they want** (`IllagerTalk`): an illager who has lost limbs (Jaymo's Powers' dismembering) wants a stand-in for them before whatever their nature wants: a wooden leg while a leg is gone, else a wooden arm, and says how many limbs short they are ("I'm 2 limbs short. Wooden Leg to start, and keep them coming."). Their nature's want (gold for the greedy, and so on) waits and comes back once they're whole.
- **Giving one** (Gift what you hold): any wooden, iron or Rig arm or leg of Powers' is fitted on a bare limb of the right kind, with a knock of wood or a ring of iron. Each is thanked like a want met (+15 hearts, a better mood); the last of them also pays the want's reward.
- `FrostTech.bare`, `wantedLimb` and `fitGiven` work for any body, not only illagers. Villagers already fit their own: they go to a smith once they've lost a limb (`FrostTech.tick`).
- Not changed: paying a hostile one off (Talk them round) is still coin. A limb fitted on an illager counts (they stop asking for it), but it isn't drawn: the fitted-limb layer is only on humanoid models, and illagers' isn't one.
- Built in CI; not played.
