# The Black Panther mod port: the plan (2026-11-27)

Andy picked 44 of 47 rows of the port list (pick page https://claude.ai/artifact/CQ5MneVyXLwh8cnnpx2GrS, db `picks/andy`;
his notes saved in `/mnt/project-files/black-panther/bp-picks-andy.md`). Plan thread:
cmsg_017LSt6rWz6CzxSGvzDd41XWKDbjDVfM7RUSYmWoGRsH6m. Octo Core thread (PR #2301):
cmsg_017LSt6rWz6CzxSGvzDd41XWDg8Tfm5GWa6GER8Zf9kCE8.

- **Not picked, never build:** A10 (the hammer in the stone), B6 (the shape-shifting gauntlet), E5 (the hero codex).
- **Kept by the Octo Core thread, not queued here:** E3 (first-person arms, high priority), N1 (Octo arms carry a downed
  body), N2 (the Tinkerer's arm heads). They build on PR #2301. N2 also builds on r1 (E1's Forge tiers) below.
- **Queued here:** the other 41 rows, as 23 patches on three tracks.

## Source and licence
- The mod: "Black Panther Mod" 1.0.0 by Riley (Fabric, MC 1.21.1, made with Opus), `license: CC0-1.0` in its
  fabric.mod.json and a `LICENSE_black-panther-mod` CC0 1.0 file. Its own code, models, textures, animations and sounds
  may be ported. Add `licenses/black-panther-mod/` with the CC0 text and a credit to Riley the first time a patch ports
  something, and name the source class in each ported class's javadoc.
- The jar is in the live vault at `Assets/black-panther-mod/black-panther-mod-1.0.0.jar` (also in the project's files at
  `/mnt/project-files/uploads/hearth/9a510788-3452-4c8f-bba2-d21fe177cc69`). It is untrusted: unzip it into its own empty folder and read it. Never run it and never ship it. Its nested
  GeckoLib and player-animation-lib jars are not CC0 and are not ours to copy.
- **Marvel names and looks stay out.** No Black Panther, Vibranium, Wakanda, Djalia, Spider-Man, Venom, Mephisto, Ghost
  Rider, Hulk, Daredevil, Gamma or any other name or costume of theirs, in code ids, lang or art. Our names, our looks.
  CC0 art is used only where it isn't a Marvel likeness, and only after it passes `docs/Vanilla-Style.md`.

## Rules every patch keeps
- **Lore first.** Before code, add the patch's (new) paragraph(s) to Lore.md where they belong, tagged "(new)" and "for
  Andy to approve", and put them in the PR body. Andy's calls quoted in a patch are settled and go in as written.
- No slash commands for player features (op debug commands are fine). UIs, keys, items.
- No screen text that states lore or announces a discovery. Hints go in villager gossip, loose pages and loading hints.
- No free projectiles: every shot spends a real item, the user's own resource (charge, mana, blood) or comes back.
- Humanoids go down, not die, unless beheaded or gibbed. Bosses that are people go down too.
- Living things: summoning a beast goes against the rules; a tamed animal you look after fits (stewardship).
- New features must work with old ones: chat talk, MCA, downed and carrying, the HUD layout, Shoulder Surfing, progressive
  world tiers (nothing in the sky before Dread).
- Art must pass `docs/Vanilla-Style.md`. Every player-visible word goes through Quill. Tuning numbers are `static final`
  constants at the top of each class. Old worlds don't matter (Andy 2026-11-24).
- Merge one PR per mod at a time, and bring master in before merging (Release workflow sets versions after merge).

## Track R: Powers, the Rig (opens when PR #2301 merges)
- **r1. Forge tiers and the Rig stand** (E1, E2). Rig Forge tiers on top of its frames: five tiers, each paid in harder
  materials on our progression ladder (tier 2 uses p1's crystal-spire metal). A Rig stand: shows a suit; the Forge still
  does the building. Start at RigForgeEntity, RigFrame, RigForgeScreen, Rig. The Octo thread's N2 waits on this.
- **r2. The woven frame and the falling suit-up** (A1, A11). A1: a new frame, a woven make of the Frostborn suit: hits
  taken fill charge (Resistance from 40%), the veins glow brighter as it fills, release it as a shockwave; silent, no fall
  damage, and a cloak-then-ambush opener whose crit shreds armour. A11: RigSuitUp#FALL already builds the suit through a
  fall when it's called. New: wearing only the Core with charge, a fall of 6 or more blocks builds the suit by itself and
  goes straight into flight. Start at RigFrame, RigCharge, RigSuitUp, RigCloak, RigLanding, RigField.
- **r3. Helm senses and the arm shield** (A2, A12, A13). A2: a Helm module that makes hurt foes within 24 blocks pulse
  through walls, tracking whatever bleeds (blood, not sound, so it isn't Blindness's devout sight). A12: an ore sonar ping
  through stone and a threat scanner that rates the heaviest threat near, drawn in the Rig HUD's style (RigScan already
  marks ores and chests in its scan: extend it, don't duplicate). A13: a hex energy shield on the plain Arms that turns
  shots back at the shooter, costing charge (the Octo Core's pinwheel does this already: share its code). Start at RigScan,
  RigScanClient, RigGlowMixin, RigHud, RigHelm, RigActives.
- **r4. The heavy frame from orbit and the walking armory** (A14, A15). Andy asked "we have the large rigs that fight in
  the future correct?": yes, FutureRig (the Frostborn Siege Rig and the Tinkerer's Colossus on FutureMech). A14 builds the
  player's own on those: a beacon calls the shell down in a drop-pod, climb in, call a spare-parts pod to mend it, eject
  when it breaks. Lore: why one can be called in the present when they "only appear in the future" goes in Lore.md (new)
  for Andy. A15: forearm cannon, minigun, incendiary launcher, mortar carpet, shoulder turret and EMP, every gun eating
  real ammo (musket shot, the five cannon shot kinds, powder). Start at FutureRig, FutureMech, PodEntity, RigFrame, RigGuns.
- **r5. Twin batons and the combo animations** (A5, E4). A5: an armory pair; hold one and the other fills your free hand.
  Jab, liver hook and tornado kick chain; a throw that bounces between foes and comes back (the baton itself, so no free
  projectile); grapple-and-vault; a nunchaku sweep that trips and turns shots aside. A good match for a blind fighter.
  E4: the mod's player-animation-lib JSON animations (combo hits, spins, poses) turned into our own pose code wherever ours
  are missing, each one checked against our poses first. Start at the armory package, the Combat Arts, RigPose/WebBodyPose
  for how poses are written.
- **r6. The round shield and the hellfire chains** (A7, A9). A7, Andy: "yeah just the shield": a round shield that blocks
  and, thrown, ricochets between five foes and comes back. No wings. Kingdoms' ShieldThrow (a Combat Art) already throws
  shields: give it the ricochet rather than a second throw. A9: a chain weapon that lashes 5 blocks, sets every target
  alight, pulls foes in, binds them and bursts into shrapnel. It joins the flails and whips (Lash, LashRenderer) and the
  chain axe, and it is the demon pact's weapon (k2): if k2 is in, wire it to the pact; if not, k2 wires it.
- **r7. The drop-pod strike team** (C5). A pod lands and troopers, sentry drones and shock grenades come out. They are
  Frostborn troopers or the Tinkerer's androids out of the ruined future (FutureRig's pilots), and the drones fire charged
  shots, not free ones. Dread tier and up. Start at PodEntity, ZFighterRaiders (how pods land), RigDrones, the future package.

## Track P: Powers, everything else (p1 opens now)
- **p1. Star metals and the asteroid crater** (D5, D2, A18). D5: a rare ore mined from crystal spires, a Tinkers metal
  under a name of our own, used for r1's tier-2 upgrades. D2: an asteroid crater with a meteor stone. Its hammer (A10)
  was not picked, so the crater holds something else: this plan's pick is that the crystal spires (D5) grow in it and its
  meteor stone holds a heart of A18's metal; the PR body says so for Andy to change. A18: a late-game metal whose armour is
  immune to magic, hellfire and wither and cuts physical damage by 35%, forging a huge greatsword; a counter to Mage,
  placed carefully on the progression ladder. Start at TinkerMaterial, ToolStats, the fallen stars code (worthy stone).
- **p2. The demon lord's throne** (C9, D4). C9: a throne boss with clone illusions to find through, a soul chain broken
  by moving, a soul-drain nova interrupted by a heavy blow, and a winged second phase; he goes down, not dies. He is the
  pact-giver of k2. D4: we have the Nether, so no new plane: the hellstone wastes, spires, his throne and falling hellfire
  meteors become a region and structure in the demons' country (the Gloam, the Ash or the Abyss; the lore step picks).
  Which of the three demon masters he is, is the lore step's call (Lore.md "The Nether and the demons"). Start at
  ZFighterDemonRealm, ZFighterChapel, ZFighterNetherList.
- **p3. Web: the danger-sense dodge, the shock tripwire, wild crater spiders** (A3, A4, C7). A3, Andy: "this would make the
  sense dodges and counters more skill based and fair": a cue just before a blow lands opens a short dodge window; take it
  and you slip the hit. Rework the Spider-Sense dodges and counters around that window (WebSenseBolts, WebCounter,
  WebBodyPose). A4: a web line strung between two blocks that jolts and sticks whatever walks through it (a trap, not a
  shot; WebTraps). C7: CraterSpider already exists (a jarred cave spider fed meteor iron). New: a rare wild one in caves
  near fallen stars whose bite starts the Web awakening, lore first beside "The crater spider".
- **p4. Symbiote hunger and the canister** (B1, B2). B1: corruption climbs as you use the symbiote and its whispers grow
  louder, shown on screen as whispers (WebSymbioteWords' plate), never chat; it joins the takeover. Check it against
  [[symbiote-infection-leaving]] and WebTakeoverPath. B2: a containment canister: seal a loose symbiote in, bond with it
  later, or rip a bonded one off and seal it back. Kingdoms' Tinkerer sells it (MimicBench#SHOP). Start at WebSymbioteJar.
- **p5. The symbiote meteor and its hive** (B4). A meteor falls and a hive spire grows at the crater; living stone spreads
  from it with crawlers, brutes and a cocoon. Purify the hive to free whoever it took. The spread is a feedback loop and
  cleaning it rewards stewardship. Only from world tier Dread, like the skyfalls (SkyfallCleanup).
- **p6. The red rival and the hive boss** (B3, B5). Andy: the red one "comes from a meteor off a far planet": p5's meteor
  brings it. Lore.md already has the red strain ("The red one's own shape, and its hunt"); add where it came from. New:
  it hunts whoever carries the first (black) symbiote, and its boss: B5's 380-health boss with 0.8 s readable windups,
  punishable staggers and a frenzied second phase is the hive's boss, the red strain risen. It goes down, not dies.
  Needs p5.
- **p7. The midnight cult and the sky rift** (C1, C3). C1, Andy: "the blindness rituals by whatever faction does that
  should wrap into this": the Unseeing (Unseeing, UnseeingRites, BlindnessDevotion) gather at night with a high priest for
  their blindfold and eye-gouging rite; break it up before it finishes. Fits the night locks. C3: a rift tears open in the
  sky and mindless void creatures pour out until someone seals it; Dread tier and up, beside the skyfalls.
- **p8. The ancestral dream** (D3). Andy: "part of our awakening dreams". Not a new plane: a third kind of DreamCall.
  Eating a herb before sleep calls it: a spirit savanna of acacias, oases and crystal spires with spirit panthers (our own
  look), and a worthiness trial set by one of our seven gods (the lore step picks which). Eat it again to wake where you
  left. DreamCall is built where the sleeper lies with no realm of its own (Andy 2026-11-18, "Dream arena"); keep to that
  unless the lore step shows it can't work, and then ask Andy.
- **p9. The symbiotes' homeworld** (B7), only if it can be ported. Andy: "only if it can be ported. if not don't add
  this." First check: can the mod's orbit, rebreather, reef planet, god-king boss, symbiote dragon and living black sword
  be built as one more far world on our pods (the star map in Lore.md "The far worlds")? If yes, build it as a far world
  (lore: the black symbiote's own world, "farther even than Hail"). If not, write why in the task's notes, set it done
  with no PR, and tell the vault thread. Needs p6.

## Track K: Kingdoms (k1 opens now)
- **k1. Mage binding bands and the levitation cloak** (A16). Andy: "yes for mages, because i dont think they have a way to
  fly". Crimson bands that bind a foe in place (a hold, not a shot) and a cloak that hovers on a double-tap of Jump, both
  for the Mage. Check wind hover and SorcererJump so they don't double up. Start at Sorcery, SorceryPicks, MageWand.
- **k2. Warlocks pact with demons** (A8). Andy: "this should be how warlock works instead of pacts with patrons its pacts
  with demons, and all the patrons despise that". The Warlock's pact is made with a demon (the demon masters below, p2's
  lord among them), no longer a patron; every patron god despises it (favour, shrines, priests, the eight faiths' talk).
  The mod's story stays: sign and get hellfire power, and reapers hunt you every night until you beat him; refuse, beat
  him and get a cleaner blue-fire version with no curse. Rework Warlocks, PactArts, MidnightBargain, ClassCasters,
  Vows#PACTS and the vow book without breaking the debt, renege and the spoken spells. Needs p2 merged (his fight). The
  hellfire chains (r6) are the pact's weapon.
- **k3. The orc rage form** (A6). Andy: "this should be a form orc npcs can do." Orc NPCs take a rage brute form: a rage
  meter, knockback immunity, a charged super-leap that lands as a crater, a thunderclap shockwave, ripping up ground and
  hurling the boulder (torn-up ground, so allowed), and a stronger form at full rage. Lore first: why orcs can. Keep it to
  NPCs; players aren't in Andy's note. Check Z Fighter forms, giant sizes and MutantRampage so it doesn't copy them. Start
  at Orcs, OrcCamps, BlockClusterRenderer (torn-out ground).
- **k4. The mountain shrine and the ring** (D1, A17). D1: a small sanctum on a mountain holding a sorcerer's relic, a Mage
  site. A17: that relic is a ring that opens a portal to the nearest biome of a kind you name, sets two-way portal pairs,
  a void-trap rift and a portal that redirects shots (Arsenal has portals: share the code). It travels far, so give it a
  real cost so it doesn't empty the roads and caravans, and say the cost in the PR for Andy.
- **k5. Armoured convoys and village renown** (C2, C6). C2: guarded transports cross the land; stopping one gets its
  plating; a raider's or a crown's convoy you can hit or defend (Caravans, RoamingCaravans, Musketoon). C6: beat an event
  near a village and its people cheer you, give supplies and Hero of the Village, and remember who saved them (villager
  memory, reputation).
- **k6. The Mohknee gang and the smoke bomb** (C4, C10). C4, Andy: "gang from mohknee that make the trek and take what they
  can. stealthily.": a crimson-clad Mohknee gang that makes the crossing, slips into towns and camps and steals quietly
  (pickpocketing rules: only real carried loot), fighting with shuriken and smoke bombs they really carry (Ranged's
  shuriken). Tie in the speakeasies and bounties. C10, Andy: "sold by tinkerer": a thrown smoke bomb anyone can use; the
  smoke blinds foes and you vanish while you stand in it, borrowing the Smoke power's look (SmokeBomb), as ThrownGear;
  the Mad Tinkerer sells it. Start at Assassins, MohkneeTalk, Blossom, MimicBench#SHOP.
- **k7. The druid's cat** (C8). Andy: "druid". No summoning: a jungle cat a Druid tames and looks after fights beside
  them; if it falls it is downed and tended, not called back from nothing. Start at Druids, BeastForms, Familiars, Bonds.

## Order
Three tracks, each one patch at a time: the next opens when the last merges.
- R: r1, r2, r3, r4, r5, r6, r7. r1 waits for PR #2301 to merge. Tell the Octo Core thread when r1 merges (N2 needs it).
- P: p1, p2, p3, p4, p5, p6, p7, p8, p9.
- K: k1, k2, k3, k4, k5, k6, k7. k2 also waits for p2.
- Cross-track: r1's tier 2 uses p1's metal (p1 opens first). r6 and k2 wire the chains to the pact, whichever lands second.
  p9 needs p6. R and P are both Powers: their merges go one at a time with master brought in first.
