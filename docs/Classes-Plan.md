# Plan: player classes (pack 0.9.0)

Andy's ask (2026-10-13): 0.9.0 is about classes. Eight of them, each a bundle of passive strengths and a cost. Classes
sit beside powers (a Mage can still be a Wind user) and beside the K skill tree; they change *what you're good with*,
the tree changes *how far you've practised it*.

## Where it lives
In **Jaymo's Kingdoms**, next to the skill tree (`Skills`), which already has the attribute-modifier plumbing, a respec
path (court wizard or bottle o' enchanting), the K screen and the casters' staffs and scrolls (`Arcana`). New classes:
`Classes` (the enum, the attachment, join/respawn refresh), `ClassPerks` (the numbers, all `static final` at the top),
client `ClassScreen` (the choice) and `lang/parts/classes.json`.

Weapons are recognised by **item tags**, so every mod's gear counts without a compile dependency: `#jaymoskingdoms:light_blades`
(daggers, knives, ninjato, kunai, Balkon's knives and daggers, Tinkering daggers), `#two_handed` (claymore, war axe,
glaive, war scythe, halberds, Balkon's halberd/battleaxe/warhammer, Tinkering great weapons), `#bludgeons` (flanged mace,
cudgels, Balkon's mace, flails and Armory axes), `#guns` (Balkon's musket, blunderbuss, flintlock), `#light_armor`
(leather, chainmail, caveman hide) and `#heavy_armor` (iron, diamond, netherite, the Paladin/Gladiator/Warlord plate).

## Choosing
- The first time a player joins a world (no class yet), a screen opens: the eight classes, a line each, pick one. Esc
  isn't allowed; ops can `/kingdom class set <player> <class>` and `/kingdom class clear`.
- Respec: a **Writ of Rebirth**, a rare item (Grim ruin and goblin-king loot, and the court wizard sells one for 30
  emeralds once per Minecraft week). Using it clears your class, and the screen opens again. Mage's beginner spell and
  Sorcerer's element are chosen on that same screen as a second page.
- Creative players can switch on the H powers screen too (a class tab), like powers.

## The eight classes, mapped to what's in the pack
| Class | Gain | Cost | How |
|---|---|---|---|
| **Thief** | Light blades +25% damage, 20% faster swing; each leather/chain/hide piece gives +1 armour | none | tags; attack-damage and attack-speed modifiers while a light blade is held; armour modifier per piece |
| **Warlord** | Two-handed weapons +25% damage; heavy armour slows half as much | none | tags; the heavy-armour weight (below) halved |
| **Mage** | A wizard's staff that casts; spells hit harder, cooldowns shorter; one beginner spell of choice | −2 hearts (max health −4) | `Arcana` grows a castable staff: right-click casts the spell on it, scroll wheel while sneaking swaps; a Mage who reads a scroll *learns* it instead of using it up. Beginner choice: Fire Bolt, Arcane Bolt or Warding (Raising and Draining are a necromancer's, found later) |
| **Archer** | Bows and crossbows +25% damage, +10% move speed; a 2-block jump while drawing; feather falling while a bow is drawn | none | arrow-damage hook; jump-strength modifier and fall-damage multiplier only while `isUsingItem` on a bow |
| **Survivalist** | Hunger drains 30% slower, air lasts twice as long, +1 heart, fall damage −30%, +5% move speed | none | exhaustion factor (as Packhorse), oxygen-bonus attribute, max-health, fall-damage multiplier (ADD_MULTIPLIED_TOTAL, so powers that negate fall damage stay at zero, Andy's rule), move speed |
| **Brute** | Shields block 100% and shrug off axe disables faster; maces, flails and axes +30% damage; +2 hearts | base speed −8%, heavy armour slows twice as much | tags; shield hook; speed and health modifiers |
| **Marksman** | Guns +30% damage, reload and windup 35% faster, range +50%, slight aim assist (the shot bends up to 3° to the nearest target); each light-armour piece gives +1 armour | −1 heart | Balkon's gun items found by id like `Muskets` does; mixins on WeaponMod's ranged-item use time and projectile spawn (optional mod: all through `WeaponModCompat`) |
| **Rider** (the ninth, Andy asked for one more) | In the saddle the horse runs 15% faster and tires 25% slower; horses trust you 50% sooner; blows from horseback +20% | on foot 5% slower | transient speed modifier on the mount while ridden; `Equestrian.drain` and `Bonding.addTrust` factors; mounted attack-damage modifier |
| **Sorcerer** | Imbue a held blade with one of six elements (water, fire, earth, air, lightning, ice); a few short-cooldown bolts of that element | can't cast the big scrolls (Raising, Warding) | an **Imbue** key (default J): the blade takes the element for 2 minutes with its trail. Fire sets alight; water douses and hurts blazes/endermen, slows; earth staggers and knocks down; air launches; lightning chains to 2 neighbours; ice freezes 3 s. The element is chosen on the class screen and swapped at a court wizard |

Heavy-armour weight is new for everyone: each iron/diamond/netherite/plate piece slows 1.5% (6% in full plate), so
Warlord's and Brute's lines mean something. Gravity users cancel it already (their rule since 0.8.1).

Hearts: Mage 8, Marksman 9, vanilla 10, Survivalist 11, Brute 12. These stack with the skill tree's Second Wind.

## Stages (each a draft PR; pack 0.9.0 at the first merge)
1. **Classes and the choice** (Kingdoms 0.6.0 / pack 0.9.0): the eight classes with every *passive* number above
   (tags, attributes, armour weight, Archer's draw-jump and feather fall, Brute's shield), the first-join screen, the
   Writ of Rebirth, the op commands, the class shown on the K screen.
2. **Mage and Sorcerer magic** (done, same PR): the castable staff, learning scrolls, the beginner spell; the Imbue
   key (J; sneak+J the bolt) and the six elements.
3. **Marksman's guns** (done, same PR): reload, windup, range, aim assist through WeaponMod mixins.

## Andy's answers (2026-10-13)
1. The "1." meant a ninth class: make one up. It's the **Rider**, built on the Equestrian horses.
2. Heavy armour slows everyone a little: yes.
3. Respec: keep both the Writ of Rebirth in loot and the court wizard's sale.
4. The pick screen looks like a character-creation screen (Andy's Elden Ring reference): a card per class with a
   portrait, a stat table and the gear it's good with.
5. (07:45Z) No starting gear for anyone. The Mage gets the staff's recipe from a court wizard, or finds a staff.
