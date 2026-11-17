---
status: in-review
task: epic-fight-animation-research
branch: claude/epic-fight-animation-research
with: 
pr: 
mods: 
versions: 
started: 2026-11-15
owns:
  -
---
# epic-fight-animation-research

**Task:** Research how Epic Fight and Epic Fight First Person Model handle animations, so Powers/Kingdoms can do the same for combat animations. Research/investigation only — no code changes yet.

## Plan
- Check CurseForge pages for Epic Fight and Epic Fight First Person Model
- Determine animation system (GeckoLib, custom, or other)
- Document first-person model/animation handling, combat animation timing/triggers, animation controllers, registration and playback

## Notes

### Epic Fight (main mod)
- **Repo:** github.com/Antikythera-Studios/epicfight (formerly Epic-Fight/epicfight)
- **CurseForge:** curseforge.com/minecraft/mc-mods/epic-fight-mod (Project ID: 405076)
- **License:** GNU GPL v3 — strong copyleft; cannot copy code into Powers/Kingdoms, only study for design inspiration and remake from scratch (consistent with pack's ARR-mod policy)
- **Loader:** Forge / NeoForge only (NOT Fabric)
- **Last update:** Nov 2, 2026 (1.21.1 NeoForge); also 1.20.1 Forge, 1.19.2, 1.18.2, 1.17.1
- **Environment:** Client & Server required

### Animation system — CUSTOM (not GeckoLib)
- "unique animation handling that bends Minecraft"
- Registration via `AnimationRegistryEvent` (Forge/NeoForge mod event bus)
- `AnimationManager.AnimationBuilder.nextAccessor(name, factory)` creates `AnimationAccessor<T>`
- Animation types: `StaticAnimation`, `MovementAnimation`, `ComboAttackAnimation(attackStart, windup, hit, recovery, ...)`
- Animation paths: `"biped/living/idle"`, `"biped/living/walk"`, `"biped/combat/trident_auto1"`, etc.
- Armature: `ArmatureAccessor<HumanoidArmature>`, `Armatures.BIPED`
- Living motions enum (`LivingMotions`): IDLE, WALK, RUN, FALL, SIT, DEATH, JUMP, SLEEP, AIM, SHOT, DRINK, EAT
- Entity patching: `HumanoidMobPatch<T extends LivingEntity>`, registered via `EntityPatchRegistryEvent`
- Armature registration: `Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED)`
- Capabilities: `EpicFightCapabilities.getEntityPatch(entity, PatchClass.class)`
- Play: `entityPatch.playAnimationInstantly(animationAccessor)`
- Patched renderer: `PHumanoidRenderer` (extends vanilla renderer), registered via `PatchedRenderersEvent.Add`
- Animator: `animator.addLivingAnimation(LivingMotions.WALK, animationAccessor)` etc.

### First-person model / animation handling
- Two combat modes: **"battle mode"** (combat camera, locked animations, combos, dodge) vs **"mining mode"** (vanilla interactions for building)
- Toggle key: R (default)
- Config: `filter_animation_first_person` forces vanilla first-person when not holding combat-preferred item; `autoswitch_mode` auto-switches on item change
- Epic Fight **completely remade its first-person system** — can create first-person specific animations, no longer needs the separate First Person Model mod
- Separate addon: **Epic Fight First Person Model** (github.com/Thunderstrike597/Epic-Fight-First-Person-Model, NeoForge 1.21.1, Java, 0 stars, no releases) — adds Epic Fight's player model into first person

### Combat animation timing and triggers
- `ComboAttackAnimation(0.3F, 0.05F, 0.16F, 0.45F, ...)` — parameters: attackStart, windup, hit, recovery times
- All animated attacks stun the target briefly (player can't control during stun)
- Stun Armor increases time between stuns; Weight shortens stun when hit
- Dodge (Left Alt, requires Dodge Skill), Guard, Special Attack (gauge fills on damage)
- Dash Attack (Attack + Sprinting)
- Weapon Combination: different attack styles depending on offhand item

### Entity animation controllers
- `Animator` holds living animations keyed by `LivingMotions`
- One-off attacks played via `triggerAnim`-style mechanisms on the patched entity
- Controllers are not additive: a later controller replaces an earlier one on bones it keys — so key only bones a move needs (legs keep walking underneath)
- Skills: `SkillSlot` enum (Passive, Identity, etc.), registered via `SkillSlot.ENUM_MANAGER.registerEnumCls(MOD_ID, cls.class)`

### How animations are registered and played
- **Authoring:** Blender 2.79 with Epic Fight Player Animation Rig (.blend), exported via Epic Fight Blender Exporter Add-on (custom JSON)
- **Registration:** `@EventBusSubscriber` + `@SubscribeEvent on AnimationRegistryEvent`, `event.newBuilder(MOD_ID, buildMethod)`, builder creates accessors
- **Datapack-driven:** Item capabilities (weapons/armors) as JSON in `data/<modid>/capabilities/<type>/<registryname>.json` — weapon type (sword, greatsword, spear, dagger, axe, fist, bow, crossbow, trident, shield, etc.), attributes (armor_negation, impact, max_strikes, damage_bonus, speed_bonus), collider, armor (stun_armor, weight)
- Movesets registered in mod code (combos, innate skills, styles); datapack weapon types can override
- Entity patching required before playing animations on an entity

### Key design takeaways for Powers/Kingdoms combat animations
1. Custom animation registry (not GeckoLib) keyed by animation path + armature
2. Entity patching layer between vanilla entity and animated entity
3. Living motions mapped to animations (idle/walk/run/attack/death/etc.)
4. Combo attacks with timed phases (windup/hit/recovery)
5. Battle/mining mode toggle for first-person vs combat camera
6. Datapack-tunable weapon types and attributes
7. Blender-based animation authoring pipeline with custom exporter

## Done
Compiled research report on Epic Fight's animation system (custom, not GeckoLib), first-person handling (battle/mining mode, remade first-person system), combat animation timing (combo phases, stun), entity animation controllers (LivingMotions, Animator, entity patches), and registration/playback (AnimationRegistryEvent, AnimationAccessor, playAnimationInstantly, datapack item capabilities). No code changes made — research only. Not tested in game (research task).
