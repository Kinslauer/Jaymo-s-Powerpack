# Disguises notes (0.7.139)

Files: `dragons/Disguises.java`, client `McaDisguiseSkinMixin`, `McaDisguiseLayerMixin`. Fold in and delete this file.

## What it does
- `Disguises.DISGUISE` (String "eurus"/"aeolus", persistent, synced to all trackers) on a Villager (MCA male/female_villager
  by registry id, plain Villager without MCA). `VISITED` (Long) keeps last-near time. Both are logged from `register()`.
- Spawns: every 1200 ticks, Overworld, a random player; Eurus 1/150, Aeolus 1/360 per check; 24-44 blocks off, surface,
  out of line of sight; one of each loaded; no Aeolus with an AeolusDragon within 192 blocks.
- Right-click: two questions (who / slain, three flavour answers each), then verdict from `DragonRecords`. Reveal:
  clouds, poof, growl, Eurus sparks and a visual-only bolt; villager discarded, wild dragon spawned (lifted up to 12
  blocks to fit, else "cramped" line and the villager stays), `setTarget(player)` + `takeOff()` (same package, protected).
  Shoo: line, shove, 5 minute cooldown (per player and kind).
- Choice made: they cannot be hurt at all (ALLOW_DAMAGE false except /kill and void), so a blow neither reveals nor angers.
- They leave after 72000 ticks (3 days) with no player within 48 blocks.
- Lines go out as chat via `message.jaymoskingdoms.says` (not `Speech`), so village tongues (`Languages`) can't garble them.
- Skin layout: MCA's `PlayerEntityExtendedModel` extends vanilla `PlayerModel` (64x64 player layout). Eurus' skin is the
  slim-arm layout (alpha 0 at x 54-55 on arm rows), so Eurus uses `female_villager` (slim); Aeolus is classic arms, `male_villager`.

## Lang keys (en_us.json), for kind K in eurus, aeolus: `disguise.jaymoskingdoms.K.<key>`
Each of `who`, `slain` has the question plus `.a`, `.b`, `.c` answers.

eurus.who = "Hm. You. You walk like you're trailing weather behind you. Who are you?"
eurus.who.a = "Just a traveller."
eurus.who.b = "Nobody you need fear."
eurus.who.c = "Who's asking?"
eurus.slain = "And what have you killed? Quick now. I can smell it on you."
eurus.slain.a = "Nothing worth naming."
eurus.slain.b = "A dragon or two."
eurus.slain.c = "Ask the wind."
eurus.reveal = "Then you'll do. Hold your breath, little storm!"
eurus.shoo.1 = "Pah. A wind's worth of nothing. Away with you!"
eurus.shoo.2 = "Not yet. Come back when you've bled a dragon or two."
eurus.shoo.3 = "Off! I've no time for the unblooded."
eurus.busy = "Still here? Go. Go!"
eurus.cramped = "Too many trees about. Find me somewhere with sky."

aeolus.who = "Mm... come closer, child. The air tells me little of you. Who are you?"
aeolus.who.a = "A traveller, grandfather."
aeolus.who.b = "Only someone who is passing through."
aeolus.who.c = "And who might you be?"
aeolus.slain = "And in all your wandering... what have you slain, little one? Speak slowly. I am old, and the winds are long."
aeolus.slain.a = "Nothing yet."
aeolus.slain.b = "Some of your children, I think."
aeolus.slain.c = "Whatever came for me."
aeolus.reveal = "So. The winds were right about you. Then let us see what you are made of."
aeolus.shoo.1 = "Not yet, child. The winds have not spoken your name. Go on, go home."
aeolus.shoo.2 = "Hm. Young. Come back when you have seen more of the sky."
aeolus.shoo.3 = "Run along now. The old have no use for the green."
aeolus.busy = "Ah. You again. The winds have not changed their minds."
aeolus.cramped = "There is no room here for what I am. Walk with me to open ground."

Existing key used: `message.jaymoskingdoms.says` (speaker, words).

## MODLOG suggestions
- Kingdoms 0.7.139: Eurus and Aeolus walk the world as villagers (MCA skins, no clothes, hair or face layers); they question
  you on right-click, shoo you off unless you've beaten 2 / 3 kinds of wind dragon (or are wind, Arsenal, Chrono, Darktide,
  have a tamed dragon or an earned heritage), then show themselves.

## Shared-file changes
- None needed. (Optional: make `McaCompat.newVillager` public and use it instead of the registry lookup of `mca:male_villager`/`mca:female_villager`.)
- Possible clash to be aware of: `Captives`/`McaBridge.candidate` could enlist or take a disguised villager that is inside a settlement.

## UNVERIFIED APIs
- Registry ids `mca:male_villager` / `mca:female_villager` (seen as strings in `EntitiesMCA`); creating an MCA villager with
  `EntityType.create` then `finalizeSpawn` (Captives does the same after `newVillager`), and that it comes out an adult
  (guard: `setBaby(false)` if `isBaby()`).
- `Entity.syncVelocity` on a ServerPlayer (used in Orcs on any entity); `LivingEntity.hasLineOfSight(Entity)`;
  `Level.getEntitiesOfClass(Class, AABB)` two-arg; `ParticleTypes.ELECTRIC_SPARK`, `SoundEvents.VILLAGER_NO` (not `.value()`).
- Mixin targets (checked with javap): `SkinLayer.getSkin(VillagerVisuals)`, `getColor(VillagerVisuals,float)`;
  `VillagerLayer.submit(PoseStack,SubmitNodeCollector,int,HumanoidRenderState,float,float)` (erased generic S; the
  EntityRenderState bridge calls it). `VillagerVisuals.require(state)` throws IllegalStateException when absent (caught).
- Fabric client `UseEntityCallback` returning PASS on the client and SUCCESS on the server is the Approaches pattern; MCA's own
  menu is assumed to open from the server-side `mobInteract` (so the callback's SUCCESS stops it).
