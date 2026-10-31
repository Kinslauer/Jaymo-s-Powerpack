# Havens, monster kings and sworn captains: the plan (2026-10-31)

Andy picked 39 of the 40 rows on the pick page (https://claude.ai/artifact/S6pnoNw53cdLq2WUH2MQ8j, copy in project
files `havens-monsters-study/havens-picklist.html`). The only row he left out was "Ask CanineGray to use the horse model".

Sources, all ideas only, with our own names, code and vanilla-style art:
- **King's Monsters** (Ares12): All Rights Reserved, NeoForge only.
- **Middle-earth: Shadow of War** (Monolith / Warner Bros.): a commercial game. WB also holds a US patent (granted 2021)
  on its Nemesis system. We never use the name "Nemesis", we copy none of its text or screens, and we build our version
  from our own systems (Recollections, Temperaments, wounds and limbs, Will staredowns, Valor, the post, UltimateFilm).
- **Better Horses** (CanineGray): CC BY-NC 4.0, but its licence file says not to modify its designs. We ship it unchanged
  with credit, the same way as Canoe Boats, and copy none of it.

Standing rules that bind every patch:
- No new dimensions or portals.
- Lore comes only through talk and books, never screen text that states it.
- Iron tools and weapons are forged (Tinkers route), never made at the crafting grid. Every route shows in JEI.
- Humanoids are MCA villagers. Ogres stay monsters of legend (`monsters/`).
- Tuning numbers go in `static final` constants at the top of each class.

Andy's notes from the page, folded in below:
- **Learning their traits:** "you can question downed humanoids, unlocked in the tree, holding right click for the
  execution now has you select either question or kill dragging either left or right. goblins, orcs and players count.
  different animations for each weapon. and it opens the talk menu with the relevant options." Patch 0.
- **Challenges and death threats:** "tidings can now be nailed this way and posted on boards in towns, they should be
  dropped to you by bird less as those generally come from kingdoms. they would need a good reason to send it straight to
  you. you may just not hear of some news." Patch 0 (the post) and patch 4 (challenges).
- **More monstrous non-humans:** "they can still tie their tails down, but those who know, will always smell it." Patch 7.
- **A greenskin life:** "unlockable by reaching good rep with the orcs". Patch 7.
- **Havens built their own way:** "some havens live in unity with goblins and orcs or maybe under an orc king". Patch 6.
- **Turning captains to your side:** "requires valor or learning a spell from Varro who remembers commanding leagues of
  orcs". Patch 4. Varro is the mana spirit (`spirits/`); the spell is taught in talk with him.

## Patches

Each patch is its own PR. Every finding gets an independent verifier, and the version is set to master + 1 just before
merging. Patches 1 to 5 stack (they share the tribe and captain code). Patch 6 (havens) and patch 8 (horses) touch other
files and can run beside them, two tracks at a time.

### Patch 0, question or kill and the town post (Powers + Kingdoms)
- **Question or kill.** Holding use on a downed humanoid (villager, goblin, orc, player) opens a choice: drag the mouse
  left to question, right to kill. Kill is today's finisher. Question plays a per-weapon hold (blade at the throat, club
  raised, bow drawn at the face, bare fist in the collar) and opens MCA's talk screen with the questioning options
  (who leads you, where is your camp, what does your captain fear, let them go). Unlocked by a node in the Skills tree.
  Players being questioned get a choice screen of their own (answer or stay silent).
- **The town post.** Tidings from kingdoms are posted on town bounty boards as notices. A pigeon brings one straight to
  you only when there's a reason (it's about you, your realm or your kin, or someone sent it to you). Otherwise you hear
  it from the board, from a courier in town, or not at all.

### Patch 1, the tribes and their grudge (Kingdoms)
- A tribe per brood and region: goblin villages, orc camps and ogre holds belong to the nearest king's tribe.
- **One grudge per tribe**, per player: raiding camps and killing near them raise it. High grudge sends a hunting band.
  Tribute, a truce token, killing the tribe's enemies or time lower it. You hear the mood from captives and the pedlar.
- **The pedlar under escort:** a goblin trader with two orc guards walks between camps, sells odd wares, **seat maps**
  and the truce token. Killing him raises the whole tribe's grudge.
- **Maps to the king's seat** in every chief's chest.
- **The bestiary** book (filled as you kill or question each kind; hints only).

### Patch 2, the ogre tribe and the king's seat (Kingdoms)
- **Ogre outposts** (plains, swamps) led by a two-headed brute or a cave ogre, and a great **ogre hold** per region with
  an **Ogre King** in three phases. Killing him clears his outposts.
- **The king's guard and trials:** the biggest of their kind posted at every king's door. One trial before the throne:
  a pit fight (orcs), a trap hall (goblins), a stone-lifting brawl (ogres).
- **Ore held by a king:** bog iron in swamps crumbles while any Ogre King reigns, and mines for everyone once one falls.
  Its tools and armour are forged through Tinkers.
- **Crippling blows:** heavy orc and ogre hits can break a leg.

### Patch 3, captains (Kingdoms)
- **A ladder under every king:** named captains (bynames, rolled looks, gear, temperament) lead camps, patrols and
  outposts. When a king dies the strongest captain takes the seat.
- **Strengths, weaknesses and fears:** 2 to 4 traits each, tied to our systems.
- **Learning their traits** through patch 0's questioning, a chief's orders in camp chests, or the pedlar.
- **They remember every meeting** (Recollections): lines about how you ran, spared or shamed them. A short letterbox
  film the first time you meet each one.
- **Kill a player, climb the ladder:** the killer moves up a rank, gains a trait and wears your trophy. It becomes your
  sworn foe in your journal.

### Patch 4, captains living their lives (Kingdoms)
- **They come back** with our wound and limb marks, unless finished or burned.
- **Captains fight each other:** duels, feasts and ambushes each day, heard of by the post. You can interfere.
- **Blood brothers and avengers.**
- **Shame or spare** a downed captain with a Will staredown.
- **Challenges and death threats:** a challenge nailed to a camp gate, in the same way as patch 0's posted tidings.
- **Turning captains to your side:** needs Valor, or the binding spell Varro teaches (he remembers commanding leagues
  of orcs). Sworn captains guard, spy and lead raids; proud or spiteful ones may turn.
- **Taking a king's seat:** a siege where his warchiefs must fall or turn first. Win it to crown a sworn captain.

### Patch 5, spoils (Kingdoms + Powers)
- **Trophies worn as baubles**, **weapons taken from kings** (Tinkers routes), **monster crowns** (worn or placed).

### Patch 6, havens with their own life (Kingdoms, can run beside 1-5)
- **Built their own way** (stilts, hillside, trees, longhouses; mixed takes a bit of each). Some havens live alongside
  goblins and orcs, and a few sit under an orc king.
- **Banners and dress**, **wares only havens make**, **haven rulers and their titles**, **strangers at the gate**,
  **haven bounties**, **clashes on the border**.

### Patch 7, havens and the tribes (Kingdoms, after 1 and 6)
- **Tribes who spare non-humans**, **calling on a tribe**.
- **A greenskin life:** a life-story road unlocked by reaching good standing with an orc tribe.
- **More monstrous non-humans:** bigger looks for the bloods furthest from human. Tails can be tied down to pass, but those
  who know (their own blood, havens, hunters, the tribes) always smell it.

### Patch 8, horses (Kingdoms + pack, can run beside any)
- **Ship Better Horses** unchanged with credit (packResourcePacks). Check that 26.3 loads it.
- **Donkeys and mules join our breeds** (bond, roster, handbook, wheel) but keep vanilla's renderer, so Better Horses
  draws them (Andy's choice, 2026-10-31).
- **A fuller breed horse of our own**, **coat names you can read**, **smoother rearing and grazing**.

## Decided
Donkeys and mules join our breed systems but keep vanilla's renderer, so Better Horses draws them (Andy, 2026-10-31).

## Done
- Patch 0 (Powers 0.18.97, Kingdoms 0.7.424): question or kill on the full execution hold (turn left to question, right
  to kill; Kingdoms' Interrogation perk, Will page), four questioning holds by weapon, MCA's talk menu with Who leads
  you / Where is your home / Press the threat / Let them go / Finish them, memories of being questioned or spared; the
  world's news posted on town boards ("Word from abroad"), each town hearing about 70% of it, and a bird bringing a piece
  only when it names you or your realm.
- Patch 0 follow-up (Andy: "early on the tree, and available in stealth and strength as well"; "can you make one?"):
  any of three tier-1 perks unlocks it. Strength's Rough Questions (threats hurt, break twice as often, one more try),
  Stealth's Quiet Word (take the unaware from behind in a choke hold without downing them; they keep no memory of you;
  "Who else is about?"), Will's Interrogation (no lies get past you, a stare once threats fail, "Swear you'll never raise
  a hand to me"). A player held down gets an answer screen (truth, lie, silence or their own words), and the asker a
  question screen.
- Patch 1 (Kingdoms 0.7.425): tribes per brood and region with the oldest camp as the king's seat, one grudge per tribe
  per player (kills, chiefs, the pedlar up; tribute, truce tokens, the rival brood's dead and time down), hunting bands
  at 60, the pedlar under orc escort (wares, mask, seat map, truce token, bestiary, the tribe's mood), seat maps in
  chiefs' stores, the Greasy Bestiary, and "What does your king think of me?" when questioning.
- Patch 2, first half (Kingdoms 0.7.427): ogre outposts and an ogre hold per region, the three-phase Ogre King with his
  two-headed guard, his fall clearing the region's outposts and freeing bog iron (crumbles to rust until then), and
  crippling blows from ogres and orcs.
- Patch 2, second half (Kingdoms 0.7.429, Powers 0.18.102): goblin and orc kings' guards at the door, the three trials
  (pit fight, trap hall, stone-lifting brawl) that win a truce and a gift, and bog iron as its own Tinkers material with
  forged armour.
- Patch 3 (Kingdoms 0.7.431): captains. A ladder of 4 per tribe (ranks 1-5, bynames, gear and size by rank) posted to
  camps or leading hunting bands; 15 traits (8 strengths, 4 weaknesses, 3 fears); learned by questioning, chiefs'
  orders and the pedlar, kept in the journal; a film at the first meeting and lines about the last one; a player's
  killer climbs (or rises) and becomes their sworn foe, wearing their head; the strongest takes a fallen king's seat.
- Patch 4, first half (Kingdoms 0.7.432): captains come back unless finished or burned (missing what they lost); daily
  duels, ambushes and feasts set and settled on the post (kill one first and it's off); blood brothers who join the
  fight and avengers who swear on your head; a Will stare on a downed captain spares him (empty hand) or shames him.
