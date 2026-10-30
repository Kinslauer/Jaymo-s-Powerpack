# Weapon Forms: Andy's picks from Ultimate Battle (2026-10-30)

Andy linked Ultimate Battle (CurseForge, by jevelyn, Fabric 26.3, v1.0.1). It is **All Rights Reserved**, so we take
ideas only. Our code, our names and our own vanilla-style art. That mod gives you 12 weapon styles, each with two skills
and a filmed ultimate, carried in five slots and switched on by a mask. Most of its powered moves were already ours, so
the list turned its styles into fighting skills for everyone. Pick page: https://claude.ai/artifact/265bdrjAjv4smt7gY3qLNh

Andy ticked **all 40 rows**. He wrote two notes:
- Five-Art Belt: "Weapon forms unlocks this".
- Battle Stance: "weapon forms".

He also added, in the project chat: "you should be able to get training quests from wizards, experienced guards".

**Reading of the notes:** the whole system is called **Weapon Forms**. A *form* is one learned move (an "art" on the
pick page). Nobody has a belt or a stance at first. Learning your **first form** from any teacher unlocks both. The
teachers are a school master, a veteran guard or a wizard.

## Where the code goes
- **Powers** `combat/forms/` holds the engine: the form registry, the belt, the stance, practice, the moves, the HUD
  bits and the films. It sits next to `combat/Guard`, `core/LockOn`, `core/finisher/Finishers` and `UltimateFilm`.
  Each weapon family gets its own class (`BladeForms`, `SpearForms`, `FistForms`, `KnifeForms`, `ClawForms`), so
  tracks never edit the same file.
- **Kingdoms** holds the teachers and the people: schools, halls, masters, guard drills, wizard trials, duels, bouts
  and schooled guards. It tells Powers "teach form X" through `PowersCompat`, by reflection and only when Powers is
  loaded, the way Powers already reads `Skills.rank`.
- Every new attachment class goes into `JaymosPowers.registerAttachments()`. Each patch gets its own
  `lang/parts/forms_*.json`. Tuning numbers are `static final` constants at the top of each class.
- Every new recipe and route shows in JEI. Forged items take Tinkers routes, never grid recipes.

## Keys (open question 1)
Ultimate Battle's Z, X, C and H are all taken in our pack. Middle mouse is lock-on, and while locked on the scroll
wheel swaps targets. Proposal:
- **Hold mouse side button 1** (the Combat Art key) with a weapon out to take the stance. Hold it again to drop it.
  Putting the weapon away drops it too.
- **In stance, a tap of the same key** fires the form in the current belt slot. **Sneak + tap** fires the second form,
  and the masterstroke fires on a K.O.'d foe.
- **In stance, Alt + scroll** picks the belt slot, so scrolling stays free for lock-on. The current slot shows as a
  small strip above the hotbar that fades with Auto HUD.
- Out of stance, the key keeps its current jobs: Charge, War Cry and Shield Throw.

## Patch 1: Weapon Forms core + veteran guard drills (Powers, small Kingdoms part)
- **Form registry** (`combat/forms/Forms`): id, weapon family (item tags, Tinkers kinds and WeaponMod through its
  compat class), kind (art or masterstroke), cooldown, stamina cost, practice needed.
- **Learned by practice**: each form has a practice count. It grows when the form lands on Sparring Dummies, Training
  Posts or real foes, and each rank makes the form stronger.
- **Five-Art Belt**: five slots, unlocked by your first form. You change slots only at a training post, a Training
  Dummy or a school hall, never mid-fight.
- **Battle Stance**: unlocked by your first form. Taking it turns lock-on on and trims the HUD down. Each school's
  stance has its own pose (`PoseBlend`), and it works with every weapon family.
- **Two forms and a masterstroke** structure. A masterstroke only fires on a K.O.'d foe and counts as that weapon's
  top finisher.
- **Chain Counter**: the Combat Arts chain shows as a small counter with a grade by the crosshair, and fades with
  Auto HUD.
- **Form Cancels**: a timed form press cancels the last swing of a chain into the form.
- First forms, so the patch can be tested: **Sheath Draw** (a dash cut on the draw, harder on an unaware foe) and
  **Crescent Sweep** (a low arc that trips foes and does heavy poise damage).
- **Veteran Guard Drills** (Kingdoms): a guard with long service and high rank gives drill quests. The drills are hold
  the gate against a drill wave, parry ten blows, or spar to the first fall. Each finished drill teaches or ranks up
  one form without joining a school. Drills post as town errands.
- Ops command: `/forms teach|forget|list <player> [form]`.

## Patch 2: blade and spear forms + how fights look (Powers)
- **Rain of Cuts** (blade masterstroke), **Spear Lunge** (the rapier lunge made longer for spears and javelins,
  goes through to a second foe), **Couched Lance** (Rider, mounted, damage from horse speed, knocks riders out of the
  saddle, and Kingdoms Cavalry uses it too), **Pinning Throw** (spear masterstroke: a javelin pins a foe and a tug of
  the cord pulls it back).
- **Dutch Angles and Cuts**: `UltimateFilm` gets a tilted (rolled) shot and hard cuts between two or three angles,
  for every power film.
- **Masterstroke Films**: masterstrokes play short `UltimateCinematic` films that can be skipped, and witnesses get
  the bars.
- **School Swing Trails**: each school leaves its own vanilla-style trail. Ink for blade, sparks for lance, dust for
  fist.

## Patch 3: fists, knives and claws (Powers, Alchemy part)
- **Ground Stomp** (the Giants' shockwave ring at small size), **Falling Fist** (you still take your fall damage,
  part of it goes into the foe as well), **Marked Quarry** (the knife school marks a foe whose outline shows through
  walls, then a dash behind it), **Cast-off Cloak** (a straw decoy that mobs swing at for 3 s), **Tangle Cord**
  (bolas that slow a foe like a sprained foot).
- **Clockwork Hornets**: a Golemancy research and infusion makes small clockwork drones that harry a marked foe.
  There is no grid recipe.
- **Fang Gauntlets**: a new Tinkers tool kind (claws), forged only (a `WeaponParts` kind), with Rend bleeding on the
  rake and a pounce. Its school lives in the Nether's demon villages.

## Patch 4: schools and teachers (Kingdoms)
- **Fighting Schools**: one for each weapon family. Each has a **training hall** in capitals, built like the Blossom
  job halls, and an MCA villager **master** with a talk screen. You join one school at first.
- **Wizard Trials**: Court Wizards and caster villagers set trials. Duel a summoned shade, keep a ward up through a
  storm, or strike only the marked foe. They teach Mage and Sorcerer forms and spell-forms for blades. Their hints
  come in talk, never on the screen.
- **School Manuals**: a book from each founder, written as odd sayings with oblique hints only. The sky school's
  founder came down from the Aether's remnants.
- **A Second School** once you master one. Each class gets a bonus with its matching school: Warlord with blade,
  Brute with fist, Thief with knife, Rider with lance.
- **Master's Weapon**: mastering a school gives you a blueprint for its signature weapon, made through Tinkers and
  Overgeared only.
- **School Masks**: one original cosmetic mask for each school, worn as a costume head piece and taken on or off
  with the `'` key.

## Patch 5: who fights with forms (Kingdoms)
- **Schooled Guards**: a kingdom's guards learn its local school and use those forms on you. They're MCA villager
  guards, so this fits `Rulers` and Temperaments.
- **Honour Duels**: students challenge you. A duel ends when someone is downed, and their Temperament decides
  mercy. Winning raises your standing with the school, and beating its master opens the masterstroke.
- **Rival Schools**: schools in rival kingdoms look down on each other, and you hear it in Tidings and talk.
- **Festival Bouts**: taverns and festivals hold school bouts on the tournament bracket screen.

## Patch 6: for the powered (Powers + Kingdoms)
- Divine-born: **Flying Sword** (the held blade fights on its own for a few seconds, then comes back) and
  **Eight-Sign Circle** (foes inside can't use powers or forms for a short time).
- Gravity: **Comet Ram**. Arsenal: **Gate Ricochet** through Void Gates. Nadir: **Three Worlds Film**, an ultimate
  with flashes of the Overworld, Nether and Aether. It's visual only, with no portal and no new realm.
- Sorcerer lightning: **Bolt Step and Storm Ring**. Wind Thunderhead: **Thunder Dive**, and fall rules still apply.

## Running it
- Order: **1 alone first** (the others build on it), then **2 + 3** (both Powers, separate classes), then **4 + 5**
  (Kingdoms, separate classes), then **6**.
- Two Sonnet editor tracks at a time, each in its own worktree. Each finding gets an independent verifier, and a
  fix loop stops after two rounds with no progress.
- One PR per patch, and Andy merges to test. Every PR bumps its version. The SevTech PRs (#699 to #701, Powers
  0.18.14 to 0.18.16, plus the patches still to come) are claiming Powers versions, so each Weapon Forms PR takes the
  next free number at the moment it opens. It re-checks the open PRs first, and bumps again if a merge takes its
  number.

## Open questions
1. The keys above: hold the side button for stance, tap to fire, Alt + scroll for the belt.
2. Can a powered player learn forms too, or are forms only for people with no power? Proposal: everyone can, but a
   power's own keys win, and forms only fire in stance.
