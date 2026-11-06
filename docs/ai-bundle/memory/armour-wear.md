---
name: armour-wear
description: Armour rework PR #979 (Powers 0.20.68/Kingdoms 0.7.557, hint 101): 30% durability, absorbs more, breaks without vanishing, NPC armour wears, no repairs in combat
metadata:
  type: project
  modified: 2026-11-05T02:36:55.628Z
---
2026-11-05 Andy: armour far less durable but absorbs more; at 0 it stays, repairable, but gives nothing; axes/maces wear it faster; players and NPCs alike; no repairs on the battlefield.
- PR #979 MERGED 2026-11-05 02:40Z (branch claude/project-thread-ny8ldf), built, not played. Next free: Powers 0.20.69, Kingdoms 0.7.558, hint 102. Code core/ArmourWear + mixin/ArmourWear*Mixin.
- Numbers: durability x0.3 (DefaultItemComponentEvents, any mod's humanoid armour, not elytra), armour x1.4 / toughness x1.25 in the absorb calc, axes/maces/Fractures.blunt wear x2.5.
- Broken = vanilla ItemStack.isBroken (damage == max): vanilla skips its attributes; enchantments silenced. Graded pieces keep Wear's Broken rule.
- NPC armour now wears (vanilla never did); mobs self-mend 10%/5 s after 30 s out of a fight.
- In combat (10 s since hit/hurt, or hostile targeting within 16): anvil won't give repaired armour, Mending gives no repair. Grindstone/crafting combine NOT blocked..
