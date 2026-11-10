# Reference notes

One entry per link or video Andy has shared. **Before reading or watching a reference, search this file for its URL or filename and reuse the entry.** After reading a new one, add an entry at the bottom of its date (2-4 lines: what it is, licence/loader if checked, verdict, which PR). Re-read the file just before appending; other threads write here too.

Cloud limits: YouTube and medal.tv links can't be opened from the cloud, so ask Andy to upload the clip here. Videos are watched as still frames; there's no sound.


## 2026-10-10

- [illager-invasion](https://www.curseforge.com/minecraft/mc-mods/illager-invasion), [ati-structures](https://www.curseforge.com/minecraft/mc-mods/ati-structures), [ati-structures-datapack-vanilla](https://www.curseforge.com/minecraft/mc-mods/ati-structures-datapack-vanilla), [grim-kingdomscan](https://www.curseforge.com/minecraft/mc-mods/grim-kingdomscan), [grindstone-sharper-tools](https://www.curseforge.com/minecraft/mc-mods/grindstone-sharper-tools), [mp-moogs-paths](https://www.curseforge.com/minecraft/mc-mods/mp-moogs-paths), [hotbar-satchels](https://www.curseforge.com/minecraft/mc-mods/hotbar-satchels), [better-mob-combat](https://www.curseforge.com/minecraft/mc-mods/better-mob-combat), [epic-fight-mod](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod)
  - Asked: "can you work these armor sets into the different factions? there should be illager camps as well that work like the barbarians"
  - Result: PR #3: swap Towns and Towers for ATi and Grim; PR #5: pack additions 0.8.5; PR #2: Kingdoms 0.3 + 0.4; PR #8: Kingdoms joins the pack (0.8.6); PR #17: roll on double-tap Sneak, ATi compounds (0.8.20)
  -

## 2026-10-11

- [minecraft-comes-alive-reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn), [github.com/Luke100000/minecraft-comes-alive/wiki/G](https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations), [github.com/Luke100000/minecraft-comes-alive/wiki/T](https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS)
  - Asked: "we NEED some kind of integration with this mod. it makes all villagers interactable. if you cant just integrate it out righ"
  - Verdict: Used as-is: MCA ships from a self-built 26.3 jar (PR #18) until an official release.
  - Result: PR #18: self-built MCA jar + Kingdoms tie-in.
  -
  - 2026-10-21 GPT3-conversations page read (thread "Villager chat AI and memory"): off by default; op turns it on with `/mca chatAI default` (or `enableVillagerChatAI` in the server's config/mca.json). Say a villager's name to start. Free quota resets hourly; `/mca chatAI <model> "<url>" "<token>"` for own OpenAI/Mistral endpoint. Villagers remember ~a dozen sentences; experimental long-term memory flags store summaries on the author's server. Chats may be used for training. Kingdoms adds its own realm context + per-villager memory to the prompt.
- (video attached, filename not recorded)
  - Asked: "I need gravity's R pull to have a larger hurtbox on players, display an indicator over the cursor when they are within range, and increase the range to about 60"
  - Result: Gravity G shockwave rebuilt from the video, plus kneeling, earthboard and meteor changes. PRs: PR #21: Gravity's weight (0.8.25); PR #28: Gravity Well around you, shoulder view (0.8.35)
  -

## 2026-10-12

- [cinematic-horizons-dh-shaders-edition](https://www.curseforge.com/minecraft/modpacks/cinematic-horizons-dh-shaders-edition)
  - Asked: "let me know if this would work if not re integrate distant horizons for better render distance"
  - Verdict: DH was re-added here (PR #37) but is OUT again since 2026-10-13; see the Distant Horizons entry.
  - Result: PR #37: Distant Horizons back in the pack
  -
- [mca-default-medieval-by-de4th4sh](https://www.curseforge.com/minecraft/texture-packs/mca-default-medieval-by-de4th4sh)
  - Result: PR #47: Medieval MCA villagers resource pack
  -
- [treechop](https://www.curseforge.com/minecraft/mc-mods/treechop)
  - Asked: "can you combine the way this works with out tree mod?"
  - Verdict: TreeChop-style chops remade in Kingdoms.
  - Result: PR #48: trees take chops to fell
  -
- [dawn-craft](https://www.curseforge.com/minecraft/modpacks/dawn-craft), [quest-giver](https://www.curseforge.com/minecraft/mc-mods/quest-giver)
  - Asked: "Create quests using the quest giver mod getting quests from villagers and kings exc"
  - Result: PR #52: Kingdoms 0.5.28 quests; PR #57: quest foes no longer spawn inside the earth
  -
- [age-of-weapons](https://www.curseforge.com/minecraft/mc-mods/age-of-weapons)
  - Asked: "mix the existing weapons we have with these dont add anything modern or past the medieval ages, but show me which weapons you can add fi"
  - Verdict: 27 weapons remade in Jaymo's Armory.
  - Result: PR #51: Jaymo's Armory
  -
- [jet-and-elias-armors](https://www.curseforge.com/minecraft/mc-mods/jet-and-elias-armors)
  - Asked: "anything we can do to implement these?"
  - Result: PR #53: Kingdoms 0.5.27 armour sets
  -
- [goblins-tyranny](https://www.curseforge.com/minecraft/mc-mods/goblins-tyranny)
  - Asked: "i really like this idea too, infiltrating goblin villages. that should be implemented if nothing else"
  - Verdict: Remade as goblin villages; Loot Goblins ported (no ore mobs).
  - Result: PR #54: goblin villages
  -
- [medal.tv/games/minecraft/clips/nI8lkx2L7YcIWevLU?i](https://medal.tv/games/minecraft/clips/nI8lkx2L7YcIWevLU?invite=cr-MSxkeU0sNjc0MDAwNzk)
  - Asked: "Beast quest was bugged"
  - Verdict: Threads can't open medal.tv links: ask Andy to upload the clip to the project files instead.
  - Result: Routed to an existing thread. PR #52: Kingdoms 0.5.28 quests; PR #57: quest foes no longer spawn inside the earth
  -
- [medal.tv/games/minecraft/clips/nI86uQHAGvokX2vUJ?i](https://medal.tv/games/minecraft/clips/nI86uQHAGvokX2vUJ?invite=cr-MSwxeXIsNjc0MDAwNzk)
  - Asked: "the mounted npcs float high above their mount"
  - Verdict: Threads can't open medal.tv links: ask Andy to upload the clip to the project files instead.
  - Result: Riders sat with no offset; fixed so mounted folk sit in the saddle. (Agent later said it cannot open Medal links.) PRs: PR #56: mounted folk sit in the saddle
  -
- [irons-spells-n-spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks), [mo-creatures](https://www.curseforge.com/minecraft/mc-mods/mo-creatures)
  - Asked: "I want this to fuel the necromancy and wizards that affiliate with the kingdoms and barbarians. Barbarians probably more so ht"
  - Verdict: Remade as our own sorcery (necromancers, shamans, court wizards). Mo' Creatures is GPL-3: its elephant model and hides were ported for the equestrian work.
  - Result: PR #60: Kingdoms sorcery
  -
- [tinkers-construct](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct)
  - Asked: "can we get tinkering?"
  - Verdict: Remade as Jaymo's Tinkering (3 stages).
  - Result: PR #61: Jaymo's Tinkering stage 1; PR #63: Tinkering stage 2; PR #65: Tinkering stage 3; PR #106: No punching trees, no vanilla metal tools
  -
- [william-wythers-overhauled-overworld](https://www.curseforge.com/minecraft/mc-mods/william-wythers-overhauled-overworld)
  - Asked: "can we add this?"
  - Verdict: WWOO added (biome decoration only, needs Cristel Lib).
  - Result: PR #59: Overhauled Overworld
  -
- [ai-improvements](https://modrinth.com/mod/ai-improvements)
  - Asked: "I want to change how mobs path, too many times they get stuck, can you do anything about that or will this?"
  - Result: PR #62: Mobs work themselves loose
  -
- [alexs-mobs](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs)
  - Asked: "can we use the horses from here? if so can we add all the other mobs?"
  - Verdict: Added (PR #70), then REMOVED (PR #90): Alex's Mobs + Citadel break Iris shaders. Don't re-add.
  - Result: PR #70: Add Alex's Mobs
  -

## 2026-10-13

- [the-orcs](https://www.curseforge.com/minecraft/mc-mods/the-orcs)
  - Verdict: Remade as orc war camps.
  - Result: PR #73: Orc war camps
  -
- [gitlab.com/jeseibel/minecraft-lod-mod](https://gitlab.com/jeseibel/minecraft-lod-mod), [voxy](https://modrinth.com/mod/voxy), `MedalTVMinecraft20261012205311868.mp4`, `MedalTVMinecraft20261012234452380.mp4`
  - Asked: "weird artifacting when using shaders, any idea whats wrong?"
  - Verdict: Not the cause (a friend saw artifacts with no DH). DH stays out anyway; Voxy rejected (no 26.3 build).
  - Result: Artifacts traced to Alex's Mobs + Citadel (not DH); both removed. DH nightlies and Voxy (no 26.3 build) rejected. PRs: PR #90: Remove Alex's Mobs and Citadel
  -
- [naturalist](https://www.curseforge.com/minecraft/mc-mods/naturalist/gallery)
  - Asked: "can we add the hostile mobs from this? maybe rarely spawn them near players in the wilderness to influence a random encounter"
  - Result: PR #92: Naturalist wilderness encounters; PR #95
  -
- [alienevo](https://www.curseforge.com/minecraft/mc-mods/alienevo)
  - Asked: "how does palladium work?"
  - Verdict: Palladium/AlienEvo is 1.20.1 and All Rights Reserved: remade as Xenoform.
  - Result: PR #109: Xenoform, the Xeno-Band (pack 0.8.125); PR #126: alien detail pass, band slam, ending glow (0.9.18)
  -
- [fiskheroes](https://www.curseforge.com/minecraft/mc-mods/fiskheroes)
  - Asked: "How compatible are these I just want to use some animations"
  - Verdict: Fisk's Superheroes is 1.7.10 and All Rights Reserved: ideas remade only.
  - Result: PR #112: rigid tendrils, two-web whip, barrage
  -
- [lunar](https://www.curseforge.com/minecraft/mc-mods/lunar), [enhanced-celestials-2-default-lunar-events](https://www.curseforge.com/minecraft/mc-mods/enhanced-celestials-2-default-lunar-events)
  - Asked: "experiment with random events, inspired by these, also en"
  - Result: PR #117: Lunar events and Bad Omen grudges
  -
- [weather-storms-tornadoes](https://www.curseforge.com/minecraft/mc-mods/weather-storms-tornadoes)
  - Asked: "what can we do with natural disasters? i knoiw this is forge but i've seen you do alot"
  - Verdict: Weather2 is All Rights Reserved: remade as our own disasters (storms, tornadoes).
  - Result: PR #127: Natural disasters, stage 1; PR #137: Rarer tornadoes; PR #172: Layered storm clouds
  -
- [camera-utils](https://www.curseforge.com/minecraft/mc-mods/camera-utils)
  - Asked: "can I put this config on an f7 bind?"
  - Result: No PR; answered/advice only
  -

## 2026-10-14

- [gravity-control](https://www.curseforge.com/minecraft/mc-mods/gravity-control)
  - Asked: "can gravity use anything from this"
  - Result: PR #135: Gravity sideways falls, fall forward, wall walk; PR #147: Earthboard ride animation
  -
- [powerborne-heroes](https://www.curseforge.com/minecraft/mc-mods/powerborne-heroes)
  - Result: PR #146: Stronger Web (0.10.20)
  -
- [github.com/superdesigndev/superdesign-skill](https://github.com/superdesigndev/superdesign-skill)
  - Asked: "can we change all the UI? for example the H screen when you acquire an ability? make it more readable and consice like the new z fighter ones or"
  - Result: PR #151: Tidier powers screen (H)
  -
- [hells-kitchen-heropack](https://www.curseforge.com/minecraft/customization/hells-kitchen-heropack)
  - Asked: "can we use this?"
  - Result: PR #153: Flash speed + Shield Throw
  -
- [fa-player-extension](https://modrinth.com/resourcepack/fa-player-extension)
  - Asked: "we need these worked in"
  - Verdict: Added with EMF/ETF; our own poses pause it (PR #154).
  - Result: PR #154 merged (pack 0.10.29).
  -
- [sleeping-overhaul-2](https://modrinth.com/mod/sleeping-overhaul-2)
  - Asked: "we need this"
  - Verdict: Forge-only: remade as our own timelapse night + bed rest.
  - Result: PR #156: Timelapse night and bed rest
  -
- [real-camera](https://modrinth.com/mod/real-camera)
  - Asked: "is this better than our fpv mod?"
  - Verdict: Not added: keep First-person Model (Real Camera would swing the view during our poses). Optional only if Andy asks.
  - Result: Answered in thread, no PR: keep First-person Model rather than Real Camera.
  -
- [patreon.com/Haubna](https://www.patreon.com/Haubna), [eureka](https://modrinth.com/mod/eureka), [valkyrien-skies](https://modrinth.com/mod/valkyrien-skies), [valkyrien-pirates](https://modrinth.com/mod/valkyrien-pirates)
  - Asked: "i want sailing"
  - Verdict: Valkyrien Skies/Eureka not used (Create Aeronautics can't be used either): our own block-built ships.
  - Result: PR #155: Sailing; PR #174: sailing stage 2; PR #187: creative ship kits
  -
- [perception](https://modrinth.com/mod/perception)
  - Asked: "if this works can we use it for all the previous screenshake effects? ( symbiote, modes, heavy character movement and impacts)"
  - Verdict: Perception is All Rights Reserved / 1.21.11: remade as our own directional screenshake.
  - Result: PR #157: Directional screen shake
  -
- [timeless-and-classics-zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)
  - Verdict: TaCZ added but runs our own flintlock gunpack; modern guns only in the Los Perrito future.
  - Result: PR #158: TaCZ guns
  -
- [via-romana](https://modrinth.com/mod/via-romana), [countereds-settlement-roads](https://modrinth.com/mod/countereds-settlement-roads)
  - Asked: "i want to edit the world travel. can this work with our carriages?"
  - Verdict: Via Romana / Settlement Roads remade as King's Roads in Kingdoms.
  - Result: PR #162 King's Roads (draft #159 closed).
  -
- [sound](https://modrinth.com/mod/sound), [a-good-place](https://modrinth.com/mod/a-good-place), [particular](https://modrinth.com/mod/particular), [subtle-effects](https://modrinth.com/mod/subtle-effects), [dynamicsurroundingsfabric](https://modrinth.com/mod/dynamicsurroundingsfabric), [physicsmod](https://modrinth.com/mod/physicsmod)
  - Asked: "can we use these anim"
  - Verdict: Physics Mod is closed, All Rights Reserved, client-only, no API: own ragdoll physics instead (PR #161). Sounds, AmbientSounds, Subtle Effects, A Good Place added + our own ambience (PR #160). Physics Mod split off (see its entry).
  - Result: Split into two threads: "Sounds, particles and detail mods" (Sounds mod installed in mods folder) and "Physics Mod ragdolls".
  -
- [physical-falling-trees](https://modrinth.com/datapack/physical-falling-trees)
  - Asked: "combine this with our treed"
  - Verdict: Remade in FallingTree (heavier side, speed damage, bees).
  - Result: PR #164: Physical falling trees
  -
- [simple-clouds](https://modrinth.com/mod/simple-clouds)
  - Asked: "would this work with our tornadoes?"
  - Verdict: Can't use: Forge-only, PolyForm Perimeter licence. Thread offered our own layered storm clouds (PR #172).
  - Result: Routed to an existing thread. PR #127: Natural disasters, stage 1; PR #137: Rarer tornadoes; PR #172: Layered storm clouds
  -
- [distanthorizons](https://modrinth.com/mod/distanthorizons)
  - Asked: "i really miss the distant horizons render benefits, is there any way you can increase render distance or chunk loading speed? my issue is the flight in the pack"
  - Verdict: Distant Horizons stays OUT (Andy's call). Chunky pre-gen instead + flight slows while chunks load (PR #165).
  - Result: PR #165 merged (pack 0.10.51): Chunky + flight slows ahead of unloaded chunks.
  -
- [mo-bends-fabric-port-by-skaffy](https://www.curseforge.com/minecraft/mc-mods/mo-bends-fabric-port-by-skaffy), [immersive-portals-mod](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod), [spiders-2-0](https://www.curseforge.com/minecraft/mc-mods/spiders-2-0), [nether-portal-spread](https://www.curseforge.com/minecraft/mc-mods/nether-portal-spread), [better-third-person](https://www.curseforge.com/minecraft/mc-mods/better-third-person), [shoulder-surfing-reloaded](https://www.curseforge.com/minecraft/mc-mods/shoulder-surfing-reloaded), [tool-belt](https://www.curseforge.com/minecraft/mc-mods/tool-belt), [realistic-torches](https://www.curseforge.com/minecraft/mc-mods/realistic-torches), [total-darkness](https://www.curseforge.com/minecraft/mc-mods/total-darkness)
  - Asked: "question about these mods, are any compatible? the third person mods seem to be better than our solutions, and they would all improve immersion"
  - Verdict: Shoulder Surfing replaced Camera Utils; Stormie's Spiders, Nether Portal Spread, Traveler Tool Belt added; torches + dark nights remade ourselves.
  - Result: PR #167: immersion mods (pack 0.10.52)
  -
- [advanced-hook-launchers](https://modrinth.com/mod/advanced-hook-launchers)
  - Asked: "can we adapt the web line and behaviors?"
  - Verdict: Ideas remade as weighted web lines.
  - Result: PR #166: Web lines with weight
  -
- [youtube.com/watch?v=suH-0zIiLU4](https://www.youtube.com/watch?v=suH-0zIiLU4), [advanced-chimneys](https://modrinth.com/mod/advanced-chimneys)
  - Asked: "i want to use this to power a new Smoke power's logic"
  - Verdict: YouTube is blocked from the cloud (Google robot check): ask Andy to upload the clip or describe the moves.
  - Result: YouTube link was a mistake (blocked from cloud anyway); Smoke power built around Advanced Chimneys behaviour, mod added to pack. PRs: PR #168: Smoke power, stage 1
  -
- [pillager-caravans](https://modrinth.com/mod/pillager-caravans)
  - Asked: "can you work this into the existing caravan system so there is a "hostile/neutral" caravan to interact with?"
  - Result: PR #169: Roaming pillager and merchant caravans
  -
- [countereds-accurate-hitboxes](https://modrinth.com/mod/countereds-accurate-hitboxes)
  - Asked: "will this work at all?"
  - Verdict: Not added: own per-limb hitboxes instead.
  - Result: PR #170: per-limb hitboxes
  -
- [snow-imprints](https://modrinth.com/mod/snow-imprints)
  - Asked: "this seems incredible"
  - Verdict: Soft Imprints is MIT, client-only, Iris-safe: added to the pack as-is.
  - Result: PR #171: Soft Imprints
  -
- [pocket_dimension](https://modrinth.com/datapack/pocket_dimension)
  - Asked: "can we add this to arsenal instead of his current storage?"
  - Verdict: Remade as the Arsenal pocket room.
  - Result: PR #173: Arsenal Pocket Dimension
  -
- [legends-superheroes](https://modrinth.com/mod/legends-superheroes)
  - Asked: "how would this tie in? i like the wall cling, and swing animations"
  - Verdict: Forge 1.20.1, All Rights Reserved: wall cling + swing remade ourselves.
  - Result: PR #194: active swing and new wall cling
  -
- [youtube.com/watch?v=nzqr4ZMGGpg](https://www.youtube.com/watch?v=nzqr4ZMGGpg)
  - Asked: "im going to send some references for what i want some moves to look like, Spike burst, should shoot tendrils out of everywhere on the body, not just from the ba"
  - Verdict: YouTube is blocked from the cloud (Google robot check): ask Andy to upload the clip or describe the moves.
  - Result: YouTube blocked from cloud (got title + thumbnails only); whole-body Spike Burst and symbiote suit/tendril remodel plan built and merged. PRs: PR #195: whole-body Spike Burst
  -
- [los-perrito](https://www.curseforge.com/minecraft/worlds/los-perrito)
  - Asked: "what if we made a new dimension, that was just this map, with feral zombies like the ones we made for corpses? I want to simulate going to to"
  - Result: PR #197: The future (0.10.102)
  -

## 2026-10-15

- [the-realistic-leaves-mod](https://www.curseforge.com/minecraft/mc-mods/the-realistic-leaves-mod)
  - Asked: "i need this"
  - Result: PR #203: Soft leaves
  -
- [villagelife](https://www.curseforge.com/minecraft/mc-mods/villagelife)
  - Asked: "can we add some of these features to our existing system?"
  - Verdict: Village Life is All Rights Reserved: features remade in Kingdoms.
  - Result: PR #214: Village life
  -
- [spice-of-life-valheim-edition](https://modrinth.com/mod/spice-of-life-valheim-edition)
  - Asked: "can we add this?"
  - Result: PR #240: Hearty meals
  -
- [enchanting-system-overhaul](https://modrinth.com/mod/enchanting-system-overhaul), [pomkots-mechs](https://modrinth.com/mod/pomkots-mechs)
  - Asked: "do either of these work?"
  - Result: PR #246: Enchanting overhaul
  -
- [bobby](https://modrinth.com/mod/bobby), [c2me-fabric](https://modrinth.com/mod/c2me-fabric)
  - Asked: "will this help rendering ?"
  - Result: PR #242: Bobby
  -
- [farsighted-mobs](https://modrinth.com/mod/farsighted-mobs), [stalker-creepers](https://www.curseforge.com/minecraft/mc-mods/stalker-creepers)
  - Result: PR #243: Farsighted monsters and stalking creepers
  -
- [real-camera](https://modrinth.com/mod/real-camera)
  - Asked: "this be better than our fpv mod?"
  - Verdict: Not added: keep First-person Model (Real Camera would swing the view during our poses). Optional only if Andy asks.
  - Result: Repeat of the 2026-10-14 ask; answered: probably not, keep First-person Model (Real Camera would throw the view around during power poses).
  -
- [playerex](https://www.curseforge.com/minecraft/mc-mods/playerex), [origins](https://www.curseforge.com/minecraft/mc-mods/origins)
  - Asked: "can i add these in any way?"
  - Result: PR #247: Attributes + Heritage
  -
- [fsang18s-heropack](https://modrinth.com/datapack/fsang18s-heropack)
  - Asked: "can we use this at all?"
  - Result: PR #248: certain affinity and callings; PR #257: Costume Table; PR #260: secret identities; PR #268: costume fix, power sets, tailors; PR #273: The Rig, stage 1; PR #274: The Rig, stage 2
  -
- [vampires-strike-back](https://www.curseforge.com/minecraft/modpacks/vampires-strike-back)
  - Asked: "how much of this can we include for our vampires?"
  - Result: PR #256 Night gifts
  -

## 2026-10-16

- `MedalTVMinecraft20261016010427502.mp4`
  - Asked: "im having the same clipping issue on the earth board, it seems some other skin is being drawn"
  - Result: Video showed a second head through the earthboard pose; MCA layers got the head turn twice, fixed. PRs: PR #276: Gravity meteor burst + earthboard feet; PR #278: Earthboard no second head
  -
- [loot-integrations](https://www.curseforge.com/minecraft/mc-mods/loot-integrations)
  - Asked: "are our lootr chests dropping correctly? can this configure them?"
  - Result: PR #287: Mimics keep Lootr chests' loot
  -
- [soul-industries](https://www.curseforge.com/minecraft/customization/soul-industries)
  - Asked: "one last rig pass?"
  - Result: PR #290: Rig Unibeam, repulsor poses, jets, pods
  -
- [clumps](https://www.curseforge.com/minecraft/mc-mods/clumps)
  - Asked: "can this help in anyway for lag or optimization?"
  - Result: PR #297: Add Clumps (0.12.6)
  -
- [apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis)
  - Asked: "this seems incredible, world tiers are just what i need. how much of this can we integrate, becaause i already see the license is ARR"
  - Result: PR #298: World tiers
  -
- `MedalTVMinecraft20261016135001977.mp4`
  - Asked: "why did everyone spawn in here? also villagers should try to help others that are downed, and they can all be downed now, unless decapitated"
  - Result: Villagers shut in a closed pocket; shut-in escape + downed/tending villagers added. PRs: PR #302: downed villagers + shut-in fix
  -

## 2026-10-20

- [github.com/Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify)
  - Asked: "I have a feeling something like this could improve the workflow seeing as i use alot of links for reference, and you having to index and read every webpage and "
  - Verdict: Not used: Claude still reads each page once, Graphify only transcribes speech from videos, YouTube blocked. This notes file is the replacement.
  - Result: Graphify suggested to cut token cost of reading reference links; being evaluated in this thread (no PR yet).
  -
- [more-babies](https://modrinth.com/mod/more-babies), [nyfs-spiders](https://modrinth.com/mod/nyfs-spiders), [github.com/Mugen87/yuka.git](https://github.com/Mugen87/yuka.git)
  - Asked: "Can we make every spider this size? then give them this ai"
  - Result: Thread started 2026-10-20 after usage-limit wait; work in progress, no PR yet.
  -
- [keybind-atlas](https://www.curseforge.com/minecraft/mc-mods/keybind-atlas) (CF 1513576, Modrinth fokFjwwh)
  - What: client-only clickable keyboard/mouse map (K) showing bound/unused keys per mod, rebind from it. All Rights Reserved; builds only for 1.20.1, 1.21.1, 26.2 (no 26.3).
  - Verdict: not added. No 26.3 build, ARR (can't port), K clashes with our skill tree; Controlling covers search. A visual map of our power keys would be our own remake if Andy wants it.
  -
- [just-in-nether](https://www.curseforge.com/minecraft/mc-mods/just-in-nether) (by justinoway)
  - What: "Bullkin", a gold-loving piglin-like Nether species (plus zombified bullkin, bullkin brute) that feuds with piglins over gold; a piglin boss and a bullkin boss lead armies, both hunt the player. GeckoLib. All Rights Reserved; NeoForge 1.21.1 / Forge 1.20.1 only.
  - Verdict: can't port (ARR, wrong loader/version). Remade as our own Nether faction in Kingdoms (thread "Nether faction").
  -

## 2026-10-21
- [nightfall-plague](https://www.curseforge.com/minecraft/mc-mods/nightfall-plague) (CF 1462657, by ALEXYSSSJ4/Anzukum)
  - What: vampire illager add-on: Night Bleeder (heals on hit, summons vampire bats, dashes, burns in sun, turns into a Blightwing bat near death), vampire bats (suction/charge), Atrophy plague effect (dead mobs rise as bats), Immunity potion from Cursed Tusks, tusk/immunity arrows, vampire villager callings (Alchemist/Slaughter/Chronicler/Bard: Immunity, Dried Meat, Cursed Card dash, calming Lute), nightmares, the Nightfall event (2-3 nights with no sunrise, miasma fog). All Rights Reserved; Forge 1.20.1 / NeoForge 1.21.1 & 1.21.8 only.
  - Verdict: can't port (ARR, wrong loader). Remade as our own in Kingdoms' vampires (thread "Nightfall Plague for vampires").
  -
- [better-mount-steering](https://www.curseforge.com/minecraft/mc-mods/better-mount-steering) (by Seramicx, MIT, source github.com/Seramicx/better-mount-steering)
  - What: client-side free-look mount camera; WASD steers the mount toward where you look; config mountTurnSpeed 0.25. Forge 1.20.1, NeoForge/Fabric 1.21.1 only, no 26.3.
  - Verdict: can't drop in (no 26.3). Rebuilt as our own "free rein" in Kingdoms equestrian (FreeRein/FreeReinClient), Kingdoms 0.7.107 / pack 0.15.6.
  -
- [jaki-versatile-structures-sails-sea](https://www.curseforge.com/minecraft/mc-mods/jaki-versatile-structures-sails-sea) (by Jackiefr + TheGoldenWorld, GPLv3, source github.com/jackieLama/jvssailandsea; Forge/Fabric 1.20.1 only)
  - What: 9 ocean ship structures (illager galleon, cargo ship, villager trader, trireme, Roman war trireme, drakkar, 2 catamarans, small sub) with pillager/villager crews.
  - Verdict: GPL, so the galleon, cargo ship, trader, war trireme and drakkar .nbt files ship unchanged in Kingdoms (licenses/jaki) and JakiShips turns them into crewed ships. PR #349 merged, Kingdoms 0.7.112.
  -
- [zombie-awareness](https://modrinth.com/mod/zombie-awareness) (by Corosauce, ARR, needs CoroUtil; Fabric/Forge/NeoForge up to 1.21.1)
  - What: zombies track you by blood scent (spilled when hurt, dripping at low health), sound (mining, doors, chests, pistons, TNT, eating, bows; louder wins) and exposed light; optional wandering hordes.
  - Verdict: ARR, remade as Kingdoms `Awareness` (zombies only, tag jaymoskingdoms:aware) tied to Quiet Step / Shadowed / Slip the Net. PR #402 merged, Kingdoms 0.7.155 / pack 0.15.73.
  -
- [flails](https://modrinth.com/mod/flails) (by ingobeans, MIT, source github.com/ingobeans/flails; Fabric 26.1.x)
  - What: a flail whose small spiked head (its own entity) orbits the owner about 3 blocks out at waist height; the chain is small crossed-plane links (vanilla iron_chain texture) every 0.375 blocks from the owner to the head; arm swing via playeranim.
  - Verdict: no code or art copied. Our Balkon flail keeps its own whirl/slam timing and effects but now looks like it: interlocking iron links off the held-out hand, a ball under half the old size, the whirl round the hand at waist height (pack 0.16.2, PR #491).
  - Thread id: flail/whip thread (PRs #487, #491)

## 2026-10-25
- [immersive-weathering](https://modrinth.com/mod/immersive-weathering) (by AstralOrdana, LGPL-3.0, needs Moonlight Lib; Fabric/Forge, newest 1.20.1, last update 2025-01)
  - What: blocks age by themselves: moss spreads, stone/bricks crack, copper/iron rust, leaves drop leaf piles, icicles and frost, mud/sand changes, soot from fires.
  - Verdict: no 26.3 build and dormant, can't add. Remake offered (Kitchen/Kingdoms), kept slow and out of settlements so Epic villages don't rot
- [dehydration](https://modrinth.com/mod/dehydration) (by Globox1997, GPL-3.0, needs Cloth Config; Fabric only, newest 1.21.1)
  - What: thirst bar beside hunger, dirty water (boil it), flasks/leather canteens, heat and sprinting drain faster, thirst damage.
  - Verdict: no 26.3 build; GPL code can't go in our MIT mods. Remake offered as Kitchen thirst. Same thread.
- [scorchful](https://modrinth.com/mod/scorchful) + [frostiful](https://modrinth.com/mod/frostiful) (by TheDeathlyCow, LGPL-3.0, both need Thermoo + YACL + Fabric API; Fabric/NeoForge up to 26.1.2, Frostiful 26.1 still beta)
  - What: body temperature. Scorchful: heat in deserts/nether/sun, sweat, sunstroke, turbans/sand cloaks, drinks. Frostiful: cold in snow/at night/wet, freezing, fur clothes, heaters, frost wands.
  - Verdict: no 26.2 or 26.3 builds yet, and they'd double up with Kitchen WinterCold + seasons + blizzards + Frostborn. Remake offered as one temperature system on top of WinterCold. Same thread.

- **Create: Lanterns** (https://www.curseforge.com/minecraft/mc-mods/create-lanterns), MrXeon, NeoForge 1.21.1, All Rights Reserved.
  Screenshots show ring-bearers and grabbed mobs wrapped in a soft yellow halo: a bright edge hugging the silhouette and a wavy, fading outer rim.
  Took only the look for the Z Fighter ki glow (Andy: "faint, but apparent"): `ZFighterKiGlow`, PR for pack 0.16.7. Nothing copied.
- [whip](https://www.curseforge.com/minecraft/mc-mods/whip) (by Cukkoo, MIT, source github.com/Cukkoo12/whip; Forge 1.20.1)
  - What: physics whip and flail: 25-segment Verlet chains in world space, anchored at the handle tip captured from the item's own render matrix (per entity, view space), slash, charged overhead strike, grapple and reach enchantments.
  - Verdict: no code or art copied. Took the idea: our flail chain and whip lash became Verlet ropes anchored at the handle tip read from the drawn item (LashSockets), head pinned to our own Lash path. Pack 0.16.3, PR #491.
  - Thread id: flail/whip thread (PRs #487, #491)
- [viltrumite](https://modrinth.com/mod/viltrumite) (by baranhan123, All Rights Reserved; Fabric/Forge/NeoForge 1.20.1 + 1.21.1, needs Viltrumite Flight)
  - What: Invincible-style flying brawler. Gifs show close, rapid exchanges, a blow that launches the foe into a cliff with a burst of debris and white shockwave rings, then the attacker rushing after it to keep hitting; bot clones that dash and block.
  - Verdict: ARR, nothing copied. Took the feel for Z Fighters: tyrants/champions throw strings of light blows instead of one-shot axe swings, finishers slam foes into walls/ground (ZFighterImpact), players and rogues rush after the launched foe. Pack 0.16.10, Z Fighter combat thread.
- [Courier Owls](https://www.curseforge.com/minecraft/mc-mods/courier-owls) (CurseForge 1675546, by MehVahdJukaar; All Rights Reserved, no source link; Fabric/NeoForge 1.21.1 + 26.1.2, needs Moonlight; no 26.3 build)
  - What: ten owl species, tree hollows and bird houses; tame with wolf/rabbit food; put items in a Cardboard Package with a player's name and a tamed owl flies it to them wherever they are (time grows with distance), both get notified, recipient has a few seconds to take it before it returns.
  - Verdict: ARR, nothing copied. Remade as Kingdoms 0.7.201 (pack 0.16.14): written/sealed letters, tame messenger pigeons (parrot base, own art) that fly letters across the world, MCA villager couriers who walk or ride them along King's Roads, and world news + MCA mail delivered as letters instead of chat. Thread "Add messenger birds and couriers".
- [Farmer's Delight](https://www.curseforge.com/minecraft/mc-mods/farmers-delight) (CurseForge 398521, by vectorwing; MIT for the whole repo incl. textures, github.com/vectorwing/FarmersDelight; Forge/NeoForge 1.20.1 + 1.21.1 only, no Fabric 26.3 build)
  - What: placed cooking pot and skillet over any heat (campfire, stove), cutting board + knives (chopping, straw from grass), feasts placed as blocks and served by the bowl, Comfort/Nourishment effects, rice/cabbage/onion/tomato + wild crops, rich soil/compost, canvas, cabinets.
  - Verdict: MIT, art reused with credit (LICENSE_farmersdelight). Remade in Kitchen 0.5.28 (PR #524): pot/pan over fire, cutting board + knives, feasts, Comfort/Nourishment, cabbage/rice + wild crops, FD textures on matching items. Thread "Overhaul cooking with Farmer's Delight ideas".
- [Warring Nations](https://www.curseforge.com/minecraft/mc-mods/warring-nations) (by TacYEET; All Rights Reserved, source-available at github.com/tacyeet/warringnations; NeoForge 1.21.1, needs FTB Teams/Chunks/Library; no Fabric 26.3 build)
  - What: player nations on FTB teams/claims: 10 data-driven war types (chunk capture, attrition, breach, risk, trench, total war), score-based peace terms (land, reparations, tribute, vassalage, white peace), governments, vassals/colonies, rebellions, treasuries, bounties, war betting, king of the hill, 627 presets.
  - Verdict: ARR, nothing copied. Took ideas only for Kingdoms 0.7.224 (pack 0.17.9, `GreatWars`): realm creeds whose clashes build tension into war, creed war goals shaping the peace, great-war coalitions and sellswords, rebellions, war wagers. Thread "Add large wars between wealthy kingdoms".

## 2026-10-27
- [Horizon](https://github.com/horizon-rust/horizon) (horizon-rust; MIT; Rust reimplementation of the Minecraft Java 26.3 client + dedicated server, ~2.8k stars, parity audit 2026-10-13)
  - What: vanilla 26.3 rewritten in Rust (Vulkan/wgpu), joins vanilla servers, own mod system = Rust compiled to sandboxed WebAssembly. "Java mod jars do not run"; no Fabric API, no Mixins ("Mods add; they cannot patch the game's code"). Wasm API has HUD/commands/chat/storage, experimental UI/input/render, JSON blocks/items, server block/chat/join hooks; entities, attachments, custom models/renderers and client-server networking are still "planned". Modpack importer carries data/assets only; code mods are ported one by one (ATM10: 1 full, 42 partial).
  - Verdict: not portable today. Our ~470k lines of Java (426 mixins, custom entities/renderers/screens/payloads/attachments, MCA dependency) would need a full Rust rewrite AND APIs Horizon doesn't have yet. Revisit if its entity/attachment/net APIs ship. Thread "Assess porting the pack to Horizon".
- [Hi3D v1.0](https://github.com/hi3d-official/Hi3Dv1.0) (hi3d-official; Hi3D Open License 1.0, commercial OK under 200k MAU with "Powered by Hi3D" credit; released 2026-10-23)
  - What: AI refiner that turns a coarse 3D mesh + a reference image into a very detailed mesh (512-1536 res, GLB). Geometry only: no textures, materials or colours. Needs Linux + NVIDIA GPU (Ampere+), CUDA 12, flash-attn; tested on a 96 GB H20, no CPU path.
  - Verdict: no use for the pack. Minecraft entity/block models are a few dozen textured boxes; Hi3D makes the opposite (dense untextured meshes), and our cloud has no GPU. Blockbench stays the model tool. Thread "Assess porting the pack to Horizon".
- Blockbench helpers (Andy 2026-10-27, "find whichever you can use best"): [WaitMyDawn/blockbench-mcp](https://github.com/WaitMyDawn/blockbench-mcp) (MIT, Python, Chinese README, 0 stars), [Formixel](https://github.com/Rynix01/Formixel) (MIT, Node 22 CLI, WIP), [yberudev/Blockbench-MCP](https://github.com/yberudev/Blockbench-MCP) (no licence, 100 tools but needs the Blockbench desktop app), [BlockForge](https://github.com/zielonyxiom/blockforge) (Roblox/GLB stud models, not Minecraft).
  - Verdict: WaitMyDawn/blockbench-mcp is the one: runs headless in the cloud (in-memory model, no app), cubes/bones/textures/pixel paint/animations, saves .bbmodel, exports Java block/item JSON, GeckoLib, Bedrock, OptiFine CEM, software preview renders + UV quality checks. Tested 2026-10-27: 124/125 tests pass (needs `mcp<2`), built + rendered + exported a block. No Java entity-model code export; we translate its cubes into LayerDefinition code ourselves. Thread "Assess porting the pack to Horizon".

## 2026-10-28
- [Visiting Villagers](https://www.curseforge.com/minecraft/mc-mods/visiting-villagers) (redstone3game; All Rights Reserved; Fabric/Forge/NeoForge up to 26.2)
  - What: day visitors (14 professions + unemployed + children) arrive at daybreak and leave at dusk, no beds/workstations; needs 6 villagers + 1 golem; specialised wandering traders by biome; `/visitors check|clear|population`.
  - Verdict: ARR, remade ourselves in Kingdoms `Visitors` (MCA villagers, from a named neighbouring town, eat and spend in town). Thread "Add village visitors and food economy".
- [Chef's Delight (Fabric)](https://www.curseforge.com/minecraft/mc-mods/chefs-delight-fabric) (redstone3game; MIT; Fabric up to 26.2; needs Farmer's Delight Refabricated)
  - What: Cook (skillet) and Chef (cooking pot) villager professions with meal trades, cook/chef houses in villages.
  - Verdict: no 26.3 build and needs the real Farmer's Delight (we remade FD in Kitchen), so ideas only: Kingdoms `Meals` (cook profession, cookhouses, townsfolk cook at home or buy meals). Same thread.

## 2026-10-29
- [Symbiote: A Bonding Experience](https://www.curseforge.com/minecraft/mc-mods/symbiote-a-bonding-experience) (kitigawa; All Rights Reserved, "free to use in any modpack"; Forge 1.20.1; v1.1.3; no wiki/GitHub, Discord only)
  - What: one symbiote from asteroids; 5 strains (Predator/Guardian/Shadow/Sculk/Royal); Bond/Trust/Stress/Hunger/Mood meters, 4 named stages; radial commands (Protect/Hunt/Hide) + Status panel; Living Armor, Tendril Yank, Wall Cling, Consume, Apex Form, arm item slots, build wall/bridge/stairs, mantle; requests; takeovers; rebond at death spot holding your inventory; no cure.
  - Verdict: ideas only (ARR, Forge 1.20.1). Read for a feature checklist of what it adds that our symbiote doesn't (thread "Symbiote: A Bonding Experience checklist").

- **Symbiote: A Bonding Experience** (https://www.curseforge.com/minecraft/mc-mods/symbiote-a-bonding-experience), kitigawa, Forge 1.20.1, ARR ("free to use in any modpack" only).
  - Bond/Trust/Stress/Hunger/Mood meters, 5 strains (Sculk senses heartbeats), requests that build stress if ignored, takeovers (walks/digs/turns your head), border pulses, no cure.
  - Ideas only, nothing copied: vampire/werewolf deception checklist (Claude Doc https://claude.ai/artifact/4k22memmMbtEq7rQnobfbD); vampire hunger + heartbeat near players, PR #650.

- **Proximity Text Chat** (https://www.curseforge.com/minecraft/mc-mods/proximity-text-chat), StyStatic, CF 716287, ARR, Fabric/Forge up to 1.21 (no 26.3).
  - Chat only heard nearby; optional partial-hearing band (lost words as a phrase or underscores). Ideas only: Kingdoms `ProximityChat` (0.7.322) does its own talk/whisper/shout/act ranges, dropped words, walls, bubbles.
- **Voice Physics** (https://www.curseforge.com/minecraft/mc-mods/voice-physics), Shamanalle, CF 1712556, MIT, Fabric 26.3 file 9251110 (CurseForge only), needs Simple Voice Chat.
  - Walls muffle by material, echo, weather/water, quieter sneaking, HUD radar, server game rules. Shipped unchanged in the pack (0.17.149).
- **Simple Voice Voice Changer** (https://www.curseforge.com/minecraft/mc-mods/simple-voice-voice-changer), sawiq, CF 1658503 / Modrinth simple-voice-voice-changer, CC BY-NC-SA 4.0, Fabric 26.3 1.2.5 (CF 9030770; 1.3.0 Modrinth only so far).
  - Presets man/woman/kid/titan/demon/batman/radio, studio, J toggles; client API `VoiceChangerApi.pushOverride` (docs/API.md on GitHub imsawiq/sv-voice-changer). Shipped unchanged; Kingdoms `VoiceMasks` overrides voice for werewolf/ape/goblin disguise/mask. Simple Voice Chat (CF 416089, ARR, 26.3 2.6.27) shipped by id; UDP 24454.

- **github.com/storytold/craft-anything** (MIT/Apache, Rust desktop app, 2026-11-03): a clean-room "study a site/app/game, have an agent rebuild it, compare" engine. Not a Forge→Fabric porting tool (Gemini's handoff said so wrongly). Took only the idea: study first, rebuild clean, check independently. Nothing installed. Thread "Integrate Gemini's Ki..." (PR #856).
- **github.com/fatihaydost/argon-bridge** (MIT, Claude Code plugin, 2026-11-03): has Gemini 4 Argon (via the `agy` CLI) review Claude's plans and commits. Needs Andy's own Google/agy login; not usable in cloud threads. Not installed.
- **github.com/marmyx77/omni-ai-mcp** (MIT, Python MCP server, 2026-11-03): Gemini/OpenRouter tools (media, research, coding agent) for Claude. Needs a Gemini API key; would have to be added as a connector by Andy. Not installed. Re-checked 2026-11-03: cloud containers reach generativelanguage.googleapis.com and PyPI, so it can run in cloud threads via `uvx omni-ai-mcp==4.8.2` with GEMINI_API_KEY as an environment secret. argon-bridge needs an interactive `agy` Google login, so only the Steam Deck could host it.
- **curseforge.com/minecraft/mc-mods/immersive-tanning** (Otectus, GPLv3, Forge 1.20.1, 2026-11-03): multi-step leather (Tanner's Rack fleshing, Vat, Supple/Harness/Hardened finishes, Leatherworker's Bench, quality from fleshing + stitch timing). No blood. Not usable (Forge); ideas only. Thread "Let blood magic draw from ground stains".
- **curseforge.com/minecraft/mc-mods/alexs-butchery-compat** (Otectus, GPLv3, Forge 1.20.1, needs Butchery + Alex's Mobs, 2026-11-03): carcasses hung on a hook/rope bleed out into a blood grate (250/1000/2000 mB by size; giants bled where they lie; fish/insects skip), 3 cutting stages, skins on a rack, trophies, rugs, skeleton displays. Not usable (Forge, Alex's Mobs banned); ideas only.

## Recrafted (resource pack by MR_CHOO; Andy's, 2026-11-03)
- respackopts id "recrafted" v13, formats 34-75. Options: Recrafted clouds, fog, lightmap, light rays, bushy/animated leaves, animated grass/ferns, overlay textures; font choice. Credits: Fresh Animations-based mob animations used with FreshLX's permission (all rights reserved).
- No armour dirt or puddles in its options. Ideas only, never copy its assets. Our own versions are in PR #861 (code-built light shafts, puddles, armour grime).

## Otectus batch (Andy's comparison list, 2026-11-03)
All Otectus unless noted; none have a Fabric 26.3 build, so ideas only. Checklist: https://claude.ai/artifact/ShEhPAGKjExHKUE7AFcmAu
- **sitting** (Sitting+, SaltyWater, ARR, Fabric/Forge/NeoForge to 1.21.5): sit anywhere/stairs/slabs/fences, 20+ poses, held-item poses. Ours: Meditate sit (0.18.99); seats + poses missing.
- **custom-trade-currency** (GPLv3, Forge 1.20.1): coins replace emeralds, minting, coin sack. Missing (emeralds stay).
- **immersive-apotheosis** (GPLv3, Forge 1.20.1, needs Apotheosis): reforge/salvage/socket/cut minigames. N/a; no sockets/affixes in pack.
- **immersive-jewelry** (GPLv3, Forge 1.20.1): jeweller's bench, lapidary, graded rings/amulets. Partly: Baubles slots, found pieces only.
- **locks-reforged** (CC BY-NC, Forge/NeoForge): lock any block, keys, pin minigame. Partly: Kingdoms Lockpicking 0.7.234 (locked loot chests only).
- **mca-quests** (GPLv3, Forge/NeoForge): 260+ villager quests, situations, projects. Ours: Kingdoms Quests #52/#83.
- **plant-fibers** (ARR): fibre, grass bandage/bed/armour, rope. Partly: plant fibre, string #520, linen bandage, rope lift.
- **guard-villager-skins**, **recruits-skins** (GPLv3, client add-ons): n/a, we run neither base mod; guards are MCA bodies.
- **immersive-smithing** (GPLv3): forge/anvil/quench, quality. Ours: Overgeared smithing 0.17.119.
- **mca-mob-compat** (GPLv3): n/a (no Guard Villagers; workers MCA #40).
- **mca-conversations** (GPLv3): topic hub, hidden trust values, promises. Partly: Approaches #67, Recollections #347, SharedHistory #574.
- **runic-gods** (GPLv3, Forge 1.20.1): 8 patron gods, favour, apostasy, god bosses. Missing (shrine 0.7.462 + Faith creed only).
- **immersive-brewing** (GPLv3): mortar/kettle/alembic, quality grades. Partly: cauldron brewing #626, Alchemy 0.17.0.
- **mca-reputation** (GPLv3): per-village incidents, witnesses, gossip. Ours: Kingdoms reputation + Deeds #255.
- **ars-n-spells** (GPLv3): Ars Nouveau + Iron's Spells bridge. N/a.
- **mca-kingdoms** (GPLv3, Forge 1.20.1): named villages, governments, elections, diplomacy. Ours (bigger); elections/treaties missing.

## Structura (structuramc.com, Andy's link 2026-11-03)
- https://structuramc.com/build/bo46tuxsoqydl4r "Medieval House" (16x16x23, 1410 blocks, DataVersion 5023, made with bloxelizer). Site offers .litematic, .schem (Sponge v2), .nbt (vanilla structure) and .mcstructure (Bedrock) for every build, free without login (PocketBase API: /hcgi/platform/api/collections/builds/records/<id>, files under /hcgi/platform/api/files/pbc_1033968107/<id>/<file>).
- Terms: personal use; no selling or commercial redistribution. So players download and upload their own copies; we don't bundle their files in the jar.
- Verdict: all three Java formats readable by Kingdoms designs (schematics thread, Kingdoms 0.7.484+).

- **Werewolves: Become a Beast** (Cheaterpaul / TeamLapen, curseforge.com/minecraft/mc-mods/werewolves-become-a-beast, github TeamLapen/Werewolves). Forge/NeoForge 1.19-1.21.1, needs Vampirism. Code LGPLv3, but README: textures and models may be used only "for anything related to Vampirism", so not in our pack. Ideas: three werewolf forms/paths (fighter beast, survivalist, human-like), fur armour, silver weakness, meat-only diet. 2026-11-05 verdict: can't use its model; offered our own new form inspired by it.

- MedalTVMinecraft20261105102225282.mp4 (2026-11-05, 30 s): Andy on an ocean island, dives in; seabed crowded with dozens of sharks/fish. Andy: "for the spawn rates". Routed to the mob overspawning thread.

- **ImmersiveVFX** (Shinyflvres, curseforge.com/minecraft/mc-mods/immersivevfx, CF id 1766116). 26.3 NeoForge only, alpha, All Rights Reserved, client-only; works with Sodium but NOT Iris. Adds its own shader-like look: vanilla-style shadows, clouds/sky, fire, water foam + caustics, fireflies, ambient/ground fog, biome/dimension visual overhauls, sandstorms, auroras, heavy rain, bouncy block placement. No blood/explosion effects. 2026-11-05 verdict: can't add (wrong loader, ARR, clashes with Iris + Complementary); offered Andy own remakes of the pieces that don't fight Iris.

- MedalTVMinecraft20261105175333046.mp4 (Andy, 2026-11-05): Nether start; Andy is a vampire (HUD shows "Kin" and "Wings %"), climbing nether pillars then gliding on bat wings. The wings were elytra slabs hiding the body, and the red eyes didn't sit on MCA's face. Fixed in PR #1052.

## Felled (CurseForge 1715901), checked 2026-11-06
- Raiiiden (Zarra Studios). All rights reserved ("code requestable"): ship unchanged, never copy. Fabric 26.3 file 9305152 (0.1.5).
- Needs Zarith (1720181, Fabric 26.3 file 9284445, Jolt via jolt-jni natives). Jolt rigid-body trees: tip, roll, snap, float, walkable.
- Fells on PlayerBlockBreakEvents.BEFORE (default phase); public FellingHandler.fellFrom(ServerLevel, BlockPos, Player, Vec3). Kingdoms FelledCompat calls it (PR #1071).
